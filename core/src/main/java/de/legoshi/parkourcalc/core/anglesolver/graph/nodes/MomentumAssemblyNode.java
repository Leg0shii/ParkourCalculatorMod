package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.MomentumAssembly;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MomentumAssemblyNode implements NodeRuntime {

    private final int minBudgetSec;

    public MomentumAssemblyNode(ParamValues params) {
        this.minBudgetSec = params.getInt("minBudgetSec");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : 0L;
        if (remaining <= minBudgetSec * 1_000_000_000L) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("momentum assembly"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "momentum assembly start budgetMs=%d", remaining / 1_000_000L);
        MomentumAssembly.Result asm = MomentumAssembly.solve(ctx.exactModel, ctx.spec, ctx.feasTol,
                ctx.freeBox, deadlineNanos, nodeToken);
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "momentum assembly %s", asm != null ? "solved" : "miss");
        if (asm == null) return NodeOutcome.of(Guarantee.NONE, in);
        JumpPhysicsInputs sc = ctx.scenario;
        if (asm.startX != sc.startPos.x || asm.startZ != sc.startPos.z) {
            sc.startPos = new Vec3dCore(asm.startX, sc.startPos.y, asm.startZ);
            sc.startBox = StartBox.pinned(asm.startX, asm.startZ, sc.initialVelocity.x, sc.initialVelocity.z);
        }
        ctx.chainAppend("momentum assembly");
        Candidate out = Candidate.of(ctx, asm.yaws);
        if (ctx.progress != null) ctx.progress.report(asm.yaws, out.objective, out.violation, true);
        return NodeOutcome.of(Guarantee.FOUND, out);
    }
}
