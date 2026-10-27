package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.InputSmooth;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InputSmoothTest {

    private static final int N = 6;

    private static JumpPhysicsInputs force45() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.jumpTick = 0;
        sc.jumpPerTick = new boolean[] {true, false, false, false, false, false};
        sc.strafePerTick = new boolean[] {false, true, true, true, true, true};
        sc.strafeSign = 1;
        sc.slipPerTick = new double[] {0.6, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.6};
        sc.forwardInputPerTick = new float[N];
        sc.strafeInputPerTick = new float[N];
        sc.sprintPerTick = new boolean[N];
        for (int t = 0; t < N; t++) {
            sc.forwardInputPerTick[t] = TickInputs.SCALE;
            sc.sprintPerTick[t] = true;
        }
        sc.incomingSprint = Boolean.TRUE;
        return sc;
    }

    @Test
    public void readsTheKeysOfEveryCanonicalTick() {
        JumpPhysicsInputs sc = force45();
        assertEquals(new TickInputs(true, false, false, false, true), TickInputs.of(sc, 0));
        assertEquals(new TickInputs(true, true, false, false, true), TickInputs.of(sc, 1));
        sc.strafeSignPerTick = new int[] {1, 1, -1, 1, 1, 1};
        assertEquals(new TickInputs(true, false, false, true, true), TickInputs.of(sc, 2));
        sc.strafePerTick[3] = false;
        sc.forwardInputPerTick[3] = 0.3F * TickInputs.SCALE;
        assertNull("a sneak-scaled sample is not a key combination", TickInputs.of(sc, 3));
        sc.forwardInputPerTick[4] = 0.0F;
        sc.strafeInputPerTick[4] = -TickInputs.SCALE;
        sc.strafePerTick[4] = false;
        sc.sprintPerTick[4] = false;
        assertEquals(new TickInputs(false, false, false, true, false), TickInputs.of(sc, 4));
    }

    @Test
    public void appliedKeysReadBackAndLeaveTheOriginalAlone() {
        JumpPhysicsInputs base = force45();
        JumpPhysicsInputs editable = TickInputs.editable(base);
        JumpPhysicsInputs copy = TickInputs.cloneInputs(editable);
        TickInputs[] combos = {
                TickInputs.NONE,
                new TickInputs(true, false, false, false, true),
                new TickInputs(true, true, false, false, true),
                new TickInputs(true, false, false, true, false),
                new TickInputs(false, true, false, false, false),
                new TickInputs(false, false, true, false, false),
        };
        for (int t = 0; t < combos.length; t++) {
            combos[t].applyTo(copy, t);
            assertEquals("tick " + t, combos[t], TickInputs.of(copy, t));
            assertEquals("the sprint key follows the sprint on tick " + t, combos[t].sprint, copy.sprintKeyAt(t));
        }
        assertTrue("W D runs through the strafe path", copy.strafeAt(3));
        assertEquals(-1, copy.strafeSignAt(3));
        assertFalse("a lone A is a plain strafe input", copy.strafeAt(4));
        assertEquals(TickInputs.SCALE, copy.strafeInputAt(4), 0.0F);
        for (int t = 0; t < N; t++) {
            assertEquals("the editable copy keeps the base at tick " + t, TickInputs.of(base, t), TickInputs.of(editable, t));
        }
        assertEquals("W A", combos[2].keys());
        assertEquals("W D (no sprint)", combos[3].label());
        assertEquals("none", TickInputs.NONE.label());
    }

    @Test
    public void sprintOffNeedsAStoppedSprintAndNoFreshGroundPress() {
        JumpPhysicsInputs sc = TickInputs.editable(force45());
        TickInputs wNoSprint = new TickInputs(true, false, false, false, false);
        assertFalse("sprint cannot drop while the previous tick sprints", InputSmooth.realizable(sc, 2, wNoSprint));
        TickInputs.NONE.applyTo(sc, 1);
        assertTrue("after an empty air tick W may run without sprint in the air", InputSmooth.realizable(sc, 2, wNoSprint));
        TickInputs.NONE.applyTo(sc, 4);
        assertFalse("a fresh W press on the ground without the key is the double-tap trap",
                InputSmooth.realizable(sc, 5, wNoSprint));
        wNoSprint.applyTo(sc, 4);
        assertTrue("holding W without sprint onto the ground keeps sprint off", InputSmooth.realizable(sc, 5, wNoSprint));
    }

    @Test
    public void sprintOnMustNotLeakIntoANonSprintingRun() {
        JumpPhysicsInputs sc = TickInputs.editable(force45());
        new TickInputs(true, false, false, false, false).applyTo(sc, 3);
        new TickInputs(true, false, false, false, false).applyTo(sc, 4);
        TickInputs sprint = new TickInputs(true, true, false, false, true);
        assertFalse("sprint at T3 would carry into the non-sprinting W at T4", InputSmooth.realizable(sc, 2, sprint));
        TickInputs.NONE.applyTo(sc, 3);
        assertTrue("an empty tick in between stops the sprint again", InputSmooth.realizable(sc, 2, sprint));
    }

    @Test
    public void jumpAndSneakTicksKeepTheirKeys() {
        JumpPhysicsInputs sc = TickInputs.editable(force45());
        sc.sneakPerTick = new boolean[] {false, false, true, false, false, false};
        TickInputs[] original = new TickInputs[N];
        for (int t = 0; t < N; t++) original[t] = TickInputs.of(sc, t);
        assertFalse("the firing jump tick", InputSmooth.eligible(sc, original, 0));
        assertTrue(InputSmooth.eligible(sc, original, 1));
        assertFalse("a sneaking tick", InputSmooth.eligible(sc, original, 2));
        assertTrue("a grounded tick without a jump", InputSmooth.eligible(sc, original, 5));
    }

    @Test
    public void sprintKeyTicksAreWhereSprintRestartsWithoutTheKey() {
        JumpPhysicsInputs sc = TickInputs.editable(force45());
        assertArrayEquals(new int[0], InputSmooth.sprintKeyTicks(sc));
        TickInputs.NONE.applyTo(sc, 2);
        sc.sprintKeyPerTick[3] = false;
        assertArrayEquals("T3 restarts the sprint and its row has no sprint key", new int[] {3}, InputSmooth.sprintKeyTicks(sc));
        sc.sprintKeyPerTick[3] = true;
        assertArrayEquals(new int[0], InputSmooth.sprintKeyTicks(sc));
    }
}
