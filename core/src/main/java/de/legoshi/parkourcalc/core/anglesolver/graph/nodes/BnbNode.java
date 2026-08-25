package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.GateMip;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BnbNode implements NodeRuntime {

    private final boolean firstFeasible;
    private final int minBudgetMs;
    private final String labelSuffix;
    private final BoundPrunedRecovery.Config cfg;

    public BnbNode(ParamValues params) {
        this.firstFeasible = "FIRST_FEASIBLE".equals(params.getString("mode"));
        this.minBudgetMs = params.getInt("minBudgetMs");
        this.labelSuffix = params.getString("labelSuffix");
        this.cfg = new BoundPrunedRecovery.Config();
        cfg.searchShare = params.getDouble("searchShare");
        cfg.pruneTol = params.getDouble("pruneTol");
        cfg.slpViolTrigger = params.getDouble("slpViolTrigger");
        cfg.seedMargins = ParamParse.doubles(params.getString("seedMargins"), cfg.seedMargins);
        cfg.maxPatterns = params.getInt("maxPatterns");
        cfg.minSeamWidth = params.getDouble("minSeamWidth");
        cfg.restoreIters = params.getInt("restoreIters");
        cfg.treeSlpPhase1Calls = params.getInt("treeSlpPhase1Calls");
        cfg.treeSlpTotalCalls = params.getInt("treeSlpTotalCalls");
        cfg.treeSlpTrMinDeg = params.getDouble("treeSlpTrMinDeg");
        cfg.polishSlpPhase1Calls = params.getInt("polishSlpPhase1Calls");
        cfg.polishSlpTotalCalls = params.getInt("polishSlpTotalCalls");
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
                    remaining, max ? -1.0e300 : 1.0e300, cfg, ctx.closestMiss());
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "bnb rescue %s", rescue != null ? "solved" : "miss");
            double[] gate = GateMip.improve(ctx.exactModel, ctx.spec, rescue, ctx.feasTol, nodeToken, deadlineNanos);
            if (gate != null && (rescue == null || keepGate(ctx, gate, rescue, max))) rescue = gate;
            if (rescue == null) return passthrough(in);
            double[] yaws = Angles.wrapAll(rescue);
            ctx.chainAppend("pattern B&B");
            if (labelSuffix != null && !labelSuffix.isEmpty()) ctx.chainSuffix(labelSuffix);
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, yaws));
        }
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("branch and bound"));
        double cap = Scoring.objectiveCap(ctx.spec);
        double stopAt = Double.isNaN(cap) ? Double.NaN : (max ? cap - Scoring.CAP_GAP_TOL : cap + Scoring.CAP_GAP_TOL);
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "bnb start budgetMs=%d stopAt=%s", remaining / 1_000_000L,
                    Double.isNaN(stopAt) ? "-" : String.valueOf(stopAt));
        }
        double[] bnb = BoundPrunedRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken, remaining, stopAt, cfg,
                ctx.closestMiss());
        double[] gateSeed = bnb != null && keepGate(ctx, bnb, in.yaws, max) ? bnb : in.yaws;
        double[] gate = GateMip.improve(ctx.exactModel, ctx.spec, gateSeed, ctx.feasTol, nodeToken, deadlineNanos);
        if (gate != null && keepGate(ctx, gate, gateSeed, max)) bnb = gate;
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

    private static boolean keepGate(GraphContext ctx, double[] cand, double[] ref, boolean max) {
        if (ref == null) return true;
        double c = ctx.scoredObjective(cand);
        double r = ctx.scoredObjective(ref);
        return max ? c > r : c < r;
    }

    private static NodeOutcome passthrough(Candidate in) {
        return NodeOutcome.of(in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE, in);
    }
}
