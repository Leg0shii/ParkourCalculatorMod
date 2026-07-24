package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LabelNode implements NodeRuntime {

    private final String text;

    public LabelNode(ParamValues params) {
        this.text = params.getString("text");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in != null && in.yaws != null && text != null && !text.isEmpty()) {
            ctx.chainSuffix(text);
        }
        return NodeOutcome.of(Guarantee.DONE, in);
    }
}
