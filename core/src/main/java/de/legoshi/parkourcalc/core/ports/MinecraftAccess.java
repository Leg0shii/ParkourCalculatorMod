package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

/**
 * Live MC client-state queries the loader exposes to core. Core uses these for
 * "snap start to where the player stands" and "cast a ray from the camera" without
 * importing any MC type. Implementations may return Vec3dCore.ZERO when the world
 * or player isn't loaded yet; callers should guard their actions accordingly.
 */
public interface MinecraftAccess {

    /** Feet position of the local player. */
    Vec3dCore getPlayerPosition();

    /** Eye / camera position for picking and ray-casts. */
    Vec3dCore getEyePosition();

    /** Unit vector along the camera's look direction. */
    Vec3dCore getLookDirection();

    /** Current state of the left mouse button (true while held). */
    boolean isMousePressedLeft();

    /** True when the player and world are both available to query. */
    boolean isReady();
}
