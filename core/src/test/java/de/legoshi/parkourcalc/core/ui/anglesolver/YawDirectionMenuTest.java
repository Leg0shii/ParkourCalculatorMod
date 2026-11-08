package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YawDirectionMenuTest {

    private static AngleSolverTable table(AngleSolverState state, int rows) {
        return new AngleSolverTable(state, new Settings(), new SelectionManager(null), new ConstraintSelection(), () -> rows);
    }

    private static List<Constraint> dfOn(AngleSolverState state, int tick) {
        return state.tickConstraints(tick).getConstraints();
    }

    @Test
    public void constantYawAddsHeldFacingOnEveryRow() {
        AngleSolverState state = new AngleSolverState();
        AngleSolverTable table = table(state, 6);

        table.setYawDirection(Arrays.asList(2, 3, 4), Constraint.Op.EQ);

        for (int tick = 2; tick <= 4; tick++) {
            List<Constraint> list = dfOn(state, tick);
            assertEquals(1, list.size());
            Constraint c = list.get(0);
            assertEquals(Constraint.Field.DF, c.getField());
            assertEquals(Constraint.Op.EQ, c.getOp());
            assertEquals(0.0, c.getValue(), 0.0);
            assertFalse(c.isUnsupportedDf());
        }
        assertNull(state.tickConstraintsOrNull(1));
        assertTrue(table.anyYawDirection(Arrays.asList(3)));
    }

    @Test
    public void constantYawReplacesOneWayRowAndKeepsOtherConstraints() {
        AngleSolverState state = new AngleSolverState();
        AngleSolverTable table = table(state, 4);
        dfOn(state, 1).add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 1.5));
        table.setYawDirection(Arrays.asList(1), Constraint.Op.GE);

        table.setYawDirection(Arrays.asList(1), Constraint.Op.EQ);

        List<Constraint> list = dfOn(state, 1);
        assertEquals(2, list.size());
        assertEquals(Constraint.Field.X, list.get(0).getField());
        assertEquals(Constraint.Op.EQ, list.get(1).getOp());

        table.setYawDirection(Arrays.asList(1), null);
        assertFalse(table.anyYawDirection(Arrays.asList(1)));
        assertEquals(1, dfOn(state, 1).size());
    }
}
