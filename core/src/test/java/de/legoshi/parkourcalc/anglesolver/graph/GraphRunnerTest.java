package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.graph.Branch;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.InputRequirement;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCategory;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeStatus;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(SlowSolverTests.class)
public class GraphRunnerTest {

    private static GraphNode marker(String id, String typeId) {
        NodeType t = NodeCatalog.byId(typeId);
        return new GraphNode(id, t, t.defaultParams());
    }

    private static GraphNode stub(String id, NodeRuntime runtime, Guarantee... branches) {
        return stub(id, runtime, 0, branches);
    }

    private static GraphNode stub(String id, NodeRuntime runtime, int budgetSec,
                                  Guarantee... branches) {
        NodeType.Builder b = NodeType.builder("stub-" + id, id, NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .fallback(branches[branches.length - 1])
                .factory(p -> runtime);
        for (Guarantee g : branches) b.branch(Branch.preserves(g));
        if (budgetSec > 0) {
            b.param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, budgetSec));
            b.budgetParam("budgetSec");
        }
        NodeType t = b.build();
        return new GraphNode(id, t, t.defaultParams());
    }

    private static SolverGraph graph(List<GraphNode> nodes, List<GraphEdge> edges) {
        return new SolverGraph("t", false, nodes, edges);
    }

    @Test
    public void routesByBranchAndRecordsOrder() {
        List<String> order = new ArrayList<>();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            order.add("a");
            return NodeOutcome.of(Guarantee.FOUND, in);
        }, Guarantee.FOUND, Guarantee.NONE);
        GraphNode b = stub("b", (ctx, in, tok, dl) -> {
            order.add("b");
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        GraphNode c = stub("c", (ctx, in, tok, dl) -> {
            order.add("c");
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a, b, c),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.FOUND, "b"),
                        new GraphEdge("a", Guarantee.NONE, "c"),
                        new GraphEdge("b", Guarantee.DONE, "emit")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        assertEquals(Arrays.asList("a", "b"), order);
    }

    @Test
    public void runStateRecordsTheWalk() {
        GraphNode a = stub("a", (ctx, in, tok, dl) -> NodeOutcome.of(Guarantee.FOUND, in),
                Guarantee.FOUND, Guarantee.NONE);
        GraphNode b = stub("b", (ctx, in, tok, dl) -> NodeOutcome.of(Guarantee.DONE, in), Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a, b),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.FOUND, "b"),
                        new GraphEdge("b", Guarantee.DONE, "emit")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        List<GraphRunState.Step> steps = ctx.runState.steps();
        assertEquals(2, steps.size());
        assertEquals("a", steps.get(0).nodeId);
        assertEquals(Guarantee.FOUND, steps.get(0).taken);
        assertEquals("b", steps.get(1).nodeId);
        assertEquals(Guarantee.DONE, steps.get(1).taken);
        assertNull(ctx.runState.activeNodeId());
        assertEquals(NodeStatus.Phase.DONE, ctx.runState.status("a").phase);
        assertEquals(1, ctx.runState.status("a").visits);
        assertEquals(NodeStatus.Phase.DONE, ctx.runState.status("b").phase);
    }

    @Test
    public void unwiredBranchFallsThroughToEmit() {
        List<String> order = new ArrayList<>();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            order.add("a");
            return NodeOutcome.of(Guarantee.NONE, in);
        }, Guarantee.FOUND, Guarantee.NONE);
        GraphNode b = stub("b", (ctx, in, tok, dl) -> {
            order.add("b");
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a, b),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.FOUND, "b")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        assertEquals(Arrays.asList("a"), order);
    }

    @Test
    public void selfLoopStopsAtVisitCap() {
        AtomicInteger count = new AtomicInteger();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            count.incrementAndGet();
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.DONE, "a")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        assertEquals(GraphRunner.MAX_NODE_VISITS, count.get());
    }

    @Test
    public void watchdogTripsNodeTokenAtBudget() {
        long t0 = System.nanoTime();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            long failsafe = System.nanoTime() + 10_000_000_000L;
            while (!tok.get() && System.nanoTime() < failsafe) {
                sleep(5);
            }
            return NodeOutcome.of(tok.get() ? Guarantee.DONE : Guarantee.NONE, in);
        }, 1, Guarantee.DONE, Guarantee.NONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a),
                Arrays.asList(new GraphEdge("entry", Guarantee.DONE, "a")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue("budget should trip around 1s, was " + elapsedMs + " ms", elapsedMs >= 900 && elapsedMs < 5000);
    }

    @Test
    public void parentCancelAbortsTheRun() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        List<String> order = new ArrayList<>();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            long failsafe = System.nanoTime() + 10_000_000_000L;
            while (!tok.get() && System.nanoTime() < failsafe) {
                sleep(5);
            }
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        GraphNode b = stub("b", (ctx, in, tok, dl) -> {
            order.add("b");
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a, b),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.DONE, "b"),
                        new GraphEdge("b", Guarantee.DONE, "emit")));
        GraphContext ctx = TestScenarios.context(2, null, cancel);
        Thread canceller = new Thread(() -> {
            sleep(150);
            cancel.set(true);
        });
        canceller.setDaemon(true);
        canceller.start();
        assertNull(GraphRunner.run(g, ctx));
        assertTrue("no node after the cancelled one may run", order.isEmpty());
    }

    @Test
    public void nodeAbortExceptionIsContainedAsFallback() {
        List<String> order = new ArrayList<>();
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            tok.set(true);
            throw new IllegalStateException("aborted mid-search");
        }, Guarantee.FOUND, Guarantee.NONE);
        GraphNode b = stub("b", (ctx, in, tok, dl) -> {
            order.add("b");
            return NodeOutcome.of(Guarantee.DONE, in);
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a, b),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.NONE, "b"),
                        new GraphEdge("b", Guarantee.DONE, "emit")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        GraphRunner.run(g, ctx);
        assertEquals(Arrays.asList("b"), order);
    }

    @Test
    public void genuineCrashPropagates() {
        GraphNode a = stub("a", (ctx, in, tok, dl) -> {
            throw new IllegalStateException("boom");
        }, Guarantee.DONE);
        SolverGraph g = graph(
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), a),
                Arrays.asList(new GraphEdge("entry", Guarantee.DONE, "a")));
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        try {
            GraphRunner.run(g, ctx);
            fail("expected the crash to propagate");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
