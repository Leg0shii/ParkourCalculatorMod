package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphValidator;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.graph.ValidationIssue;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraphValidatorTest {

    private static boolean hasError(List<ValidationIssue> issues, String fragment) {
        for (ValidationIssue i : issues) {
            if (i.severity == ValidationIssue.Severity.ERROR && i.message.contains(fragment)) return true;
        }
        return false;
    }

    private static boolean hasWarn(List<ValidationIssue> issues, String fragment) {
        for (ValidationIssue i : issues) {
            if (i.severity == ValidationIssue.Severity.WARN && i.message.contains(fragment)) return true;
        }
        return false;
    }

    @Test
    public void builtinGraphsHaveNoErrors() {
        for (SolverGraph g : new SolverGraph[] {
                BuiltinGraphs.fast(),
                BuiltinGraphs.optimize(10),
                BuiltinGraphs.optimize(600),
                BuiltinGraphs.fromBudget(true, true, true, 10, 3, 0),
                BuiltinGraphs.fromBudget(false, false, false, 8, 2, 30)}) {
            List<ValidationIssue> issues = GraphValidator.validate(g);
            assertFalse(g.name + ": " + issues, GraphValidator.hasErrors(issues));
        }
    }

    @Test
    public void missingEmitIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "exactly one Emit"));
    }

    @Test
    public void duplicateEntryIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("entry2", "entry").add("emit", "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "exactly one Entry"));
    }

    @Test
    public void undeclaredBranchIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit").add("s", "markSettled");
        g.edge("entry", Guarantee.DONE, "s");
        g.edge("s", Guarantee.FOUND, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "has no branch FOUND"));
    }

    @Test
    public void doubleWiredBranchIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit").add("s", "markSettled");
        g.edge("entry", Guarantee.DONE, "s");
        g.edge("s", Guarantee.DONE, "emit");
        g.edge("s", Guarantee.DONE, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "wired more than once"));
    }

    @Test
    public void edgeIntoEntryIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit").add("s", "markSettled");
        g.edge("entry", Guarantee.DONE, "s");
        g.edge("s", Guarantee.DONE, "entry");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "edge into Entry"));
    }

    @Test
    public void unwiredBranchWarnsFallThrough() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit").add("d", "dualChain");
        g.edge("entry", Guarantee.DONE, "d");
        g.edge("d", Guarantee.FOUND, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasWarn(issues, "falls through to Emit"));
    }

    @Test
    public void unreachableNodeWarns() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit").add("s", "markSettled");
        g.edge("entry", Guarantee.DONE, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasWarn(issues, "unreachable"));
    }

    @Test
    public void feasibleRequiresRejectsUnprovenSource() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit");
        g.add("race", "freeStartImprove");
        g.add("ils", "ilsPolish");
        g.edge("entry", Guarantee.DONE, "race");
        g.edge("race", Guarantee.IMPROVED, "ils");
        g.edge("ils", Guarantee.IMPROVED, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "requires a FEASIBLE candidate"));
    }

    @Test
    public void feasibleRequiresAcceptsFeasibilityRouterTrue() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit");
        g.add("race", "freeStartImprove");
        g.add("r", "router");
        g.set("r", "predicate", "CANDIDATE_FEASIBLE_RAW");
        g.add("ils", "ilsPolish");
        g.edge("entry", Guarantee.DONE, "race");
        g.edge("race", Guarantee.IMPROVED, "r");
        g.edge("r", Guarantee.TRUE, "ils");
        g.edge("ils", Guarantee.IMPROVED, "emit");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertFalse(issues.toString(), hasError(issues, "requires a FEASIBLE candidate"));
    }

    @Test
    public void unguardedCycleIsAnError() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit");
        g.add("a", "router").add("b", "router");
        g.edge("entry", Guarantee.DONE, "a");
        g.edge("a", Guarantee.TRUE, "b");
        g.edge("b", Guarantee.TRUE, "a");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertTrue(hasError(issues, "unguarded loop"));
    }

    @Test
    public void budgetGuardedCycleIsAccepted() {
        GraphBuilder g = new GraphBuilder("t", false);
        g.add("entry", "entry").add("emit", "emit");
        g.add("a", "router");
        g.add("p", "ilsPolish");
        g.set("p", "budgetSec", 5);
        g.edge("entry", Guarantee.DONE, "a");
        g.edge("a", Guarantee.TRUE, "p");
        g.edge("p", Guarantee.IMPROVED, "a");
        List<ValidationIssue> issues = GraphValidator.validate(g.build());
        assertFalse(issues.toString(), hasError(issues, "unguarded loop"));
    }
}
