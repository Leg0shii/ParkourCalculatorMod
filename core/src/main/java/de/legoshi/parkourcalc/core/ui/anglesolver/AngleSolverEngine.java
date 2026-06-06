package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.CmaesJumpHarness;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.ConstraintScene;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.HarnessResult;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.M2PwlSigmoid;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.PathResult;
import de.legoshi.parkourcalc.core.ui.anglesolver.solver.Spike0Scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Bridges the Angle Solver UI to the M2 smooth solver and back into the live TAS.
 *
 * <p>Threading: {@link #solve()} snapshots the whole problem on the caller (main) thread into an
 * immutable {@link Job}, then runs the multistart solve on a daemon thread so the game never stalls.
 * The worker touches only the snapshot, never live state. {@link #poll()} (called each frame on the
 * main thread) publishes a finished result into {@link AngleSolverState}. {@link #apply()} folds the
 * solved facings back into the rows and retriggers the sim.
 *
 * <p>Scope (v1): the model assumes W+Sprint on the segment rows and reproduces only what M2 models
 * (45-strafe, per-tick slipperiness, per-tick Speed). Jump-Boost and Slowness potions are not
 * modeled. M2 is approximate; the live SimulatorEntity is the source of truth after Apply. */
public final class AngleSolverEngine {

    private static final double FEAS_TOL = 1.0e-6;
    private static final double MET_TOL = 1.0e-3;

    /** CMA-ES is a derivative-free GLOBAL method, so it escapes the facing-clamp local optima the
     *  gradient solver railed into (and it needs far fewer restarts). The warm start plus this many
     *  random restarts run in parallel; the best feasible-then-objective result wins. Only one strafe
     *  sign is solved: A and D are mirror-symmetric (flip the sign and shift air-tick facings by 90deg
     *  for an identical trajectory), so the optimal objective is the same either way. */
    private static final int RESTARTS = 24;

    /** CMA-ES initial step (deg). With the wider-than-one-turn search bounds the global basin is a
     *  single continuous region, so a moderate sigma finds it in a handful of restarts. */
    private static final double CMAES_SIGMA_DEG = 90.0;

    private final AngleSolverState state;
    private final BoxController boxes;
    private final InputData inputs;
    private final IntConsumer onApplied;

    private Plan lastPlan;

    // Background-solve handoff. `pending` is the single volatile publish point: the worker fully
    // builds the Outcome, then assigns it here; poll() reads it on the main thread.
    private volatile boolean solving;
    private volatile long startNanos;
    private volatile Outcome pending;

    public AngleSolverEngine(AngleSolverState state, BoxController boxes, InputData inputs, IntConsumer onApplied) {
        this.state = state;
        this.boxes = boxes;
        this.inputs = inputs;
        this.onApplied = onApplied;
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
        final ConstraintScene scene;
        final Objective.Sense sense;
        final int startTick;
        final int landingTick;
        final int numTicks;
        final boolean[] strafeMask;
        final List<ConstraintAt> uiConstraints;
        final long startNanos;

        Job(ConstraintScene scene, Objective.Sense sense, int startTick, int landingTick,
            int numTicks, boolean[] strafeMask, List<ConstraintAt> uiConstraints, long startNanos) {
            this.scene = scene;
            this.sense = sense;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.numTicks = numTicks;
            this.strafeMask = strafeMask;
            this.uiConstraints = uiConstraints;
            this.startNanos = startNanos;
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
        int[] speedAmp = new int[numTicks];
        double[] slipPerTick = new double[numTicks];
        for (int k = 0; k < numTicks; k++) {
            int t = startTick + k;
            boolean jumpRow = rows.get(t).isKeyActive(InputRow.Key.JUMP);
            strafeMask[k] = (effInputs(t) == AngleSolverState.InputMode.FORCE_45) && !jumpRow;
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

        ConstraintScene scene = new ConstraintScene();
        scene.startPos = seed.position;
        scene.startYaw = seed.yaw;
        scene.incomingVelocity = seed.velocity;
        scene.numTicks = numTicks;
        scene.jumpTick = jumpTickRel;
        scene.strafePerTick = strafeMask;
        scene.speedAmplifier = speedAmp;
        scene.slipPerTick = slipPerTick;
        scene.objective = new Objective(axis(state.getAxis()), sense(state.getGoal()), numTicks);
        for (ConstraintAt ca : uiCons) addMapped(scene, ca.c, ca.absTick, ca.segTick, numTicks);

        long t0 = System.nanoTime();
        Job job = new Job(scene, scene.objective.sense, startTick, landingTick, numTicks, strafeMask, uiCons, t0);

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

    /** One restart: an immutable spec (read-only during solve, so shareable across threads), the
     *  strafe sign it carries, and the initial facings to start from. */
    private static final class Restart {
        final double[] init;
        volatile HarnessResult result;

        Restart(double[] init) {
            this.init = init;
        }
    }

    /** Runs entirely on the worker thread, reading only the immutable Job. The restarts are mutually
     *  independent and CPU-bound, so each CMA-ES run goes in parallel; we keep the best
     *  feasible-then-objective across all of them. Single strafe sign (+1 = A); see RESTARTS. */
    private Outcome runJob(Job job) {
        double[] warm = new double[job.numTicks];
        java.util.Arrays.fill(warm, job.scene.startYaw);
        java.util.Random rng = new java.util.Random(0x9E3779B9L ^ job.numTicks);
        M2PwlSigmoid model = new M2PwlSigmoid();

        job.scene.strafeSign = 1;
        JumpSpec spec = job.scene.toJumpSpec(); // immutable snapshot; arrays read-only during solve, so shareable
        List<Restart> restarts = new ArrayList<>();
        for (int r = 0; r < RESTARTS; r++) {
            restarts.add(new Restart(r == 0 ? warm : randomInit(rng, job.numTicks)));
        }
        restarts.parallelStream().forEach(rs ->
                rs.result = new CmaesJumpHarness(1.0e6, 1.0e6, CMAES_SIGMA_DEG, 8000).solve(model, spec, rs.init));

        Restart best = null;
        for (Restart rs : restarts) {
            if (isBetter(rs.result, best == null ? null : best.result, job.sense)) best = rs;
        }

        // The search ran in a wider-than-one-turn space; wrap each facing back to (-180,180] for
        // display/apply. sin/cos are periodic, so the trajectory is unchanged.
        double[] yaws = best.result.yawAbsDeg.clone();
        for (int i = 0; i < yaws.length; i++) yaws[i] = wrapDeg(yaws[i]);
        PathResult path = model.forward(spec.asScenario(), yaws);
        SolveResult result = buildResult(job, yaws, path);
        result.setDurationMs((System.nanoTime() - job.startNanos) / 1_000_000L);
        result.setFinishedAt(formatClock());
        result.setObjective(best.result.objectiveValue);
        Plan plan = new Plan(job.startTick, yaws, job.strafeMask, 1);
        return new Outcome(result, plan);
    }

    private static String formatClock() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    private boolean isBetter(HarnessResult cand, HarnessResult cur, Objective.Sense sense) {
        if (cur == null) return true;
        double vc = maxViolation(cand);
        double vu = maxViolation(cur);
        boolean cf = vc <= FEAS_TOL;
        boolean uf = vu <= FEAS_TOL;
        if (cf && uf) {
            return sense == Objective.Sense.MAX
                    ? cand.objectiveValue > cur.objectiveValue
                    : cand.objectiveValue < cur.objectiveValue;
        }
        if (cf) return true;
        if (uf) return false;
        return vc < vu;
    }

    private static double maxViolation(HarnessResult r) {
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

    private static double wrapDeg(double d) {
        d = d % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    // ---- apply (main thread) --------------------------------------------------

    public boolean hasResult() {
        return lastPlan != null;
    }

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
                while (delta > 180.0) delta -= 360.0;
                while (delta < -180.0) delta += 360.0;
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

    private void addMapped(ConstraintScene scene, Constraint c, int absTick, int segTick, int numTicks) {
        String tag = c.getField().label + "@" + absTick;
        switch (c.getField()) {
            case X:
                addScalarOrRange(scene, JumpConstraint.Mode.X, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case Z:
                addScalarOrRange(scene, JumpConstraint.Mode.Z, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case F:
                if (segTick >= numTicks) break; // no facing for the post-final state
                addScalarOrRange(scene, JumpConstraint.Mode.F, segTick, null, JumpConstraint.Op.PLUS, c, tag);
                break;
            case DX:
                if (segTick < 1) break; // velocity needs t-1
                addRangePair(scene, JumpConstraint.Mode.X, segTick, segTick - 1, c, tag);
                break;
            case DZ:
                if (segTick < 1) break;
                addRangePair(scene, JumpConstraint.Mode.Z, segTick, segTick - 1, c, tag);
                break;
        }
    }

    /** Scalar X/Z/F (one constraint) or a position range (a GE/LE pair). */
    private void addScalarOrRange(ConstraintScene scene, JumpConstraint.Mode mode, int t1, Integer t2,
                                  JumpConstraint.Op op, Constraint c, String tag) {
        if (c.isRange()) {
            scene.constraints.add(new JumpConstraint(mode, t1, t2, op, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo", true));
            scene.constraints.add(new JumpConstraint(mode, t1, t2, op, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi", true));
        } else {
            scene.constraints.add(new JumpConstraint(mode, t1, t2, op, cmp(c.getOp()), c.getValue(), tag, true));
        }
    }

    /** Velocity (dX/dZ) range: pos[t1]-pos[t2] within [lo,hi], as a GE/LE pair. */
    private void addRangePair(ConstraintScene scene, JumpConstraint.Mode mode, int t1, int t2, Constraint c, String tag) {
        scene.constraints.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo", true));
        scene.constraints.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi", true));
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

    private SolveResult buildResult(Job job, double[] yaws, PathResult path) {
        int total = 0;
        int met = 0;
        List<SolveResult.Outcome> outs = new ArrayList<>();
        for (ConstraintAt ca : job.uiConstraints) {
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

    private Double findValue(Constraint c, int segTick, int numTicks, double[] yaws, PathResult path) {
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
        double f = c.getField() == Constraint.Field.F ? v + wrapDeg(found - v) : found;
        switch (c.getOp()) {
            case GT: return f > v - MET_TOL;
            case GE: return f >= v - MET_TOL;
            case LT: return f < v + MET_TOL;
            case LE: return f <= v + MET_TOL;
            case EQ: return Math.abs(f - v) <= MET_TOL;
            default: return false;
        }
    }

    private SolveResult.Outcome outcome(Constraint c, int absTick, double found) {
        String field = c.getField().label;
        String tickLabel = "T" + (absTick + 1);
        if (c.isRange()) {
            return new SolveResult.Outcome(field, tickLabel, ConstraintText.chip(c), fmt(found), "");
        }
        double v = c.getValue();
        String relation = c.getOp().glyph + " " + ConstraintText.num(v);
        double diff = c.getField() == Constraint.Field.F ? wrapDeg(found - v) : (found - v);
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
        String marginStr = c.getOp() == Constraint.Op.EQ ? "" : (margin >= 0 ? "+" : "") + fmt(margin);
        return new SolveResult.Outcome(field, tickLabel, relation, fmt(found), marginStr);
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

    private static Spike0Scenario.Axis axis(AngleSolverState.Axis a) {
        return a == AngleSolverState.Axis.X ? Spike0Scenario.Axis.X : Spike0Scenario.Axis.Z;
    }

    private static Objective.Sense sense(AngleSolverState.Goal g) {
        return g == AngleSolverState.Goal.MAX ? Objective.Sense.MAX : Objective.Sense.MIN;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
