package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class IlsPolish {

    private static final double FEAS_TOL = 0.0;

    public static final class Config {
        public int perturbTicksMin = 3;
        public int perturbTicksSpan = 13;
        public double perturbMagMin = 3.0;
        public double perturbMagSpan = 50.0;
    }

    private IlsPolish() {
    }

    public static double[] polish(ForwardModel model, JumpSpec spec, double[] feasibleSeedAbsWrapped,
                                  long deadlineNanos, int roundCap, boolean sequential,
                                  AtomicBoolean cancel, SolveProgress progress) {
        return polish(model, spec, feasibleSeedAbsWrapped, deadlineNanos, roundCap, sequential, cancel, progress,
                new Config());
    }

    public static double[] polish(ForwardModel model, JumpSpec spec, double[] feasibleSeedAbsWrapped,
                                  long deadlineNanos, int roundCap, boolean sequential,
                                  AtomicBoolean cancel, SolveProgress progress, Config cfg) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs sc = spec.asScenario();
        Objective obj = spec.objective;
        double sign = obj.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        int n = sc.numTicks;
        int batch = Math.max(1, Runtime.getRuntime().availableProcessors());
        Random rng = new Random(0x5DEECE66DL ^ n);

        double[] best = Angles.wrapAll(feasibleSeedAbsWrapped.clone());
        double bestScore = score(model, sc, c, obj, sign, best);
        if (bestScore == Double.POSITIVE_INFINITY) return best;

        try {
            for (int round = 0; round < roundCap; round++) {
                if (cancel != null && cancel.get()) return best;
                if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) break;

                double[] base = best;
                List<double[]> kicks = new ArrayList<>(batch);
                for (int b = 0; b < batch; b++) {
                    double[] cand = base.clone();
                    int ticks = cfg.perturbTicksMin + rng.nextInt(cfg.perturbTicksSpan);
                    double mag = cfg.perturbMagMin + rng.nextDouble() * cfg.perturbMagSpan;
                    for (int q = 0; q < ticks; q++) cand[rng.nextInt(n)] += (rng.nextDouble() * 2.0 - 1.0) * mag;
                    kicks.add(cand);
                }

                java.util.stream.Stream<double[]> stream = sequential ? kicks.stream() : kicks.parallelStream();
                List<double[]> climbed = stream
                        .map(k -> climb(model, spec, sc, c, obj, sign, k, cancel, cfg))
                        .collect(Collectors.toList());

                boolean improved = false;
                for (double[] y : climbed) {
                    double s = score(model, sc, c, obj, sign, y);
                    if (s < bestScore) {
                        bestScore = s;
                        best = y;
                        improved = true;
                    }
                }
                if (improved) {
                    double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(best), BucketAscentPolish.THOROUGH, cancel);
                    double ps = score(model, sc, c, obj, sign, pol);
                    if (ps < bestScore) {
                        bestScore = ps;
                        best = pol;
                    }
                    if (progress != null) {
                        double[] rgf = sc.toGameFacings(Angles.wrapAll(best));
                        progress.report(best, model.forward(sc, rgf).getPos(obj.tick, obj.axis), 0.0, true);
                    }
                }
            }
        } catch (SolveCancelledException e) {
            return best;
        }
        return best;
    }

    private static double[] climb(ForwardModel model, JumpSpec spec, JumpPhysicsInputs sc,
                                  JumpConstraintCompiler.Compiled c, Objective obj, double sign,
                                  double[] start, AtomicBoolean cancel, Config cfg) {
        double[] cur = start;
        double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(cur), BucketAscentPolish.FAST, cancel);
        return score(model, sc, c, obj, sign, pol) < score(model, sc, c, obj, sign, cur) ? pol : cur;
    }

    private static double score(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled c,
                                Objective obj, double sign, double[] absWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absWrapped));
        ForwardPath pr = model.forward(sc, gf);
        if (c.maxViolation(gf, pr) > FEAS_TOL) return Double.POSITIVE_INFINITY;
        return sign * pr.getPos(obj.tick, obj.axis) + obj.smoothPenalty(sc.startYaw, absWrapped);
    }
}
