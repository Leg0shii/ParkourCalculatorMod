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
import static org.junit.Assert.assertTrue;

public class BuiltinGraphsTest {

    private static final List<String> PIPELINE = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "sweep", "ils2", "translate2", "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap",
            "translate", "snap", "emit");

    private static final List<String> LINEAR_PAIRS_FROM = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "ils2", "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "snap");

    private static List<String> nodeIds(SolverGraph g) {
        List<String> ids = new ArrayList<String>();
        for (GraphNode n : g.nodes) ids.add(n.id);
        return ids;
    }

    private static void assertLinearExceptLoop(SolverGraph g) {
        for (String from : LINEAR_PAIRS_FROM) {
            String to = PIPELINE.get(PIPELINE.indexOf(from) + 1);
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
    public void fastAndOptimizeShareTheSamePipelineShape() {
        SolverGraph fast = BuiltinGraphs.fast();
        SolverGraph opt = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(fast)));
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(opt)));
        assertEquals(PIPELINE, nodeIds(fast));
        assertEquals(PIPELINE, nodeIds(opt));
        assertLinearExceptLoop(fast);
        assertLinearExceptLoop(opt);
        assertLoopEdges(fast, false);
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
        assertEquals(0, fast.node("snap").params.getInt("pairPass"));
        assertEquals(1, opt.node("snap").params.getInt("pairPass"));
        assertEquals(0, fast.node("sweep").params.getInt("budgetSec"));
        assertTrue(opt.node("sweep").params.getInt("budgetSec") > 0);
        assertEquals(0, fast.node("ils2").params.getInt("budgetSec"));
        assertTrue(opt.node("ils2").params.getInt("budgetSec") > 0);
    }

    @Test
    public void customFollowsTheSameShape() {
        SolverGraph g = BuiltinGraphs.fromBudget(true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertEquals(PIPELINE, nodeIds(g));
        assertLinearExceptLoop(g);
        assertLoopEdges(g, true);
    }
}
