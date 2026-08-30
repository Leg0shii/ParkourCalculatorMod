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

    private static final int OPT_SAFETY_NODE_CAP = 500_000;
    private static final long FOLD_FF_NANOS = 30_000_000_000L;
    private static final long FOLD_TAIL_NANOS = 1_500_000_000L;

    private final int ffSec;
    private final int ffNodeCap;
    private final int tickCap;
    private final String labelSuffix;

    public CertifiedBnbNode(ParamValues params) {
        this.ffSec = params.getInt("ffSec");
        this.ffNodeCap = params.getInt("ffNodeCap");
        this.tickCap = params.getInt("tickCap");
        this.labelSuffix = params.getString("labelSuffix");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        boolean optimize = in != null && in.yaws != null && in.feasible;
        Guarantee miss = in != null && in.yaws != null ? Guarantee.UNCHANGED : Guarantee.NONE;
        long now = System.nanoTime();
        int cap;
        long solveDeadline;
        AtomicBoolean cancelToken = nodeToken;
        if (optimize) {
            if (deadlineNanos <= 0 || now >= deadlineNanos) return NodeOutcome.of(miss, in);
            cap = OPT_SAFETY_NODE_CAP;
            solveDeadline = deadlineNanos;
        } else {
            if (ffNodeCap <= 0) return NodeOutcome.of(miss, in);
            cap = ffNodeCap;
            long sub = ffSec > 0 ? now + ffSec * 1_000_000_000L : 0L;
            solveDeadline = deadlineNanos > 0 && sub > 0 ? Math.min(deadlineNanos, sub)
                    : (deadlineNanos > 0 ? deadlineNanos : sub);
            long overall = ctx.overallDeadline();
            if (ctx.foldableDf() && overall > 0) {
                long foldDeadline = Math.min(overall - FOLD_TAIL_NANOS, now + FOLD_FF_NANOS);
                if (foldDeadline > solveDeadline) {
                    solveDeadline = foldDeadline;
                    cancelToken = ctx.cancel;
                }
            }
        }
        if (!ctx.exact() || ctx.stageLocked()) return NodeOutcome.of(miss, in);
        if (tickCap > 0 && ctx.scenario.numTicks > tickCap) return NodeOutcome.of(miss, in);
        ExactJumpModel exact = ctx.exactModel;
        JumpSpec dspec = bnbSpec(ctx);

        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = optimize ? CertifiedBnb.Mode.OPTIMIZE : CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = cap;
        cfg.polishCap = optimize ? 12 : 2;
        cfg.cancel = cancelToken;
        cfg.deadlineNanos = solveDeadline;
        cfg.homotopy = ctx.foldableDf() && ctx.overallDeadline() > 0;
        if (in != null && in.yaws != null) {
            cfg.seedYaws = in.yaws;
            cfg.seedPx = ctx.scenario.startPos.x;
            cfg.seedPz = ctx.scenario.startPos.z;
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
        if (optimize && !Double.isNaN(res.gap)) out = out.withDualGap(res.gap);
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
