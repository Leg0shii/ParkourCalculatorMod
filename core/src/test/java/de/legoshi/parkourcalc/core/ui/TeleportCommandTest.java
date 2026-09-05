package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.util.TeleportCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TeleportCommandTest {

    @Test
    public void keepsFullPrecisionCoordinates() {
        String cmd = TeleportCommand.format(new Vec3dCore(1.5, 64.0, -2.25), 0f, 0f);
        assertEquals("/tp 1.5 64.0 -2.25 0.0 0.0", cmd);
    }

    @Test
    public void wrapsYawIntoF3Range() {
        Vec3dCore p = new Vec3dCore(0.0, 0.0, 0.0);
        assertEquals("/tp 0.0 0.0 0.0 -90.0 0.0", TeleportCommand.format(p, 270f, 0f));
        assertEquals("/tp 0.0 0.0 0.0 -180.0 0.0", TeleportCommand.format(p, 180f, 0f));
        assertEquals("/tp 0.0 0.0 0.0 45.0 0.0", TeleportCommand.format(p, 765f, 0f));
        assertEquals("/tp 0.0 0.0 0.0 -45.0 0.0", TeleportCommand.format(p, -765f, 0f));
    }

    @Test
    public void keepsPitchUnwrapped() {
        assertEquals("/tp 0.0 0.0 0.0 0.0 -37.5", TeleportCommand.format(new Vec3dCore(0.0, 0.0, 0.0), 0f, -37.5f));
    }
}
