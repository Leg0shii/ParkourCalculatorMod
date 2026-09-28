package de.legoshi.parkourcalc.core.anglesolver.profile;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AttemptSampler {

    public static final class Scatter {
        public double flickRestPx = 1.5;
        public double flickMovingPx = 4.0;
        public double smoothPx = 1.0;
        public double flickThresholdDeg = 6.0;
        public double flickOnTickChance = 0.7;
        public double phaseScale = 1.0;
        public long seed = 1234L;
    }

    public static final int RESERVOIR = 400;

    public static final class Stats {
        public final int attempts;
        public final int landings;
        public final double[] blame;
        public final boolean[] flickTick;
        public final double[][] landed;
        public final double[][] failed;
        public final double[] landedLo;
        public final double[] landedHi;

        Stats(int attempts, int landings, double[] blame, boolean[] flickTick, double[][] landed, double[][] failed,
              double[] landedLo, double[] landedHi) {
            this.attempts = attempts;
            this.landings = landings;
            this.blame = blame;
            this.flickTick = flickTick;
            this.landed = landed;
            this.failed = failed;
            this.landedLo = landedLo;
            this.landedHi = landedHi;
        }

        public double rate() {
            return attempts == 0 ? 0.0 : (double) landings / attempts;
        }
    }

    private enum State { HELD, SMOOTH, FLICK }

    private AttemptSampler() {
    }

    public static Stats sample(ForwardModel model, JumpSpec spec, double[] facing, boolean[] held, boolean[] keyEdge,
                               double pixelDeg, Scatter scatter, int attempts, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] intended = new double[n];
        double prev = sc.startYaw;
        for (int t = 0; t < n; t++) {
            intended[t] = Angles.wrapDelta(facing[t] - prev);
            prev = facing[t];
        }
        State[] state = new State[n];
        boolean[] flickTick = new boolean[n];
        for (int t = 0; t < n; t++) {
            double a = Math.abs(intended[t]);
            state[t] = held[t] || a < TurnProfile.SIG_ANGLE_DEG ? State.HELD
                    : a >= scatter.flickThresholdDeg ? State.FLICK : State.SMOOTH;
            flickTick[t] = state[t] == State.FLICK;
        }
        Random rng = new Random(scatter.seed);
        double[] dF = new double[n];
        double[] err = new double[n];
        double[] sigma = new double[n];
        double[] yaws = new double[n];
        double[] blameCount = new double[n];
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
            System.arraycopy(intended, 0, dF, 0, n);
            for (int t = 0; t < n; t++) {
                if (state[t] != State.FLICK || keyEdge[t]) continue;
                double u = rng.nextDouble();
                if (u < scatter.flickOnTickChance) continue;
                int shift = u < scatter.flickOnTickChance + (1.0 - scatter.flickOnTickChance) * 0.5 ? -1 : 1;
                int to = t + shift;
                if (to < 0 || to >= n) continue;
                dF[to] += dF[t];
                dF[t] = 0.0;
            }
            double phase = rng.nextDouble() * scatter.phaseScale;
            State before = State.HELD;
            double f = sc.startYaw;
            for (int t = 0; t < n; t++) {
                double e = 0.0;
                double s = 0.0;
                if (state[t] == State.FLICK) {
                    s = before == State.HELD ? scatter.flickRestPx : scatter.flickMovingPx;
                    e = Math.round(rng.nextGaussian() * s) * pixelDeg;
                } else if (state[t] == State.SMOOTH) {
                    s = scatter.smoothPx;
                    e = Math.round(rng.nextGaussian() * s) * pixelDeg - phase * dF[t];
                }
                err[t] = e;
                sigma[t] = s * pixelDeg;
                f = Angles.wrap(f + dF[t] + e);
                yaws[t] = f;
                before = state[t];
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
                continue;
            }
            reservoir(failedPool, failedSeen++, yaws, rng);
            int worst = -1;
            double worstScore = 0.0;
            for (int t = 0; t < n; t++) {
                if (sigma[t] <= 0.0) continue;
                double score = Math.abs(err[t]) / sigma[t];
                if (score > worstScore) {
                    worstScore = score;
                    worst = t;
                }
            }
            if (worst >= 0) blameCount[worst] += 1.0;
        }
        int fails = done - landings;
        double[] blame = new double[n];
        if (fails > 0) for (int t = 0; t < n; t++) blame[t] = blameCount[t] / fails;
        if (landings == 0) {
            java.util.Arrays.fill(landedLo, 0.0);
            java.util.Arrays.fill(landedHi, 0.0);
        }
        return new Stats(done, landings, blame, flickTick, trim(landedPool, landedSeen), trim(failedPool, failedSeen),
                landedLo, landedHi);
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
