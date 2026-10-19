package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SearchGraphCheck;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CascadeCheck implements FastCheck {

    public static final long SWEEP_CAP_NANOS = 400_000_000L;
    public static final long WARM_CAP_NANOS = 300_000_000L;
    public static final long MIN_SEARCH_NANOS = 200_000_000L;

    private final ThetaSweepAirSlp sweep;
    private final SlpWarmStartDiskTheta warm = new SlpWarmStartDiskTheta();
    private final SearchGraphCheck search = new SearchGraphCheck();

    public CascadeCheck() {
        this(false);
    }

    public CascadeCheck(boolean playable) {
        this.sweep = new ThetaSweepAirSlp(playable);
    }

    @Override
    public void prepare(NoTurnProblem problem) {
        warm.prepare(problem);
    }

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        long deadline = System.nanoTime() + budgetNanos;
        FastCheckVerdict v = sweep.check(problem, spec, model, Math.min(SWEEP_CAP_NANOS, budgetNanos / 4), cancel);
        if (v.kind == FastCheckVerdict.Kind.FEASIBLE) return v;
        if (cancel != null && cancel.get()) return FastCheckVerdict.unknown("cancelled");
        long rem = deadline - System.nanoTime();
        if (rem <= 0) return FastCheckVerdict.unknown("budget after sweep");
        v = warm.check(problem, spec, model, Math.min(WARM_CAP_NANOS, rem / 2), cancel);
        if (v.kind == FastCheckVerdict.Kind.FEASIBLE) return v;
        if (cancel != null && cancel.get()) return FastCheckVerdict.unknown("cancelled");
        rem = deadline - System.nanoTime();
        if (rem < MIN_SEARCH_NANOS) return FastCheckVerdict.unknown("budget after warm");
        return search.check(problem, spec, model, rem, cancel);
    }

    @Override
    public String describe() {
        return "CascadeCheck(sweep,warm,search)";
    }
}
