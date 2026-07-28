package de.legoshi.parkourcalc.anglesolver.metriclab;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MeasurementInvariantsTest {

    @Test
    public void trivialD1HoldsInvariantsAndIsDeterministic() {
        HpkHumanSet.Sample sample = HpkHumanSet.load("d1", "j001_4bm_1b");
        JumpMeasurements m = MeasurementEngine.measure(sample);

        assertEquals(1, m.dLevel);
        assertTrue("baseline margin must be positive, was " + m.minMargin, m.minMargin > 0.0);
        for (int k = 0; k < m.numTicks; k++) {
            assertTrue("windowLo[" + k + "] out of range: " + m.windowLo[k],
                    m.windowLo[k] >= 0.0 && m.windowLo[k] <= MeasurementEngine.WINDOW_CAP_DEG);
            assertTrue("windowHi[" + k + "] out of range: " + m.windowHi[k],
                    m.windowHi[k] >= 0.0 && m.windowHi[k] <= MeasurementEngine.WINDOW_CAP_DEG);
        }
        double narrow = MeasurementEngine.minNarrowSide(m.windowLo, m.windowHi);
        assertTrue("jitter " + m.jitterDeg + " exceeds narrowest one-tick window " + narrow,
                m.jitterDeg <= narrow + 1.0e-9);
        assertTrue("a d1 straight jump must have a generous jitter radius, was " + m.jitterDeg,
                m.jitterDeg > 1.0);

        int momentumEdges = 0;
        int jumpEdges = 0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            assertTrue("shiftLo[" + i + "] out of range: " + m.shiftLo[i],
                    m.shiftLo[i] >= 0 && m.shiftLo[i] <= MeasurementEngine.SHIFT_CAP_TICKS);
            assertTrue("shiftHi[" + i + "] out of range: " + m.shiftHi[i],
                    m.shiftHi[i] >= 0 && m.shiftHi[i] <= MeasurementEngine.SHIFT_CAP_TICKS);
            if (m.shiftEdgeMomentum[i]) {
                momentumEdges++;
            } else {
                jumpEdges++;
            }
        }
        assertEquals("shift edge enumeration must match the momentum input-edge count",
                m.inputEdgesMomentum, momentumEdges);
        assertEquals("shift edge enumeration must match the jump input-edge count",
                m.inputEdgesJump, jumpEdges);
        assertTrue("a d1 straight jump must not measure frame-perfect momentum timing, geo was "
                + m.shiftGeoMomentumTicks, m.shiftGeoMomentumTicks > 1.0);

        JumpMeasurements again = MeasurementEngine.measure(sample);
        assertEquals(m.jitterDeg, again.jitterDeg, 0.0);
        assertArrayEquals(m.windowLo, again.windowLo, 0.0);
        assertArrayEquals(m.windowHi, again.windowHi, 0.0);
        assertArrayEquals(m.shiftLo, again.shiftLo);
        assertArrayEquals(m.shiftHi, again.shiftHi);
        assertTrue(Arrays.equals(m.shiftLoFree, again.shiftLoFree));
        assertTrue(Arrays.equals(m.shiftHiFree, again.shiftHiFree));
    }

    @Test
    public void sprintReleaseEdgeIsFree() {
        HpkHumanSet.Sample sample = HpkHumanSet.load("d1", "j001_4bm_1b");
        JumpMeasurements m = MeasurementEngine.measure(sample);
        int i = edgeIndex(m, "-SPRINT");
        assertTrue("releasing the sprint key is physically inert, both sides must be free, lo="
                + m.shiftLoFree[i] + " hi=" + m.shiftHiFree[i], m.shiftLoFree[i] && m.shiftHiFree[i]);
    }

    @Test
    public void delayedSprintStartIsFrameExact() {
        HpkHumanSet.Sample sample = HpkHumanSet.load("d2", "j012_1bm_4.25b");
        JumpMeasurements m = MeasurementEngine.measure(sample);
        int i = edgeIndex(m, "+SPRINT-JUMP");
        assertEquals("j012 needs sprint from exactly its recorded tick, lo must be 0", 0, m.shiftLo[i]);
        assertEquals("j012 needs sprint from exactly its recorded tick, hi must be 0", 0, m.shiftHi[i]);
        assertTrue("neither side of j012's sprint start may read free",
                !m.shiftLoFree[i] && !m.shiftHiFree[i]);
    }

    private static int edgeIndex(JumpMeasurements m, String keys) {
        for (int i = 0; i < m.shiftEdgeKeys.length; i++) {
            if (keys.equals(m.shiftEdgeKeys[i])) {
                return i;
            }
        }
        throw new AssertionError("no edge with keys " + keys + " in " + Arrays.toString(m.shiftEdgeKeys));
    }
}
