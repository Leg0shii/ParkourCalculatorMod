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
    private static final double MET_TOL = 1.0e-3;

    /** CMA-ES initial step (deg). With the wider-than-one-turn search bounds the global basin is a
     *  single continuous region, so a moderate sigma finds it in a handful of restarts. Only one strafe
     *  sign is solved: A and D are mirror-symmetric (flip the sign and shift air-tick facings by 90deg
     *  for an identical trajectory), so the optimal objective is the same either way. */
    private static final double CMAES_SIGMA_DEG = 90.0;

    /** Per-effort solve budget (see {@link SolveCore}). Fewer restarts/evals is faster but can miss a
     *  feasible basin on a hard jump, so FAST trades robustness for ~100ms. */
    private static SolveCore.Budget budgetFor(AngleSolverState.Effort effort) {
        switch (effort) {
            case FAST: return new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);
            case THOROUGH: return new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
            default: return new SolveCore.Budget(28, 7000, 4, BucketAscentPolish.BALANCED);
        }
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
        // Ticks solved under Force 45 (snapshot from the solve, not re-read at Apply time): these get
        // the assumption written back as keys -- W + sprint, strafe per the mask (gh-104).
        final boolean[] force45Mask;
        final int strafeSign;

        Plan(int startTick, double[] yaws, boolean[] strafeMask, boolean[] force45Mask, int strafeSign) {
            this.startTick = startTick;
            this.yaws = yaws;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.strafeSign = strafeSign;
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
        final AngleSolverState.Effort effort;

        Job(JumpSpec spec, Objective.Sense sense, int startTick, int landingTick,
            int numTicks, boolean[] strafeMask, boolean[] force45Mask, List<ConstraintAt> uiConstraints,
            AngleSolverState.Effort effort
        ) {
            this.spec = spec;
            this.sense = sense;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.uiConstraints = uiConstraints;
            this.effort = effort;
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

        // Freeze the problem on the main thread so the worker reads a genuinely read-only snapshot.
        JumpSpec spec = new JumpSpec(ph.inputs, constraints, objective);
        return new Job(spec, objective.sense, startTick, landingTick, numTicks, ph.strafeMask,
                ph.force45Mask, uiCons, state.getEffort());
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
        // Show the spinner instead of a stale result, then run off-thread.
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
                // Force 45 assumes W + sprint held (+A via the mask); Apply writes these keys back to the rows.
                forwardIn[k] = 1.0F * 0.98F;
                strafeIn[k] = 0.0F;
                sprintArr[k] = true;
            } else {
                // Keep ticks run what the sim actually ran: the post-tick movement sample carries the
                // version-exact moveFlying inputs (sneak scaling included) and the sprint flag (gh-120).
                // Tick t's run is sampled into state t+1, same indexing as constraints.
                TickState sampled = boxes.getState(t + 1);
                if (sampled != null && sampled.hasMovementSample()) {
                    forwardIn[k] = sampled.moveForward;
                    strafeIn[k] = sampled.moveStrafe;
                    sprintArr[k] = sampled.sprinting;
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

    private List<ConstraintAt> collectUiConstraints(int startTick, int numTicks) {
        List<ConstraintAt> uiCons = new ArrayList<>();
        for (Integer tickKey : state.populatedTicks()) {
            int absTick = tickKey;
            int segTick = absTick - startTick;
            if (segTick < 0 || segTick > numTicks) continue;
            TickConstraints tc = state.tickConstraintsOrNull(absTick);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled()) continue; // disabled constraints are invisible to the solve (gh-118)
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

    /** Publish a finished background solve. Call every frame on the main thread. */
    public void poll() {
        Outcome o = pending;
        if (o == null) return;
        pending = null;
        if (o.derived != null) {
            // Block solve: author the segment from the constraints the cutting plane found, set the objective.
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

    /** Runs entirely on the worker thread, reading only the immutable Job. CMA-ES restarts are mutually
     *  independent and CPU-bound, so they run in parallel; then the most promising feasible basins are
     *  polished in parallel and the best kept. Single strafe sign (+1 = A); A and D are mirror-symmetric
     *  (flip the sign and shift air facings 90deg for an identical trajectory). Budget scales with effort. */
    private Outcome runJob(Job job, AtomicBoolean cancel) {
        JumpSpec spec = job.spec; // frozen on the main thread; arrays read-only during solve, so shareable
        lastSpecDebug = spec;
        JumpPhysicsInputs sc = spec.asScenario();

        // yaws are absolute wrapped facings (what Apply writes as deltas); the game runs the float-accumulated
        // facings, so the reported path forwards toGameFacings(yaws), bit-for-bit the in-game trajectory.
        // Closed-form path first: the dual costate solver finds the globally-optimal facings in microseconds
        // for position-wall jumps (the common case). It returns null when it does not apply (facing/equality
        // walls) or cannot certify byte-exact feasibility, in which case we fall back to the full cold
        // multistart so reliability is never reduced.
        long solveStart = System.nanoTime();
        double[] yaws = null;
        if (model instanceof ExactJumpModel) {
            ExactJumpModel em = (ExactJumpModel) model;
            if (countJumps(sc) <= 1) {
                // SINGLE jump -- the closed-form dual finds the globally-optimal facings in microseconds (the
                // common case, the <0.1 ms fast path). Feasibility does NOT depend on the Solve-For direction:
                // the closed form only certifies the objective's optimal vertex, so for some directions that
                // vertex is byte-exact-infeasible and it returns null even though another direction on the SAME
                // constraints certifies cleanly -- so try the other directions before giving up.
                yaws = ClosedFormSolve.optimize(em, spec, FEAS_TOL, cancel);
                if (yaws == null && !cancel.get()) {
                    for (Objective alt : alternateObjectives(spec.objective)) {
                        if (cancel.get()) return null;
                        double[] altYaws = ClosedFormSolve.optimize(em, new JumpSpec(sc, spec.constraints, alt), FEAS_TOL, cancel);
                        if (altYaws != null) { yaws = altYaws; break; }
                    }
                }
            } else {
                // MULTI-jump span -- solve it FROM SCRATCH with the receding-horizon solver (each window IS the
                // closed-form dual, so a short span is solved in one window exactly as the closed form would,
                // and a long span slides). The monolithic dual does not converge across dozens of jumps, so
                // there is no point spending its full margin ladder over four directions on the whole span
                // first; go straight to the decomposition. It reads no recorded trajectory, so a run solves
                // identically whether or not a prior (possibly broken) path exists. The solver certified the
                // replay toGameFacings(wrapAll(gf)) -- exactly what the standard reporting below recomputes
                // and what Apply realizes -- so the wrapped facings flow through the normal path.
                double[] fromScratch = LongRunSolver.solve(em, spec, FEAS_TOL, cancel);
                if (fromScratch != null) { yaws = Angles.wrapAll(fromScratch); }
            }
        }
        if (cancel.get()) return null;
        if (yaws == null) {
            yaws = SolveCore.optimize(model, spec, budgetFor(job.effort), CMAES_SIGMA_DEG, FEAS_TOL, cancel);
        }
        if (yaws == null) return null;
        // Underdetermined solves: iron the free ticks toward their neighbors. The gates inside keep
        // byte-exact feasibility and the achieved objective, so only the path's looks can change.
        yaws = SmoothingPolish.smooth(model, spec, yaws, cancel);
        long solveNanos = System.nanoTime() - solveStart;

        // Every path produces absolute wrapped facings whose game-facing realization is toGameFacings(yaws)
        // -- the chain Apply writes back as float deltas -- so the reported path is bit-for-bit the applied one.
        double[] gameFacings = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gameFacings);
        SolveResult result = buildResult(job, yaws, gameFacings, path);
        result.setDurationNanos(solveNanos);
        result.setDurationMs(solveNanos / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setObjective(path.getPos(spec.objective.tick, spec.objective.axis));
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, job.force45Mask, 1);
        return new Outcome(result, plan);
    }

    private static String formatClock() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    // ---- solve from blocks (cutting plane, off-thread) ------------------------

    private static final double HALF = BoxStyle.HITBOX_HALF_WIDTH;
    /** Total solves the cutting-plane DFS may spend before giving up (and honestly reporting no solution). */
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
        final AngleSolverState.Effort effort;

        BlockJob(Phys ph, List<JumpConstraint> footprints, List<ConstraintAt> footprintUi, double[] landFp,
                 List<BlockSolver.Obstacle> obstacles, double[] heights, List<Objective> objectives, int startTick,
                 int landingTick, int numTicks, AngleSolverState.Effort effort) {
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
            this.effort = effort;
        }
    }

    /** Solves the puzzle defined by the picked start / collision / land blocks: footprints pin the launch
     *  and landing, and a cutting plane adds keep-out walls only where the solved path actually collides,
     *  warm-started each round. The objective (reach toward an open footprint edge) is auto-chosen. Off-thread. */
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
                startTick, landingTick, numTicks, state.getEffort());
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
                job.obstacles, job.heights, job.objectives, blockBudget(job.effort), CMAES_SIGMA_DEG, FEAS_TOL, BLOCK_MAX_ITERS, cancel);
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
        result.setObjective(r.path.getPos(r.objective.tick, r.objective.axis));
        Plan plan = new Plan(job.startTick, r.yaws, job.ph.strafeMask, job.ph.force45Mask, 1);
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

    /** Block solving uses the SAME per-effort budget as the normal Solve, so "Solve from blocks" never
     *  searches weaker than the "Solve" that runs on the constraints it just derived (otherwise the block
     *  solve can report no-solution and a follow-up Solve then finds it on the identical constraints). */
    private static SolveCore.Budget blockBudget(AngleSolverState.Effort effort) {
        return budgetFor(effort);
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
                // are left alone -- their keys ARE what the solve ran.
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
            // count as feasible for the polish (FEAS_TOL is 0) -- EQ specs silently lost the fast path and
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
        ordered.sort((a, b) -> Integer.compare(a.absTick, b.absTick)); // panel lists constraints first-tick-first
        for (ConstraintAt ca : ordered) {
            Double found = findValue(ca.c, ca.segTick, job.numTicks, gameFacings, path);
            if (found == null) continue; // unmappable (e.g. velocity on tick 0)
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
     *  sim runs), wrapped for display -- the wrapped-abs plan yaw differs from it by float accumulation,
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
            return new SolveResult.Outcome(field, tickLabel, ConstraintText.chip(c), ConstraintText.fixed7(found), "");
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
        String marginStr = c.getOp() == Constraint.Op.EQ ? "" : (margin >= 0 ? "+" : "") + ConstraintText.fixed7(margin);
        return new SolveResult.Outcome(field, tickLabel, relation, ConstraintText.fixed7(found), marginStr);
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

    /** The other three Solve-For directions at the same tick, ordered to prefer the user's axis (so a
     *  feasibility fallback returns a solution on the axis they care about when possible). Used only to find
     *  ANY feasible landing when the user's own direction cannot be certified -- feasibility is
     *  objective-independent, so if one direction solves, all of them should report a solution. */
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
