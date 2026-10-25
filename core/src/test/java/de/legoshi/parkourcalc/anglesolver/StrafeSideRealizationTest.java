package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.StrafeSideRealization;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-338: under Force 45 the same movement is W+A at yaw theta or W+D at yaw theta - 90, so the
 * strafe side can be chosen per tick after the solve to keep the camera flat through a flick. The
 * pass must flip exactly where a flip lowers the anchored yaw jerk, keep its hands off ticks that
 * are not strafe ticks or carry a facing constraint, and only adopt a realization the byte-exact
 * forward re-verifies.
 */
public class StrafeSideRealizationTest {

    private static final int N = 14;
    private static final ForwardModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    private static JumpPhysicsInputs sprintJump(int base) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0f;
        sc.initialVelocity = new Vec3dCore(0.0, 0.0, 0.2);
        sc.startBox = StartBox.pinned(0.5, 0.5, 0.0, 0.2);
        sc.jumpTick = 0;
        boolean[] jump = new boolean[N];
        jump[0] = true;
        sc.jumpPerTick = jump;
        sc.strafeSign = base;
        boolean[] strafe = new boolean[N];
        for (int t = 1; t < N; t++) strafe[t] = true;
        sc.strafePerTick = strafe;
        sc.speedAmplifier = new int[N];
        double[] slip = new double[N];
        Arrays.fill(slip, Double.NaN);
        slip[0] = 0.6;
        sc.slipPerTick = slip;
        sc.yawLockedPerTick = new boolean[N];
        float[] fwd = new float[N];
        Arrays.fill(fwd, 0.98f);
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = new float[N];
        boolean[] sprint = new boolean[N];
        Arrays.fill(sprint, true);
        sc.sprintPerTick = sprint;
        sc.incomingSprint = Boolean.TRUE;
        sc.incomingAmp = 0;
        return sc;
    }

    private static JumpSpec spec(JumpPhysicsInputs sc, List<JumpConstraint> cons) {
        return new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, N));
    }

    private static double[] flick(int at, double to) {
        double[] y = new double[N];
        for (int t = at; t < N; t++) y[t] = to;
        return y;
    }

    private static ForwardPath forward(JumpPhysicsInputs sc, double[] yaws) {
        return MODEL.forward(sc, sc.toGameFacings(yaws));
    }

    private static StrafeSideRealization realize(JumpSpec spec, double[] yaws) {
        return StrafeSideRealization.realize(MODEL, spec, yaws, forward(spec.asScenario(), yaws), false, 0.0, false);
    }

    @Test
    public void flickOverFortyFiveFlipsTheTailOntoTheOtherSide() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 60.0);
        JumpSpec spec = spec(sc, Collections.<JumpConstraint>emptyList());
        StrafeSideRealization r = realize(spec, yaws);
        assertNotNull("a 60 deg flick pays a flip", r);
        assertEquals(1, r.base);
        assertEquals(N - 5, r.flipped);
        for (int t = 0; t < N; t++) {
            assertEquals("side at " + t, t < 5 ? 1 : -1, r.sides[t]);
            assertEquals("yaw at " + t, t < 5 ? 0.0 : -30.0, r.yaws[t], 1e-12);
        }
        assertEquals(Angles.wiggleDeg(0.0, yaws), r.wiggleBefore, 1e-12);
        assertEquals(Angles.wiggleDeg(0.0, r.yaws), r.wiggleAfter, 1e-12);
        assertEquals(120.0, r.wiggleBefore, 1e-12);
        assertEquals(60.0, r.wiggleAfter, 1e-12);
        ForwardPath base = forward(sc, yaws);
        for (int k = 0; k <= N; k++) {
            assertEquals("x at " + k, base.posX[k], r.path.posX[k], 1e-3);
            assertEquals("z at " + k, base.posZ[k], r.path.posZ[k], 1e-3);
        }
        assertTrue("the realized path satisfies the (empty) constraint list", r.verified);
    }

    @Test
    public void facingThatCrossesZeroMovesBySineStepsAndIsRefusedWhenTheObjectiveMustHold() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 60.0);
        JumpSpec spec = spec(sc, Collections.<JumpConstraint>emptyList());
        ForwardPath base = forward(sc, yaws);
        StrafeSideRealization r = StrafeSideRealization.realize(MODEL, spec, yaws, base, false, 0.0, true);
        assertNotNull(r);
        assertEquals(N - 5, r.flipped);
        boolean moved = false;
        for (int k = 0; k <= N; k++) moved |= base.posZ[k] != r.path.posZ[k];
        assertTrue("the game truncates the sine index toward zero, so a facing that changes sign lands on the"
                + " next table step", moved);
        assertEquals(spec.objective.evaluate(base) > spec.objective.evaluate(r.path), !r.verified);
    }

    @Test
    public void tightWallDropsTheDriftingFlipsAndKeepsTheExactOnes() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = new double[N];
        for (int t = 3; t < 6; t++) yaws[t] = 70.0;
        for (int t = 9; t < N; t++) yaws[t] = 160.0;
        ForwardPath base = forward(sc, yaws);
        JumpSpec loose = spec(sc, Collections.<JumpConstraint>emptyList());
        StrafeSideRealization unconstrained = StrafeSideRealization.realize(MODEL, loose, yaws, base, false, 0.0, false);
        assertNotNull(unconstrained);
        for (int t = 3; t < 6; t++) assertEquals("crossing flick tick " + t, -1, unconstrained.sides[t]);
        for (int t = 9; t < N; t++) assertEquals("exact flick tick " + t, -1, unconstrained.sides[t]);
        assertEquals(0, unconstrained.dropped);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.EQ, base.posZ[N], "landing"));
        StrafeSideRealization r = StrafeSideRealization.realize(MODEL, spec(sc, cons), yaws, base, false, 0.0, false);
        assertNotNull(r);
        assertTrue("the exact flips survive the repair", r.verified);
        assertTrue("at least the first drifting flip is dropped", r.dropped >= 1);
        for (int t = 0; t < 9; t++) assertEquals("tick " + t, 1, r.sides[t]);
        for (int t = 9; t < N; t++) assertEquals("tick " + t, -1, r.sides[t]);
        assertArrayEquals(base.posX, r.path.posX, 0.0);
        assertArrayEquals(base.posZ, r.path.posZ, 0.0);
        assertTrue(r.wiggleAfter < r.wiggleBefore);
        assertEquals(StrafeSideRealization.wiggle(r.yaws, 0.0, false), r.wiggleAfter, 1e-12);
    }

    @Test
    public void crossingFlickAloneUnderATightWallStaysOnOneSide() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 60.0);
        ForwardPath base = forward(sc, yaws);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.EQ, base.posZ[N], "landing"));
        StrafeSideRealization r = StrafeSideRealization.realize(MODEL, spec(sc, cons), yaws, base, false, 0.0, false);
        assertNotNull("the refused first attempt is reported", r);
        assertFalse(r.verified);
        assertEquals(N - 5, r.flipped);
    }

    @Test
    public void facingThatKeepsItsSignIsByteExact() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 120.0);
        JumpSpec spec = spec(sc, Collections.<JumpConstraint>emptyList());
        ForwardPath base = forward(sc, yaws);
        StrafeSideRealization r = StrafeSideRealization.realize(MODEL, spec, yaws, base, false, 0.0, true);
        assertNotNull(r);
        assertEquals(N - 5, r.flipped);
        for (int t = 5; t < N; t++) assertEquals(30.0, r.yaws[t], 1e-12);
        assertArrayEquals(base.posX, r.path.posX, 0.0);
        assertArrayEquals(base.posZ, r.path.posZ, 0.0);
        assertTrue(r.verified);
    }

    @Test
    public void realizedPathRunsTheOtherKeysOnTheShiftedFacing() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 60.0);
        StrafeSideRealization r = realize(spec(sc, Collections.<JumpConstraint>emptyList()), yaws);
        assertNotNull(r);
        JumpPhysicsInputs sides = sc.copy();
        sides.strafeSignPerTick = r.sides;
        ForwardPath again = MODEL.forward(sides, sides.toGameFacings(r.yaws));
        assertArrayEquals(r.path.posX, again.posX, 0.0);
        assertArrayEquals(r.path.posZ, again.posZ, 0.0);
        JumpPhysicsInputs oneSide = sc.copy();
        ForwardPath wrongKeys = MODEL.forward(oneSide, oneSide.toGameFacings(r.yaws));
        assertTrue("the shifted facings on the old side are a different jump",
                Math.abs(wrongKeys.posX[N] - r.path.posX[N]) > 0.05 || Math.abs(wrongKeys.posZ[N] - r.path.posZ[N]) > 0.05);
    }

    @Test
    public void smoothArcIsLeftAlone() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = new double[N];
        for (int t = 0; t < N; t++) yaws[t] = 16.0 * t;
        assertNull(realize(spec(sc, Collections.<JumpConstraint>emptyList()), yaws));
    }

    @Test
    public void flatPathIsLeftAlone() {
        JumpPhysicsInputs sc = sprintJump(1);
        assertNull(realize(spec(sc, Collections.<JumpConstraint>emptyList()), new double[N]));
        JumpSpec spec = spec(sc, Collections.<JumpConstraint>emptyList());
        double[] yaws = new double[N];
        Arrays.fill(yaws, 35.0);
        assertNull("a free start yaw makes every side a tie, the tie keeps the solved side",
                StrafeSideRealization.realize(MODEL, spec, yaws, forward(sc, yaws), true, 0.0, false));
    }

    @Test
    public void withoutStrafeTicksThereIsNothingToChoose() {
        JumpPhysicsInputs sc = sprintJump(1);
        sc.strafePerTick = new boolean[N];
        assertNull(realize(spec(sc, Collections.<JumpConstraint>emptyList()), flick(5, 60.0)));
    }

    @Test
    public void facingConstraintPinsItsTick() {
        JumpPhysicsInputs sc = sprintJump(1);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, 8, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, 50.0, "F8"));
        JumpSpec spec = spec(sc, cons);
        boolean[] free = StrafeSideRealization.flippable(spec, N);
        assertFalse(free[0]);
        assertFalse(free[8]);
        for (int t = 1; t < N; t++) {
            if (t != 8) assertTrue("tick " + t, free[t]);
        }
        assertNull("flipping around a pinned tick costs a 90 deg turn each way and never pays",
                realize(spec, flick(5, 60.0)));
    }

    @Test
    public void flickBackIsFlippedOnlyInBetween() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = new double[N];
        for (int t = 5; t < 9; t++) yaws[t] = 60.0;
        StrafeSideRealization r = realize(spec(sc, Collections.<JumpConstraint>emptyList()), yaws);
        assertNotNull(r);
        assertEquals(4, r.flipped);
        for (int t = 0; t < N; t++) assertEquals("side at " + t, t >= 5 && t < 9 ? -1 : 1, r.sides[t]);
        assertEquals(240.0, r.wiggleBefore, 1e-12);
        assertEquals(120.0, r.wiggleAfter, 1e-12);
    }

    @Test
    public void baseSideDFlipsOntoAWithAPlusNinetyShift() {
        JumpPhysicsInputs sc = sprintJump(-1);
        double[] yaws = flick(5, -60.0);
        StrafeSideRealization r = realize(spec(sc, Collections.<JumpConstraint>emptyList()), yaws);
        assertNotNull(r);
        assertEquals(-1, r.base);
        assertEquals(N - 5, r.flipped);
        for (int t = 5; t < N; t++) {
            assertEquals(1, r.sides[t]);
            assertEquals(30.0, r.yaws[t], 1e-12);
        }
        ForwardPath base = forward(sc, yaws);
        assertEquals(base.posX[N], r.path.posX[N], 1e-3);
        assertEquals(base.posZ[N], r.path.posZ[N], 1e-3);
    }

    @Test
    public void failedVerifyKeepsTheRealizationUnadopted() {
        JumpPhysicsInputs sc = sprintJump(1);
        double[] yaws = flick(5, 60.0);
        JumpSpec spec = spec(sc, Collections.<JumpConstraint>emptyList());
        StrafeSideRealization r = StrafeSideRealization.realize(MODEL, spec, yaws, forward(sc, yaws), false, -1.0, false);
        assertNotNull(r);
        assertFalse(r.verified);
        assertEquals(N - 5, r.flipped);
    }

    @Test
    public void dynamicProgramMatchesBruteForceOnRandomPaths() {
        Random rnd = new Random(338);
        int n = 10;
        for (int trial = 0; trial < 200; trial++) {
            double[] yaws = new double[n];
            boolean[] free = new boolean[n];
            double y = rnd.nextDouble() * 360.0 - 180.0;
            for (int t = 0; t < n; t++) {
                y += (rnd.nextDouble() - 0.5) * (rnd.nextInt(4) == 0 ? 240.0 : 20.0);
                yaws[t] = Angles.wrap(y);
                free[t] = rnd.nextInt(5) != 0;
            }
            int base = rnd.nextBoolean() ? 1 : -1;
            boolean freeStart = rnd.nextInt(4) == 0;
            double anchor = rnd.nextDouble() * 360.0 - 180.0;
            int[] sides = StrafeSideRealization.chooseSides(yaws, free, base, anchor, freeStart);
            double got = cost(yaws, sides, base, anchor, freeStart);
            int gotFlips = flips(sides, base);
            double best = Double.POSITIVE_INFINITY;
            int bestFlips = Integer.MAX_VALUE;
            for (int mask = 0; mask < (1 << n); mask++) {
                int[] cand = new int[n];
                boolean ok = true;
                for (int t = 0; t < n; t++) {
                    boolean flip = (mask >> t & 1) == 1;
                    if (flip && !free[t]) {
                        ok = false;
                        break;
                    }
                    cand[t] = flip ? -base : base;
                }
                if (!ok) continue;
                double c = cost(yaws, cand, base, anchor, freeStart);
                int f = flips(cand, base);
                if (c < best - StrafeSideRealization.TIE_EPS_DEG
                        || (Math.abs(c - best) <= StrafeSideRealization.TIE_EPS_DEG && f < bestFlips)) {
                    best = c;
                    bestFlips = f;
                }
            }
            assertEquals("trial " + trial, best, got, 1e-9);
            assertEquals("trial " + trial + " flips", bestFlips, gotFlips);
            for (int t = 0; t < n; t++) {
                if (!free[t]) assertEquals("trial " + trial + " pinned tick " + t, base, sides[t]);
            }
        }
    }

    private static double cost(double[] yaws, int[] sides, int base, double anchor, boolean freeStart) {
        double[] realized = StrafeSideRealization.realizedYaws(yaws, sides, base);
        return StrafeSideRealization.wiggle(realized, anchor, freeStart);
    }

    private static int flips(int[] sides, int base) {
        int f = 0;
        for (int s : sides) {
            if (s != base) f++;
        }
        return f;
    }
}
