package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.concurrent.atomic.AtomicBoolean;

public interface FastCheck {

    FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                           AtomicBoolean cancel);

    default void prepare(NoTurnProblem problem) {
    }

    default String describe() {
        return getClass().getSimpleName();
    }
}
