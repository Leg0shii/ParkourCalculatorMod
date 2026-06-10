package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.Potion;
import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-111: duplicating a tick duplicates its constraints and state override onto the copy --
 * as an independent deep copy, while everything past the copy slides down one row.
 */
public class DuplicateTickStateTest {

    private static AngleSolverState seeded() {
        AngleSolverState s = new AngleSolverState();
        s.setStartTick(0);
        s.setLandingTick(6);
        TickConstraints t3 = s.tickConstraints(3);
        t3.getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.5));
        t3.getConstraints().add(Constraint.range(Constraint.Field.Z, -1.0, 1.0, true, false));
        t3.getOverride().setSlipperiness(Slipperiness.ICE);
        t3.getOverride().setInputs(AngleSolverState.InputMode.KEEP);
        t3.getOverride().getAdded().add(new PotionDose(Potion.SPEED, 2));
        s.tickConstraints(5).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 0.0));
        return s;
    }

    @Test
    public void duplicateCopiesConstraintsAndOverrideOntoTheNewRow() {
        AngleSolverState s = seeded();
        s.onRowDuplicated(3);

        TickConstraints copy = s.tickConstraintsOrNull(4);
        assertNotNull("the duplicated row carries the tick's data", copy);
        assertEquals(2, copy.getConstraints().size());
        assertEquals(Constraint.Field.X, copy.getConstraints().get(0).getField());
        assertEquals(1.5, copy.getConstraints().get(0).getValue(), 0.0);
        assertTrue(copy.getConstraints().get(1).isRange());
        assertEquals(Slipperiness.ICE, copy.getOverride().getSlipperiness());
        assertEquals(AngleSolverState.InputMode.KEEP, copy.getOverride().getInputs());
        assertEquals(2, copy.getOverride().findAdded(Potion.SPEED).level);

        // Everything past the copy slid down; the source is untouched.
        assertEquals(Constraint.Field.F, s.tickConstraintsOrNull(6).getConstraints().get(0).getField());
        assertNull(s.tickConstraintsOrNull(5));
        assertEquals(2, s.tickConstraintsOrNull(3).getConstraints().size());
        assertEquals("landing slid with its row", 7, s.getLandingTick());
    }

    @Test
    public void theCopyIsIndependentOfTheSource() {
        AngleSolverState s = seeded();
        s.onRowDuplicated(3);
        TickConstraints src = s.tickConstraintsOrNull(3);
        TickConstraints copy = s.tickConstraintsOrNull(4);

        copy.getConstraints().get(0).setValue(99.0);
        copy.getOverride().setSlipperiness(Slipperiness.SLIME);
        copy.getOverride().findAdded(Potion.SPEED).level = 9;

        assertEquals("source constraint untouched", 1.5, src.getConstraints().get(0).getValue(), 0.0);
        assertEquals("source override untouched", Slipperiness.ICE, src.getOverride().getSlipperiness());
        assertEquals("source dose untouched", 2, src.getOverride().findAdded(Potion.SPEED).level);
    }

    @Test
    public void duplicatingABareRowJustShifts() {
        AngleSolverState s = seeded();
        s.onRowDuplicated(0); // row 0 has no tick data
        assertNull(s.tickConstraintsOrNull(1));
        assertNotNull("3 slid to 4", s.tickConstraintsOrNull(4));
        assertEquals(7, s.getLandingTick());
        assertEquals("start at the duplicated row's index stays", 0, s.getStartTick());
    }
}
