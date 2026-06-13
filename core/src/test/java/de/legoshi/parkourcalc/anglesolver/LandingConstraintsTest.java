package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LandingConstraintsTest {

    private static final double HALF = 0.3;
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

    @Test
    public void generatesFootprintRangesForSampleBlock() {
        AngleSolverState state = new AngleSolverState();
        int bx = 10, by = 64, bz = -3, tick = 5;
        state.addLandingConstraintsForBlock(bx, by, bz, tick);

        TickConstraints tc = state.tickConstraintsOrNull(tick);
        assertNotNull("the selected tick should now hold constraints", tc);
        List<Constraint> list = tc.getConstraints();
        assertEquals("exactly an X and a Z range", 2, list.size());

        Constraint x = fieldRange(list, Constraint.Field.X);
        assertNotNull("an X footprint range", x);
        assertEquals(bx - HALF, x.getLo(), EPS);
        assertEquals((bx + 1.0) + HALF, x.getHi(), EPS);
        assertTrue("footprint ends are inclusive", x.isLoInclusive() && x.isHiInclusive());

        Constraint z = fieldRange(list, Constraint.Field.Z);
        assertNotNull("a Z footprint range", z);
        assertEquals(bz - HALF, z.getLo(), EPS);
        assertEquals((bz + 1.0) + HALF, z.getHi(), EPS);
        assertTrue("footprint ends are inclusive", z.isLoInclusive() && z.isHiInclusive());
    }

    @Test
    public void marksSelectedTickAsLandingTick() {
        AngleSolverState state = new AngleSolverState();
        state.setLandingTick(0);
        state.addLandingConstraintsForBlock(0, 64, 0, 7);
        assertEquals("the selected tick becomes the landing/goal tick", 7, state.getLandingTick());
    }

    @Test
    public void repeatedPickReplacesRatherThanStacks() {
        AngleSolverState state = new AngleSolverState();
        int tick = 3;
        state.addLandingConstraintsForBlock(1, 64, 1, tick);
        state.addLandingConstraintsForBlock(20, 70, 20, tick);

        List<Constraint> list = state.tickConstraintsOrNull(tick).getConstraints();
        assertEquals("still exactly one X + one Z range", 2, list.size());
        Constraint x = fieldRange(list, Constraint.Field.X);
        assertEquals("X range reflects the latest block", 20 - HALF, x.getLo(), EPS);
        assertEquals(21.0 + HALF, x.getHi(), EPS);
    }

    @Test
    public void keepsUnrelatedConstraintsOnTheTick() {
        AngleSolverState state = new AngleSolverState();
        int tick = 4;
        List<Constraint> list = state.tickConstraints(tick).getConstraints();
        list.add(Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 12.5));
        list.add(Constraint.scalar(Constraint.Field.F, Constraint.Op.EQ, 45.0));

        state.addLandingConstraintsForBlock(2, 64, 2, tick);

        List<Constraint> after = state.tickConstraintsOrNull(tick).getConstraints();
        assertEquals(4, after.size());
        boolean keptWall = false, keptFacing = false;
        for (Constraint c : after) {
            if (c.getField() == Constraint.Field.X && !c.isRange() && c.getOp() == Constraint.Op.LE) keptWall = true;
            if (c.getField() == Constraint.Field.F && c.getOp() == Constraint.Op.EQ) keptFacing = true;
        }
        assertTrue("the scalar X wall is kept", keptWall);
        assertTrue("the F constraint is kept", keptFacing);
    }

    @Test
    public void ignoresNegativeTick() {
        AngleSolverState state = new AngleSolverState();
        state.addLandingConstraintsForBlock(0, 64, 0, -1);
        assertTrue("no tick should be populated for an invalid selection", state.populatedTicks().isEmpty());
    }
}
