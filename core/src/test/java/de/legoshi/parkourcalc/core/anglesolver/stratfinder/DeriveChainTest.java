package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DeriveChainTest {

    @Test
    public void flatJumpLandsTwelveTicksAfterThePress() {
        DeriveChain c = DeriveChain.fromPresses(0, new int[]{1}, 65.0, new double[]{65.0}, true);
        assertNull(c.error);
        assertTrue(Arrays.equals(new int[]{13}, c.landings));
        assertEquals(14, c.yPerTick.length);
        assertEquals(65.0, c.yPerTick[0], 0.0);
        assertEquals(65.0, c.yPerTick[1], 0.0);
        assertTrue(c.yPerTick[5] > 65.0);
        assertEquals(65.0, c.yPerTick[13], 0.0);
    }

    @Test
    public void plusOneLandsNineTicksAfterThePress() {
        DeriveChain c = DeriveChain.fromPresses(0, new int[]{2}, 65.0, new double[]{66.0}, true);
        assertNull(c.error);
        assertTrue(Arrays.equals(new int[]{11}, c.landings));
        assertEquals(66.0, c.yPerTick[11], 0.0);
    }

    @Test
    public void doubleJumpChainsLikeBfNeo() {
        DeriveChain c = DeriveChain.fromPresses(0, new int[]{1, 13}, 65.0,
                new double[]{65.0, 65.0}, true);
        assertNull(c.error);
        assertTrue(Arrays.equals(new int[]{13, 25}, c.landings));
        assertEquals(65.0, c.yPerTick[13], 0.0);
        assertTrue(c.yPerTick[14] > 65.0);
        assertEquals(65.0, c.yPerTick[25], 0.0);
    }

    @Test
    public void pressBeforeTheComputedLandingIsRejected() {
        DeriveChain c = DeriveChain.fromPresses(0, new int[]{1, 12}, 65.0,
                new double[]{65.0, 65.0}, true);
        assertNotNull(c.error);
    }

    @Test
    public void unreachableRiseIsRejected() {
        DeriveChain c = DeriveChain.fromPresses(0, new int[]{1}, 65.0, new double[]{68.0}, true);
        assertNotNull(c.error);
    }
}
