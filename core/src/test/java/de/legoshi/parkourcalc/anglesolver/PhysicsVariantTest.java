package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PhysicsVariantTest {

    private static final PhysicsVariant NO_BOOST = new PhysicsVariant(null, null, false, "test");

    private static JumpPhysicsInputs groundTick(float forward, float strafe, boolean force45) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(0.0, 64.0, 0.0);
        sc.startYaw = 0.0F;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[] {false};
        sc.slipPerTick = new double[] {0.6};
        sc.sprintPerTick = new boolean[] {true};
        sc.forwardInputPerTick = new float[] {forward};
        sc.strafeInputPerTick = new float[] {strafe};
        sc.strafePerTick = new boolean[] {force45};
        return sc;
    }

    private static double speed(ExactJumpModel model, JumpPhysicsInputs sc) {
        ForwardPath p = model.forward(sc, new double[] {0.0});
        double dx = p.posX[1] - p.posX[0];
        double dz = p.posZ[1] - p.posZ[0];
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Test
    public void vanillaVariantIsTheVersionModel() {
        ExactJumpModel base = ExactJumpModel.forMcVersion("26.3");
        assertSame(base, base.withVariant(PhysicsVariant.VANILLA));
        assertSame(base, base.withVariant(null));
        assertTrue(base.strafeBoost());
        assertTrue(PhysicsVariant.VANILLA.isVanilla());
        assertFalse(NO_BOOST.isVanilla());
    }

    @Test
    public void typedDiagonalIsAsFastAsForwardWithoutTheBoost() {
        ExactJumpModel vanilla = ExactJumpModel.forMcVersion("26.3");
        ExactJumpModel noBoost = ExactJumpModel.forMcVersion("26.3", NO_BOOST);
        double forwardOnly = speed(vanilla, groundTick(0.98F, 0.0F, false));
        assertEquals(forwardOnly, speed(noBoost, groundTick(0.98F, 0.0F, false)), 0.0);
        double diagonalVanilla = speed(vanilla, groundTick(0.98F, 0.98F, false));
        double diagonalNoBoost = speed(noBoost, groundTick(0.98F, 0.98F, false));
        assertEquals(forwardOnly / 0.98, diagonalVanilla, forwardOnly * 1e-6);
        assertEquals(forwardOnly, diagonalNoBoost, forwardOnly * 1e-6);
        assertTrue(diagonalVanilla > diagonalNoBoost);
    }

    @Test
    public void legacyFloatChainLosesTheBoostTheSameWay() {
        PhysicsVariant bpk157 = new PhysicsVariant(1.0E-4, true, false, "BPKMod");
        ExactJumpModel vanilla = ExactJumpModel.forMcVersion("1.12.2");
        ExactJumpModel noBoost = ExactJumpModel.forMcVersion("1.12.2", bpk157);
        assertFalse(vanilla.modern());
        assertFalse(noBoost.modern());
        assertFalse(noBoost.strafeBoost());
        assertEquals(0.003, vanilla.inertiaThreshold(), 0.0);
        assertEquals(1.0E-4, noBoost.inertiaThreshold(), 0.0);
        assertTrue(noBoost.perAxisInertia());
        double forwardOnly = speed(vanilla, groundTick(0.98F, 0.0F, false));
        assertEquals(forwardOnly, speed(noBoost, groundTick(0.98F, 0.0F, false)), 0.0);
        assertEquals(forwardOnly / 0.98, speed(vanilla, groundTick(0.98F, 0.98F, false)), forwardOnly * 1e-6);
        assertEquals(forwardOnly, speed(noBoost, groundTick(0.98F, 0.98F, false)), forwardOnly * 1e-6);
        assertEquals(forwardOnly, speed(noBoost, groundTick(0.98F, 0.0F, true)), forwardOnly * 1e-6);
        float raw = ExactJumpModel.rawDiagonalInput();
        assertEquals(speed(noBoost, groundTick(0.98F, 0.98F, false)), speed(noBoost, groundTick(raw, raw, false)), 0.0);
    }

    @Test
    public void forced45TickLosesTheBoostToo() {
        ExactJumpModel vanilla = ExactJumpModel.forMcVersion("26.3");
        ExactJumpModel noBoost = ExactJumpModel.forMcVersion("26.3", NO_BOOST);
        double forwardOnly = speed(vanilla, groundTick(0.98F, 0.0F, false));
        assertEquals(forwardOnly / 0.98, speed(vanilla, groundTick(0.98F, 0.0F, true)), forwardOnly * 1e-6);
        assertEquals(forwardOnly, speed(noBoost, groundTick(0.98F, 0.0F, true)), forwardOnly * 1e-6);
    }

    @Test
    public void sampledDiagonalInputIsLeftAlone() {
        ExactJumpModel noBoost = ExactJumpModel.forMcVersion("26.3", NO_BOOST);
        float raw = ExactJumpModel.rawDiagonalInput();
        float sneak = raw * 0.3F;
        double sneakDiagonal = speed(noBoost, groundTick(sneak, sneak, false));
        double walkDiagonal = speed(noBoost, groundTick(raw, raw, false));
        assertEquals(0.3, sneakDiagonal / walkDiagonal, 1e-6);
    }

    @Test
    public void inertiaOverrideReplacesTheVersionGate() {
        ExactJumpModel vanilla = ExactJumpModel.forMcVersion("26.3");
        ExactJumpModel noInertia = ExactJumpModel.forMcVersion("26.3", new PhysicsVariant(0.0, true, true, "test"));
        ExactJumpModel perAxis = ExactJumpModel.forMcVersion("26.3", new PhysicsVariant(1.0E-4, true, true, "test"));
        assertEquals(0.003, vanilla.inertiaThreshold(), 0.0);
        assertFalse(vanilla.perAxisInertia());
        assertEquals(0.0, noInertia.inertiaThreshold(), 0.0);
        assertTrue(noInertia.perAxisInertia());
        assertEquals(1.0E-4, perAxis.inertiaThreshold(), 0.0);

        Vec3dCore tiny = new Vec3dCore(2.0E-4, 2.0E-4, 2.0E-4);
        assertEquals(tiny, noInertia.zeroSubThresholdVelocity(tiny));
        assertEquals(tiny, perAxis.zeroSubThresholdVelocity(tiny));
        assertEquals(Vec3dCore.ZERO.x, vanilla.zeroSubThresholdVelocity(tiny).x, 0.0);

        Vec3dCore smaller = new Vec3dCore(5.0E-5, 0.0, 5.0E-5);
        assertEquals(0.0, perAxis.zeroSubThresholdVelocity(smaller).x, 0.0);
        assertEquals(5.0E-5, noInertia.zeroSubThresholdVelocity(smaller).x, 0.0);
    }

    @Test
    public void scenarioCopiesCarryTheFlag() {
        JumpPhysicsInputs sc = groundTick(0.98F, 0.0F, false);
        sc.strafeBoost = false;
        assertFalse(sc.copy().strafeBoost);
        assertFalse(sc.slice(0, 0.0, 64.0, 0.0, 0.0, 0.0, 0.0, 0.0F).strafeBoost);
        assertTrue(groundTick(0.98F, 0.0F, false).strafeBoost);
    }

    @Test
    public void describeNamesTheSource() {
        assertEquals("vanilla physics", PhysicsVariant.VANILLA.describe());
        assertEquals("BPKMod: inertia off, 45 strafe off",
                new PhysicsVariant(0.0, true, false, "BPKMod").describe());
        assertEquals("BPKMod: inertia threshold 1.0E-4 per axis, 45 strafe on",
                new PhysicsVariant(1.0E-4, true, true, "BPKMod").describe());
    }
}
