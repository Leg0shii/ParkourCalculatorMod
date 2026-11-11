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
        public int[] freeTicks = null;
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
        SolveClock clock = SolveClock.current();
        int batch = clock != null ? SolveClock.WORKERS : Math.max(1, Runtime.getRuntime().availableProcessors());
        Random rng = new Random(0x5DEECE66DL ^ n ^ SolveClock.seedMix());

        double[] best = Angles.wrapAll(feasibleSeedAbsWrapped.clone());
        double bestScore = score(model, sc, c, obj, sign, best);
        if (bestScore == Double.POSITIVE_INFINITY) return best;

        try {
            for (int round = 0; round < roundCap; round++) {
                if (cancel != null && cancel.get()) return best;
                if (deadlineNanos > 0 && SolveClock.nanoTime() >= deadlineNanos) break;

                double[] base = best;
                List<double[]> kicks = new ArrayList<>(batch);
                for (int b = 0; b < batch; b++) {
                    kicks.add(kickCandidate(base, rng, n, cfg));
                }

                List<double[]> climbed;
                if (clock != null) {
                    climbed = climbForked(clock, kicks, deadlineNanos, sequential, model, spec, sc, c, obj, sign, cfg);
                } else {
                    java.util.stream.Stream<double[]> stream = sequential ? kicks.stream() : kicks.parallelStream();
                    climbed = stream
                            .map(k -> climb(model, spec, sc, c, obj, sign, k, cancel, cfg))
                            .collect(Collectors.toList());
                }

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
                    double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(best), BucketAscentPolish.THOROUGH, cancel, true, cfg.freeTicks);
                    double ps = score(model, sc, c, obj, sign, pol);
                    if (ps < bestScore) {
                        bestScore = ps;
                        best = pol;
                    }
                    if (progress != null) {
                        double[] rgf = sc.toGameFacings(Angles.wrapAll(best));
                        progress.report(best, obj.evaluate(model.forward(sc, rgf)), 0.0, true);
                    }
                }
            }
        } catch (SolveCancelledException e) {
            return best;
        }
        return best;
    }

    private static List<double[]> climbForked(SolveClock clock, List<double[]> kicks, long deadlineNanos,
                                              boolean sequential, ForwardModel model, JumpSpec spec,
                                              JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled c, Objective obj,
                                              double sign, Config cfg) {
        int count = kicks.size();
        SolveClock[] forks = new SolveClock[count];
        AtomicBoolean[] tokens = new AtomicBoolean[count];
        for (int i = 0; i < count; i++) {
            tokens[i] = new AtomicBoolean(false);
            forks[i] = clock.fork(tokens[i], deadlineNanos);
        }
        java.util.stream.IntStream indices = java.util.stream.IntStream.range(0, count);
        if (!sequential) indices = indices.parallel();
        boolean[] cancelled = new boolean[count];
        List<double[]> climbed = indices.mapToObj(i -> {
            SolveClock previous = forks[i].enter();
            try {
                return climb(model, spec, sc, c, obj, sign, kicks.get(i), tokens[i], cfg);
            } catch (SolveCancelledException e) {
                cancelled[i] = true;
                return null;
            } finally {
                SolveClock.restore(previous);
            }
        }).collect(Collectors.toList());
        for (boolean hit : cancelled) {
            if (!hit) continue;
            clock.advanceTo(deadlineNanos);
            clock.addUnits(forks);
            throw new SolveCancelledException();
        }
        clock.join(forks);
        return climbed;
    }

    public static double[] kickCandidate(double[] base, Random rng, int n, Config cfg) {
        double[] cand = base.clone();
        int[] free = cfg.freeTicks;
        boolean useFree = free != null && free.length > 0;
        int freeN = useFree ? free.length : n;
        int ticks;
        if (useFree) {
            int lo = Math.min(cfg.perturbTicksMin, freeN);
            int span = Math.max(1, Math.min(cfg.perturbTicksSpan, freeN));
            ticks = Math.min(freeN, lo + rng.nextInt(span));
        } else {
            ticks = cfg.perturbTicksMin + rng.nextInt(cfg.perturbTicksSpan);
        }
        double mag = cfg.perturbMagMin + rng.nextDouble() * cfg.perturbMagSpan;
        for (int q = 0; q < ticks; q++) {
            int idx = useFree ? free[rng.nextInt(freeN)] : rng.nextInt(n);
            cand[idx] += (rng.nextDouble() * 2.0 - 1.0) * mag;
        }
        return cand;
    }

    private static double[] climb(ForwardModel model, JumpSpec spec, JumpPhysicsInputs sc,
                                  JumpConstraintCompiler.Compiled c, Objective obj, double sign,
                                  double[] start, AtomicBoolean cancel, Config cfg) {
        double[] cur = start;
        double[] pol = BucketAscentPolish.polish(model, spec, Angles.wrapAll(cur), BucketAscentPolish.FAST, cancel, true, cfg.freeTicks);
        return score(model, sc, c, obj, sign, pol) < score(model, sc, c, obj, sign, cur) ? pol : cur;
    }

    private static double score(ForwardModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled c,
                                Objective obj, double sign, double[] absWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absWrapped));
        ForwardPath pr = model.forward(sc, gf);
        if (c.maxViolation(gf, pr) > FEAS_TOL) return Double.POSITIVE_INFINITY;
        return sign * obj.evaluate(pr) + obj.smoothPenalty(sc.startYaw, absWrapped);
    }
}
