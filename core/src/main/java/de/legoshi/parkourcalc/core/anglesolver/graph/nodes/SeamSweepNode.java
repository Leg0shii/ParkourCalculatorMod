package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.SeamSweepRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SeamSweepNode implements NodeRuntime {

    private final SeamSweepRecovery.Config cfg;

    public SeamSweepNode(ParamValues params) {
        this.cfg = new SeamSweepRecovery.Config();
        cfg.sweepPinHalf = params.getDouble("sweepPinHalf");
        cfg.narrowPinHalf = params.getDouble("narrowPinHalf");
        cfg.finePinHalf = params.getDouble("finePinHalf");
        cfg.beamPinHalf = params.getDouble("beamPinHalf");
        cfg.holdPinHalf = params.getDouble("holdPinHalf");
        cfg.maxSeams = params.getInt("maxSeams");
        cfg.maxCells1d = params.getInt("maxCells1d");
        cfg.maxCells2d = params.getInt("maxCells2d");
        cfg.narrowCells1d = params.getInt("narrowCells1d");
        cfg.narrowCells2d = params.getInt("narrowCells2d");
        cfg.slpRescueCap = params.getInt("slpRescueCap");
        cfg.narrowSlpRescueCap = params.getInt("narrowSlpRescueCap");
        cfg.beamWidth = params.getInt("beamWidth");
        cfg.beamMaxCells = params.getInt("beamMaxCells");
        cfg.wideBeamWidth = params.getInt("wideBeamWidth");
        cfg.wideBeamMaxCells = params.getInt("wideBeamMaxCells");
        cfg.beamMaxSeams = params.getInt("beamMaxSeams");
        cfg.beamSlpCap = params.getInt("beamSlpCap");
        cfg.polishReserveFraction = params.getDouble("polishReserveFraction");
        cfg.longRunFraction = params.getDouble("longRunFraction");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : 0L;
        if (remaining <= 0) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("seam sweep"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "seam sweep start budgetMs=%d", remaining / 1_000_000L);
        double[] swept = SeamSweepRecovery.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken,
                remaining, Angles.wrapAll(in.yaws.clone()), cfg);
        if (swept != null) {
            boolean max = ctx.maximize();
            double cur = ctx.scoredObjective(in.yaws);
            double sweptObj = ctx.scoredObjective(swept);
            if (max ? sweptObj > cur : sweptObj < cur) {
                ctx.chainAppend("seam sweep");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, swept));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }
}
