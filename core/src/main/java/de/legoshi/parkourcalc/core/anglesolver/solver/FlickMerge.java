package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class FlickMerge {

    static final int MAX_RUN_TICKS = 3;
    static final int MAX_CLUSTER_TICKS = 10;
    static final int MIN_RUNS = 3;
    private static final double[] HOLD_DEG = {
            60.0, 75.0, 90.0, 105.0, 120.0, 135.0, 150.0, 165.0, 180.0,
            -165.0, -150.0, -135.0, -120.0, -105.0, -90.0, -75.0, -60.0};
    private static final double FEAS_TOL = 0.0;

    private FlickMerge() {
    }

    static double[] run(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped, AtomicBoolean cancel,
                        double giveBack, long deadlineNanos) {
        if (yawsAbsWrapped == null || yawsAbsWrapped.length < 3) return yawsAbsWrapped;
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> cs = DeWiggle.usable(sc, spec);
        if (cs.isEmpty()) return yawsAbsWrapped;
        boolean[] held = DeWiggle.heldByFacing(sc.numTicks, spec);
        double anchor = sc.startYaw;
        double[] y = Angles.wrapAll(yawsAbsWrapped.clone());
        if (DeWiggle.violation(model, sc, compiled, y) > FEAS_TOL) return yawsAbsWrapped;
        boolean maxObj = spec.objective.sense == Objective.Sense.MAX;
        double objFloor = DeWiggle.objAt(model, sc, spec.objective, y) + (maxObj ? -giveBack : giveBack);

        TurnShape best = TurnShape.of(anchor, y);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int[] cl : clusters(sc, anchor, y)) {
                if (expired(cancel, deadlineNanos)) return y;
                double[] merged = mergeCluster(model, sc, compiled, spec, cs, held, anchor, y, cl[0], cl[1],
                        maxObj, objFloor, best, cancel, deadlineNanos);
                if (merged == null) continue;
                y = merged;
                best = TurnShape.of(anchor, y);
                improved = true;
                break;
            }
        }
        return y;
    }

    static List<int[]> clusters(JumpPhysicsInputs sc, double anchor, double[] y) {
        List<int[]> runs = new ArrayList<>();
        for (int[] r : DeWiggle.runs(anchor, y)) {
            int start = r[0];
            for (int t = r[0]; t <= r[1]; t++) {
                boolean grounded = !Double.isNaN(sc.slipAt(t));
                if (grounded) {
                    if (t > start) runs.add(new int[] {start, t - 1});
                    start = t + 1;
                }
            }
            if (start <= r[1]) runs.add(new int[] {start, r[1]});
        }
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < runs.size()) {
            int j = i;
            while (j < runs.size() && runs.get(j)[1] - runs.get(j)[0] + 1 <= MAX_RUN_TICKS
                    && runs.get(j)[1] - runs.get(i)[0] + 1 <= MAX_CLUSTER_TICKS
                    && (j == i || runs.get(j)[0] == runs.get(j - 1)[1] + 1)) {
                j++;
            }
            if (j - i >= MIN_RUNS) out.add(new int[] {runs.get(i)[0], runs.get(j - 1)[1]});
            i = Math.max(j, i + 1);
        }
        return out;
    }

    private static final double COARSE_STEP_DEG = 5.0;
    private static final int REFINE_SEEDS = 24;
    private static final int REFINE_ITERS = 48;

    private static final class Seed {
        final int m;
        final int o;
        final double hold;
        final double viol;

        Seed(int m, int o, double hold, double viol) {
            this.m = m;
            this.o = o;
            this.hold = hold;
            this.viol = viol;
        }
    }

    private static double[] shape(double[] y, double base, int lo, int hi, int m, int o, double hold) {
        double[] c = y.clone();
        for (int k = lo; k <= hi; k++) c[k] = base;
        for (int k = lo + o; k < lo + o + m; k++) c[k] = Angles.wrap(base + hold);
        return c;
    }

    private static double[] mergeCluster(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                         JumpSpec spec, List<JumpConstraint> cs, boolean[] held, double anchor, double[] y,
                                         int lo, int hi, boolean maxObj, double objFloor, TurnShape best,
                                         AtomicBoolean cancel, long deadlineNanos) {
        for (int t = lo; t <= hi; t++) if (held[t]) return null;
        double base = lo == 0 ? anchor : y[lo - 1];
        int span = hi - lo + 1;
        List<Seed> seeds = new ArrayList<>();
        for (int m = 1; m <= span; m++) {
            for (int o = 0; o + m <= span; o++) {
                for (double hold = -180.0 + COARSE_STEP_DEG; hold <= 180.0; hold += COARSE_STEP_DEG) {
                    if (Math.abs(hold) < 30.0) continue;
                    double v = DeWiggle.violation(model, sc, compiled, shape(y, base, lo, hi, m, o, hold));
                    seeds.add(new Seed(m, o, hold, v));
                }
            }
        }
        seeds.sort((a, b) -> Double.compare(a.viol, b.viol));
        double[] bestY = null;
        TurnShape bestShape = best;
        double diagBest = Double.POSITIVE_INFINITY;
        long t0 = System.nanoTime();
        for (int i = 0; i < Math.min(REFINE_SEEDS, seeds.size()); i++) {
            if (expired(cancel, deadlineNanos)) break;
            Seed sd = seeds.get(i);
            double hold = sd.hold;
            double step = COARSE_STEP_DEG / 2.0;
            double[] cur = landed(model, sc, compiled, spec, cs, held, y, base, lo, hi, sd.m, sd.o, hold, cancel);
            double curV = cur == null ? Double.POSITIVE_INFINITY : 0.0;
            if (cur == null) curV = DeWiggle.violation(model, sc, compiled, shape(y, base, lo, hi, sd.m, sd.o, hold));
            for (int it = 0; it < REFINE_ITERS && curV > FEAS_TOL; it++) {
                if (expired(cancel, deadlineNanos)) break;
                boolean moved = false;
                for (double dir : new double[] {1.0, -1.0}) {
                    double h = hold + dir * step;
                    double[] c = landed(model, sc, compiled, spec, cs, held, y, base, lo, hi, sd.m, sd.o, h, cancel);
                    double v = c != null ? 0.0 : DeWiggle.violation(model, sc, compiled, shape(y, base, lo, hi, sd.m, sd.o, h));
                    if (v < curV) {
                        hold = h;
                        curV = v;
                        cur = c;
                        moved = true;
                        break;
                    }
                }
                if (!moved) step *= 0.5;
            }
            diagBest = Math.min(diagBest, curV);
            if (cur == null) continue;
            double obj = DeWiggle.objAt(model, sc, spec.objective, cur);
            if (maxObj ? obj < objFloor : obj > objFloor) continue;
            TurnShape shape = TurnShape.of(anchor, cur);
            if (shape.reversals >= best.reversals) continue;
            if (!shape.betterThan(bestShape)) continue;
            bestShape = shape;
            bestY = Angles.wrapAll(cur);
        }
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "flick merge %d..%d seeds=%d bestViol=%.3e adopted=%s ms=%d",
                    lo, hi, seeds.size(), diagBest, bestY != null, (System.nanoTime() - t0) / 1_000_000L);
        }
        return bestY;
    }

    private static double[] landed(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                   JumpSpec spec, List<JumpConstraint> cs, boolean[] held, double[] y, double base,
                                   int lo, int hi, int m, int o, double hold, AtomicBoolean cancel) {
        double[] c = shape(y, base, lo, hi, m, o, hold);
        if (DeWiggle.violation(model, sc, compiled, c) <= FEAS_TOL) return c;
        double[] r = DeWiggle.repair(model, sc, compiled, spec, cs, c, lo, hi, held, cancel);
        if (r == null || DeWiggle.violation(model, sc, compiled, r) > FEAS_TOL) return null;
        return r;
    }

    private static boolean expired(AtomicBoolean cancel, long deadlineNanos) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }
}
