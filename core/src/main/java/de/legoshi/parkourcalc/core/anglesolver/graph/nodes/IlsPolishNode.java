package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.IlsPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.YawTies;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IlsPolishNode implements NodeRuntime {

    private final IlsPolish.Config cfg;

    public IlsPolishNode(ParamValues params) {
        this.cfg = new IlsPolish.Config();
        cfg.perturbTicksMin = params.getInt("perturbTicksMin");
        cfg.perturbTicksSpan = params.getInt("perturbTicksSpan");
        cfg.perturbMagMin = params.getDouble("perturbMagMin");
        cfg.perturbMagSpan = params.getDouble("perturbMagSpan");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (!in.feasible || ctx.stageLocked()) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        if (deadlineNanos <= 0) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        cfg.freeTicks = freeTicksFrom(ctx.spec.constraints, in.yaws.length);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("ILS"));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "ils start remainingMs=%d",
                    (deadlineNanos - System.nanoTime()) / 1_000_000L);
        }
        double[] ils = IlsPolish.polish(ctx.model, ctx.spec, in.yaws, deadlineNanos, Integer.MAX_VALUE,
                ctx.sequential, nodeToken, ctx.progress, cfg);
        if (ils != null) {
            boolean max = ctx.maximize();
            double cur = ctx.scoredExactObjective(in.yaws);
            double ilsObj = ctx.scoredExactObjective(ils);
            if (max ? ilsObj > cur : ilsObj < cur) {
                ctx.chainAppend("ILS");
                ctx.chainSuffix(" (better objective)");
                return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, ils));
            }
        }
        return NodeOutcome.of(Guarantee.UNCHANGED, in);
    }

    public static int[] freeTicksFrom(List<JumpConstraint> constraints, int n) {
        YawTies ties = YawTies.of(constraints, n);
        if (ties == null) return null;
        int[] counts = new int[n];
        for (int t = 0; t < n; t++) counts[ties.groupOf(t)]++;
        int[] tmp = new int[n];
        int m = 0;
        for (int t = 0; t < n; t++) {
            if (ties.varOf(t) >= 0 && counts[ties.groupOf(t)] == 1) tmp[m++] = t;
        }
        if (m == 0 || m == n) return null;
        return Arrays.copyOf(tmp, m);
    }
}
