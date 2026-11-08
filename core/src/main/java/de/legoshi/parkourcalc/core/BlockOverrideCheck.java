package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.CollisionRayTrace;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockOverrideCheck {

    public interface Reach {
        double eyeHeight(boolean sneaking);

        Vec3dCore look(float yaw, float pitch);

        double blockReach();

        List<AABB> collisionBoxes(int x, int y, int z);

        boolean chunkLoaded(int blockX, int blockZ);
    }

    private BlockOverrideCheck() {
    }

    public static Reach reachOf(final MinecraftAccess mc) {
        return new Reach() {
            @Override
            public double eyeHeight(boolean sneaking) {
                return mc.getEyeHeight(sneaking);
            }

            @Override
            public Vec3dCore look(float yaw, float pitch) {
                return mc.getLookVector(yaw, pitch);
            }

            @Override
            public double blockReach() {
                return mc.getBlockReach();
            }

            @Override
            public List<AABB> collisionBoxes(int x, int y, int z) {
                return mc.getBlockCollisionBoxes(x, y, z);
            }

            @Override
            public boolean chunkLoaded(int blockX, int blockZ) {
                return mc.isChunkLoaded(blockX >> 4, blockZ >> 4);
            }
        };
    }

    public static Map<BlockOverride, String> run(InputData inputData, BoxController boxes, Reach reach) {
        return run(inputData, boxes, reach, null);
    }

    public static Map<BlockOverride, String> run(InputData inputData, BoxController boxes, Reach reach, List<BlockOverride> waitingForChunks) {
        Map<BlockOverride, String> out = new LinkedHashMap<BlockOverride, String>();
        List<InputRow> rows = inputData.getRows();
        List<TickState> states = boxes.getStates();
        for (BlockOverride b : inputData.getBlockOverrides().all()) {
            if (!b.player || b.spec().isAir()) continue;
            int row = b.startTick - 1;
            if (row < 0 || row >= rows.size()) continue;
            if (!rows.get(row).isKeyActive(InputRow.Key.RIGHT_CLICK)) {
                out.put(b, problem(b, "no right click"));
                continue;
            }
            if (row + 1 >= states.size()) continue;
            Vec3dCore p = boxes.getPosition(row);
            Vec3dCore eye = new Vec3dCore(p.x, p.y + reach.eyeHeight(states.get(row).sneaking), p.z);
            if (!reach.chunkLoaded((int) Math.floor(eye.x), (int) Math.floor(eye.z)) || !reach.chunkLoaded(b.x, b.z)) {
                if (waitingForChunks != null) waitingForChunks.add(b);
                continue;
            }
            Vec3dCore look = reach.look(states.get(row + 1).yaw, boxes.getPitch(row + 1));
            BlockHit hit = CollisionRayTrace.trace(worldAt(inputData, row, reach), eye, look, reach.blockReach());
            if (hit == null) {
                out.put(b, problem(b, "the right click hits nothing in reach"));
                continue;
            }
            int[] lands = BlockPlacing.placementTarget(hit);
            if (lands[0] != b.x || lands[1] != b.y || lands[2] != b.z) {
                out.put(b, problem(b, "the right click lands at " + lands[0] + " " + lands[1] + " " + lands[2]));
            }
        }
        return out.isEmpty() ? Collections.<BlockOverride, String>emptyMap() : out;
    }

    private static String problem(BlockOverride b, String reason) {
        return "Not placed at tick " + b.startTick + ": " + reason;
    }

    private static CollisionRayTrace.World worldAt(InputData inputData, int row, final Reach reach) {
        final Map<Long, Boolean> solid = new HashMap<Long, Boolean>();
        for (BlockPatch p : inputData.getBlockOverrides().patchesAtRow(row)) {
            solid.put(p.posKey(), !BlockOverride.Spec.parse(p.block).isAir());
        }
        for (BlockOverride b : inputData.getBlockOverrides().all()) {
            if (b.player && b.startTick == row + 1) solid.put(BlockPatch.posKey(b.x, b.y, b.z), !b.beforeSpec().isAir());
        }
        return new CollisionRayTrace.World() {
            @Override
            public List<AABB> collisionBoxes(int x, int y, int z) {
                Boolean patched = solid.get(BlockPatch.posKey(x, y, z));
                if (patched == null) return reach.collisionBoxes(x, y, z);
                if (!patched) return Collections.emptyList();
                return Collections.singletonList(new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1, y + 1, z + 1)));
            }

            @Override
            public boolean pickWithoutCollision(int x, int y, int z) {
                return false;
            }
        };
    }
}
