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

    public static final class Scatter {
        public double flickRestPct = 5.0;
        public double flickMovingPct = 8.0;
        public double smoothPx = 1.0;
        public double flickThresholdDeg = 6.0;
        public double flickMsMin = 20.0;
        public double flickMsMax = 40.0;
        public double flickStartJitterMs = 10.0;
        public double phaseScale = 0.0;
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

    public static final double TICK_MS = 50.0;

    public static Stats sample(ForwardModel model, JumpSpec spec, double[] facing, boolean[] held,
                               double pixelDeg, Scatter scatter, int attempts, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled comp = positionConstraints(spec);
        double[] intended = new double[n];
        double prev = n > 0 ? facing[0] : sc.startYaw;
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
        double[] inc = new double[n];
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
            double phase = rng.nextDouble() * scatter.phaseScale;
            State before = State.HELD;
            for (int t = 0; t < n; t++) {
                double e = 0.0;
                double s = 0.0;
                inc[t] = state[t] == State.FLICK ? 0.0 : intended[t];
                if (state[t] == State.SMOOTH) {
                    s = scatter.smoothPx * pixelDeg;
                    e = Math.round(rng.nextGaussian() * scatter.smoothPx) * pixelDeg - phase * intended[t];
                    inc[t] += e;
                }
                err[t] = e;
                sigma[t] = s;
                before = state[t];
            }
            before = State.HELD;
            for (int t = 0; t < n; t++) {
                if (state[t] == State.FLICK) {
                    double s = Math.abs(intended[t]) * (before == State.HELD ? scatter.flickRestPct : scatter.flickMovingPct) / 100.0;
                    double amp = intended[t] + Math.round(rng.nextGaussian() * s / pixelDeg) * pixelDeg;
                    double dur = scatter.flickMsMin + rng.nextDouble() * Math.max(0.0, scatter.flickMsMax - scatter.flickMsMin);
                    double start = (TICK_MS - dur) * 0.5 + (rng.nextDouble() * 2.0 - 1.0) * scatter.flickStartJitterMs;
                    double worst = Math.abs(amp - intended[t]);
                    double prevFrac = 0.0;
                    for (int k = (int) Math.floor(Math.min(0.0, start) / TICK_MS); ; k++) {
                        int at = t - 1 + k;
                        if (at >= n) break;
                        double frac = Math.min(1.0, Math.max(0.0, (k * TICK_MS - start) / dur));
                        if (at >= 0) inc[at] += amp * (frac - prevFrac);
                        double planned = k >= 1 ? intended[t] : 0.0;
                        worst = Math.max(worst, Math.abs(amp * frac - planned));
                        prevFrac = frac;
                        if (frac >= 1.0) break;
                    }
                    err[t] = worst;
                    sigma[t] = s;
                }
                before = state[t];
            }
            double f = n > 0 ? facing[0] : sc.startYaw;
            for (int t = 0; t < n; t++) {
                f = Angles.wrap(f + inc[t]);
                double off = Angles.wrapDelta(f - facing[t]);
                yaws[t] = Angles.wrap(facing[t] + Math.round(off / pixelDeg) * pixelDeg);
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
