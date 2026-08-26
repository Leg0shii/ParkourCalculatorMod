package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SphereDecodeSnap;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SphereSnapNode implements NodeRuntime {

    private static final long FAST_BUDGET_NANOS = 2_000_000_000L;

    private final int minRemainingSec;

    public SphereSnapNode(ParamValues params) {
        this.minRemainingSec = params.getInt("minRemainingSec");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null) {
            return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : Long.MAX_VALUE;
        if (remaining <= minRemainingSec * 1_000_000_000L) return NodeOutcome.of(Guarantee.REJECTED, in);
        long snapDeadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + FAST_BUDGET_NANOS;
        JumpPhysicsInputs sc = ctx.scenario;
        double[] snapGf = SphereDecodeSnap.snap(ctx.exactModel, ctx.spec, in.yaws, ctx.feasTol, nodeToken, snapDeadline);
        if (snapGf == null) return NodeOutcome.of(Guarantee.REJECTED, in);
        if (!Scoring.adoptStageResult(ctx.model, sc, ctx.spec, ctx.freeBox, snapGf, ctx.feasTol)) {
            return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        boolean[] lockAll = new boolean[sc.numTicks];
        Arrays.fill(lockAll, true);
        sc.yawLockedPerTick = lockAll;
        ctx.setStageLocked(true);
        ctx.chainAppend("sphere snap");
        return NodeOutcome.of(Guarantee.ADOPTED, Candidate.of(ctx, snapGf));
    }
}
