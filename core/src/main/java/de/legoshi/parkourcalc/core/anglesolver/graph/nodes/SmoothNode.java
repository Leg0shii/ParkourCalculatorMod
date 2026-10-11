package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmoothNode implements NodeRuntime {

    public static final long FAST_BUDGET_NANOS = 400_000_000L;

    public SmoothNode(ParamValues params) {
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || !ctx.smoothFinal() || in == null || in.yaws == null) {
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        if (ctx.spec.objective.smoothLambda <= 0.0) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        JumpPhysicsInputs sc = ctx.scenario;
        double[] gf = sc.toGameFacings(in.yaws);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(ctx.spec);
        if (comp.maxViolation(gf, ctx.exactModel.forward(sc, gf)) > ctx.feasTol) {
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        long deadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + FAST_BUDGET_NANOS;
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("smooth"));
        double[] smoothed;
        try {
            smoothed = TrendFilterSmooth.smooth(ctx.exactModel, ctx.spec, in.yaws, deadline, nodeToken);
        } catch (RuntimeException e) {
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "final facing smoothing failed: %s", String.valueOf(e));
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        if (smoothed == null || Arrays.equals(smoothed, in.yaws)) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        ctx.chainAppend("smooth");
        return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, smoothed));
    }
}
