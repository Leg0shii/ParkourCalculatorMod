package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.SupportOverlap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupportOverlapTest {

    private static final double RECORDED_CONCRETE_COLORS_LANDING_Z = 0.70000008217296328;

    @Test
    public void recordedConcreteColorsLandingFailsModernButLandsLegacy() {
        assertFalse(SupportOverlap.supports(true, RECORDED_CONCRETE_COLORS_LANDING_Z, 1.0, 2.0));
        assertTrue(SupportOverlap.supports(false, RECORDED_CONCRETE_COLORS_LANDING_Z, 1.0, 2.0));
    }

    @Test
    public void exactHitboxTouchNeverSupportsInEitherEra() {
        double touch = 1.0 - (double) (0.6F / 2.0F);
        assertFalse(SupportOverlap.supports(false, touch, 1.0, 2.0));
        assertFalse(SupportOverlap.supports(true, touch, 1.0, 2.0));
    }

    @Test
    public void centerBoundsAreTheExactBoundaryDoubles() {
        double[][] segments = {{1.0, 2.0}, {-6.0, -5.0}, {0.0, 1.0}, {4.0, 5.0}, {10000.0, 10001.0}};
        for (boolean modern : new boolean[]{false, true}) {
            for (double[] seg : segments) {
                double lo = SupportOverlap.minCenter(modern, seg[0], seg[1]);
                assertTrue(SupportOverlap.supports(modern, lo, seg[0], seg[1]));
                assertFalse(SupportOverlap.supports(modern, Math.nextDown(lo), seg[0], seg[1]));
                double hi = SupportOverlap.maxCenter(modern, seg[0], seg[1]);
                assertTrue(SupportOverlap.supports(modern, hi, seg[0], seg[1]));
                assertFalse(SupportOverlap.supports(modern, Math.nextUp(hi), seg[0], seg[1]));
            }
        }
    }

    @Test
    public void modernBoundaryExcludesTheRecordedMissByTheCollideEpsilon() {
        double legacy = SupportOverlap.minCenter(false, 1.0, 2.0);
        double modern = SupportOverlap.minCenter(true, 1.0, 2.0);
        assertTrue(modern > RECORDED_CONCRETE_COLORS_LANDING_Z);
        assertTrue(legacy < RECORDED_CONCRETE_COLORS_LANDING_Z);
        assertEquals(1.0e-7, modern - legacy, 1.0e-9);
    }
}
