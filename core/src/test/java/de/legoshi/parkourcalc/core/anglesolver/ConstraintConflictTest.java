package de.legoshi.parkourcalc.core.anglesolver;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConstraintConflictTest {

    private static Constraint s(Constraint.Field f, Constraint.Op op, double v) {
        return Constraint.scalar(f, op, v);
    }

    private static List<Constraint> of(Constraint... cs) {
        return Arrays.asList(cs);
    }

    @Test
    public void oppositeWallsOnOneTickConflict() {
        String why = ConstraintConflict.describe(28, of(
                s(Constraint.Field.X, Constraint.Op.LE, 4.7),
                s(Constraint.Field.X, Constraint.Op.GE, 5.0)));
        assertNotNull(why);
        assertTrue(why, why.startsWith("T29: "));
        assertTrue(why, why.contains("X <= 4.7"));
        assertTrue(why, why.contains("X >= 5"));
    }

    @Test
    public void strictAndInclusiveAtTheSameValueConflict() {
        assertNotNull(ConstraintConflict.describe(0, of(
                s(Constraint.Field.Z, Constraint.Op.GT, 5.0),
                s(Constraint.Field.Z, Constraint.Op.LE, 5.0))));
        assertNull(ConstraintConflict.describe(0, of(
                s(Constraint.Field.Z, Constraint.Op.GE, 5.0),
                s(Constraint.Field.Z, Constraint.Op.LE, 5.0))));
    }

    @Test
    public void equalityOutsideARangeConflicts() {
        Constraint range = Constraint.range(Constraint.Field.X, 5.7, 7.3, true, true);
        assertNotNull(ConstraintConflict.describe(3, of(range, s(Constraint.Field.X, Constraint.Op.EQ, 4.0))));
        assertNull(ConstraintConflict.describe(3, of(range, s(Constraint.Field.X, Constraint.Op.EQ, 6.0))));
        assertNull(ConstraintConflict.describe(3, of(range, s(Constraint.Field.X, Constraint.Op.LE, 6.0))));
    }

    @Test
    public void disjointRangesConflict() {
        assertNotNull(ConstraintConflict.describe(3, of(
                Constraint.range(Constraint.Field.Z, 1.0, 2.0, true, true),
                Constraint.range(Constraint.Field.Z, 2.0, 3.0, false, true))));
        assertNull(ConstraintConflict.describe(3, of(
                Constraint.range(Constraint.Field.Z, 1.0, 2.0, true, true),
                Constraint.range(Constraint.Field.Z, 2.0, 3.0, true, true))));
    }

    @Test
    public void emptyRangeIsReportedAlone() {
        String why = ConstraintConflict.describe(9, Collections.singletonList(
                Constraint.range(Constraint.Field.X, 5.0, 5.0, false, false)));
        assertNotNull(why);
        assertTrue(why, why.contains("empty range"));
    }

    @Test
    public void differentFieldsDisabledAndRelativeNeverConflict() {
        Constraint off = s(Constraint.Field.X, Constraint.Op.GE, 5.0);
        off.setEnabled(false);
        Constraint vs = s(Constraint.Field.DX, Constraint.Op.GE, 5.0);
        vs.setVsOther(true);
        assertNull(ConstraintConflict.describe(0, of(
                s(Constraint.Field.X, Constraint.Op.LE, 4.7),
                s(Constraint.Field.Z, Constraint.Op.GE, 5.0),
                off,
                s(Constraint.Field.DX, Constraint.Op.LE, 4.0),
                vs)));
    }

    @Test
    public void facingRangesAreLeftAlone() {
        assertNull(ConstraintConflict.describe(0, of(
                Constraint.range(Constraint.Field.F, 170.0, -170.0, true, true),
                s(Constraint.Field.F, Constraint.Op.EQ, 175.0))));
        assertEquals("T1: F = 10 and F = 20 cannot both hold. Change or delete one of them.",
                ConstraintConflict.describe(0, of(
                        s(Constraint.Field.F, Constraint.Op.EQ, 10.0),
                        s(Constraint.Field.F, Constraint.Op.EQ, 20.0))));
    }
}
