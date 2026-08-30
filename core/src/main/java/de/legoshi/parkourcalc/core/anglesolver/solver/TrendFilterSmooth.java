package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TrendFilterSmooth {

    public static final double MAX_GIVE_BACK = 8.0e-3;

    private static final double FEAS_TOL = 0.0;
    private static final double[] LAMBDAS = {0.5, 2.0, 8.0, 32.0};

    private TrendFilterSmooth() {
    }

    public static double[] smooth(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped,
                                  long deadlineNanos, AtomicBoolean cancel) {
        return smooth(exact, spec, seedAbsWrapped, MAX_GIVE_BACK, deadlineNanos, cancel);
    }

    public static double[] smooth(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double giveBack,
                                  long deadlineNanos, AtomicBoolean cancel) {
        if (seedAbsWrapped == null || seedAbsWrapped.length < 2) return seedAbsWrapped;
        JumpPhysicsInputs sc = spec.asScenario();
        double anchor = sc.startYaw;
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        double[] seedGf = sc.toGameFacings(seed);
        if (comp.maxViolation(seedGf, exact.forward(sc, seedGf)) > FEAS_TOL) return seedAbsWrapped;

        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double reference = spec.objective.evaluate(exact.forward(sc, seedGf));
        JumpSpec guarded = guard(sc, spec, max, reference, giveBack);
        JumpConstraintCompiler.Compiled guardedComp = JumpConstraintCompiler.compile(guarded);
        boolean[] frozen = frozenPins(sc, spec);

        SmoothFaceRecovery.Config cfg = new SmoothFaceRecovery.Config();
        cfg.deadlineNanos = deadlineNanos;
        cfg.frozen = frozen;

        double[] best = seed;
        int bestRev = Angles.reversals(anchor, best, Angles.REVERSAL_FLOOR_DEG);
        double bestJerk = jerk(anchor, best);

        double[] deWiggled = DeWiggle.run(exact, spec, seed, cancel, giveBack);
        if (deWiggled != null && deWiggled != seed) {
            double[] dgf = sc.toGameFacings(Angles.wrapAll(deWiggled));
            if (comp.maxViolation(dgf, exact.forward(sc, dgf)) <= FEAS_TOL) {
                int dRev = Angles.reversals(anchor, deWiggled, Angles.REVERSAL_FLOOR_DEG);
                double dJerk = jerk(anchor, deWiggled);
                if (dRev < bestRev || (dRev == bestRev && dJerk < bestJerk - 1.0e-9)) {
                    best = Angles.wrapAll(deWiggled);
                    bestRev = dRev;
                    bestJerk = dJerk;
                }
            }
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (cancel != null && cancel.get()) break;
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
            boolean improved = false;
            double[] rate = turnRate(anchor, best);
            for (int li = 0; li <= LAMBDAS.length; li++) {
                if (cancel != null && cancel.get()) break;
                if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) break;
                double[] target = li == LAMBDAS.length ? null : reconstruct(anchor, condatTV(rate, LAMBDAS[li]));
                double[] cand = SmoothFaceRecovery.smoothToward(exact, guarded, guardedComp, best, target, FEAS_TOL, cancel, cfg);
                if (cand == null) continue;
                double[] cgf = sc.toGameFacings(cand);
                if (comp.maxViolation(cgf, exact.forward(sc, cgf)) > FEAS_TOL) continue;
                double obj = spec.objective.evaluate(exact.forward(sc, cgf));
                if (max ? obj < reference - giveBack : obj > reference + giveBack) continue;
                int rev = Angles.reversals(anchor, cand, Angles.REVERSAL_FLOOR_DEG);
                double jk = jerk(anchor, cand);
                if (rev < bestRev || (rev == bestRev && jk < bestJerk - 1.0e-9)) {
                    best = cand;
                    bestRev = rev;
                    bestJerk = jk;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        return best;
    }

    private static final int MAX_PASSES = 6;

    private static JumpSpec guard(JumpPhysicsInputs sc, JumpSpec spec, boolean max, double reference, double giveBack) {
        if (spec.objective.isCustomAngle()) return spec;
        JumpConstraint.Mode axisMode = spec.objective.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X
                : spec.objective.axis == JumpPhysicsInputs.Axis.Z ? JumpConstraint.Mode.Z : null;
        if (axisMode == null) return spec;
        double rhs = max ? reference - giveBack : reference + giveBack;
        JumpConstraint objGuard = new JumpConstraint(axisMode, spec.objective.tick, null, JumpConstraint.Op.PLUS,
                max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, rhs, "objGuard");
        List<JumpConstraint> cons = new ArrayList<>(spec.constraints);
        cons.add(objGuard);
        return new JumpSpec(sc, cons, spec.objective);
    }

    private static boolean[] frozenPins(JumpPhysicsInputs sc, JumpSpec spec) {
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, new JumpLinearModel(sc));
        if (pre == null || pre.isIdentity()) return null;
        boolean[] frozen = new boolean[sc.numTicks];
        for (int t = 0; t < sc.numTicks; t++) frozen[t] = pre.varIndex(t) < 0;
        return frozen;
    }

    private static double[] turnRate(double anchor, double[] absWrapped) {
        double[] rate = new double[absWrapped.length];
        double prev = anchor;
        for (int t = 0; t < absWrapped.length; t++) {
            rate[t] = Angles.wrapDelta(absWrapped[t] - prev);
            prev = absWrapped[t];
        }
        return rate;
    }

    private static double[] reconstruct(double anchor, double[] rate) {
        double[] out = new double[rate.length];
        double running = anchor;
        for (int t = 0; t < rate.length; t++) {
            running += rate[t];
            out[t] = Angles.wrap(running);
        }
        return out;
    }

    private static double jerk(double anchor, double[] absWrapped) {
        double[] d = turnRate(anchor, absWrapped);
        double s = 0.0;
        for (int i = 1; i < d.length; i++) s += Math.abs(d[i] - d[i - 1]);
        return s;
    }

    static double[] condatTV(double[] input, double lambda) {
        int width = input.length;
        double[] output = new double[width];
        if (width == 0) return output;
        if (lambda <= 0.0) {
            System.arraycopy(input, 0, output, 0, width);
            return output;
        }
        int k = 0, k0 = 0;
        double umin = lambda, umax = -lambda;
        double vmin = input[0] - lambda, vmax = input[0] + lambda;
        int kplus = 0, kminus = 0;
        double twolambda = 2.0 * lambda;
        double minlambda = -lambda;
        for (;;) {
            while (k == width - 1) {
                if (umin < 0.0) {
                    do { output[k0] = vmin; k0++; } while (k0 <= kminus);
                    kminus = k = k0;
                    vmin = input[k0];
                    umin = lambda;
                    umax = vmin + umin - vmax;
                } else if (umax > 0.0) {
                    do { output[k0] = vmax; k0++; } while (k0 <= kplus);
                    kplus = k = k0;
                    vmax = input[k0];
                    umax = minlambda;
                    umin = vmax + umax - vmin;
                } else {
                    vmin += umin / (k - k0 + 1);
                    do { output[k0] = vmin; k0++; } while (k0 <= k);
                    return output;
                }
            }
            if ((umin += input[k + 1] - vmin) < minlambda) {
                do { output[k0] = vmin; k0++; } while (k0 <= kminus);
                kminus = k = k0;
                vmin = input[k0];
                vmax = vmin + twolambda;
                umin = lambda;
                umax = minlambda;
            } else if ((umax += input[k + 1] - vmax) > lambda) {
                do { output[k0] = vmax; k0++; } while (k0 <= kplus);
                kplus = k = k0;
                vmax = input[k0];
                vmin = vmax - twolambda;
                umin = lambda;
                umax = minlambda;
            } else {
                k++;
                if (umin >= lambda) {
                    kminus = k;
                    vmin += (umin - lambda) / (kminus - k0 + 1);
                    umin = lambda;
                }
                if (umax <= minlambda) {
                    kplus = k;
                    vmax += (umax + lambda) / (kplus - k0 + 1);
                    umax = minlambda;
                }
            }
        }
    }
}
