package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SprintJumpBoostEraTest {

    private static final double YAW = 106.5729777858163;

    private static JumpPhysicsInputs sprintJumpFromRest() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(2.764828765972325, -60.0, -0.3976251780293907);
        sc.initialVelocity = new Vec3dCore(0.0, -0.0784000015258789, 0.0);
        sc.startYaw = (float) YAW;
        sc.jumpTick = 0;
        sc.jumpPerTick = new boolean[]{true};
        sc.strafePerTick = new boolean[]{false};
        sc.speedAmplifier = new int[]{0};
        sc.slipPerTick = new double[]{0.6};
        sc.sprintPerTick = new boolean[]{true};
        sc.yawLockedPerTick = new boolean[]{false};
        return sc;
    }

    private static double boostX(double yaw, boolean floatProduct) {
        float fj = (float) yaw * (float) (Math.PI / 180.0);
        float s = McSineTable.sinStep(fj);
        return floatProduct ? -(double) (s * 0.2F) : -s * 0.2;
    }

    @Test
    public void mc1204AndMc1213DifferByTheFloatBoostOnTheJumpTick() {
        JumpPhysicsInputs sc = sprintJumpFromRest();
        double[] yaws = {YAW};
        ForwardPath p1204 = ExactJumpModel.forMcVersion("1.20.4").forward(sc, yaws);
        ForwardPath p1213 = ExactJumpModel.forMcVersion("1.21.3").forward(sc, yaws);
        double dx = p1204.posX[1] - p1213.posX[1];
        double expected = boostX(YAW, true) - boostX(YAW, false);
        assertNotEquals(0.0, expected, 0.0);
        assertTrue("boost gap must exceed the apply tolerance", Math.abs(expected) > 1.0e-9);
        assertEquals(expected, dx, 1.0e-15);
    }

    @Test
    public void explicitFlagMatchesTheVersionRule() {
        JumpPhysicsInputs sc = sprintJumpFromRest();
        double[] yaws = {YAW};
        ForwardPath byVersion = ExactJumpModel.forMcVersion("1.20.4").forward(sc, yaws);
        ForwardPath byFlag = new ExactJumpModel(0.003, true, true, false, false, true).forward(sc, yaws);
        ForwardPath modern = new ExactJumpModel(0.003, true, true, false).forward(sc, yaws);
        assertEquals(byFlag.posX[1], byVersion.posX[1], 0.0);
        assertEquals(byFlag.posZ[1], byVersion.posZ[1], 0.0);
        assertEquals(modern.posX[1], ExactJumpModel.forMcVersion("1.21.3").forward(sc, yaws).posX[1], 0.0);
    }
}
