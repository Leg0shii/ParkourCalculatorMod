package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WrapYawsNode implements NodeRuntime {

    public WrapYawsNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.DONE, in);
        return NodeOutcome.of(Guarantee.DONE, Candidate.of(ctx, Angles.wrapAll(in.yaws)));
    }
}
