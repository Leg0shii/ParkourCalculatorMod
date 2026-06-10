package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothingPolish;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * gh-99: underdetermined solves wiggle. The smoothing pass must iron the free ticks toward their
 * neighbors while never crossing a wall and never giving back any of the achieved objective --
 * where every tick is load-bearing it must be an exact no-op.
 */
public class SmoothingPolishTest {

    private static final int N = 8;
    private static final ForwardModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    /** All-air, W-held segment from rest at facing 180 (running -Z). */
    private static JumpPhysicsInputs airScenario() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.startYaw = 180f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[N];
        sc.strafePerTick = new boolean[N];
        sc.speedAmplifier = new int[N];
        sc.slipPerTick = new double[N];
        for (int t = 0; t < N; t++) sc.slipPerTick[t] = Double.NaN;
        sc.yawLockedPerTick = new boolean[N];
        return sc;
    }

    private static double zAt(JumpPhysicsInputs sc, double[] absWrapped) {
        return MODEL.forward(sc, sc.toGameFacings(absWrapped)).getPos(N, JumpPhysicsInputs.Axis.Z);
    }

    /** Facings alternating around the back axis: feasible but maximally wiggly. */
    private static double[] zigzag(double amplitudeDeg) {
        double[] y = new double[N];
        for (int k = 0; k < N; k++) y[k] = Angles.wrap(k % 2 == 0 ? amplitudeDeg : -amplitudeDeg);
        return y;
    }

    private static double maxAdjacentTurn(double[] y) {
        double m = 0.0;
        for (int k = 1; k < y.length; k++) m = Math.max(m, Math.abs(Angles.wrapDelta(y[k] - y[k - 1])));
        return m;
    }

    @Test
    public void underdeterminedZigzagSmoothsWithoutClippingOrGivingBackObjective() {
        JumpPhysicsInputs sc = airScenario();
        // Put the wall at 3/4 of the straight-run depth: the zigzag (~half the depth) starts
        // feasible, and the unconstrained optimum (straight 180) would clip it, so the wall binds
        // and leaves the facings slack to distribute.
        double[] straight = new double[N];
        java.util.Arrays.fill(straight, 180.0);
        double z0 = sc.startPos.z;
        double wall = z0 + (zAt(sc, straight) - z0) * 0.75;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, wall, "zwall"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        double[] start = zigzag(120.0);
        double startZ = zAt(sc, start);
        assertTrue("zigzag start must be feasible", startZ >= wall);
        assertTrue("the wall must bind the straight run", zAt(sc, straight) < wall);

        double startRough = SmoothingPolish.roughness(sc.startYaw, start);
        double[] smoothed = SmoothingPolish.smooth(MODEL, spec, start.clone(), new AtomicBoolean(false));

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(smoothed);
        assertEquals("smoothing must stay strictly feasible",
                0.0, compiled.maxViolation(gf, MODEL.forward(sc, gf)), 0.0);
        assertTrue("objective must be kept or improved (MIN Z)", zAt(sc, smoothed) <= startZ);
        double smoothedRough = SmoothingPolish.roughness(sc.startYaw, smoothed);
        assertTrue("roughness should collapse (was " + startRough + ", now " + smoothedRough + ")",
                smoothedRough < startRough / 4.0);
        assertTrue("the 240deg zigzag turns should be gone (max now " + maxAdjacentTurn(smoothed) + ")",
                maxAdjacentTurn(smoothed) < 60.0);
    }

    @Test
    public void infeasibleStartIsLeftUntouched() {
        JumpPhysicsInputs sc = airScenario();
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, sc.startPos.z + 5.0, "unreachable"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        double[] start = zigzag(120.0);
        double[] out = SmoothingPolish.smooth(MODEL, spec, start, new AtomicBoolean(false));
        assertArrayEquals("a failed solve must pass through unchanged for honest reporting",
                start, out, 0.0);
    }

    @Test
    public void anchorPullNeverTradesObjective() {
        // A straight all-90 run (facing -X) under MIN X: the only roughness reduction left is
        // bending the head toward the 180 launch anchor, and every such pull makes the objective
        // strictly worse. The floor must block it, bit for bit.
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = new JumpSpec(sc, new ArrayList<JumpConstraint>(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, N));

        double[] start = new double[N];
        java.util.Arrays.fill(start, 90.0);
        double[] out = SmoothingPolish.smooth(MODEL, spec, start.clone(), new AtomicBoolean(false));
        assertArrayEquals("no smoothing may trade objective for looks", start, out, 0.0);
    }
}
