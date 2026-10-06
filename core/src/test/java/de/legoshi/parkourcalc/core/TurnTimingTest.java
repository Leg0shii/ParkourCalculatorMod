package de.legoshi.parkourcalc.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TurnTimingTest {

    private static final long MS = 1_000_000L;

    @Test
    public void aTickTraceKeepsTheStillFrameBeforeEachChange() {
        TurnTiming.TickTrace tr = new TurnTiming.TickTrace();
        tr.begin(0L, 10f);
        tr.frame(5 * MS, 10f);
        tr.frame(10 * MS, 10f);
        tr.frame(20 * MS, 11f);
        tr.frame(30 * MS, 13f);
        tr.frame(40 * MS, 13f);
        assertTrue(tr.moved());
        assertEquals(0.4f, tr.onset(50 * MS), 1e-6f);
        assertEquals(0.6f, tr.end(50 * MS), 1e-6f);
        float[] p = tr.points(50 * MS);
        assertNotNull(p);
        assertEquals(8, p.length);
        assertEquals(0f, p[0], 0f);
        assertEquals(10f, p[1], 0f);
        assertEquals(0.2f, p[2], 1e-6f);
        assertEquals(10f, p[3], 0f);
        assertEquals(0.4f, p[4], 1e-6f);
        assertEquals(11f, p[5], 0f);
        assertEquals(0.6f, p[6], 1e-6f);
        assertEquals(13f, p[7], 0f);
    }

    @Test
    public void aStillTickHasNoOnsetAndNoPoints() {
        TurnTiming.TickTrace tr = new TurnTiming.TickTrace();
        tr.begin(0L, 10f);
        tr.frame(10 * MS, 10f);
        tr.frame(40 * MS, 10f);
        assertFalse(tr.moved());
        assertTrue(Float.isNaN(tr.onset(50 * MS)));
        assertTrue(Float.isNaN(tr.end(50 * MS)));
        assertNull(tr.points(50 * MS));
    }

    @Test
    public void aCatchUpTickWithoutSpanHasNoPhase() {
        TurnTiming.TickTrace tr = new TurnTiming.TickTrace();
        tr.begin(7L, 10f);
        tr.frame(7L, 12f);
        assertTrue(Float.isNaN(tr.onset(7L)));
        assertTrue(Float.isNaN(tr.points(7L)[2]));
    }

    @Test
    public void phasesClampToTheTickAndFramesBeforeTheTickAreIgnored() {
        TurnTiming.TickTrace tr = new TurnTiming.TickTrace();
        tr.begin(100 * MS, 10f);
        tr.frame(50 * MS, 99f);
        tr.frame(170 * MS, 12f);
        assertEquals(1f, tr.onset(150 * MS), 0f);
        assertEquals(1f, tr.end(150 * MS), 0f);
        assertEquals(4, tr.points(150 * MS).length);
    }

    @Test
    public void onsetStatsTakeTheMedianOfTheLatestJudgedAttempts() {
        List<TurnAttempt> list = new ArrayList<TurnAttempt>();
        float[] phases = {0.1f, 0.5f, 0.3f, 0.9f, 0.7f};
        for (int i = 0; i < phases.length; i++) {
            list.add(new TurnAttempt(i + 1, 4, new double[] {0, 0, 0}, 3, true, true, false, "", -0.1, -1, -1, 0, 0, 0,
                    new float[] {Float.NaN, phases[i], Float.NaN}, new float[] {Float.NaN, 1f, Float.NaN}, null, false, Double.NaN, null));
        }
        list.add(new TurnAttempt(9, 4, new double[] {0, 0, 0}, 3, true, false, false, "", Double.NaN, -1, 5, 0, 0, 0,
                new float[] {0f, 0f, 0f}, new float[] {1f, 1f, 1f}, null, true, 1.0, null));
        list.add(new TurnAttempt(10, 4, new double[] {0, 0, 0}, 3, true, true, false, "", -0.1, -1, -1, 0, 0, 0, null, null, null, false, Double.NaN, null));
        TurnTiming.Onset o = TurnTiming.onset(list, 5, 100);
        assertNotNull(o);
        assertEquals(5, o.attempts);
        assertEquals(0.5, o.median, 1e-6);
        assertNull(TurnTiming.onset(list, 4, 100));
        assertEquals(2, TurnTiming.onset(list, 5, 2).attempts);
        assertEquals("25 ms", TurnTiming.ms(0.5));
    }

    @Test
    public void theMainTurnTickIsTheLargestFacingStep() {
        double[] facing = {0.0, 1.0, 40.0, 42.0, -178.0};
        int n = facing.length;
        TurnProfileController.Current cur = new TurnProfileController.Current(3, -1, facing, new int[n], new boolean[n],
                new int[n], new boolean[n], new boolean[n], new boolean[n], new int[n], new int[n], new int[0], null, null,
                null, 0.1, null);
        assertEquals(3, TurnTiming.mainTurnTick(cur));
    }
}
