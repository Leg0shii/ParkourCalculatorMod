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
        assertEquals(0, g.node("raceColdFull").params.getInt("budgetSec"));
        assertEquals(240, g.node("momentum").params.getInt("budgetSec"));
        assertEquals(20, g.node("freeImprove").params.getInt("budgetSec"));
    }

    @Test
    public void optimizeThrottlesTheRaceToTwoFifths() {
        SolverGraph g = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNotNull(g.node("raceColdThrottled"));
        assertEquals(20, g.node("raceColdThrottled").params.getInt("budgetSec"));
        assertEquals(50, g.node("raceColdFull").params.getInt("budgetSec"));
        assertEquals(20, g.node("raceWarm").params.getInt("budgetSec"));
        assertEquals(2, g.node("raceWarm").params.getInt("polishCount"));
        assertEquals(4, g.node("raceColdFull").params.getInt("polishCount"));
        assertNull(g.node("rescueBnb"));
        assertNotNull(g.node("sweep"));
        assertNotNull(g.node("nearBnb"));
        assertEquals(1000, g.node("nearBnb").params.getInt("minBudgetMs"));
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
        SolverGraph g = BuiltinGraphs.fromBudget(32, 9000, 3, true, true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertNotNull(g.node("rescueBnb"));
        assertNotNull(g.node("wrap"));
        assertNull(g.node("sweep"));
        assertNull(g.node("raceColdThrottled"));
        assertEquals(32, g.node("raceColdFull").params.getInt("restarts"));
        assertEquals("EXHAUSTIVE", g.node("raceColdFull").params.getString("polishDepth"));
        assertEquals(16, g.node("raceWarm").params.getInt("restarts"));
        assertEquals("LIGHT", g.node("raceWarm").params.getString("polishDepth"));
    }

    @Test
    public void customWithoutWindowSolverHasNoHorizonOrPeel() {
        SolverGraph g = BuiltinGraphs.fromBudget(16, 4500, 2, false, false, false, false, 10, 3, 0);
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
