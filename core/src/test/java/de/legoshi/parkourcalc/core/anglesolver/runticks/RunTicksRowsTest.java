package de.legoshi.parkourcalc.core.anglesolver.runticks;

import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunTicksRowsTest {

    private static InputRow sprinting() {
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.W, true);
        row.setKeyActive(InputRow.Key.SPRINT, true);
        return row;
    }

    private static TickConstraints grounded() {
        TickConstraints tc = new TickConstraints();
        tc.getOverride().setSlipperiness(Slipperiness.DEFAULT);
        return tc;
    }

    @Test
    public void aGroundedNonJumpTickInTheRangeIsARunTick() {
        assertTrue(RunTicksRows.isRunTick(sprinting(), grounded()));
    }

    @Test
    public void aJumpTickIsNeverARunTick() {
        InputRow jump = sprinting();
        jump.setKeyActive(InputRow.Key.JUMP, true);
        assertFalse("the jump itself is the takeoff, not run-up", RunTicksRows.isRunTick(jump, grounded()));
    }

    @Test
    public void anAirborneTickIsNotARunTick() {
        assertFalse("no override means the range default, which is AIR",
                RunTicksRows.isRunTick(sprinting(), new TickConstraints()));
        assertFalse(RunTicksRows.isRunTick(sprinting(), null));

        TickConstraints ice = new TickConstraints();
        ice.getOverride().setSlipperiness(Slipperiness.AIR);
        assertFalse(RunTicksRows.isRunTick(sprinting(), ice));
    }

    @Test
    public void aGroundedTickStaysARunTickWhateverElseItHolds() {
        InputRow strafing = sprinting();
        strafing.setKeyActive(InputRow.Key.A, true);
        strafing.setYaw(-104.25f);
        TickConstraints tc = grounded();
        tc.getConstraints().add(de.legoshi.parkourcalc.core.anglesolver.Constraint.range(
                de.legoshi.parkourcalc.core.anglesolver.Constraint.Field.X, 624.7, 626.3, true, true));
        assertTrue(RunTicksRows.isRunTick(strafing, tc));
    }
}
