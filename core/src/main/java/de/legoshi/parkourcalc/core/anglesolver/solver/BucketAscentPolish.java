package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic exhaustive block-coordinate ascent on the byte-exact model, run on a feasible CMA-ES
 *  result to finish the job. Each yaw selects one of MC's 65536 float32 sine-table buckets, so the
 *  objective is a step function and the inequality walls fragment the feasible region into disconnected
 *  discrete islands. A single-tick scan rails into one island's corner (a strict coordinate-wise local
 *  optimum); the joint two-tick scans here make the feasible moves that hop between islands, which
 *  CMA-ES's quadratic penalty cannot (the penalty puts a barrier on every wall).
 *
 *  <p>Every candidate is evaluated with the same wrap + toGameFacings + forward chain CmaesJumpHarness
 *  uses, and rejected unless strictly feasible, so this can only improve a feasible start, never clip.
 *  No RNG affects the outcome: the optional perturbation restarts use a fixed seed. The {@link Config}
 *  trades depth for speed; the engine polishes several CMA-ES basins in parallel and keeps the best. */
public final class BucketAscentPolish {

    private static final double FEAS_TOL = 0.0;

    /** Resolution schedule and reach. Coarse-to-fine (window, step) pairs in degrees; the finest step
     *  over-samples the ~0.0055 deg sine buckets so none is skipped. Wider block-2 hops islands, finer
     *  block-2 settles the wall-binding ticks onto their exact bucket pair. */
    public static final class Config {
        final double[][] b1;
        final double[][] b2;
        final int maxRounds;
        final int restarts;
        final int pairSpan;

        public Config(double[][] b1, double[][] b2, int maxRounds, int restarts, int pairSpan) {
            this.b1 = b1;
            this.b2 = b2;
            this.maxRounds = maxRounds;
            this.restarts = restarts;
            this.pairSpan = pairSpan;
        }
    }

    /** ~light: a couple of narrow passes. Cheap per basin; the engine relies on several basins for reach. */
    public static final Config FAST = new Config(
            new double[][]{{0.2, 0.004}, {0.04, 0.0006}, {0.01, 0.0001}},
            new double[][]{{0.08, 0.0025}},
            1, 0, 3
    );

    /** Default: a few medium passes. With ~4 basins polished in parallel this matches the heavy solve. */
    public static final Config BALANCED = new Config(
            new double[][]{{0.2, 0.004}, {0.04, 0.0006}, {0.01, 0.0001}},
            new double[][]{{0.15, 0.004}, {0.04, 0.001}},
            2, 0, 3
    );

    /** Exhaustive: wide coarse block-2 island hops, fine settle, plus a few fixed-seed restarts. */
    public static final Config THOROUGH = new Config(
            new double[][]{{1.5, 0.02}, {0.4, 0.005}, {0.1, 0.001}, {0.03, 0.0003}, {0.01, 0.0001}, {0.004, 0.00003}},
            new double[][]{{1.0, 0.02}, {0.3, 0.008}, {0.1, 0.003}, {0.04, 0.001}},
            12, 8, 3
    );

    public static double[] polish(ForwardModel model, JumpSpec spec, double[] startAbsWrapped, Config cfg, AtomicBoolean cancel) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs scenario = spec.asScenario();
        Objective obj = spec.objective;
        double sign = obj.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        int n = startAbsWrapped.length;
        int[][] pairs = pairs(n, cfg.pairSpan);

        double[] best = startAbsWrapped.clone();
        if (score(model, scenario, c, obj, sign, best) == Double.POSITIVE_INFINITY) {
            return best; // start not strictly feasible: leave it (the engine only polishes feasible results)
        }
        best = ascend(best, model, scenario, c, obj, sign, pairs, cfg, cancel);
        double bestScore = score(model, scenario, c, obj, sign, best);

        if (cfg.restarts > 0) {
            Random rng = new Random(0x9E3779B97F4A7C15L ^ n);
            for (int r = 0; r < cfg.restarts; r++) {
                if (cancel != null && cancel.get()) throw new SolveCancelledException();
                double[] y = best.clone();
                double mag = 0.5 + 6.0 * rng.nextDouble();
                for (int t = 0; t < n; t++) y[t] += (rng.nextDouble() * 2.0 - 1.0) * mag;
                y = ascend(y, model, scenario, c, obj, sign, pairs, cfg, cancel);
                double s = score(model, scenario, c, obj, sign, y);
                if (s < bestScore) { bestScore = s; best = y; }
            }
        }
        return best;
    }

    private static double[] ascend(double[] start, ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, int[][] pairs, Config cfg, AtomicBoolean cancel) {
        double[] y = start.clone();
        if (score(model, scenario, c, obj, sign, y) == Double.POSITIVE_INFINITY) return y;
        b1refine(y, model, scenario, c, obj, sign, cfg, cancel);
        for (int round = 0; round < cfg.maxRounds; round++) {
            boolean moved = false;
            for (double[] r : cfg.b2) {
                if (block2(y, pairs, r[0], r[1], model, scenario, c, obj, sign, cancel)) moved = true;
            }
            b1refine(y, model, scenario, c, obj, sign, cfg, cancel);
            if (!moved) break;
        }
        return y;
    }

    private static void b1refine(double[] y, ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, Config cfg, AtomicBoolean cancel) {
        for (double[] r : cfg.b1) {
            for (int it = 0; it < 60; it++) {
                if (!block1(y, r[0], r[1], model, scenario, c, obj, sign, cancel)) break;
            }
        }
    }

    /** Scan each tick over [-win, win] at step; keep the best strictly-feasible improvement. */
    private static boolean block1(double[] y, double win, double step, ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, AtomicBoolean cancel) {
        boolean improved = false;
        double best = score(model, scenario, c, obj, sign, y);
        int n = y.length;
        for (int t = 0; t < n; t++) {
            if (cancel != null && cancel.get()) throw new SolveCancelledException();
            double orig = y[t], bestY = orig, bestO = best;
            for (double d = -win; d <= win + 1e-12; d += step) {
                y[t] = orig + d;
                double o = score(model, scenario, c, obj, sign, y);
                if (o < bestO) { bestO = o; bestY = y[t]; }
            }
            y[t] = bestY;
            if (bestO < best) { best = bestO; improved = true; }
        }
        return improved;
    }

    /** Scan pairs jointly over a 2-D window; keep the best strictly-feasible improvement. */
    private static boolean block2(double[] y, int[][] pairs, double win, double step, ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, AtomicBoolean cancel) {
        boolean improved = false;
        double best = score(model, scenario, c, obj, sign, y);
        for (int[] pr : pairs) {
            if (cancel != null && cancel.get()) throw new SolveCancelledException();
            int i = pr[0], j = pr[1];
            double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
            for (double di = -win; di <= win + 1e-12; di += step) {
                y[i] = oi + di;
                for (double dj = -win; dj <= win + 1e-12; dj += step) {
                    y[j] = oj + dj;
                    double o = score(model, scenario, c, obj, sign, y);
                    if (o < bo) { bo = o; bi = y[i]; bj = y[j]; }
                }
            }
            y[i] = bi; y[j] = bj;
            if (bo < best) { best = bo; improved = true; }
        }
        return improved;
    }

    private static int[][] pairs(int n, int span) {
        List<int[]> p = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int d = 1; d <= span && i + d < n; d++) p.add(new int[]{i, i + d});
        }
        return p.toArray(new int[0][]);
    }

    /** sign*objective via the exact wrap + game-facing + byte-exact forward; +inf if any wall is crossed. */
    private static double score(ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, double[] abs) {
        double[] gf = scenario.toGameFacings(Angles.wrapAll(abs));
        ForwardPath pr = model.forward(scenario, gf);
        double viol = c.maxViolation(gf, pr);
        if (viol > FEAS_TOL) return Double.POSITIVE_INFINITY;
        return sign * pr.getPos(obj.tick, obj.axis);
    }
}
