package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverRunResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;

import java.util.ArrayList;
import java.util.List;
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

    /** Per-effort solve budget. CMA-ES (restarts x maxEval) finds the feasible basins; the bucket polish
     *  exactizes them. {@code polishCount} basins are polished in parallel and the best kept, because the
     *  best pre-polish objective is not always the basin that polishes best. Fewer restarts/evals is
     *  faster but can miss a feasible basin on a hard jump, so FAST trades robustness for ~100ms. */
    private static final class Budget {
        final int restarts;
        final int maxEval;
        final int polishCount;
        final BucketAscentPolish.Config polishCfg;

        Budget(int restarts, int maxEval, int polishCount, BucketAscentPolish.Config polishCfg) {
            this.restarts = restarts;
            this.maxEval = maxEval;
            this.polishCount = polishCount;
            this.polishCfg = polishCfg;
        }
    }

    private static Budget budgetFor(AngleSolverState.Effort effort) {
        switch (effort) {
            case FAST:     return new Budget(16, 4500, 2, BucketAscentPolish.FAST);
            case THOROUGH: return new Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
            default:       return new Budget(28, 7000, 4, BucketAscentPolish.BALANCED);
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
    private volatile boolean solving;
    private volatile long startNanos;
    private volatile Outcome pending;

    public AngleSolverEngine(AngleSolverState state, BoxController boxes, InputData inputs,
                             IntConsumer onApplied, ForwardModel model) {
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
        final int strafeSign;

        Plan(int startTick, double[] yaws, boolean[] strafeMask, int strafeSign) {
            this.startTick = startTick;
            this.yaws = yaws;
            this.strafeMask = strafeMask;
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
        final List<ConstraintAt> uiConstraints;
        final long startNanos;
        final AngleSolverState.Effort effort;

        Job(JumpSpec spec, Objective.Sense sense, int startTick, int landingTick,
            int numTicks, boolean[] strafeMask, List<ConstraintAt> uiConstraints, long startNanos,
            AngleSolverState.Effort effort) {
            this.spec = spec;
            this.sense = sense;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.strafeMask = strafeMask;
            this.uiConstraints = uiConstraints;
            this.startNanos = startNanos;
            this.effort = effort;
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

    public void solve() {
        if (solving) return;
        int startTick = state.getStartTick();
        int landingTick = state.getLandingTick();
        int total = segmentConstraintCount(startTick, landingTick);

        List<InputRow> rows = inputs.getRows();
        int numTicks = landingTick - startTick;
        if (numTicks <= 0 || startTick < 0 || startTick >= boxes.size()
                || landingTick > rows.size() || startTick >= rows.size()) {
            state.setResult(new SolveResult(false, 0, total, startTick + 1, landingTick + 1));
            return;
        }

        TickState seed = boxes.getState(startTick);
        int jumpTickRel = firstJumpTick(rows, startTick, numTicks);

        boolean[] strafeMask = new boolean[numTicks];
        boolean[] yawLocked = new boolean[numTicks];
        int[] speedAmp = new int[numTicks];
        double[] slipPerTick = new double[numTicks];
        for (int k = 0; k < numTicks; k++) {
            int t = startTick + k;
            boolean jumpRow = rows.get(t).isKeyActive(InputRow.Key.JUMP);
            strafeMask[k] = (effInputs(t) == AngleSolverState.InputMode.FORCE_45) && !jumpRow;
            yawLocked[k] = rows.get(t).isYawLocked();
            speedAmp[k] = effSpeedLevel(t);
            double slip = slipValue(effSlipperiness(t));
            slipPerTick[k] = slip < 1.0 ? slip : Double.NaN;
        }

        List<ConstraintAt> uiCons = new ArrayList<>();
        for (Integer tickKey : state.populatedTicks()) {
            int absTick = tickKey;
            int segTick = absTick - startTick;
            if (segTick < 0 || segTick > numTicks) continue;
            TickConstraints tc = state.tickConstraintsOrNull(absTick);
            if (tc == null) continue;
            for (Constraint c : tc.getConstraints()) uiCons.add(new ConstraintAt(absTick, segTick, c.copy()));
        }

        JumpPhysicsInputs inputs = new JumpPhysicsInputs(numTicks);
        inputs.startPos = seed.position;
        inputs.startYaw = seed.yaw;
        inputs.initialVelocity = seed.velocity;
        inputs.jumpTick = jumpTickRel;
        inputs.strafeSign = 1; // A and D are mirror-symmetric; solve the +1 sign only
        inputs.strafePerTick = strafeMask;
        inputs.speedAmplifier = speedAmp;
        inputs.slipPerTick = slipPerTick;
        inputs.yawLockedPerTick = yawLocked;

        List<JumpConstraint> constraints = new ArrayList<>();
        Objective objective = new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks);
        for (ConstraintAt ca : uiCons) addMapped(constraints, ca.c, ca.absTick, ca.segTick, numTicks);

        // Freeze the problem on the main thread so the worker reads a genuinely read-only snapshot.
        JumpSpec spec = new JumpSpec(inputs, constraints, objective);
        long t0 = System.nanoTime();
        Job job = new Job(spec, objective.sense, startTick, landingTick, numTicks, strafeMask, uiCons, t0,
                state.getEffort());

        // Show the spinner instead of a stale result, then run off-thread.
        state.clearResult();
        lastPlan = null;
        pending = null;
        startNanos = t0;
        solving = true;
        Thread worker = new Thread(() -> {
            try {
                pending = runJob(job);
            } catch (RuntimeException e) {
                // Never leave the spinner stuck: publish a failure so poll() clears the solving state.
                SolveResult fail = new SolveResult(false, 0, job.uiConstraints.size(),
                        job.startTick + 1, job.landingTick + 1);
                pending = new Outcome(fail, null);
            }
        }, "angle-solver");
        worker.setDaemon(true);
        worker.start();
    }

    /** Publish a finished background solve. Call every frame on the main thread. */
    public void poll() {
        Outcome o = pending;
        if (o == null) return;
        pending = null;
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
    private Outcome runJob(Job job) {
        JumpSpec spec = job.spec; // frozen on the main thread; arrays read-only during solve, so shareable
        JumpPhysicsInputs sc = spec.asScenario();

        double[] warm = new double[job.numTicks];
        java.util.Arrays.fill(warm, sc.startYaw);
        java.util.Random rng = new java.util.Random(0x9E3779B9L ^ job.numTicks);
        Budget budget = budgetFor(job.effort);

        List<double[]> inits = new ArrayList<>();
        for (int r = 0; r < budget.restarts; r++) inits.add(r == 0 ? warm : randomInit(rng, job.numTicks));

        // Stiffer constraint penalty (1e7) so CMA-ES drives the path right onto the wall instead of settling
        // ~5e-7 past it; with FEAS_TOL=0 only restarts that land strictly behind the wall count as feasible.
        List<SolverRunResult> results = inits.parallelStream()
                .map(in -> new CmaesJumpHarness(1.0e7, 1.0e7, CMAES_SIGMA_DEG, budget.maxEval).solve(model, spec, in))
                .collect(java.util.stream.Collectors.toList());

        boolean max = job.sense == Objective.Sense.MAX;
        List<SolverRunResult> feasible = new ArrayList<>();
        for (SolverRunResult r : results) if (maxViolation(r) <= FEAS_TOL) feasible.add(r);

        // yaws are absolute wrapped facings (what Apply writes as deltas); the game runs the float-accumulated
        // facings, so the reported path forwards toGameFacings(yaws), bit-for-bit the in-game trajectory.
        double[] yaws;
        if (feasible.isEmpty()) {
            // No feasible basin: keep the least-violating result so the panel can show which walls are unmet.
            SolverRunResult best = null;
            for (SolverRunResult r : results) if (best == null || maxViolation(r) < maxViolation(best)) best = r;
            yaws = wrapAll(best.yawAbsDeg);
        } else {
            // The best pre-polish objective is not always the basin that polishes best, so polish the top
            // few feasible basins in parallel and keep the best. CMA-ES (penalty) settles at one island's
            // corner; the deterministic block-1+block-2 ascent hops between the bucket-quantized feasible
            // islands with strictly-feasible joint moves, recovering what the penalty leaves on the table.
            feasible.sort((a, b) -> max ? Double.compare(b.objectiveValue, a.objectiveValue)
                                        : Double.compare(a.objectiveValue, b.objectiveValue));
            List<double[]> top = new ArrayList<>();
            for (int i = 0; i < Math.min(budget.polishCount, feasible.size()); i++) {
                top.add(wrapAll(feasible.get(i).yawAbsDeg));
            }
            List<double[]> polished = top.parallelStream()
                    .map(y -> BucketAscentPolish.polish(model, spec, y, budget.polishCfg))
                    .collect(java.util.stream.Collectors.toList());
            yaws = polished.get(0);
            double bestObj = objectiveOf(sc, spec.objective, yaws);
            for (int i = 1; i < polished.size(); i++) {
                double o = objectiveOf(sc, spec.objective, polished.get(i));
                if (max ? o > bestObj : o < bestObj) { bestObj = o; yaws = polished.get(i); }
            }
        }

        ForwardPath path = model.forward(sc, sc.toGameFacings(yaws));
        SolveResult result = buildResult(job, yaws, path);
        result.setDurationMs((System.nanoTime() - job.startNanos) / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setObjective(path.getPos(spec.objective.tick, spec.objective.axis));
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, 1);
        return new Outcome(result, plan);
    }

    private static String formatClock() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    private double objectiveOf(JumpPhysicsInputs sc, Objective obj, double[] absWrapped) {
        return model.forward(sc, sc.toGameFacings(absWrapped)).getPos(obj.tick, obj.axis);
    }

    private static double[] wrapAll(double[] yawAbsDeg) {
        return Angles.wrapAll(yawAbsDeg);
    }

    private static double maxViolation(SolverRunResult r) {
        double m = 0.0;
        for (double s : r.ineqSlack) m = Math.max(m, s);
        for (double e : r.eqResidual) m = Math.max(m, Math.abs(e));
        return m;
    }

    private static double[] randomInit(java.util.Random rng, int n) {
        double[] f = new double[n];
        for (int i = 0; i < n; i++) f[i] = -180.0 + 360.0 * rng.nextDouble();
        return f;
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
            boolean strafeThis = p.strafeMask[k];
            row.setKeyActive(InputRow.Key.A, strafeThis && p.strafeSign > 0);
            row.setKeyActive(InputRow.Key.D, strafeThis && p.strafeSign < 0);
            prevAbs = abs;
        }
        onApplied.accept(p.startTick);
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
                addScalarOrRange(out, JumpConstraint.Mode.X, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case Z:
                addScalarOrRange(out, JumpConstraint.Mode.Z, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case F:
                if (segTick >= numTicks) break; // no facing for the post-final state
                addScalarOrRange(out, JumpConstraint.Mode.F, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case DX:
                if (segTick < 1) break; // velocity needs t-1
                addRangePair(out, JumpConstraint.Mode.X, segTick, segTick - 1, c, tag);
                break;
            case DZ:
                if (segTick < 1) break;
                addRangePair(out, JumpConstraint.Mode.Z, segTick, segTick - 1, c, tag);
                break;
        }
    }

    /** Scalar X/Z/F (one constraint) or a position range (a GE/LE pair). */
    private void addScalarOrRange(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, Integer t2,
                                  JumpConstraint.Op op, Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, t2, op, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo", true));
            out.add(new JumpConstraint(mode, t1, t2, op, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi", true));
        } else {
            out.add(new JumpConstraint(mode, t1, t2, op, cmp(c.getOp()), c.getValue(), tag, true));
        }
    }

    /** Velocity (dX/dZ) range: pos[t1]-pos[t2] within [lo,hi], as a GE/LE pair. */
    private void addRangePair(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, int t2, Constraint c, String tag) {
        out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo", true));
        out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi", true));
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

    private SolveResult buildResult(Job job, double[] yaws, ForwardPath path) {
        int total = 0;
        int met = 0;
        List<SolveResult.Outcome> outs = new ArrayList<>();
        List<ConstraintAt> ordered = new ArrayList<>(job.uiConstraints);
        ordered.sort((a, b) -> Integer.compare(a.absTick, b.absTick)); // panel lists constraints first-tick-first
        for (ConstraintAt ca : ordered) {
            Double found = findValue(ca.c, ca.segTick, job.numTicks, yaws, path);
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

    private Double findValue(Constraint c, int segTick, int numTicks, double[] yaws, ForwardPath path) {
        switch (c.getField()) {
            case X: return path.posX[segTick];
            case Z: return path.posZ[segTick];
            case F: return segTick < numTicks ? yaws[segTick] : null;
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
            if (tc != null) n += tc.getConstraints().size();
        }
        return n;
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
}
