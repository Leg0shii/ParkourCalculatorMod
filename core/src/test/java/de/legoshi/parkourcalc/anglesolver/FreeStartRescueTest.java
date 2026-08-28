package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FreeStartRescueTest {

    private static GraphNode nodeById(SolverGraph g, String id) {
        for (GraphNode n : g.nodes) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    @Test
    public void builtinGraphsCarryTheEarlyRescueNode() {
        for (SolverGraph g : new SolverGraph[]{BuiltinGraphs.fast(), BuiltinGraphs.optimize(10)}) {
            GraphNode rescue = nodeById(g, "freeRescue");
            assertNotNull(g.name + " must contain the early free-start rescue", rescue);
            assertTrue(rescue.params.getBool("jointOnly"));

        }
    }

    @Test
    public void infeasibleSeedIsRescuedBeforeTheRaces() {
        ProblemFixture pf = ProblemFixture.load("solve", "df-chain-free-start");
        ProblemFixture.Run run = pf.solve(60_000L);
        SolveResult r = run.result;
        assertNotNull("engine returned no result", r);
        assertTrue("free-start dF capture must solve", r.isSuccess());
        assertNotNull("solver label missing", r.getSolver());
        assertTrue("expected the early joint rescue to produce the candidate, got: " + r.getSolver(),
                r.getSolver().contains("free start rescue"));
    }
}
