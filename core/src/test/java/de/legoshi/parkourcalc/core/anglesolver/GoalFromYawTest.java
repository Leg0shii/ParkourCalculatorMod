package de.legoshi.parkourcalc.core.anglesolver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GoalFromYawTest {

    private static AngleSolverState facing(double yaw) {
        AngleSolverState state = new AngleSolverState();
        state.setCustomAngle(true);
        state.setGoalFromYaw(yaw);
        assertFalse(state.isCustomAngle());
        return state;
    }

    @Test
    public void southLooksTowardPlusZ() {
        AngleSolverState s = facing(0.0);
        assertEquals(AngleSolverState.Axis.Z, s.getAxis());
        assertEquals(AngleSolverState.Goal.MAX, s.getGoal());
        assertEquals("+Z", s.goalLabel());
    }

    @Test
    public void westLooksTowardMinusX() {
        AngleSolverState s = facing(90.0);
        assertEquals(AngleSolverState.Axis.X, s.getAxis());
        assertEquals(AngleSolverState.Goal.MIN, s.getGoal());
        assertEquals("-X", s.goalLabel());
    }

    @Test
    public void northLooksTowardMinusZ() {
        AngleSolverState s = facing(180.0);
        assertEquals(AngleSolverState.Axis.Z, s.getAxis());
        assertEquals(AngleSolverState.Goal.MIN, s.getGoal());
        assertEquals("-Z", facing(-180.0).goalLabel());
    }

    @Test
    public void eastLooksTowardPlusX() {
        AngleSolverState s = facing(-90.0);
        assertEquals(AngleSolverState.Axis.X, s.getAxis());
        assertEquals(AngleSolverState.Goal.MAX, s.getGoal());
        assertEquals("+X", facing(270.0).goalLabel());
    }

    @Test
    public void unwrappedYawsAndDiagonalsSnapToTheNearestAxis() {
        assertEquals("+Z", facing(360.0 * 3 + 30.0).goalLabel());
        assertEquals("-X", facing(50.0).goalLabel());
        assertEquals("-Z", facing(-140.0).goalLabel());
        assertEquals("+X", facing(-50.0).goalLabel());
    }

    @Test
    public void theCustomAngleLabelShowsTheFacing() {
        AngleSolverState s = new AngleSolverState();
        s.setCustomAngle(true);
        s.setCustomAngleDeg(45.0);
        assertEquals("angle 45.00", s.goalLabel());
    }
}
