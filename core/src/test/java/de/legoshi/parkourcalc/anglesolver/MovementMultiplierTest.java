package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-102: the compiled spec reads the per-tick movement multiplier from the rows the user actually
 * set (W/S forward, A/D strafe) in Keep mode, and authors the W+A assumption on force-45 ticks
 * (W-only on the grounded jump tick). The byte-exact model coasts a no-input tick instead of
 * assuming W held.
 */
public class MovementMultiplierTest {

    private static final float F = 1.0F * 0.98F;

    /** Rows: 0 = no keys, 1 = W, 2 = W+A, 3 = S+D, 4 = W (force-45 override), 5 = W+JUMP (force-45 + ground). */
    private static JumpPhysicsInputs compile() {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < 6; t++) {
            inputs.getRows().add(new InputRow());
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        inputs.get(1).setKeyActive(InputRow.Key.W, true);
        inputs.get(2).setKeyActive(InputRow.Key.W, true);
        inputs.get(2).setKeyActive(InputRow.Key.A, true);
        inputs.get(3).setKeyActive(InputRow.Key.S, true);
        inputs.get(3).setKeyActive(InputRow.Key.D, true);
        inputs.get(4).setKeyActive(InputRow.Key.W, true);
        inputs.get(5).setKeyActive(InputRow.Key.W, true);
        inputs.get(5).setKeyActive(InputRow.Key.JUMP, true);

        AngleSolverState state = new AngleSolverState();
        state.setDefaultInputs(AngleSolverState.InputMode.KEEP);
        state.tickConstraints(4).getOverride().setInputs(AngleSolverState.InputMode.FORCE_45);
        state.tickConstraints(5).getOverride().setInputs(AngleSolverState.InputMode.FORCE_45);
        state.tickConstraints(5).getOverride().setSlipperiness(Slipperiness.DEFAULT); // grounded jump tick
        state.setStartTick(0);
        state.setLandingTick(6);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return spec.asScenario();
    }

    @Test
    public void keepModeReadsTheRowKeys() {
        JumpPhysicsInputs sc = compile();
        assertEquals("no keys coasts", 0.0F, sc.forwardAt(0), 0.0F);
        assertEquals(0.0F, sc.strafeInputAt(0), 0.0F);
        assertEquals("W runs forward", F, sc.forwardAt(1), 0.0F);
        assertEquals(0.0F, sc.strafeInputAt(1), 0.0F);
        assertEquals(F, sc.forwardAt(2), 0.0F);
        assertEquals("A strafes left (positive)", F, sc.strafeInputAt(2), 0.0F);
        assertEquals("S runs backward", -F, sc.forwardAt(3), 0.0F);
        assertEquals("D strafes right (negative)", -F, sc.strafeInputAt(3), 0.0F);
        assertFalse("keep ticks never get the force-45 strafe", sc.strafeAt(0) || sc.strafeAt(1)
                || sc.strafeAt(2) || sc.strafeAt(3));
    }

    @Test
    public void force45TicksCarryTheAssumption() {
        JumpPhysicsInputs sc = compile();
        assertEquals("force 45 assumes W regardless of rows", F, sc.forwardAt(4), 0.0F);
        assertEquals(0.0F, sc.strafeInputAt(4), 0.0F);
        assertTrue("force 45 strafes via the mask", sc.strafeAt(4));
        assertEquals("the grounded jump tick stays W-only", F, sc.forwardAt(5), 0.0F);
        assertEquals(0.0F, sc.strafeInputAt(5), 0.0F);
        assertFalse(sc.strafeAt(5));
    }

    @Test
    public void coastTickIgnoresTheFacing() {
        // A no-input tick adds no acceleration, so its facing cannot move the path.
        JumpPhysicsInputs sc = airScenario(3);
        sc.forwardInputPerTick = new float[]{F, 0.0F, F};
        sc.strafeInputPerTick = new float[3];
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        ForwardPath a = model.forward(sc, sc.toGameFacings(new double[]{180.0, 180.0, 180.0}));
        ForwardPath b = model.forward(sc, sc.toGameFacings(new double[]{180.0, 90.0, 180.0}));
        assertEquals("coast tick: same X no matter the facing", a.posX[2], b.posX[2], 0.0);
        assertEquals("coast tick: same Z no matter the facing", a.posZ[2], b.posZ[2], 0.0);
        assertTrue("the W ticks do move the path", Math.abs(a.posZ[1] - a.posZ[0]) > 0.0);
    }

    @Test
    public void unsetArraysKeepTheLegacyAssumption() {
        // Null input arrays must behave exactly like explicit all-W rows -- the fixtures and the
        // solver's internal scenarios rely on it.
        JumpPhysicsInputs legacy = airScenario(4);
        JumpPhysicsInputs explicit = airScenario(4);
        explicit.forwardInputPerTick = new float[]{F, F, F, F};
        explicit.strafeInputPerTick = new float[4];
        double[] yaws = {170.0, -160.0, 150.0, -140.0};
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        ForwardPath a = model.forward(legacy, legacy.toGameFacings(yaws));
        ForwardPath b = model.forward(explicit, explicit.toGameFacings(yaws));
        for (int t = 0; t <= 4; t++) {
            assertEquals(a.posX[t], b.posX[t], 0.0);
            assertEquals(a.posZ[t], b.posZ[t], 0.0);
        }
    }

    private static JumpPhysicsInputs airScenario(int n) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startYaw = 180f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[n];
        sc.strafePerTick = new boolean[n];
        sc.speedAmplifier = new int[n];
        sc.slipPerTick = new double[n];
        for (int t = 0; t < n; t++) sc.slipPerTick[t] = Double.NaN;
        sc.yawLockedPerTick = new boolean[n];
        sc.initialVelocity = new Vec3dCore(0.0, 0.0, -0.2);
        return sc;
    }
}
