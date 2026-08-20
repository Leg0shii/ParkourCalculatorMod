package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphValidator;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinGraphsTest {

    @Test
    public void fastHasRescueButNoExhaustiveOrWrapStages() {
        SolverGraph g = BuiltinGraphs.fast();
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNotNull(g.node("rescueBnb"));
        assertNull(g.node("sweep"));
        assertNull(g.node("ils"));
        assertNull(g.node("wrap"));
        assertNull(g.node("nearBnb"));
        assertEquals("FIRST_FEASIBLE", g.node("rescueBnb").params.getString("mode"));
        assertEquals(3, g.node("rescueBnb").params.getInt("budgetSec"));
        assertNull(g.node("raceColdFull"));
        assertEquals(240, g.node("momentum").params.getInt("budgetSec"));
        assertEquals(20, g.node("freeImprove").params.getInt("budgetSec"));
    }

    @Test
    public void optimizeHasNoRaceAndKeepsTheExhaustiveBlock() {
        SolverGraph g = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNull(g.node("raceColdFull"));
        assertNull(g.node("raceColdThrottled"));
        assertNull(g.node("raceWarm"));
        assertNull(g.node("rescueBnb"));
        assertNotNull(g.node("sweep"));
        assertNotNull(g.node("nearBnb"));
        assertEquals(1000, g.node("nearBnb").params.getInt("minBudgetMs"));
        assertNotNull(g.node("coldBnb"));
        assertEquals("FIRST_FEASIBLE", g.node("coldBnb").params.getString("mode"));
        assertEquals("coldBnb", g.edgeFor("rHave", Guarantee.FALSE).toNode);
        assertEquals("cap2", g.edgeFor("rColdHave", Guarantee.TRUE).toNode);
    }

    @Test
    public void optimizeWrapAdoptedGoesStraightToEmit() {
        SolverGraph g = BuiltinGraphs.optimize(30);
        GraphEdge adopted = g.edgeFor("wrap", Guarantee.ADOPTED);
        assertNotNull(adopted);
        assertEquals("emit", adopted.toNode);
        GraphEdge rejected = g.edgeFor("wrap", Guarantee.REJECTED);
        assertEquals("rTrans", rejected.toNode);
    }

    @Test
    public void optimizeExhaustiveBudgetsSplitTheWindow() {
        SolverGraph g = BuiltinGraphs.optimize(120);
        assertEquals(24, g.node("sweep").params.getInt("budgetSec"));
        assertEquals(72, g.node("bnbOpt").params.getInt("budgetSec"));
        assertEquals(24, g.node("ils").params.getInt("budgetSec"));
        assertEquals(60, g.node("nearBnb").params.getInt("budgetSec"));
    }

    @Test
    public void customWithStopOnFeasibleAndExhaustiveKeepsWrapButNoExhaustiveBlock() {
        SolverGraph g = BuiltinGraphs.fromBudget(true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNotNull(g.node("rescueBnb"));
        assertNotNull(g.node("wrap"));
        assertNull(g.node("sweep"));
        assertNull(g.node("coldBnb"));
        assertNull(g.node("raceColdFull"));
    }

    @Test
    public void customWithoutWindowSolverHasNoHorizonOrPeel() {
        SolverGraph g = BuiltinGraphs.fromBudget(false, false, false, 10, 3, 0);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNull(g.node("horizon"));
        assertNull(g.node("peel"));
        assertEquals("seedMulti", g.edgeFor("rJumps", Guarantee.FALSE).toNode);
        assertEquals("repA", g.edgeFor("seedMulti", Guarantee.NONE).toNode);
    }

    @Test
    public void multiJumpSettledPathSkipsTheRace() {
        SolverGraph g = BuiltinGraphs.fast();
        assertEquals("settledMark", g.edgeFor("rWarmTicks", Guarantee.FALSE).toNode);
        assertEquals("repSkip", g.edgeFor("settledMark", Guarantee.DONE).toNode);
        assertEquals("rMomGate", g.edgeFor("repSkip", Guarantee.DONE).toNode);
    }
}
