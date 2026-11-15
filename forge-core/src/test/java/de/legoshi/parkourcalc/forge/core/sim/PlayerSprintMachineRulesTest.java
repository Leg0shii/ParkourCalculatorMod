package de.legoshi.parkourcalc.forge.core.sim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSprintMachineRulesTest {

    private static final PlayerSprintMachine.Rules BPK =
            new PlayerSprintMachine.Rules(true, 1.0E-5F, 1.0E-5F, true, true, false);

    private static PlayerSprintMachine.Inputs keys(boolean w, boolean s, boolean a, boolean sneak, boolean sprintKey,
                                                   boolean wall) {
        return new PlayerSprintMachine.Inputs(w, s, a, false, false, sneak, sprintKey, true, false, false, wall,
                false, false, 20.0F);
    }

    private static PlayerSprintMachine.State sprinting(boolean prevSneak) {
        return new PlayerSprintMachine.State(prevSneak, 1.0F, 0, 600, true);
    }

    @Test
    public void vanillaRulesAreTheTwoArgumentTick() {
        PlayerSprintMachine.State state = PlayerSprintMachine.State.initial();
        PlayerSprintMachine.State withRules = PlayerSprintMachine.State.initial();
        boolean[][] script = {
                {true, false, false, false, true, false},
                {true, false, true, false, true, false},
                {true, false, true, true, false, false},
                {true, false, false, false, false, true},
                {false, true, false, false, true, false},
                {true, false, false, false, true, false},
        };
        for (boolean[] k : script) {
            PlayerSprintMachine.Inputs in = keys(k[0], k[1], k[2], k[3], k[4], k[5]);
            PlayerSprintMachine.Outputs a = PlayerSprintMachine.tick(in, state);
            PlayerSprintMachine.Outputs b = PlayerSprintMachine.tick(in, withRules, PlayerSprintMachine.Rules.VANILLA);
            assertEquals(a.moveForward, b.moveForward, 0.0F);
            assertEquals(a.moveStrafe, b.moveStrafe, 0.0F);
            assertEquals(a.next.isSprinting, b.next.isSprinting);
            assertEquals(a.next.sprintToggleTimer, b.next.sprintToggleTimer);
            assertEquals(a.next.sprintingTicksLeft, b.next.sprintingTicksLeft);
            state = a.next;
            withRules = b.next;
        }
    }

    @Test
    public void strafeCancelNormalisesTheDiagonalBeforeSneak() {
        PlayerSprintMachine.Outputs diag = PlayerSprintMachine.tick(keys(true, false, true, false, false, false),
                PlayerSprintMachine.State.initial(), BPK);
        float unit = 1.0F / (float) Math.sqrt(2.0);
        assertEquals(unit, diag.moveForward, 0.0F);
        assertEquals(unit, diag.moveStrafe, 0.0F);
        PlayerSprintMachine.Outputs sneak = PlayerSprintMachine.tick(keys(true, false, true, true, false, false),
                PlayerSprintMachine.State.initial(), BPK);
        assertEquals(unit * 0.3F, sneak.moveForward, 0.0F);
        PlayerSprintMachine.Outputs straight = PlayerSprintMachine.tick(keys(true, false, false, false, false, false),
                PlayerSprintMachine.State.initial(), BPK);
        assertEquals(1.0F, straight.moveForward, 0.0F);
    }

    @Test
    public void sneakKeepsSprintUntilThePoseLands() {
        PlayerSprintMachine.Inputs sneakW = keys(true, false, false, true, false, false);
        assertFalse(PlayerSprintMachine.tick(sneakW, sprinting(false)).next.isSprinting);
        PlayerSprintMachine.Outputs first = PlayerSprintMachine.tick(sneakW, sprinting(false), BPK);
        assertTrue(first.next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(sneakW, first.next, BPK).next.isSprinting);
    }

    @Test
    public void wallHitIsIgnoredWhenTheRuleSaysSo() {
        PlayerSprintMachine.Inputs wall = keys(true, false, false, false, false, true);
        assertFalse(PlayerSprintMachine.tick(wall, sprinting(false)).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(wall, sprinting(false), BPK).next.isSprinting);
    }

    @Test
    public void sprintKeyIsBlockedInTheSneakPose() {
        PlayerSprintMachine.Inputs keyW = keys(true, false, false, false, true, false);
        PlayerSprintMachine.State posed = new PlayerSprintMachine.State(true, 0.3F, 0, 0, false);
        assertTrue(PlayerSprintMachine.tick(keyW, posed).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(keyW, posed, BPK).next.isSprinting);
        PlayerSprintMachine.State standing = new PlayerSprintMachine.State(false, 0.0F, 0, 0, false);
        assertTrue(PlayerSprintMachine.tick(keyW, standing, BPK).next.isSprinting);
    }

    @Test
    public void sprintBackwardKeepsSprintWhileReversing() {
        PlayerSprintMachine.Rules backward = new PlayerSprintMachine.Rules(true, 1.0E-5F, 1.0E-5F, true, true, true);
        PlayerSprintMachine.Inputs reverse = keys(false, true, false, false, false, false);
        assertFalse(PlayerSprintMachine.tick(reverse, sprinting(false), BPK).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(reverse, sprinting(false), backward).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(reverse, PlayerSprintMachine.State.initial(), backward).next.isSprinting);
    }

    @Test
    public void nonSprintingStrafeNeedsTheFullForwardToStart() {
        PlayerSprintMachine.Rules strict = new PlayerSprintMachine.Rules(true, 1.0E-5F, 1.0F, true, true, false);
        PlayerSprintMachine.Inputs diagKey = keys(true, false, true, false, true, false);
        assertTrue(PlayerSprintMachine.tick(diagKey, PlayerSprintMachine.State.initial(), BPK).next.isSprinting);
        assertFalse(PlayerSprintMachine.tick(diagKey, PlayerSprintMachine.State.initial(), strict).next.isSprinting);
        assertTrue(PlayerSprintMachine.tick(diagKey, sprinting(false), strict).next.isSprinting);
        PlayerSprintMachine.Inputs straightKey = keys(true, false, false, false, true, false);
        assertTrue(PlayerSprintMachine.tick(straightKey, PlayerSprintMachine.State.initial(), strict).next.isSprinting);
    }
}
