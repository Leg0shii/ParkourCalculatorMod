package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.BnbNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BnbNodeTest {

    private static final String CAPTURE = "inertia-1tick-neo";
    private static final int RESCUE_SEC = 20;

    @Test
    public void aRescueOnAJumpWithACapWallEndsAtItsFirstLanding() {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        assertFalse(CAPTURE + " must have a cap wall on its objective",
                Double.isNaN(Scoring.objectiveCap(spec)));
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            double[] first = BoundPrunedRecovery.solve(pf.model, spec, 0.0, new AtomicBoolean(false),
                    RESCUE_SEC * 1_000_000_000L, max ? -1.0e300 : 1.0e300);
            double[] toTheCap = BoundPrunedRecovery.solve(pf.model, spec, 0.0, new AtomicBoolean(false),
                    RESCUE_SEC * 1_000_000_000L, Scoring.objectiveCap(spec));
            assertNotNull(CAPTURE + " must land in the pattern search", first);
            assertNotNull(toTheCap);
            GraphContext ctx = new GraphContext(spec, pf.model, null, null, 0.0,
                    new AtomicBoolean(false), null, true, null);
            assertTrue("a search on to the cap must end on another landing, or this test shows nothing",
                    ctx.exactObjective(Angles.wrapAll(first)) != ctx.exactObjective(Angles.wrapAll(toTheCap)));
            BnbNode node = new BnbNode(NodeCatalog.byId("bnb").defaultParams().set("ffSec", RESCUE_SEC));
            NodeOutcome out = node.execute(ctx, null, new AtomicBoolean(false), 0L);
            assertEquals(Guarantee.FOUND, out.branch);
            assertArrayEquals(Angles.wrapAll(first), out.candidate.yaws, 0.0);
        } finally {
            clock.uninstall();
        }
    }
}
