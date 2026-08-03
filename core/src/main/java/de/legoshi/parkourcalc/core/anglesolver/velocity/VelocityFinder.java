package de.legoshi.parkourcalc.core.anglesolver.velocity;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
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

/** Sweeps the initial-velocity plane and, for each candidate velocity, runs the exact same angle solve
 *  the Angle Solver runs: the user's per-tick constraints plus their objective. A velocity "lands" iff
 *  that solve is feasible (every constraint met); there is no landing pad. The per-cell field shown on
 *  the heatmap is the objective offset, the optimized coordinate's margin past its constraint edge. */
public final class VelocityFinder {

    public interface ProblemFactory {
        AngleSolverState newState();
        InputData newInputs();
    }

    public static final class Anchor {
        public final int tick;
        public final Vec3dCore pos;
        public final float yaw;
        public final double keepVy;
        public final int rowCount;

        public Anchor(int tick, Vec3dCore pos, float yaw, double keepVy, int rowCount) {
            this.tick = tick;
            this.pos = pos;
            this.yaw = yaw;
            this.keepVy = keepVy;
            this.rowCount = rowCount;
        }
    }

    public static final class Grid {
        public final double vxLo, vxHi, vxStep, vzLo, vzHi, vzStep;

        public Grid(double vxLo, double vxHi, double vxStep, double vzLo, double vzHi, double vzStep) {
            this.vxLo = vxLo; this.vxHi = vxHi; this.vxStep = vxStep;
            this.vzLo = vzLo; this.vzHi = vzHi; this.vzStep = vzStep;
        }
    }

    public static final class Candidate {
        public final double vx, vz;
        public final boolean constraintsMet;
        public final boolean lands;
        public final double landX, landZ, support;
        /** Objective-axis coordinate at the tick where the objective constraint lives, against which the
         *  offset is measured. NaN when infeasible or no objective constraint exists. */
        public final double objValue;
        public final double[] yawsGameFacing;
        public final boolean[] force45Mask;
        public final boolean[] strafeMask;
        public final int strafeSign;

        Candidate(double vx, double vz, boolean constraintsMet, boolean lands,
                  double landX, double landZ, double support, double objValue, double[] yawsGameFacing,
                  boolean[] force45Mask, boolean[] strafeMask, int strafeSign) {
            this.vx = vx;
            this.vz = vz;
            this.constraintsMet = constraintsMet;
            this.lands = lands;
            this.landX = landX;
            this.landZ = landZ;
            this.support = support;
            this.objValue = objValue;
            this.yawsGameFacing = yawsGameFacing;
            this.force45Mask = force45Mask;
            this.strafeMask = strafeMask;
            this.strafeSign = strafeSign;
        }
    }

    private static final double FAST_FEAS_TOL = 1.0e-6;

    private static final double MULTI_FALLBACK_VIOL = 2.0;

    private static double[] windowSolve(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc) {
        return LongRunSolver.solve(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false),
                LongRunSolver.LongRunConfig.of(Math.max(2, jumpCount(sc)), 1));
    }

    private static final class FastSolve {
        final double[] yaws;
        final ClosedFormSolve.Result closed;

        FastSolve(double[] yaws, ClosedFormSolve.Result closed) {
            this.yaws = yaws;
            this.closed = closed;
        }
    }

    private FastSolve solveFastCapturing(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc) {
        ClosedFormSolve.Result res = ClosedFormSolve.optimizeRobustGraded(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        if (jumpCount(sc) <= 1) {
            return new FastSolve(res != null && res.feasible ? res.yaws : null, res);
        }
        if (res != null && res.feasible) return new FastSolve(res.yaws, res);
        if (accuracy == Accuracy.FAST) return new FastSolve(null, res);
        double viol = res == null ? Double.POSITIVE_INFINITY : res.violation;
        if (viol <= MULTI_FALLBACK_VIOL) {
            return new FastSolve(windowSolve(exact, spec, sc), res);
        }
        return new FastSolve(null, res);
    }

    private double[] solveFast(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc) {
        return solveFastCapturing(exact, spec, sc).yaws;
    }

    private final ProblemFactory problem;
    private final ForwardModel model;
    private final Anchor anchor;
    private final int landingPosIndex;
    private final List<TickState> recordedStates;
    private final long perSolveMs;
    private volatile JumpPhysicsInputs.Axis objectiveAxis = JumpPhysicsInputs.Axis.X;
    private volatile boolean objectiveMax = true;
    private volatile double objConstraint = Double.NaN;
    private volatile int objEdgeTick = -1;

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

    private volatile JumpSpec templateSpec;
    private volatile boolean templateTried;
    private volatile boolean[] templateForce45Mask;
    private volatile boolean[] templateStrafeMask;
    private volatile int templateStrafeSign = 1;

    public VelocityFinder(ProblemFactory problem, ForwardModel model, Anchor anchor,
                          int landingTick, List<TickState> recordedStates, long perSolveMs) {
        this.problem = problem;
        this.model = model;
        this.anchor = anchor;
        this.landingPosIndex = landingTick - anchor.tick;
        this.recordedStates = recordedStates == null ? null : new ArrayList<>(recordedStates);
        this.perSolveMs = perSolveMs;
    }

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

    double[] fieldNode(double vx, double vz, AtomicBoolean cancel) {
        CellResult cr = evaluateAndField(vx, vz, cancel);
        return new double[]{cr.field, cr.cand.landX, cr.cand.landZ};
    }

    static final class CellResult {
        final Candidate cand;
        final double field;

        CellResult(Candidate cand, double field) {
            this.cand = cand;
            this.field = field;
        }
    }

    CellResult evaluateAndField(double vx, double vz, AtomicBoolean cancel) {
        if (accuracy != Accuracy.HYPER) {
            JumpSpec tmpl = template();
            if (tmpl != null && model instanceof ExactJumpModel) {
                return evaluateFastWithField((ExactJumpModel) model, tmpl, vx, vz);
            }
        }
        Candidate c = evaluate(vx, vz, cancel);
        return new CellResult(c, cellField(c, vx, vz));
    }

    private CellResult evaluateFastWithField(ExactJumpModel exact, JumpSpec tmpl, double vx, double vz) {
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));
        JumpSpec spec = new JumpSpec(sc, tmpl.constraints, tmpl.objective);

        FastSolve fs = solveFastCapturing(exact, spec, sc);
        if (fs.yaws == null) {
            objectiveAxis = spec.objective.axis;
            objectiveMax = spec.objective.sense == Objective.Sense.MAX;
            Candidate cand = new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
            return new CellResult(cand, missField(fs.closed));
        }
        Candidate cand = landingCandidate(vx, vz, sc, fs.yaws, templateForce45Mask, templateStrafeMask, templateStrafeSign,
                spec.objective.axis, spec.objective.sense == Objective.Sense.MAX);
        return new CellResult(cand, fieldFor(cand));
    }

    /** Infeasible cell: the field is the (positive) constraint violation of the best closed-form try, so
     *  the heatmap can shade near-misses; NaN when no solution was produced at all. */
    private double missField(ClosedFormSolve.Result res) {
        if (res == null || res.yaws == null) return Double.NaN;
        return Math.max(0.0, res.violation);
    }

    double cellField(Candidate c, double vx, double vz) {
        if (c.constraintsMet) return fieldFor(c);
        if (accuracy == Accuracy.HYPER) return Double.NaN;
        JumpSpec tmpl = template();
        if (tmpl == null || !(model instanceof ExactJumpModel)) return Double.NaN;
        ExactJumpModel exact = (ExactJumpModel) model;
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));
        JumpSpec spec = new JumpSpec(sc, tmpl.constraints, tmpl.objective);
        objectiveAxis = spec.objective.axis;
        objectiveMax = spec.objective.sense == Objective.Sense.MAX;
        ClosedFormSolve.Result res = ClosedFormSolve.optimizeRobustGraded(exact, spec, FAST_FEAS_TOL, new AtomicBoolean(false));
        return missField(res);
    }

    /** The objective constraint the offset is measured against: its value plus the segment-relative tick it
     *  lives at (the trajectory's coordinate is read there, not at the landing). segTick < 0 = no such
     *  constraint. */
    public void setObjectiveConstraint(double value, int segTick) {
        this.objConstraint = value;
        this.objEdgeTick = segTick;
    }

    /** The objective-axis constraint the offset is measured against (the constraint added for the solved
     *  objective). NaN when none was set. */
    public double constraintEdge() {
        return objConstraint;
    }

    /** Signed margin past the objective edge in the improving direction (>= 0 once feasible), measured at
     *  the edge constraint's own tick. NaN when infeasible or no objective edge exists. */
    public double offsetOf(Candidate c) {
        if (c == null || Double.isNaN(c.objValue) || Double.isNaN(objConstraint)) return Double.NaN;
        return objectiveMax ? (c.objValue - objConstraint) : (objConstraint - c.objValue);
    }

    /** Heatmap field for a feasible candidate: -|offset| so the color pipeline shades a larger margin past
     *  the edge more strongly. Flat when no objective edge exists. */
    public double fieldFor(Candidate c) {
        double m = offsetOf(c);
        if (Double.isNaN(m)) return -1.0;
        return -Math.abs(m);
    }

    public boolean objectiveIsMax() {
        return objectiveMax;
    }

    public Candidate evaluateThorough(double vx, double vz) {
        return evaluateViaEngine(vx, vz, new AtomicBoolean(false));
    }

    private Candidate evaluateFast(ExactJumpModel exact, JumpSpec tmpl, double vx, double vz) {
        JumpPhysicsInputs base = tmpl.asScenario();
        JumpPhysicsInputs sc = copyWithVelocity(base, new Vec3dCore(vx, base.initialVelocity.y, vz));

        JumpSpec spec = new JumpSpec(sc, tmpl.constraints, tmpl.objective);

        double[] yaws = solveFast(exact, spec, sc);
        if (yaws == null) {
            objectiveAxis = spec.objective.axis;
            objectiveMax = spec.objective.sense == Objective.Sense.MAX;
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
        return landingCandidate(vx, vz, sc, yaws, templateForce45Mask, templateStrafeMask, templateStrafeSign,
                spec.objective.axis, spec.objective.sense == Objective.Sense.MAX);
    }

    private Candidate landingCandidate(double vx, double vz, JumpPhysicsInputs sc, double[] absYaws,
                                       boolean[] force45Mask, boolean[] strafeMask, int strafeSign,
                                       JumpPhysicsInputs.Axis objAxis, boolean senseMax) {
        objectiveAxis = objAxis;
        objectiveMax = senseMax;
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath path = model.forward(sc, gf);
        double landX = path.posX[landingPosIndex];
        double landZ = path.posZ[landingPosIndex];
        int edgeTick = objEdgeTick >= 0 && objEdgeTick <= landingPosIndex ? objEdgeTick : landingPosIndex;
        double objValue = path.getPos(edgeTick, objAxis);
        double m = Double.isNaN(objValue) || Double.isNaN(objConstraint) ? Double.NaN
                : (senseMax ? objValue - objConstraint : objConstraint - objValue);
        double support = Double.isNaN(m) ? 0.0 : Math.abs(m);
        return new Candidate(vx, vz, true, true, landX, landZ, support, objValue, gf,
                force45Mask, strafeMask, strafeSign);
    }

    private static int jumpCount(JumpPhysicsInputs sc) {
        if (sc.jumpPerTick == null) return sc.jumpTick >= 0 ? 1 : 0;
        int n = 0;
        for (boolean j : sc.jumpPerTick) if (j) n++;
        return n;
    }

    private Candidate evaluateViaEngine(double vx, double vz, AtomicBoolean cancel) {
        AngleSolverState state = problem.newState();
        state.clearResult();
        state.setStopOnFeasible(true);
        InputData inputs = problem.newInputs();
        AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(vx, vz), inputs, t -> { }, model);
        engine.setSequentialSolve(true);
        driveEngine(engine, cancel);

        SolveResult r = state.getResult();
        JumpSpec spec = engine.lastSpecDebug();
        if (spec != null) {
            objectiveAxis = spec.objective.axis;
            objectiveMax = spec.objective.sense == Objective.Sense.MAX;
        }
        if (r == null || !r.isSuccess() || spec == null) {
            return new Candidate(vx, vz, false, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, null, null, 0);
        }
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

    private JumpSpec template() {
        if (templateTried) return templateSpec;
        synchronized (this) {
            if (!templateTried) {
                AngleSolverState state = problem.newState();
                state.clearResult();
                state.setStopOnFeasible(true);
                InputData inputs = problem.newInputs();
                AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(0.0, 0.0), inputs, t -> { }, model);
                engine.setSequentialSolve(true);
                driveEngine(engine, new AtomicBoolean(false));
                templateForce45Mask = engine.lastForce45MaskDebug();
                templateStrafeMask = engine.lastStrafeMaskDebug();
                JumpSpec spec = engine.lastSpecDebug();
                if (spec != null) templateStrafeSign = spec.asScenario().strafeSign;
                templateSpec = spec;
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
    }

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
        c.surfacePerTick = s.surfacePerTick;
        c.sneakPerTick = s.sneakPerTick;
        c.yawLockedPerTick = s.yawLockedPerTick;
        c.sprintPerTick = s.sprintPerTick;
        c.forwardInputPerTick = s.forwardInputPerTick;
        c.strafeInputPerTick = s.strafeInputPerTick;
        return c;
    }

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

    public interface CellListener {
        void onCell(int row, int col, Candidate c);
    }

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
                    CellResult cr = evaluateAndField(vx, vz, cancel);
                    out[row * nc + col] = (float) cr.field;
                    if (listener != null) {
                        synchronized (out) { listener.onNode(row, col, cr.field, cr.cand); }
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

    public static List<Candidate> rankedLanders(List<Candidate> candidates) {
        List<Candidate> landers = new ArrayList<>();
        for (Candidate c : candidates) if (c != null && c.lands) landers.add(c);
        landers.sort(Comparator.comparingDouble((Candidate c) -> c.support).reversed());
        return landers;
    }

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
