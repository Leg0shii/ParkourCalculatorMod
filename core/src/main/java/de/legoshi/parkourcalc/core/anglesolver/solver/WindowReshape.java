package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class WindowReshape {

    static final int MAX_WINDOW_TICKS = 10;
    static final double BRAKE_DEG = 45.0;
    private static final double RAMP_MAX_DEG = 40.0;
    private static final double RAMP_STEP_DEG = 4.0;
    private static final double REPAIR_RAW_VIOL = 0.05;
    private static final int REPAIR_SEEDS = 16;
    private static final double FEAS_TOL = 0.0;

    private WindowReshape() {
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
        for (int[] w : windows(sc, anchor, y)) {
            if (expired(cancel, deadlineNanos)) break;
            double[] r = reshape(model, sc, compiled, spec, cs, held, anchor, y, w[0], w[1], maxObj, objFloor, best, cancel, deadlineNanos);
            if (r == null) continue;
            y = r;
            best = TurnShape.of(anchor, y);
        }
        return y;
    }

    static List<int[]> windows(JumpPhysicsInputs sc, double anchor, double[] y) {
        int n = y.length;
        double[] d = TurnShape.deltas(anchor, y);
        List<int[]> out = new ArrayList<>();
        int t = 0;
        while (t < n) {
            if (!Double.isNaN(sc.slipAt(t))) {
                t++;
                continue;
            }
            int a = t;
            while (t < n && Double.isNaN(sc.slipAt(t))) t++;
            int b = t - 1;
            List<Integer> starts = new ArrayList<>();
            starts.add(a);
            for (int k = a; k < b; k++) if (Math.abs(d[k]) >= BRAKE_DEG) starts.add(k + 1);
            for (int s : starts) {
                if (b - s + 1 < 2 || b - s + 1 > MAX_WINDOW_TICKS) continue;
                if (monotone(d, s, b)) continue;
                out.add(new int[] {s, b});
            }
        }
        return out;
    }

    private static boolean monotone(double[] d, int lo, int hi) {
        int sign = 0;
        double prevMag = -1.0;
        for (int k = lo; k <= hi; k++) {
            if (Math.abs(d[k]) <= TurnShape.FLOOR_DEG) continue;
            int s = d[k] > 0.0 ? 1 : -1;
            if (sign != 0 && s != sign) return false;
            sign = s;
            if (prevMag >= 0.0 && Math.abs(d[k]) > prevMag + TurnShape.ACCEL_FLOOR_DEG) return false;
            prevMag = Math.abs(d[k]);
        }
        return true;
    }

    private static double[] ramp(double[] y, double base, int lo, int hi, double first, double last) {
        double[] c = y.clone();
        int n = hi - lo + 1;
        double cum = base;
        for (int k = 0; k < n; k++) {
            double dk = n == 1 ? first : first + (last - first) * k / (n - 1.0);
            cum = Angles.wrap(cum + dk);
            c[lo + k] = cum;
        }
        return c;
    }

    private static final class Seed {
        final double first;
        final double last;
        final double viol;

        Seed(double first, double last, double viol) {
            this.first = first;
            this.last = last;
            this.viol = viol;
        }
    }

    private static double[] reshape(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                    JumpSpec spec, List<JumpConstraint> cs, boolean[] held, double anchor, double[] y,
                                    int lo, int hi, boolean maxObj, double objFloor, TurnShape best,
                                    AtomicBoolean cancel, long deadlineNanos) {
        for (int t = lo; t <= hi; t++) if (held[t]) return null;
        double base = lo == 0 ? anchor : y[lo - 1];
        List<Seed> seeds = new ArrayList<>();
        for (double first = -RAMP_MAX_DEG; first <= RAMP_MAX_DEG; first += RAMP_STEP_DEG) {
            for (double last = -RAMP_MAX_DEG; last <= RAMP_MAX_DEG; last += RAMP_STEP_DEG) {
                if (first * last < 0.0) continue;
                double v = DeWiggle.violation(model, sc, compiled, ramp(y, base, lo, hi, first, last));
                if (v <= REPAIR_RAW_VIOL) seeds.add(new Seed(first, last, v));
            }
        }
        seeds.sort((p, q) -> Double.compare(p.viol, q.viol));
        double[] bestY = null;
        TurnShape bestShape = best;
        for (int i = 0; i < Math.min(REPAIR_SEEDS, seeds.size()); i++) {
            if (expired(cancel, deadlineNanos)) break;
            Seed sd = seeds.get(i);
            double[] c = ramp(y, base, lo, hi, sd.first, sd.last);
            if (DeWiggle.violation(model, sc, compiled, c) > FEAS_TOL) {
                c = DeWiggle.repair(model, sc, compiled, spec, cs, c, lo, hi, held, cancel);
                if (c == null || DeWiggle.violation(model, sc, compiled, c) > FEAS_TOL) continue;
            }
            double obj = DeWiggle.objAt(model, sc, spec.objective, c);
            if (maxObj ? obj < objFloor : obj > objFloor) continue;
            TurnShape shape = TurnShape.of(anchor, c);
            if (!shape.betterThan(bestShape)) continue;
            bestShape = shape;
            bestY = Angles.wrapAll(c);
        }
        return bestY;
    }

    private static boolean expired(AtomicBoolean cancel, long deadlineNanos) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }
}
