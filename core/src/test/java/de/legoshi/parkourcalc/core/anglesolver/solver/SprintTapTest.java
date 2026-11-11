package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SprintTapTest {

    private static final TickInputs SPRINT = new TickInputs(true, false, false, false, true);
    private static final TickInputs WALK = new TickInputs(true, false, false, false, false);
    private static final TickInputs BACK = new TickInputs(false, false, true, false, false);
    private static final TickInputs STRAFE = new TickInputs(false, true, false, false, false);

    private static JumpPhysicsInputs released(int n) {
        JumpPhysicsInputs base = new JumpPhysicsInputs(n);
        base.incomingSprint = Boolean.FALSE;
        JumpPhysicsInputs sc = TickInputs.editable(base);
        for (int t = 0; t < n; t++) {
            TickInputs.NONE.applyTo(sc, t);
        }
        return sc;
    }

    private static int count(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) {
            if (flag) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void aSecondPressInsideTheWindowSprints() {
        JumpPhysicsInputs sc = released(30);
        SPRINT.applyTo(sc, 10);
        WALK.applyTo(sc, 12);
        WALK.applyTo(sc, 13);
        boolean[] conflicts = SprintTap.conflicts(sc);
        assertTrue(conflicts[12]);
        assertEquals(1, count(conflicts));
        assertEquals(10, SprintTap.armer(sc, 12));
    }

    @Test
    public void theWindowEndsAfterSixTicks() {
        JumpPhysicsInputs inside = released(30);
        SPRINT.applyTo(inside, 10);
        WALK.applyTo(inside, 16);
        assertTrue(SprintTap.conflicts(inside)[16]);
        JumpPhysicsInputs outside = released(30);
        SPRINT.applyTo(outside, 10);
        WALK.applyTo(outside, 17);
        assertEquals(0, count(SprintTap.conflicts(outside)));
    }

    @Test
    public void aBackwardPressClearsTheWindow() {
        JumpPhysicsInputs sc = released(30);
        SPRINT.applyTo(sc, 10);
        BACK.applyTo(sc, 11);
        WALK.applyTo(sc, 12);
        assertEquals(0, count(SprintTap.conflicts(sc)));
        STRAFE.applyTo(sc, 11);
        assertTrue(SprintTap.conflicts(sc)[12]);
    }

    @Test
    public void aHeldKeyIsNotASecondPress() {
        JumpPhysicsInputs sc = released(30);
        WALK.applyTo(sc, 10);
        WALK.applyTo(sc, 11);
        WALK.applyTo(sc, 12);
        assertEquals(0, count(SprintTap.conflicts(sc)));
    }

    @Test
    public void aSecondPressThatIsMeantToSprintIsFine() {
        JumpPhysicsInputs sc = released(30);
        WALK.applyTo(sc, 10);
        SPRINT.applyTo(sc, 12);
        assertEquals(0, count(SprintTap.conflicts(sc)));
    }

    @Test
    public void thePressThatStartsSprintingDoesNotRestartTheWindow() {
        JumpPhysicsInputs sc = released(30);
        WALK.applyTo(sc, 10);
        SPRINT.applyTo(sc, 15);
        WALK.applyTo(sc, 17);
        assertEquals(0, count(SprintTap.conflicts(sc)));
    }

    @Test
    public void theFirstTicksCountAsInsideAnUnknownWindow() {
        JumpPhysicsInputs sc = released(30);
        WALK.applyTo(sc, 5);
        assertTrue(SprintTap.conflicts(sc)[5]);
        JumpPhysicsInputs later = released(30);
        WALK.applyTo(later, 6);
        assertEquals(0, count(SprintTap.conflicts(later)));
    }

    @Test
    public void aKnownStartFollowsTheGameExactly() {
        JumpPhysicsInputs known = released(30);
        known.incomingSprintTap = 0;
        known.incomingForward = Boolean.FALSE;
        SPRINT.applyTo(known, 0);
        SPRINT.applyTo(known, 6);
        WALK.applyTo(known, 8);
        assertEquals(0, count(SprintTap.conflicts(known)));
        JumpPhysicsInputs unknown = released(30);
        SPRINT.applyTo(unknown, 0);
        SPRINT.applyTo(unknown, 6);
        WALK.applyTo(unknown, 8);
        assertTrue(SprintTap.conflicts(unknown)[8]);
    }

    @Test
    public void aRunningWindowFromBeforeTheSpanCounts() {
        JumpPhysicsInputs inside = released(30);
        inside.incomingSprintTap = 3;
        inside.incomingForward = Boolean.FALSE;
        WALK.applyTo(inside, 1);
        assertTrue(SprintTap.conflicts(inside)[1]);
        assertEquals(-1, SprintTap.armer(inside, 1));
        JumpPhysicsInputs outside = released(30);
        outside.incomingSprintTap = 3;
        outside.incomingForward = Boolean.FALSE;
        WALK.applyTo(outside, 2);
        assertEquals(0, count(SprintTap.conflicts(outside)));
    }

    @Test
    public void aPressTheRecordedRunAlreadyHadIsNotNew() {
        JumpPhysicsInputs base = released(30);
        WALK.applyTo(base, 3);
        JumpPhysicsInputs same = TickInputs.cloneInputs(base);
        SPRINT.applyTo(same, 20);
        assertEquals(-1, SprintTap.firstNew(base, same));
        JumpPhysicsInputs earlier = TickInputs.cloneInputs(base);
        SPRINT.applyTo(earlier, 1);
        assertEquals(3, SprintTap.firstNew(base, earlier));
        JumpPhysicsInputs added = TickInputs.cloneInputs(base);
        SPRINT.applyTo(added, 20);
        WALK.applyTo(added, 22);
        assertEquals(22, SprintTap.firstNew(base, added));
    }

    @Test
    public void addedNamesTheFirstNewTick() {
        boolean[] before = {false, true, false};
        assertEquals(-1, SprintTap.added(before, new boolean[]{false, true, false}));
        assertEquals(-1, SprintTap.added(before, new boolean[]{false, false, false}));
        assertEquals(2, SprintTap.added(before, new boolean[]{false, true, true}));
    }
}
