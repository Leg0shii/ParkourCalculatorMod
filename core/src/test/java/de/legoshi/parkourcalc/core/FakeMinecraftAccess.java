package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class FakeMinecraftAccess implements MinecraftAccess {

    public final List<AABB> worldBoxes = new ArrayList<>();
    public final Map<List<Integer>, List<AABB>> blockBoxes = new HashMap<>();
    public int[] lookedAtBlock;
    public Face lookedAtFace;
    public Vec3dCore lookedAtHitVec;
    public float playerYaw;
    public float playerPitch;
    public boolean ready = true;
    public double[] pressurePlateFootprint;
    public final Set<List<Integer>> cobwebs = new HashSet<>();
    public double insideBlockInset = 0.001;
    public Vec3dCore eyePosition = Vec3dCore.ZERO;
    public Vec3dCore lookDirection = Vec3dCore.ZERO;

    public void addBlock(int x, int y, int z, AABB... boxes) {
        List<AABB> own = new ArrayList<>(Arrays.asList(boxes));
        blockBoxes.put(Arrays.asList(x, y, z), own);
        worldBoxes.addAll(own);
    }

    @Override public Vec3dCore getPlayerPosition() { return Vec3dCore.ZERO; }
    @Override public float getPlayerYaw() { return playerYaw; }
    @Override public float getPlayerPitch() { return playerPitch; }
    @Override public Vec3dCore getEyePosition() { return eyePosition; }
    @Override public Vec3dCore getLookDirection() { return lookDirection; }
    @Override public BlockHit getLookedAtHit() {
        if (lookedAtBlock == null || lookedAtFace == null) return MinecraftAccess.super.getLookedAtHit();
        return new BlockHit(lookedAtBlock[0], lookedAtBlock[1], lookedAtBlock[2], lookedAtFace, lookedAtHitVec);
    }
    @Override public double[] getPressurePlateFootprint(int x, int y, int z) { return pressurePlateFootprint; }
    @Override public boolean isCobweb(int x, int y, int z) { return cobwebs.contains(Arrays.asList(x, y, z)); }
    @Override public double getInsideBlockInset() { return insideBlockInset; }

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

    @Override
    public List<AABB> getBlockCollisionBoxes(int x, int y, int z) {
        List<AABB> own = blockBoxes.get(Arrays.asList(x, y, z));
        if (own != null) return new ArrayList<>(own);
        return MinecraftAccess.super.getBlockCollisionBoxes(x, y, z);
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
