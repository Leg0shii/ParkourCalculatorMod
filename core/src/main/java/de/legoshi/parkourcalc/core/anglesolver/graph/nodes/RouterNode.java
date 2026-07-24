package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.RouterPredicate;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RouterNode implements NodeRuntime {

    private final RouterPredicate predicate;
    private final double epsilon;
    private final int cap;

    public RouterNode(ParamValues params) {
        this.predicate = RouterPredicate.valueOf(params.getString("predicate"));
        this.epsilon = params.getDouble("epsilon");
        this.cap = params.getInt("cap");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        boolean v = predicate.evaluate(ctx, in, epsilon, cap);
        return NodeOutcome.of(v ? Guarantee.TRUE : Guarantee.FALSE, in);
    }
}
