package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinGraphsTest {

    private static final List<String> FAST_NODES = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "sweep", "ils2", "translate2", "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap",
            "translate", "emit");

    private static final List<String> OPTIMIZE_NODES = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "sweep", "ils2", "translate2", "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap",
            "translate", "snap", "emit");

    private static final String[][] FAST_PAIRS = {
            {"entry", "seed"}, {"seed", "horizon"}, {"horizon", "wrap0"}, {"wrap0", "cap1"},
            {"cap1", "freeRescue"}, {"freeRescue", "peel"}, {"peel", "freeImprove"}, {"freeImprove", "sweep"},
            {"ils2", "translate2"}, {"fold", "ladder"}, {"ladder", "cert"}, {"cert", "bnb"}, {"bnb", "ils"},
            {"ils", "cap2"}, {"cap2", "wrap"}, {"wrap", "translate"}, {"translate", "emit"}};

    private static final String[][] OPTIMIZE_PAIRS = {
            {"entry", "horizon"}, {"horizon", "wrap0"}, {"wrap0", "seed"}, {"seed", "cap1"},
            {"cap1", "freeRescue"}, {"freeRescue", "peel"}, {"peel", "freeImprove"}, {"freeImprove", "sweep"},
            {"ils2", "translate2"}, {"fold", "ladder"}, {"ladder", "cert"}, {"cert", "bnb"}, {"bnb", "ils"},
            {"ils", "cap2"}, {"cap2", "wrap"}, {"wrap", "translate"}, {"translate", "snap"}, {"snap", "emit"}};

    private static List<String> nodeIds(SolverGraph g) {
        List<String> ids = new ArrayList<String>();
        for (GraphNode n : g.nodes) ids.add(n.id);
        return ids;
    }

    private static void assertLinear(SolverGraph g, String[][] pairs) {
        for (String[] pair : pairs) {
            String from = pair[0];
            String to = pair[1];
            int wired = 0;
            for (Guarantee br : Guarantee.values()) {
                GraphEdge e = g.edgeFor(from, br);
                if (e == null) continue;
                wired++;
                assertEquals(from + " branch " + br, to, e.toNode);
            }
            assertTrue(from + " has no outgoing edges", wired > 0);
        }
    }

    private static void assertLoopEdges(SolverGraph g, boolean looping) {
        assertNotNull("sweep TRUE must continue into ils2", g.edgeFor("sweep", Guarantee.TRUE));
        assertEquals("ils2", g.edgeFor("sweep", Guarantee.TRUE).toNode);
        for (Guarantee br : new Guarantee[]{Guarantee.FOUND, Guarantee.IMPROVED, Guarantee.UNCHANGED, Guarantee.NONE}) {
            assertNotNull("sweep " + br + " must exit to fold", g.edgeFor("sweep", br));
            assertEquals("sweep " + br, "fold", g.edgeFor("sweep", br).toNode);
        }
        assertEquals("ils2", "translate2", g.edgeFor("ils2", Guarantee.IMPROVED).toNode);
        assertEquals("ils2", "translate2", g.edgeFor("ils2", Guarantee.UNCHANGED).toNode);
        assertNotNull(g.edgeFor("translate2", Guarantee.DONE));
        assertEquals("translate2 back edge only when the tier runs the loop",
                looping ? "sweep" : "fold", g.edgeFor("translate2", Guarantee.DONE).toNode);
    }

    @Test
    public void fastIsSeedFirstWithoutLeafSnap() {
        SolverGraph fast = BuiltinGraphs.fast();
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(fast)));
        assertEquals(FAST_NODES, nodeIds(fast));
        assertLinear(fast, FAST_PAIRS);
        assertLoopEdges(fast, false);
        assertNull("fast has no leaf snap stage", fast.node("snap"));
    }

    @Test
    public void optimizeIsHorizonFirstWithLeafSnap() {
        SolverGraph opt = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(opt)));
        assertEquals(OPTIMIZE_NODES, nodeIds(opt));
        assertLinear(opt, OPTIMIZE_PAIRS);
        assertLoopEdges(opt, true);
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
        assertEquals(0, fast.node("sweep").params.getInt("budgetSec"));
        assertTrue(opt.node("sweep").params.getInt("budgetSec") > 0);
        assertEquals(0, fast.node("ils2").params.getInt("budgetSec"));
        assertTrue(opt.node("ils2").params.getInt("budgetSec") > 0);
    }

    @Test
    public void fastRunTicksIsSeedFirstAndUncapped() {
        SolverGraph rt = BuiltinGraphs.fastRunTicks();
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(rt)));
        assertEquals(FAST_NODES, nodeIds(rt));
        assertLinear(rt, FAST_PAIRS);
        assertLoopEdges(rt, false);
        assertEquals(0, rt.node("seed").params.getInt("budgetMs"));
    }

    @Test
    public void customFollowsTheOptimizeShape() {
        SolverGraph g = BuiltinGraphs.fromBudget(true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertEquals(OPTIMIZE_NODES, nodeIds(g));
        assertLinear(g, OPTIMIZE_PAIRS);
        assertLoopEdges(g, true);
    }
}
