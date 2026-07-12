package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DualChainNode implements NodeRuntime {

    private final boolean keepBetter;

    public DualChainNode(ParamValues params) {
        this.keepBetter = params.getBool("keepBetter");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        String[] chainName = new String[1];
        double[] chain = AngleSolverEngine.dualChain(ctx.exactModel, ctx.spec, ctx.scenario, nodeToken,
                chainName, deadlineNanos);
        if (chain == null) {
            if (!keepBetter) ctx.chainAppend("closed form");
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        if (in == null || in.yaws == null) {
            ctx.chainAppend(chainName[0]);
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, chain));
        }
        boolean max = ctx.maximize();
        double cur = ctx.exactObjective(in.yaws);
        double chained = ctx.exactObjective(chain);
        if (max ? chained > cur : chained < cur) {
            ctx.chainAppend(chainName[0]);
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, chain));
        }
        return NodeOutcome.of(Guarantee.FOUND, in);
    }
}
