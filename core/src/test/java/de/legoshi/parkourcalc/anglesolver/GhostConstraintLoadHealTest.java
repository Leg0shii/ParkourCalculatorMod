package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class GhostConstraintLoadHealTest {

    private static SaveFile fileWithConstraintAtTick(int tick, int rowCount, int landingTick) {
        SaveFile f = new SaveFile();
        f.rows = new ArrayList<SaveFile.Row>();
        for (int i = 0; i < rowCount; i++) f.rows.add(new SaveFile.Row());
        f.angleSolver = new SaveFile.AngleSolver();
        f.angleSolver.startTick = 0;
        f.angleSolver.landingTick = landingTick;
        f.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        SaveFile.Tick t = new SaveFile.Tick();
        t.tick = tick;
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "X";
        c.op = "LE";
        c.value = 718.7;
        t.constraints.add(c);
        f.angleSolver.ticks.add(t);
        return f;
    }

    @Test
    public void constraintPastTheLastRowClampsOntoTheLastRowOnLoad() {
        SaveFile f = fileWithConstraintAtTick(84, 84, 61);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state, 84);

        assertNull("ghost slot is gone", state.tickConstraintsOrNull(84));
        assertNotNull("constraint healed onto the last row", state.tickConstraintsOrNull(83));
        assertEquals(1, state.tickConstraintsOrNull(83).getConstraints().size());
        assertEquals(61, state.getLandingTick());
    }

    @Test
    public void staleStartAndLandingTicksClampOnLoad() {
        SaveFile f = fileWithConstraintAtTick(2, 4, 9);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state, 4);

        assertEquals(3, state.getLandingTick());
        assertNotNull(state.tickConstraintsOrNull(2));
    }

    @Test
    public void withoutRowCountTicksApplyUnclamped() {
        SaveFile f = fileWithConstraintAtTick(84, 84, 61);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);

        assertNotNull(state.tickConstraintsOrNull(84));
    }
}
