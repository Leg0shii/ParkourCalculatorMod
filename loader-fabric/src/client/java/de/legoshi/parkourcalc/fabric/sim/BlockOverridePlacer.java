package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.sim.BlockOverride;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockOverridePlacer {

    private static final Map<String, Object> RESOLVED = new HashMap<>();
    private static Map<String, Block> blocksById;

    private BlockOverridePlacer() {
    }

    private record Placed(LevelChunkSection section, int x, int y, int z, BlockState previous) {
    }

    public static Runnable place(Level level, List<BlockOverride> blocks) {
        if (level == null || blocks.isEmpty()) return null;
        List<Placed> placed = new ArrayList<>();
        for (BlockOverride b : blocks) {
            BlockState state = state(b.block);
            if (state == null) continue;
            int cx = b.x >> 4;
            int cz = b.z >> 4;
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk chunk = level.getChunk(cx, cz);
            LevelChunkSection[] sections = chunk.getSections();
            int index = chunk.getSectionIndex(b.y);
            if (index < 0 || index >= sections.length || sections[index] == null) continue;
            LevelChunkSection section = sections[index];
            int lx = b.x & 15;
            int ly = b.y & 15;
            int lz = b.z & 15;
            BlockState previous = section.setBlockState(lx, ly, lz, state, false);
            placed.add(new Placed(section, lx, ly, lz, previous));
        }
        if (placed.isEmpty()) return null;
        return () -> {
            for (int i = placed.size() - 1; i >= 0; i--) {
                Placed p = placed.get(i);
                p.section().setBlockState(p.x(), p.y(), p.z(), p.previous(), false);
            }
        };
    }

    public static BlockState state(String spec) {
        Object r = resolve(spec);
        return r instanceof BlockState s ? s : null;
    }

    public static String error(String spec) {
        Object r = resolve(spec);
        return r instanceof String s ? s : null;
    }

    public static String describe(BlockState state) {
        Map<String, String> props = new LinkedHashMap<>();
        for (Property<?> p : state.getProperties()) {
            props.put(p.getName(), valueName(state, p));
        }
        return BlockOverride.Spec.format(idOf(state.getBlock()), props);
    }

    private static synchronized Object resolve(String spec) {
        Object cached = RESOLVED.get(spec);
        if (cached != null) return cached;
        Object r = parse(spec);
        RESOLVED.put(spec, r);
        return r;
    }

    private static Object parse(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return parsed.error;
        Block block = blocks().get(parsed.id);
        if (block == null) return "Unknown block " + parsed.id;
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> e : parsed.properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(e.getKey());
            if (property == null) return parsed.id + " has no state '" + e.getKey() + "'";
            BlockState next = withValue(state, property, e.getValue());
            if (next == null) return "'" + e.getKey() + "' cannot be '" + e.getValue() + "'";
            state = next;
        }
        return state;
    }

    private static Map<String, Block> blocks() {
        if (blocksById == null) {
            Map<String, Block> map = new HashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) map.put(idOf(block), block);
            blocksById = map;
        }
        return blocksById;
    }

    private static String idOf(Block block) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        for (T v : property.getPossibleValues()) {
            if (property.getName(v).equals(value)) return state.setValue(property, v);
        }
        return null;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
