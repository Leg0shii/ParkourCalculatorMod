package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;

import java.util.concurrent.atomic.AtomicBoolean;

public final class StartPickNode implements NodeRuntime {

    public StartPickNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        Candidate held = ctx.heldStart();
        if (held == null) return NodeOutcome.of(Guarantee.DONE, in);
        boolean further = in != null && in.yaws != null && in.feasible
                && (ctx.maximize() ? in.objective > held.objective : in.objective < held.objective);
        if (further) {
            ctx.dropHeldStart();
            return NodeOutcome.of(Guarantee.DONE, in);
        }
        ctx.chainAppend("first start kept");
        return NodeOutcome.of(Guarantee.DONE, ctx.restoreHeldStart());
    }
}
