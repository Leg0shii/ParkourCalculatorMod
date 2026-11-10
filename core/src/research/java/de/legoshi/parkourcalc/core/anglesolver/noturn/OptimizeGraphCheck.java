package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OptimizeGraphCheck implements FastCheck {

    private final SolverGraph graph = BuiltinGraphs.optimize(6);

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        NoTurnCertifier.Result r = new NoTurnCertifier(model).certify(spec, graph, budgetNanos, cancel);
        if (r != null && r.feasible) return FastCheckVerdict.feasible(r.yaws, r.startX, r.startZ, "optimize6");
        return FastCheckVerdict.unknown("optimize6 miss");
    }
}
