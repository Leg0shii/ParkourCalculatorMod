package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LandingConstraintsTest {

    private static final double HALF = ConstraintDeriver.HALF;
    private static final double EPS = 1.0e-9;

    private static Constraint fieldRange(List<Constraint> list, Constraint.Field field) {
        Constraint found = null;
        for (Constraint c : list) {
            if (c.getField() == field && c.isRange()) {
                assertTrue("only one " + field.label + " range expected", found == null);
                found = c;
            }
        }
        return found;
    }

    private static Constraint scalar(List<Constraint> list, Constraint.Field field) {
        Constraint found = null;
        for (Constraint c : list) {
            if (c.getField() == field && !c.isRange()) {
                assertTrue("only one " + field.label + " scalar expected", found == null);
                found = c;
            }
        }
        return found;
    }

    @Test
    public void setFootprintWritesInclusiveXandZRanges() {
        AngleSolverState state = new AngleSolverState();
        state.setFootprint(5, 10 - HALF, 11 + HALF, -3 - HALF, -2 + HALF);

        TickConstraints tc = state.tickConstraintsOrNull(5);
        assertNotNull(tc);
        List<Constraint> list = tc.getConstraints();
        assertEquals(2, list.size());

        Constraint x = fieldRange(list, Constraint.Field.X);
        assertNotNull(x);
        assertEquals(10 - HALF, x.getLo(), EPS);
        assertEquals(11 + HALF, x.getHi(), EPS);
        assertTrue(x.isLoInclusive() && x.isHiInclusive());

        Constraint z = fieldRange(list, Constraint.Field.Z);
        assertNotNull(z);
        assertEquals(-3 - HALF, z.getLo(), EPS);
        assertEquals(-2 + HALF, z.getHi(), EPS);
    }

    @Test
    public void setFootprintReplacesRatherThanStacks() {
        AngleSolverState state = new AngleSolverState();
        state.setFootprint(3, 1 - HALF, 2 + HALF, 1 - HALF, 2 + HALF);
        state.setFootprint(3, 20 - HALF, 21 + HALF, 20 - HALF, 21 + HALF);

        List<Constraint> list = state.tickConstraintsOrNull(3).getConstraints();
        assertEquals(2, list.size());
        Constraint x = fieldRange(list, Constraint.Field.X);
        assertEquals(20 - HALF, x.getLo(), EPS);
        assertEquals(21 + HALF, x.getHi(), EPS);
    }

    @Test
    public void setFootprintKeepsUnrelatedConstraints() {
        AngleSolverState state = new AngleSolverState();
        List<Constraint> list = state.tickConstraints(4).getConstraints();
        list.add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 45.0));

        state.setFootprint(4, 2 - HALF, 3 + HALF, 2 - HALF, 3 + HALF);

        List<Constraint> after = state.tickConstraintsOrNull(4).getConstraints();
        assertEquals(3, after.size());
        assertNotNull(scalar(after, Constraint.Field.F));
    }

    @Test
    public void clearFootprintDropsOnlyTheXandZRanges() {
        AngleSolverState state = new AngleSolverState();
        state.setFootprint(2, 0 - HALF, 1 + HALF, 0 - HALF, 1 + HALF);
        state.tickConstraints(2).getConstraints().add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 90.0));

        state.clearFootprint(2);

        List<Constraint> list = state.tickConstraintsOrNull(2).getConstraints();
        assertEquals(1, list.size());
        assertNotNull(scalar(list, Constraint.Field.F));
    }

    @Test
    public void setFootprintIgnoresNegativeTick() {
        AngleSolverState state = new AngleSolverState();
        state.setFootprint(-1, 0, 1, 0, 1);
        assertTrue(state.populatedTicks().isEmpty());
    }

    @Test
    public void putWallReplacesSameDirectionKeepsOpposite() {
        AngleSolverState state = new AngleSolverState();
        state.putScalarReplacingDirection(0, Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.3));
        state.putScalarReplacingDirection(0, Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 2.7));

        List<Constraint> list = state.tickConstraintsOrNull(0).getConstraints();
        assertEquals("corridor keeps both a lower and an upper X wall", 2, list.size());

        state.putScalarReplacingDirection(0, Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.9));
        list = state.tickConstraintsOrNull(0).getConstraints();
        assertEquals("re-writing the lower wall replaces it, not the upper", 2, list.size());

        Constraint lower = null, upper = null;
        for (Constraint c : list) {
            if (c.getOp() == Constraint.Op.GE) lower = c;
            if (c.getOp() == Constraint.Op.LE) upper = c;
        }
        assertNotNull(lower);
        assertNotNull(upper);
        assertEquals(1.9, lower.getValue(), EPS);
        assertEquals(2.7, upper.getValue(), EPS);
    }

    @Test
    public void clearWallRemovesTheMatchingDirectionOnly() {
        AngleSolverState state = new AngleSolverState();
        state.putScalarReplacingDirection(0, Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.3));
        state.putScalarReplacingDirection(0, Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 2.7));

        state.clearWall(0, Constraint.Field.X, true);

        List<Constraint> list = state.tickConstraintsOrNull(0).getConstraints();
        assertEquals(1, list.size());
        assertEquals(Constraint.Op.LE, list.get(0).getOp());
    }

    @Test
    public void clearWallOnEmptyTickIsNoOp() {
        AngleSolverState state = new AngleSolverState();
        state.clearWall(7, Constraint.Field.Z, false);
        assertNull(state.tickConstraintsOrNull(7));
    }
}
