package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.SeamSweepRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SeamSweepNode implements NodeRuntime {

    public SeamSweepNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : 0L;
        if (remaining <= 0) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("seam sweep"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "seam sweep start budgetMs=%d", remaining / 1_000_000L);
        double[] swept = SeamSweepRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken,
                remaining, Angles.wrapAll(in.yaws.clone()));
        if (swept != null) {
            boolean max = ctx.maximize();
            double cur = ctx.scoredObjective(in.yaws);
            double sweptObj = ctx.scoredObjective(swept);
            if (max ? sweptObj > cur : sweptObj < cur) {
                ctx.chainAppend("seam sweep");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, swept));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }
}
