package de.legoshi.parkourcalc.forge.core.sim;

import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine.Inputs;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine.Outputs;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine.State;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSprintMachineTest {

    private static final class Press {
        boolean w, s, a, d, jump, sneak, sprintKey, using, riding, wall, blind, fly;
        boolean ground = true;
        float food = 20f;

        Press w() { w = true; return this; }
        Press s() { s = true; return this; }
        Press a() { a = true; return this; }
        Press d() { d = true; return this; }
        Press jump() { jump = true; return this; }
        Press sneak() { sneak = true; return this; }
        Press sprintKey() { sprintKey = true; return this; }
        Press air() { ground = false; return this; }
        Press using() { using = true; return this; }
        Press riding() { riding = true; return this; }
        Press wall() { wall = true; return this; }
        Press blind() { blind = true; return this; }
        Press fly() { fly = true; return this; }
        Press food(float level) { food = level; return this; }

        Inputs in() {
            return new Inputs(w, s, a, d, jump, sneak, sprintKey, ground, using, riding, wall, blind, fly, food);
        }
    }

    private static Press press() {
        return new Press();
    }

    private static State run(State state, Press... ticks) {
        for (Press p : ticks) state = PlayerSprintMachine.tick(p.in(), state).next;
        return state;
    }

    private static State hold(State state, Press p, int ticks) {
        for (int i = 0; i < ticks; i++) state = PlayerSprintMachine.tick(p.in(), state).next;
        return state;
    }

    private static State sprintingByKey() {
        State s = run(State.initial(), press().w().sprintKey());
        assertTrue(s.isSprinting);
        return s;
    }

    @Test
    public void aDoubleTapOfForwardStartsSprintingOnTheGround() {
        State armed = run(State.initial(), press().w());
        assertFalse(armed.isSprinting);
        assertEquals(7, armed.sprintToggleTimer);
        assertEquals(1.0F, armed.prevMoveForward, 0f);
        State released = run(armed, press());
        assertFalse(released.isSprinting);
        assertEquals(6, released.sprintToggleTimer);
        State tapped = run(released, press().w());
        assertTrue(tapped.isSprinting);
        assertEquals(600, tapped.sprintingTicksLeft);
    }

    @Test
    public void theDoubleTapWindowClosesAfterSixTicks() {
        State armed = run(State.initial(), press().w());
        State inTime = run(hold(armed, press(), 5), press().w());
        assertTrue(inTime.isSprinting);
        State late = run(hold(armed, press(), 6), press().w());
        assertFalse(late.isSprinting);
        assertEquals(7, late.sprintToggleTimer);
    }

    @Test
    public void holdingForwardWithoutATapNeverStartsSprinting() {
        State s = State.initial();
        for (int t = 0; t < 20; t++) {
            s = run(s, press().w());
            assertFalse("tick " + t, s.isSprinting);
        }
    }

    @Test
    public void aTapInTheAirDoesNotArmTheToggle() {
        State s = run(State.initial(), press().w().air());
        assertEquals(0, s.sprintToggleTimer);
        assertFalse(s.isSprinting);
    }

    @Test
    public void theSprintKeyStartsSprintingAtOnceEvenInTheAir() {
        State ground = run(State.initial(), press().w().sprintKey());
        assertTrue(ground.isSprinting);
        assertEquals(600, ground.sprintingTicksLeft);
        State air = run(State.initial(), press().w().sprintKey().air());
        assertTrue(air.isSprinting);
    }

    @Test
    public void theSprintKeyNeedsForwardToStart() {
        assertFalse(run(State.initial(), press().sprintKey()).isSprinting);
        assertFalse(run(State.initial(), press().a().sprintKey()).isSprinting);
        assertFalse(run(State.initial(), press().s().sprintKey()).isSprinting);
    }

    @Test
    public void sprintPersistsInTheAirWhileForwardIsHeld() {
        State s = hold(sprintingByKey(), press().w().jump().air(), 12);
        assertTrue(s.isSprinting);
        assertEquals(600 - 12, s.sprintingTicksLeft);
    }

    @Test
    public void releasingForwardStopsTheSprint() {
        State s = run(sprintingByKey(), press().sprintKey());
        assertFalse(s.isSprinting);
        assertEquals(0, s.sprintingTicksLeft);
        assertFalse(run(sprintingByKey(), press().a().sprintKey()).isSprinting);
    }

    @Test
    public void aWallHitStopsTheSprint() {
        State s = run(sprintingByKey(), press().w().sprintKey().wall());
        assertFalse(s.isSprinting);
        assertEquals(0, s.sprintingTicksLeft);
    }

    @Test
    public void theSprintTimerRunsOutUnlessTheKeyIsHeld() {
        State tapped = run(State.initial(), press().w(), press(), press().w());
        assertEquals(600, tapped.sprintingTicksLeft);
        State before = hold(tapped, press().w(), 599);
        assertTrue(before.isSprinting);
        assertEquals(1, before.sprintingTicksLeft);
        State expired = run(before, press().w());
        assertFalse(expired.isSprinting);
        assertEquals(0, expired.sprintingTicksLeft);
        State renewed = run(before, press().w().sprintKey());
        assertTrue(renewed.isSprinting);
        assertEquals(600, renewed.sprintingTicksLeft);
    }

    @Test
    public void sneakingScalesTheAxesAndBlocksTheSprint() {
        Outputs out = PlayerSprintMachine.tick(press().w().a().sneak().sprintKey().in(), State.initial());
        assertEquals(0.3F, out.moveForward, 0f);
        assertEquals(0.3F, out.moveStrafe, 0f);
        assertFalse(out.next.isSprinting);
        assertTrue(out.next.prevSneak);
        assertFalse(run(sprintingByKey(), press().w().sneak().sprintKey()).isSprinting);
    }

    @Test
    public void usingAnItemScalesTheAxesAndCancelsTheTap() {
        State armed = run(State.initial(), press().w());
        Outputs out = PlayerSprintMachine.tick(press().w().d().using().in(), armed);
        assertEquals(0.2F, out.moveForward, 0f);
        assertEquals(-0.2F, out.moveStrafe, 0f);
        assertEquals(0, out.next.sprintToggleTimer);
        assertFalse(out.next.isSprinting);
        Outputs mounted = PlayerSprintMachine.tick(press().w().using().riding().sprintKey().in(), State.initial());
        assertEquals(1.0F, mounted.moveForward, 0f);
        assertFalse(mounted.next.isSprinting);
    }

    @Test
    public void hungerBlocksSprintingUnlessFlyingIsAllowed() {
        assertFalse(run(State.initial(), press().w().sprintKey().food(6f)).isSprinting);
        assertTrue(run(State.initial(), press().w().sprintKey().food(6f).fly()).isSprinting);
        assertTrue(run(State.initial(), press().w().sprintKey().food(6.5f)).isSprinting);
        State starved = run(sprintingByKey(), press().w().sprintKey().food(6f));
        assertFalse(starved.isSprinting);
        assertEquals(0, starved.sprintingTicksLeft);
    }

    @Test
    public void blindnessBlocksEveryStartButNotARunningSprint() {
        assertFalse(run(State.initial(), press().w().sprintKey().blind()).isSprinting);
        State tapped = run(State.initial(), press().w().blind(), press(), press().w().blind());
        assertFalse(tapped.isSprinting);
        assertEquals(0, tapped.sprintToggleTimer);
        assertTrue(run(sprintingByKey(), press().w().blind()).isSprinting);
    }

    @Test
    public void theAxesFollowTheKeysAndOpposingKeysCancel() {
        assertEquals(1.0F, PlayerSprintMachine.tick(press().w().in(), State.initial()).moveForward, 0f);
        assertEquals(-1.0F, PlayerSprintMachine.tick(press().s().in(), State.initial()).moveForward, 0f);
        assertEquals(0.0F, PlayerSprintMachine.tick(press().w().s().in(), State.initial()).moveForward, 0f);
        assertEquals(1.0F, PlayerSprintMachine.tick(press().a().in(), State.initial()).moveStrafe, 0f);
        assertEquals(-1.0F, PlayerSprintMachine.tick(press().d().in(), State.initial()).moveStrafe, 0f);
        assertEquals(0.0F, PlayerSprintMachine.tick(press().a().d().in(), State.initial()).moveStrafe, 0f);
        Outputs jump = PlayerSprintMachine.tick(press().jump().in(), State.initial());
        assertTrue(jump.isJumping);
        assertFalse(PlayerSprintMachine.tick(press().w().in(), State.initial()).isJumping);
        Outputs back = PlayerSprintMachine.tick(press().s().in(), State.initial());
        assertEquals(-1.0F, back.next.prevMoveForward, 0f);
        assertFalse(back.next.isSprinting);
    }

    @Test
    public void withIsSprintingOnlyReplacesTheFlag() {
        State s = new State(true, 0.3F, 4, 120, false);
        State seeded = s.withIsSprinting(true);
        assertTrue(seeded.isSprinting);
        assertTrue(seeded.prevSneak);
        assertEquals(0.3F, seeded.prevMoveForward, 0f);
        assertEquals(4, seeded.sprintToggleTimer);
        assertEquals(120, seeded.sprintingTicksLeft);
        State initial = State.initial();
        assertFalse(initial.prevSneak);
        assertEquals(0f, initial.prevMoveForward, 0f);
        assertEquals(0, initial.sprintToggleTimer);
        assertEquals(0, initial.sprintingTicksLeft);
        assertFalse(initial.isSprinting);
    }

    @Test
    public void aSeededSprintFlagIsDroppedWhenTheKeysDoNotCarryIt() {
        State seeded = new State(false, 1.0F, 0, 300, false).withIsSprinting(true);
        Outputs kept = PlayerSprintMachine.tick(press().w().in(), seeded);
        assertTrue(kept.next.isSprinting);
        assertEquals(299, kept.next.sprintingTicksLeft);
        Outputs dropped = PlayerSprintMachine.tick(press().in(), seeded);
        assertFalse(dropped.next.isSprinting);
        assertEquals(0, dropped.next.sprintingTicksLeft);
    }
}
