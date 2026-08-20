package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphFactory;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class LegacyCustomMappingTest {

    private static AngleSolverState custom(int restarts, int maxEval, int polishCount,
                                           AngleSolverState.PolishDepth depth, boolean stopOnFeasible,
                                           boolean ilsExhaustive, boolean useWindowSolver,
                                           int window, int commit, int timeBudgetSeconds) {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.setStopOnFeasible(stopOnFeasible);
        AngleSolverState.SolveBudget b = s.getSolveBudget();
        b.setRestarts(restarts);
        b.setMaxEval(maxEval);
        b.setPolishCount(polishCount);
        b.setPolishDepth(depth);
        b.setIlsExhaustive(ilsExhaustive);
        b.setUseWindowSolver(useWindowSolver);
        b.setWindow(window);
        b.setCommit(commit);
        b.setTimeBudgetSeconds(timeBudgetSeconds);
        return s;
    }

    private static void assertSameShape(SolverGraph expected, SolverGraph actual) {
        assertEquals(GraphPresetIO.toJson(GraphPresetIO.fromGraph(expected)),
                GraphPresetIO.toJson(GraphPresetIO.fromGraph(actual)));
    }

    @Test
    public void defaultKnobsMapToTheDefaultFromBudgetGraph() {
        AngleSolverState s = custom(16, 4500, 2, AngleSolverState.PolishDepth.LIGHT, false, false, true, 10, 3, 0);
        assertSameShape(BuiltinGraphs.fromBudget(false, false, false, true, 10, 3, 0),
                GraphFactory.forState(s));
    }

    @Test
    public void stopOnFeasibleMapsToTheRescueGraph() {
        AngleSolverState s = custom(16, 4500, 2, AngleSolverState.PolishDepth.LIGHT, true, false, true, 10, 3, 0);
        SolverGraph g = GraphFactory.forState(s);
        assertSameShape(BuiltinGraphs.fromBudget(false, true, false, true, 10, 3, 0), g);
        assertNotNull(g.node("rescueBnb"));
    }

    @Test
    public void exhaustiveKnobsMapToTheExhaustiveGraph() {
        AngleSolverState s = custom(32, 9000, 4, AngleSolverState.PolishDepth.EXHAUSTIVE, false, true, true, 12, 4, 60);
        SolverGraph g = GraphFactory.forState(s);
        assertSameShape(BuiltinGraphs.fromBudget(true, false, true, true, 12, 4, 60), g);
        assertEquals(60, g.node("seedSingle").params.getInt("budgetSec"));
        assertEquals(12, g.node("horizon").params.getInt("window"));
        assertEquals(4, g.node("horizon").params.getInt("commit"));
        assertEquals(12, g.node("peel").params.getInt("window"));
        assertNotNull(g.node("sweep"));
        assertNotNull(g.node("wrap"));
    }

    @Test
    public void windowSolverOffDropsHorizonAndPeel() {
        AngleSolverState s = custom(16, 4500, 2, AngleSolverState.PolishDepth.LIGHT, false, false, false, 10, 3, 0);
        SolverGraph g = GraphFactory.forState(s);
        assertSameShape(BuiltinGraphs.fromBudget(false, false, false, false, 10, 3, 0), g);
        assertNull(g.node("horizon"));
        assertNull(g.node("peel"));
    }

    @Test
    public void customGraphTakesPrecedenceOverTheKnobs() {
        AngleSolverState s = custom(16, 4500, 2, AngleSolverState.PolishDepth.LIGHT, false, false, true, 10, 3, 0);
        SolverGraph user = BuiltinGraphs.optimize(30);
        s.setCustomGraph(user);
        assertSame(user, GraphFactory.forState(s));
    }

    @Test
    public void missingPresetFallsBackToTheLegacyMapping() {
        AngleSolverState s = custom(24, 6000, 3, AngleSolverState.PolishDepth.LIGHT, false, false, true, 10, 3, 0);
        s.setGraphPresetName("no-such-preset");
        assertSameShape(BuiltinGraphs.fromBudget(false, false, false, true, 10, 3, 0),
                GraphFactory.forState(s));
    }

    @Test
    public void resetClearsThePresetSelection() {
        AngleSolverState s = new AngleSolverState();
        s.setGraphPresetName("something");
        s.setCustomGraph(BuiltinGraphs.fast());
        s.reset();
        assertNull(s.getGraphPresetName());
        assertNull(s.getCustomGraph());
    }
}
