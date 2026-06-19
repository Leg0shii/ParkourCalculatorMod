package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver.LongRunConfig;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LongRunConfigTest {

    @Test
    public void defaultsMirrorShippedConstants() {
        LongRunConfig d = LongRunConfig.defaults();
        assertEquals(10, d.window());
        assertEquals(3, d.commit());
        assertArrayEquals(new int[]{10, 7, 5, 3, 2, 1}, d.windowLadder);
        assertArrayEquals(new int[]{3, 1}, d.commitLadder);
    }

    @Test
    public void ofTenThreeReproducesDefaults() {
        LongRunConfig c = LongRunConfig.of(10, 3);
        assertArrayEquals(new int[]{10, 7, 5, 3, 2, 1}, c.windowLadder);
        assertArrayEquals(new int[]{3, 1}, c.commitLadder);
    }

    @Test
    public void windowLadderDropsRungsAtOrAboveTheWindow() {
        assertArrayEquals(new int[]{6, 5, 3, 2, 1}, LongRunConfig.of(6, 3).windowLadder);
        assertArrayEquals(new int[]{14, 7, 5, 3, 2, 1}, LongRunConfig.of(14, 4).windowLadder);
    }

    @Test
    public void commitLadderKeepsTheAutomaticOneRetry() {
        assertArrayEquals(new int[]{4, 1}, LongRunConfig.of(14, 4).commitLadder);
        assertArrayEquals("commit 1 collapses to a single rung", new int[]{1}, LongRunConfig.of(10, 1).commitLadder);
    }

    @Test
    public void commitIsClampedToWindowMinusOne() {
        LongRunConfig c = LongRunConfig.of(8, 99);
        assertEquals(7, c.commit());
        assertArrayEquals(new int[]{7, 1}, c.commitLadder);
    }
}
