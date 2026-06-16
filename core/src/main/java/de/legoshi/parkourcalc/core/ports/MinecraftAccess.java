package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.Supplier;

/**
 * Live MC client-state queries the loader exposes to core. Core uses these for
 * "snap start to where the player stands" and "cast a ray from the camera" without
 * importing any MC type. Implementations may return Vec3dCore.ZERO when the world
 * or player isn't loaded yet; callers should guard their actions accordingly.
 */
public interface MinecraftAccess {

    /** Feet position of the local player. */
    Vec3dCore getPlayerPosition();

    /** Current facing yaw of the local player, in MC degrees. */
    float getPlayerYaw();

    /** Eye / camera position for picking and ray-casts. */
    Vec3dCore getEyePosition();

    /** Unit vector along the camera's look direction. */
    Vec3dCore getLookDirection();

    /** Integer coords {@code [x, y, z]} of the looked-at block, or {@code null} when nothing is targeted.
     *  Default returns null so a loader without a raycast makes the keybind a no-op instead of failing to build. */
    default int[] getLookedAtBlock() {
        return null;
    }

    default boolean isBlockSolid(int x, int y, int z) {
        return false;
    }

    /** Current state of the left mouse button (true while held). */
    boolean isMousePressedLeft();

    /** Current state of the right mouse button (true while held). */
    boolean isMousePressedRight();

    /** Cursor X in window-pixel coords (origin top-left, GLFW-style). Used by the 2 px gizmo engage threshold. */
    double getCursorScreenX();

    /** Cursor Y in window-pixel coords (origin top-left, GLFW-style). */
    double getCursorScreenY();

    /** Either control key currently held. Polled directly, not via ImGui IO. */
    boolean isCtrlDown();

    /** Whether the quick-save chord (Ctrl+S) is held right now. Loaders read their own raw keyboard
     *  (ImGui 1.86 exposes no portable letter-key ids), core edge-detects and saves (gh-107). */
    default boolean isSaveChordDown() {
        return false;
    }

    /** Either shift key currently held. Polled directly, not via ImGui IO. */
    boolean isShiftDown();

    /** True when the player and world are both available to query. */
    boolean isReady();

    /** True when the client owns the integrated server (singleplayer or LAN host). */
    boolean isSinglePlayer();

    /** SP: runs the task on the server's main thread and blocks for the result.
     *  MP: runs inline. Lets the simulator tick against ServerWorld natively. */
    <T> T runOnServerThread(Supplier<T> task);
}
