package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BlockSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothingPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** Bridges the Angle Solver UI to the byte-exact jump model and back into the live TAS.
 *
 * <p>Threading: {@link #solve()} snapshots the whole problem on the caller (main) thread into an
 * immutable {@link Job}, then runs the multistart solve on a daemon thread so the game never stalls.
 * The worker touches only the snapshot, never live state. {@link #poll()} (called each frame on the
 * main thread) publishes a finished result into {@link AngleSolverState}. {@link #apply()} folds the
 * solved facings back into the rows and retriggers the sim.
 *
 * <p>The model (ExactJumpModel) reproduces MC movement to the bit, so the reported path equals what
 * the sim runs after Apply; the live SimulatorEntity remains the source of truth once applied. */
public final class AngleSolverEngine {

    // Byte-exact model: "X <= wall" holds to the bit, so no cushion is needed. Require strictly-feasible
    // solutions (0 = never accept a clip) and let the player hug each wall as close as the facing lattice
    // allows on the safe side. The achievable hug is bounded by the ~1e-6-spaced sine buckets, not by this.
    private static final double FEAS_TOL = 0.0;
    /** EQ corridor half-width and met-reporting slack (docs/research/angle-solver.md 3.1). */
    private static final double MET_TOL = 1.0e-4;

    /** A result within this of a same-axis user cap at the objective tick is optimal on any model: the
     *  race is skipped, or its winner labeled (docs/research/angle-solver.md 3.1). */
    private static final double CAP_GAP_TOL = 1.0e-6;

    /** CMA-ES initial step (deg). With the wider-than-one-turn search bounds the global basin is a
     *  single continuous region, so a moderate sigma finds it in a handful of restarts. Only one strafe
     *  sign is solved: A and D are mirror-symmetric (flip the sign and shift air-tick facings by 90deg
     *  for an identical trajectory), so the optimal objective is the same either way. */
    private static final double CMAES_SIGMA_DEG = 90.0;

    /** Per-effort solve budget (see {@link SolveCore}). Fewer restarts/evals is faster but can miss a
     *  feasible basin on a hard jump, so FAST trades robustness for speed. */
    static SolveCore.Budget budgetFor(AngleSolverState state) {
        switch (state.getEffort()) {
            case THOROUGH: return new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
            case CUSTOM: {
                AngleSolverState.SolveBudget b = state.getSolveBudget();
                BucketAscentPolish.Config cfg = b.getPolishDepth() == AngleSolverState.PolishDepth.EXHAUSTIVE
                        ? BucketAscentPolish.THOROUGH : BucketAscentPolish.FAST;
                return new SolveCore.Budget(b.getRestarts(), b.getMaxEval(), b.getPolishCount(), cfg);
            }
            default: return new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);
        }
    }

    static long deadlineNanosFor(AngleSolverState state) {
        if (state.getEffort() != AngleSolverState.Effort.CUSTOM) return 0L;
        int secs = state.getSolveBudget().getTimeBudgetSeconds();
        return secs > 0 ? secs * 1_000_000_000L : 0L;
    }

    static LongRunSolver.LongRunConfig longRunConfigFor(AngleSolverState state) {
        if (state.getEffort() != AngleSolverState.Effort.CUSTOM) return LongRunSolver.LongRunConfig.defaults();
        AngleSolverState.SolveBudget b = state.getSolveBudget();
        return LongRunSolver.LongRunConfig.of(b.getWindow(), b.getCommit());
    }

    static boolean useWindowSolverFor(AngleSolverState state) {
        if (state.getEffort() != AngleSolverState.Effort.CUSTOM) return true;
        return state.getSolveBudget().getUseWindowSolver();
    }

    private final AngleSolverState state;
    private final BoxController boxes;
    private final InputData inputs;
    private final IntConsumer onApplied;

    /** Byte-exact forward, configured for the loader's MC inertia rule (see ExactJumpModel.forMcVersion).
     *  Stateless/immutable, so a single instance is shared read-only across the restart threads. */
    private final ForwardModel model;

    private Plan lastPlan;

    // Background-solve handoff. `pending` is the single volatile publish point: the worker fully
    // builds the Outcome, then assigns it here; poll() reads it on the main thread.
    /** Test-only: the last JumpSpec handed to the solver, so tests can replay it on alternative models. */
    private volatile de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec lastSpecDebug;

    public de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec lastSpecDebug() {
        return lastSpecDebug;
    }

    private volatile boolean solving;
    private volatile long startNanos;
    private volatile Outcome pending;
    private volatile AtomicBoolean cancel;

    public AngleSolverEngine(AngleSolverState state, BoxController boxes, InputData inputs, IntConsumer onApplied, ForwardModel model) {
        this.state = state;
        this.boxes = boxes;
        this.inputs = inputs;
        this.onApplied = onApplied;
        this.model = model;
    }

    private static final class Plan {
        final int startTick;
        final double[] yaws;
        final boolean[] strafeMask;
        // Ticks solved under Force 45, snapshotted at solve time (not re-read at Apply time).
        final boolean[] force45Mask;
        final int strafeSign;
        // The model's predicted trajectory; Apply checks the resim against it (see checkApplyDeviation).
        final ForwardPath path;

        Plan(int startTick, double[] yaws, boolean[] strafeMask, boolean[] force45Mask, int strafeSign,
             ForwardPath path) {
            this.startTick = startTick;
            this.yaws = yaws;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.strafeSign = strafeSign;
            this.path = path;
        }
    }

    /** One in-segment UI constraint, snapshotted (copied) so the worker reads no live state. */
    private static final class ConstraintAt {
        final int absTick;
        final int segTick;
        final Constraint c;

        ConstraintAt(int absTick, int segTick, Constraint c) {
            this.absTick = absTick;
            this.segTick = segTick;
            this.c = c;
        }
    }

    /** Immutable problem snapshot handed to the worker thread. */
    private static final class Job {
        final JumpSpec spec;
        final Objective.Sense sense;
        final int startTick;
        final int landingTick;
        final int numTicks;
        final boolean[] strafeMask;
        final boolean[] force45Mask;
        final List<ConstraintAt> uiConstraints;
        final SolveCore.Budget budget;
        final long deadlineNanos;
        final LongRunSolver.LongRunConfig longRun;
        final boolean useWindowSolver;

        Job(JumpSpec spec, Objective.Sense sense, int startTick, int landingTick,
            int numTicks, boolean[] strafeMask, boolean[] force45Mask, List<ConstraintAt> uiConstraints,
            SolveCore.Budget budget, long deadlineNanos, LongRunSolver.LongRunConfig longRun, boolean useWindowSolver
        ) {
            this.spec = spec;
            this.sense = sense;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.uiConstraints = uiConstraints;
            this.budget = budget;
            this.deadlineNanos = deadlineNanos;
            this.longRun = longRun;
            this.useWindowSolver = useWindowSolver;
        }
    }

    private static final class Outcome {
        final SolveResult result;
        final Plan plan;
        // Block solve only: constraints to write into the table + the axis/goal to publish (null for a normal solve).
        final List<ConstraintAt> derived;
        final int derivedFrom;
        final int derivedTo;
        final AngleSolverState.Axis axis;
        final AngleSolverState.Goal goal;

        Outcome(SolveResult result, Plan plan) {
            this(result, plan, null, 0, 0, null, null);
        }

        Outcome(SolveResult result, Plan plan, List<ConstraintAt> derived, int derivedFrom, int derivedTo,
                AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
            this.result = result;
            this.plan = plan;
            this.derived = derived;
            this.derivedFrom = derivedFrom;
            this.derivedTo = derivedTo;
            this.axis = axis;
            this.goal = goal;
        }
    }

    // ---- solve (kick off on the main thread) ----------------------------------

    /** Build the immutable problem snapshot from the current UI state, on the caller (main) thread.
     *  Returns null and publishes a no-solution result when the tick range is invalid. Shared by
     *  {@link #solve()} and exposed (via {@link #debugBuildSpec()}) so tests can obtain the exact compiled
     *  spec without spawning the worker / triggering the slow fallback. */
    private Job buildJob() {
        int startTick = state.getStartTick();
        int landingTick = state.getLandingTick();
        int total = segmentConstraintCount(startTick, landingTick);

        List<InputRow> rows = inputs.getRows();
        int numTicks = landingTick - startTick;
        if (numTicks <= 0 || startTick < 0 || startTick >= boxes.size()
                || landingTick > rows.size() || startTick >= rows.size()) {
            state.setResult(new SolveResult(false, 0, total, startTick + 1, landingTick + 1));
            return null;
        }

        Phys ph = buildPhys(startTick, numTicks);
        List<ConstraintAt> uiCons = collectUiConstraints(startTick, numTicks);

        List<JumpConstraint> constraints = new ArrayList<>();
        Objective objective = new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks);
        for (ConstraintAt ca : uiCons) addMapped(constraints, ca.c, ca.absTick, ca.segTick, numTicks);

        JumpSpec spec = new JumpSpec(ph.inputs, constraints, objective);
        return new Job(spec, objective.sense, startTick, landingTick, numTicks, ph.strafeMask,
                ph.force45Mask, uiCons,
                budgetFor(state), deadlineNanosFor(state), longRunConfigFor(state), useWindowSolverFor(state));
    }

    /** Test-only: the compiled spec for the current UI state, built synchronously (no worker thread). */
    public JumpSpec debugBuildSpec() {
        Job job = buildJob();
        return job == null ? null : job.spec;
    }

    public void solve() {
        if (solving) return;
        Job job = buildJob();
        if (job == null) return; // invalid range: buildJob already published the failure result

        long t0 = System.nanoTime();
        // Show the spinner instead of a stale result.
        state.clearResult();
        lastPlan = null;
        pending = null;
        startNanos = t0;
        AtomicBoolean token = new AtomicBoolean(false);
        cancel = token;
        solving = true;
        Thread worker = new Thread(() -> {
            try {
                Outcome o = runJob(job, token);
                if (o != null && !token.get()) pending = o;
            } catch (Throwable t) {
                if (!token.get()) {
                    SolveResult fail = new SolveResult(false, 0, job.uiConstraints.size(),
                            job.startTick + 1, job.landingTick + 1);
                    pending = new Outcome(fail, null);
                }
            }
        }, "angle-solver");
        worker.setDaemon(true);
        worker.start();
    }

    /** Per-tick physics snapshot shared by solve() and the block solver. */
    private static final class Phys {
        final JumpPhysicsInputs inputs;
        final boolean[] strafeMask;
        final boolean[] force45Mask;
        final int jumpTickRel;

        Phys(JumpPhysicsInputs inputs, boolean[] strafeMask, boolean[] force45Mask, int jumpTickRel) {
            this.inputs = inputs;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.jumpTickRel = jumpTickRel;
        }
    }

    private Phys buildPhys(int startTick, int numTicks) {
        List<InputRow> rows = inputs.getRows();
        TickState seed = boxes.getState(startTick);
        int jumpTickRel = firstJumpTick(rows, startTick, numTicks);
        boolean[] strafeMask = new boolean[numTicks];
        boolean[] force45Mask = new boolean[numTicks];
        boolean[] jumpMask = new boolean[numTicks];
        boolean[] yawLocked = new boolean[numTicks];
        int[] speedAmp = new int[numTicks];
        double[] slipPerTick = new double[numTicks];
        float[] forwardIn = new float[numTicks];
        float[] strafeIn = new float[numTicks];
        boolean[] sprintArr = new boolean[numTicks];
        boolean deriveSprint = state.getDefaultSprint() == AngleSolverState.SprintMode.DERIVE;
        for (int k = 0; k < numTicks; k++) {
            int t = startTick + k;
            InputRow row = rows.get(t);
            boolean jumpRow = row.isKeyActive(InputRow.Key.JUMP);
            // Ground/air is hand-defined per tick via slipperiness: a ground value (< 1.0) is grounded, AIR
            // (the default) is airborne. No dynamic fallback to a recorded trajectory.
            double slip = slipValue(effSlipperiness(t));
            boolean ground = slip < 1.0;
            slipPerTick[k] = ground ? slip : Double.NaN;
            jumpMask[k] = jumpRow;
            force45Mask[k] = effInputs(t) == AngleSolverState.InputMode.FORCE_45;
            // W-only only on a real (grounded) jump, so the 0.2 sprintjump boost stays aligned with travel.
            strafeMask[k] = force45Mask[k] && !(jumpRow && ground);
            if (force45Mask[k]) {
                // Force 45 assumes W + sprint held (+A via the mask).
                forwardIn[k] = 1.0F * 0.98F;
                strafeIn[k] = 0.0F;
                sprintArr[k] = true;
            } else {
                // Keep ticks run what the sim actually ran: the post-tick movement sample carries the
                // version-exact moveFlying inputs (sneak scaling included) and, under Sprint: Derive, the
                // sprint flag (gh-120). Tick t's run is sampled into state t+1, same indexing as constraints.
                TickState sampled = boxes.getState(t + 1);
                if (sampled != null && sampled.hasMovementSample()) {
                    forwardIn[k] = sampled.moveForward;
                    strafeIn[k] = sampled.moveStrafe;
                    sprintArr[k] = !deriveSprint || sampled.sprinting;
                } else {
                    // No recorded run to sample: the rows' keys (gh-102) and the legacy sprint assumption.
                    forwardIn[k] = 0.98F * ((row.isKeyActive(InputRow.Key.W) ? 1 : 0) - (row.isKeyActive(InputRow.Key.S) ? 1 : 0));
                    strafeIn[k] = 0.98F * ((row.isKeyActive(InputRow.Key.A) ? 1 : 0) - (row.isKeyActive(InputRow.Key.D) ? 1 : 0));
                    sprintArr[k] = true;
                }
            }
            yawLocked[k] = row.isYawLocked();
            speedAmp[k] = effSpeedLevel(t);
        }
        if (deriveSprint) healWallHitSprint(startTick, numTicks, sprintArr, forwardIn);
        JumpPhysicsInputs phys = new JumpPhysicsInputs(numTicks);
        phys.startPos = seed.position;
        phys.startYaw = seed.yaw;
        phys.initialVelocity = seed.velocity;
        phys.jumpTick = jumpTickRel;
        phys.jumpPerTick = jumpMask;
        phys.strafePerTick = strafeMask;
        phys.speedAmplifier = speedAmp;
        phys.slipPerTick = slipPerTick;
        phys.yawLockedPerTick = yawLocked;
        phys.forwardInputPerTick = forwardIn;
        phys.strafeInputPerTick = strafeIn;
        phys.sprintPerTick = sprintArr;
        return new Phys(phys, strafeMask, force45Mask, jumpTickRel);
    }

    /** Sampled-forward floor that still sustains sprint: full/diagonal W passes, sneak-scaled W and released W stop. */
    private static final float SPRINT_SUSTAIN_F = 0.6F;

    /** Sprint lost to an in-window wall hit is healed while the inputs sustain it: the solve exists to route
     *  around that wall, so the broken run's post-hit sprint=false samples would doom the remaining jumps. */
    private void healWallHitSprint(int startTick, int numTicks, boolean[] sprint, float[] forwardIn) {
        boolean healing = false;
        for (int k = 1; k < numTicks; k++) {
            if (sprint[k]) { healing = false; continue; }
            if (!healing) {
                TickState hit = boxes.getState(startTick + k);
                healing = sprint[k - 1] && hit != null && hit.wallCollision && !hit.softCollision;
            }
            if (!healing) continue;
            if (forwardIn[k] < SPRINT_SUSTAIN_F) return;
            sprint[k] = true;
        }
    }

    private List<ConstraintAt> collectUiConstraints(int startTick, int numTicks) {
        List<ConstraintAt> uiCons = new ArrayList<>();
        for (Integer tickKey : state.populatedTicks()) {
            int absTick = tickKey;
            int segTick = absTick - startTick;
            if (segTick < 0 || segTick > numTicks) continue;
            TickConstraints tc = state.tickConstraintsOrNull(absTick);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled()) continue;
                uiCons.add(new ConstraintAt(absTick, segTick, c.copy()));
            }
        }
        return uiCons;
    }

    public void cancel() {
        if (!solving) return;
        AtomicBoolean token = cancel;
        if (token != null) token.set(true);
        pending = null;
        solving = false;
    }

    /** The problem was replaced under us (load/new session): kill the in-flight solve and drop the
     *  applied plan, or the old run's outcome lands on the new rows via poll()/apply(). */
    public void onProblemReplaced() {
        cancel();
        lastPlan = null;
    }

    /** Publish a finished background solve. Call every frame on the main thread. */
    public void poll() {
        Outcome o = pending;
        if (o == null) return;
        pending = null;
        if (o.derived != null) {
            // Block solve: replace the segment's constraints with the derived ones and publish the objective.
            state.clearConstraintsInRange(o.derivedFrom, o.derivedTo);
            for (ConstraintAt ca : o.derived) state.tickConstraints(ca.absTick).getConstraints().add(ca.c);
            if (o.axis != null) state.setAxis(o.axis);
            if (o.goal != null) state.setGoal(o.goal);
        }
        state.setResult(o.result);
        lastPlan = o.plan;
        solving = false;
    }

    public boolean isSolving() {
        return solving;
    }

    public double elapsedSeconds() {
        return solving ? (System.nanoTime() - startNanos) / 1.0e9 : 0.0;
    }

    /** Counts forward-model evaluations for the Details panel. Only wraps the search phases that take the
     *  interface (CMA-ES core, smoothing); the closed-form paths need the concrete ExactJumpModel and
     *  count their work internally, not per forward call. */
    private static final class CountingModel implements ForwardModel {
        final ForwardModel inner;
        final java.util.concurrent.atomic.AtomicLong evals = new java.util.concurrent.atomic.AtomicLong();

        CountingModel(ForwardModel inner) {
            this.inner = inner;
        }

        @Override
        public ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg) {
            evals.incrementAndGet();
            return inner.forward(scenario, yawAbsDeg);
        }
    }

    /** Runs entirely on the worker thread, reading only the immutable Job. */
    private Outcome runJob(Job job, AtomicBoolean cancel) {
        JumpSpec spec = job.spec;
        lastSpecDebug = spec;
        JumpPhysicsInputs sc = spec.asScenario();

        long solveStart = System.nanoTime();
        double[] yaws = null;
        // The chain of attempted solvers, e.g. "closed form -> CMA-ES" when the fast path fell through.
        String solverName = null;
        // True once the result needs no CMA-ES pass (optimal at a user cap, or a multi-jump span).
        boolean settled = false;
        // Diagnostic distance from the weak-duality bound (NaN when none): the bound holds for the
        // linearized model, not the game, so it can never settle a result — negative means the byte-exact
        // path out-reached the LP.
        double dualGap = Double.NaN;
        if (model instanceof ExactJumpModel) {
            ExactJumpModel em = (ExactJumpModel) model;
            if (countJumps(sc) <= 1) {
                // Single jump: closed form, else SLP on the user's own objective (a direction that
                // optimizes into a same-axis wall degenerates the dual's recovery; see
                // docs/research/angle-solver.md 2.1.1), reseeded from another direction's certified
                // optimum if its dual seed stalls. Both solvers are exact only for the linearized model,
                // whose reach the game can beat on swing-heavy jumps (2.1.3), so CMA-ES races every
                // result below unless a user cap on the objective axis at the objective tick — a bound
                // no path on any model can beat — certifies it.
                solverName = "closed form";
                yaws = ClosedFormSolve.optimize(em, spec, FEAS_TOL, cancel);
                if (yaws == null && !cancel.get()) {
                    yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel);
                    String slpName = "SLP";
                    if (yaws == null && !cancel.get()) {
                        for (Objective alt : alternateObjectives(spec.objective)) {
                            if (cancel.get()) return null;
                            double[] seed = ClosedFormSolve.optimize(em, new JumpSpec(sc, spec.constraints, alt), FEAS_TOL, cancel);
                            if (seed == null) continue;
                            yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel, seed);
                            if (yaws != null) {
                                slpName = "SLP (reseeded)";
                                break;
                            }
                        }
                    }
                    if (yaws != null) solverName = "closed form -> " + slpName;
                }
                if (yaws != null) {
                    double achieved = exactObjective(sc, spec, yaws);
                    double cap = objectiveCap(spec);
                    if (!Double.isNaN(cap)
                            && (spec.objective.sense == Objective.Sense.MAX ? cap - achieved : achieved - cap) <= CAP_GAP_TOL) {
                        settled = true;
                        solverName += ", optimal at constraint cap";
                    } else {
                        double bound = ClosedFormSolve.dualBound(spec);
                        if (!Double.isNaN(bound)) {
                            dualGap = spec.objective.sense == Objective.Sense.MAX ? bound - achieved : achieved - bound;
                        }
                    }
                }
            } else if (job.useWindowSolver) {
                // Multi-jump span: straight to the receding-horizon solver, because the monolithic dual does not
                // converge across dozens of jumps, so its full margin ladder over four directions would be
                // wasted on the whole span. Each window IS the closed-form dual, so a short span still
                // solves in one window. Reads no recorded trajectory: a run solves identically whether or
                // not a prior (possibly broken) path exists.
                solverName = "receding horizon";
                double[] fromScratch = LongRunSolver.solve(em, spec, FEAS_TOL, cancel, job.longRun);
                if (fromScratch != null) {
                    yaws = Angles.wrapAll(fromScratch);
                    settled = true;
                }
            }
        }
        if (cancel.get()) return null;
        CountingModel cmaes = new CountingModel(model);
        SolveCore.Budget budget = job.budget;
        if (!settled) {
            long deadline = job.deadlineNanos > 0 ? solveStart + job.deadlineNanos : 0L;
            double[] cma = SolveCore.optimize(cmaes, spec, budget, CMAES_SIGMA_DEG, FEAS_TOL, cancel, null, deadline);
            if (yaws == null) {
                yaws = cma;
                solverName = solverName == null ? "CMA-ES" : solverName + " -> CMA-ES";
            } else if (cma != null) {
                // Both are byte-exact feasible on the user's own objective; keep the better one.
                boolean max = spec.objective.sense == Objective.Sense.MAX;
                double slpObj = exactObjective(sc, spec, yaws);
                double cmaObj = exactObjective(sc, spec, cma);
                if (max ? cmaObj > slpObj : cmaObj < slpObj) {
                    yaws = cma;
                    solverName += " -> CMA-ES (better objective)";
                } else {
                    solverName += " (beat CMA-ES)";
                }
            }
        }
        if (yaws == null) return null;
        if (!settled) {
            // The race's winner can still be provably optimal: byte-exact paths polish to ~1e-10 of a
            // same-axis cap, so report that exactness when it happened.
            double cap = objectiveCap(spec);
            if (!Double.isNaN(cap)) {
                double achieved = exactObjective(sc, spec, yaws);
                if ((spec.objective.sense == Objective.Sense.MAX ? cap - achieved : achieved - cap) <= CAP_GAP_TOL) {
                    solverName += ", optimal at constraint cap";
                }
            }
        }
        // Gates inside keep byte-exact feasibility and the achieved objective; only the path's looks change.
        CountingModel smoothing = new CountingModel(model);
        yaws = SmoothingPolish.smooth(smoothing, spec, yaws, cancel);
        long solveNanos = System.nanoTime() - solveStart;

        // Every path produces absolute wrapped facings whose game-facing realization is toGameFacings(yaws)
        // (the chain Apply writes back as float deltas), so the reported path is bit-for-bit the applied one.
        double[] gameFacings = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gameFacings);
        SolveResult result = buildResult(job, yaws, gameFacings, path);
        result.setDurationNanos(solveNanos);
        result.setDurationMs(solveNanos / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setSolver(solverName);
        result.setObjective(path.getPos(spec.objective.tick, spec.objective.axis));
        result.getOutcomes().add(0, objectiveOutcome(result, spec.objective, job.startTick));
        addBaseDetails(result, solveNanos);
        if (!Double.isNaN(dualGap)) result.addDetail("Dual bound gap", ConstraintText.fixedStat(dualGap));
        result.addDetail("Jumps", Integer.toString(countJumps(sc)));
        if (countJumps(sc) > 1 && job.useWindowSolver) {
            result.addDetail("Window", Integer.toString(job.longRun.window()));
            result.addDetail("Commit", Integer.toString(job.longRun.commit()));
        }
        int locked = 0;
        if (sc.yawLockedPerTick != null) {
            for (boolean b : sc.yawLockedPerTick) if (b) locked++;
        }
        if (locked > 0) result.addDetail("Locked yaws", Integer.toString(locked));
        if (cmaes.evals.get() > 0) {
            result.addDetail("CMA-ES restarts", Integer.toString(budget.restarts));
            result.addDetail("CMA-ES max evals", Integer.toString(budget.maxEval));
            result.addDetail("CMA-ES evals", Long.toString(cmaes.evals.get()));
            result.addDetail("Polish basins", Integer.toString(budget.polishCount));
            if (job.deadlineNanos > 0) result.addDetail("Time budget", (job.deadlineNanos / 1_000_000_000L) + " s");
        }
        if (smoothing.evals.get() > 0) result.addDetail("Smoothing evals", Long.toString(smoothing.evals.get()));
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, job.force45Mask, 1, path);
        return new Outcome(result, plan);
    }

    /** The byte-exact objective value the given facings realize (for comparing two feasible candidates). */
    private double exactObjective(JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbsWrapped) {
        ForwardPath p = model.forward(sc, sc.toGameFacings(yawsAbsWrapped));
        return p.getPos(spec.objective.tick, spec.objective.axis);
    }

    /** The tightest same-axis position bound at the objective tick lying in the objective's improving
     *  direction (MAX: an LE wall, MIN: a GE wall), or NaN. Such a wall caps the objective for any path on
     *  any model, so a feasible result within {@link #CAP_GAP_TOL} of it is globally optimal — the only
     *  certificate strong enough to skip the byte-exact race (the solvers' own optima and the dual bound
     *  hold for the linearized model only). */
    private static double objectiveCap(JumpSpec spec) {
        Objective o = spec.objective;
        JumpConstraint.Mode mode = o.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        boolean max = o.sense == Objective.Sense.MAX;
        double cap = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != mode || c.t1 != o.tick || c.t2 != null) continue;
            if (c.cmp != (max ? JumpConstraint.Cmp.LE : JumpConstraint.Cmp.GE)) continue;
            if (Double.isNaN(cap) || (max ? c.rhs < cap : c.rhs > cap)) cap = c.rhs;
        }
        return cap;
    }

    /** The objective as the leading Solved-values row: axis @ tick, max/min as the relation, achieved value. */
    private static SolveResult.Outcome objectiveOutcome(SolveResult r, Objective o, int startTick) {
        String field = o.axis == JumpPhysicsInputs.Axis.X ? "X" : "Z";
        String sense = o.sense == Objective.Sense.MAX ? "max" : "min";
        return new SolveResult.Outcome(field, "T" + (startTick + o.tick + 1), sense,
                ConstraintText.fixedStat(r.getObjectiveValue()), "");
    }

    // The solver chain is not a detail row: the UI lists it in its own numbered section from getSolver().
    private void addBaseDetails(SolveResult r, long solveNanos) {
        r.addDetail("Runtime", ConstraintText.duration(solveNanos));
        r.addDetail("Finished", r.getFinishedAt());
        r.addDetail("Model", model.getClass().getSimpleName());
    }

    private static String formatClock() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    // ---- solve from blocks (off-thread) ----------------------------------------

    private static final double HALF = BoxStyle.HITBOX_HALF_WIDTH;
    /** Total inner solves the block solver may spend before giving up (and honestly reporting no solution). */
    private static final int BLOCK_MAX_ITERS = 40;

    private static final class BlockJob {
        final Phys ph;
        final List<JumpConstraint> footprints;
        final List<ConstraintAt> footprintUi;
        final double[] landFp;
        final List<BlockSolver.Obstacle> obstacles;
        final double[] heights;
        final List<Objective> objectives;
        final int startTick;
        final int landingTick;
        final int numTicks;
        final SolveCore.Budget budget;

        BlockJob(Phys ph, List<JumpConstraint> footprints, List<ConstraintAt> footprintUi, double[] landFp,
                 List<BlockSolver.Obstacle> obstacles, double[] heights, List<Objective> objectives, int startTick,
                 int landingTick, int numTicks, SolveCore.Budget budget) {
            this.ph = ph;
            this.footprints = footprints;
            this.footprintUi = footprintUi;
            this.landFp = landFp;
            this.obstacles = obstacles;
            this.heights = heights;
            this.objectives = objectives;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.budget = budget;
        }
    }

    /** Solves the puzzle defined by the picked start / collision / land blocks: footprints pin the launch
     *  and landing, and {@link BlockSolver} derives the per-tick keep-out walls that wrap the obstacles.
     *  The objective is the user's Axis + Goal. Off-thread. */
    public void solveFromBlocks() {
        if (solving) return;
        int startTick = state.getStartTick();
        int landingTick = state.getLandingTick();
        int numTicks = landingTick - startTick;
        List<InputRow> rows = inputs.getRows();
        BlockSelection land = state.getLandBlock();
        if (numTicks <= 0 || startTick < 0 || startTick >= boxes.size()
                || landingTick > rows.size() || startTick >= rows.size() || land == null) {
            state.setResult(new SolveResult(false, 0, 0, startTick + 1, landingTick + 1));
            return;
        }

        Phys ph = buildPhys(startTick, numTicks);
        int jumpAbs = ph.jumpTickRel < 0 ? -1 : startTick + ph.jumpTickRel;

        List<JumpConstraint> footprints = new ArrayList<>();
        List<ConstraintAt> footprintUi = new ArrayList<>();
        addFootprint(footprints, footprintUi, numTicks, startTick, land.box);
        double[] landFp = expand(land.box);

        BlockSelection start = state.getStartBlock();
        if (start != null && jumpAbs > startTick) {
            addFootprint(footprints, footprintUi, (jumpAbs - 1) - startTick, startTick, start.box);
        }

        List<BlockSolver.Obstacle> obstacles = new ArrayList<>();
        for (BlockSelection c : state.getCollisionBlocks()) {
            obstacles.add(new BlockSolver.Obstacle(c.box));
        }
        double[] heights = new double[numTicks + 1];
        for (int st = 0; st <= numTicks; st++) {
            TickState s = boxes.getState(startTick + st);
            heights[st] = (s != null && s.sneaking) ? BoxStyle.HITBOX_HEIGHT_SNEAKING : BoxStyle.HITBOX_HEIGHT_STANDING;
        }

        List<Objective> objectives = objectiveCandidates(numTicks);

        long t0 = System.nanoTime();
        BlockJob job = new BlockJob(ph, footprints, footprintUi, landFp, obstacles, heights, objectives,
                startTick, landingTick, numTicks, budgetFor(state));
        state.clearResult();
        lastPlan = null;
        pending = null;
        startNanos = t0;
        AtomicBoolean token = new AtomicBoolean(false);
        cancel = token;
        solving = true;
        Thread worker = new Thread(() -> {
            try {
                Outcome o = runBlockJob(job, token);
                if (o != null && !token.get()) pending = o;
            } catch (Throwable t) {
                if (!token.get()) {
                    pending = new Outcome(new SolveResult(false, 0, 0, job.startTick + 1, job.landingTick + 1), null);
                }
            }
        }, "angle-block-solver");
        worker.setDaemon(true);
        worker.start();
    }

    private Outcome runBlockJob(BlockJob job, AtomicBoolean cancel) {
        long solveStart = System.nanoTime();
        BlockSolver.Result r = new BlockSolver().solve(model, job.ph.inputs, job.footprints, job.landFp,
                job.obstacles, job.heights, job.objectives, job.budget, CMAES_SIGMA_DEG, FEAS_TOL, BLOCK_MAX_ITERS, cancel);
        long solveNanos = System.nanoTime() - solveStart;
        if (cancel.get() || r == null || r.yaws == null) return null;

        List<ConstraintAt> derived = new ArrayList<>(job.footprintUi);
        for (BlockSolver.Face f : r.faces) {
            int absTick = job.startTick + f.segTick;
            Constraint c = Constraint.scalar(f.axisX ? Constraint.Field.X : Constraint.Field.Z,
                    f.upper ? Constraint.Op.GE : Constraint.Op.LE, f.value);
            derived.add(new ConstraintAt(absTick, f.segTick, c));
        }

        SolveResult result = buildBlockResult(job, r.yaws, job.ph.inputs.toGameFacings(r.yaws), r.path, derived, r.ok());
        result.setDurationNanos(solveNanos);
        result.setDurationMs(solveNanos / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setSolver("block solver");
        result.setObjective(r.path.getPos(r.objective.tick, r.objective.axis));
        result.getOutcomes().add(0, objectiveOutcome(result, r.objective, job.startTick));
        addBaseDetails(result, solveNanos);
        result.addDetail("Derived walls", Integer.toString(r.faces.size()));
        Plan plan = new Plan(job.startTick, r.yaws, job.ph.strafeMask, job.ph.force45Mask, 1, r.path);
        AngleSolverState.Axis ax = r.objective.axis == JumpPhysicsInputs.Axis.X ? AngleSolverState.Axis.X : AngleSolverState.Axis.Z;
        AngleSolverState.Goal gl = r.objective.sense == Objective.Sense.MAX ? AngleSolverState.Goal.MAX : AngleSolverState.Goal.MIN;
        return new Outcome(result, plan, derived, job.startTick, job.landingTick, ax, gl);
    }

    private SolveResult buildBlockResult(BlockJob job, double[] yaws, double[] gameFacings, ForwardPath path,
                                         List<ConstraintAt> derived, boolean ok) {
        int total = 0;
        int met = 0;
        List<SolveResult.Outcome> outs = new ArrayList<>();
        List<ConstraintAt> ordered = new ArrayList<>(derived);
        ordered.sort((a, b) -> Integer.compare(a.absTick, b.absTick));
        for (ConstraintAt ca : ordered) {
            Double found = findValue(ca.c, ca.segTick, job.numTicks, gameFacings, path);
            if (found == null) continue;
            total++;
            if (satisfied(ca.c, found)) met++;
            outs.add(outcome(ca.c, ca.absTick, found));
        }
        SolveResult r = new SolveResult(ok, met, total, job.startTick + 1, job.landingTick + 1);
        r.getOutcomes().addAll(outs);
        for (int k = 0; k < yaws.length; k++) r.getYaws().add(new SolveResult.YawEntry(job.startTick + k + 1, yaws[k]));
        return r;
    }

    private void addFootprint(List<JumpConstraint> fps, List<ConstraintAt> ui, int segTick, int startTick, AABB box) {
        if (segTick < 0) return;
        double[] e = expand(box);
        fps.add(new JumpConstraint(JumpConstraint.Mode.X, segTick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, e[0], "fpXlo"));
        fps.add(new JumpConstraint(JumpConstraint.Mode.X, segTick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, e[1], "fpXhi"));
        fps.add(new JumpConstraint(JumpConstraint.Mode.Z, segTick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, e[2], "fpZlo"));
        fps.add(new JumpConstraint(JumpConstraint.Mode.Z, segTick, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, e[3], "fpZhi"));
        int absTick = startTick + segTick;
        ui.add(new ConstraintAt(absTick, segTick, Constraint.range(Constraint.Field.X, e[0], e[1], true, true)));
        ui.add(new ConstraintAt(absTick, segTick, Constraint.range(Constraint.Field.Z, e[2], e[3], true, true)));
    }

    /** [xlo, xhi, zlo, zhi] keep-out / footprint region: the block's horizontal AABB plus the half-width. */
    private static double[] expand(AABB box) {
        return new double[] {box.min.x - HALF, box.max.x + HALF, box.min.z - HALF, box.max.z + HALF};
    }

    /** The single objective the user picked (Axis + Goal). The block solver derives the keep-out
     *  constraints; the user decides which coordinate to optimize, rather than the tool auto-choosing. */
    private List<Objective> objectiveCandidates(int numTicks) {
        return java.util.Collections.singletonList(
                new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks));
    }

    // ---- apply (main thread) --------------------------------------------------

    public void apply() {
        if (lastPlan == null) return;
        Plan p = lastPlan;
        List<InputRow> rows = inputs.getRows();
        if (p.startTick < 0 || p.startTick >= rows.size()) return;
        double prevAbs = boxes.getYaw(p.startTick);
        for (int k = 0; k < p.yaws.length && p.startTick + k < rows.size(); k++) {
            InputRow row = rows.get(p.startTick + k);
            double abs = p.yaws[k];
            if (row.isYawLocked()) {
                row.setYaw((float) abs);
            } else {
                double delta = abs - prevAbs;
                delta = Angles.wrapDelta(delta);
                row.setYaw((float) delta);
            }
            if (p.force45Mask[k]) {
                // A Force-45 tick realizes its solve assumption in the rows (gh-104): W + sprint held
                // on every tick, strafe per the mask (the grounded jump tick stays W-only). Keep ticks
                // are left alone; their keys ARE what the solve ran.
                boolean strafeThis = p.strafeMask[k];
                row.setKeyActive(InputRow.Key.W, true);
                row.setKeyActive(InputRow.Key.SPRINT, true);
                row.setKeyActive(InputRow.Key.A, strafeThis && p.strafeSign > 0);
                row.setKeyActive(InputRow.Key.D, strafeThis && p.strafeSign < 0);
            }
            prevAbs = abs;
        }
        // Full resim (-1), not simulateFrom(startTick): the partial path restores an incomplete
        // checkpoint, so resuming mid-path can pick up stale entity state. Apply is one-shot, so the
        // cost of a clean run is irrelevant.
        onApplied.accept(-1);
        checkApplyDeviation(p);
    }

    /** Per-tick displacement tolerance. The 1.21.10 model is bit-exact to the sim (a clean tick differs by
     *  exactly 0.0), so this only guards versions without a proven model; per-tick comparison localizes the
     *  offending tick. Tight enough to catch even soft (sprint-keeping) grazes. */
    private static final double APPLY_MATCH_TOL = 1.0e-9;

    /** The resim left the solved path, so the sim did something the collision-free model could not see
     *  (a wall hit, usually) and every outcome from that tick on is void. Publishes the message and its
     *  cause into the state; clears both when the resim matches the plan.
     *  X/Z only: the model's posY is not physical (never clamped onto a surface), so Y always drifts. */
    private void checkApplyDeviation(Plan p) {
        if (p.path != null) {
            for (int k = 1; k <= p.yaws.length; k++) {
                int t = p.startTick + k;
                if (t >= boxes.size()) break;
                TickState s = boxes.getState(t);
                TickState prev = boxes.getState(t - 1);
                if (s == null || prev == null) break;
                double dx = (s.position.x - prev.position.x) - (p.path.posX[k] - p.path.posX[k - 1]);
                double dz = (s.position.z - prev.position.z) - (p.path.posZ[k] - p.path.posZ[k - 1]);
                if (Math.abs(dx) <= APPLY_MATCH_TOL && Math.abs(dz) <= APPLY_MATCH_TOL) continue;
                publishDeviation(p.startTick, t);
                return;
            }
        }
        state.setApplyDeviation(null, null);
    }

    /** Ticks scanned back from the deviation for a SNEAK row: the slowdown lands a tick late and the
     *  forced-crouch pose can outlive the key by a few ticks. */
    private static final int SNEAK_DESYNC_LOOKBACK = 5;

    private void publishDeviation(int startTick, int t) {
        String head = "Sim left the solved path at T" + (t + 1);
        String tail = ". Re-solving from this run might fix it.";
        for (int i = startTick + 1; i <= t; i++) {
            TickState c = boxes.getState(i);
            if (c != null && c.wallCollision) {
                state.setApplyDeviation(head + ": it hit a wall the solve cannot see. Add a constraint to route around it.",
                        AngleSolverState.DeviationKind.WALL);
                return;
            }
        }
        List<InputRow> rows = inputs.getRows();
        for (int r = t; r >= Math.max(startTick, t - SNEAK_DESYNC_LOOKBACK); r--) {
            if (r < rows.size() && rows.get(r).isKeyActive(InputRow.Key.SNEAK)) {
                state.setApplyDeviation(head + ": the sneak at T" + (r + 1)
                        + " ran at a different position in the sampled run" + tail,
                        AngleSolverState.DeviationKind.SNEAK);
                return;
            }
        }
        state.setApplyDeviation(head + tail, AngleSolverState.DeviationKind.OTHER);
    }

    // ---- effective per-tick state (main thread, during snapshot) --------------

    private AngleSolverState.InputMode effInputs(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesInputs()) return ov.getInputs();
        return state.getDefaultInputs();
    }

    private Slipperiness effSlipperiness(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesSlipperiness()) return ov.getSlipperiness();
        return state.getDefaultSlipperiness();
    }

    /** Effective Speed amplifier at a tick: override added/removed over the default potions. */
    private int effSpeedLevel(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null) {
            PotionDose added = ov.findAdded(Potion.SPEED);
            if (added != null) return added.level;
            if (ov.getRemoved().contains(Potion.SPEED)) return 0;
        }
        for (PotionDose d : state.getDefaultPotions()) {
            if (d.potion == Potion.SPEED) return d.level;
        }
        return 0;
    }

    private StateOverride overrideAt(int tick) {
        TickConstraints tc = state.tickConstraintsOrNull(tick);
        return tc == null ? null : tc.getOverride();
    }

    private static double slipValue(Slipperiness s) {
        return Double.parseDouble(s.valueLabel);
    }

    // ---- constraint mapping (UI Constraint -> solver JumpConstraint) -----------

    private void addMapped(List<JumpConstraint> out, Constraint c, int absTick, int segTick, int numTicks) {
        String tag = c.getField().label + "@" + absTick;
        switch (c.getField()) {
            case X:
                addScalarOrRange(out, JumpConstraint.Mode.X, segTick, c, tag);
                break;
            case Z:
                addScalarOrRange(out, JumpConstraint.Mode.Z, segTick, c, tag);
                break;
            case F:
                if (segTick >= numTicks) break; // no facing for the post-final state
                addScalarOrRange(out, JumpConstraint.Mode.F, segTick, c, tag);
                break;
            case DX:
                if (segTick < 1) break; // velocity needs t-1
                addVelocity(out, JumpConstraint.Mode.X, segTick, c, tag);
                break;
            case DZ:
                if (segTick < 1) break;
                addVelocity(out, JumpConstraint.Mode.Z, segTick, c, tag);
                break;
        }
    }

    /** Scalar X/Z/F (one constraint) or a position range (a GE/LE pair). Single-tick, same-axis, additive. */
    private void addScalarOrRange(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            // EQ as a +-MET_TOL corridor. A byte-exact equality to a typed target is unattainable on the
            // sine-bucket lattice, so a solver-side equality could never certify on the closed form nor
            // count as feasible for the polish (FEAS_TOL is 0), so EQ specs silently lost the fast path and
            // were never polished. The panel already reports EQ as met within MET_TOL, so enforce exactly
            // that band as two strict walls; the F-mode wrap in evaluate() keeps the corridor correct
            // across the +-180 seam.
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, cmp(c.getOp()), c.getValue(), tag));
        }
    }

    /** Velocity (dX/dZ): pos[t1]-pos[t1-1] against a range (GE/LE pair), an equality (the same
     *  +-MET_TOL corridor as scalar fields, see addScalarOrRange), or a single comparison wall. */
    private void addVelocity(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, t1 - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(mode, t1, t1 - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            out.add(new JumpConstraint(mode, t1, t1 - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(mode, t1, t1 - 1, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(mode, t1, t1 - 1, JumpConstraint.Op.MINUS, cmp(c.getOp()), c.getValue(), tag));
        }
    }

    private static JumpConstraint.Cmp cmp(Constraint.Op op) {
        switch (op) {
            case LT:
            case LE:
                return JumpConstraint.Cmp.LE;
            case EQ:
                return JumpConstraint.Cmp.EQ;
            default: // GT, GE
                return JumpConstraint.Cmp.GE;
        }
    }

    // ---- result panel (worker thread, from the Job snapshot) ------------------

    private SolveResult buildResult(Job job, double[] yaws, double[] gameFacings, ForwardPath path) {
        int total = 0;
        int met = 0;
        List<SolveResult.Outcome> outs = new ArrayList<>();
        List<ConstraintAt> ordered = new ArrayList<>(job.uiConstraints);
        ordered.sort((a, b) -> Integer.compare(a.absTick, b.absTick));
        for (ConstraintAt ca : ordered) {
            Double found = findValue(ca.c, ca.segTick, job.numTicks, gameFacings, path);
            if (found == null) continue; // unmappable, e.g. velocity on tick 0
            total++;
            if (satisfied(ca.c, found)) met++;
            outs.add(outcome(ca.c, ca.absTick, found));
        }
        SolveResult r = new SolveResult(met == total, met, total, job.startTick + 1, job.landingTick + 1);
        r.getOutcomes().addAll(outs);
        for (int k = 0; k < yaws.length; k++) {
            r.getYaws().add(new SolveResult.YawEntry(job.startTick + k + 1, yaws[k]));
        }
        return r;
    }

    /** The value a constraint is judged against. F reads the GAME facing (what the solver enforced and the
     *  sim runs), wrapped for display; the wrapped-abs plan yaw differs from it by float accumulation,
     *  which the strict wall gate would mis-report on a hugged facing wall. */
    private Double findValue(Constraint c, int segTick, int numTicks, double[] gameFacings, ForwardPath path) {
        switch (c.getField()) {
            case X: return path.posX[segTick];
            case Z: return path.posZ[segTick];
            case F: return segTick < numTicks ? Angles.wrap(gameFacings[segTick]) : null;
            case DX: return segTick >= 1 ? path.posX[segTick] - path.posX[segTick - 1] : null;
            case DZ: return segTick >= 1 ? path.posZ[segTick] - path.posZ[segTick - 1] : null;
            default: return null;
        }
    }

    private boolean satisfied(Constraint c, double found) {
        if (c.isRange()) {
            boolean lo = c.isLoInclusive() ? found >= c.getLo() - MET_TOL : found > c.getLo() - MET_TOL;
            boolean hi = c.isHiInclusive() ? found <= c.getHi() + MET_TOL : found < c.getHi() + MET_TOL;
            return lo && hi;
        }
        double v = c.getValue();
        // Facings are angular: compare the wrapped difference so e.g. -179 satisfies a +179 target.
        double f = c.getField() == Constraint.Field.F ? v + Angles.wrap(found - v) : found;
        // Walls (the inequalities) are gated strictly at FEAS_TOL, so "Solved" never counts a clip as met;
        // only the exact target (=) keeps MET_TOL, since a single facing/position bucket is never hit to the bit.
        switch (c.getOp()) {
            case GT: return f > v - FEAS_TOL;
            case GE: return f >= v - FEAS_TOL;
            case LT: return f < v + FEAS_TOL;
            case LE: return f <= v + FEAS_TOL;
            case EQ: return Math.abs(f - v) <= MET_TOL;
            default: return false;
        }
    }

    private SolveResult.Outcome outcome(Constraint c, int absTick, double found) {
        String field = c.getField().label;
        String tickLabel = "T" + (absTick + 1);
        if (c.isRange()) {
            return new SolveResult.Outcome(field, tickLabel, ConstraintText.chip(c), ConstraintText.fixedStat(found), "");
        }
        double v = c.getValue();
        String relation = c.getOp().glyph + " " + ConstraintText.num(v);
        double diff = c.getField() == Constraint.Field.F ? Angles.wrap(found - v) : (found - v);
        double margin;
        switch (c.getOp()) {
            case LT:
            case LE:
                margin = -diff;
                break;
            case EQ:
                margin = 0.0;
                break;
            default: // GT, GE
                margin = diff;
                break;
        }
        String marginStr = c.getOp() == Constraint.Op.EQ ? "" : (margin >= 0 ? "+" : "") + ConstraintText.fixedStat(margin);
        return new SolveResult.Outcome(field, tickLabel, relation, ConstraintText.fixedStat(found), marginStr);
    }

    // ---- helpers --------------------------------------------------------------

    private int segmentConstraintCount(int startTick, int landingTick) {
        int n = 0;
        for (Integer tickKey : state.populatedTicks()) {
            int seg = tickKey - startTick;
            if (seg < 0 || seg > landingTick - startTick) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) if (c.isEnabled()) n++;
        }
        return n;
    }

    /** Number of distinct jumps in the span: rising edges of a grounded JUMP press. Used to gate the
     *  long-run feasibility fallback to genuine multi-jump spans (single jumps stay on the fast path). */
    private static int countJumps(JumpPhysicsInputs sc) {
        int count = 0;
        boolean prev = false;
        for (int t = 0; t < sc.numTicks; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boolean jump = sc.jumpAt(t) && grounded;
            if (jump && !prev) count++;
            prev = jump;
        }
        return count;
    }

    private static int firstJumpTick(List<InputRow> rows, int startTick, int numTicks) {
        for (int k = 0; k < numTicks && startTick + k < rows.size(); k++) {
            if (rows.get(startTick + k).isKeyActive(InputRow.Key.JUMP)) return k;
        }
        return -1;
    }

    private static JumpPhysicsInputs.Axis axis(AngleSolverState.Axis a) {
        return a == AngleSolverState.Axis.X ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
    }

    private static Objective.Sense sense(AngleSolverState.Goal g) {
        return g == AngleSolverState.Goal.MAX ? Objective.Sense.MAX : Objective.Sense.MIN;
    }

    /** The other three Solve-For directions at the same tick, user's axis first. Seed sources only
     *  (a certified optimum of any direction is feasible for all of them), never the returned result. */
    private static List<Objective> alternateObjectives(Objective o) {
        List<Objective> out = new ArrayList<>(3);
        JumpPhysicsInputs.Axis[] axisOrder = (o.axis == JumpPhysicsInputs.Axis.X)
                ? new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.X, JumpPhysicsInputs.Axis.Z}
                : new JumpPhysicsInputs.Axis[]{JumpPhysicsInputs.Axis.Z, JumpPhysicsInputs.Axis.X};
        for (JumpPhysicsInputs.Axis ax : axisOrder) {
            for (Objective.Sense se : new Objective.Sense[]{Objective.Sense.MAX, Objective.Sense.MIN}) {
                if (ax == o.axis && se == o.sense) continue;
                out.add(new Objective(ax, se, o.tick));
            }
        }
        return out;
    }
}
