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
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.anglesolver.solver.WrapWindowIls;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WrapIlsNode implements NodeRuntime {

    private final int minRemainingSec;

    public WrapIlsNode(ParamValues params) {
        this.minRemainingSec = params.getInt("minRemainingSec");
    }

    @Override
    public NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos) {
        if (!ctx.exact() || in == null || in.yaws == null) return NodeOutcome.of(Guarantee.REJECTED, in);
        long remaining = deadlineNanos > 0 ? deadlineNanos - System.nanoTime() : 0L;
        if (remaining <= minRemainingSec * 1_000_000_000L) return NodeOutcome.of(Guarantee.REJECTED, in);
        JumpPhysicsInputs sc = ctx.scenario;
        double incViol = ctx.scoredViol(in.yaws);
        if (ctx.progress != null) ctx.progress.setStage(ctx.chainWith("wrap ILS"));
        if (SolverTrace.on()) SolverTrace.log("ENGINE", "wrap ils start incViol=%.3e", incViol);
        double[] gfInc = sc.toGameFacings(Angles.wrapAll(in.yaws));
        double[] dom = ctx.freeBox != null ? Scoring.translationDomain(sc, ctx.freeBox)
                : new double[] {0.0, 0.0, 0.0, 0.0};
        WrapWindowIls.Config wcfg = new WrapWindowIls.Config();
        if (ctx.legalGoal != null) {
            wcfg.legalObjective = ctx.spec.objective;
            wcfg.legalGoalRhs = ctx.legalGoal.rhs;
        }
        double incScore = ctx.legalGoal != null
                ? WrapWindowIls.scoreOf(ctx.exactModel, ctx.spec, gfInc, dom, wcfg)
                : incViol;
        WrapWindowIls.Result w = WrapWindowIls.polish(ctx.exactModel, ctx.spec, gfInc, dom,
                wcfg, deadlineNanos, nodeToken);
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "wrap ils end score=%.3e evals=%d rounds=%d",
                    w != null ? w.viol : Double.NaN, w != null ? w.evals : 0, w != null ? w.rounds : 0);
        }
        boolean adopt = w != null
                && (ctx.legalGoal != null
                        ? w.viol < incScore && w.viol < WrapWindowIls.LEGAL_HARD_INFEASIBLE
                        : w.viol <= ctx.feasTol)
                && Scoring.adoptStageResult(ctx.model, sc, ctx.spec, ctx.freeBox, w.gf, ctx.feasTol);
        if (adopt) {
            double[] yaws = w.gf.clone();
            boolean[] lockAll = new boolean[sc.numTicks];
            Arrays.fill(lockAll, true);
            sc.yawLockedPerTick = lockAll;
            ctx.setStageLocked(true);
            ctx.chainAppend("wrap ILS");
            return NodeOutcome.of(Guarantee.ADOPTED, Candidate.of(ctx, yaws));
        }
        return NodeOutcome.of(Guarantee.REJECTED, in);
    }
}
