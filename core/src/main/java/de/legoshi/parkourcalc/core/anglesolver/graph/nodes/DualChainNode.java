package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DualChainNode implements NodeRuntime {

    private final boolean keepBetter;
    private final int warmSec;
    private final ParamValues params;

    public DualChainNode(ParamValues params) {
        this.keepBetter = params.getBool("keepBetter");
        this.warmSec = params.getInt("warmSec");
        this.params = params;
    }

    private SlpSolve.Config slpConfig() {
        SlpSolve.Config cfg = new SlpSolve.Config();
        cfg.phase1Calls = params.getInt("slpPhase1Calls");
        cfg.totalCalls = params.getInt("slpTotalCalls");
        cfg.trStartDeg = params.getDouble("slpTrStartDeg");
        cfg.trMaxDeg = params.getDouble("slpTrMaxDeg");
        cfg.trMinDeg = params.getDouble("slpTrMinDeg");
        cfg.lpMaxIter = params.getInt("slpLpMaxIter");
        return cfg;
    }

    private ClosedFormSolve.Config cfConfig() {
        ClosedFormSolve.Config cfg = new ClosedFormSolve.Config();
        cfg.margins = ParamParse.doubles(params.getString("cfMargins"), cfg.margins);
        cfg.maxInertiaPasses = params.getInt("cfMaxInertiaPasses");
        cfg.rungStallLimit = params.getInt("cfRungStallLimit");
        return cfg;
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact()) return NodeOutcome.of(Guarantee.NONE, in);
        if (in != null && in.yaws != null && in.feasible && warmSec <= 0) {
            return NodeOutcome.of(Guarantee.FOUND, in);
        }
        String[] chainName = new String[1];
        double[] chain = AngleSolverEngine.dualChain(ctx.exactModel, ctx.spec, ctx.scenario, nodeToken,
                chainName, deadlineNanos, slpConfig(), cfConfig(), ctx.closestMiss());
        if (chain == null) {
            if (!keepBetter) ctx.chainAppend("closed form");
            return NodeOutcome.of(Guarantee.NONE, in);
        }
        if (in == null || in.yaws == null) {
            ctx.chainAppend(chainName[0]);
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, chain));
        }
        boolean max = ctx.maximize();
        double cur = ctx.scoredExactObjective(in.yaws);
        double chained = ctx.scoredExactObjective(chain);
        if (max ? chained > cur : chained < cur) {
            ctx.chainAppend(chainName[0]);
            return NodeOutcome.of(Guarantee.FOUND, Candidate.of(ctx, chain));
        }
        return NodeOutcome.of(Guarantee.FOUND, in);
    }
}
