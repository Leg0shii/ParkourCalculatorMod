package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConstraintTextExactTest {

    private final int precision = ConstraintText.statsPrecision;

    @After
    public void restore() {
        ConstraintText.statsPrecision = precision;
    }

    @Test
    public void editorTextKeepsEveryDecimalWhateverThePrecision() {
        ConstraintText.statsPrecision = 1;
        assertEquals("0.123456789012", ConstraintText.exact(0.123456789012));
        assertEquals(0.1234567890123456789, Double.parseDouble(ConstraintText.exact(0.1234567890123456789)), 0.0);
        assertEquals("0.1", ConstraintText.num(0.123456789012));
    }

    @Test
    public void editorTextTrimsTrailingZerosAndNeverUsesAnExponent() {
        assertEquals("2", ConstraintText.exact(2.0));
        assertEquals("-1.5", ConstraintText.exact(-1.5));
        assertEquals("0", ConstraintText.exact(-0.0));
        assertEquals("0.00001", ConstraintText.exact(1.0e-5));
        assertEquals("12000000", ConstraintText.exact(1.2e7));
    }
}
