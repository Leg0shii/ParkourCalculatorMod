package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FakeMinecraftAccess implements MinecraftAccess {

    public final List<AABB> worldBoxes = new ArrayList<>();
    public int[] lookedAtBlock;
    public Face lookedAtFace;
    public Vec3dCore lookedAtHitVec;
    public boolean ready = true;

    @Override public Vec3dCore getPlayerPosition() { return Vec3dCore.ZERO; }
    @Override public float getPlayerYaw() { return 0f; }
    @Override public Vec3dCore getEyePosition() { return Vec3dCore.ZERO; }
    @Override public Vec3dCore getLookDirection() { return Vec3dCore.ZERO; }
    @Override public int[] getLookedAtBlock() { return lookedAtBlock; }
    @Override public Face getLookedAtFace() { return lookedAtFace; }
    @Override public Vec3dCore getLookedAtHitVec() { return lookedAtHitVec; }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        for (AABB b : worldBoxes) {
            if (b.max.x > minX && b.min.x < maxX + 1.0
                    && b.max.y > minY && b.min.y < maxY + 1.0
                    && b.max.z > minZ && b.min.z < maxZ + 1.0) {
                out.add(b);
            }
        }
        return out;
    }

    @Override public boolean isMousePressedLeft() { return false; }
    @Override public boolean isMousePressedRight() { return false; }
    @Override public double getCursorScreenX() { return 0; }
    @Override public double getCursorScreenY() { return 0; }
    @Override public boolean isCtrlDown() { return false; }
    @Override public boolean isShiftDown() { return false; }
    @Override public boolean isReady() { return ready; }
    @Override public boolean isSinglePlayer() { return true; }
    @Override public <T> T runOnServerThread(Supplier<T> task) { return task.get(); }
}
