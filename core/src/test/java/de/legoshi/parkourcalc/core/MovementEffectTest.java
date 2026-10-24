package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.MovementEffect;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MovementEffectTest {

    @Test
    public void availabilityFollowsTheVersionTheEffectWasAdded() {
        assertTrue(MovementEffect.SPEED.availableIn("1.8.9"));
        assertTrue(MovementEffect.BLINDNESS.availableIn("1.8.9"));
        assertFalse(MovementEffect.LEVITATION.availableIn("1.8.9"));
        assertTrue(MovementEffect.LEVITATION.availableIn("1.12.2"));
        assertFalse(MovementEffect.SLOW_FALLING.availableIn("1.12.2"));
        assertTrue(MovementEffect.SLOW_FALLING.availableIn("1.20.4"));
        assertTrue(MovementEffect.DOLPHINS_GRACE.availableIn("1.20.4"));
        assertFalse(MovementEffect.WEAVING.availableIn("1.20.4"));
        assertTrue(MovementEffect.WEAVING.availableIn("1.20.5"));
        assertTrue(MovementEffect.WEAVING.availableIn("1.21.3"));
        assertTrue(MovementEffect.WEAVING.availableIn("26.3"));
        assertTrue(MovementEffect.WEAVING.availableIn(null));
    }

    @Test
    public void byteAmplifierWrapsBelow1205Only() {
        assertTrue(MovementEffect.wrapsAmplifier("1.8.9"));
        assertTrue(MovementEffect.wrapsAmplifier("1.12.2"));
        assertTrue(MovementEffect.wrapsAmplifier("1.20.4"));
        assertFalse(MovementEffect.wrapsAmplifier("1.20.5"));
        assertFalse(MovementEffect.wrapsAmplifier("1.21.3"));
        assertFalse(MovementEffect.wrapsAmplifier("26.3"));
        assertFalse(MovementEffect.wrapsAmplifier(null));
    }

    @Test
    public void clientLevelWrapsLevelsAbove128() {
        assertEquals(0, MovementEffect.clientLevel(0, true));
        assertEquals(128, MovementEffect.clientLevel(128, true));
        assertEquals(-126, MovementEffect.clientLevel(130, true));
        assertEquals(0, MovementEffect.clientLevel(256, true));
        assertEquals(130, MovementEffect.clientLevel(130, false));
        assertEquals(-128, MovementEffect.clientAmplifier(129, true));
        assertEquals(128, MovementEffect.clientAmplifier(129, false));
    }

    @Test
    public void toggleEffectsClampToOne() {
        assertEquals(1, MovementEffect.SLOW_FALLING.clampLevel(5));
        assertEquals(255, MovementEffect.SPEED.clampLevel(400));
        assertEquals(0, MovementEffect.SPEED.clampLevel(-3));
    }

    @Test
    public void describeAndLookup() {
        assertEquals("Speed 2", MovementEffect.SPEED.describe(2));
        assertEquals("SlowFall", MovementEffect.SLOW_FALLING.describe(1));
        assertEquals("", MovementEffect.SPEED.describe(0));
        assertSame(MovementEffect.DOLPHINS_GRACE, MovementEffect.byId("dolphins_grace"));
        assertNull(MovementEffect.byId("night_vision"));
    }
}
