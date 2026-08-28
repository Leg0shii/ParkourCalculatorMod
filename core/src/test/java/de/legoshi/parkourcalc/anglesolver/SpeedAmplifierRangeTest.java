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
}
