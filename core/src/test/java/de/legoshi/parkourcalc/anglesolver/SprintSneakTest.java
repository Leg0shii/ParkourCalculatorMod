package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
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
 * gh-120: Keep ticks run what the sim actually ran. The compiled spec samples the recorded
 * trajectory's post-tick movement state (sprint flag + the moveFlying inputs, version-exact sneak
 * scaling included) instead of rederiving sprint/sneak from the rows; the model gates the ground
 * 1.3x attribute, the air-accel constant and the 0.2 jump boost on the per-tick sprint flag. Null
 * masks keep the legacy always-sprinting assumption, so the fixtures stay bit-identical.
 */
public class SprintSneakTest {

    private static final float F = 1.0F * 0.98F;
    private static final float SNEAK_F = 0.29400003F; // a recorded sneak-tick forward sample

    private static JumpPhysicsInputs compile(InputData inputs, BoxController boxes, int numTicks,
                                             AngleSolverState state) {
        state.setDefaultInputs(AngleSolverState.InputMode.KEEP);
        state.setStartTick(0);
        state.setLandingTick(numTicks);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return spec.asScenario();
    }

    private static InputData rows(int n) {
        InputData inputs = new InputData();
        for (int t = 0; t < n; t++) inputs.getRows().add(new InputRow());
        return inputs;
    }

    private static TickState sampled(boolean sprinting, float moveForward, float moveStrafe) {
        return new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN,
                sprinting, moveForward, moveStrafe);
    }

    private static TickState unsampled() {
        return new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, false, 0f,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    @Test
    public void keepTicksReadTheSampledEntityState() {
        // Recorded run: tick 0 sprints at full W, tick 1 lost sprint and ran a sneak-scaled input,
        // tick 2 coasted. Tick t's run is sampled into state t+1.
        InputData inputs = rows(3);
        BoxController boxes = new BoxController();
        boxes.add(unsampled());                       // state 0: the seed, never sampled for inputs
        boxes.add(sampled(true, F, 0.0F));            // after tick 0
        boxes.add(sampled(false, SNEAK_F, -0.1F));    // after tick 1
        boxes.add(sampled(false, 0.0F, 0.0F));        // after tick 2
        JumpPhysicsInputs sc = compile(inputs, boxes, 3, new AngleSolverState());
        assertTrue(sc.sprintAt(0));
        assertEquals(F, sc.forwardAt(0), 0.0F);
        assertFalse("sprint flag comes from the entity, not the rows", sc.sprintAt(1));
        assertEquals("the sampled input already carries the sneak scaling", SNEAK_F, sc.forwardAt(1), 0.0F);
        assertEquals(-0.1F, sc.strafeInputAt(1), 0.0F);
        assertFalse(sc.sprintAt(2));
        assertEquals(0.0F, sc.forwardAt(2), 0.0F);
    }

    @Test
    public void force45TicksKeepTheAssumptionOverTheSample() {
        // The recorded run was not sprinting, but force-45 ticks author W + sprint regardless.
        InputData inputs = rows(2);
        BoxController boxes = new BoxController();
        for (int i = 0; i < 3; i++) boxes.add(sampled(false, 0.0F, 0.0F));
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(0).getOverride().setInputs(AngleSolverState.InputMode.FORCE_45);
        state.tickConstraints(1).getOverride().setInputs(AngleSolverState.InputMode.FORCE_45);
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, state);
        assertTrue(sc.sprintAt(0));
        assertTrue(sc.sprintAt(1));
        assertEquals(F, sc.forwardAt(0), 0.0F);
    }

    @Test
    public void unsampledStatesFallBackToKeysAndLegacySprint() {
        // Old recordings carry no movement sample: keys author the inputs (gh-102), sprint stays assumed.
        InputData inputs = rows(2);
        inputs.get(0).setKeyActive(InputRow.Key.W, true);
        inputs.get(1).setKeyActive(InputRow.Key.S, true);
        BoxController boxes = new BoxController();
        for (int i = 0; i < 3; i++) boxes.add(unsampled());
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, new AngleSolverState());
        assertEquals(F, sc.forwardAt(0), 0.0F);
        assertEquals(-F, sc.forwardAt(1), 0.0F);
        assertTrue(sc.sprintAt(0));
        assertTrue(sc.sprintAt(1));
    }

    @Test
    public void sprintGatesAirAccelGroundAttrAndJumpBoost() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        // Air tick, W held, facing 0: the Z gain is the forward input times the air-accel constant.
        JumpPhysicsInputs air = scenario(1, Double.NaN, false);
        air.sprintPerTick = new boolean[]{false};
        ForwardPath a = model.forward(air, air.toGameFacings(new double[]{0.0}));
        assertEquals("air accel without sprint is 0.02", (double) (F * Constants.AIR_SPEED_NO_SPRINT_F),
                a.posZ[1] - a.posZ[0], 0.0);

        // Grounded jump tick without sprint: ground accel at the unsprinted attribute, and no 0.2 boost.
        JumpPhysicsInputs jump = scenario(1, 0.6, true);
        jump.sprintPerTick = new boolean[]{false};
        ForwardPath j = model.forward(jump, jump.toGameFacings(new double[]{0.0}));
        float f4 = 0.6F * 0.91F;
        float accel = Constants.attrValueF(0, false) * (0.16277136F / (f4 * f4 * f4));
        assertEquals("no boost, unsprinted ground accel", (double) (F * accel), j.posZ[1] - j.posZ[0], 0.0);
        assertTrue("the boost would dominate this displacement", j.posZ[1] - j.posZ[0] < 0.19);
    }

    @Test
    public void nullSprintMaskKeepsTheLegacyAssumption() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaws = {170.0, -160.0, 150.0};
        JumpPhysicsInputs legacy = scenario(3, Double.NaN, false);
        JumpPhysicsInputs explicit = scenario(3, Double.NaN, false);
        explicit.sprintPerTick = new boolean[]{true, true, true};
        ForwardPath a = model.forward(legacy, legacy.toGameFacings(yaws));
        ForwardPath b = model.forward(explicit, explicit.toGameFacings(yaws));
        for (int t = 0; t <= 3; t++) {
            assertEquals(a.posX[t], b.posX[t], 0.0);
            assertEquals(a.posZ[t], b.posZ[t], 0.0);
        }
    }

    /** n ticks, all at the given slip (NaN = air), W held, optional jump press on every tick. */
    private static JumpPhysicsInputs scenario(int n, double slip, boolean jump) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startYaw = 0f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[n];
        sc.strafePerTick = new boolean[n];
        sc.speedAmplifier = new int[n];
        sc.slipPerTick = new double[n];
        sc.yawLockedPerTick = new boolean[n];
        sc.forwardInputPerTick = new float[n];
        sc.strafeInputPerTick = new float[n];
        for (int t = 0; t < n; t++) {
            sc.slipPerTick[t] = slip;
            sc.jumpPerTick[t] = jump;
            sc.forwardInputPerTick[t] = F;
        }
        sc.initialVelocity = Vec3dCore.ZERO;
        return sc;
    }
}
