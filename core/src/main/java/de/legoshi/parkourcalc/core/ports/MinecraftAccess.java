package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.CollisionRayTrace;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Live MC client-state queries the loader exposes to core. Core uses these for
 * "snap start to where the player stands" and "cast a ray from the camera" without
 * importing any MC type. Implementations may return Vec3dCore.ZERO when the world
 * or player isn't loaded yet; callers should guard their actions accordingly.
 */
public interface MinecraftAccess {

    double PICK_REACH = 64.0;

    /** Feet position of the local player. */
    Vec3dCore getPlayerPosition();

    /** Current facing yaw of the local player, in MC degrees. */
    float getPlayerYaw();

    float getPlayerPitch();

    /** Eye / camera position for picking and ray-casts. */
    Vec3dCore getEyePosition();

    /** Unit vector along the camera's look direction. */
    Vec3dCore getLookDirection();

    default boolean isBlockSolid(int x, int y, int z) {
        return false;
    }

    default boolean isClimbable(int x, int y, int z) {
        return false;
    }

    default boolean isSlimeBlock(int x, int y, int z) {
        return false;
    }

    default boolean isIce(int x, int y, int z) {
        return false;
    }

    default double[] getPressurePlateFootprint(int x, int y, int z) {
        return null;
    }

    default boolean isCobweb(int x, int y, int z) {
        return false;
    }

    double getInsideBlockInset();

    default AABB getCobwebContactBox(int x, int y, int z) {
        if (!isCobweb(x, y, z)) return null;
        double inset = getInsideBlockInset();
        return new AABB(
                new Vec3dCore(x + inset, y + inset, z + inset),
                new Vec3dCore(x + 1.0 - inset, y + 1.0 - inset, z + 1.0 - inset));
    }

    default BlockHit getLookedAtHit() {
        CollisionRayTrace.World world = new CollisionRayTrace.World() {
            @Override
            public List<AABB> collisionBoxes(int x, int y, int z) {
                List<AABB> boxes = getBlockCollisionBoxes(x, y, z);
                if (boxes != null && !boxes.isEmpty()) return boxes;
                AABB cobweb = getCobwebContactBox(x, y, z);
                return cobweb == null ? boxes : Collections.singletonList(cobweb);
            }

            @Override
            public boolean pickWithoutCollision(int x, int y, int z) {
                return getPressurePlateFootprint(x, y, z) != null || isClimbable(x, y, z);
            }
        };
        return CollisionRayTrace.trace(world, getEyePosition(), getLookDirection(), PICK_REACH);
    }

    default List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return Collections.emptyList();
    }

    default List<AABB> getBlockCollisionBoxes(int x, int y, int z) {
        return getCollisionBoxes(x, y, z, x, y, z);
    }

    default double getEyeHeight(boolean sneaking) {
        return 1.62;
    }

    default double clipBlockDistance(Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        return -1.0;
    }

    default double getBlockReach() {
        return 4.5;
    }

    default Vec3dCore getLookVector(float yawDeg, float pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double cosP = Math.cos(pitchRad);
        return new Vec3dCore(-Math.sin(yawRad) * cosP, -Math.sin(pitchRad), Math.cos(yawRad) * cosP);
    }

    /** Current state of the left mouse button (true while held). */
    boolean isMousePressedLeft();

    default boolean isSelectTickPressed() {
        return isMousePressedLeft();
    }

    /** Current state of the right mouse button (true while held). */
    boolean isMousePressedRight();

    /** Cursor X in window-pixel coords (origin top-left, GLFW-style). Used by the 2 px gizmo engage threshold. */
    double getCursorScreenX();

    /** Cursor Y in window-pixel coords (origin top-left, GLFW-style). */
    double getCursorScreenY();

    /** Either control key currently held. Polled directly, not via ImGui IO. */
    boolean isCtrlDown();

    /** Either alt key currently held. Polled directly, not via ImGui IO. */
    default boolean isAltDown()  {
        return false;
    }

    /** Whether the quick-save chord (Ctrl+S) is held right now. Loaders read their own raw keyboard
     *  (ImGui 1.86 exposes no portable letter-key ids), core edge-detects and saves (gh-107). */
    default boolean isSaveChordDown() {
        return false;
    }

    default boolean isUndoChordDown() {
        return false;
    }

    default boolean isRedoChordDown() {
        return false;
    }

    default boolean isCopyChordDown() {
        return false;
    }

    default boolean isPasteChordDown() {
        return false;
    }

    /** Either shift key currently held. Polled directly, not via ImGui IO. */
    boolean isShiftDown();

    /** True when the player and world are both available to query. */
    boolean isReady();

    default float getMouseSensitivity() {
        return 0.5f;
    }

    /** True when the client owns the integrated server (singleplayer or LAN host). */
    boolean isSinglePlayer();

    default boolean isChunkLoaded(int chunkX, int chunkZ) {
        return true;
    }

    default String getRerunSimulationKeyName() {
        return null;
    }

    /** SP: runs the task on the server's main thread and blocks for the result.
     *  MP: runs inline. Lets the simulator tick against ServerWorld natively. */
    <T> T runOnServerThread(Supplier<T> task);
}
