package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmoothNode implements NodeRuntime {

    public static final long FAST_BUDGET_NANOS = 400_000_000L;

    private final double giveBackRatio;
    private final double turnCapDeg;

    public SmoothNode(ParamValues params) {
        this.giveBackRatio = params.getDouble("giveBack");
        this.turnCapDeg = params.getDouble("turnCap");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        JumpPhysicsInputs sc = ctx.scenario;
        double[] gf = sc.toGameFacings(in.yaws);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(ctx.spec);
        ForwardPath path = ctx.exactModel.forward(sc, gf);
        if (comp.maxViolation(gf, path) > ctx.feasTol) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        double giveBack = giveBackBlocks(ctx, path);
        long deadline = deadlineNanos > 0 ? deadlineNanos : System.nanoTime() + FAST_BUDGET_NANOS;
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("smooth"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "smooth start giveBack=%.6f blocks cap=%.2f", giveBack, turnCapDeg);
        double[] smoothed;
        try {
            smoothed = TrendFilterSmooth.smooth(ctx.exactModel, ctx.spec, in.yaws, giveBack, turnCapDeg, deadline, nodeToken);
        } catch (RuntimeException e) {
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "final facing smoothing failed: %s", String.valueOf(e));
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        if (smoothed == null || Arrays.equals(smoothed, in.yaws)) return NodeOutcome.of(Guarantee.UNCHANGED, in);
        ctx.chainAppend("smooth");
        return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, smoothed));
    }

    private double giveBackBlocks(GraphContext ctx, ForwardPath path) {
        Objective obj = ctx.spec.objective;
        if (obj.isCustomAngle()) return Double.POSITIVE_INFINITY;
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(ctx.spec.constraints, obj);
        if (wall == null) return Double.POSITIVE_INFINITY;
        double achieved = obj.evaluate(path);
        double offset = obj.sense == Objective.Sense.MAX ? achieved - wall.rhs : wall.rhs - achieved;
        return Math.max(0.0, offset) * giveBackRatio;
    }
}
