package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.CountingForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CmaesRaceNode implements NodeRuntime {

    private final SolveCore.Budget budget;
    private final double sigmaDeg;
    private final boolean warmStart;

    public CmaesRaceNode(ParamValues params) {
        BucketAscentPolish.Config cfg = "EXHAUSTIVE".equals(params.getString("polishDepth"))
                ? BucketAscentPolish.THOROUGH : BucketAscentPolish.FAST;
        this.budget = new SolveCore.Budget(params.getInt("restarts"), params.getInt("maxEval"),
                params.getInt("polishCount"), cfg);
        this.sigmaDeg = params.getDouble("sigmaDeg");
        this.warmStart = params.getBool("warmStart");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        CountingForwardModel counting = new CountingForwardModel(ctx.model);
        double[] warm = warmStart && in != null ? in.yaws : null;
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("CMA-ES"));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "race start warm=%s deadlineMs=%d", warm != null,
                    deadlineNanos > 0 ? (deadlineNanos - System.nanoTime()) / 1_000_000L : -1);
        }
        double[] cma = SolveCore.optimize(counting, ctx.spec, budget, sigmaDeg, ctx.feasTol, nodeToken,
                warm, deadlineNanos, ctx.sequential, ctx.progress);
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "race end %s", cma != null ? "solved" : "miss");
        ctx.cmaesEvals.addAndGet(counting.evals());
        if (in == null || in.yaws == null) {
            ctx.chainAppend("CMA-ES");
            if (cma == null) return NodeOutcome.of(Guarantee.NONE, in);
            Candidate out = Candidate.of(ctx, cma);
            return NodeOutcome.of(ctx.scoredViol(cma) <= ctx.feasTol ? Guarantee.FEASIBLE : Guarantee.INFEASIBLE, out);
        }
        if (cma == null) {
            return NodeOutcome.of(ctx.scoredViol(in.yaws) <= ctx.feasTol ? Guarantee.FEASIBLE : Guarantee.INFEASIBLE, in);
        }
        boolean max = ctx.maximize();
        boolean curFeasible = ctx.scoredViol(in.yaws) <= ctx.feasTol;
        boolean cmaFeasible = ctx.scoredViol(cma) <= ctx.feasTol;
        boolean take;
        if (curFeasible != cmaFeasible) {
            take = cmaFeasible;
        } else {
            double slpObj = ctx.scoredObjective(in.yaws);
            double cmaObj = ctx.scoredObjective(cma);
            take = max ? cmaObj > slpObj : cmaObj < slpObj;
        }
        if (take) {
            ctx.chainSuffix(" -> CMA-ES (better objective)");
            return NodeOutcome.of(cmaFeasible ? Guarantee.FEASIBLE : Guarantee.INFEASIBLE, Candidate.of(ctx, cma));
        }
        ctx.chainSuffix(" (beat CMA-ES)");
        return NodeOutcome.of(curFeasible ? Guarantee.FEASIBLE : Guarantee.INFEASIBLE, in);
    }
}
