package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmoothPortfolio {

    public static final double SEED_SHARE = 0.35;
    public static final double CLASSIC_SHARE = 0.6;

    private static final long WATCHDOG_SLEEP_MS = 10L;

    private static final ShapeSmooth.Weights EASE = new ShapeSmooth.Weights(ShapeSmooth.Norm.L1, 0.0, 1.0, 1.0, 0.0);
    private static final ShapeSmooth.Weights EASE_LOOK = new ShapeSmooth.Weights(ShapeSmooth.Norm.L1, 0.2, 1.0, 1.0, 1.0);
    private static final ShapeSmooth.Weights ENERGY = new ShapeSmooth.Weights(ShapeSmooth.Norm.L2, 0.0, 1.0, 1.0, 0.0);
    private static final ShapeSmooth.Weights SPARSE_LOOK = new ShapeSmooth.Weights(ShapeSmooth.Norm.LOG, 0.2, 1.0, 1.0, 1.0);
    private static final ShapeSmooth.Weights SHORT = new ShapeSmooth.Weights(ShapeSmooth.Norm.L1, 1.0, 0.0, 0.0, 0.0);

    private static final ShapeSmooth.Weights[][] FROM_SEED = {
            {SPARSE_LOOK, EASE_LOOK},
            {EASE},
            {SHORT, EASE},
            {ENERGY, EASE},
    };

    private static final ShapeSmooth.Weights[][] FROM_CLASSIC = {
            {EASE_LOOK},
            {EASE},
    };

    private SmoothPortfolio() {
    }

    public static TrendFilterSmooth.Report smoothReport(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped,
                                                        double giveBack, long deadlineNanos, AtomicBoolean cancel) {
        long started = System.nanoTime();
        if (seedAbsWrapped == null || seedAbsWrapped.length < 2) {
            return new TrendFilterSmooth.Report(seedAbsWrapped, TrendFilterSmooth.Stop.CONVERGED, 0L);
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        double[] seedGf = sc.toGameFacings(seed);
        ForwardPath seedPath = exact.forward(sc, seedGf);
        if (JumpConstraintCompiler.compile(spec).maxViolation(seedGf, seedPath) > 0.0) {
            return new TrendFilterSmooth.Report(seedAbsWrapped, TrendFilterSmooth.Stop.CONVERGED, System.nanoTime() - started);
        }
        JumpSpec guarded = guard(sc, spec, spec.objective.evaluate(seedPath), giveBack);
        ShapeSmooth shape = new ShapeSmooth(exact, guarded);
        double anchor = sc.startYaw;

        double[] best = seed;
        WatchScore bestScore = WatchScore.of(anchor, seed, seedPath);
        List<double[]> starts = new ArrayList<>();
        long seedDeadline = share(deadlineNanos, SEED_SHARE);
        for (int i = 0; i < FROM_SEED.length; i++) {
            if (expired(seedDeadline, cancel)) {
                break;
            }
            long chainDeadline = share(seedDeadline, 1.0 / (FROM_SEED.length - i));
            double[] out = runChain(shape, seed, FROM_SEED[i], chainDeadline, cancel);
            WatchScore score = score(exact, sc, anchor, out);
            if (shape.feasible(out) && score.betterThan(bestScore)) {
                best = out;
                bestScore = score;
            }
        }

        TrendFilterSmooth.Stop stop = TrendFilterSmooth.Stop.CONVERGED;
        if (!expired(deadlineNanos, cancel)) {
            long classicDeadline = share(deadlineNanos, CLASSIC_SHARE);
            TrendFilterSmooth.Report classic = classic(exact, spec, seed, giveBack, classicDeadline, cancel);
            if (classic.yaws != null && shape.feasible(classic.yaws)) {
                double[] classicYaws = Angles.wrapAll(classic.yaws.clone());
                starts.add(classicYaws);
                WatchScore score = score(exact, sc, anchor, classicYaws);
                if (score.betterThan(bestScore)) {
                    best = classicYaws;
                    bestScore = score;
                }
            }
            if (classic.stop != TrendFilterSmooth.Stop.CONVERGED) {
                stop = classic.stop == TrendFilterSmooth.Stop.PASS_LIMIT ? classic.stop : TrendFilterSmooth.Stop.BUDGET;
            }
        }
        for (double[] start : starts) {
            for (ShapeSmooth.Weights[] chain : FROM_CLASSIC) {
                if (expired(deadlineNanos, cancel)) {
                    break;
                }
                double[] out = runChain(shape, start, chain, deadlineNanos, cancel);
                WatchScore score = score(exact, sc, anchor, out);
                if (shape.feasible(out) && score.betterThan(bestScore)) {
                    best = out;
                    bestScore = score;
                }
            }
        }
        if (cancel != null && cancel.get()) {
            stop = TrendFilterSmooth.Stop.CANCELLED;
        } else if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            stop = TrendFilterSmooth.Stop.BUDGET;
        }
        double[] result = best == seed ? seedAbsWrapped : best;
        return new TrendFilterSmooth.Report(result, stop, System.nanoTime() - started);
    }

    private static TrendFilterSmooth.Report classic(ExactJumpModel exact, JumpSpec spec, double[] seed, double giveBack,
                                                    long deadlineNanos, AtomicBoolean cancel) {
        if (deadlineNanos == 0L) {
            return TrendFilterSmooth.smoothReport(exact, spec, seed, giveBack, 0L, cancel);
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            try {
                while (System.nanoTime() < deadlineNanos && (cancel == null || !cancel.get())) {
                    Thread.sleep(WATCHDOG_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                return;
            }
            stop.set(true);
        }, "smooth-budget");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            return TrendFilterSmooth.smoothReport(exact, spec, seed, giveBack, deadlineNanos, stop);
        } finally {
            watchdog.interrupt();
        }
    }

    private static double[] runChain(ShapeSmooth shape, double[] start, ShapeSmooth.Weights[] chain, long deadlineNanos,
                                     AtomicBoolean cancel) {
        double[] cur = start;
        for (ShapeSmooth.Weights w : chain) {
            cur = shape.run(cur, w, deadlineNanos, cancel);
        }
        return cur;
    }

    private static WatchScore score(ExactJumpModel exact, JumpPhysicsInputs sc, double anchor, double[] absWrapped) {
        return WatchScore.of(anchor, absWrapped, exact.forward(sc, sc.toGameFacings(absWrapped)));
    }

    private static long share(long deadlineNanos, double fraction) {
        if (deadlineNanos == 0L) {
            return 0L;
        }
        long now = System.nanoTime();
        return now + (long) (fraction * Math.max(0L, deadlineNanos - now));
    }

    private static boolean expired(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            return true;
        }
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }

    static JumpSpec guard(JumpPhysicsInputs sc, JumpSpec spec, double reference, double giveBack) {
        if (spec.objective.isCustomAngle() || Double.isInfinite(giveBack)) {
            return spec;
        }
        JumpConstraint.Mode mode = spec.objective.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X
                : spec.objective.axis == JumpPhysicsInputs.Axis.Z ? JumpConstraint.Mode.Z : null;
        if (mode == null) {
            return spec;
        }
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double rhs = max ? reference - giveBack : reference + giveBack;
        JumpConstraint floor = new JumpConstraint(mode, spec.objective.tick, null, JumpConstraint.Op.PLUS,
                max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, rhs, "objGuard");
        List<JumpConstraint> cons = new ArrayList<>(spec.constraints);
        cons.add(floor);
        return new JumpSpec(sc, cons, spec.objective);
    }
}
