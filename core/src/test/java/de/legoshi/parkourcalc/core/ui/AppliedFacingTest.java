package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppliedFacingTest {

    private static BoxController controllerWithYaws(float... yaws) {
        BoxController c = new BoxController();
        for (float yaw : yaws) {
            c.add(new TickState(new Vec3dCore(0.0, 0.0, 0.0), true, false, false, yaw,
                    null, Vec3dCore.ZERO, false, Double.NaN));
        }
        return c;
    }

    @Test
    public void appliedYawIsTheTicksOwnTurnNotThePriorOne() {
        BoxController c = controllerWithYaws(0f, 45f, 90f);
        assertEquals(0f, c.getYaw(0), 0f);
        assertEquals(45f, c.getAppliedYaw(0), 0f);
        assertEquals(90f, c.getAppliedYaw(1), 0f);
    }

    @Test
    public void appliedYawFallsBackOnTheLastState() {
        BoxController c = controllerWithYaws(0f, 45f, 90f);
        assertEquals(90f, c.getAppliedYaw(2), 0f);
    }

    @Test
    public void appliedPitchIsFoldedOneRowFurtherThanPreTickPitch() {
        BoxController c = controllerWithYaws(0f, 0f, 0f);
        c.setPitches(new float[]{0f, -20f, -35f});
        assertEquals(0f, c.getPitch(0), 0f);
        assertEquals(-20f, c.getAppliedPitch(0), 0f);
        assertEquals(-35f, c.getAppliedPitch(1), 0f);
        assertEquals(-35f, c.getAppliedPitch(2), 0f);
    }
}
