package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ReportNode implements NodeRuntime {

    public ReportNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in != null && in.yaws != null && ctx.progress != null) {
            double v = ctx.violationOf(in.yaws);
            ctx.progress.setStage(ctx.chain());
            ctx.progress.report(in.yaws, ctx.exactObjective(in.yaws), v, v <= ctx.feasTol);
        }
        return NodeOutcome.of(Guarantee.DONE, in);
    }
}
