package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** The multistart optimize for one fixed {@link JumpSpec}: parallel CMA-ES restarts find the feasible
 *  basins, then the top few are bucket-polished in parallel and the best kept. Extracted from the engine
 *  so both the normal solve and the block-driven lazy solve share one implementation. Returns the
 *  absolute wrapped facings (what Apply writes), or null if cancelled. */
public final class SolveCore {

    private static final double NEAR_MISS_RESCUE_MAX_VIOL = 5.0e-2;
    private static final double NEAR_MISS_RESCUE_SIGMA_DEG = 2.0;
    private static final int NEAR_MISS_RESCUE_SEEDS = 4;

    private SolveCore() {
    }

    /** Per-effort solve budget: CMA-ES restarts x maxEval, then polish the best {@code polishCount} feasible basins. */
    public static final class Budget {
        public final int restarts;
        public final int maxEval;
        public final int polishCount;
        public final BucketAscentPolish.Config polishCfg;
        public final CmaesJumpHarness.Config harness;

        public Budget(int restarts, int maxEval, int polishCount, BucketAscentPolish.Config polishCfg) {
            this(restarts, maxEval, polishCount, polishCfg, new CmaesJumpHarness.Config());
        }

        public Budget(int restarts, int maxEval, int polishCount, BucketAscentPolish.Config polishCfg,
                      CmaesJumpHarness.Config harness) {
            this.restarts = restarts;
            this.maxEval = maxEval;
            this.polishCount = polishCount;
            this.polishCfg = polishCfg;
            this.harness = harness;
        }
    }

    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel) {
        return optimize(model, spec, budget, sigmaDeg, feasTol, cancel, null, 0L);
    }

    /** {@code warmStart} (absolute facings) seeds the first restart; the incremental block solver passes the
     *  previous iteration's solution so each added constraint is a small step, not a fresh cold search. */
    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel, double[] warmStart) {
        return optimize(model, spec, budget, sigmaDeg, feasTol, cancel, warmStart, 0L);
    }

    /** {@code deadlineNanos}: an absolute {@link System#nanoTime()} deadline, or {@code 0} for the fixed
     *  {@code budget.restarts} batch (byte-identical to the non-anytime path). When positive, restart batches
     *  keep launching until it passes (checked between batches) and the best feasible is returned. */
    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel, double[] warmStart,
                                    long deadlineNanos) {
        return optimize(model, spec, budget, sigmaDeg, feasTol, cancel, warmStart, deadlineNanos, false);
    }

    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel, double[] warmStart,
                                    long deadlineNanos, boolean sequential) {
        return optimize(model, spec, budget, sigmaDeg, feasTol, cancel, warmStart, deadlineNanos, sequential, null);
    }

    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel, double[] warmStart,
                                    long deadlineNanos, boolean sequential, SolveProgress progress) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        double[] warm;
        if (warmStart != null && warmStart.length == n) {
            warm = warmStart.clone();
        } else {
            warm = new double[n];
            java.util.Arrays.fill(warm, sc.startYaw);
        }
        Random rng = new Random(0x9E3779B9L ^ n);
        boolean stopOnFeasible = progress != null && progress.stopOnFeasible();

        List<double[]> inits = new ArrayList<>();
        List<SolverRunResult> results = new ArrayList<>();
        try {
            boolean firstBatch = true;
            do {
                List<double[]> batch = new ArrayList<>();
                for (int r = 0; r < budget.restarts; r++) batch.add(firstBatch && r == 0 ? warm : randomInit(rng, n));
                firstBatch = false;
                inits.addAll(batch);
                results.addAll(runRestarts(model, spec, sigmaDeg, budget, batch, false, sequential, cancel, feasTol, progress));
                if (cancel.get()) return bestOrNull(progress);
                if (stopOnFeasible && hasFeasible(results, feasTol)) break;
            } while (deadlineNanos > 0 && System.nanoTime() < deadlineNanos && !cancel.get());

            boolean max = spec.objective.sense == Objective.Sense.MAX;
            List<SolverRunResult> feasible = filterFeasible(results, feasTol);

            if (feasible.isEmpty() && (deadlineNanos == 0L || System.nanoTime() < deadlineNanos)) {
                List<double[]> seeds = nearMissSeeds(results, feasTol);
                if (!seeds.isEmpty()) {
                    List<SolverRunResult> seeded = runRestarts(model, spec, NEAR_MISS_RESCUE_SIGMA_DEG, budget,
                            seeds, true, sequential, cancel, feasTol, progress);
                    if (cancel.get()) return bestOrNull(progress);
                    List<SolverRunResult> rescued = filterFeasible(seeded, feasTol);
                    if (!rescued.isEmpty()) {
                        results = seeded;
                        feasible = rescued;
                    } else {
                        results = new ArrayList<>(results);
                        results.addAll(seeded);
                    }
                }
            }

            // Rescue pass: whether a solution EXISTS must not depend on the Solve-For direction, but the
            // objective-weighted fitness can settle a hair infeasible for some directions (see the
            // feasibilityOnly constructor on CmaesJumpHarness). Purely additive: this only runs when we
            // would otherwise report no solution, so it can never regress a solve that already succeeds.
            if (feasible.isEmpty() && (deadlineNanos == 0L || System.nanoTime() < deadlineNanos)) {
                List<double[]> rescueInits = deadlineNanos > 0L && inits.size() > budget.restarts
                        ? inits.subList(0, budget.restarts) : inits;
                List<SolverRunResult> feasOnly = runRestarts(model, spec, sigmaDeg, budget, rescueInits, true, sequential, cancel, feasTol, progress);
                if (cancel.get()) return bestOrNull(progress);
                List<SolverRunResult> rescued = filterFeasible(feasOnly, feasTol);
                if (!rescued.isEmpty()) {
                    results = feasOnly;
                    feasible = rescued;
                } else {
                    results = new ArrayList<>(results);
                    results.addAll(feasOnly);
                }
            }

            if (feasible.isEmpty()) {
                SolverRunResult best = null;
                double bestS = 0.0;
                for (SolverRunResult r : results) {
                    double s = spec.objective.scored(r.objectiveValue, r.yawAbsDeg);
                    if (best == null || (max ? s > bestS : s < bestS)) {
                        best = r;
                        bestS = s;
                    }
                }
                if (best == null) return bestOrNull(progress);
                return Angles.wrapAll(best.yawAbsDeg);
            }

            feasible.sort((a, b) -> {
                double sa = spec.objective.scored(a.objectiveValue, a.yawAbsDeg);
                double sb = spec.objective.scored(b.objectiveValue, b.yawAbsDeg);
                return max ? Double.compare(sb, sa) : Double.compare(sa, sb);
            });
            if (stopOnFeasible) return Angles.wrapAll(feasible.get(0).yawAbsDeg);

            List<double[]> top = new ArrayList<>();
            for (int i = 0; i < Math.min(budget.polishCount, feasible.size()); i++) {
                top.add(Angles.wrapAll(feasible.get(i).yawAbsDeg));
            }
            java.util.stream.Stream<double[]> polishStream = sequential ? top.stream() : top.parallelStream();
            List<double[]> polished = polishStream
                    .map(y -> BucketAscentPolish.polish(model, spec, y, budget.polishCfg, cancel))
                    .collect(Collectors.toList());
            if (cancel.get()) return bestOrNull(progress);

            double[] yaws = polished.get(0);
            double bestObj = spec.objective.scored(objectiveOf(model, sc, spec.objective, yaws), yaws);
            for (int i = 1; i < polished.size(); i++) {
                double[] cand = polished.get(i);
                double o = spec.objective.scored(objectiveOf(model, sc, spec.objective, cand), cand);
                if (max ? o > bestObj : o < bestObj) {
                    bestObj = o;
                    yaws = cand;
                }
            }
            return yaws;
        } catch (SolveCancelledException e) {
            return bestOrNull(progress);
        }
    }

    private static double[] bestOrNull(SolveProgress progress) {
        return progress != null && progress.haveBest() ? progress.bestYaws() : null;
    }

    private static boolean hasFeasible(List<SolverRunResult> results, double feasTol) {
        for (SolverRunResult r : results) if (maxViolation(r) <= feasTol) return true;
        return false;
    }

    private static List<double[]> nearMissSeeds(List<SolverRunResult> results, double feasTol) {
        List<SolverRunResult> near = new ArrayList<>();
        for (SolverRunResult r : results) {
            double v = maxViolation(r);
            if (v > feasTol && v <= NEAR_MISS_RESCUE_MAX_VIOL) near.add(r);
        }
        near.sort((a, b) -> Double.compare(maxViolation(a), maxViolation(b)));
        List<double[]> seeds = new ArrayList<>();
        for (int i = 0; i < Math.min(NEAR_MISS_RESCUE_SEEDS, near.size()); i++) {
            seeds.add(Angles.wrapAll(near.get(i).yawAbsDeg));
        }
        return seeds;
    }

    /** One parallel multistart of CMA-ES restarts over {@code inits}. {@code feasibilityOnly} drops the
     *  objective so the search optimizes pure constraint satisfaction. */
    private static List<SolverRunResult> runRestarts(ForwardModel model, JumpSpec spec, double sigmaDeg,
                                                     Budget budget, List<double[]> inits, boolean feasibilityOnly,
                                                     boolean sequential, AtomicBoolean cancel, double feasTol,
                                                     SolveProgress progress) {
        boolean stopOnFeasible = !feasibilityOnly && progress != null && progress.stopOnFeasible();
        AtomicBoolean found = stopOnFeasible ? new AtomicBoolean(false) : null;
        java.util.stream.Stream<double[]> stream = sequential ? inits.stream() : inits.parallelStream();
        return stream
                .map(in -> {
                    if (found != null && found.get()) return null;
                    SolverRunResult rr;
                    try {
                        rr = new CmaesJumpHarness(budget.harness, sigmaDeg, budget.maxEval, feasibilityOnly).solve(model, spec, in, cancel, found);
                    } catch (SolveCancelledException e) {
                        if (cancel.get()) throw e;
                        return null;
                    }
                    if (progress != null) {
                        double v = maxViolation(rr);
                        progress.report(rr.yawAbsDeg, rr.objectiveValue, v, v <= feasTol);
                    }
                    if (found != null && maxViolation(rr) <= feasTol) found.set(true);
                    return rr;
                })
                .filter(rr -> rr != null)
                .collect(Collectors.toList());
    }

    private static List<SolverRunResult> filterFeasible(List<SolverRunResult> results, double feasTol) {
        List<SolverRunResult> feasible = new ArrayList<>();
        for (SolverRunResult r : results) if (maxViolation(r) <= feasTol) feasible.add(r);
        return feasible;
    }

    public static double objectiveOf(ForwardModel model, JumpPhysicsInputs sc, Objective obj, double[] absWrapped) {
        return model.forward(sc, sc.toGameFacings(absWrapped)).getPos(obj.tick, obj.axis);
    }

    public static double maxViolation(SolverRunResult r) {
        double m = 0.0;
        for (double s : r.ineqSlack) m = Math.max(m, s);
        for (double e : r.eqResidual) m = Math.max(m, Math.abs(e));
        return m;
    }

    private static double[] randomInit(Random rng, int n) {
        double[] f = new double[n];
        for (int i = 0; i < n; i++) f[i] = -180.0 + 360.0 * rng.nextDouble();
        return f;
    }
}
