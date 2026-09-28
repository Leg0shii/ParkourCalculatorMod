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
        public long seed = 1234L;
    }

    public static final class Stats {
        public final int attempts;
        public final int landings;
        public final double[] blame;
        public final boolean[] flickTick;

        Stats(int attempts, int landings, double[] blame, boolean[] flickTick) {
            this.attempts = attempts;
            this.landings = landings;
            this.blame = blame;
            this.flickTick = flickTick;
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
            double phase = rng.nextDouble();
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
                continue;
            }
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
        return new Stats(done, landings, blame, flickTick);
    }
}
