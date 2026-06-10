package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-89: constraints, overrides, and the start/landing flags are anchored to rows, so row edits
 * (insert, delete, drag-move) must carry them along instead of leaving them on stale indices.
 */
public class RowEditShiftTest {

    /** start=2, landing=8; X>1 @ 2, Z<5 @ 5, ICE override @ 5, F=0 @ 9. */
    private static AngleSolverState seeded() {
        AngleSolverState s = new AngleSolverState();
        s.setStartTick(2);
        s.setLandingTick(8);
        s.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 1.0));
        s.tickConstraints(5).getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.LT, 5.0));
        s.tickConstraints(5).getOverride().setSlipperiness(Slipperiness.ICE);
        s.tickConstraints(9).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 0.0));
        return s;
    }

    private static void assertField(AngleSolverState s, int tick, Constraint.Field f) {
        assertNotNull("expected data on tick " + tick, s.tickConstraintsOrNull(tick));
        assertEquals(f, s.tickConstraintsOrNull(tick).getConstraints().get(0).getField());
    }

    @Test
    public void insertShiftsEverythingAtOrPastTheIndex() {
        AngleSolverState s = seeded();
        s.onRowsInserted(5, 2);
        assertField(s, 2, Constraint.Field.X);   // before the insert: stays
        assertNull(s.tickConstraintsOrNull(5));  // slid away
        assertField(s, 7, Constraint.Field.Z);   // 5 -> 7
        assertEquals(Slipperiness.ICE, s.tickConstraintsOrNull(7).getOverride().getSlipperiness());
        assertField(s, 11, Constraint.Field.F);  // 9 -> 11
        assertEquals(2, s.getStartTick());
        assertEquals(10, s.getLandingTick());    // 8 -> 10
    }

    @Test
    public void removeDropsTheRowsDataAndSlidesTheRest() {
        AngleSolverState s = seeded();
        s.onRowsRemoved(Arrays.asList(5, 3)); // descending, like InputData.removeRows
        assertField(s, 2, Constraint.Field.X);   // untouched
        assertField(s, 7, Constraint.Field.F);   // 9 -> 7 (two removals below)
        boolean zSurvives = false;
        for (int t : s.populatedTicks()) {
            if (s.tickConstraintsOrNull(t).getConstraints().size() == 1
                    && s.tickConstraintsOrNull(t).getConstraints().get(0).getField() == Constraint.Field.Z) {
                zSurvives = true;
            }
        }
        assertTrue("tick 5's data died with its row", !zSurvives);
        assertEquals(2, s.getStartTick());
        assertEquals(6, s.getLandingTick());     // 8 -> 6
    }

    @Test
    public void removingTheStartRowKeepsTheFlagOnTheReplacement() {
        AngleSolverState s = seeded();
        s.onRowsRemoved(Arrays.asList(2));
        assertEquals("start stays at the index the next row slid into", 2, s.getStartTick());
        assertEquals(7, s.getLandingTick());
    }

    @Test
    public void moveRotatesTheRange() {
        AngleSolverState s = seeded();
        // Drag row 5 to the gap above row 2 (drop-line semantics: to == 2, effective dest 2).
        s.onRowMoved(5, 2);
        assertField(s, 2, Constraint.Field.Z);   // the moved row's data came along
        assertEquals(Slipperiness.ICE, s.tickConstraintsOrNull(2).getOverride().getSlipperiness());
        assertField(s, 3, Constraint.Field.X);   // 2 slid down to 3
        assertField(s, 9, Constraint.Field.F);   // outside the rotation: stays
        assertEquals("start followed its row", 3, s.getStartTick());
        assertEquals(8, s.getLandingTick());
    }

    @Test
    public void moveNoOpsOnNeighborGaps() {
        AngleSolverState s = seeded();
        s.onRowMoved(5, 5);
        s.onRowMoved(5, 6); // the gap right below itself: InputData no-ops, so must we
        assertField(s, 5, Constraint.Field.Z);
        assertEquals(2, s.getStartTick());
        assertEquals(8, s.getLandingTick());
    }

    @Test
    public void mapRowMoveMatchesTheTickRemap() {
        // gh-119: row-keyed drawer UI state (the expanded row) follows a drag-move via this mapping.
        assertEquals(2, AngleSolverState.mapRowMove(5, 5, 2));   // the moved row itself
        assertEquals(3, AngleSolverState.mapRowMove(2, 5, 2));   // inside the rotation: slides
        assertEquals(9, AngleSolverState.mapRowMove(9, 5, 2));   // outside: stays
        assertEquals(5, AngleSolverState.mapRowMove(2, 2, 6));   // forward move: effective dest 5
        assertEquals(5, AngleSolverState.mapRowMove(5, 5, 6));   // neighbor gap: no-op
        assertEquals(-1, AngleSolverState.mapRowMove(-1, 5, 2)); // "none" sentinel passes through
    }

    @Test
    public void moveForwardUsesTheEffectiveDestination() {
        AngleSolverState s = seeded();
        // Drag row 2 to the gap below row 5 (to == 6 -> effective dest 5).
        s.onRowMoved(2, 6);
        assertField(s, 5, Constraint.Field.X);   // moved row's data lands on 5
        assertField(s, 4, Constraint.Field.Z);   // 5 slid up to 4
        assertEquals(Slipperiness.ICE, s.tickConstraintsOrNull(4).getOverride().getSlipperiness());
        assertEquals(5, s.getStartTick());       // start rode the moved row
        assertEquals(8, s.getLandingTick());
    }
}
