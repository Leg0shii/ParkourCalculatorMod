package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class GraphRunnerDeadlineTest {

    private static SolverGraph settledGraph() {
        GraphBuilder g = new GraphBuilder("deadline-test", true);
        g.add("entry", "entry");
        g.add("emit", "emit");
        g.add("mark", "markSettled");
        g.edge("entry", Guarantee.DONE, "mark");
        g.edge("mark", Guarantee.DONE, "emit");
        return g.build();
    }

    @Test
    public void expiredOverallDeadlineStillWalksWithExpiredNodeDeadlines() {
        GraphContext ctx = TestScenarios.context(4, null, new AtomicBoolean(false));
        ctx.setOverallDeadline(System.nanoTime() - 1L);
        GraphRunner.run(settledGraph(), ctx);
        assertTrue(ctx.settled());
    }

    @Test
    public void withoutOverallDeadlineNodesRun() {
        GraphContext ctx = TestScenarios.context(4, null, new AtomicBoolean(false));
        GraphRunner.run(settledGraph(), ctx);
        assertTrue(ctx.settled());
    }

    @Test
    public void futureOverallDeadlineNodesRun() {
        GraphContext ctx = TestScenarios.context(4, null, new AtomicBoolean(false));
        ctx.setOverallDeadline(System.nanoTime() + 60_000_000_000L);
        GraphRunner.run(settledGraph(), ctx);
        assertTrue(ctx.settled());
    }
}
