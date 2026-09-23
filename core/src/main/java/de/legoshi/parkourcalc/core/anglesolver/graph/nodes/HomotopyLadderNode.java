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
import de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;

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
        double[] yaws = best.yawsDeg;
        String stage = "homotopy ladder";
        if (!best.feasible()) {
            ctx.closestMiss().offer(best.yawsDeg, best.maxViolation);
            yaws = snapNearMiss(ctx, sc, best, nodeToken, deadlineNanos);
            if (yaws == null) return NodeOutcome.of(Guarantee.NONE, in);
            stage = "homotopy ladder -> cell snap";
        }
        if (Double.isNaN(Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, yaws,
                best.px, best.pz, ctx.feasTol))) {
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        Scoring.adoptPinnedStart(ctx.scenario, best.px, best.pz);
        ctx.chainAppend(stage);
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, yaws));
    }

    public static final double NEAR_MISS_VIOL = 2.0e-3;
    private static final long SNAP_BUDGET_NANOS = 1_000_000_000L;

    private static double[] snapNearMiss(GraphContext ctx, JumpPhysicsInputs sc, FoldReplayDriver.Round miss,
                                         AtomicBoolean nodeToken, long deadlineNanos) {
        if (!(miss.maxViolation <= NEAR_MISS_VIOL)) return null;
        long now = System.nanoTime();
        long snapDeadline = deadlineNanos > 0 ? Math.min(deadlineNanos, now + SNAP_BUDGET_NANOS) : now + SNAP_BUDGET_NANOS;
        if (snapDeadline <= now) return null;
        JumpPhysicsInputs at = Scoring.pinnedScenario(sc, miss.px, miss.pz);
        JumpSpec pinned = new JumpSpec(at, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
        double[] gf = at.toGameFacings(Angles.wrapAll(miss.yawsDeg));
        WrapWindowIls.Config cfg = new WrapWindowIls.Config();
        cfg.maxAbsGf = 180.0;
        WrapWindowIls.Result w = WrapWindowIls.polish(ctx.exactModel, pinned, gf, new double[] {0.0, 0.0, 0.0, 0.0},
                cfg, snapDeadline, nodeToken);
        if (SolverTrace.on()) {
            SolverTrace.log("LADDER", "near miss viol=%.3e snap -> %s", miss.maxViolation,
                    w == null ? "none" : String.format(java.util.Locale.ROOT, "viol=%.3e evals=%d", w.viol, w.evals));
        }
        if (w == null || !(w.viol <= ctx.feasTol)) return null;
        return Angles.wrapAll(w.gf);
    }
}
