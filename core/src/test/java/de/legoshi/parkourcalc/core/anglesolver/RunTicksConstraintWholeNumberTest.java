package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksFilter;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunTicksConstraintWholeNumberTest {

    @Test
    public void rtScalarRoundsToAWholeTickCount() {
        Constraint c = Constraint.scalar(Constraint.Field.RT, Constraint.Op.EQ, 2.6);
        assertEquals(3.0, c.getValue(), 0.0);
        c.setValue(1.4);
        assertEquals(1.0, c.getValue(), 0.0);
        assertTrue(c.isWholeNumber());
    }

    @Test
    public void rtRangeRoundsBothBounds() {
        Constraint c = Constraint.range(Constraint.Field.RT, 0.7, 4.2, true, true);
        assertEquals(1.0, c.getLo(), 0.0);
        assertEquals(4.0, c.getHi(), 0.0);
        c.setLo(2.5);
        c.setHi(5.49);
        assertEquals(3.0, c.getLo(), 0.0);
        assertEquals(5.0, c.getHi(), 0.0);
    }

    @Test
    public void switchingAFieldToRtRoundsItsValues() {
        Constraint scalar = Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 2.3125);
        scalar.setField(Constraint.Field.RT);
        assertEquals(2.0, scalar.getValue(), 0.0);

        Constraint range = Constraint.range(Constraint.Field.Z, 1.5, 3.75, true, true);
        range.setField(Constraint.Field.RT);
        assertEquals(2.0, range.getLo(), 0.0);
        assertEquals(4.0, range.getHi(), 0.0);
    }

    @Test
    public void otherFieldsKeepTheirDecimals() {
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 2.3125);
        assertEquals(2.3125, c.getValue(), 0.0);
        c.setValue(0.0625);
        assertEquals(0.0625, c.getValue(), 0.0);
        assertFalse(c.isWholeNumber());
    }

    @Test
    public void decimalEqualityAgreesWithTheSearchCap() {
        Constraint eq = Constraint.scalar(Constraint.Field.RT, Constraint.Op.EQ, 2.6);
        int cap = RunTicksFilter.maxAllowed(Arrays.asList(eq));
        assertEquals(3, cap);
        assertTrue(RunTicksFilter.allows(Arrays.asList(eq), cap));
    }
}
