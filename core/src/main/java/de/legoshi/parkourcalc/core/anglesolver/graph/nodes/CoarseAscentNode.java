package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CoarseAscentNode implements NodeRuntime {

    public CoarseAscentNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        double[] wide = BucketAscentPolish.coarse(ctx.model, ctx.spec, in.yaws, nodeToken);
        if (wide == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        boolean max = ctx.maximize();
        double cur = ctx.scoredExactObjective(in.yaws);
        double got = ctx.scoredExactObjective(wide);
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "coarse %.7f -> %.7f", cur, got);
        if (max ? got > cur : got < cur) {
            ctx.chainAppend("coarse ascent");
            return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, wide));
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }
}
