package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BnbNode implements NodeRuntime {

    private final boolean firstFeasible;
    private final int minBudgetMs;
    private final String labelSuffix;

    public BnbNode(ParamValues params) {
        this.firstFeasible = "FIRST_FEASIBLE".equals(params.getString("mode"));
        this.minBudgetMs = params.getInt("minBudgetMs");
        this.labelSuffix = params.getString("labelSuffix");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return passthrough(in);
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : 0L;
        if (remaining <= minBudgetMs * 1_000_000L) return passthrough(in);
        boolean max = ctx.maximize();
        if (firstFeasible) {
            if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("pattern B&B"));
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "bnb rescue budgetMs=%d", remaining / 1_000_000L);
            double[] rescue = BoundPrunedRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken,
                    remaining, max ? -1.0e300 : 1.0e300);
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "bnb rescue %s", rescue != null ? "solved" : "miss");
            if (rescue == null) return passthrough(in);
            double[] yaws = Angles.wrapAll(rescue);
            ctx.chainAppend("pattern B&B");
            if (labelSuffix != null && !labelSuffix.isEmpty()) ctx.chainSuffix(labelSuffix);
            Candidate out = Candidate.of(ctx, yaws);
            if (ctx.progress != null) ctx.progress.report(yaws, out.objective, out.violation, true);
            return NodeOutcome.of(Guarantee.FOUND, out);
        }
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("branch and bound"));
        double cap = Scoring.objectiveCap(ctx.spec);
        double stopAt = Double.isNaN(cap) ? Double.NaN : (max ? cap - Scoring.CAP_GAP_TOL : cap + Scoring.CAP_GAP_TOL);
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "bnb start budgetMs=%d stopAt=%s", remaining / 1_000_000L,
                    Double.isNaN(stopAt) ? "-" : String.valueOf(stopAt));
        }
        double[] bnb = BoundPrunedRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken, remaining, stopAt);
        if (bnb != null) {
            double cur = ctx.scoredObjective(in.yaws);
            double bnbObj = ctx.scoredObjective(bnb);
            if (max ? bnbObj > cur : bnbObj < cur) {
                ctx.chainAppend("branch and bound");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, Angles.wrapAll(bnb)));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }

    private static NodeOutcome passthrough(Candidate in) {
        return NodeOutcome.of(in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE, in);
    }
}
