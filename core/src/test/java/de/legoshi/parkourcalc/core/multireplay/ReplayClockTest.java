package de.legoshi.parkourcalc.core.multireplay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayClockTest {

    private static final long MS = 1_000_000L;

    @Test
    public void advancesByTickDuration() {
        ReplayClock c = new ReplayClock();
        c.setLastTick(10);
        c.setTickDurationMs(250);
        c.play();
        c.advance(1_000 * MS);
        c.advance(1_500 * MS);
        assertEquals(2.0, c.time(), 1e-9);
        assertEquals(2, c.tick());
        assertEquals(0.0, c.fraction(), 1e-9);
        c.advance(1_625 * MS);
        assertEquals(2, c.tick());
        assertEquals(0.5, c.fraction(), 1e-9);
        assertTrue(c.isPlaying());
    }

    @Test
    public void clampsAtEndAndPauses() {
        ReplayClock c = new ReplayClock();
        c.setLastTick(4);
        c.play();
        c.advance(0);
        c.advance(60_000 * MS);
        assertEquals(4.0, c.time(), 1e-9);
        assertFalse(c.isPlaying());
        assertTrue(c.atEnd());
    }

    @Test
    public void playAtEndRestarts() {
        ReplayClock c = new ReplayClock();
        c.setLastTick(4);
        c.seek(4);
        c.play();
        assertEquals(0.0, c.time(), 1e-9);
        assertTrue(c.isPlaying());
    }

    @Test
    public void stepsSnapToWholeTicks() {
        ReplayClock c = new ReplayClock();
        c.setLastTick(5);
        c.play();
        c.seek(1.5);
        c.stepForward();
        assertEquals(2.0, c.time(), 1e-9);
        assertFalse(c.isPlaying());
        c.stepBackward();
        assertEquals(1.0, c.time(), 1e-9);
        c.seek(1.5);
        c.stepBackward();
        assertEquals(1.0, c.time(), 1e-9);
        c.seek(0);
        c.stepBackward();
        assertEquals(0.0, c.time(), 1e-9);
        c.seek(5);
        c.stepForward();
        assertEquals(5.0, c.time(), 1e-9);
    }

    @Test
    public void tickDurationIsClamped() {
        ReplayClock c = new ReplayClock();
        c.setTickDurationMs(1);
        assertEquals(ReplayClock.MIN_TICK_MS, c.tickDurationMs());
        c.setTickDurationMs(999_999);
        assertEquals(ReplayClock.MAX_TICK_MS, c.tickDurationMs());
    }

    @Test
    public void emptyClockNeverPlays() {
        ReplayClock c = new ReplayClock();
        c.play();
        assertFalse(c.isPlaying());
        c.advance(0);
        c.advance(1_000 * MS);
        assertEquals(0.0, c.time(), 1e-9);
    }

    @Test
    public void shrinkingLengthClampsTime() {
        ReplayClock c = new ReplayClock();
        c.setLastTick(10);
        c.seek(8);
        c.setLastTick(3);
        assertEquals(3.0, c.time(), 1e-9);
    }
}
