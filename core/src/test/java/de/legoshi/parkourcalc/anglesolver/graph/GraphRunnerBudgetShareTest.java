package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.Branch;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.InputRequirement;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCategory;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeRuntime;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraphRunnerBudgetShareTest {

    private static final long SEC = 1_000_000_000L;

    private static final class Seen {
        final AtomicLong deadline = new AtomicLong();
        final AtomicLong enteredAt = new AtomicLong();
        final AtomicInteger visits = new AtomicInteger();
    }

    private static GraphNode marker(String id, String typeId) {
        NodeType t = NodeCatalog.byId(typeId);
        return new GraphNode(id, t, t.defaultParams());
    }

    private static GraphNode budgeted(String id, int budgetSec, double share, double reserveFraction,
                                      int reserveCapSec, NodeRuntime runtime) {
        NodeType.Builder b = NodeType.builder("stub-" + id, id, NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(p -> runtime)
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .param(ParamSpec.decimal(GraphRunner.BUDGET_SHARE_PARAM, "Share", 0.0, 1.0, 0.0))
                .budgetParam("budgetSec");
        if (reserveFraction > 0.0) {
            b.param(ParamSpec.decimal(GraphRunner.RESERVE_FRACTION_PARAM, "Reserve", 0.0, 1.0, 0.0));
            b.param(ParamSpec.integer(GraphRunner.RESERVE_CAP_PARAM, "Reserve cap", 0, 600, 0));
        }
        NodeType t = b.build();
        GraphNode n = new GraphNode(id, t, t.defaultParams());
        n.params.set("budgetSec", budgetSec);
        n.params.set(GraphRunner.BUDGET_SHARE_PARAM, share);
        if (reserveFraction > 0.0) {
            n.params.set(GraphRunner.RESERVE_FRACTION_PARAM, reserveFraction);
            n.params.set(GraphRunner.RESERVE_CAP_PARAM, reserveCapSec);
        }
        return n;
    }

    private static NodeRuntime instant(Seen seen) {
        return (ctx, in, tok, dl) -> {
            seen.visits.incrementAndGet();
            seen.enteredAt.set(System.nanoTime());
            seen.deadline.set(dl);
            return NodeOutcome.of(Guarantee.DONE, in);
        };
    }

    private static NodeRuntime untilTripped(Seen seen) {
        return (ctx, in, tok, dl) -> {
            seen.visits.incrementAndGet();
            seen.enteredAt.set(System.nanoTime());
            seen.deadline.set(dl);
            long failsafe = System.nanoTime() + 10 * SEC;
            while (!tok.get() && (dl <= 0 || System.nanoTime() < dl) && System.nanoTime() < failsafe) {
                sleep(2);
            }
            return NodeOutcome.of(Guarantee.DONE, in);
        };
    }

    private static SolverGraph chain(GraphNode... nodes) {
        List<GraphNode> all = new ArrayList<>();
        all.add(marker("entry", "entry"));
        all.add(marker("emit", "emit"));
        all.addAll(Arrays.asList(nodes));
        List<GraphEdge> edges = new ArrayList<>();
        String prev = "entry";
        for (GraphNode n : nodes) {
            edges.add(new GraphEdge(prev, Guarantee.DONE, n.id));
            prev = n.id;
        }
        edges.add(new GraphEdge(prev, Guarantee.DONE, "emit"));
        return new SolverGraph("t", false, all, edges);
    }

    private static GraphContext context(long overallFromNow) {
        GraphContext ctx = TestScenarios.context(2, null, new AtomicBoolean(false));
        if (overallFromNow > 0) ctx.setOverallDeadline(System.nanoTime() + overallFromNow);
        return ctx;
    }

    private static long budgetOf(GraphContext ctx, String id) {
        return ctx.runState.status(id).budgetNanos;
    }

    private static void assertBetween(String what, long lo, long hi, long actual) {
        assertTrue(what + " expected in [" + lo / 1e9 + " s, " + hi / 1e9 + " s], was " + actual / 1e9 + " s",
                actual >= lo && actual <= hi);
    }

    @Test
    public void shareIsAFractionOfTheRemainingSolveTime() {
        Seen a = new Seen();
        GraphContext ctx = context(10 * SEC);
        GraphRunner.run(chain(budgeted("a", 0, 0.5, 0.0, 0, instant(a))), ctx);
        assertBetween("a budget", 4 * SEC, 5 * SEC, budgetOf(ctx, "a"));
        assertBetween("a deadline", 4 * SEC, 5 * SEC, a.deadline.get() - a.enteredAt.get());
    }

    @Test
    public void earlyFinisherHandsItsTimeOn() {
        Seen a = new Seen();
        Seen b = new Seen();
        GraphContext ctx = context(10 * SEC);
        GraphRunner.run(chain(
                budgeted("a", 0, 0.5, 0.0, 0, instant(a)),
                budgeted("b", 0, 1.0, 0.0, 0, instant(b))), ctx);
        assertBetween("a budget", 4 * SEC, 5 * SEC, budgetOf(ctx, "a"));
        assertBetween("b budget takes what a left", 9 * SEC, 10 * SEC, budgetOf(ctx, "b"));
    }

    @Test
    public void shareIsDebitedByTheTimeActuallySpent() {
        Seen a = new Seen();
        Seen b = new Seen();
        GraphContext ctx = context(2 * SEC);
        GraphRunner.run(chain(
                budgeted("a", 0, 0.5, 0.0, 0, untilTripped(a)),
                budgeted("b", 0, 1.0, 0.0, 0, instant(b))), ctx);
        assertBetween("a budget", (long) (0.9 * SEC), SEC, budgetOf(ctx, "a"));
        assertBetween("b budget is the remainder after a ran out its share", (long) (0.5 * SEC),
                (long) (1.1 * SEC), budgetOf(ctx, "b"));
    }

    @Test
    public void theSmallerOfShareAndSecondsWins() {
        Seen a = new Seen();
        Seen b = new Seen();
        GraphContext ctx = context(100 * SEC);
        GraphRunner.run(chain(
                budgeted("a", 20, 0.5, 0.0, 0, instant(a)),
                budgeted("b", 20, 0.1, 0.0, 0, instant(b))), ctx);
        assertEquals("seconds cap below the share", 20 * SEC, budgetOf(ctx, "a"));
        assertBetween("share below the seconds cap", 9 * SEC, 10 * SEC, budgetOf(ctx, "b"));
    }

    @Test
    public void shareWithoutASolveTimeIsSkipped() {
        Seen a = new Seen();
        Seen b = new Seen();
        GraphContext ctx = context(0L);
        GraphRunner.run(chain(
                budgeted("a", 0, 0.5, 0.0, 0, instant(a)),
                budgeted("b", 0, 0.0, 0.0, 0, instant(b))), ctx);
        assertEquals(1, a.visits.get());
        assertEquals("the share has no pool, so the node gets a zero budget", 0L, budgetOf(ctx, "a"));
        assertTrue("the deadline handed to the node is already past",
                a.deadline.get() > 0 && a.deadline.get() <= a.enteredAt.get());
        assertEquals("the walk goes on past the skipped node", 1, b.visits.get());
        assertEquals("a node without any budget stays unbounded", 0L, b.deadline.get());
    }

    @Test
    public void shareWithoutASolveTimeFallsBackToTheSecondsLimit() {
        Seen a = new Seen();
        GraphContext ctx = context(0L);
        GraphRunner.run(chain(budgeted("a", 2, 0.5, 0.0, 0, instant(a))), ctx);
        assertEquals(2 * SEC, budgetOf(ctx, "a"));
        assertBetween("a deadline", (long) (1.9 * SEC), 2 * SEC, a.deadline.get() - a.enteredAt.get());
    }

    @Test
    public void shareLeavesTheReservePendingDownstreamAlone() {
        Seen a = new Seen();
        Seen b = new Seen();
        GraphContext ctx = context(10 * SEC);
        GraphRunner.run(chain(
                budgeted("a", 0, 1.0, 0.0, 0, instant(a)),
                budgeted("b", 0, 0.0, 0.3, 0, instant(b))), ctx);
        assertBetween("a takes the pool minus b's 3 s reserve", 6 * SEC, 7 * SEC, budgetOf(ctx, "a"));
        assertBetween("b runs on its reserve", (long) (2.9 * SEC), 3 * SEC, budgetOf(ctx, "b"));
    }

    @Test
    public void shareGuardedLoopEndsWithTheSolveTime() {
        Seen a = new Seen();
        GraphNode node = budgeted("a", 0, 0.5, 0.0, 0, untilTripped(a));
        SolverGraph g = new SolverGraph("t", false,
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), node),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.DONE, "a")));
        long t0 = System.nanoTime();
        GraphContext ctx = context((long) (0.4 * SEC));
        GraphRunner.run(g, ctx);
        long elapsed = System.nanoTime() - t0;
        assertTrue("the loop ran its visits", a.visits.get() >= 2);
        assertTrue("the loop stopped near the solve time, took " + elapsed / 1e9 + " s", elapsed < 5 * SEC);
        assertNotNull(ctx.runState.status("a"));
    }

    @Test
    public void shareGuardedLoopWithoutASolveTimeStopsAtTheVisitCap() {
        Seen a = new Seen();
        GraphNode node = budgeted("a", 0, 1.0, 0.0, 0, instant(a));
        SolverGraph g = new SolverGraph("t", false,
                Arrays.asList(marker("entry", "entry"), marker("emit", "emit"), node),
                Arrays.asList(
                        new GraphEdge("entry", Guarantee.DONE, "a"),
                        new GraphEdge("a", Guarantee.DONE, "a")));
        long t0 = System.nanoTime();
        GraphRunner.run(g, context(0L));
        assertEquals(GraphRunner.MAX_NODE_VISITS, a.visits.get());
        assertTrue("every visit was a zero-budget pass", System.nanoTime() - t0 < 5 * SEC);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
