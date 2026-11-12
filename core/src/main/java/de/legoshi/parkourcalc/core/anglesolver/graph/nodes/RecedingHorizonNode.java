package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RecedingHorizonNode implements NodeRuntime {

    private final LongRunSolver.LongRunConfig cfg;
    private final boolean keepIncoming;

    public RecedingHorizonNode(ParamValues params) {
        this.keepIncoming = params.getBool("keepIncoming");
        this.cfg = LongRunSolver.LongRunConfig.of(params.getInt("window"), params.getInt("commit"),
                ParamParse.ints(params.getString("windowLadder"), null),
                ParamParse.ints(params.getString("commitLadder"), null));
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        if (ctx.jumpCount() <= 1) return NodeOutcome.of(Guarantee.NONE, in);
        boolean hold = keepIncoming && in != null && in.yaws != null && in.feasible && ctx.freeStart
                && !ctx.stageLocked();
        if (in != null && in.yaws != null && !hold) return NodeOutcome.of(Guarantee.NONE, in);
        if (hold) ctx.holdStart(in);
        ctx.chainAppend("receding horizon");
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "receding horizon start");
        LongRunSolver.WindowCache windows = new LongRunSolver.WindowCache();
        double[] fromScratch = LongRunSolver.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken, cfg, windows,
                deadlineNanos);
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "receding horizon %s", fromScratch != null ? "solved" : "miss");
        LongRunSolver.FreeRun free = ctx.freeStart && !ctx.stageLocked()
                ? LongRunSolver.solveFree(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken, cfg, ctx.freeBox,
                        new LongRunSolver.WindowCache(), deadlineNanos)
                : null;
        if (free != null) {
            JumpPhysicsInputs sc = ctx.scenario;
            Scoring.adoptPinnedStart(sc, free.startX, free.startZ);
            ctx.chainAppend("free window");
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "receding horizon free window start=(%.5f,%.5f)",
                        free.startX, free.startZ);
            }
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, free.gf));
        }
        if (fromScratch == null) {
            if (hold) ctx.dropHeldStart();
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, fromScratch));
    }
}
