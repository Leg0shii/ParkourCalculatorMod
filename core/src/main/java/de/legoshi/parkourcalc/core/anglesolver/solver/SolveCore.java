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

    private SolveCore() {
    }

    /** Per-effort solve budget: CMA-ES restarts x maxEval, then polish the best {@code polishCount} feasible basins. */
    public static final class Budget {
        public final int restarts;
        public final int maxEval;
        public final int polishCount;
        public final BucketAscentPolish.Config polishCfg;

        public Budget(int restarts, int maxEval, int polishCount, BucketAscentPolish.Config polishCfg) {
            this.restarts = restarts;
            this.maxEval = maxEval;
            this.polishCount = polishCount;
            this.polishCfg = polishCfg;
        }
    }

    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel) {
        return optimize(model, spec, budget, sigmaDeg, feasTol, cancel, null);
    }

    /** {@code warmStart} (absolute facings) seeds the first restart; the incremental block solver passes the
     *  previous iteration's solution so each added constraint is a small step, not a fresh cold search. */
    public static double[] optimize(ForwardModel model, JumpSpec spec, Budget budget,
                                    double sigmaDeg, double feasTol, AtomicBoolean cancel, double[] warmStart) {
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

        List<double[]> inits = new ArrayList<>();
        for (int r = 0; r < budget.restarts; r++) inits.add(r == 0 ? warm : randomInit(rng, n));

        List<SolverRunResult> results = runRestarts(model, spec, sigmaDeg, budget.maxEval, inits, false, cancel);
        if (cancel.get()) return null;

        boolean max = spec.objective.sense == Objective.Sense.MAX;
        List<SolverRunResult> feasible = filterFeasible(results, feasTol);

        // Feasibility must not depend on the Solve-For direction. The penalized fitness blends the objective
        // with the constraint penalty, so for some objectives CMA-ES settles a hair infeasible (and the
        // strict-feasible polish then bails) while a different objective on the SAME constraints lands
        // feasible -- the "X-min solves but X-max reports no solution" bug. If the objective-weighted pass
        // found nothing feasible, retry ignoring the objective (pure constraint satisfaction). Purely
        // additive: this only runs when we would otherwise report no solution, so it can never regress a
        // solve that already succeeds.
        if (feasible.isEmpty()) {
            List<SolverRunResult> feasOnly = runRestarts(model, spec, sigmaDeg, budget.maxEval, inits, true, cancel);
            if (cancel.get()) return null;
            List<SolverRunResult> rescued = filterFeasible(feasOnly, feasTol);
            if (!rescued.isEmpty()) {
                results = feasOnly;
                feasible = rescued;
            }
        }

        if (feasible.isEmpty()) {
            SolverRunResult best = null;
            for (SolverRunResult r : results) if (best == null || maxViolation(r) < maxViolation(best)) best = r;
            return Angles.wrapAll(best.yawAbsDeg);
        }

        feasible.sort((a, b) -> max ? Double.compare(b.objectiveValue, a.objectiveValue)
                                    : Double.compare(a.objectiveValue, b.objectiveValue));
        List<double[]> top = new ArrayList<>();
        for (int i = 0; i < Math.min(budget.polishCount, feasible.size()); i++) {
            top.add(Angles.wrapAll(feasible.get(i).yawAbsDeg));
        }
        List<double[]> polished = top.parallelStream()
                .map(y -> BucketAscentPolish.polish(model, spec, y, budget.polishCfg, cancel))
                .collect(Collectors.toList());
        if (cancel.get()) return null;

        double[] yaws = polished.get(0);
        double bestObj = objectiveOf(model, sc, spec.objective, yaws);
        for (int i = 1; i < polished.size(); i++) {
            double o = objectiveOf(model, sc, spec.objective, polished.get(i));
            if (max ? o > bestObj : o < bestObj) {
                bestObj = o;
                yaws = polished.get(i);
            }
        }
        return yaws;
    }

    /** One parallel multistart of CMA-ES restarts over {@code inits}. {@code feasibilityOnly} drops the
     *  objective so the search optimizes pure constraint satisfaction. */
    private static List<SolverRunResult> runRestarts(ForwardModel model, JumpSpec spec, double sigmaDeg,
                                                     int maxEval, List<double[]> inits, boolean feasibilityOnly,
                                                     AtomicBoolean cancel) {
        return inits.parallelStream()
                .map(in -> new CmaesJumpHarness(1.0e7, 1.0e7, sigmaDeg, maxEval, feasibilityOnly).solve(model, spec, in, cancel))
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
