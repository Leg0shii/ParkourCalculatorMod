package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.LeafSnap;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LeafSnapNode implements NodeRuntime {

    private static final long FAST_BUDGET_NANOS = 2_000_000_000L;
    private static final int MAX_CALLS = 64;
    private static final int WINDOW_STEP = 6;

    private final boolean pairPass;
    private final boolean repeat;

    public LeafSnapNode(ParamValues params) {
        this.pairPass = params.getInt("pairPass") > 0;
        this.repeat = params.getBool("repeat");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || ctx.stageLocked() || in == null || in.yaws == null) {
            return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        long snapDeadline = deadlineNanos > 0 ? deadlineNanos : SolveClock.nanoTime() + FAST_BUDGET_NANOS;
        JumpPhysicsInputs sc = ctx.scenario;
        double[] snapGf = LeafSnap.snap(ctx.exactModel, ctx.spec, in.yaws, ctx.feasTol, nodeToken,
                snapDeadline, pairPass);
        if (snapGf == null) return NodeOutcome.of(Guarantee.REJECTED, in);
        if (ctx.scoredViol(snapGf) > ctx.feasTol) return NodeOutcome.of(Guarantee.REJECTED, in);
        if (in.feasible) {
            boolean max = ctx.maximize();
            double candObj = ctx.scoredObjective(snapGf);
            double curObj = ctx.scoredObjective(in.yaws);
            if (!(max ? candObj > curObj : candObj < curObj)) return NodeOutcome.of(Guarantee.REJECTED, in);
        }
        double[] first = snapGf;
        double heldObj = ctx.scoredObjective(snapGf);
        int calls = 1;
        while (repeat && deadlineNanos > 0 && calls < MAX_CALLS && SolveClock.nanoTime() < snapDeadline) {
            double[] next = LeafSnap.snap(ctx.exactModel, ctx.spec, snapGf, ctx.feasTol, nodeToken,
                    snapDeadline, pairPass);
            if (next == null || ctx.scoredViol(next) > ctx.feasTol) break;
            double nextObj = ctx.scoredObjective(next);
            if (!(ctx.maximize() ? nextObj > heldObj : nextObj < heldObj)) break;
            snapGf = next;
            heldObj = nextObj;
            calls++;
        }
        if (repeat && deadlineNanos > 0) {
            int ticks = LeafSnap.snapTickCount(ctx.spec, snapGf);
            boolean moved = ticks > LeafSnap.MAX_ENUM;
            int firstShift = WINDOW_STEP;
            while (moved && SolveClock.nanoTime() < snapDeadline) {
                moved = false;
                for (int shift = firstShift; shift < ticks && SolveClock.nanoTime() < snapDeadline; shift += WINDOW_STEP) {
                    double[] next = LeafSnap.snap(ctx.exactModel, ctx.spec, snapGf, ctx.feasTol, nodeToken,
                            snapDeadline, pairPass, shift);
                    if (next == null || ctx.scoredViol(next) > ctx.feasTol) continue;
                    double nextObj = ctx.scoredObjective(next);
                    if (!(ctx.maximize() ? nextObj > heldObj : nextObj < heldObj)) continue;
                    snapGf = next;
                    heldObj = nextObj;
                    moved = true;
                }
                firstShift = 0;
            }
        }
        if (!Scoring.adoptStageResult(ctx.model, sc, ctx.spec, ctx.freeBox,
                sc.toGameFacings(Angles.wrapAll(snapGf)), ctx.feasTol)) {
            if (snapGf == first || !Scoring.adoptStageResult(ctx.model, sc, ctx.spec, ctx.freeBox,
                    sc.toGameFacings(Angles.wrapAll(first)), ctx.feasTol)) {
                return NodeOutcome.of(Guarantee.REJECTED, in);
            }
            snapGf = first;
        }
        ctx.chainAppend("leaf snap");
        return NodeOutcome.of(Guarantee.ADOPTED, Candidate.of(ctx, snapGf));
    }
}
