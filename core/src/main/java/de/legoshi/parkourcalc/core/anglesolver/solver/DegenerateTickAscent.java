package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DegenerateTickAscent {

    private static final double PIN_EPS = 5.0e-5;
    private static final double DEGEN_REL = 1.0e-5;
    private static final double FREE_START_SMOOTH = 5.0e-4;
    private static final int MAX_DEGEN = 24;
    private static final double COARSE_STEP = 1.0;
    private static final double[] REFINE_STEPS = {0.25, 0.05, 0.01, 0.002};
    private static final int REFINE_EACH = 6;
    private static final int MAX_SWEEPS = 4;
    private static final int SLP_PHASE1 = 40;
    private static final int SLP_TOTAL = 60;

    private DegenerateTickAscent() {
    }

    public static int[] degenerateTicks(JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, lin);
        if (pre == null) return null;
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return null;
        FacingPrefold.Reduced red = pre.reduce(cx, cz, lin.mMagAll(), walls);
        if (red.walls.isEmpty()) return new int[0];
        CostateDualSolver.FreeP0 freeP0 = freeStartTerm(sc, spec.objective);
        int rn = red.n;
        double[] gx;
        double[] gz;
        DiskSocpKernel.Result ipm = DiskSocpKernel.solve(rn, red.cx, red.cz, red.mMag, red.walls, freeP0);
        if (ipm != null && ipm.converged) {
            gx = ipm.gx;
            gz = ipm.gz;
        } else {
            CostateDualSolver.Result r =
                    new CostateDualSolver(rn, red.cx, red.cz, red.mMag, red.walls, freeP0).solve(0.0, null);
            if (r == null) return null;
            gx = r.gx;
            gz = r.gz;
        }
        double maxMag = 0.0;
        double[] mag = new double[rn];
        for (int v = 0; v < rn; v++) {
            mag[v] = Math.sqrt(gx[v] * gx[v] + gz[v] * gz[v]);
            if (mag[v] > maxMag) maxMag = mag[v];
        }
        if (maxMag == 0.0) return new int[0];
        double thr = DEGEN_REL * maxMag;
        List<Integer> d = new ArrayList<>();
        for (int v = 0; v < rn; v++) if (mag[v] < thr) d.add(pre.repTick(v));
        int[] out = new int[d.size()];
        for (int i = 0; i < out.length; i++) out[i] = d.get(i);
        return out;
    }

    private static CostateDualSolver.FreeP0 freeStartTerm(JumpPhysicsInputs sc, Objective obj) {
        StartBox box = sc.startBox;
        if (box == null || !box.startFree()) return null;
        return CostateDualSolver.FreeP0.forObjective(box, obj, FREE_START_SMOOTH);
    }

    public static double[] improve(ExactJumpModel exact, JumpSpec spec, double[] baseline, double feasTol,
                                   AtomicBoolean cancel, long deadlineNanos) {
        if (baseline == null) return null;
        int[] degen = degenerateTicks(spec);
        if (degen == null || degen.length == 0 || degen.length > MAX_DEGEN) return baseline;

        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        boolean max = spec.objective.sense == Objective.Sense.MAX;

        double[] base = Angles.wrapAll(baseline);
        double baseObj = objectiveOf(exact, sc, spec, base);

        double[] thetas = new double[degen.length];
        for (int i = 0; i < degen.length; i++) thetas[i] = base[degen[i]];

        double[] current = base;
        double currentObj = baseObj;
        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
            boolean improved = false;
            for (int i = 0; i < degen.length; i++) {
                if (cancel != null && cancel.get()) break;
                if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
                Cand best = scanTick(exact, spec, sc, compiled, degen, thetas, i, current, feasTol, cancel,
                        deadlineNanos, max, currentObj);
                if (best != null && best.feasible && better(best.obj, currentObj, max)) {
                    thetas[i] = best.theta;
                    current = best.yaws;
                    currentObj = best.obj;
                    improved = true;
                }
            }
            if (!improved) break;
            if (degen.length == 1) break;
        }

        if (better(currentObj, baseObj, max)) return current;
        return baseline;
    }

    private static Cand scanTick(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                 JumpConstraintCompiler.Compiled compiled, int[] degen, double[] thetas, int i,
                                 double[] seed, double feasTol, AtomicBoolean cancel, long deadlineNanos,
                                 boolean max, double floorObj) {
        Cand best = null;
        for (double th = -180.0; th < 180.0; th += COARSE_STEP) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            Cand c = complete(exact, spec, sc, compiled, degen, thetas, i, th, seed, feasTol, cancel);
            if (c != null && c.feasible && (best == null || better(c.obj, best.obj, max))) best = c;
        }
        if (best == null) return null;
        for (double step : REFINE_STEPS) {
            double center = best.theta;
            for (int k = -REFINE_EACH; k <= REFINE_EACH; k++) {
                if (k == 0) continue;
                if (cancel != null && cancel.get()) break;
                if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
                Cand c = complete(exact, spec, sc, compiled, degen, thetas, i, center + k * step, seed, feasTol, cancel);
                if (c != null && c.feasible && better(c.obj, best.obj, max)) best = c;
            }
        }
        return best;
    }

    private static Cand complete(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                 JumpConstraintCompiler.Compiled compiled, int[] degen, double[] thetas, int i,
                                 double theta, double[] seed, double feasTol, AtomicBoolean cancel) {
        List<JumpConstraint> cons = new ArrayList<>(spec.constraints);
        for (int j = 0; j < degen.length; j++) {
            addPin(cons, degen[j], j == i ? theta : thetas[j]);
        }
        JumpSpec pinned = new JumpSpec(sc, cons, spec.objective);
        double[] yaws = ClosedFormSolve.optimize(exact, pinned, feasTol, cancel);
        if (yaws == null) {
            yaws = SlpSolve.optimizeBestEffort(exact, pinned, feasTol, cancel, seed, SLP_PHASE1, SLP_TOTAL, true);
        }
        if (yaws == null) return null;
        yaws = Angles.wrapAll(yaws);
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        double viol = compiled.maxViolation(gf, path);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        return new Cand(yaws, obj, viol <= feasTol, viol, Angles.wrap(theta));
    }

    private static void addPin(List<JumpConstraint> cons, int tick, double theta) {
        double lo = theta - PIN_EPS;
        double hi = theta + PIN_EPS;
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, tick, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, lo, "ascentPinLo@" + tick));
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, tick, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.LE, hi, "ascentPinHi@" + tick));
    }

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static boolean better(double a, double b, boolean max) {
        return max ? a > b : a < b;
    }

    private static final class Cand {
        final double[] yaws;
        final double obj;
        final boolean feasible;
        final double violation;
        final double theta;

        Cand(double[] yaws, double obj, boolean feasible, double violation, double theta) {
            this.yaws = yaws;
            this.obj = obj;
            this.feasible = feasible;
            this.violation = violation;
            this.theta = theta;
        }
    }
}
