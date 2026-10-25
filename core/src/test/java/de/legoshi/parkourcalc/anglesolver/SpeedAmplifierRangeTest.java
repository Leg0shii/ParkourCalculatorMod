package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The speed amplifier enters the solver's byte-exact model only through {@link Constants#attrValueF};
 *  these guard that a level up to 255 stays finite and keeps scaling (no overflow/truncation). */
public class SpeedAmplifierRangeTest {

    @Test
    public void attrValueStaysFiniteAndMonotonicUpTo255() {
        float prev = Constants.attrValueF(0, true);
        int[] amps = {1, 2, 9, 32, 128, 255};
        for (int amp : amps) {
            float v = Constants.attrValueF(amp, true);
            assertTrue("attrValueF(" + amp + ") must be finite", Float.isFinite(v));
            assertTrue("attrValueF(" + amp + ") must exceed attrValueF(" + (amp - 1) + ")", v > prev);
            prev = v;
        }
    }

    @Test
    public void attrValueMatchesLinearFormulaAt255() {
        double base = (double) 0.1F * (1.0 + (double) 0.3F);
        float expected = (float) (base * (1.0 + (double) 0.2F * 255));
        assertEquals(expected, Constants.attrValueF(255, true), 0.0f);
    }

    @Test
    public void slownessScalesDownAndStopsFromLevelSeven() {
        float prev = Constants.attrValueF(0, 0, true);
        for (int slow = 1; slow <= 6; slow++) {
            float v = Constants.attrValueF(0, slow, true);
            assertTrue("slowness " + slow + " must slow the ground attribute", v < prev && v > 0.0f);
            prev = v;
        }
        for (int slow : new int[]{7, 8, 128, 255}) {
            assertEquals("slowness " + slow + " zeroes the clamped attribute", 0.0f, Constants.attrValueF(0, slow, true), 0.0f);
        }
    }

    @Test
    public void speedAndSlownessMultiply() {
        double base = (double) 0.1F * (1.0 + (double) 0.3F);
        float expected = (float) (base * (1.0 + (double) 0.2F * 3) * (1.0 + (double) -0.15F * 4));
        assertEquals(expected, Constants.attrValueF(3, 4, true), 0.0f);
        assertEquals("no slowness is the two-argument value", Constants.attrValueF(5, true), Constants.attrValueF(5, 0, true), 0.0f);
    }
}
