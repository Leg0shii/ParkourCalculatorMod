package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphFactory;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunLog;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LevelSetAscent;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Bridges the Angle Solver UI to the byte-exact jump model and back into the live TAS.
 *
 * <p>Threading: {@link #solve()} snapshots the whole problem on the caller (main) thread into an
 * immutable {@link Job}, then runs the solve on a daemon thread so the game never stalls.
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

    private static final long RELAX_MIN_REMAINING_NANOS = 3_000_000_000L;

    static long deadlineNanosFor(AngleSolverState state) {
        if (state.isBruteForceActive()) return 0L;
        switch (state.getEffort()) {
            case THOROUGH: return state.getOptimizeSeconds() * 1_000_000_000L;
            case CUSTOM: {
                int secs = state.getSolveBudget().getTimeBudgetSeconds();
                return secs > 0 ? secs * 1_000_000_000L : 0L;
            }
            default: return 0L;
        }
    }

    static LongRunSolver.LongRunConfig longRunConfigFor(AngleSolverState state) {
        if (state.isBruteForceActive() || state.getEffort() != AngleSolverState.Effort.CUSTOM) return LongRunSolver.LongRunConfig.defaults();
        AngleSolverState.SolveBudget b = state.getSolveBudget();
        return LongRunSolver.LongRunConfig.of(b.getWindow(), b.getCommit());
    }

    static boolean useWindowSolverFor(AngleSolverState state) {
        if (state.isBruteForceActive() || state.getEffort() != AngleSolverState.Effort.CUSTOM) return true;
        return state.getSolveBudget().getUseWindowSolver();
    }

    static boolean ilsExhaustiveFor(AngleSolverState state) {
        if (state.isBruteForceActive()) return false;
        switch (state.getEffort()) {
            case THOROUGH: return true;
            case CUSTOM: return state.getSolveBudget().isIlsExhaustive();
            default: return false;
        }
    }

    static boolean stopOnFeasibleFor(AngleSolverState state) {
        if (state.isBruteForceActive()) return true;
        switch (state.getEffort()) {
            case FAST: return true;
            case THOROUGH: return false;
            default: return state.isStopOnFeasible();
        }
    }

    private final AngleSolverState state;
    private final BoxController boxes;
    private final InputData inputs;
    private final IntConsumer onApplied;

    private Consumer<Vec3dCore> onStartMoved = pos -> { };

    public void setOnStartMoved(Consumer<Vec3dCore> onStartMoved) {
        this.onStartMoved = onStartMoved != null ? onStartMoved : pos -> { };
    }

    /** Byte-exact forward, configured for the loader's MC inertia rule (see ExactJumpModel.forMcVersion).
     *  Stateless/immutable, so a single instance is shared read-only across the restart threads. */
    private final ForwardModel model;
    private final boolean modernCollision;

    private Plan lastPlan;

    // Background-solve handoff. `pending` is the single volatile publish point: the worker fully
    // builds the Outcome, then assigns it here; poll() reads it on the main thread.
    /** Test-only: the last JumpSpec handed to the solver, so tests can replay it on alternative models. */
    private volatile de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec lastSpecDebug;

    public de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec lastSpecDebug() {
        return lastSpecDebug;
    }

    private volatile boolean[] lastForce45MaskDebug;
    private volatile boolean[] lastStrafeMaskDebug;

    public boolean[] lastForce45MaskDebug() {
        return lastForce45MaskDebug;
    }

    public boolean[] lastStrafeMaskDebug() {
        return lastStrafeMaskDebug;
    }

    private volatile boolean sequentialSolve;

    public void setSequentialSolve(boolean sequential) {
        this.sequentialSolve = sequential;
    }

    private volatile boolean solving;
    private volatile long startNanos;
    private volatile Outcome pending;
    private volatile AtomicBoolean cancel;
    private volatile SolveProgress currentProgress;
    private volatile Job currentJob;
    private SolveResult liveResult;
    private int liveVersion = -1;
    private volatile SolveRunLog runLog;
    private volatile RunRecording recording;
    private volatile SolveRunRecord lastRunRecord;

    public void setRunLog(SolveRunLog log) {
        this.runLog = log;
    }

    public SolveRunRecord lastRunRecord() {
        return lastRunRecord;
    }

    private static final class RunRecording {
        final SolveRunRecord.Config config;
        final SolveRunRecord.Problem problem;
        final SolveProgress progress;
        final long startNanos;
        volatile GraphContext ctx;
        volatile SolveRunRecord.Race race;
        final AtomicBoolean written = new AtomicBoolean(false);

        RunRecording(SolveRunRecord.Config config, SolveRunRecord.Problem problem, SolveProgress progress, long startNanos) {
            this.config = config;
            this.problem = problem;
            this.progress = progress;
            this.startNanos = startNanos;
        }
    }

    private void finishRecord(RunRecording rec, String status, Double objective, Double violation,
                              Boolean feasible, String chain, double[] yaws) {
        if (rec == null || !rec.written.compareAndSet(false, true)) return;
        SolveRunRecord r = new SolveRunRecord();
        r.config = rec.config;
        r.problem = rec.problem;
        SolveRunRecord.Outcome out = new SolveRunRecord.Outcome();
        out.status = status;
        out.wallNanos = System.nanoTime() - rec.startNanos;
        if (objective != null) {
            out.objective = objective;
            out.violation = violation;
            out.feasible = feasible != null && feasible;
        } else if (rec.progress.haveBest()) {
            out.objective = rec.progress.bestObjective();
            out.violation = rec.progress.bestViolation();
            out.feasible = rec.progress.isBestFeasible();
        }
        SolveRunRecord.smoothnessOf(out, yaws != null ? yaws : rec.progress.bestYaws());
        GraphContext ctx = rec.ctx;
        out.chain = chain != null ? chain : (ctx != null ? ctx.chain() : null);
        r.outcome = out;
        r.trajectory = SolveRunRecord.samplesOf(rec.progress.samples());
        r.race = rec.race;
        if (ctx != null) {
            r.nodes = SolveRunRecord.nodeRunsOf(ctx.runState.statuses());
            SolveRunRecord.Counters counters = new SolveRunRecord.Counters();
            counters.smoothingEvals = ctx.smoothingEvals.get();
            r.counters = counters;
        }
        r.model = model.getClass().getSimpleName();
        lastRunRecord = r;
        SolveRunLog log = runLog;
        if (log != null) log.append(r);
    }

    public AngleSolverEngine(AngleSolverState state, BoxController boxes, InputData inputs, IntConsumer onApplied, ForwardModel model) {
        this.state = state;
        this.boxes = boxes;
        this.inputs = inputs;
        this.onApplied = onApplied;
        this.model = model;
        this.modernCollision = model instanceof ExactJumpModel && ((ExactJumpModel) model).modern();
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
        final Vec3dCore start;
        final boolean lockYaws;

        Plan(int startTick, double[] yaws, boolean[] strafeMask, boolean[] force45Mask, int strafeSign,
             ForwardPath path, Vec3dCore start) {
            this(startTick, yaws, strafeMask, force45Mask, strafeSign, path, start, false);
        }

        Plan(int startTick, double[] yaws, boolean[] strafeMask, boolean[] force45Mask, int strafeSign,
             ForwardPath path, Vec3dCore start, boolean lockYaws) {
            this.startTick = startTick;
            this.yaws = yaws;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.strafeSign = strafeSign;
            this.path = path;
            this.start = start;
            this.lockYaws = lockYaws;
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
        final long deadlineNanos;
        final LongRunSolver.LongRunConfig longRun;
        final boolean useWindowSolver;
        final boolean stopOnFeasible;
        final boolean ilsExhaustive;
        final JumpConstraint legalGoal;
        final SolverGraph graph;
        final boolean raceExplore;

        Job(JumpSpec spec, Objective.Sense sense, int startTick, int landingTick,
            int numTicks, boolean[] strafeMask, boolean[] force45Mask, List<ConstraintAt> uiConstraints,
            long deadlineNanos, LongRunSolver.LongRunConfig longRun, boolean useWindowSolver,
            boolean stopOnFeasible, boolean ilsExhaustive, JumpConstraint legalGoal, SolverGraph graph,
            boolean raceExplore
        ) {
            this.spec = spec;
            this.sense = sense;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.strafeMask = strafeMask;
            this.force45Mask = force45Mask;
            this.uiConstraints = uiConstraints;
            this.deadlineNanos = deadlineNanos;
            this.longRun = longRun;
            this.useWindowSolver = useWindowSolver;
            this.stopOnFeasible = stopOnFeasible;
            this.ilsExhaustive = ilsExhaustive;
            this.legalGoal = legalGoal;
            this.graph = graph;
            this.raceExplore = raceExplore;
        }
    }

    private static final class Outcome {
        final SolveResult result;
        final Plan plan;

        Outcome(SolveResult result, Plan plan) {
            this.result = result;
            this.plan = plan;
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
        lastForce45MaskDebug = ph.force45Mask;
        lastStrafeMaskDebug = ph.strafeMask;
        List<ConstraintAt> uiCons = collectUiConstraints(startTick, numTicks);

        Set<Constraint> footprintCons = null;
        if (startTick == 0 && model instanceof ExactJumpModel) {
            Set<Constraint> consumed = new HashSet<>();
            StartBox freeBox = deriveFreeStartBox(uiCons, ph.inputs, consumed);
            if (freeBox != null) {
                ph.inputs.startBox = freeBox;
                footprintCons = consumed;
            }
        }

        List<JumpConstraint> constraints = new ArrayList<>();
        Objective objective = new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks,
                state.getSmoothLambda());
        for (ConstraintAt ca : uiCons) {
            if (footprintCons != null && footprintCons.contains(ca.c)) continue;
            addMapped(constraints, ca.c, ca.absTick, ca.segTick, numTicks, ph.inputs.startYaw);
        }

        JumpConstraint legalGoal = null;
        if (state.isLegalMode()) {
            String[] whyNot = new String[1];
            legalGoal = selectLegalGoalWall(constraints, objective, whyNot);
            if (legalGoal == null) {
                SolveResult r = new SolveResult(false, 0, total, startTick + 1, landingTick + 1);
                r.setSolver("legal mode");
                r.addDetail("Legal mode", whyNot[0]);
                state.setResult(r);
                return null;
            }
            constraints.remove(legalGoal);
        }

        JumpSpec spec = new JumpSpec(ph.inputs, constraints, objective);
        return new Job(spec, objective.sense, startTick, landingTick, numTicks, ph.strafeMask,
                ph.force45Mask, uiCons,
                deadlineNanosFor(state), longRunConfigFor(state), useWindowSolverFor(state),
                stopOnFeasibleFor(state), ilsExhaustiveFor(state), legalGoal, GraphFactory.forState(state),
                state.isBruteForceActive() || state.getEffort() == AngleSolverState.Effort.FAST);
    }

    public String legalGoalWallLabel() {
        int startTick = state.getStartTick();
        int landingTick = state.getLandingTick();
        int numTicks = landingTick - startTick;
        if (numTicks <= 0) return null;
        List<ConstraintAt> uiCons = collectUiConstraints(startTick, numTicks);
        List<JumpConstraint> constraints = new ArrayList<>();
        TickState seamSeed = boxes.getState(startTick);
        float seamSeedYaw = seamSeed != null ? seamSeed.yaw : 0f;
        for (ConstraintAt ca : uiCons) {
            addMapped(constraints, ca.c, ca.absTick, ca.segTick, numTicks, seamSeedYaw);
        }
        Objective objective = new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks);
        String[] whyNot = new String[1];
        JumpConstraint goal = selectLegalGoalWall(constraints, objective, whyNot);
        return goal != null ? goal.name : null;
    }

    public static JumpConstraint selectLegalGoalWall(List<JumpConstraint> constraints, Objective objective, String[] whyNot) {
        boolean max = objective.sense == Objective.Sense.MAX;
        JumpConstraint.Cmp want = max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE;
        JumpConstraint.Mode wantMode = objective.axis == JumpPhysicsInputs.Axis.X
                ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        List<JumpConstraint> cands = new ArrayList<>();
        for (JumpConstraint c : constraints) {
            if (c.t2 != null) continue;
            if (c.mode != wantMode) continue;
            if (c.t1 != objective.tick) continue;
            if (c.cmp != want) continue;
            if (c.name != null && (c.name.endsWith("eqLo") || c.name.endsWith("eqHi"))) continue;
            cands.add(c);
        }
        if (cands.isEmpty()) {
            whyNot[0] = "no qualifying goal wall on the objective axis at the objective tick";
            return null;
        }
        JumpConstraint tight = cands.get(0);
        for (JumpConstraint c : cands) {
            if (max ? c.rhs > tight.rhs : c.rhs < tight.rhs) tight = c;
        }
        int ties = 0;
        for (JumpConstraint c : cands) {
            if (c.rhs == tight.rhs) ties++;
        }
        if (ties != 1) {
            whyNot[0] = "ambiguous goal wall: " + ties + " walls tie at " + tight.rhs;
            return null;
        }
        return tight;
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
        SolveProgress progress = new SolveProgress(job.sense == Objective.Sense.MAX, job.stopOnFeasible,
                job.spec.objective.smoothLambda, job.spec.asScenario().startYaw);
        currentProgress = progress;
        currentJob = job;
        liveResult = null;
        liveVersion = -1;
        String presetName = state.getEffort() == AngleSolverState.Effort.CUSTOM ? state.getGraphPresetName() : null;
        RunRecording rec = new RunRecording(
                SolveRunRecord.configOf(job.graph, presetName, state.getEffort().name(), FEAS_TOL, job.spec.objective),
                SolveRunRecord.problemOf(job.spec, countJumps(job.spec.asScenario())),
                progress, t0);
        recording = rec;
        solving = true;
        Thread worker = new Thread(() -> {
            try {
                Outcome o = runJob(job, token, progress, rec);
                if (o != null && !token.get()) pending = o;
            } catch (Throwable t) {
                t.printStackTrace();
                if (!token.get()) {
                    finishRecord(rec, SolveRunRecord.STATUS_FAILED, null, null, null, null, null);
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

    private Vec3dCore normalizeSeedVelocity(Vec3dCore v) {
        return model instanceof ExactJumpModel ? ((ExactJumpModel) model).zeroSubThresholdVelocity(v) : v;
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
        SurfaceKind[] surfacePerTick = new SurfaceKind[numTicks];
        int[] soulsandCellsPerTick = new int[numTicks];
        boolean[] sneakPerTick = new boolean[numTicks];
        float[] forwardIn = new float[numTicks];
        float[] strafeIn = new float[numTicks];
        boolean[] sprintArr = new boolean[numTicks];
        boolean deriveAny = false;
        for (int k = 0; k < numTicks; k++) {
            int t = startTick + k;
            InputRow row = rows.get(t);
            boolean deriveSprint = effSprint(t) == AngleSolverState.SprintMode.DERIVE;
            deriveAny |= deriveSprint;
            boolean jumpRow = row.isKeyActive(InputRow.Key.JUMP);
            // Ground/air is hand-defined per tick via slipperiness: a ground value (< 1.0) is grounded, AIR
            // (the default) is airborne. No dynamic fallback to a recorded trajectory.
            double slip = effSlipperiness(t).slip;
            boolean ground = slip < 1.0;
            slipPerTick[k] = ground ? slip : Double.NaN;
            surfacePerTick[k] = effMedium(t).kind;
            soulsandCellsPerTick[k] = effSoulsandCells(t);
            sneakPerTick[k] = row.isKeyActive(InputRow.Key.SNEAK);
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
        if (deriveAny) healWallHitSprint(startTick, numTicks, sprintArr, forwardIn);
        JumpPhysicsInputs phys = new JumpPhysicsInputs(numTicks);
        phys.startPos = seed.position;
        phys.startYaw = seed.yaw;
        Vec3dCore vel = normalizeSeedVelocity(seed.velocity);
        phys.initialVelocity = vel;
        phys.startBox = StartBox.pinned(seed.position.x, seed.position.z, vel.x, vel.z);
        phys.jumpTick = jumpTickRel;
        phys.jumpPerTick = jumpMask;
        phys.strafePerTick = strafeMask;
        phys.speedAmplifier = speedAmp;
        phys.slipPerTick = slipPerTick;
        phys.surfacePerTick = surfacePerTick;
        phys.soulsandCellsPerTick = soulsandCellsPerTick;
        phys.sneakPerTick = sneakPerTick;
        phys.yawLockedPerTick = yawLocked;
        phys.forwardInputPerTick = forwardIn;
        phys.strafeInputPerTick = strafeIn;
        phys.sprintPerTick = sprintArr;
        phys.liveAirSprintFactor = modernCollision;
        phys.incomingSprint = effSprint(startTick) == AngleSolverState.SprintMode.DERIVE
                ? (seed.hasMovementSample() ? seed.sprinting : Boolean.TRUE)
                : Boolean.TRUE;
        phys.incomingAmp = startTick > 0 ? effSpeedLevel(startTick - 1) : effSpeedLevel(startTick);
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

    private StartBox deriveFreeStartBox(List<ConstraintAt> uiCons, JumpPhysicsInputs phys, Set<Constraint> consumed) {
        double seedX = phys.startPos.x;
        double seedZ = phys.startPos.z;
        double[] xIv = firstTickInterval(uiCons, Constraint.Field.X);
        double[] zIv = firstTickInterval(uiCons, Constraint.Field.Z);
        boolean freeX = xIv != null && xIv[1] > xIv[0];
        boolean freeZ = zIv != null && zIv[1] > zIv[0];
        if (!freeX && !freeZ) return null;

        for (ConstraintAt ca : uiCons) {
            if (ca.segTick != 0 || ca.c.isRelative()) continue;
            if (freeX && ca.c.getField() == Constraint.Field.X) consumed.add(ca.c);
            if (freeZ && ca.c.getField() == Constraint.Field.Z) consumed.add(ca.c);
        }

        double pxLo = freeX ? xIv[0] : seedX;
        double pxHi = freeX ? xIv[1] : seedX;
        double pzLo = freeZ ? zIv[0] : seedZ;
        double pzHi = freeZ ? zIv[1] : seedZ;
        double vx = phys.initialVelocity.x;
        double vz = phys.initialVelocity.z;
        double refX = Math.max(pxLo, Math.min(pxHi, seedX));
        double refZ = Math.max(pzLo, Math.min(pzHi, seedZ));
        return new StartBox(refX, refZ, vx, vz, pxLo, pxHi, pzLo, pzHi, vx, vx, vz, vz);
    }

    private static double[] firstTickInterval(List<ConstraintAt> uiCons, Constraint.Field field) {
        boolean hasRange = false;
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        for (ConstraintAt ca : uiCons) {
            if (ca.segTick != 0) continue;
            Constraint c = ca.c;
            if (c.getField() != field || c.isRelative()) continue;
            if (c.isRange()) {
                hasRange = true;
                lo = Math.max(lo, c.getLo());
                hi = Math.min(hi, c.getHi());
            } else {
                switch (c.getOp()) {
                    case GT:
                    case GE:
                        lo = Math.max(lo, c.getValue());
                        break;
                    case LT:
                    case LE:
                        hi = Math.min(hi, c.getValue());
                        break;
                    case EQ:
                        lo = Math.max(lo, c.getValue());
                        hi = Math.min(hi, c.getValue());
                        break;
                    default:
                        break;
                }
            }
        }
        if (!hasRange) return null;
        return new double[] {lo, hi};
    }

    public void cancel() {
        if (!solving) return;
        AtomicBoolean token = cancel;
        if (token != null) token.set(true);
        finishRecord(recording, SolveRunRecord.STATUS_CANCELLED, null, null, null, null, null);
        recording = null;
        pending = null;
        solving = false;
        currentProgress = null;
        currentJob = null;
        liveResult = null;
        liveVersion = -1;
    }

    public void stopAndUseBest() {
        if (!solving) return;
        if (pending != null) return;
        SolveProgress prog = currentProgress;
        Job job = currentJob;
        AtomicBoolean token = cancel;
        if (token != null) token.set(true);
        currentProgress = null;
        currentJob = null;
        liveResult = null;
        liveVersion = -1;
        if (prog != null && job != null && prog.haveBest()) {
            finishRecord(recording, SolveRunRecord.STATUS_STOPPED_BEST, null, null, null, prog.bestSolver(), null);
            pending = finalizeBest(job, prog.bestYaws(), prog.bestSolver());
        } else {
            finishRecord(recording, SolveRunRecord.STATUS_CANCELLED, null, null, null, null, null);
            pending = null;
            solving = false;
        }
        recording = null;
    }

    public SolveResult liveBestResult() {
        SolveProgress p = currentProgress;
        Job job = currentJob;
        if (p == null || job == null || !p.haveBest()) return null;
        int v = p.version();
        if (liveResult == null || v != liveVersion) {
            double[] yaws = p.bestYaws();
            if (yaws == null) return liveResult;
            liveResult = buildLiveResult(job, yaws);
            liveVersion = v;
        }
        return liveResult;
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
        state.setResult(o.result);
        lastPlan = o.plan;
        solving = false;
        currentProgress = null;
        currentJob = null;
        liveResult = null;
        liveVersion = -1;
        recording = null;
    }

    public boolean isSolving() {
        return solving;
    }

    public double elapsedSeconds() {
        return solving ? (System.nanoTime() - startNanos) / 1.0e9 : 0.0;
    }

    private volatile GraphContext currentGraphContext;
    private volatile GraphRunState lastRunState;
    private LiveTrajectory liveTraj;
    private int liveTrajVersion = -1;
    private long liveTrajSeq;

    public GraphRunState graphRunState() {
        GraphContext c = currentGraphContext;
        return c != null ? c.runState : lastRunState;
    }

    public boolean isGraphSolving() {
        return currentGraphContext != null;
    }

    public SolveProgress liveProgress() {
        return currentProgress;
    }

    public LiveTrajectory liveTrajectory() {
        SolveProgress p = currentProgress;
        Job job = currentJob;
        if (p == null || job == null || !p.haveBest()) {
            liveTraj = null;
            liveTrajVersion = -1;
            return null;
        }
        int v = p.version();
        if (liveTraj == null || v != liveTrajVersion) {
            double[] yaws = p.bestYaws();
            if (yaws == null) return liveTraj;
            JumpPhysicsInputs sc = job.spec.asScenario();
            ForwardPath path = model.forward(sc, sc.toGameFacings(yaws));
            liveTraj = new LiveTrajectory(++liveTrajSeq, job.startTick, path.posX, path.posZ, p.isBestFeasible());
            liveTrajVersion = v;
        }
        return liveTraj;
    }

    private static final class ArmState {
        volatile Candidate cand;
        volatile boolean feasible;
        volatile boolean done;
    }

    private static final class RaceRun {
        final GraphContext winnerCtx;
        final Candidate cand;
        final JumpPhysicsInputs winnerSc;
        final boolean exploreWon;

        RaceRun(GraphContext winnerCtx, Candidate cand, JumpPhysicsInputs winnerSc, boolean exploreWon) {
            this.winnerCtx = winnerCtx;
            this.cand = cand;
            this.winnerSc = winnerSc;
            this.exploreWon = exploreWon;
        }
    }

    private RaceRun runStagedRace(Job job, JumpSpec spec, JumpPhysicsInputs sc, StartBox freeBox,
                                  AtomicBoolean master, SolveProgress progress, RunRecording rec) {
        GraphContext primaryCtx = new GraphContext(spec, model, freeBox, job.legalGoal, FEAS_TOL, master,
                progress, sequentialSolve, job.longRun);
        if (rec != null) rec.ctx = primaryCtx;
        lastRunState = primaryCtx.runState;
        currentGraphContext = primaryCtx;
        ArmState primary = new ArmState();
        long raceStart = System.nanoTime();
        SolveRunRecord.Race raceInfo = new SolveRunRecord.Race();
        raceInfo.winner = "primary";
        if (rec != null) rec.race = raceInfo;

        try {
            runArm(job.graph, primaryCtx, spec, primary);
            boolean primaryOk = primary.cand != null && primary.cand.yaws != null;
            if (master.get() || (primaryOk && primary.feasible)) {
                return new RaceRun(primaryCtx, primary.cand, sc, false);
            }

            SolverGraph exploreGraph = BuiltinGraphs.explore();
            SolveProgress exploreProgress = new SolveProgress(
                    job.sense == Objective.Sense.MAX, job.stopOnFeasible,
                    spec.objective.smoothLambda, sc.startYaw);
            exploreProgress.forwardTo(progress, "explore");
            JumpSpec exploreSpec = new JumpSpec(sc.copy(), spec.constraints, spec.objective);
            GraphContext exploreCtx = new GraphContext(exploreSpec, model, freeBox, job.legalGoal, FEAS_TOL,
                    master, exploreProgress, sequentialSolve, job.longRun);
            ArmState explore = new ArmState();
            raceInfo.spawned = true;
            raceInfo.spawnElapsedNanos = System.nanoTime() - raceStart;
            if (SolverTrace.on()) {
                SolverTrace.log("RACE", "explore stage started at %.1fs", raceInfo.spawnElapsedNanos / 1.0e9);
            }
            runArm(exploreGraph, exploreCtx, exploreSpec, explore);

            boolean exploreWon = explore.cand != null && explore.cand.yaws != null
                    && (!primaryOk || explore.feasible);
            raceInfo.winner = exploreWon ? "explore" : "primary";
            raceInfo.exploreChain = exploreCtx.chain();
            raceInfo.exploreGraphHash = SolveRunRecord.graphHash(exploreGraph);
            raceInfo.exploreNodes = SolveRunRecord.nodeRunsOf(exploreCtx.runState.statuses());
            if (SolverTrace.on()) {
                SolverTrace.log("RACE", "winner=%s primaryFeas=%s exploreFeas=%s",
                        exploreWon ? "explore" : "primary", primary.feasible, explore.feasible);
            }
            if (exploreWon) {
                exploreCtx.chainSuffix(" (explore)");
                return new RaceRun(exploreCtx, explore.cand, exploreSpec.asScenario(), true);
            }
            return new RaceRun(primaryCtx, primary.cand, sc, false);
        } finally {
            currentGraphContext = null;
        }
    }

    private void runArm(SolverGraph graph, GraphContext ctx, JumpSpec spec, ArmState out) {
        try {
            Candidate c = GraphRunner.run(graph, ctx);
            out.cand = c;
            if (c != null && c.yaws != null) {
                JumpPhysicsInputs armSc = spec.asScenario();
                double viol;
                if (ctx.stageLocked()) {
                    double[] gf = armSc.toGameFacings(c.yaws);
                    viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(armSc, gf));
                } else {
                    viol = violationOf(armSc, spec, c.yaws);
                }
                out.feasible = viol <= FEAS_TOL;
            }
        } catch (RuntimeException e) {
            if (SolverTrace.on()) SolverTrace.log("RACE", "arm error: %s", String.valueOf(e));
            e.printStackTrace();
        } finally {
            out.done = true;
        }
    }

    private Outcome runJob(Job job, AtomicBoolean cancel, SolveProgress progress, RunRecording rec) {
        JumpSpec spec = job.spec;
        lastSpecDebug = spec;
        JumpPhysicsInputs sc = spec.asScenario();

        boolean freeStart = model instanceof ExactJumpModel && sc.startBox != null && sc.startBox.startFree();
        StartBox freeBox = null;
        if (freeStart) {
            freeBox = sc.startBox;
            double refX = Math.max(freeBox.pxLo, Math.min(freeBox.pxHi, sc.startPos.x));
            double refZ = Math.max(freeBox.pzLo, Math.min(freeBox.pzHi, sc.startPos.z));
            if (refX != sc.startPos.x || refZ != sc.startPos.z) {
                if (SolverTrace.on()) {
                    SolverTrace.log("ENGINE", "free start seed (%.4f,%.4f) outside box, re-referenced to (%.4f,%.4f)",
                            sc.startPos.x, sc.startPos.z, refX, refZ);
                }
                sc.startPos = new Vec3dCore(refX, sc.startPos.y, refZ);
            }
            sc.startBox = StartBox.pinned(sc.startPos.x, sc.startPos.z, sc.initialVelocity.x, sc.initialVelocity.z);
        }

        long solveStart = System.nanoTime();
        if (SolverTrace.on()) {
            SolverTrace.solveStart(String.format(
                    "n=%d m=%d jumps=%d budgetS=%d window=%s exhaustive=%s stopOnFeasible=%s",
                    sc.numTicks, spec.constraints.size(), countJumps(sc),
                    job.deadlineNanos / 1_000_000_000L, job.useWindowSolver, job.ilsExhaustive, job.stopOnFeasible));
        }
        GraphContext ctx;
        Candidate cand;
        if (job.raceExplore) {
            RaceRun race = runStagedRace(job, spec, sc, freeBox, cancel, progress, rec);
            ctx = race.winnerCtx;
            cand = race.cand;
            if (race.exploreWon) sc = race.winnerSc;
        } else {
            GraphContext single = new GraphContext(spec, model, freeBox, job.legalGoal, FEAS_TOL, cancel, progress,
                    sequentialSolve, job.longRun);
            if (job.deadlineNanos > 0) single.setOverallDeadline(System.nanoTime() + job.deadlineNanos);
            if (rec != null) rec.ctx = single;
            lastRunState = single.runState;
            currentGraphContext = single;
            try {
                cand = GraphRunner.run(job.graph, single);
            } finally {
                currentGraphContext = null;
            }
            ctx = single;
        }
        if (cancel.get()) return null;
        if (cand == null || cand.yaws == null) {
            finishRecord(rec, SolveRunRecord.STATUS_FAILED, null, null, null, ctx.chain(), null);
            SolveResult fail = new SolveResult(false, 0, job.uiConstraints.size(),
                    job.startTick + 1, job.landingTick + 1);
            if (ctx.chain() != null) fail.setSolver(ctx.chain());
            return new Outcome(fail, null);
        }
        double[] yaws = cand.yaws;
        String solverName = ctx.chain();
        boolean stageLocked = ctx.stageLocked();
        double dualGap = ctx.dualGap();
        long solveNanos = System.nanoTime() - solveStart;
        if (SolverTrace.on()) {
            double doneViol = stageLocked
                    ? JumpConstraintCompiler.compile(spec).maxViolation(sc.toGameFacings(yaws),
                            model.forward(sc, sc.toGameFacings(yaws)))
                    : violationOf(sc, spec, yaws);
            SolverTrace.log("ENGINE", "done solver=\"%s\" obj=%.9f viol=%.3e ms=%d",
                    solverName, exactObjective(sc, spec, yaws), doneViol, solveNanos / 1_000_000L);
        }

        if (job.legalGoal != null) {
            solverName = solverName == null ? "legal mode" : solverName + " (legal)";
        }
        double[] gameFacings = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gameFacings);
        SolveResult result = assembleResult(job, yaws, gameFacings, path, solverName, solveNanos, dualGap);
        if (job.legalGoal != null) {
            double achieved = path.getPos(spec.objective.tick, spec.objective.axis);
            double shortfall = spec.objective.sense == Objective.Sense.MAX
                    ? job.legalGoal.rhs - achieved : achieved - job.legalGoal.rhs;
            result.addDetail("Legal shortfall", String.format(java.util.Locale.ROOT,
                    "%.9e short of %s", shortfall, job.legalGoal.name));
        }
        if (ctx.smoothingEvals.get() > 0) result.addDetail("Smoothing evals", Long.toString(ctx.smoothingEvals.get()));
        double finalObjective = path.getPos(spec.objective.tick, spec.objective.axis);
        double finalViolation = JumpConstraintCompiler.compile(spec).maxViolation(gameFacings, path);
        if (finalViolation <= FEAS_TOL && JumpLinearModel.hasFacingWall(spec.constraints)) {
            result.setNotice(DF_DIRECTION_NOTICE);
        }
        finishRecord(rec, SolveRunRecord.STATUS_SOLVED, finalObjective, finalViolation,
                finalViolation <= FEAS_TOL, solverName, yaws);
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, job.force45Mask, 1, path, sc.startPos, stageLocked);
        return new Outcome(result, plan);
    }

    public static final String DF_DIRECTION_NOTICE =
            "This jump has a delta-facing (dF) constraint. Landing does not depend on the Solve For"
            + " direction, but optimizing toward it is not guaranteed here: the deterministic"
            + " direction-optimizer only runs without dF constraints, so this result comes from the"
            + " general search and may not be the exact directional optimum.";

    /** The byte-exact objective value the given facings realize (for comparing two feasible candidates). */
    private double exactObjective(JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbsWrapped) {
        ForwardPath p = model.forward(sc, sc.toGameFacings(yawsAbsWrapped));
        return p.getPos(spec.objective.tick, spec.objective.axis);
    }

    private double violationOf(JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbsWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbsWrapped));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }

    private SolveResult buildLiveResult(Job job, double[] yaws) {
        JumpPhysicsInputs sc = job.spec.asScenario();
        double[] gameFacings = sc.toGameFacings(yaws);
        return buildResultWithObjective(job, yaws, gameFacings, model.forward(sc, gameFacings));
    }

    private Outcome finalizeBest(Job job, double[] yaws, String solver) {
        JumpPhysicsInputs sc = job.spec.asScenario();
        double[] gameFacings = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gameFacings);
        String name = solver == null || solver.isEmpty() ? "stopped early" : solver;
        SolveResult result = assembleResult(job, yaws, gameFacings, path, name, System.nanoTime() - startNanos, Double.NaN);
        result.addDetail("Stopped early", "kept best found");
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, job.force45Mask, 1, path, sc.startPos);
        return new Outcome(result, plan);
    }

    private SolveResult buildResultWithObjective(Job job, double[] yaws, double[] gameFacings, ForwardPath path) {
        SolveResult result = buildResult(job, yaws, gameFacings, path);
        result.setObjective(path.getPos(job.spec.objective.tick, job.spec.objective.axis));
        result.getOutcomes().add(0, objectiveOutcome(result, job.spec.objective, job.startTick));
        return result;
    }

    private SolveResult assembleResult(Job job, double[] yaws, double[] gameFacings, ForwardPath path,
                                       String solver, long solveNanos, double dualGap) {
        JumpPhysicsInputs sc = job.spec.asScenario();
        SolveResult result = buildResultWithObjective(job, yaws, gameFacings, path);
        result.setDurationNanos(solveNanos);
        result.setDurationMs(solveNanos / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setSolver(solver);
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
        SolveRunRecord.Outcome smooth = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(smooth, yaws);
        if (smooth.yawTravelDeg != null) {
            result.addDetail("Yaw travel", Math.round(smooth.yawTravelDeg) + " deg");
            result.addDetail("Yaw reversals", Integer.toString(smooth.yawDirChanges));
            result.addDetail("Yaw jerk", Math.round(smooth.yawJerkDeg) + " deg");
        }
        if (job.spec.objective.smoothLambda > 0.0) {
            result.addDetail("Smooth lambda", Double.toString(job.spec.objective.smoothLambda));
        }
        return result;
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

    // ---- apply (main thread) --------------------------------------------------

    public void apply() {
        if (lastPlan == null) return;
        Plan p = lastPlan;
        List<InputRow> rows = inputs.getRows();
        if (p.startTick < 0 || p.startTick >= rows.size()) return;
        if (p.startTick == 0 && p.start != null && startMoved(p.start)) {
            onStartMoved.accept(p.start);
        }
        if (p.lockYaws) {
            for (int k = 0; k < p.yaws.length && p.startTick + k < rows.size(); k++) {
                rows.get(p.startTick + k).setYawLocked(true);
            }
        }
        writeYawRows(rows, p.startTick, p.yaws, (float) boxes.getYaw(p.startTick));
        for (int k = 0; k < p.yaws.length && p.startTick + k < rows.size(); k++) {
            if (p.force45Mask[k]) {
                // A Force-45 tick realizes its solve assumption in the rows (gh-104): W + sprint held
                // on every tick, strafe per the mask (the grounded jump tick stays W-only). Keep ticks
                // are left alone; their keys ARE what the solve ran.
                rows.get(p.startTick + k).applyForce45(p.strafeMask[k], p.strafeSign);
            }
        }
        onApplied.accept(p.startTick);
        checkApplyDeviation(p);
    }

    public static void writeYawRows(List<InputRow> rows, int startTick, double[] yaws, float startYaw) {
        double prevAbs = startYaw;
        for (int k = 0; k < yaws.length && startTick + k < rows.size(); k++) {
            InputRow row = rows.get(startTick + k);
            double abs = yaws[k];
            if (row.isYawLocked()) {
                row.setYaw((float) abs);
            } else {
                double delta = abs - prevAbs;
                delta = Angles.wrapDelta(delta);
                row.setYaw((float) delta);
            }
            prevAbs = abs;
        }
    }

    private boolean startMoved(Vec3dCore start) {
        TickState s0 = boxes.getState(0);
        if (s0 == null || s0.position == null) return true;
        return Math.abs(start.x - s0.position.x) > 1.0e-9 || Math.abs(start.z - s0.position.z) > 1.0e-9;
    }

    /** Per-tick displacement tolerance. The modern model is bit-exact to the sim (a clean tick differs by
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
                        AngleSolverState.DeviationKind.WALL, t);
                return;
            }
        }
        List<InputRow> rows = inputs.getRows();
        for (int r = t; r >= Math.max(startTick, t - SNEAK_DESYNC_LOOKBACK); r--) {
            if (r < rows.size() && rows.get(r).isKeyActive(InputRow.Key.SNEAK)) {
                state.setApplyDeviation(head + ": the sneak at T" + (r + 1)
                        + " ran at a different position in the sampled run" + tail,
                        AngleSolverState.DeviationKind.SNEAK, t);
                return;
            }
        }
        state.setApplyDeviation(head + tail, AngleSolverState.DeviationKind.OTHER, t);
    }

    // ---- effective per-tick state (main thread, during snapshot) --------------

    private AngleSolverState.InputMode effInputs(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesInputs()) return ov.getInputs();
        return state.getDefaultInputs();
    }

    private AngleSolverState.SprintMode effSprint(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesSprint()) return ov.getSprint();
        return state.getDefaultSprint();
    }

    private Slipperiness effSlipperiness(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesSlipperiness()) return ov.getSlipperiness();
        return state.getDefaultSlipperiness();
    }

    private Medium effMedium(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.overridesMedium()) return ov.getMedium();
        return Medium.NONE;
    }

    private int effSoulsandCells(int tick) {
        StateOverride ov = overrideAt(tick);
        if (ov != null && ov.getMedium() == Medium.SOULSAND) return ov.getSoulsandCells();
        return 1;
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

    // ---- constraint mapping (UI Constraint -> solver JumpConstraint) -----------

    private void addMapped(List<JumpConstraint> out, Constraint c, int absTick, int segTick, int numTicks, float seedYaw) {
        String tag = (c.isVsDz() ? "dXvsdZ" : ConstraintText.fieldLabel(c)) + "@" + absTick;
        int startTick = absTick - segTick;
        switch (c.getField()) {
            case X:
                if (c.isRelative()) {
                    int refSeg = c.getRefTick() - startTick;
                    if (refSeg < 0 || refSeg > numTicks) break;
                    addRelative(out, JumpConstraint.Mode.X, segTick, refSeg, c, tag);
                } else {
                    addScalarOrRange(out, JumpConstraint.Mode.X, segTick, c, tag);
                }
                break;
            case Z:
                if (c.isRelative()) {
                    int refSeg = c.getRefTick() - startTick;
                    if (refSeg < 0 || refSeg > numTicks) break;
                    addRelative(out, JumpConstraint.Mode.Z, segTick, refSeg, c, tag);
                } else {
                    addScalarOrRange(out, JumpConstraint.Mode.Z, segTick, c, tag);
                }
                break;
            case F:
                if (segTick >= numTicks) break; // no facing for the post-final state
                addScalarOrRange(out, JumpConstraint.Mode.F, segTick, c, tag);
                break;
            case DX:
                if (segTick < 1) break; // velocity needs t-1
                addRelative(out, c.isVsDz() ? JumpConstraint.Mode.DXZ : JumpConstraint.Mode.X,
                        segTick, segTick - 1, c, tag);
                break;
            case DZ:
                if (segTick < 1) break;
                addRelative(out, JumpConstraint.Mode.Z, segTick, segTick - 1, c, tag);
                break;
            case DF:
                if (segTick >= numTicks) break;
                if (segTick < 1) {
                    addSeamDeltaFacing(out, c, tag, seedYaw);
                    break;
                }
                addRelative(out, JumpConstraint.Mode.F, segTick, segTick - 1, c, tag);
                break;
            case RT:
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

    /** Two-tick difference (dX/dZ/dXZ on positions, dF on facings, relative X/Z against a reference tick):
     *  v[t1]-v[t2] against a range (GE/LE pair), an equality (the same +-MET_TOL corridor as scalar
     *  fields, see addScalarOrRange), or a single comparison wall. */
    private void addRelative(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, int t2, Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, cmp(c.getOp()), c.getValue(), tag));
        }
    }

    private void addSeamDeltaFacing(List<JumpConstraint> out, Constraint c, String tag, float seedYaw) {
        double base = seedYaw;
        if (c.isRange()) {
            out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, base + c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, base + c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, base + c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, base + c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS, cmp(c.getOp()), base + c.getValue(), tag));
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
        List<Integer> unmet = new ArrayList<>();
        for (ConstraintAt ca : ordered) {
            Double found = findValue(ca.c, ca.segTick, job.startTick, job.numTicks, gameFacings, path,
                    job.spec.asScenario().startYaw);
            if (found == null) continue; // unmappable, e.g. velocity on tick 0
            total++;
            boolean ok = satisfied(ca.c, found);
            if (ok) met++;
            else unmet.add(ca.absTick);
            outs.add(outcome(ca.c, ca.absTick, found, ok));
        }
        SolveResult r = new SolveResult(met == total, met, total, job.startTick + 1, job.landingTick + 1);
        r.getOutcomes().addAll(outs);
        for (int t : unmet) r.addUnmetTick(t);
        for (int k = 0; k < yaws.length; k++) {
            r.getYaws().add(new SolveResult.YawEntry(job.startTick + k + 1, yaws[k]));
        }
        return r;
    }

    /** The value a constraint is judged against. F reads the GAME facing (what the solver enforced and the
     *  sim runs), wrapped for display; the wrapped-abs plan yaw differs from it by float accumulation,
     *  which the strict wall gate would mis-report on a hugged facing wall. */
    private Double findValue(Constraint c, int segTick, int startTick, int numTicks, double[] gameFacings, ForwardPath path, float seedYaw) {
        Integer refSeg = null;
        if (c.isRelative()) {
            int r = c.getRefTick() - startTick;
            if (r < 0 || r > numTicks) return null;
            refSeg = r;
        }
        switch (c.getField()) {
            case X: return refSeg != null ? path.posX[segTick] - path.posX[refSeg] : path.posX[segTick];
            case Z: return refSeg != null ? path.posZ[segTick] - path.posZ[refSeg] : path.posZ[segTick];
            case F: return segTick < numTicks ? Angles.wrap(gameFacings[segTick]) : null;
            case DX: return segTick >= 1
                    ? (c.isVsDz()
                        ? Math.abs(path.posX[segTick] - path.posX[segTick - 1])
                            - Math.abs(path.posZ[segTick] - path.posZ[segTick - 1])
                        : path.posX[segTick] - path.posX[segTick - 1]) : null;
            case DZ: return segTick >= 1 ? path.posZ[segTick] - path.posZ[segTick - 1] : null;
            case DF:
                if (segTick >= numTicks) return null;
                return segTick >= 1
                        ? Angles.wrap(gameFacings[segTick] - gameFacings[segTick - 1])
                        : Angles.wrap(gameFacings[0] - seedYaw);
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
        boolean angular = c.getField() == Constraint.Field.F || c.getField() == Constraint.Field.DF;
        double f = angular ? v + Angles.wrap(found - v) : found;
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

    private SolveResult.Outcome outcome(Constraint c, int absTick, double found, boolean met) {
        String field = ConstraintText.fieldLabel(c);
        String tickLabel = "T" + (absTick + 1);
        if (c.isRange()) {
            double margin = Math.min(found - c.getLo(), c.getHi() - found);
            String marginStr = (margin >= 0 ? "+" : "") + ConstraintText.fixedStat(margin);
            return new SolveResult.Outcome(field, tickLabel, ConstraintText.chip(c), ConstraintText.fixedStat(found), marginStr, met);
        }
        double v = c.getValue();
        String relation = c.getOp().glyph + " " + (c.isVsDz() ? ConstraintText.chip(c) : ConstraintText.num(v));
        double diff = c.getField() == Constraint.Field.F || c.getField() == Constraint.Field.DF
                ? Angles.wrap(found - v) : (found - v);
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
        return new SolveResult.Outcome(field, tickLabel, relation, ConstraintText.fixedStat(found), marginStr, met);
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

    public static double[] dualChain(ExactJumpModel em, JumpSpec spec, JumpPhysicsInputs sc,
                                     AtomicBoolean cancel, String[] nameOut) {
        return dualChain(em, spec, sc, cancel, nameOut, 0L);
    }

    public static double[] dualChain(ExactJumpModel em, JumpSpec spec, JumpPhysicsInputs sc,
                                     AtomicBoolean cancel, String[] nameOut, long deadlineNanos) {
        return dualChain(em, spec, sc, cancel, nameOut, deadlineNanos, new SlpSolve.Config());
    }

    public static double[] dualChain(ExactJumpModel em, JumpSpec spec, JumpPhysicsInputs sc,
                                     AtomicBoolean cancel, String[] nameOut, long deadlineNanos,
                                     SlpSolve.Config slpCfg) {
        return dualChain(em, spec, sc, cancel, nameOut, deadlineNanos, slpCfg, new ClosedFormSolve.Config(),
                new RelaxationRecovery.Config());
    }

    public static double[] dualChain(ExactJumpModel em, JumpSpec spec, JumpPhysicsInputs sc,
                                     AtomicBoolean cancel, String[] nameOut, long deadlineNanos,
                                     SlpSolve.Config slpCfg, ClosedFormSolve.Config cfCfg,
                                     RelaxationRecovery.Config rrCfg) {
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "closed form start");
        double[] yaws = ClosedFormSolve.optimize(em, spec, FEAS_TOL, cancel, cfCfg);
        if (yaws != null) {
            nameOut[0] = "closed form";
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "closed form solved");
            return yaws;
        }
        if (cancel.get()) return null;
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "slp start");
        yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel, null, slpCfg);
        if (yaws != null) {
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "slp solved");
            return levelSetTopUp(em, spec, yaws, cancel, "closed form -> SLP", nameOut);
        }
        if (deadlineNanos == 0L || deadlineNanos - System.nanoTime() >= RELAX_MIN_REMAINING_NANOS) {
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "relaxation start");
            yaws = RelaxationRecovery.solve(em, spec, FEAS_TOL, cancel, rrCfg);
            if (yaws != null) {
                if (SolverTrace.on()) SolverTrace.log("CHAIN", "relaxation solved");
                return levelSetTopUp(em, spec, yaws, cancel, "closed form -> relaxation recovery", nameOut);
            }
        } else if (SolverTrace.on()) {
            SolverTrace.log("CHAIN", "relaxation skipped (deadline)");
        }
        for (Objective alt : alternateObjectives(spec.objective)) {
            if (cancel.get()) return null;
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "alt seed %s %s start", alt.axis, alt.sense);
            double[] seed = ClosedFormSolve.optimize(em, new JumpSpec(sc, spec.constraints, alt), FEAS_TOL, cancel, cfCfg);
            if (seed == null) continue;
            yaws = SlpSolve.optimize(em, spec, FEAS_TOL, cancel, seed, slpCfg);
            if (yaws != null) {
                if (SolverTrace.on()) SolverTrace.log("CHAIN", "reseeded slp solved");
                return levelSetTopUp(em, spec, yaws, cancel, "closed form -> SLP (reseeded)", nameOut);
            }
        }
        if (SolverTrace.on()) SolverTrace.log("CHAIN", "miss");
        return null;
    }

    /** A non-closed-form feasible result can be short of the objective's dual bound when the chosen Solve For
     *  degenerates the dual recovery (optimizing into a same-axis position wall); ladder the objective up to
     *  the bound via feasibility solves ({@link LevelSetAscent}). No-op with dF constraints (no dual bound). */
    private static double[] levelSetTopUp(ExactJumpModel em, JumpSpec spec, double[] yaws, AtomicBoolean cancel,
                                          String name, String[] nameOut) {
        double[] improved = LevelSetAscent.improve(em, spec, yaws, FEAS_TOL, cancel);
        if (improved != null && improved != yaws) {
            nameOut[0] = name + " -> level set";
            if (SolverTrace.on()) SolverTrace.log("CHAIN", "level set improved");
            return improved;
        }
        nameOut[0] = name;
        return yaws;
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
