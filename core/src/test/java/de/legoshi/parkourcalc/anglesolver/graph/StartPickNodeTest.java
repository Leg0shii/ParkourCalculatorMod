package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.graph.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeOutcome;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.DualChainNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RecedingHorizonNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.StartPickNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StartPickNodeTest {

    private static final int TICKS = 12;
    private static final String CAPTURE = "gh386-4x2-luckyseed";
    private static final double BOX_HALF_WIDTH = 0.05;
    private static final long BUDGET_NANOS = 60_000_000_000L;

    private static GraphContext plainContext() {
        boolean[] jumps = new boolean[TICKS];
        jumps[0] = true;
        return TestScenarios.context(TICKS, jumps, new AtomicBoolean(false));
    }

    private static double[] facings(double deg) {
        double[] yaws = new double[TICKS];
        Arrays.fill(yaws, deg);
        return yaws;
    }

    private static NodeOutcome pick(GraphContext ctx, Candidate in) {
        return new StartPickNode(NodeCatalog.byId("pickStart").defaultParams())
                .execute(ctx, in, new AtomicBoolean(false), 0L);
    }

    @Test
    public void withNothingHeldTheCandidatePassesThrough() {
        GraphContext ctx = plainContext();
        Candidate in = Candidate.of(ctx, facings(-90.0));
        NodeOutcome out = pick(ctx, in);
        assertEquals(Guarantee.DONE, out.branch);
        assertSame(in, out.candidate);
        assertEquals(Guarantee.DONE, pick(ctx, null).branch);
        assertNull(pick(ctx, null).candidate);
    }

    @Test
    public void theHeldStartComesBackWithItsStartWhenItLandsFurther() {
        GraphContext ctx = plainContext();
        Candidate straight = Candidate.of(ctx, facings(-90.0));
        Candidate turned = Candidate.of(ctx, facings(-60.0));
        assertTrue("a straight run must reach further in X", straight.objective > turned.objective);
        Vec3dCore pos = ctx.scenario.startPos;
        StartBox box = ctx.scenario.startBox;

        ctx.holdStart(straight);
        Scoring.adoptPinnedStart(ctx.scenario, 0.25, -0.5);
        NodeOutcome out = pick(ctx, turned);

        assertEquals(Guarantee.DONE, out.branch);
        assertSame(straight, out.candidate);
        assertSame("the start position of the held landing must be back", pos, ctx.scenario.startPos);
        assertSame("the start box of the held landing must be back", box, ctx.scenario.startBox);
        assertNull("the held landing is handed out once", ctx.heldStart());
        assertTrue(ctx.chain().contains("first start kept"));
        assertEquals("the landing must equal its replay at the restored start",
                ctx.exactObjective(straight.yaws), out.candidate.objective, 0.0);
    }

    @Test
    public void theCurrentLandingStaysWhenItLandsFurther() {
        GraphContext ctx = plainContext();
        Candidate straight = Candidate.of(ctx, facings(-90.0));
        Candidate turned = Candidate.of(ctx, facings(-60.0));

        ctx.holdStart(turned);
        Scoring.adoptPinnedStart(ctx.scenario, 0.25, -0.5);
        Vec3dCore moved = ctx.scenario.startPos;
        NodeOutcome out = pick(ctx, straight);

        assertSame(straight, out.candidate);
        assertSame("the current start stays", moved, ctx.scenario.startPos);
        assertNull(ctx.heldStart());
        assertTrue(ctx.chain() == null || !ctx.chain().contains("first start kept"));
    }

    @Test
    public void theHeldStartComesBackWhenNothingElseLanded() {
        GraphContext ctx = plainContext();
        Candidate held = Candidate.of(ctx, facings(-60.0));
        ctx.holdStart(held);
        assertSame(held, pick(ctx, null).candidate);
        assertNull(ctx.heldStart());
    }

    @Test
    public void anEqualLandingKeepsTheHeldStart() {
        GraphContext ctx = plainContext();
        Candidate held = Candidate.of(ctx, facings(-90.0));
        Candidate same = Candidate.of(ctx, facings(-90.0));
        ctx.holdStart(held);
        assertSame(held, pick(ctx, same).candidate);
    }

    @Test
    public void theHorizonSkipsAnExistingLandingUnlessToldToRunBesideIt() {
        assertFalse(NodeCatalog.byId("recedingHorizon").defaultParams().getBool("keepIncoming"));
        GraphContext ctx = freeStartContext();
        Candidate cold = coldStart(ctx);

        NodeOutcome skipped = horizon(false).execute(ctx, cold, new AtomicBoolean(false),
                System.nanoTime() + BUDGET_NANOS);
        assertEquals(Guarantee.NONE, skipped.branch);
        assertSame(cold, skipped.candidate);
        assertNull(ctx.heldStart());
    }

    @Test
    public void theHorizonRunsBesideAColdStartAndThePickKeepsTheFurtherOne() {
        GraphContext ctx = freeStartContext();
        double sign = ctx.maximize() ? 1.0 : -1.0;
        Candidate cold = coldStart(ctx);
        Vec3dCore coldPos = ctx.scenario.startPos;

        NodeOutcome built = horizon(true).execute(ctx, cold, new AtomicBoolean(false),
                System.nanoTime() + BUDGET_NANOS);
        assertEquals(CAPTURE + ": the horizon must build its own route", Guarantee.FOUND, built.branch);
        assertTrue(built.candidate.feasible);
        assertSame("the cold start must be set aside", cold, ctx.heldStart());

        NodeOutcome picked = pick(ctx, built.candidate);
        assertNull(ctx.heldStart());
        Candidate kept = picked.candidate;
        assertTrue(kept == cold || kept == built.candidate);
        assertTrue("the pick must keep the further landing",
                sign * kept.objective >= sign * cold.objective
                        && sign * kept.objective >= sign * built.candidate.objective);
        if (kept == cold) assertSame(coldPos, ctx.scenario.startPos);
        assertEquals("the kept landing must equal its replay at the current start",
                ctx.exactObjective(kept.yaws), kept.objective, 0.0);
        assertTrue(ctx.violationOf(kept.yaws) <= ctx.feasTol);
    }

    @Test
    public void aPinnedStartIsNeverSetAside() {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        GraphContext ctx = new GraphContext(spec, pf.model, null, null, 1.0e-9, new AtomicBoolean(false), null,
                true, null);
        Candidate cold = coldStart(ctx);
        NodeOutcome out = horizon(true).execute(ctx, cold, new AtomicBoolean(false),
                System.nanoTime() + BUDGET_NANOS);
        assertEquals(Guarantee.NONE, out.branch);
        assertSame(cold, out.candidate);
        assertNull(ctx.heldStart());
    }

    private static RecedingHorizonNode horizon(boolean keepIncoming) {
        return new RecedingHorizonNode(NodeCatalog.byId("recedingHorizon").defaultParams()
                .set("keepIncoming", keepIncoming));
    }

    private static Candidate coldStart(GraphContext ctx) {
        NodeOutcome out = new DualChainNode(NodeCatalog.byId("dualChain").defaultParams().set("keepBetter", true))
                .execute(ctx, null, new AtomicBoolean(false), System.nanoTime() + BUDGET_NANOS);
        assertNotNull(CAPTURE + ": the cold start must land", out.candidate);
        assertTrue(CAPTURE + ": the cold start must land", out.candidate.yaws != null && out.candidate.feasible);
        return out.candidate;
    }

    private static GraphContext freeStartContext() {
        ProblemFixture pf = ProblemFixture.load("solve", CAPTURE);
        JumpSpec spec = pf.rig().spec();
        JumpPhysicsInputs sc = spec.asScenario();
        double x = sc.startPos.x;
        double z = sc.startPos.z;
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        StartBox box = new StartBox(x, z, vx, vz, x - BOX_HALF_WIDTH, x + BOX_HALF_WIDTH,
                z - BOX_HALF_WIDTH, z + BOX_HALF_WIDTH, vx, vx, vz, vz);
        return new GraphContext(spec, pf.model, box, null, 1.0e-9, new AtomicBoolean(false), null, true, null);
    }
}
