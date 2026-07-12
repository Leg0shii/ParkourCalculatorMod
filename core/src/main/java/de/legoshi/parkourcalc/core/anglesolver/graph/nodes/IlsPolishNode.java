package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class IlsPolishNode implements NodeRuntime {

    private final int roundCap;

    public IlsPolishNode(ParamValues params) {
        this.roundCap = params.getInt("roundCap");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("ILS"));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "ils start remainingMs=%d",
                    deadlineNanos > 0 ? (deadlineNanos - System.nanoTime()) / 1_000_000L : -1);
        }
        double[] ils = IlsPolish.polish(ctx.model, ctx.spec, in.yaws, deadlineNanos, roundCap,
                ctx.sequential, nodeToken, ctx.progress);
        if (ils != null) {
            boolean max = ctx.maximize();
            double cur = ctx.exactObjective(in.yaws);
            double ilsObj = ctx.exactObjective(ils);
            if (max ? ilsObj > cur : ilsObj < cur) {
                ctx.chainAppend("ILS");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, ils));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }
}
