package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.concurrent.atomic.AtomicBoolean;

public final class IlsPolishNode implements NodeRuntime {

    private final int roundCap;
    private final IlsPolish.Config cfg;

    public IlsPolishNode(ParamValues params) {
        this.roundCap = params.getInt("roundCap");
        this.cfg = new IlsPolish.Config();
        cfg.perturbTicksMin = params.getInt("perturbTicksMin");
        cfg.perturbTicksSpan = params.getInt("perturbTicksSpan");
        cfg.perturbMagMin = params.getDouble("perturbMagMin");
        cfg.perturbMagSpan = params.getDouble("perturbMagSpan");
        cfg.climbMuIneq = params.getDouble("climbMuIneq");
        cfg.climbMuEq = params.getDouble("climbMuEq");
        cfg.climbSigmaDeg = params.getDouble("climbSigmaDeg");
        cfg.climbMaxEval = params.getInt("climbMaxEval");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("ILS"));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "ils start remainingMs=%d",
                    deadlineNanos > 0 ? (deadlineNanos - System.nanoTime()) / 1_000_000L : -1);
        }
        double[] ils = IlsPolish.polish(ctx.model, ctx.spec, in.yaws, deadlineNanos, roundCap,
                ctx.sequential, nodeToken, ctx.progress, cfg);
        if (ils != null) {
            boolean max = ctx.maximize();
            double cur = ctx.exactObjective(in.yaws);
            double ilsObj = ctx.exactObjective(ils);
            if (max ? ilsObj > cur : ilsObj < cur) {
                ctx.chainAppend("ILS");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, ils));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }
}
