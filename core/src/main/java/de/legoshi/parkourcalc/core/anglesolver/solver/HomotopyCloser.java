package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HomotopyCloser {

    private static final double[][] DESCENT_B1 = {{0.05, 0.001}, {0.012, 0.0002}, {0.003, 0.00005}, {0.0008, 0.00001}};
    private static final double[][] DESCENT_B2 = {{0.02, 0.0008}, {0.006, 0.0002}, {0.0015, 0.00004}};

    public static final class Config {
        public double[] repairSigmas = {0.3, 1.0, 3.0};
        public int repairMaxEval = 60000;
        public int maxRungs = 90;
        public int maxRefines = 6;
        public double epsFloor = 2.0e-6;
        public int descentRounds = 24;
        public int descentPairSpan = 3;
        public int entryRestarts = 192;
        public int entryMaxEval = 100000;
        public int entryPolishCount = 8;
    }

    private HomotopyCloser() {
    }

    public static double[] close(ExactJumpModel model, JumpSpec spec, double[] warmAbs, double eps0,
                                 long deadlineNanos, AtomicBoolean cancel) {
        return close(model, spec, warmAbs, eps0, deadlineNanos, cancel, new Config());
    }

    public static double[] close(ExactJumpModel model, JumpSpec spec, double[] warmAbs, double eps0,
                                 long deadlineNanos, AtomicBoolean cancel, Config cfg) {
        SolveCore.Budget entryBudget = new SolveCore.Budget(cfg.entryRestarts, cfg.entryMaxEval,
                cfg.entryPolishCount, BucketAscentPolish.FAST);
        double eps = eps0;
        double[] y = null;
        for (int i = 0; i < 2 && y == null; i++, eps *= 4.0) {
            if (out(deadlineNanos, cancel)) return null;
            JumpSpec relaxed = relax(spec, eps);
            double[] cand = warmAbs != null && slack(model, relaxed, warmAbs) <= 0.0 ? Angles.wrapAll(warmAbs.clone())
                    : SolveCore.optimize(model, relaxed, entryBudget, 20.0, 0.0, cancel,
                            warmAbs != null ? Angles.wrapAll(warmAbs.clone()) : null);
            if (cand == null) continue;
            if (slack(model, relaxed, cand) > 0.0) cand = descend(model, relaxed, cand, deadlineNanos, cancel, cfg);
            if (slack(model, relaxed, cand) <= 0.0) y = cand;
        }
        if (y == null) return null;
        double epsGood = eps / 4.0;
        int rung = 0;
        while (epsGood > 0.0 && rung < cfg.maxRungs) {
            if (out(deadlineNanos, cancel)) return finish(model, spec, y, cfg);
            double epsNext = epsGood <= cfg.epsFloor ? 0.0 : epsGood * 0.5;
            int refine = 0;
            while (true) {
                rung++;
                JumpSpec sp = relax(spec, epsNext);
                double before = slack(model, sp, y);
                if (before <= 0.0) break;
                double[] fixed = repair(model, sp, y, cancel, cfg);
                double after = fixed == null ? before : slack(model, sp, fixed);
                if (after <= 0.0) {
                    y = fixed;
                    break;
                }
                if (fixed != null && after < before) y = fixed;
                refine++;
                if (refine > cfg.maxRefines) {
                    double[] rep = descend(model, sp, y, deadlineNanos, cancel, cfg);
                    if (slack(model, sp, rep) <= 0.0) {
                        y = rep;
                        break;
                    }
                    return finish(model, spec, y, cfg);
                }
                epsNext = 0.5 * (epsGood + epsNext);
                if (out(deadlineNanos, cancel)) return finish(model, spec, y, cfg);
            }
            epsGood = epsNext;
        }
        return finish(model, spec, y, cfg);
    }

    private static double[] finish(ExactJumpModel model, JumpSpec spec, double[] y, Config cfg) {
        if (y == null) return null;
        if (slack(model, spec, y) <= 0.0) return y;
        double[] rep = descend(model, spec, y, 0L, null, cfg);
        return slack(model, spec, rep) <= 0.0 ? rep : null;
    }

    private static boolean out(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
    }

    private static double[] repair(ExactJumpModel model, JumpSpec sp, double[] warm, AtomicBoolean cancel,
                                   Config cfg) {
        double[] best = null;
        double bestV = Double.POSITIVE_INFINITY;
        for (double sigma : cfg.repairSigmas) {
            SolverRunResult rr;
            try {
                rr = new CmaesJumpHarness(1.0e7, 1.0e7, sigma, cfg.repairMaxEval, true)
                        .solve(model, sp, Angles.wrapAll(warm.clone()), cancel);
            } catch (SolveCancelledException e) {
                return best;
            }
            double vv = slack(model, sp, rr.yawAbsDeg);
            if (vv < bestV) {
                bestV = vv;
                best = rr.yawAbsDeg;
            }
            if (vv <= 0.0) return rr.yawAbsDeg;
        }
        return best;
    }

    public static double[] descend(ExactJumpModel model, JumpSpec spec, double[] start,
                                   long deadlineNanos, AtomicBoolean cancel) {
        return descend(model, spec, start, deadlineNanos, cancel, new Config());
    }

    public static double[] descend(ExactJumpModel model, JumpSpec spec, double[] start,
                                   long deadlineNanos, AtomicBoolean cancel, Config cfg) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y = Angles.wrapAll(start.clone());
        double best = rawSlack(model, spec, c, sc, y);
        int n = y.length;
        for (int round = 0; round < cfg.descentRounds && best > 0.0; round++) {
            if (out(deadlineNanos, cancel)) return y;
            boolean moved = false;
            for (double[] r : DESCENT_B1) {
                for (int t = 0; t < n && best > 0.0; t++) {
                    double orig = y[t], by = orig, bo = best;
                    for (double d = -r[0]; d <= r[0] + 1e-12; d += r[1]) {
                        y[t] = orig + d;
                        double s = rawSlack(model, spec, c, sc, y);
                        if (s < bo) {
                            bo = s;
                            by = y[t];
                        }
                    }
                    y[t] = by;
                    if (bo < best) {
                        best = bo;
                        moved = true;
                    }
                }
            }
            if (best <= 0.0) break;
            if (out(deadlineNanos, cancel)) return y;
            for (double[] r : DESCENT_B2) {
                for (int i = 0; i < n && best > 0.0; i++) {
                    for (int j = i + 1; j <= Math.min(n - 1, i + cfg.descentPairSpan); j++) {
                        double oi = y[i], oj = y[j], bi = oi, bj = oj, bo = best;
                        for (double di = -r[0]; di <= r[0] + 1e-12; di += r[1]) {
                            y[i] = oi + di;
                            for (double dj = -r[0]; dj <= r[0] + 1e-12; dj += r[1]) {
                                y[j] = oj + dj;
                                double s = rawSlack(model, spec, c, sc, y);
                                if (s < bo) {
                                    bo = s;
                                    bi = y[i];
                                    bj = y[j];
                                }
                            }
                        }
                        y[i] = bi;
                        y[j] = bj;
                        if (bo < best) {
                            best = bo;
                            moved = true;
                        }
                    }
                }
            }
            if (!moved) break;
        }
        return y;
    }

    public static double slack(ExactJumpModel model, JumpSpec spec, double[] absYaws) {
        if (absYaws == null) return Double.POSITIVE_INFINITY;
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        return rawSlack(model, spec, c, spec.asScenario(), Angles.wrapAll(absYaws.clone()));
    }

    private static double rawSlack(ExactJumpModel model, JumpSpec spec, JumpConstraintCompiler.Compiled c,
                                   JumpPhysicsInputs sc, double[] absYaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absYaws));
        ForwardPath pr = model.forward(sc, gf);
        double m = Double.NEGATIVE_INFINITY;
        StartBox box = sc.startBox;
        if (box != null && box.startFree()) {
            double[] d = FreeStartSolve.bestTranslate(spec, gf, pr, box);
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.translatedSlack(cc, gf, pr, d[0], d[1]));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.translatedEvaluate(cc, gf, pr, d[0], d[1])));
        } else {
            for (JumpConstraint cc : c.ineq) m = Math.max(m, JumpConstraintCompiler.slack(cc, gf, pr));
            for (JumpConstraint cc : c.eq) m = Math.max(m, Math.abs(JumpConstraintCompiler.evaluate(cc, gf, pr)));
        }
        return m;
    }

    public static JumpSpec relax(JumpSpec spec, double eps) {
        if (eps <= 0.0) return spec;
        List<JumpConstraint> out = new ArrayList<JumpConstraint>();
        for (JumpConstraint c : spec.constraints) {
            if (c.cmp == JumpConstraint.Cmp.GE) {
                out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs - eps, c.name));
            } else if (c.cmp == JumpConstraint.Cmp.LE) {
                out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, c.rhs + eps, c.name));
            } else {
                out.add(c);
            }
        }
        return new JumpSpec(spec.asScenario(), out, spec.objective);
    }
}
