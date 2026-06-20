package de.legoshi.parkourcalc.core.anglesolver.velocity;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the initial velocities (in relative space, at a chosen anchor tick) from which a jump can be
 * landed. For each candidate velocity it re-runs the full angle-solver ladder (the jump aim is
 * re-optimized per velocity), forward-sims the recovered solution, and checks whether the path
 * actually lands on a target pad -- closing the landing into a real box so "feasible" means lands,
 * not merely "satisfied the one-sided placed wall constraints".
 *
 * <p>The anchor is the grounded tick on the takeoff block, one tick before the jump resolves: the
 * position the player can be set to, with the found velocity, so replaying from there solves the jump.
 *
 * <p>MC-free: the finder orchestrates {@link AngleSolverEngine} runs and a {@link ForwardModel}; the
 * caller supplies fresh solver state/input each evaluation via {@link ProblemFactory} (so nothing
 * leaks between candidates) and the byte-exact model for the MC version.
 */
public final class VelocityFinder {

    /** Half the player's horizontal AABB width; the landing center must keep the AABB over the pad. */
    public static final double PLAYER_HALF_WIDTH = 0.3;

    public static volatile boolean TRACE = false;

    /** Supplies a fresh, ready-to-solve solver state and input set for each candidate evaluation. */
    public interface ProblemFactory {
        AngleSolverState newState();
        InputData newInputs();
    }

    /** The target landing footprint (a block's horizontal extent). */
    public static final class Pad {
        public final double x0, x1, z0, z1;

        public Pad(double x0, double x1, double z0, double z1) {
            this.x0 = Math.min(x0, x1);
            this.x1 = Math.max(x0, x1);
            this.z0 = Math.min(z0, z1);
            this.z1 = Math.max(z0, z1);
        }

        /** AABB-on-pad overlap of a landing center, per axis; the limiting one is the support margin. */
        public double support(double landX, double landZ) {
            return Math.min(overlapX(landX), overlapZ(landZ));
        }

        public double overlapX(double landX) {
            return Math.min(landX + PLAYER_HALF_WIDTH, x1) - Math.max(landX - PLAYER_HALF_WIDTH, x0);
        }

        public double overlapZ(double landZ) {
            return Math.min(landZ + PLAYER_HALF_WIDTH, z1) - Math.max(landZ - PLAYER_HALF_WIDTH, z0);
        }
    }

    /** Where the found velocities are measured from: the grounded tick on the takeoff block. */
    public static final class Anchor {
        public final int tick;          // segment start tick (index into the input rows)
        public final Vec3dCore pos;     // player position at that tick (on the takeoff block)
        public final float yaw;         // facing at that tick
        public final double keepVy;     // entry vy carried in (overridden by the jump on a grounded tick)
        public final int rowCount;      // number of input rows (length of the box list)

        public Anchor(int tick, Vec3dCore pos, float yaw, double keepVy, int rowCount) {
            this.tick = tick;
            this.pos = pos;
            this.yaw = yaw;
            this.keepVy = keepVy;
            this.rowCount = rowCount;
        }
    }

    /** A rectangular search region over the horizontal initial velocity (vx, vz). */
    public static final class Grid {
        public final double vxLo, vxHi, vxStep, vzLo, vzHi, vzStep;

        public Grid(double vxLo, double vxHi, double vxStep, double vzLo, double vzHi, double vzStep) {
            this.vxLo = vxLo; this.vxHi = vxHi; this.vxStep = vxStep;
            this.vzLo = vzLo; this.vzHi = vzHi; this.vzStep = vzStep;
        }
    }

    /** One evaluated initial velocity: did the walls solve, did it land, where, how solidly, and the
     *  solved per-tick aim (the TAS) that produces it -- so applying the cell needs no re-solve. */
    public static final class Candidate {
        public final double vx, vz;
        public final boolean constraintsMet;
        public final boolean lands;
        public final double landX, landZ, support;
        /** Game-facing yaw per segment tick (the TAS aim), or null if no solution was found. */
        public final double[] yawsGameFacing;
        public final boolean[] force45Mask;
        public final boolean[] strafeMask;
        public final int strafeSign;

        Candidate(double vx, double vz, boolean constraintsMet, boolean lands,
                  double landX, double landZ, double support, double[] yawsGameFacing,
                  boolean[] force45Mask, boolean[] strafeMask, int strafeSign) {
            this.vx = vx;
            this.vz = vz;
            this.constraintsMet = constraintsMet;
            this.lands = lands;
            this.landX = landX;
            this.landZ = landZ;
            this.support = support;
            this.yawsGameFacing = yawsGameFacing;
            this.force45Mask = force45Mask;
            this.strafeMask = strafeMask;
            this.strafeSign = strafeSign;
        }
    }

    /** Re-cert tolerance for the finder's fast feasibility path. Unlike the engine's strict
     *  FEAS_TOL = 0 (which gates the authoritative "did it land" verdict), the finder only needs to
     *  map which velocities work and re-checks the real landing box itself, so a lattice-scale
     *  tolerance is appropriate here. It absorbs the sub-1e-4 sine-bucket quantization grazes that
     *  make the closed form return null on vertex-pinned jumps (e.g. j022), without admitting
     *  anything that clips at the game's actual resolution. */
    public static volatile double FAST_FEAS_TOL = 1.0e-6;

    public static volatile double MULTI_FALLBACK_VIOL = 2.0;

    private static double[] windowSolve(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc) {
        return LongRunSolver.solve(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false),
                LongRunSolver.LongRunConfig.of(Math.max(2, jumpCount(sc)), 1));
    }

    private double[] solveFast(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc) {
        if (jumpCount(sc) <= 1) {
            return ClosedFormSolve.optimizeRobust(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        }
        ClosedFormSolve.Result res = ClosedFormSolve.optimizeRobustGraded(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        if (res != null && res.feasible) return res.yaws;
        if (accuracy == Accuracy.FAST) return null;
        double viol = res == null ? Double.POSITIVE_INFINITY : res.violation;
        if (viol <= MULTI_FALLBACK_VIOL) {
            return windowSolve(exact, spec, sc);
        }
        return null;
    }

    private final ProblemFactory problem;
    private final ForwardModel model;
    private final Anchor anchor;
    private final int landingPosIndex;   // index into ForwardPath of the landing position (landingTick - anchor.tick)
    private final Pad pad;
    private final List<TickState> recordedStates;
    private final long perSolveMs;
    private volatile JumpPhysicsInputs.Axis objectiveAxis = JumpPhysicsInputs.Axis.X;
    private volatile boolean objectiveMax = true;
    private volatile double objConstraint = Double.NaN;

    public Pad pad() {
        return pad;
    }

    public JumpPhysicsInputs.Axis objectiveAxis() {
        return objectiveAxis;
    }

    public boolean objectiveIsX() {
        return objectiveAxis != JumpPhysicsInputs.Axis.Z;
    }

    public enum Accuracy { FAST, ACCURATE, HYPER }

    private volatile Accuracy accuracy = Accuracy.ACCURATE;

    public void setAccuracy(Accuracy a) {
        if (a != null) this.accuracy = a;
    }

    // Built once (one engine solve), then cloned per cell with a swapped initial velocity so the fast
    // path can run the closed form / SLP directly without a full engine solve per cell.
    private volatile JumpSpec templateSpec;
    private volatile boolean templateTried;
    private volatile boolean[] templateForce45Mask;
    private volatile boolean[] templateStrafeMask;
    private volatile int templateStrafeSign = 1;

    public VelocityFinder(ProblemFactory problem, ForwardModel model, Anchor anchor,
                          int landingTick, Pad pad, List<TickState> recordedStates, long perSolveMs) {
        this.problem = problem;
        this.model = model;
        this.anchor = anchor;
        this.landingPosIndex = landingTick - anchor.tick;
        this.pad = pad;
        this.recordedStates = recordedStates == null ? null : new ArrayList<>(recordedStates);
        this.perSolveMs = perSolveMs;
    }

    /** Evaluate a single initial velocity at the anchor. Tries the microsecond closed-form / SLP fast
     *  path first (re-cert at {@link #FAST_FEAS_TOL}, no CMA race), falling back to the full engine
     *  ladder only when the linear model cannot certify the walls at all. */
    public Candidate evaluate(double vx, double vz) {
        return evaluate(vx, vz, new AtomicBoolean(false));
    }

    Candidate evaluate(double vx, double vz, AtomicBoolean cancel) {
        if (accuracy == Accuracy.HYPER) return evaluateViaEngine(vx, vz, cancel);
        JumpSpec tmpl = template();
        if (tmpl != null && model instanceof ExactJumpModel) {
            return evaluateFast((ExactJumpModel) model, tmpl, vx, vz);
        }
        return evaluateViaEngine(vx, vz, cancel);
    }

    public double fieldAt(double vx, double vz) {
        return fieldAt(vx, vz, new AtomicBoolean(false));
    }

    double fieldAt(double vx, double vz, AtomicBoolean cancel) {
        return fieldNode(vx, vz, cancel)[0];
    }

    /** {@code [fieldValue, landX, landZ]}: the heatmap value (negated objective-axis margin past the
     *  landing constraint, so lands are negative and misses positive; see {@link #landingField}) and the
     *  exact landing position at the goal tick (NaN when there is no aim). */
    double[] fieldNode(double vx, double vz, AtomicBoolean cancel) {
        Candidate c = evaluate(vx, vz, cancel);
        return new double[]{cellField(c, vx, vz), c.landX, c.landZ};
    }

    double cellField(Candidate c, double vx, double vz) {
        if (c.constraintsMet) return landingField(c.landX, c.landZ);
        if (accuracy == Accuracy.HYPER) return Double.NaN;
        JumpSpec tmpl = template();
        if (tmpl == null || !(model instanceof ExactJumpModel)) return Double.NaN;
        ExactJumpModel exact = (ExactJumpModel) model;
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));
        JumpSpec spec = new JumpSpec(sc, withPadWalls(tmpl.constraints), tmpl.objective);
        objectiveAxis = spec.objective.axis;
        objectiveMax = spec.objective.sense == Objective.Sense.MAX;
        ClosedFormSolve.Result res = ClosedFormSolve.optimizeRobustGraded(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        if (res == null || res.yaws == null) return Double.NaN;
        ForwardPath missPath = model.forward(sc, sc.toGameFacings(Angles.wrapAll(res.yaws)));
        double mf = landingField(missPath.posX[landingPosIndex], missPath.posZ[landingPosIndex]);
        return mf < 0.0 ? 0.0 : mf;
    }

    private List<JumpConstraint> withPadWalls(List<JumpConstraint> base) {
        List<JumpConstraint> cons = new ArrayList<>(base);
        double h = PLAYER_HALF_WIDTH;
        cons.add(padWall(JumpConstraint.Mode.X, JumpConstraint.Cmp.GE, pad.x0 - h, "padX>="));
        cons.add(padWall(JumpConstraint.Mode.X, JumpConstraint.Cmp.LE, pad.x1 + h, "padX<="));
        cons.add(padWall(JumpConstraint.Mode.Z, JumpConstraint.Cmp.GE, pad.z0 - h, "padZ>="));
        cons.add(padWall(JumpConstraint.Mode.Z, JumpConstraint.Cmp.LE, pad.z1 + h, "padZ<="));
        return cons;
    }

    public boolean objectiveIsMax() {
        return objectiveMax;
    }

    public void setObjectiveConstraint(double value) {
        this.objConstraint = value;
    }

    public double constraintEdge() {
        if (!Double.isNaN(objConstraint)) return objConstraint;
        if (objectiveAxis == JumpPhysicsInputs.Axis.Z) {
            return objectiveMax ? pad.z1 : pad.z0;
        }
        return objectiveMax ? pad.x1 : pad.x0;
    }

    public double constraintOffset(double landX, double landZ) {
        if (Double.isNaN(landX) || Double.isNaN(landZ)) return Double.NaN;
        double coord = objectiveAxis == JumpPhysicsInputs.Axis.Z ? landZ : landX;
        return coord - constraintEdge();
    }

    private double landsBy(double landX, double landZ) {
        if (objectiveAxis == JumpPhysicsInputs.Axis.Z) {
            return objectiveMax ? landZ - pad.z0 : pad.z1 - landZ;
        }
        return objectiveMax ? landX - pad.x0 : pad.x1 - landX;
    }

    public double landingField(double landX, double landZ) {
        if (Double.isNaN(landX) || Double.isNaN(landZ)) return Double.NaN;
        if (pad.overlapX(landX) > 0.0 && pad.overlapZ(landZ) > 0.0) {
            return -landsBy(landX, landZ);
        }
        double lo, hi, coord;
        if (objectiveAxis == JumpPhysicsInputs.Axis.Z) {
            lo = pad.z0; hi = pad.z1; coord = landZ;
        } else {
            lo = pad.x0; hi = pad.x1; coord = landX;
        }
        return Math.max(0.0, Math.max(lo - coord, coord - hi));
    }

    /** Authoritative single-cell evaluation via the full engine ladder (closed form -> SLP -> CMA) at
     *  the engine's strict feasibility. Slower; use to confirm a cell the fast map left uncertain. */
    public Candidate evaluateThorough(double vx, double vz) {
        return evaluateViaEngine(vx, vz, new AtomicBoolean(false));
    }

    /** The fast path: a pure FEASIBILITY query. The landing pad is added as a two-sided constraint box
     *  (the AABB-overlap footprint), so "lands" is exactly "the spec is feasible" -- no objective to
     *  optimize, hence no same-axis degeneracy and no SLP. The robust (clearance-first) closed-form
     *  solve finds a point landing well inside the pad in microseconds, or null when no aim lands. */
    private Candidate evaluateFast(ExactJumpModel exact, JumpSpec tmpl, double vx, double vz) {
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));

        List<JumpConstraint> cons = withPadWalls(tmpl.constraints);
        JumpSpec spec = new JumpSpec(sc, cons, tmpl.objective); // objective kept but irrelevant to robust

        double[] yaws = solveFast(exact, spec, sc);
        if (yaws == null) {
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
        return landingCandidate(vx, vz, sc, yaws, templateForce45Mask, templateStrafeMask, templateStrafeSign,
                spec.objective.axis, spec.objective.sense == Objective.Sense.MAX);
    }

    /** Diagnostic: ONLY the closed form, no window/SLP/CMA fallback. {@code constraintsMet} reports whether
     *  the closed form CERTIFIED feasibility; {@code lands}/{@code support} report where its best recovered
     *  aim actually lands on the pad (forward-simmed), feasible or not. */
    public Candidate evaluateClosedFormOnly(double vx, double vz) {
        JumpSpec tmpl = template();
        if (tmpl == null || !(model instanceof ExactJumpModel)) {
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
        ExactJumpModel exact = (ExactJumpModel) model;
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));
        JumpSpec spec = new JumpSpec(sc, withPadWalls(tmpl.constraints), tmpl.objective);
        ClosedFormSolve.Result res = ClosedFormSolve.optimizeRobustGraded(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        if (res == null || res.yaws == null) {
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
        double[] gf = sc.toGameFacings(Angles.wrapAll(res.yaws));
        ForwardPath path = model.forward(sc, gf);
        double landX = path.posX[landingPosIndex];
        double landZ = path.posZ[landingPosIndex];
        double sx = pad.overlapX(landX);
        double sz = pad.overlapZ(landZ);
        boolean lands = sx > 0.0 && sz > 0.0;
        double support = spec.objective.axis == JumpPhysicsInputs.Axis.Z ? sz : sx;
        return new Candidate(vx, vz, res.feasible, lands, landX, landZ, support, gf, null, null, sc.strafeSign);
    }

    /** The single place a solved aim becomes a landing verdict. The solvers return ABSOLUTE wrapped
     *  facings; the sim runs the float-accumulated GAME facings ({@link JumpPhysicsInputs#toGameFacings}),
     *  and the MC sine table buckets on the exact float (a mod-360 difference shifts the bucket), so the
     *  two are NOT interchangeable in a forward-sim. Both the fast (closed-form) and engine (HYPER) paths
     *  funnel through here so the facing transform and pad check can never diverge between them again. */
    private Candidate landingCandidate(double vx, double vz, JumpPhysicsInputs sc, double[] absYaws,
                                       boolean[] force45Mask, boolean[] strafeMask, int strafeSign,
                                       JumpPhysicsInputs.Axis objAxis, boolean senseMax) {
        objectiveAxis = objAxis;
        objectiveMax = senseMax;
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath path = model.forward(sc, gf);
        double landX = path.posX[landingPosIndex];
        double landZ = path.posZ[landingPosIndex];
        double sx = pad.overlapX(landX);
        double sz = pad.overlapZ(landZ);
        boolean lands = sx > 0.0 && sz > 0.0;
        double support = objAxis == JumpPhysicsInputs.Axis.Z ? sz : sx;
        return new Candidate(vx, vz, true, lands, landX, landZ, support, gf,
                force45Mask, strafeMask, strafeSign);
    }


    private static int jumpCount(JumpPhysicsInputs sc) {
        if (sc.jumpPerTick == null) return sc.jumpTick >= 0 ? 1 : 0;
        int n = 0;
        for (boolean j : sc.jumpPerTick) if (j) n++;
        return n;
    }

    private JumpConstraint padWall(JumpConstraint.Mode mode, JumpConstraint.Cmp cmp, double rhs, String name) {
        return new JumpConstraint(mode, landingPosIndex, null, JumpConstraint.Op.PLUS, cmp, rhs, name);
    }

    /** Authoritative fallback: the full engine ladder (closed form -> SLP -> CMA) at the engine's
     *  strict feasibility, for cells the linear model could not certify. */
    private Candidate evaluateViaEngine(double vx, double vz, AtomicBoolean cancel) {
        AngleSolverState state = problem.newState();
        state.clearResult();
        InputData inputs = problem.newInputs();
        AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(vx, vz), inputs, t -> { }, model);
        engine.setSequentialSolve(true);
        driveEngine(engine, cancel);

        SolveResult r = state.getResult();
        if (TRACE) traceEngineResult(vx, vz, engine, r);
        if (r == null || !r.isSuccess()) {
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
        JumpSpec spec = engine.lastSpecDebug();
        JumpPhysicsInputs sc = spec.asScenario();
        double[] absYaws = new double[sc.numTicks];
        int i = 0;
        for (SolveResult.YawEntry y : r.getYaws()) {
            if (i < absYaws.length) absYaws[i++] = y.yaw;
        }
        return landingCandidate(vx, vz, sc, absYaws,
                engine.lastForce45MaskDebug(), engine.lastStrafeMaskDebug(), sc.strafeSign,
                spec.objective.axis, spec.objective.sense == Objective.Sense.MAX);
    }

    /** Build the template spec once (one engine solve; the spec is published before the solve runs, so
     *  even an infeasible probe velocity yields it). The constraints and objective are velocity-
     *  independent, so cells reuse them and only swap the scenario's initial velocity. */
    private JumpSpec template() {
        if (templateTried) return templateSpec;   // common case: no lock, so cells don't serialize
        synchronized (this) {
            if (!templateTried) {
                AngleSolverState state = problem.newState();
                state.clearResult();
                InputData inputs = problem.newInputs();
                AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(0.0, 0.0), inputs, t -> { }, model);
                engine.setSequentialSolve(true);
                driveEngine(engine, new AtomicBoolean(false));
                templateForce45Mask = engine.lastForce45MaskDebug();
                templateStrafeMask = engine.lastStrafeMaskDebug();
                JumpSpec spec = engine.lastSpecDebug();
                if (spec != null) templateStrafeSign = spec.asScenario().strafeSign;
                templateSpec = spec;   // volatile publish before the flag
                templateTried = true;
            }
        }
        return templateSpec;
    }

    private void driveEngine(AngleSolverEngine engine, AtomicBoolean cancel) {
        long t0 = System.currentTimeMillis();
        engine.solve();
        long deadline = t0 + perSolveMs;
        while (engine.isSolving() && !cancel.get() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        boolean timedOut = engine.isSolving();
        if (cancel.get() || timedOut) engine.cancel();
        engine.poll();
        if (TRACE) System.out.printf(java.util.Locale.ROOT, "[drive] elapsedMs=%d timedOut=%s perSolveMs=%d%n",
                System.currentTimeMillis() - t0, timedOut, perSolveMs);
    }

    private void traceEngineResult(double vx, double vz, AngleSolverEngine engine, SolveResult r) {
        JumpSpec sp = engine.lastSpecDebug();
        if (sp == null) {
            System.out.printf(java.util.Locale.ROOT, "[eng] v=(%.4f,%.4f) spec=NULL result=%s%n",
                    vx, vz, r == null ? "NULL" : (r.isSuccess() ? "SUCCESS" : "FAIL"));
            return;
        }
        JumpPhysicsInputs s = sp.asScenario();
        StringBuilder jp = new StringBuilder();
        if (s.jumpPerTick != null) for (boolean b : s.jumpPerTick) jp.append(b ? '1' : '0');
        StringBuilder sl = new StringBuilder();
        if (s.slipPerTick != null) for (double d : s.slipPerTick) sl.append(Double.isNaN(d) ? 'A' : 'g');
        System.out.printf(java.util.Locale.ROOT,
                "[eng] v=(%.4f,%.4f) numTicks=%d jumps=%d strafeSign=%d cons=%d result=%s obj=%s/%s@%d%n  jumpPerTick=%s%n  groundAir =%s%n  startPos=(%.3f,%.3f,%.3f) initVel=(%.4f,%.4f,%.4f) startYaw=%.3f%n",
                vx, vz, s.numTicks, jumpCount(s), s.strafeSign, sp.constraints.size(),
                r == null ? "NULL(timeout/no-result)" : (r.isSuccess() ? "SUCCESS" : "FAIL(infeasible)"),
                sp.objective.axis, sp.objective.sense, sp.objective.tick,
                jp.toString(), sl.toString(),
                s.startPos.x, s.startPos.y, s.startPos.z, s.initialVelocity.x, s.initialVelocity.y, s.initialVelocity.z, s.startYaw);
        for (JumpConstraint c : sp.constraints) {
            System.out.printf(java.util.Locale.ROOT, "    con %-12s mode=%s t=%d %s %.4f%n", c.name, c.mode, c.t1, c.cmp, c.rhs);
        }
    }

    /** Shallow copy of a scenario with a new initial velocity. The per-tick arrays are read-only in
     *  {@link ForwardModel#forward}, so they are shared (safe across threads); only the velocity differs. */
    private static JumpPhysicsInputs copyWithVelocity(JumpPhysicsInputs s, Vec3dCore vel) {
        JumpPhysicsInputs c = new JumpPhysicsInputs(s.numTicks);
        c.startPos = s.startPos;
        c.startYaw = s.startYaw;
        c.initialVelocity = vel;
        c.jumpTick = s.jumpTick;
        c.jumpPerTick = s.jumpPerTick;
        c.strafeSign = s.strafeSign;
        c.strafePerTick = s.strafePerTick;
        c.speedAmplifier = s.speedAmplifier;
        c.slipPerTick = s.slipPerTick;
        c.yawLockedPerTick = s.yawLockedPerTick;
        c.sprintPerTick = s.sprintPerTick;
        c.forwardInputPerTick = s.forwardInputPerTick;
        c.strafeInputPerTick = s.strafeInputPerTick;
        return c;
    }

    /** Evaluate every cell of the grid, row-major (vz outer, vx inner). Uses the same index-based
     *  coordinates as {@link #sweepParallel} so the two agree cell-for-cell. */
    public List<Candidate> sweep(Grid grid) {
        int nr = rows(grid), nc = cols(grid);
        List<Candidate> out = new ArrayList<>(nr * nc);
        for (int r = 0; r < nr; r++) {
            double vz = grid.vzLo + r * grid.vzStep;
            for (int c = 0; c < nc; c++) {
                out.add(evaluate(grid.vxLo + c * grid.vxStep, vz));
            }
        }
        return out;
    }

    public static int rows(Grid g) { return (int) Math.round((g.vzHi - g.vzLo) / g.vzStep) + 1; }
    public static int cols(Grid g) { return (int) Math.round((g.vxHi - g.vxLo) / g.vxStep) + 1; }

    /** Notified as each cell completes (off the calling thread), for progressive heatmap fill. */
    public interface CellListener {
        void onCell(int row, int col, Candidate c);
    }

    /**
     * Evaluate the whole grid across a thread pool. Cells are independent (each builds its own engine,
     * state and inputs via the factory; the model is immutable), so this scales near-linearly with
     * cores. The returned list is row-major and index-stable regardless of completion order; the
     * optional listener fires per cell as results land (thread-safe to call into).
     */
    public List<Candidate> sweepParallel(Grid grid, int threads, CellListener listener) {
        return sweepParallel(grid, threads, new AtomicBoolean(false), listener);
    }

    public List<Candidate> sweepParallel(Grid grid, int threads, AtomicBoolean cancel, CellListener listener) {
        int nr = rows(grid), nc = cols(grid);
        Candidate[] out = new Candidate[nr * nc];
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        List<Future<?>> futures = new ArrayList<>();
        for (int r = 0; r < nr; r++) {
            final int row = r;
            final double vz = grid.vzLo + r * grid.vzStep;
            for (int c = 0; c < nc; c++) {
                final int col = c;
                final double vx = grid.vxLo + c * grid.vxStep;
                futures.add(pool.submit(() -> {
                    if (cancel.get()) return;
                    Candidate cand = evaluate(vx, vz, cancel);
                    out[row * nc + col] = cand;
                    if (listener != null) {
                        synchronized (out) { listener.onCell(row, col, cand); }
                    }
                }));
            }
        }
        pool.shutdown();
        try {
            for (Future<?> f : futures) f.get();
        } catch (Exception e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        List<Candidate> list = new ArrayList<>(out.length);
        Collections.addAll(list, out);
        return list;
    }

    public interface FieldListener {
        void onNode(int row, int col, double field, Candidate cand);
    }

    public float[] sweepFieldParallel(Grid grid, int cornerCols, int cornerRows, int threads,
                                      AtomicBoolean cancel, FieldListener listener) {
        int nc = Math.max(1, cornerCols);
        int nr = Math.max(1, cornerRows);
        double vxStep = nc > 1 ? (grid.vxHi - grid.vxLo) / (nc - 1) : 0.0;
        double vzStep = nr > 1 ? (grid.vzHi - grid.vzLo) / (nr - 1) : 0.0;
        float[] out = new float[nr * nc];
        java.util.Arrays.fill(out, Float.NaN);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        List<Future<?>> futures = new ArrayList<>();
        for (int r = 0; r < nr; r++) {
            final int row = r;
            final double vz = grid.vzLo + r * vzStep;
            for (int c = 0; c < nc; c++) {
                final int col = c;
                final double vx = grid.vxLo + c * vxStep;
                futures.add(pool.submit(() -> {
                    if (cancel.get()) return;
                    Candidate cand = evaluate(vx, vz, cancel);
                    double field = cellField(cand, vx, vz);
                    out[row * nc + col] = (float) field;
                    if (listener != null) {
                        synchronized (out) { listener.onNode(row, col, field, cand); }
                    }
                }));
            }
        }
        pool.shutdown();
        try {
            for (Future<?> fut : futures) fut.get();
        } catch (Exception e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return out;
    }

    /** The landers from a sweep, sorted solidest-first (largest support). */
    public static List<Candidate> rankedLanders(List<Candidate> candidates) {
        List<Candidate> landers = new ArrayList<>();
        for (Candidate c : candidates) if (c != null && c.lands) landers.add(c);
        landers.sort(Comparator.comparingDouble((Candidate c) -> c.support).reversed());
        return landers;
    }

    /** Build the box list the engine reads its launch state from: all placeholder except the anchor. */
    private BoxController buildBoxes(double vx, double vz) {
        BoxController boxes = new BoxController();
        for (int i = 0; i < anchor.rowCount; i++) {
            TickState real = recordedStates != null && i < recordedStates.size() ? recordedStates.get(i) : null;
            if (i == anchor.tick) {
                boxes.add(new TickState(anchor.pos, false, false, false, anchor.yaw,
                        Collections.<Vec3dCore>emptyList(), new Vec3dCore(vx, anchor.keepVy, vz),
                        false, Double.NaN));
            } else if (real != null) {
                boxes.add(real);
            } else {
                boxes.add(new TickState(Vec3dCore.ZERO, false, false, false, 0f,
                        Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
            }
        }
        return boxes;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
