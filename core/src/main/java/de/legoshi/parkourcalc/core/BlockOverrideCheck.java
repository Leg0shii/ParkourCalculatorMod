package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.CollisionRayTrace;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
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

        List<AABB> shape(String spec);

        String blockAt(int x, int y, int z);

        boolean chunkLoaded(int blockX, int blockZ);
    }

    public static final class Pending {
        private final int[] chunks;

        Pending(int[] chunks) {
            this.chunks = chunks;
        }

        public boolean anyLoaded(Reach reach) {
            for (int i = 0; i + 1 < chunks.length; i += 2) {
                if (reach.chunkLoaded(chunks[i] << 4, chunks[i + 1] << 4)) return true;
            }
            return false;
        }

        public boolean isEmpty() {
            return chunks.length == 0;
        }
    }

    public static Pending pending(List<int[]> waitingChunks) {
        Map<Long, int[]> unique = new LinkedHashMap<Long, int[]>();
        for (int[] chunk : waitingChunks) {
            unique.put(((long) chunk[0] << 32) | (chunk[1] & 0xFFFFFFFFL), chunk);
        }
        int[] flat = new int[unique.size() * 2];
        int i = 0;
        for (int[] chunk : unique.values()) {
            flat[i++] = chunk[0];
            flat[i++] = chunk[1];
        }
        return new Pending(flat);
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
            public List<AABB> shape(String spec) {
                return mc.blockShape(spec);
            }

            @Override
            public String blockAt(int x, int y, int z) {
                return mc.describeBlockState(x, y, z);
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

    public static Map<BlockOverride, String> run(InputData inputData, BoxController boxes, Reach reach, List<int[]> waitingChunks) {
        Map<BlockOverride, String> out = new LinkedHashMap<BlockOverride, String>();
        List<InputRow> rows = inputData.getRows();
        List<TickState> states = boxes.getStates();
        BlockOverrides overrides = inputData.getBlockOverrides();
        Map<String, List<AABB>> shapes = new HashMap<String, List<AABB>>();
        for (BlockOverride b : overrides.all()) {
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
            int eyeX = (int) Math.floor(eye.x);
            int eyeZ = (int) Math.floor(eye.z);
            boolean eyeLoaded = reach.chunkLoaded(eyeX, eyeZ);
            boolean blockLoaded = reach.chunkLoaded(b.x, b.z);
            if (!eyeLoaded || !blockLoaded) {
                if (waitingChunks != null) {
                    if (!eyeLoaded) waitingChunks.add(new int[] {eyeX >> 4, eyeZ >> 4});
                    if (!blockLoaded) waitingChunks.add(new int[] {b.x >> 4, b.z >> 4});
                }
                continue;
            }
            Vec3dCore look = reach.look(states.get(row + 1).yaw, boxes.getPitch(row + 1));
            BlockHit hit = CollisionRayTrace.trace(worldAt(overrides, row, reach, shapes), eye, look, reach.blockReach());
            if (hit == null) {
                out.put(b, problem(b, "the right click hits nothing in reach"));
                continue;
            }
            if (replacesABlock(b) && hitsTheBlock(b, hit, overrides, row, reach)) continue;
            if (changesTheBlock(b)) {
                out.put(b, problem(b, "the right click hits " + hit.x + " " + hit.y + " " + hit.z));
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

    static boolean replacesABlock(BlockOverride b) {
        BlockOverride.Spec before = b.beforeSpec();
        return before.valid() && !before.isAir();
    }

    static boolean changesTheBlock(BlockOverride b) {
        return replacesABlock(b) && b.beforeSpec().id.equals(b.spec().id);
    }

    private static boolean hitsTheBlock(BlockOverride b, BlockHit hit, BlockOverrides overrides, int row, Reach reach) {
        if (hit.x != b.x || hit.z != b.z) return false;
        if (hit.y == b.y) return true;
        if (Math.abs(hit.y - b.y) != 1) return false;
        BlockOverride.Spec other = BlockOverride.Spec.parse(blockBeforeTheClick(overrides, hit.x, hit.y, hit.z, row, reach));
        return other.valid() && other.id.equals(b.spec().id) && isDoorHalf(other) && isDoorHalf(b.spec());
    }

    private static boolean isDoorHalf(BlockOverride.Spec spec) {
        String half = spec.properties.get("half");
        return "upper".equals(half) || "lower".equals(half);
    }

    private static String blockBeforeTheClick(BlockOverrides overrides, int x, int y, int z, int row, Reach reach) {
        BlockPatch patch = overrides.patchBeforeClicksAt(x, y, z, row);
        return patch != null ? patch.block : reach.blockAt(x, y, z);
    }

    private static CollisionRayTrace.World worldAt(final BlockOverrides overrides, final int row, final Reach reach,
                                                   final Map<String, List<AABB>> shapes) {
        return new CollisionRayTrace.World() {
            @Override
            public List<AABB> collisionBoxes(int x, int y, int z) {
                BlockPatch patch = overrides.patchBeforeClicksAt(x, y, z, row);
                if (patch == null) return reach.collisionBoxes(x, y, z);
                if (BlockOverride.Spec.parse(patch.block).isAir()) return Collections.emptyList();
                List<AABB> shape = shapes.get(patch.block);
                if (shape == null) {
                    shape = reach.shape(patch.block);
                    if (shape == null || shape.isEmpty()) shape = Collections.singletonList(BlockOverrideWorld.FULL_CUBE);
                    shapes.put(patch.block, shape);
                }
                List<AABB> placed = new ArrayList<AABB>(shape.size());
                for (AABB box : shape) {
                    placed.add(new AABB(new Vec3dCore(x + box.min.x, y + box.min.y, z + box.min.z),
                            new Vec3dCore(x + box.max.x, y + box.max.y, z + box.max.z)));
                }
                return placed;
            }

            @Override
            public boolean pickWithoutCollision(int x, int y, int z) {
                return false;
            }
        };
    }
}
