package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LatticeRepair {

    private static final double[][] SCHEDULE = {{0.5, 0.05}, {0.12, 0.012}, {0.03, 0.003}, {0.008, 0.0008}, {0.002, 0.0001}};
    private static final int MAX_ROUNDS = 8;
    private static final double START_VIOL_CAP = 0.08;
    private static final int PAIR_STEPS = 4;

    public static boolean DEBUG = false;

    private LatticeRepair() {
    }

    public static double[] repair(ExactJumpModel exact, JumpSpec spec, double[] startAbsWrapped,
                                  double feasTol, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int n = startAbsWrapped.length;
        double[] y = Angles.wrapAll(startAbsWrapped.clone());
        double viol = violationOf(exact, sc, compiled, y);
        if (DEBUG) System.out.printf("  REPAIR start viol=%.3e%n", viol);
        if (viol <= feasTol) return y;
        if (viol > START_VIOL_CAP) return null;

        for (double[] rung : SCHEDULE) {
            double win = rung[0];
            double step = rung[1];
            for (int round = 0; round < MAX_ROUNDS; round++) {
                if (cancel != null && cancel.get()) return null;
                boolean improved = false;
                for (int t = 0; t < n; t++) {
                    double base = y[t];
                    double bestDelta = 0.0;
                    for (double d = -win; d <= win + 1.0e-12; d += step) {
                        if (d == 0.0) continue;
                        y[t] = Angles.wrap(base + d);
                        double v = violationOf(exact, sc, compiled, y);
                        if (v < viol) {
                            viol = v;
                            bestDelta = d;
                            improved = true;
                        }
                    }
                    y[t] = Angles.wrap(base + bestDelta);
                    if (viol <= feasTol) return y;
                }
                double pairStep = win / PAIR_STEPS;
                for (int t = 0; t + 1 < n; t++) {
                    if (cancel != null && cancel.get()) return null;
                    double baseA = y[t];
                    double baseB = y[t + 1];
                    double bestA = 0.0;
                    double bestB = 0.0;
                    for (int i = -PAIR_STEPS; i <= PAIR_STEPS; i++) {
                        for (int k = -PAIR_STEPS; k <= PAIR_STEPS; k++) {
                            if (i == 0 && k == 0) continue;
                            y[t] = Angles.wrap(baseA + i * pairStep);
                            y[t + 1] = Angles.wrap(baseB + k * pairStep);
                            double v = violationOf(exact, sc, compiled, y);
                            if (v < viol) {
                                viol = v;
                                bestA = i * pairStep;
                                bestB = k * pairStep;
                                improved = true;
                            }
                        }
                    }
                    y[t] = Angles.wrap(baseA + bestA);
                    y[t + 1] = Angles.wrap(baseB + bestB);
                    if (viol <= feasTol) return y;
                }
                if (!improved) break;
            }
            if (DEBUG) System.out.printf("  REPAIR rung win=%.4f viol=%.3e%n", win, viol);
            if (viol <= feasTol) return y;
        }
        return viol <= feasTol ? y : null;
    }

    private static double violationOf(ExactJumpModel exact, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return compiled.maxViolation(gf, exact.forward(sc, gf));
    }
}
