package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TranslatedStartNode implements NodeRuntime {

    public TranslatedStartNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null || !ctx.freeStart || ctx.stageLocked()) {
            return NodeOutcome.of(Guarantee.DONE, in);
        }
        if (Scoring.adoptWinningTranslation(ctx.model, ctx.scenario, ctx.spec, ctx.freeBox, in.yaws, ctx.feasTol)) {
            ctx.chainAppend("translated start");
        }
        return NodeOutcome.of(Guarantee.DONE, in);
    }
}
