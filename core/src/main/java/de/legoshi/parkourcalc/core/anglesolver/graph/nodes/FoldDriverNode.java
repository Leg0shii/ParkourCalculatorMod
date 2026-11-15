package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.DegenerateTickAscent;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.ParallelTasks;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FoldDriverNode implements NodeRuntime {

    private static final long MIN_START_REMAINING_NANOS = 2_000_000_000L;
    private static final int MULTI_START_TICK_CAP = 120;

    private final int objectiveRounds;
    private final int multiStart;
    private final int ascentMs;
    private final int tickCap;
    private final String labelSuffix;

    public FoldDriverNode(ParamValues params) {
        this.objectiveRounds = params.getInt("objectiveRounds");
        this.multiStart = params.getInt("multiStart");
        this.ascentMs = params.getInt("ascentMs");
        this.tickCap = params.getInt("tickCap");
        this.labelSuffix = params.getString("labelSuffix");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        boolean improve = in != null && in.yaws != null && in.feasible;
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(miss, in);
        if (tickCap > 0 && ctx.scenario.numTicks > tickCap) return NodeOutcome.of(miss, in);
        if (JumpLinearModel.hasFacingWall(ctx.spec.constraints)) return NodeOutcome.of(miss, in);
        if (improve && objectiveRounds <= 0) {
            if (ascentMs <= 0) return NodeOutcome.of(miss, in);
            long ascentDeadline = SolveClock.nanoTime() + ascentMs * 1_000_000L;
            if (deadlineNanos > 0) ascentDeadline = Math.min(ascentDeadline, deadlineNanos);
            JumpPhysicsInputs at0 = ctx.scenario.pinnedAt(ctx.scenario.startPos.x,
                    ctx.scenario.startPos.z);
            JumpSpec pspec0 = new JumpSpec(at0, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
            double[] asc0 = DegenerateTickAscent.improve(ctx.exactModel, pspec0, in.yaws, ctx.feasTol,
                    nodeToken, ascentDeadline);
            if (asc0 == null || asc0 == in.yaws) return NodeOutcome.of(miss, in);
            double cur0 = ctx.exactObjective(in.yaws);
            double asc0Obj = Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, asc0,
                    ctx.scenario.startPos.x, ctx.scenario.startPos.z, ctx.feasTol);
            boolean max0 = ctx.maximize();
            if (Double.isNaN(asc0Obj) || !(max0 ? asc0Obj > cur0 : asc0Obj < cur0)) {
                return NodeOutcome.of(miss, in);
            }
            ctx.chainAppend("fold driver" + (labelSuffix == null ? "" : labelSuffix));
            return NodeOutcome.of(Guarantee.IMPROVED, Candidate.of(ctx, asc0));
        }
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec dspec = driverSpec(ctx, null);

        List<JumpSpec> starts = objectiveRounds > 0 ? multiStartSpecs(ctx, deadlineNanos) : new ArrayList<JumpSpec>();
        List<FoldReplayDriver.Result> results = ParallelTasks.run(1 + starts.size(), nodeToken, ctx.sequential,
                (i, token) -> {
                    FoldReplayDriver.Params tp = new FoldReplayDriver.Params();
                    tp.cancel = token;
                    tp.deadlineNanos = deadlineNanos;
                    tp.objectiveRounds = objectiveRounds;
                    if (i > 0) return FoldReplayDriver.solve(exact, starts.get(i - 1), tp);
                    if (improve) {
                        return FoldReplayDriver.polishFromAnchor(exact, dspec, in.yaws,
                                ctx.scenario.startPos.x, ctx.scenario.startPos.z, tp);
                    }
                    return FoldReplayDriver.solve(exact, dspec, tp);
                });
        FoldReplayDriver.Round best = null;
        for (FoldReplayDriver.Result r : results) {
            if (r == null) continue;
            best = pickBest(ctx, best, r.best);
            traceRounds(r);
        }

        if (best == null) return NodeOutcome.of(miss, in);
        if (!best.feasible()) {
            ctx.closestMiss().offer(best.yawsDeg, best.maxViolation);
            return NodeOutcome.of(miss, in);
        }
        double[] bestYaws = best.yawsDeg;
        double bestPx = best.px;
        double bestPz = best.pz;
        double bestObj = Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, bestYaws, bestPx, bestPz,
                ctx.feasTol);
        if (Double.isNaN(bestObj)) return NodeOutcome.of(miss, in);
        boolean max = ctx.maximize();
        if ((objectiveRounds > 0 || ascentMs > 0) && (nodeToken == null || !nodeToken.get())
                && (deadlineNanos == 0L || SolveClock.nanoTime() < deadlineNanos)) {
            long ascDeadline = ascentMs > 0 ? SolveClock.nanoTime() + ascentMs * 1_000_000L : deadlineNanos;
            if (deadlineNanos > 0 && ascDeadline > 0) ascDeadline = Math.min(ascDeadline, deadlineNanos);
            JumpPhysicsInputs at = ctx.scenario.pinnedAt(bestPx, bestPz);
            JumpSpec pspec = new JumpSpec(at, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
            double[] asc = DegenerateTickAscent.improve(exact, pspec, bestYaws, ctx.feasTol,
                    nodeToken, ascDeadline);
            if (asc != null && asc != bestYaws) {
                double ascObj = Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, asc, bestPx, bestPz,
                        ctx.feasTol);
                if (!Double.isNaN(ascObj) && (max ? ascObj > bestObj : ascObj < bestObj)) {
                    bestYaws = asc;
                    bestObj = ascObj;
                }
            }
        }
        if (in != null && in.yaws != null) {
            double cur = ctx.exactObjective(in.yaws);
            boolean better = max ? bestObj > cur : bestObj < cur;
            if (!better && (improve || in.feasible)) return NodeOutcome.of(miss, in);
        }
        Scoring.adoptPinnedStart(ctx.scenario, bestPx, bestPz);
        ctx.chainAppend("fold driver" + (labelSuffix == null ? "" : labelSuffix));
        Candidate out = Candidate.of(ctx, bestYaws);
        return NodeOutcome.of(improve ? Guarantee.IMPROVED : Guarantee.FOUND, out);
    }

    private List<JumpSpec> multiStartSpecs(GraphContext ctx, long deadlineNanos) {
        List<JumpSpec> specs = new ArrayList<>();
        if (multiStart <= 0 || !ctx.freeStart) return specs;
        if (ctx.scenario.numTicks > MULTI_START_TICK_CAP) return specs;
        if (deadlineNanos > 0 && deadlineNanos - SolveClock.nanoTime() < MIN_START_REMAINING_NANOS) return specs;
        StartBox box = ctx.freeBox;
        List<double[]> refs = new ArrayList<>();
        refs.add(new double[]{0.5 * (box.pxLo + box.pxHi), 0.5 * (box.pzLo + box.pzHi)});
        refs.add(new double[]{box.pxLo, box.pzLo});
        refs.add(new double[]{box.pxHi, box.pzHi});
        refs.add(new double[]{box.pxLo, box.pzHi});
        refs.add(new double[]{box.pxHi, box.pzLo});
        for (int k = 0; k < multiStart && k < refs.size(); k++) {
            double[] ref = refs.get(k);
            specs.add(driverSpec(ctx, new StartBox(ref[0], ref[1],
                    box.vx, box.vz, box.pxLo, box.pxHi, box.pzLo, box.pzHi,
                    box.vx, box.vx, box.vz, box.vz)));
        }
        return specs;
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
        if (boxOverride == null) return Scoring.freeBoxSpec(ctx);
        JumpPhysicsInputs sc = ctx.scenario.copy();
        sc.startBox = boxOverride;
        sc.startPos = new Vec3dCore(boxOverride.px, sc.startPos.y, boxOverride.pz);
        return new JumpSpec(sc, new ArrayList<>(ctx.spec.constraints), ctx.spec.objective);
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
