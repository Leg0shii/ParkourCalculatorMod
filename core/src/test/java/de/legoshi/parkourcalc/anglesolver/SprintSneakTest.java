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
        return compile(inputs, boxes, numTicks, state, "1.8.9");
    }

    private static JumpPhysicsInputs compile(InputData inputs, BoxController boxes, int numTicks,
                                             AngleSolverState state, String mcVersion) {
        state.setDefaultInputs(AngleSolverState.InputMode.KEEP);
        state.setStartTick(0);
        state.setLandingTick(numTicks);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion(mcVersion));
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

    private static AngleSolverState deriving() {
        AngleSolverState state = new AngleSolverState();
        state.setDefaultSprint(AngleSolverState.SprintMode.DERIVE);
        return state;
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
        JumpPhysicsInputs sc = compile(inputs, boxes, 3, deriving());
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
        InputData inputs = rows(2);
        BoxController boxes = new BoxController();
        for (int i = 0; i < 3; i++) boxes.add(sampled(false, 0.0F, 0.0F));
        AngleSolverState state = deriving();
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
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, deriving());
        assertEquals(F, sc.forwardAt(0), 0.0F);
        assertEquals(-F, sc.forwardAt(1), 0.0F);
        assertTrue(sc.sprintAt(0));
        assertTrue(sc.sprintAt(1));
    }

    @Test
    public void sprintAlwaysOverridesTheSampledFlag() {
        // The default mode assumes sprint everywhere; only Derive reads the recorded flag. Inputs stay sampled.
        InputData inputs = rows(2);
        BoxController boxes = new BoxController();
        boxes.add(unsampled());
        boxes.add(sampled(false, F, 0.0F));
        boxes.add(sampled(false, SNEAK_F, 0.0F));
        AngleSolverState state = new AngleSolverState();
        assertEquals(AngleSolverState.SprintMode.ALWAYS, state.getDefaultSprint());
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, state);
        assertTrue(sc.sprintAt(0));
        assertTrue(sc.sprintAt(1));
        assertEquals("inputs still come from the sample", SNEAK_F, sc.forwardAt(1), 0.0F);
    }

    @Test
    public void perTickDeriveOverrideReadsTheSampleOnlyOnThatTick() {
        // Default ALWAYS, tick 1 overridden to Derive: tick 0 keeps the assumed sprint,
        // tick 1 reads its (lost) sampled flag.
        InputData inputs = rows(2);
        BoxController boxes = new BoxController();
        boxes.add(unsampled());
        boxes.add(sampled(false, F, 0.0F));
        boxes.add(sampled(false, F, 0.0F));
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(1).getOverride().setSprint(AngleSolverState.SprintMode.DERIVE);
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, state);
        assertTrue("ALWAYS tick keeps the assumed sprint", sc.sprintAt(0));
        assertFalse("Derive override reads the sampled flag", sc.sprintAt(1));
    }

    @Test
    public void perTickAlwaysOverrideForcesSprintOverTheSample() {
        // Default Derive, tick 1 overridden to Always: tick 0 reads its lost sample, tick 1 sprints.
        InputData inputs = rows(2);
        BoxController boxes = new BoxController();
        boxes.add(unsampled());
        boxes.add(sampled(false, F, 0.0F));
        boxes.add(sampled(false, F, 0.0F));
        AngleSolverState state = deriving();
        state.tickConstraints(1).getOverride().setSprint(AngleSolverState.SprintMode.ALWAYS);
        JumpPhysicsInputs sc = compile(inputs, boxes, 2, state);
        assertFalse("Derive default reads the sampled flag", sc.sprintAt(0));
        assertTrue("Always override forces sprint", sc.sprintAt(1));
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

    @Test
    public void airSprintFactorLagsByOneTick() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaws = {25.0, 25.0};

        JumpPhysicsInputs onset = scenario(2, Double.NaN, false);
        onset.sprintPerTick = new boolean[]{false, true};
        onset.incomingSprint = Boolean.FALSE;

        JumpPhysicsInputs never = scenario(2, Double.NaN, false);
        never.sprintPerTick = new boolean[]{false, false};
        never.incomingSprint = Boolean.FALSE;

        ForwardPath on = model.forward(onset, onset.toGameFacings(yaws));
        ForwardPath nv = model.forward(never, never.toGameFacings(yaws));
        for (int t = 0; t <= 2; t++) {
            assertEquals("tick 1's own sprint flag must not move tick 1 (the factor lags)", on.posX[t], nv.posX[t], 0.0);
            assertEquals(on.posZ[t], nv.posZ[t], 0.0);
        }

        JumpPhysicsInputs prior = scenario(2, Double.NaN, false);
        prior.sprintPerTick = new boolean[]{true, false};
        prior.incomingSprint = Boolean.FALSE;
        ForwardPath pr = model.forward(prior, prior.toGameFacings(yaws));
        assertEquals("tick 0 is unsprinted in both (seeded off)", on.posX[1], pr.posX[1], 0.0);
        assertEquals(on.posZ[1], pr.posZ[1], 0.0);
        assertTrue("tick 0's sprint is what drives tick 1's factor",
                Math.abs(pr.posZ[2] - on.posZ[2]) > 1.0E-9 || Math.abs(pr.posX[2] - on.posX[2]) > 1.0E-9);
    }

    @Test
    public void incomingSprintSeedsTheFirstTickFactor() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaws = {25.0};

        JumpPhysicsInputs seededOn = scenario(1, Double.NaN, false);
        seededOn.sprintPerTick = new boolean[]{false};
        seededOn.incomingSprint = Boolean.TRUE;

        JumpPhysicsInputs seededOff = scenario(1, Double.NaN, false);
        seededOff.sprintPerTick = new boolean[]{false};
        seededOff.incomingSprint = Boolean.FALSE;

        JumpPhysicsInputs unseeded = scenario(1, Double.NaN, false);
        unseeded.sprintPerTick = new boolean[]{false};

        ForwardPath on = model.forward(seededOn, seededOn.toGameFacings(yaws));
        ForwardPath off = model.forward(seededOff, seededOff.toGameFacings(yaws));
        ForwardPath nul = model.forward(unseeded, unseeded.toGameFacings(yaws));
        assertTrue("a window opened mid-sprint applies the air boost on tick 0",
                (on.posZ[1] - on.posZ[0]) > (off.posZ[1] - off.posZ[0]));
        assertEquals("a null seed falls back to this tick's own sprint", off.posZ[1], nul.posZ[1], 0.0);
    }

    @Test
    public void groundSpeedAmplifierLagsByOneTick() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaws = {0.0, 0.0};

        JumpPhysicsInputs onset = groundSprinting(2);
        onset.speedAmplifier = new int[]{0, 2};
        onset.incomingAmp = 0;

        JumpPhysicsInputs never = groundSprinting(2);
        never.speedAmplifier = new int[]{0, 0};
        never.incomingAmp = 0;

        ForwardPath on = model.forward(onset, onset.toGameFacings(yaws));
        ForwardPath nv = model.forward(never, never.toGameFacings(yaws));
        for (int t = 0; t <= 2; t++) {
            assertEquals("tick 1's own amplifier must not move tick 1 (the ground factor lags)", on.posX[t], nv.posX[t], 0.0);
            assertEquals(on.posZ[t], nv.posZ[t], 0.0);
        }

        JumpPhysicsInputs prior = groundSprinting(2);
        prior.speedAmplifier = new int[]{2, 0};
        prior.incomingAmp = 0;
        ForwardPath pr = model.forward(prior, prior.toGameFacings(yaws));
        assertEquals("tick 0 shares the seeded (level 0) amplifier", on.posX[1], pr.posX[1], 0.0);
        assertEquals(on.posZ[1], pr.posZ[1], 0.0);
        assertTrue("tick 0's amplifier is what drives tick 1's ground factor",
                Math.abs(pr.posZ[2] - on.posZ[2]) > 1.0E-9 || Math.abs(pr.posX[2] - on.posX[2]) > 1.0E-9);
    }

    @Test
    public void modernAirSprintFactorEngagesSameTick() {
        double[] yaws = {0.0};

        JumpPhysicsInputs engage = scenario(1, Double.NaN, false);
        engage.sprintPerTick = new boolean[]{true};
        engage.incomingSprint = Boolean.FALSE;

        ForwardPath m = ExactJumpModel.forMcVersion("26.2").forward(engage, engage.toGameFacings(yaws));
        assertEquals("modern reads the off-ground speed live: tick 0 already gets the sprint air accel",
                F * (double) Constants.AIR_SPEED_F, m.posZ[1] - m.posZ[0], 0.0);

        ForwardPath l = ExactJumpModel.forMcVersion("1.8.9").forward(engage, engage.toGameFacings(yaws));
        assertEquals("legacy keeps the lagged jumpMovementFactor on tick 0",
                (double) (F * Constants.AIR_SPEED_NO_SPRINT_F), l.posZ[1] - l.posZ[0], 0.0);
    }

    @Test
    public void modernAirSprintReleaseIsLiveToo() {
        double[] yaws = {0.0};

        JumpPhysicsInputs release = scenario(1, Double.NaN, false);
        release.sprintPerTick = new boolean[]{false};
        release.incomingSprint = Boolean.TRUE;

        ForwardPath m = ExactJumpModel.forMcVersion("26.2").forward(release, release.toGameFacings(yaws));
        assertEquals("modern drops to the non-sprint air accel the same tick",
                F * (double) Constants.AIR_SPEED_NO_SPRINT_F, m.posZ[1] - m.posZ[0], 0.0);

        ForwardPath l = ExactJumpModel.forMcVersion("1.8.9").forward(release, release.toGameFacings(yaws));
        assertEquals("legacy carries the sprint factor one tick past the release",
                (double) (F * Constants.AIR_SPEED_F), l.posZ[1] - l.posZ[0], 0.0);
    }

    @Test
    public void airFactorSprintAtFollowsTheLiveFlag() {
        JumpPhysicsInputs sc = scenario(2, Double.NaN, false);
        sc.sprintPerTick = new boolean[]{true, false};
        sc.incomingSprint = Boolean.FALSE;
        assertFalse(sc.airFactorSprintAt(0));
        assertTrue(sc.airFactorSprintAt(1));
        sc.liveAirSprintFactor = true;
        assertTrue(sc.airFactorSprintAt(0));
        assertFalse(sc.airFactorSprintAt(1));
    }

    @Test
    public void deriveSeedsAnAirborneSprintStartPerEra() {
        InputData inputs = rows(1);
        BoxController boxes = new BoxController();
        boxes.add(sampled(false, 0.0F, 0.0F));
        boxes.add(sampled(true, F, 0.0F));
        JumpPhysicsInputs mc = compile(inputs, boxes, 1, deriving(), "26.2");
        assertTrue("a modern engine authors the live air factor", mc.liveAirSprintFactor);
        assertTrue(mc.sprintAt(0));
        assertEquals(Boolean.FALSE, mc.incomingSprint);
        ForwardPath p = ExactJumpModel.forMcVersion("26.2").forward(mc, mc.toGameFacings(new double[]{0.0}));
        assertEquals("an airborne standstill start under Derive engages the sprint air accel on tick 0",
                F * (double) Constants.AIR_SPEED_F, p.posZ[1] - p.posZ[0], 0.0);

        InputData legacyInputs = rows(1);
        BoxController legacyBoxes = new BoxController();
        legacyBoxes.add(sampled(false, 0.0F, 0.0F));
        legacyBoxes.add(sampled(true, F, 0.0F));
        JumpPhysicsInputs lc = compile(legacyInputs, legacyBoxes, 1, deriving(), "1.8.9");
        assertFalse("a legacy engine keeps the lagged air factor", lc.liveAirSprintFactor);
    }

    @Test
    public void groundSprintFactorDoesNotLag() {
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaws = {25.0, 25.0};

        JumpPhysicsInputs onset = scenario(2, 0.6, false);
        onset.sprintPerTick = new boolean[]{false, true};
        onset.incomingSprint = Boolean.FALSE;

        JumpPhysicsInputs never = scenario(2, 0.6, false);
        never.sprintPerTick = new boolean[]{false, false};
        never.incomingSprint = Boolean.FALSE;

        ForwardPath on = model.forward(onset, onset.toGameFacings(yaws));
        ForwardPath nv = model.forward(never, never.toGameFacings(yaws));
        assertEquals("tick 0 is unsprinted in both", on.posX[1], nv.posX[1], 0.0);
        assertEquals(on.posZ[1], nv.posZ[1], 0.0);
        assertTrue("tick 1's own sprint drives tick 1's ground factor (no lag, unlike air)",
                Math.abs(on.posX[2] - nv.posX[2]) > 1.0E-9 || Math.abs(on.posZ[2] - nv.posZ[2]) > 1.0E-9);
    }

    /** n grounded ticks, sprint held steady (so the sprint factor stays constant and only the amplifier varies). */
    private static JumpPhysicsInputs groundSprinting(int n) {
        JumpPhysicsInputs sc = scenario(n, 0.6, false);
        sc.sprintPerTick = new boolean[n];
        for (int t = 0; t < n; t++) sc.sprintPerTick[t] = true;
        sc.incomingSprint = Boolean.TRUE;
        return sc;
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
