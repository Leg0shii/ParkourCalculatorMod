package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RecedingHorizonNode implements NodeRuntime {

    private final int window;
    private final int commit;

    public RecedingHorizonNode(ParamValues params) {
        this.window = params.getInt("window");
        this.commit = params.getInt("commit");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        ctx.chainAppend("receding horizon");
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "receding horizon start");
        double[] fromScratch = LongRunSolver.solve(ctx.exactModel, ctx.spec, ctx.feasTol, nodeToken,
                LongRunSolver.LongRunConfig.of(window, commit));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "receding horizon %s", fromScratch != null ? "solved" : "miss");
        if (fromScratch == null) return NodeOutcome.of(Guarantee.NONE, in);
        return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, fromScratch));
    }
}
