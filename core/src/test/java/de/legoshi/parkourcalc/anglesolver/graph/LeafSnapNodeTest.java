package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.LeafSnapNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LeafSnap;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LeafSnapNodeTest {

    private static final String CAPTURE = "j017-postwalllightningneo";
    private static final String LONG_CAPTURE = "j024-bfly-goal-direction";
    private static final long BUDGET_NANOS = 60_000_000_000L;

    private static LeafSnapNode node(boolean repeat) {
        return new LeafSnapNode(NodeCatalog.byId("leafSnap").defaultParams()
                .set("pairPass", 1).set("repeat", repeat));
    }

    private static NodeOutcome snap(GraphContext ctx, Candidate in, boolean repeat, boolean budgeted) {
        return node(repeat).execute(ctx, in, new AtomicBoolean(false),
                budgeted ? System.nanoTime() + BUDGET_NANOS : 0L);
    }

    @Test
    public void repeatDefaultsToOff() {
        assertFalse(NodeCatalog.byId("leafSnap").defaultParams().getBool("repeat"));
    }

    private static CertifiedBnb.Result landAfter60Nodes(ProblemFixture pf, JumpSpec spec, String capture) {
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.OPTIMIZE;
        cfg.nodeCap = 60;
        cfg.polishCap = 12;
        cfg.incumbentPolishCap = 12;
        cfg.deadlineNanos = System.nanoTime() + BUDGET_NANOS;
        CertifiedBnb.Result landed = CertifiedBnb.solve(pf.model, spec, cfg);
        assertTrue(capture + " must land in 60 nodes", landed.feasible);
        return landed;
    }

    private static JumpSpec optimizeSpec(ProblemFixture pf) {
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        return rig.spec();
    }

    @Test
    public void aRepeatedSnapEndsFurtherThanOneCallAndAtAPointNoSnapImproves() {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = optimizeSpec(pf);
        double sign = spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0;
        CertifiedBnb.Result landed = landAfter60Nodes(pf, spec, CAPTURE);

        GraphContext ctx = new GraphContext(spec, pf.model, null, null, 0.0,
                new AtomicBoolean(false), null, true, null);
        Candidate in = Candidate.of(ctx, landed.yawsDeg);
        assertTrue("the certified landing must be feasible in the graph", in.feasible);

        NodeOutcome once = snap(ctx, in, false, true);
        NodeOutcome repeated = snap(ctx, in, true, true);
        NodeOutcome unbudgeted = snap(ctx, in, true, false);
        assertEquals(Guarantee.ADOPTED, once.branch);
        assertEquals(Guarantee.ADOPTED, repeated.branch);
        assertEquals(Guarantee.ADOPTED, unbudgeted.branch);
        assertTrue(once.candidate.feasible && repeated.candidate.feasible);
        assertTrue("one snap must improve the landing", sign * once.candidate.objective > sign * in.objective);
        assertEquals("a repeat without a time budget is one call", once.candidate.objective,
                unbudgeted.candidate.objective, 0.0);
        assertTrue("repeated snap " + repeated.candidate.objective + " against " + once.candidate.objective
                        + " from one call: the repeat must gain more than 1e-4 on " + CAPTURE,
                sign * repeated.candidate.objective > sign * once.candidate.objective + 1.0e-4);
        assertEquals("the repeated landing must equal its byte-exact re-simulation",
                ctx.exactObjective(repeated.candidate.yaws), repeated.candidate.objective, 0.0);
        assertEquals(0.0, ctx.violationOf(repeated.candidate.yaws), 0.0);

        NodeOutcome again = snap(ctx, repeated.candidate, false, true);
        assertEquals("no further snap may improve where the repeat stopped", Guarantee.REJECTED, again.branch);
    }

    @Test
    public void onALongJumpTheRepeatAlsoSnapsTheTicksBeforeTheLastTwelve() {
        ProblemFixture pf = ProblemFixture.load("solve", LONG_CAPTURE);
        JumpSpec spec = optimizeSpec(pf);
        double sign = spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0;
        CertifiedBnb.Result landed = landAfter60Nodes(pf, spec, LONG_CAPTURE);

        GraphContext ctx = new GraphContext(spec, pf.model, null, null, 0.0,
                new AtomicBoolean(false), null, true, null);
        Candidate in = Candidate.of(ctx, landed.yawsDeg);
        assertTrue("the certified landing must be feasible in the graph", in.feasible);

        double[] lastWindowOnly = landed.yawsDeg;
        for (int call = 0; call < 64; call++) {
            double[] next = LeafSnap.snap(pf.model, spec, lastWindowOnly, 0.0, new AtomicBoolean(false), 0L, true);
            if (next == null) break;
            lastWindowOnly = next;
        }
        int ticks = LeafSnap.snapTickCount(spec, lastWindowOnly);
        assertTrue(LONG_CAPTURE + " must have more snap ticks than one window holds, has " + ticks,
                ticks > LeafSnap.MAX_ENUM);
        double lastWindowObjective = ctx.exactObjective(lastWindowOnly);

        NodeOutcome repeated = snap(ctx, in, true, true);
        assertEquals(Guarantee.ADOPTED, repeated.branch);
        assertTrue(repeated.candidate.feasible);
        assertTrue("repeated snap " + repeated.candidate.objective + " against " + lastWindowObjective
                        + " from the last twelve ticks alone: the earlier ticks must gain more than 5e-5 on "
                        + LONG_CAPTURE,
                sign * repeated.candidate.objective > sign * lastWindowObjective + 5.0e-5);
        assertEquals("the repeated landing must equal its byte-exact re-simulation",
                ctx.exactObjective(repeated.candidate.yaws), repeated.candidate.objective, 0.0);
        assertEquals(0.0, ctx.violationOf(repeated.candidate.yaws), 0.0);
        for (int shift = 0; shift < ticks; shift += 6) {
            assertNull("no window may improve where the repeat stopped, shift " + shift,
                    LeafSnap.snap(pf.model, spec, repeated.candidate.yaws, 0.0, new AtomicBoolean(false), 0L,
                            true, shift));
        }
    }
}
