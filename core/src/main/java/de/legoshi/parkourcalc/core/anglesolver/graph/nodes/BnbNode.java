package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BnbNode implements NodeRuntime {

    private final int ffSec;
    private final int optSec;
    private final int tickCap;
    private final String labelSuffix;
    private final BoundPrunedRecovery.Config cfg;

    public BnbNode(ParamValues params) {
        this.ffSec = params.getInt("ffSec");
        this.optSec = params.getInt("optSec");
        this.tickCap = params.getInt("tickCap");
        this.labelSuffix = params.getString("labelSuffix");
        this.cfg = new BoundPrunedRecovery.Config();
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        boolean optimize = in != null && in.yaws != null && in.feasible;
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        int sec = optimize ? optSec : ffSec;
        if (sec <= 0) return NodeOutcome.of(miss, in);
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(miss, in);
        if (tickCap > 0 && ctx.scenario.numTicks > tickCap) return NodeOutcome.of(miss, in);
        if (JumpLinearModel.hasFacingWall(ctx.spec.constraints)
                && de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold.analyze(
                        ctx.spec.constraints, new JumpLinearModel(ctx.scenario)) == null) {
            return NodeOutcome.of(miss, in);
        }
        boolean max = ctx.maximize();
        long budgetNanos = sec * 1_000_000_000L;
        if (deadlineNanos > 0) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) return NodeOutcome.of(miss, in);
            budgetNanos = Math.min(budgetNanos, remaining);
        }
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("pattern B&B"));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "bnb %s budgetMs=%d", optimize ? "optimize" : "rescue",
                    budgetNanos / 1_000_000L);
        }
        double stopAt = Double.NaN;
        if (!optimize) {
            double cap = Scoring.objectiveCap(ctx.spec);
            stopAt = Double.isNaN(cap) ? (max ? -1.0e300 : 1.0e300)
                    : (max ? cap - Scoring.CAP_GAP_TOL : cap + Scoring.CAP_GAP_TOL);
        }
        double[] warm = in != null ? in.yaws : null;
        double[] bnb = BoundPrunedRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken,
                budgetNanos, stopAt, cfg, ctx.closestMiss(), warm);
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "bnb %s", bnb != null ? "solved" : "miss");
        if (bnb == null) return NodeOutcome.of(miss, in);
        double[] yaws = Angles.wrapAll(bnb);
        if (in != null && in.yaws != null) {
            double cur = ctx.scoredObjective(in.yaws);
            double bnbObj = ctx.scoredObjective(yaws);
            boolean better = max ? bnbObj > cur : bnbObj < cur;
            if (!better && (optimize || in.feasible)) return NodeOutcome.of(miss, in);
        }
        ctx.chainAppend(optimize ? "branch and bound" : "pattern B&B");
        if (labelSuffix != null && !labelSuffix.isEmpty()) ctx.chainSuffix(labelSuffix);
        return NodeOutcome.of(optimize ? Guarantee.IMPROVED : Guarantee.FOUND, Candidate.of(ctx, yaws));
    }
}
