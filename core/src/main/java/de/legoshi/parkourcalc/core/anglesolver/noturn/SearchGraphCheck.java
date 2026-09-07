package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SearchGraphCheck implements FastCheck {

    private SolverGraph graph;
    private long graphBudget = -1L;

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        if (graph == null || graphBudget != budgetNanos) {
            graph = NoTurnCertifier.searchGraph(budgetNanos);
            graphBudget = budgetNanos;
        }
        NoTurnCertifier.Result r = new NoTurnCertifier(model).certify(spec, graph, budgetNanos, cancel);
        if (r != null && r.feasible) return FastCheckVerdict.feasible(r.yaws, r.startX, r.startZ, "searchGraph");
        return FastCheckVerdict.unknown("searchGraph miss");
    }
}
