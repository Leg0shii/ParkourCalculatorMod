package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FoldDriverNode implements NodeRuntime {

    private static final long MIN_START_REMAINING_NANOS = 2_000_000_000L;
    private static final int MULTI_START_TICK_CAP = 120;

    private final boolean improve;
    private final int objectiveRounds;
    private final int multiStart;
    private final String labelSuffix;

    public FoldDriverNode(ParamValues params) {
        this.improve = "IMPROVE".equals(params.getString("mode"));
        this.objectiveRounds = params.getInt("objectiveRounds");
        this.multiStart = params.getInt("multiStart");
        this.labelSuffix = params.getString("labelSuffix");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        Guarantee miss = improve ? Guarantee.UNCHANGED : Guarantee.NONE;
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(miss, in);
        if (JumpLinearModel.hasFacingWall(ctx.spec.constraints)) return NodeOutcome.of(miss, in);
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec dspec = driverSpec(ctx, null);

        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.cancel = nodeToken;
        p.deadlineNanos = deadlineNanos;
        p.objectiveRounds = improve ? objectiveRounds : 0;

        FoldReplayDriver.Result res;
        if (improve && in != null && in.yaws != null && in.feasible) {
            res = FoldReplayDriver.polishFromAnchor(exact, dspec, in.yaws,
                    ctx.scenario.startPos.x, ctx.scenario.startPos.z, p);
        } else {
            res = FoldReplayDriver.solve(exact, dspec, p);
        }
        FoldReplayDriver.Round best = pickBest(ctx, res.best, null);
        best = runMultiStarts(ctx, exact, p, best, nodeToken, deadlineNanos);
        traceRounds(res);

        if (best == null) return NodeOutcome.of(miss, in);
        if (!best.feasible()) {
            ctx.closestMiss().offer(best.yawsDeg, best.maxViolation);
            return NodeOutcome.of(miss, in);
        }
        double bestObj = verifiedObjective(ctx, best);
        if (Double.isNaN(bestObj)) return NodeOutcome.of(miss, in);
        boolean max = ctx.maximize();
        if (in != null && in.yaws != null) {
            double cur = ctx.exactObjective(in.yaws);
            boolean better = max ? bestObj > cur : bestObj < cur;
            if (!better && (improve || in.feasible)) return NodeOutcome.of(miss, in);
        }
        adoptStart(ctx, best);
        ctx.chainAppend("fold driver" + (labelSuffix == null ? "" : labelSuffix));
        Candidate out = Candidate.of(ctx, best.yawsDeg);
        return NodeOutcome.of(improve ? Guarantee.IMPROVED : Guarantee.FOUND, out);
    }

    private FoldReplayDriver.Round runMultiStarts(GraphContext ctx, ExactJumpModel exact,
                                                  FoldReplayDriver.Params p, FoldReplayDriver.Round best,
                                                  AtomicBoolean nodeToken, long deadlineNanos) {
        if (!improve || multiStart <= 0 || !ctx.freeStart || ctx.freeBox == null) return best;
        if (ctx.scenario.numTicks > MULTI_START_TICK_CAP) return best;
        StartBox box = ctx.freeBox;
        List<double[]> refs = new ArrayList<>();
        refs.add(new double[]{0.5 * (box.pxLo + box.pxHi), 0.5 * (box.pzLo + box.pzHi)});
        refs.add(new double[]{box.pxLo, box.pzLo});
        refs.add(new double[]{box.pxHi, box.pzHi});
        refs.add(new double[]{box.pxLo, box.pzHi});
        refs.add(new double[]{box.pxHi, box.pzLo});
        int done = 0;
        for (double[] ref : refs) {
            if (done >= multiStart) break;
            if (nodeToken != null && nodeToken.get()) break;
            if (deadlineNanos > 0 && deadlineNanos - System.nanoTime() < MIN_START_REMAINING_NANOS) break;
            JumpSpec sspec = driverSpec(ctx, new StartBox(ref[0], ref[1],
                    box.vx, box.vz, box.pxLo, box.pxHi, box.pzLo, box.pzHi,
                    box.vx, box.vx, box.vz, box.vz));
            FoldReplayDriver.Result r = FoldReplayDriver.solve(exact, sspec, p);
            done++;
            best = pickBest(ctx, best, r.best);
            traceRounds(r);
        }
        return best;
    }

    private FoldReplayDriver.Round pickBest(GraphContext ctx, FoldReplayDriver.Round a,
                                            FoldReplayDriver.Round b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.feasible() != b.feasible()) return a.feasible() ? a : b;
        if (!a.feasible()) return a.maxViolation <= b.maxViolation ? a : b;
        boolean max = ctx.maximize();
        return (max ? a.objective >= b.objective : a.objective <= b.objective) ? a : b;
    }

    private JumpSpec driverSpec(GraphContext ctx, StartBox boxOverride) {
        JumpPhysicsInputs sc = ctx.scenario.copy();
        if (boxOverride != null) {
            sc.startBox = boxOverride;
            sc.startPos = new Vec3dCore(boxOverride.px, sc.startPos.y, boxOverride.pz);
        } else if (ctx.freeStart && ctx.freeBox != null && !ctx.stageLocked()) {
            sc.startBox = ctx.freeBox;
        }
        List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint> cons =
                new ArrayList<>(ctx.spec.constraints);
        return new JumpSpec(sc, cons, ctx.spec.objective);
    }

    private double verifiedObjective(GraphContext ctx, FoldReplayDriver.Round best) {
        JumpPhysicsInputs at = Scoring.pinnedScenario(ctx.scenario, best.px, best.pz);
        double[] gf = at.toGameFacings(best.yawsDeg);
        ForwardPath path = ctx.model.forward(at, gf);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(ctx.spec);
        if (compiled.maxViolation(gf, path) > ctx.feasTol) return Double.NaN;
        return path.getPos(ctx.spec.objective.tick, ctx.spec.objective.axis);
    }

    private void adoptStart(GraphContext ctx, FoldReplayDriver.Round best) {
        JumpPhysicsInputs sc = ctx.scenario;
        if (best.px != sc.startPos.x || best.pz != sc.startPos.z) {
            sc.startPos = new Vec3dCore(best.px, sc.startPos.y, best.pz);
        }
        sc.startBox = StartBox.pinned(best.px, best.pz, sc.initialVelocity.x, sc.initialVelocity.z);
    }

    static void traceRounds(FoldReplayDriver.Result res) {
        if (!SolverTrace.on() || res == null) return;
        for (FoldReplayDriver.Round r : res.rounds) {
            SolverTrace.log("DRIVER", "round=%d bound=%.6f obj=%.9f viol=%.3e events=%d polished=%s",
                    r.index, r.linearBound, r.objective, r.maxViolation, r.patternEvents, r.polished);
        }
        if (res.best != null) {
            SolverTrace.log("DRIVER", "best obj=%.9f viol=%.3e fixedPoint=%s",
                    res.best.objective, res.best.maxViolation, res.fixedPoint);
        }
    }
}
