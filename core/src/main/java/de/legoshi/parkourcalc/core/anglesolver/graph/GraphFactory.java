package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;

public final class GraphFactory {

    private GraphFactory() {
    }

    public static SolverGraph forState(AngleSolverState state) {
        return forState(state, state.getEffort());
    }

    public static SolverGraph forState(AngleSolverState state, AngleSolverState.Effort effort) {
        switch (effort) {
            case THOROUGH:
                return BuiltinGraphs.optimize(state.getOptimizeSeconds());
            case CUSTOM: {
                String preset = state.getGraphPresetName();
                if (BuiltinGraphs.FAST_PRESET.equals(preset)) return BuiltinGraphs.fast();
                if (BuiltinGraphs.OPTIMIZE_PRESET.equals(preset)) return BuiltinGraphs.optimize(state.getOptimizeSeconds());
                SolverGraph user = state.getCustomGraph();
                return user != null ? user : BuiltinGraphs.optimize(state.getOptimizeSeconds());
            }
            default:
                return BuiltinGraphs.fast();
        }
    }
}
