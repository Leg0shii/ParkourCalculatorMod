package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.SeedSweepNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SeedSweepClockTest {

    private static final String CAPTURE = "j016-X2jmmp2p";
    private static final double BOX_HALF_WIDTH = 0.05;
    private static final int SEEDS = 64;
    private static final long BUDGET_NANOS = 4_000_000L;
    private static final long OVERSHOOT_NANOS = 1_000_000L;
    private static final Pattern LANDED = Pattern.compile("seed sweep (\\d+)/" + SEEDS);

    private static final class Run {
        final String facings;
        final long workNanos;
        final String work;
        final int landed;

        Run(String facings, long workNanos, String work, int landed) {
            this.facings = facings;
            this.workNanos = workNanos;
            this.work = work;
            this.landed = landed;
        }
    }

    @Test
    public void theSeedsShareOneWorkBudget() {
        Run full = sweep(0L, false);
        Run budgeted = sweep(BUDGET_NANOS, false);
        assertEquals("without a budget every seed runs", SEEDS, full.landed);
        assertTrue("the whole sweep is more than twice the budget, counted " + full.workNanos,
                full.workNanos > 2L * BUDGET_NANOS);
        assertTrue("the sweep ran " + budgeted.workNanos + " ns of work on a budget of " + BUDGET_NANOS,
                budgeted.workNanos <= BUDGET_NANOS + OVERSHOOT_NANOS);
        assertTrue("the budget ends the sweep before every seed had its turn: " + budgeted.landed,
                budgeted.landed > 0 && budgeted.landed < SEEDS);
    }

    @Test
    public void aBudgetedSweepRepeatsOnAnyThreadCount() {
        Run threads = sweep(BUDGET_NANOS, false);
        Run again = sweep(BUDGET_NANOS, false);
        Run oneThread = sweep(BUDGET_NANOS, true);
        assertSame("a second run", threads, again);
        assertSame("one thread", threads, oneThread);
    }

    private static void assertSame(String what, Run expected, Run actual) {
        assertEquals(what + ": facings differ", expected.facings, actual.facings);
        assertEquals(what + ": counted work differs", expected.work, actual.work);
        assertEquals(what + ": work time differs", expected.workNanos, actual.workNanos);
        assertEquals(what + ": seeds that landed differ", expected.landed, actual.landed);
    }

    private static Run sweep(long budgetNanos, boolean sequential) {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        JumpPhysicsInputs sc = spec.asScenario();
        double x = sc.startPos.x;
        double z = sc.startPos.z;
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        StartBox box = new StartBox(x, z, vx, vz, x - BOX_HALF_WIDTH, x + BOX_HALF_WIDTH,
                z - BOX_HALF_WIDTH, z + BOX_HALF_WIDTH, vx, vx, vz, vz);
        GraphContext ctx = new GraphContext(spec, pf.model, box, null, 1.0e-9, new AtomicBoolean(false), null,
                sequential, null);
        SeedSweepNode node = new SeedSweepNode(NodeCatalog.byId("seedSweep").defaultParams()
                .set("seeds", SEEDS).set("threads", 0));
        AtomicBoolean token = new AtomicBoolean(false);
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            long start = clock.elapsedNanos();
            long deadline = budgetNanos > 0L ? SolveClock.nanoTime() + budgetNanos : 0L;
            if (deadline > 0L) clock.arm(token, deadline);
            NodeOutcome out = node.execute(ctx, null, token, deadline);
            Candidate found = out.candidate;
            assertNotNull(CAPTURE + ": the sweep lands", found);
            assertNotNull(CAPTURE + ": the sweep lands", found.yaws);
            String chain = ctx.chain();
            Matcher m = LANDED.matcher(chain == null ? "" : chain);
            assertTrue(CAPTURE + ": no seed count in '" + chain + "'", m.find());
            return new Run(Arrays.toString(found.yaws), clock.elapsedNanos() - start, clock.describe(),
                    Integer.parseInt(m.group(1)));
        } finally {
            clock.uninstall();
        }
    }
}
