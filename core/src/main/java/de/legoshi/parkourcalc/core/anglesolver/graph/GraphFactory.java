package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;

public final class GraphFactory {

    private GraphFactory() {
    }

    public static SolverGraph forState(AngleSolverState state) {
        switch (state.getEffort()) {
            case THOROUGH:
                return BuiltinGraphs.optimize(state.getOptimizeSeconds());
            case CUSTOM: {
                AngleSolverState.SolveBudget b = state.getSolveBudget();
                return BuiltinGraphs.fromBudget(b.getRestarts(), b.getMaxEval(), b.getPolishCount(),
                        b.getPolishDepth() == AngleSolverState.PolishDepth.EXHAUSTIVE,
                        state.isStopOnFeasible(), b.isIlsExhaustive(), b.getUseWindowSolver(),
                        b.getWindow(), b.getCommit(), b.getTimeBudgetSeconds());
            }
            default:
                return BuiltinGraphs.fast();
        }
    }
}
