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
import static org.junit.Assert.assertTrue;

public class BuiltinGraphsTest {

    private static final List<String> PIPELINE = Arrays.asList(
            "entry", "horizon", "wrap0", "seed", "cap1", "freeRescue", "peel", "freeImprove",
            "fold", "ladder", "cert", "bnb", "ils", "cap2", "wrap", "translate", "snap", "emit");

    private static List<String> nodeIds(SolverGraph g) {
        List<String> ids = new ArrayList<String>();
        for (GraphNode n : g.nodes) ids.add(n.id);
        return ids;
    }

    private static void assertLinear(SolverGraph g) {
        for (int i = 0; i < PIPELINE.size() - 1; i++) {
            String from = PIPELINE.get(i);
            String to = PIPELINE.get(i + 1);
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
    public void fastAndOptimizeShareTheSamePipelineShape() {
        SolverGraph fast = BuiltinGraphs.fast();
        SolverGraph opt = BuiltinGraphs.optimize(50);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(fast)));
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(opt)));
        assertEquals(PIPELINE, nodeIds(fast));
        assertEquals(PIPELINE, nodeIds(opt));
        assertLinear(fast);
        assertLinear(opt);
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
    }

    @Test
    public void customFollowsTheSameShape() {
        SolverGraph g = BuiltinGraphs.fromBudget(true, true, true, 10, 3, 60);
        assertFalse(GraphValidator.hasErrors(GraphValidator.validate(g)));
        assertEquals(PIPELINE, nodeIds(g));
        assertLinear(g);
    }
}
