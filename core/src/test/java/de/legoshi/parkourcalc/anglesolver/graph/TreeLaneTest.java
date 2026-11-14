package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphBuilder;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class TreeLaneTest {

    private static final String CAPTURE = "j016-X2jmmp2p";
    private static final long BUDGET_NANOS = 400_000_000L;

    private static final class Run {
        final String facings;
        final String chain;
        final long workNanos;
        final String work;
        final List<String> steps;

        Run(String facings, String chain, long workNanos, String work, List<String> steps) {
            this.facings = facings;
            this.chain = chain;
            this.workNanos = workNanos;
            this.work = work;
            this.steps = steps;
        }
    }

    @Test
    public void aLaneEndsWhereTheSameStageInTheChainEnds() {
        Run inline = run(false, false, BuiltinGraphs.TREE_BATCH);
        Run lane = run(true, false, BuiltinGraphs.TREE_BATCH);
        assertEquals("facings", inline.facings, lane.facings);
        assertEquals("chain", inline.chain, lane.chain);
        assertEquals("the lane's step stays where it was started", Arrays.asList("seed", "cert", "join"),
                lane.steps);
    }

    @Test
    public void aLaneRepeatsOnAnyThreadCount() {
        Run threads = run(true, false, BuiltinGraphs.TREE_BATCH);
        Run again = run(true, false, BuiltinGraphs.TREE_BATCH);
        Run oneThread = run(true, true, BuiltinGraphs.TREE_BATCH);
        for (Run r : new Run[]{again, oneThread}) {
            assertEquals("facings", threads.facings, r.facings);
            assertEquals("counted work", threads.work, r.work);
            assertEquals("work time", threads.workNanos, r.workNanos);
        }
    }

    @Test
    public void theSearchImprovesTheLandingItIsHanded() {
        Run lane = run(true, false, BuiltinGraphs.TREE_BATCH);
        assertTrue("the certified search should land past the seed on " + CAPTURE + ": " + lane.chain,
                lane.chain.contains("certified B&B"));
    }

    private static Run run(boolean asLane, boolean sequential, int batch) {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        GraphBuilder g = new GraphBuilder("lane", false);
        g.add("entry", "entry");
        g.add("seed", "dualChain");
        g.add("cert", "certBnb")
                .set("cert", "budgetSec", 1)
                .set("cert", "batch", batch)
                .set("cert", GraphRunner.LANE_PARAM, asLane);
        g.add("join", "joinLanes");
        g.add("emit", "emit");
        g.chainAll("entry", "seed");
        g.chainAll("seed", "cert");
        g.chainAll("cert", "join");
        g.chainAll("join", "emit");
        SolverGraph graph = g.build();
        AtomicBoolean cancel = new AtomicBoolean(false);
        GraphContext ctx = new GraphContext(spec, pf.model, null, null, 1.0e-9, cancel, null, sequential, null);
        SolveClock clock = SolveClock.install(cancel, 0L, 0L);
        try {
            ctx.setOverallDeadline(SolveClock.nanoTime() + BUDGET_NANOS);
            Candidate out = GraphRunner.run(graph, ctx);
            assertNotNull(CAPTURE + " lands", out);
            assertNotNull(CAPTURE + " lands", out.yaws);
            assertTrue(CAPTURE + " lands", out.feasible);
            List<String> steps = new ArrayList<>();
            for (GraphRunState.Step s : ctx.runState.steps()) steps.add(s.nodeId);
            return new Run(Arrays.toString(out.yaws), ctx.chain(), clock.elapsedNanos(), clock.describe(), steps);
        } finally {
            clock.uninstall();
        }
    }
}
