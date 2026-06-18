package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SaveStartVelocityTest {

    private static SaveFile.Start start(double[] vel) {
        SaveFile.Start s = new SaveFile.Start();
        s.pos = new double[]{0.0, 0.0, 0.0};
        s.vel = vel;
        s.yaw = 0.0f;
        return s;
    }

    @Test
    public void missingVelocityDefaultsToGroundRest() {
        assertEquals(Vec3dCore.GROUND_REST_VELOCITY, SaveIO.velOf(start(null)));
    }

    @Test
    public void legacyExplicitZeroVelocityLoadsAsGroundRest() {
        assertEquals(Vec3dCore.GROUND_REST_VELOCITY, SaveIO.velOf(start(new double[]{0.0, 0.0, 0.0})));
    }

    @Test
    public void editedVelocityIsPreserved() {
        Vec3dCore v = new Vec3dCore(0.1, -0.2, 0.3);
        assertEquals(v, SaveIO.velOf(start(new double[]{0.1, -0.2, 0.3})));
    }
}
