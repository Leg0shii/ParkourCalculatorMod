package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SearchGraphCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.WorkDeadline;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CascadeCheck implements FastCheck {

    public static final long SWEEP_CAP_NANOS = 400_000_000L;
    public static final long MIN_SEARCH_NANOS = 200_000_000L;

    private final ThetaSweepAirSlp sweep = new ThetaSweepAirSlp();
    private final ScreenWitnessWarmStart warm = new ScreenWitnessWarmStart();
    private final SearchGraphCheck search = new SearchGraphCheck();

    @Override
    public void prepare(NoTurnProblem problem) {
        warm.prepare(problem);
    }

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, budgetNanos);
        if (!StructurePoolDriver.multiTied(problem)) {
            FastCheckVerdict vs = sweep.check(problem, spec, model, Math.min(SWEEP_CAP_NANOS, budgetNanos / 4), cancel);
            if (vs.kind == FastCheckVerdict.Kind.FEASIBLE) return vs;
            if (cancel != null && cancel.get()) return FastCheckVerdict.unknown("cancelled");
        }
        long rem = deadline.remainingNanos();
        if (rem <= 0) return FastCheckVerdict.unknown("budget after sweep");
        FastCheckVerdict v = warm.check(problem, spec, model, rem, cancel);
        if (v.kind == FastCheckVerdict.Kind.FEASIBLE) return v;
        if (cancel != null && cancel.get()) return FastCheckVerdict.unknown("cancelled");
        rem = deadline.remainingNanos();
        if (rem < MIN_SEARCH_NANOS) return FastCheckVerdict.unknown("budget after screenWarm");
        return search.check(problem, spec, model, rem, cancel);
    }

    @Override
    public String describe() {
        return "CascadeCheck(sweep,screenWarm,search)";
    }
}
