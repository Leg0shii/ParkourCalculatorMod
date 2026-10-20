package de.legoshi.parkourcalc.forge.core.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSprintMachineBpkTest {

    private static final float STANDING = 1.8F;
    private static final float SNEAK_POSE = 1.65F;
    private static final float DIAGONAL = 1.0F / (float) Math.sqrt((double) 2.0F);

    private static PlayerSprintMachine.Rules bpk(boolean normalise, boolean nonSprintingStrafe, boolean sprintBackward,
                                                 boolean ignoreWall, boolean groundDelay, boolean bedrockSneak) {
        return new PlayerSprintMachine.Rules(true, normalise, nonSprintingStrafe, sprintBackward, ignoreWall,
                groundDelay, bedrockSneak, bedrockSneak ? 1.5F : SNEAK_POSE);
    }

    private static final PlayerSprintMachine.Rules DEFAULTS = bpk(true, false, false, true, false, false);

    private static PlayerSprintMachine.Inputs keys(boolean w, boolean s, boolean a, boolean d, boolean sneak,
                                                   boolean sprintKey, boolean onGround, boolean wall, float height) {
        return new PlayerSprintMachine.Inputs(w, s, a, d, false, sneak, sprintKey, onGround, false, false, wall,
                false, false, 20.0F, height, false, false);
    }

    private static PlayerSprintMachine.State sprinting() {
        return new PlayerSprintMachine.State(false, 1.0F, 0, 600, true);
    }

    private static void assertSame(PlayerSprintMachine.Outputs a, PlayerSprintMachine.Outputs b) {
        assertEquals(a.moveForward, b.moveForward, 0.0F);
        assertEquals(a.moveStrafe, b.moveStrafe, 0.0F);
        assertEquals(a.isJumping, b.isJumping);
        assertEquals(a.next.isSprinting, b.next.isSprinting);
        assertEquals(a.next.sprintToggleTimer, b.next.sprintToggleTimer);
        assertEquals(a.next.sprintingTicksLeft, b.next.sprintingTicksLeft);
        assertEquals(a.next.prevMoveForward, b.next.prevMoveForward, 0.0F);
        assertEquals(a.next.prevSneak, b.next.prevSneak);
    }

    @Test
    public void vanillaRulesAreTheTwoArgumentTick() {
        PlayerSprintMachine.State initial = PlayerSprintMachine.State.initial();
        PlayerSprintMachine.Inputs[] cases = {
                keys(true, false, false, false, false, true, true, false, STANDING),
                keys(true, false, true, false, false, true, true, false, STANDING),
                keys(true, false, false, false, true, true, true, false, SNEAK_POSE),
                keys(true, false, false, false, false, false, true, true, STANDING),
                keys(false, true, false, false, false, true, true, false, STANDING),
        };
        for (PlayerSprintMachine.Inputs in : cases) {
            assertSame(PlayerSprintMachine.tick(in, initial), PlayerSprintMachine.tick(in, initial, PlayerSprintMachine.Rules.VANILLA));
            assertSame(PlayerSprintMachine.tick(in, sprinting()), PlayerSprintMachine.tick(in, sprinting(), null));
            PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, initial);
            assertEquals(in.sneak, out.sneaking);
            assertFalse(out.dropSprintBoost);
        }
    }

    @Test
    public void normalisedDiagonalKeepsItsMagnitudeAndStillSprints() {
        PlayerSprintMachine.Inputs in = keys(true, false, true, false, false, true, true, false, STANDING);
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, PlayerSprintMachine.State.initial(), DEFAULTS);
        assertEquals(DIAGONAL, out.moveForward, 0.0F);
        assertEquals(DIAGONAL, out.moveStrafe, 0.0F);
        assertTrue(out.next.isSprinting);
        assertEquals(DIAGONAL, out.next.prevMoveForward, 0.0F);

        PlayerSprintMachine.Outputs sneak = PlayerSprintMachine.tick(
                keys(true, false, true, false, true, false, true, false, STANDING), PlayerSprintMachine.State.initial(), DEFAULTS);
        assertEquals((float) ((double) DIAGONAL * 0.3D), sneak.moveForward, 0.0F);
        assertEquals((float) ((double) DIAGONAL * 0.3D), sneak.moveStrafe, 0.0F);

        PlayerSprintMachine.Outputs boosted = PlayerSprintMachine.tick(in, PlayerSprintMachine.State.initial(),
                bpk(false, false, false, true, false, false));
        assertEquals(1.0F, boosted.moveForward, 0.0F);
        assertEquals(1.0F, boosted.moveStrafe, 0.0F);
    }

    @Test
    public void anyForwardImpulseSustainsSprintUnderBpk() {
        PlayerSprintMachine.Inputs sneakW = keys(true, false, false, false, true, true, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(sneakW, sprinting()).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(sneakW, sprinting(), DEFAULTS).next.isSprinting);
        PlayerSprintMachine.Inputs released = keys(false, false, false, false, false, true, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(released, sprinting(), DEFAULTS).next.isSprinting);
    }

    @Test
    public void nonSprintingStrafeNeedsPureForwardToStart() {
        PlayerSprintMachine.Rules rules = bpk(true, true, false, true, false, false);
        PlayerSprintMachine.Inputs diagonal = keys(true, false, true, false, false, true, true, false, STANDING);
        PlayerSprintMachine.Inputs forward = keys(true, false, false, false, false, true, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(diagonal, PlayerSprintMachine.State.initial(), rules).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(forward, PlayerSprintMachine.State.initial(), rules).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(diagonal, sprinting(), rules).next.isSprinting);

        PlayerSprintMachine.State doubleTap = new PlayerSprintMachine.State(false, 0.0F, 5, 0, false);
        PlayerSprintMachine.Inputs diagonalNoKey = keys(true, false, true, false, false, false, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(diagonalNoKey, doubleTap, rules).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(diagonalNoKey, doubleTap, DEFAULTS).next.isSprinting);
    }

    @Test
    public void wallsStopSprintOnlyWhenBpkSaysSo() {
        PlayerSprintMachine.Inputs intoWall = keys(true, false, false, false, false, true, true, true, STANDING);
        assertFalse(PlayerSprintMachine.tick(intoWall, sprinting()).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(intoWall, sprinting(), DEFAULTS).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(intoWall, sprinting(), bpk(true, false, false, false, false, false)).next.isSprinting);
    }

    @Test
    public void sneakPoseBlocksTheSprintKeyAndStopsSprint() {
        PlayerSprintMachine.Inputs posed = keys(true, false, false, false, false, true, true, false, SNEAK_POSE);
        PlayerSprintMachine.Inputs standing = keys(true, false, false, false, false, true, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(posed, PlayerSprintMachine.State.initial(), DEFAULTS).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(standing, PlayerSprintMachine.State.initial(), DEFAULTS).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(posed, sprinting(), DEFAULTS).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(posed, PlayerSprintMachine.State.initial()).next.isSprinting);

        PlayerSprintMachine.State doubleTap = new PlayerSprintMachine.State(false, 0.0F, 5, 0, false);
        PlayerSprintMachine.Inputs posedNoKey = keys(true, false, false, false, false, false, true, false, SNEAK_POSE);
        assertFalse(PlayerSprintMachine.tick(posedNoKey, doubleTap, DEFAULTS).next.isSprinting);
    }

    @Test
    public void sprintBackwardKeepsSprintWhileMovingUnlessSneaking() {
        PlayerSprintMachine.Rules rules = bpk(true, false, true, true, false, false);
        PlayerSprintMachine.Inputs back = keys(false, true, false, false, false, false, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(back, sprinting()).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(back, sprinting(), DEFAULTS).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(back, sprinting(), rules).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(back, PlayerSprintMachine.State.initial(), rules).next.isSprinting);
        PlayerSprintMachine.Inputs sneakBack = keys(false, true, false, false, true, false, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(sneakBack, sprinting(), rules).next.isSprinting);
        PlayerSprintMachine.Inputs still = keys(false, false, false, false, false, false, true, false, STANDING);
        assertFalse(PlayerSprintMachine.tick(still, sprinting(), rules).next.isSprinting);
    }

    @Test
    public void groundSprintDelayFlagsOnlyTheStartTick() {
        PlayerSprintMachine.Rules rules = bpk(true, false, false, true, true, false);
        PlayerSprintMachine.Inputs forward = keys(true, false, false, false, false, true, true, false, STANDING);
        PlayerSprintMachine.Outputs start = PlayerSprintMachine.tick(forward, PlayerSprintMachine.State.initial(), rules);
        assertTrue(start.next.isSprinting);
        assertTrue(start.dropSprintBoost);
        PlayerSprintMachine.Outputs running = PlayerSprintMachine.tick(forward, start.next, rules);
        assertTrue(running.next.isSprinting);
        assertFalse(running.dropSprintBoost);
        assertFalse(PlayerSprintMachine.tick(forward, PlayerSprintMachine.State.initial(), DEFAULTS).dropSprintBoost);
    }

    @Test
    public void bedrockSneakForcesTheSneakAndSlowsThePose() {
        PlayerSprintMachine.Rules rules = bpk(true, false, false, true, false, true);
        PlayerSprintMachine.Inputs forced = new PlayerSprintMachine.Inputs(true, false, false, false, false, false, false,
                true, false, false, false, false, false, 20.0F, 1.5F, false, true);
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(forced, PlayerSprintMachine.State.initial(), rules);
        assertTrue(out.sneaking);
        assertEquals(1.0F * 0.3F, out.moveForward, 0.0F);
        assertFalse(out.next.prevSneak);

        PlayerSprintMachine.Inputs flying = new PlayerSprintMachine.Inputs(true, false, false, false, false, false, false,
                true, false, false, false, false, true, 20.0F, 1.5F, true, true);
        assertEquals(1.0F, PlayerSprintMachine.tick(flying, PlayerSprintMachine.State.initial(), rules).moveForward, 0.0F);

        PlayerSprintMachine.Inputs standing = new PlayerSprintMachine.Inputs(true, false, false, false, false, false, false,
                true, false, false, false, false, false, 20.0F, STANDING, false, false);
        PlayerSprintMachine.Outputs tall = PlayerSprintMachine.tick(standing, PlayerSprintMachine.State.initial(), rules);
        assertFalse(tall.sneaking);
        assertEquals(1.0F, tall.moveForward, 0.0F);

        PlayerSprintMachine.Inputs sneakBackForced = new PlayerSprintMachine.Inputs(false, true, false, false, false, false, false,
                true, false, false, false, false, false, 20.0F, 1.5F, false, true);
        assertFalse(PlayerSprintMachine.tick(sneakBackForced, sprinting(), bpk(true, false, true, true, false, true)).next.isSprinting);
    }

    @Test
    public void heldForwardFollowsTheSameScaling() {
        assertEquals(1.0F, PlayerSprintMachine.heldForward(true, false, true, false, false, null), 0.0F);
        assertEquals(DIAGONAL, PlayerSprintMachine.heldForward(true, false, true, false, false, DEFAULTS), 0.0F);
        assertEquals((float) ((double) DIAGONAL * 0.3D), PlayerSprintMachine.heldForward(true, false, false, true, true, DEFAULTS), 0.0F);
        assertEquals(-1.0F, PlayerSprintMachine.heldForward(false, true, false, false, false, DEFAULTS), 0.0F);
    }

    @Test
    public void playbackInputNormalisesLikeBpk() {
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.W, true);
        row.setKeyActive(InputRow.Key.A, true);
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row);
        assertEquals(1.0F, state.moveForward, 0.0F);
        assertEquals(1.0F, state.moveStrafe, 0.0F);
        state.update(row, true);
        assertEquals(DIAGONAL, state.moveForward, 0.0F);
        assertEquals(DIAGONAL, state.moveStrafe, 0.0F);
        row.setKeyActive(InputRow.Key.SNEAK, true);
        state.update(row, true);
        assertEquals((float) ((double) DIAGONAL * 0.3D), state.moveForward, 0.0F);
        assertEquals((float) ((double) DIAGONAL * 0.3D), state.moveStrafe, 0.0F);
        assertTrue(state.sneak);
    }
}
