package de.legoshi.parkourcalc.core.anglesolver.profile;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AttemptSampler {

    public static final int RESERVOIR = 400;
    public static final long SEED = 1234L;

    public static final class Stats {
        public final int attempts;
        public final int landings;
        public final double[][] landed;
        public final double[][] failed;
        public final double[] landedLo;
        public final double[] landedHi;

        Stats(int attempts, int landings, double[][] landed, double[][] failed, double[] landedLo, double[] landedHi) {
            this.attempts = attempts;
            this.landings = landings;
            this.landed = landed;
            this.failed = failed;
            this.landedLo = landedLo;
            this.landedHi = landedHi;
        }

        public double rate() {
            return attempts == 0 ? 0.0 : (double) landings / attempts;
        }
    }

    private AttemptSampler() {
    }

    public static Stats sample(ForwardModel model, JumpSpec spec, double[] facing, double[][] errors, int attempts,
                               AtomicBoolean cancel) {
        return sample(model, spec, facing, errors, attempts, SEED, cancel);
    }

    public static Stats merge(Stats total, Stats chunk) {
        if (total == null) return chunk;
        int n = chunk.landedLo.length;
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int t = 0; t < n; t++) {
            if (total.landings == 0) {
                lo[t] = chunk.landedLo[t];
                hi[t] = chunk.landedHi[t];
            } else if (chunk.landings == 0) {
                lo[t] = total.landedLo[t];
                hi[t] = total.landedHi[t];
            } else {
                lo[t] = Math.min(total.landedLo[t], chunk.landedLo[t]);
                hi[t] = Math.max(total.landedHi[t], chunk.landedHi[t]);
            }
        }
        double[][] landed = chunk.landed.length > 0 || total.landings == 0 ? chunk.landed : total.landed;
        return new Stats(total.attempts + chunk.attempts, total.landings + chunk.landings, landed, chunk.failed, lo, hi);
    }

    public static Stats sample(ForwardModel model, JumpSpec spec, double[] facing, double[][] errors, int attempts,
                               long seed, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled comp = positionConstraints(spec);
        Random rng = new Random(seed);
        double[] yaws = new double[n];
        double[][] landedPool = new double[RESERVOIR][];
        double[][] failedPool = new double[RESERVOIR][];
        int landedSeen = 0, failedSeen = 0;
        double[] landedLo = new double[n];
        double[] landedHi = new double[n];
        java.util.Arrays.fill(landedLo, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(landedHi, Double.NEGATIVE_INFINITY);
        int landings = 0;
        int done = 0;
        for (int a = 0; a < attempts; a++) {
            if (cancel != null && cancel.get()) break;
            done++;
            for (int t = 0; t < n; t++) {
                double[] e = errors != null && t < errors.length ? errors[t] : null;
                double err = e == null || e.length == 0 ? 0.0 : e[rng.nextInt(e.length)];
                yaws[t] = Angles.wrap(facing[t] + err);
            }
            double[] gf = sc.toGameFacings(yaws);
            boolean lands = comp.maxViolation(gf, model.forward(sc, gf)) <= 0.0;
            if (lands) {
                landings++;
                for (int t = 0; t < n; t++) {
                    double rel = Angles.wrapDelta(yaws[t] - facing[t]);
                    if (rel < landedLo[t]) landedLo[t] = rel;
                    if (rel > landedHi[t]) landedHi[t] = rel;
                }
                reservoir(landedPool, landedSeen++, yaws, rng);
            } else {
                reservoir(failedPool, failedSeen++, yaws, rng);
            }
        }
        if (landings == 0) {
            java.util.Arrays.fill(landedLo, 0.0);
            java.util.Arrays.fill(landedHi, 0.0);
        }
        return new Stats(done, landings, trim(landedPool, landedSeen), trim(failedPool, failedSeen), landedLo, landedHi);
    }

    public static JumpConstraintCompiler.Compiled positionConstraints(JumpSpec spec) {
        List<JumpConstraint> ineq = new ArrayList<>();
        List<JumpConstraint> eq = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            if (c.cmp == JumpConstraint.Cmp.EQ) eq.add(c);
            else ineq.add(c);
        }
        return new JumpConstraintCompiler.Compiled(ineq, eq);
    }

    private static void reservoir(double[][] pool, int seen, double[] yaws, Random rng) {
        if (seen < pool.length) {
            pool[seen] = yaws.clone();
            return;
        }
        int slot = rng.nextInt(seen + 1);
        if (slot < pool.length) pool[slot] = yaws.clone();
    }

    private static double[][] trim(double[][] pool, int seen) {
        int k = Math.min(seen, pool.length);
        double[][] out = new double[k][];
        System.arraycopy(pool, 0, out, 0, k);
        return out;
    }
}
