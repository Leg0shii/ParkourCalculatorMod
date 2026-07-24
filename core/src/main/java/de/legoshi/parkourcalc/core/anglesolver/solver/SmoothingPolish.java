package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.concurrent.atomic.AtomicBoolean;

/** Ties off underdetermined solves: a feasibility- and objective-preserving descent on the total
 *  turning of the facing path. Constraints rarely pin every tick, so CMA-ES / the dual leave the
 *  free ticks wherever the search happened to land and the solved path wiggles. This pass minimizes
 *  the sum of squared facing deltas (anchored at the launch yaw) under two hard gates evaluated on
 *  the same wrap + toGameFacings + byte-exact forward chain as the polish:
 *  <ul>
 *    <li>strict feasibility (FEAS_TOL = 0): smoothing never clips a wall;</li>
 *    <li>at lambda 0 the objective never drops below the value the solve achieved: smoothing trades
 *        nothing for looks, and where every tick is load-bearing the pass is simply a no-op. With a
 *        smooth lambda the gate is the scored objective instead: moves may spend raw objective at
 *        the lambda exchange rate for less wiggle, ratcheting on the scored value.</li>
 *  </ul>
 *  Single-tick moves pull each facing toward its neighbors' angular midpoint with bisected steps;
 *  joint moves on nearby pairs make the canceling adjustments the objective floor blocks on single
 *  ticks (the smoothing mirror of the polish's block-2 island hops). Every accepted move strictly
 *  lowers roughness, so the descent terminates; the round and eval caps bound the cost regardless
 *  (worst case well under the solve itself). */
public final class SmoothingPolish {

    private static final double FEAS_TOL = 0.0;
    /** Pulls below the ~0.0055deg sine-bucket width cannot change the path; skip them. */
    private static final double MIN_MOVE_DEG = 1.0e-4;

    public static final class Config {
        public int maxRounds = 24;
        public int maxEvals = 24_000;
        public int evalsPerTick = 400;
        public int pairSpan = 3;
        /** Step fractions toward the midpoint target; bisection finds the largest gate-passing pull. */
        public double[] fractions = {1.0, 0.5, 0.25, 0.125};
    }

    private SmoothingPolish() {
    }

    /** Returns the smoothed absolute wrapped facings, or {@code yawsAbsWrapped} itself when the start
     *  is not strictly feasible (an honestly-failed solve stays untouched for the panel to report). */
    public static double[] smooth(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped, AtomicBoolean cancel) {
        return smooth(model, spec, yawsAbsWrapped, cancel, new Config());
    }

    public static double[] smooth(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped, AtomicBoolean cancel,
                                  Config cfg) {
        int n = yawsAbsWrapped.length;
        if (n < 2) return yawsAbsWrapped;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double sign = spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0;

        Work w = new Work(model, sc, compiled, spec.objective, sign, cancel, cfg,
                Math.max(cfg.maxEvals, n * cfg.evalsPerTick));
        double[] y = Angles.wrapAll(yawsAbsWrapped.clone());
        double start = w.eval(y);
        if (start == Double.POSITIVE_INFINITY) return yawsAbsWrapped;
        w.floor = start;
        w.scoredFloor = start + spec.objective.smoothPenalty(y);
        double rough = roughness(sc.startYaw, y);

        for (int round = 0; round < cfg.maxRounds && w.evals < w.evalCap; round++) {
            boolean moved = false;
            for (int t = 0; t < n; t++) {
                double pulled = pullSingle(w, y, t, rough);
                if (pulled < rough) { rough = pulled; moved = true; }
            }
            for (int i = 0; i < n; i++) {
                for (int d = 1; d <= cfg.pairSpan && i + d < n; d++) {
                    double pulled = pullPair(w, y, i, i + d, rough);
                    if (pulled < rough) { rough = pulled; moved = true; }
                    pulled = pullTransfer(w, y, i, i + d, rough);
                    if (pulled < rough) { rough = pulled; moved = true; }
                    pulled = pullTransfer(w, y, i + d, i, rough);
                    if (pulled < rough) { rough = pulled; moved = true; }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    /** Total squared turning of the facing path, anchored at the launch yaw. */
    public static double roughness(double startYaw, double[] absWrapped) {
        double sum = 0.0;
        double prev = startYaw;
        for (int k = 0; k < absWrapped.length; k++) {
            double d = Angles.wrapDelta(absWrapped[k] - prev);
            sum += d * d;
            prev = absWrapped[k];
        }
        return sum;
    }

    /** The angular midpoint of the tick's neighbors (launch yaw on the left edge; the tail pulls
     *  parallel to its left neighbor). */
    private static double midTarget(double startYaw, double[] y, int t) {
        double left = t == 0 ? startYaw : y[t - 1];
        if (t == y.length - 1) return left;
        return Angles.wrap(left + Angles.wrapDelta(y[t + 1] - left) * 0.5);
    }

    /** Try bisected pulls of tick {@code t} toward its midpoint target; keep the first (largest) one
     *  that stays feasible, holds the objective floor, and strictly lowers roughness. Returns the new
     *  roughness (== {@code rough} when no pull is accepted). */
    private static double pullSingle(Work w, double[] y, int t, double rough) {
        double delta = Angles.wrapDelta(midTarget(w.sc.startYaw, y, t) - y[t]);
        if (Math.abs(delta) < MIN_MOVE_DEG) return rough;
        double orig = y[t];
        for (double f : w.cfg.fractions) {
            if (w.evals >= w.evalCap) break;
            y[t] = Angles.wrap(orig + delta * f);
            double r = roughness(w.sc.startYaw, y);
            if (r < rough && w.accepts(y)) return r;
        }
        y[t] = orig;
        return rough;
    }

    /** Joint pull of two nearby ticks toward their (current) midpoint targets at the same fraction:
     *  the move that lets opposite adjustments cancel through the objective floor. */
    private static double pullPair(Work w, double[] y, int i, int j, double rough) {
        double di = Angles.wrapDelta(midTarget(w.sc.startYaw, y, i) - y[i]);
        double dj = Angles.wrapDelta(midTarget(w.sc.startYaw, y, j) - y[j]);
        if (Math.abs(di) < MIN_MOVE_DEG && Math.abs(dj) < MIN_MOVE_DEG) return rough;
        double oi = y[i], oj = y[j];
        for (double f : w.cfg.fractions) {
            if (w.evals >= w.evalCap) break;
            y[i] = Angles.wrap(oi + di * f);
            y[j] = Angles.wrap(oj + dj * f);
            double r = roughness(w.sc.startYaw, y);
            if (r < rough && w.accepts(y)) return r;
        }
        y[i] = oi;
        y[j] = oj;
        return rough;
    }

    /** Anti-symmetric transfer: tick {@code i} pulls toward its target while {@code j} rotates the
     *  opposite way, redistributing objective budget between them: the move that dissolves a
     *  residual kink when the floor blocks each half alone. */
    private static double pullTransfer(Work w, double[] y, int i, int j, double rough) {
        double di = Angles.wrapDelta(midTarget(w.sc.startYaw, y, i) - y[i]);
        if (Math.abs(di) < MIN_MOVE_DEG) return rough;
        double oi = y[i], oj = y[j];
        for (double f : w.cfg.fractions) {
            if (w.evals >= w.evalCap) break;
            y[i] = Angles.wrap(oi + di * f);
            y[j] = Angles.wrap(oj - di * f);
            double r = roughness(w.sc.startYaw, y);
            if (r < rough && w.accepts(y)) return r;
        }
        y[i] = oi;
        y[j] = oj;
        return rough;
    }

    /** Shared eval state: counts forwards and applies the cancel token. */
    private static final class Work {
        final ForwardModel model;
        final JumpPhysicsInputs sc;
        final JumpConstraintCompiler.Compiled compiled;
        final Objective obj;
        final double sign;
        final AtomicBoolean cancel;
        final Config cfg;
        final int evalCap;
        int evals;
        double floor;
        double scoredFloor;

        Work(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
             Objective obj, double sign, AtomicBoolean cancel, Config cfg, int evalCap) {
            this.model = model;
            this.sc = sc;
            this.compiled = compiled;
            this.obj = obj;
            this.sign = sign;
            this.cancel = cancel;
            this.cfg = cfg;
            this.evalCap = evalCap;
        }

        /** sign*objective via the exact chain; +inf when any wall is crossed (same gate as the polish). */
        double eval(double[] absWrapped) {
            if (cancel != null && cancel.get()) throw new SolveCancelledException();
            evals++;
            double[] gf = sc.toGameFacings(absWrapped);
            ForwardPath path = model.forward(sc, gf);
            if (compiled.maxViolation(gf, path) > FEAS_TOL) return Double.POSITIVE_INFINITY;
            return sign * path.getPos(obj.tick, obj.axis);
        }

        boolean accepts(double[] absWrapped) {
            double e = eval(absWrapped);
            if (e == Double.POSITIVE_INFINITY) return false;
            if (obj.smoothLambda <= 0.0) return e <= floor;
            double scored = e + obj.smoothPenalty(absWrapped);
            if (scored > scoredFloor) return false;
            scoredFloor = scored;
            return true;
        }
    }
}
