package de.legoshi.parkourcalc.core.anglesolver.graph.nodes;

import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CertifiedBnbNode implements NodeRuntime {

    private final int ffSec;
    private final int ffNodeCap;
    private final int optSec;
    private final int optNodeCap;
    private final int tickCap;
    private final String labelSuffix;

    public CertifiedBnbNode(ParamValues params) {
        this.ffSec = params.getInt("ffSec");
        this.ffNodeCap = params.getInt("ffNodeCap");
        this.optSec = params.getInt("optSec");
        this.optNodeCap = params.getInt("optNodeCap");
        this.tickCap = params.getInt("tickCap");
        this.labelSuffix = params.getString("labelSuffix");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        boolean optimize = in != null && in.yaws != null && in.feasible;
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        int cap = optimize ? optNodeCap : ffNodeCap;
        int sec = optimize ? optSec : ffSec;
        if (cap <= 0) return NodeOutcome.of(miss, in);
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(miss, in);
        if (tickCap > 0 && ctx.scenario.numTicks > tickCap) return NodeOutcome.of(miss, in);
        if (JumpLinearModel.hasFacingWall(ctx.spec.constraints)) return NodeOutcome.of(miss, in);
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec dspec = bnbSpec(ctx);

        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = optimize ? CertifiedBnb.Mode.OPTIMIZE : CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = cap;
        cfg.polishCap = optimize ? 12 : 2;
        cfg.cancel = nodeToken;
        long sub = sec > 0 ? System.nanoTime() + sec * 1_000_000_000L : 0L;
        cfg.deadlineNanos = deadlineNanos > 0 && sub > 0 ? Math.min(deadlineNanos, sub)
                : (deadlineNanos > 0 ? deadlineNanos : sub);
        if (in != null && in.yaws != null) {
            cfg.seedYaws = in.yaws;
            cfg.seedPx = ctx.scenario.startPos.x;
            cfg.seedPz = ctx.scenario.startPos.z;
        }
        if (optimize) {
            cfg.gapSink = (incObj, boundObj, gap) -> {
                if (!Double.isNaN(gap)) ctx.setDualGap(gap);
            };
        }
        CertifiedBnb.Result res = CertifiedBnb.solve(exact, dspec, cfg);
        if (SolverTrace.on()) {
            SolverTrace.log("CERT", "done declined=%s feasible=%s obj=%s bound=%s gap=%s certified=%s nodes=%d kernelMs=%d",
                    res.declined, res.feasible,
                    res.feasible ? String.format(java.util.Locale.ROOT, "%.9f", res.objective) : "-",
                    Double.isNaN(res.boundObjective) ? "-"
                            : String.format(java.util.Locale.ROOT, "%.9f", res.boundObjective),
                    Double.isNaN(res.gap) ? "-" : String.format(java.util.Locale.ROOT, "%.3e", res.gap),
                    res.certified, res.nodes, res.kernelNanos / 1_000_000L);
        }
        if (res.declined) return NodeOutcome.of(miss, in);
        if (optimize && res.feasible && !Double.isNaN(res.gap)) ctx.setDualGap(res.gap);
        if (!res.feasible) {
            if (res.bestInfeasYaws != null) ctx.closestMiss().offer(res.bestInfeasYaws, res.bestInfeasViol);
            return NodeOutcome.of(miss, in);
        }
        double obj = Scoring.verifiedObjectiveAt(ctx.model, ctx.scenario, ctx.spec, res.yawsDeg, res.px, res.pz,
                ctx.feasTol);
        if (Double.isNaN(obj)) return NodeOutcome.of(miss, in);
        boolean max = ctx.maximize();
        if (in != null && in.yaws != null) {
            double cur = ctx.exactObjective(in.yaws);
            boolean better = max ? obj > cur : obj < cur;
            if (!better && (optimize || in.feasible)) return NodeOutcome.of(miss, in);
        }
        Scoring.adoptPinnedStart(ctx.scenario, res.px, res.pz);
        ctx.chainAppend("certified B&B" + (labelSuffix == null ? "" : labelSuffix));
        Candidate out = Candidate.of(ctx, res.yawsDeg);
        return NodeOutcome.of(optimize ? Guarantee.IMPROVED : Guarantee.FOUND, out);
    }

    private JumpSpec bnbSpec(GraphContext ctx) {
        JumpPhysicsInputs sc = ctx.scenario.copy();
        if (ctx.freeStart && ctx.freeBox != null && !ctx.stageLocked()) {
            sc.startBox = ctx.freeBox;
        }
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(ctx.spec.constraints);
        return new JumpSpec(sc, cons, ctx.spec.objective);
    }

}
