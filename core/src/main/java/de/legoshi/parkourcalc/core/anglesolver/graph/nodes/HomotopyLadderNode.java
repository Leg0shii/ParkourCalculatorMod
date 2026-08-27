package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.WallHomotopyLadder;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HomotopyLadderNode implements NodeRuntime {

    private final int tickCap;

    public HomotopyLadderNode(ParamValues params) {
        this.tickCap = params.getInt("cap");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(Guarantee.NONE, in);
        if (in != null && in.feasible) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.scenario.numTicks > tickCap) return NodeOutcome.of(Guarantee.NONE, in);
        if (JumpLinearModel.hasFacingWall(ctx.spec.constraints)) return NodeOutcome.of(Guarantee.NONE, in);

        JumpPhysicsInputs sc = ctx.scenario.copy();
        if (ctx.freeStart && ctx.freeBox != null) sc.startBox = ctx.freeBox;
        JumpSpec dspec = new JumpSpec(sc, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
        WallHomotopyLadder.Result lr = WallHomotopyLadder.solve(ctx.exactModel, dspec, nodeToken, deadlineNanos);
        if (SolverTrace.on()) {
            for (WallHomotopyLadder.Rung rung : lr.rungs) {
                FoldReplayDriver.Round rb = rung.result.best;
                SolverTrace.log("LADDER", "delta=%.3g best=%s", rung.delta,
                        rb == null ? "none" : String.format(java.util.Locale.ROOT,
                                "obj=%.9f viol=%.3e", rb.objective, rb.maxViolation));
            }
        }
        FoldReplayDriver.Round best = lr.best;
        if (best == null) return NodeOutcome.of(Guarantee.NONE, in);
        if (!best.feasible()) {
            ctx.closestMiss().offer(best.yawsDeg, best.maxViolation);
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        if (Double.isNaN(Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, best.yawsDeg,
                best.px, best.pz, ctx.feasTol))) {
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        Scoring.adoptPinnedStart(ctx.scenario, best.px, best.pz);
        ctx.chainAppend("homotopy ladder");
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, best.yawsDeg));
    }
}
