package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.CountingForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamParse;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothingPolish;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SmoothingNode implements NodeRuntime {

    private final boolean countEvals;
    private final SmoothingPolish.Config cfg;

    public SmoothingNode(ParamValues params) {
        this.countEvals = params.getBool("countEvals");
        this.cfg = new SmoothingPolish.Config();
        cfg.maxRounds = params.getInt("maxRounds");
        cfg.maxEvals = params.getInt("maxEvals");
        cfg.pairSpan = params.getInt("pairSpan");
        cfg.fractions = ParamParse.doubles(params.getString("fractions"), cfg.fractions);
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null || ctx.stageLocked()) return NodeOutcome.of(Guarantee.DONE, in);
        double[] smoothed;
        if (countEvals) {
            CountingForwardModel counting = new CountingForwardModel(ctx.model);
            smoothed = SmoothingPolish.smooth(counting, ctx.spec, in.yaws, nodeToken, cfg);
            ctx.smoothingEvals.addAndGet(counting.evals());
        } else {
            smoothed = SmoothingPolish.smooth(ctx.model, ctx.spec, in.yaws, nodeToken, cfg);
        }
        if (smoothed == null) return NodeOutcome.of(Guarantee.DONE, in);
        return NodeOutcome.of(Guarantee.DONE, Candidate.of(ctx, smoothed));
    }
}
