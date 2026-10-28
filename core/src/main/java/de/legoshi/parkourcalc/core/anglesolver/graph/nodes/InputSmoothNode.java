package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.InputOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.SmoothOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.InputSmooth;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InputSmoothNode implements NodeRuntime {

    public static final long FAST_BUDGET_NANOS = 3L * SmoothNode.FAST_BUDGET_NANOS;

    private final double giveBackRatio;
    private final boolean allowA;
    private final boolean allowD;

    public InputSmoothNode(ParamValues params) {
        this.giveBackRatio = params.getDouble("giveBack");
        this.allowA = params.getBool("allowA");
        this.allowD = params.getBool("allowD");
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
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("smooth inputs"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "input smooth start giveBack=%.6f blocks", giveBack);
        TickInputs[] before = new TickInputs[sc.numTicks];
        for (int t = 0; t < before.length; t++) before[t] = TickInputs.of(sc, t);
        InputSmooth.Options opt = new InputSmooth.Options();
        opt.allowA = allowA;
        opt.allowD = allowD;
        opt.resolver = resolver(ctx);
        InputSmooth.Report report;
        try {
            report = InputSmooth.smooth(ctx.exactModel, ctx.spec, in.yaws, giveBack, opt, deadline, nodeToken);
        } catch (RuntimeException e) {
            if (SolverTrace.on()) SolverTrace.log("ENGINE", "input smoothing failed: %s", String.valueOf(e));
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        if (report.changed > 0) adopt(sc, report.scenario);
        ctx.setSmoothOutcome(new SmoothOutcome(in.yaws, report.yaws, report.stop, report.nanos));
        ctx.setInputOutcome(new InputOutcome(report.inputs, before, report.changed, report.tried));
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "input smooth stop=%s changed=%d tried=%d ms=%d", report.stop.label,
                    report.changed, report.tried, report.nanos / 1_000_000L);
        }
        if (report.yaws == null || (report.changed == 0 && Arrays.equals(report.yaws, in.yaws))) {
            return NodeOutcome.of(Guarantee.UNCHANGED, in);
        }
        ctx.chainAppend(report.changed > 0 ? "smooth inputs" : "smooth");
        return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, report.yaws));
    }

    private static void adopt(JumpPhysicsInputs target, JumpPhysicsInputs source) {
        target.forwardInputPerTick = source.forwardInputPerTick;
        target.strafeInputPerTick = source.strafeInputPerTick;
        target.sprintPerTick = source.sprintPerTick;
        target.sprintKeyPerTick = source.sprintKeyPerTick;
        target.strafePerTick = source.strafePerTick;
        target.strafeSignPerTick = source.strafeSignPerTick;
    }

    private static InputSmooth.Resolver resolver(GraphContext ctx) {
        SolverGraph fast = BuiltinGraphs.fast();
        return (guarded, seed, deadline, cancel) -> {
            AtomicBoolean token = cancel != null ? cancel : new AtomicBoolean(false);
            GraphContext sub = new GraphContext(guarded, ctx.model, null, null, ctx.feasTol, token, null, true,
                    ctx.longRun);
            sub.setOverallDeadline(deadline);
            Candidate c;
            try {
                c = GraphRunner.run(fast, sub, Candidate.of(sub, seed));
            } catch (RuntimeException e) {
                if (SolverTrace.on()) SolverTrace.log("ENGINE", "input smooth re-solve failed: %s", e);
                return null;
            }
            return c == null || c.yaws == null || !c.feasible ? null : c.yaws;
        };
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
