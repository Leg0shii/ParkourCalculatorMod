package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.LeafSnap;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LeafSnapNode implements NodeRuntime {

    private static final long FAST_BUDGET_NANOS = 2_000_000_000L;

    private final boolean pairPass;

    public LeafSnapNode(ParamValues params) {
        this.pairPass = params.getInt("pairPass") > 0;
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || ctx.stageLocked() || in == null || in.yaws == null) {
            return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        long snapDeadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + FAST_BUDGET_NANOS;
        JumpPhysicsInputs sc = ctx.scenario;
        double[] snapGf = LeafSnap.snap(ctx.exactModel, ctx.spec, in.yaws, ctx.feasTol, nodeToken,
                snapDeadline, pairPass);
        if (snapGf == null) return NodeOutcome.of(Guarantee.REJECTED, in);
        if (ctx.scoredViol(snapGf) > ctx.feasTol) return NodeOutcome.of(Guarantee.REJECTED, in);
        if (in.feasible) {
            boolean max = ctx.maximize();
            double candObj = ctx.scoredObjective(snapGf);
            double curObj = ctx.scoredObjective(in.yaws);
            if (!(max ? candObj > curObj : candObj < curObj)) return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        if (!Scoring.adoptStageResult(ctx.model, sc, ctx.spec, ctx.freeBox,
                sc.toGameFacings(Angles.wrapAll(snapGf)), ctx.feasTol)) {
            return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        ctx.chainAppend("leaf snap");
        return NodeOutcome.of(Guarantee.ADOPTED, Candidate.of(ctx, snapGf));
    }
}
