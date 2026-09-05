package de.legoshi.parkourcalc.core.ui.util;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public final class TeleportCommand {

    private TeleportCommand() {
    }

    public static String format(Vec3dCore position, float yaw, float pitch) {
        return "/tp " + position.x + " " + position.y + " " + position.z
                + " " + wrapDegrees(yaw) + " " + pitch;
    }

    private static float wrapDegrees(float value) {
        float f = value % 360.0f;
        if (f >= 180.0f) f -= 360.0f;
        if (f < -180.0f) f += 360.0f;
        return f;
    }
}
