package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TrendFilterSmooth {

    public static final double MAX_GIVE_BACK = 8.0e-3;

    private static final double FEAS_TOL = 0.0;
    private static final double[] LAMBDAS = {0.5, 2.0, 8.0, 32.0};

    public enum Stop {
        CONVERGED("converged"),
        PASS_LIMIT("pass limit"),
        BUDGET("budget"),
        CANCELLED("cancelled");

        public final String label;

        Stop(String label) {
            this.label = label;
        }
    }

    public static final class Report {
        public final double[] yaws;
        public final Stop stop;
        public final long nanos;

        public Report(double[] yaws, Stop stop, long nanos) {
            this.yaws = yaws;
            this.stop = stop;
            this.nanos = nanos;
        }
    }

    private TrendFilterSmooth() {
    }

    public static double[] smooth(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped,
                                  long deadlineNanos, AtomicBoolean cancel) {
        return smooth(exact, spec, seedAbsWrapped, MAX_GIVE_BACK, deadlineNanos, cancel);
    }

    public static double[] smooth(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double giveBack,
                                  long deadlineNanos, AtomicBoolean cancel) {
        return smoothReport(exact, spec, seedAbsWrapped, giveBack, deadlineNanos, cancel).yaws;
    }

    public static Report smoothReport(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double giveBack,
                                      long deadlineNanos, AtomicBoolean cancel) {
        if (seedAbsWrapped == null || seedAbsWrapped.length < 2) return new Report(seedAbsWrapped, Stop.CONVERGED, 0L);
        long tAll = System.nanoTime();
        SmoothProfile.reset();
        JumpPhysicsInputs sc = spec.asScenario();
        double anchor = sc.startYaw;
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        double[] seedGf = sc.toGameFacings(seed);
        if (comp.maxViolation(seedGf, exact.forward(sc, seedGf)) > FEAS_TOL) {
            return new Report(seedAbsWrapped, Stop.CONVERGED, System.nanoTime() - tAll);
        }

        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double reference = spec.objective.evaluate(exact.forward(sc, seedGf));
        JumpSpec guarded = guard(sc, spec, max, reference, giveBack);
        JumpConstraintCompiler.Compiled guardedComp = JumpConstraintCompiler.compile(guarded);
        boolean[] frozen = frozenPins(sc, spec);

        SmoothFaceRecovery.Config cfg = new SmoothFaceRecovery.Config();
        cfg.deadlineNanos = deadlineNanos;
        cfg.frozen = frozen;
        int par = threads();
        ExecutorService pool = par > 1 ? Executors.newFixedThreadPool(par, r -> {
            Thread th = new Thread(r, "smoother");
            th.setDaemon(true);
            return th;
        }) : null;
        cfg.pool = pool;
        cfg.parallelism = par;
        try {
            return smoothWith(exact, spec, comp, guarded, guardedComp, sc, anchor, seed, giveBack, deadlineNanos, cancel, cfg, tAll);
        } finally {
            if (pool != null) pool.shutdownNow();
        }
    }

    private static volatile int threadOverride;

    public static void setThreads(int n) {
        threadOverride = n;
    }

    private static int threads() {
        if (threadOverride > 0) return threadOverride;
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    private static Report smoothWith(ExactJumpModel exact, JumpSpec spec, JumpConstraintCompiler.Compiled comp,
                                       JumpSpec guarded, JumpConstraintCompiler.Compiled guardedComp, JumpPhysicsInputs sc,
                                       double anchor, double[] seed, double giveBack, long deadlineNanos,
                                       AtomicBoolean cancel, SmoothFaceRecovery.Config cfg, long tAll) {
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double reference = spec.objective.evaluate(exact.forward(sc, sc.toGameFacings(seed)));
        cfg.repairSpec = guarded;
        cfg.repairRows = DeWiggle.usable(sc, guarded);
        boolean[] held = DeWiggle.heldByFacing(sc.numTicks, spec);
        if (cfg.frozen != null) for (int t = 0; t < held.length; t++) held[t] |= cfg.frozen[t];
        cfg.repairHeld = held;

        double[] best = seed;
        TurnShape bestShape = TurnShape.of(anchor, best);

        double[] deWiggled = DeWiggle.run(exact, spec, seed, cancel, giveBack);
        if (deWiggled != null && deWiggled != seed) {
            double[] dgf = sc.toGameFacings(Angles.wrapAll(deWiggled));
            if (comp.maxViolation(dgf, exact.forward(sc, dgf)) <= FEAS_TOL) {
                TurnShape shape = TurnShape.of(anchor, deWiggled);
                if (shape.betterThan(bestShape)) {
                    best = Angles.wrapAll(deWiggled);
                    bestShape = shape;
                }
            }
        }

        long tFm = System.nanoTime();
        double[] merged = FlickMerge.run(exact, guarded, best, cancel, giveBack, deadlineNanos);
        SmoothProfile.add("flickmerge", tFm);
        if (merged != null && merged != best) {
            double[] mgf = sc.toGameFacings(merged);
            if (comp.maxViolation(mgf, exact.forward(sc, mgf)) <= FEAS_TOL) {
                TurnShape shape = TurnShape.of(anchor, merged);
                if (shape.betterThan(bestShape)) {
                    best = merged;
                    bestShape = shape;
                }
            }
        }
        long reshapeDeadline = deadlineNanos == 0L ? 0L : System.nanoTime() + (deadlineNanos - System.nanoTime()) / 3;
        long tWr = System.nanoTime();
        double[] reshaped = WindowReshape.run(exact, guarded, best, cancel, giveBack, reshapeDeadline);
        SmoothProfile.add("reshape", tWr);
        if (reshaped != null && reshaped != best) {
            double[] rgf = sc.toGameFacings(reshaped);
            if (comp.maxViolation(rgf, exact.forward(sc, rgf)) <= FEAS_TOL) {
                TurnShape shape = TurnShape.of(anchor, reshaped);
                if (shape.betterThan(bestShape)) {
                    best = reshaped;
                    bestShape = shape;
                }
            }
        }

        Stop stop = Stop.PASS_LIMIT;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (cancel != null && cancel.get()) {
                stop = Stop.CANCELLED;
                break;
            }
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
                stop = Stop.BUDGET;
                break;
            }
            boolean improved = false;
            List<double[]> targets = targets(anchor, best);
            List<double[]> shapeTargets = targets.subList(0, targets.size() - 1);
            long now = System.nanoTime();
            long passRemaining = deadlineNanos == 0L ? 0L : deadlineNanos - now;
            long shapeDeadline = deadlineNanos == 0L ? 0L : now + Math.max(passRemaining / 2, 50_000_000L);
            List<double[]> cands = runShapeTargets(exact, guarded, guardedComp, best, shapeTargets, cancel, cfg, shapeDeadline);
            for (int ti = 0; ti < cands.size(); ti++) {
                double[] cand = cands.get(ti);
                if (cand == null) continue;
                double[] cgf = sc.toGameFacings(cand);
                if (comp.maxViolation(cgf, exact.forward(sc, cgf)) > FEAS_TOL) continue;
                double obj = spec.objective.evaluate(exact.forward(sc, cgf));
                if (max ? obj < reference - giveBack : obj > reference + giveBack) continue;
                TurnShape shape = TurnShape.of(anchor, cand);
                if (shape.betterThan(bestShape)) {
                    SmoothProfile.count("accept." + (ti < 4 ? "l1" : ti < 8 ? "l2" : "tv"));
                    best = cand;
                    bestShape = shape;
                    improved = true;
                }
            }
            if (cancel != null && cancel.get()) {
                stop = Stop.CANCELLED;
                break;
            }
            now = System.nanoTime();
            if (deadlineNanos != 0L && now >= deadlineNanos) {
                stop = Stop.BUDGET;
                break;
            }
            cfg.deadlineNanos = deadlineNanos;
            long tT = System.nanoTime();
            double[] cand = SmoothFaceRecovery.smoothToward(exact, guarded, guardedComp, best, null, FEAS_TOL, cancel, cfg);
            SmoothProfile.add("target.facewalk", tT);
            if (cand != null) {
                double[] cgf = sc.toGameFacings(cand);
                double obj = spec.objective.evaluate(exact.forward(sc, cgf));
                boolean inBudget = !(max ? obj < reference - giveBack : obj > reference + giveBack);
                if (comp.maxViolation(cgf, exact.forward(sc, cgf)) <= FEAS_TOL && inBudget) {
                    TurnShape shape = TurnShape.of(anchor, cand);
                    if (shape.betterThan(bestShape)) {
                        SmoothProfile.count("accept.facewalk");
                        best = cand;
                        bestShape = shape;
                        improved = true;
                    }
                }
            }
            SmoothProfile.count("pass");
            if (!improved) {
                stop = Stop.CONVERGED;
                break;
            }
        }
        long nanos = System.nanoTime() - tAll;
        SmoothProfile.report(nanos);
        return new Report(best, stop, nanos);
    }

    private static final int MAX_PASSES = 6;

    private static List<double[]> runShapeTargets(ExactJumpModel exact, JumpSpec guarded, JumpConstraintCompiler.Compiled guardedComp,
                                                  double[] best, List<double[]> targets, AtomicBoolean cancel,
                                                  SmoothFaceRecovery.Config cfg, long deadline) {
        List<double[]> out = new ArrayList<>();
        if (cfg.pool == null) {
            for (double[] target : targets) {
                SmoothFaceRecovery.Config c = cfg.copy();
                c.deadlineNanos = deadline;
                c.pool = null;
                long tT = System.nanoTime();
                out.add(SmoothFaceRecovery.smoothToward(exact, guarded, guardedComp, best, target, FEAS_TOL, cancel, c));
                SmoothProfile.add("target.shape", tT);
            }
            return out;
        }
        List<Future<double[]>> futures = new ArrayList<>();
        for (double[] target : targets) {
            SmoothFaceRecovery.Config c = cfg.copy();
            c.deadlineNanos = deadline;
            c.pool = null;
            futures.add(cfg.pool.submit(() -> {
                long tT = System.nanoTime();
                double[] r = SmoothFaceRecovery.smoothToward(exact, guarded, guardedComp, best.clone(), target, FEAS_TOL, cancel, c);
                SmoothProfile.add("target.shape", tT);
                return r;
            }));
        }
        for (Future<double[]> f : futures) {
            try {
                out.add(f.get());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }
    private static final double[] SHAPE_LAMBDAS = {1.0, 4.0, 16.0, 64.0};

    private interface RateFilter {
        double[] apply(double[] rate, double lambda);
    }

    static List<double[]> targets(double anchor, double[] best) {
        double[] rate = turnRate(anchor, best);
        List<double[]> out = new ArrayList<>();
        RateFilter l1 = TrendFilterSmooth::trendL1;
        RateFilter l2 = (r, lambda) -> whittaker(r, lambda, null);
        RateFilter tv = TrendFilterSmooth::condatTV;
        for (double lambda : SHAPE_LAMBDAS) out.add(reconstruct(anchor, perRun(rate, lambda, l1)));
        for (double lambda : SHAPE_LAMBDAS) out.add(reconstruct(anchor, perRun(rate, lambda, l2)));
        for (double lambda : LAMBDAS) out.add(reconstruct(anchor, perRun(rate, lambda, tv)));
        out.add(null);
        return out;
    }

    private static double[] perRun(double[] rate, double lambda, RateFilter filter) {
        double[] out = rate.clone();
        int n = rate.length;
        int start = -1;
        int sign = 0;
        for (int t = 0; t <= n; t++) {
            int s = t < n && Math.abs(rate[t]) > TurnShape.FLOOR_DEG ? (rate[t] > 0.0 ? 1 : -1) : 0;
            if (s != 0 && s == sign) continue;
            if (sign != 0 && t - start >= 3) {
                double[] seg = new double[t - start];
                System.arraycopy(rate, start, seg, 0, seg.length);
                double[] f = filter.apply(seg, lambda);
                for (int k = 0; k < f.length; k++) out[start + k] = sign > 0 ? Math.max(0.0, f[k]) : Math.min(0.0, f[k]);
            }
            sign = s;
            start = t;
        }
        return out;
    }

    static double[] whittaker(double[] r, double lambda, double[] weights) {
        int n = r.length;
        if (n < 3) return r.clone();
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            a[i][i] = 1.0;
            a[i][n] = r[i];
        }
        double[] cf = {1.0, -2.0, 1.0};
        for (int t = 1; t < n - 1; t++) {
            double w = lambda * (weights == null ? 1.0 : weights[t]);
            int[] idx = {t - 1, t, t + 1};
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < 3; k++) a[idx[i]][idx[k]] += w * cf[i] * cf[k];
            }
        }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int row = c + 1; row < n; row++) if (Math.abs(a[row][c]) > Math.abs(a[piv][c])) piv = row;
            double[] tmp = a[c];
            a[c] = a[piv];
            a[piv] = tmp;
            double d = a[c][c];
            for (int row = c + 1; row < n; row++) {
                double f = a[row][c] / d;
                if (f == 0.0) continue;
                for (int k = c; k <= n; k++) a[row][k] -= f * a[c][k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = a[i][n];
            for (int k = i + 1; k < n; k++) sum -= a[i][k] * x[k];
            x[i] = sum / a[i][i];
        }
        return x;
    }

    private static final int L1_ITERS = 12;
    private static final double L1_EPS = 1.0e-2;

    static double[] trendL1(double[] r, double lambda) {
        int n = r.length;
        if (n < 3) return r.clone();
        double[] w = new double[n];
        java.util.Arrays.fill(w, 1.0);
        double[] x = r.clone();
        for (int it = 0; it < L1_ITERS; it++) {
            x = whittaker(r, lambda, w);
            for (int t = 1; t < n - 1; t++) {
                double d2 = Math.abs(x[t - 1] - 2.0 * x[t] + x[t + 1]);
                w[t] = 1.0 / Math.max(d2, L1_EPS);
            }
        }
        return x;
    }

    private static JumpSpec guard(JumpPhysicsInputs sc, JumpSpec spec, boolean max, double reference, double giveBack) {
        if (spec.objective.isCustomAngle() || Double.isInfinite(giveBack)) return spec;
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
