package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CapCertifyNode implements NodeRuntime {

    private final boolean computeDualGap;
    private final boolean markSettled;
    private final boolean skipIfSettled;

    public CapCertifyNode(ParamValues params) {
        this.computeDualGap = params.getBool("computeDualGap");
        this.markSettled = params.getBool("markSettled");
        this.skipIfSettled = params.getBool("skipIfSettled");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.FALSE, in);
        if (skipIfSettled && ctx.settled()) return NodeOutcome.of(Guarantee.FALSE, in);
        double cap = Scoring.objectiveCap(ctx.spec);
        double achieved = ctx.exactObjective(in.yaws);
        boolean atCap = !Double.isNaN(cap)
                && (ctx.maximize() ? cap - achieved : achieved - cap) <= Scoring.CAP_GAP_TOL;
        if (atCap) {
            ctx.chainSuffix(", optimal at constraint cap");
            if (markSettled) ctx.setSettled(true);
            return NodeOutcome.of(Guarantee.AT_CAP, in);
        }
        if (computeDualGap) {
            double bound = ctx.reachBound();
            if (!Double.isNaN(bound)) {
                ctx.setDualGap(ctx.maximize() ? bound - achieved : achieved - bound);
            }
        }
        return NodeOutcome.of(Guarantee.FALSE, in);
    }
}
