package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphValidator;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinGraphsTest {

    private static final List<String> FAST_NODES = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "emit");

    private static final List<String> FAST_CHAIN = Arrays.asList(
            "entry", "seed", "horizon", "wrap0", "cap1", "freeRescue", "peel", "freeImprove",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "emit");

    private static final List<String> OPTIMIZE_NODES = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "snap", "emit");

    private static final List<String> OPTIMIZE_CHAIN = OPTIMIZE_NODES;

    private static List<String> nodeIds(SolverGraph g) {
        List<String> ids = new ArrayList<String>();
        for (GraphNode n : g.nodes) ids.add(n.id);
        return ids;
    }

    private static void assertLinear(SolverGraph g, List<String> chain) {
        for (int i = 0; i < chain.size() - 1; i++) {
            String from = chain.get(i);
            String to = chain.get(i + 1);
            int wired = 0;
            for (Guarantee br : Guarantee.values()) {
                if (g.edgeFor(from, br) == null) continue;
                wired++;
                assertEquals(from + " branch " + br, to, g.edgeFor(from, br).toNode);
            }
            assertTrue(from + " has no outgoing edges", wired > 0);
        }
    }

    @Test
    public void fastIsSeedFirstWithoutLeafSnap() {
        SolverGraph fast = BuiltinGraphs.fast();
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(fast)));
        assertEquals(FAST_NODES, nodeIds(fast));
        assertLinear(fast, FAST_CHAIN);
        assertNull("fast has no leaf snap stage", fast.node("snap"));
    }

    @Test
    public void optimizeIsHorizonFirstWithLeafSnap() {
        SolverGraph opt = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(opt)));
        assertEquals(OPTIMIZE_NODES, nodeIds(opt));
        assertLinear(opt, OPTIMIZE_CHAIN);
    }

    @Test
    public void tiersDifferOnlyInBudgetsAndCaps() {
        SolverGraph fast = BuiltinGraphs.fast();
        SolverGraph opt = BuiltinGraphs.optimize(120);
        assertEquals(0, fast.node("fold").params.getInt("objectiveRounds"));
        assertTrue(opt.node("fold").params.getInt("objectiveRounds") > 0);
        assertEquals(0, fast.node("cert").params.getInt("budgetSec"));
        assertTrue(opt.node("cert").params.getInt("budgetSec") > 0);
        assertEquals(32, fast.node("cert").params.getInt("ffNodeCap"));
        assertEquals(0, fast.node("wrap").params.getInt("budgetSec"));
        assertTrue(opt.node("wrap").params.getInt("budgetSec") > 0);
        assertEquals(0, fast.node("seed").params.getInt("warmSec"));
        assertEquals(1, opt.node("seed").params.getInt("warmSec"));
        assertEquals(BuiltinGraphs.FAST_SEED_CAP_MS, fast.node("seed").params.getInt("budgetMs"));
        assertEquals(0, opt.node("seed").params.getInt("budgetMs"));
        assertNull("fast has no leaf snap stage", fast.node("snap"));
        assertEquals(1, opt.node("snap").params.getInt("pairPass"));
    }

    @Test
    public void fastRunTicksIsSeedFirstAndUncapped() {
        SolverGraph rt = BuiltinGraphs.fastRunTicks();
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(rt)));
        assertEquals(FAST_NODES, nodeIds(rt));
        assertLinear(rt, FAST_CHAIN);
        assertEquals(0, rt.node("seed").params.getInt("budgetMs"));
    }

    @Test
    public void customFollowsTheOptimizeShape() {
        SolverGraph g = BuiltinGraphs.fromBudget(true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertEquals(OPTIMIZE_NODES, nodeIds(g));
        assertLinear(g, OPTIMIZE_CHAIN);
    }
}
