package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.sim.BlockOverride;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class BlockOverridePlacer {

    private static final Map<String, Object> RESOLVED = new HashMap<String, Object>();

    private BlockOverridePlacer() {
    }

    private static final class Placed {
        final ExtendedBlockStorage[] storage;
        final int index;
        final ExtendedBlockStorage section;
        final boolean created;
        final int x;
        final int y;
        final int z;
        final IBlockState previous;

        Placed(ExtendedBlockStorage[] storage, int index, ExtendedBlockStorage section, boolean created,
               int x, int y, int z, IBlockState previous) {
            this.storage = storage;
            this.index = index;
            this.section = section;
            this.created = created;
            this.x = x;
            this.y = y;
            this.z = z;
            this.previous = previous;
        }
    }

    public static Runnable place(World world, List<BlockOverride> blocks) {
        if (world == null || blocks.isEmpty()) return null;
        final List<Placed> placed = new ArrayList<Placed>();
        for (BlockOverride b : blocks) {
            IBlockState state = state(b.block);
            if (state == null) continue;
            //? if >=1.12 {
            Chunk chunk = world.getChunk(b.x >> 4, b.z >> 4);
            //?} else {
            /*Chunk chunk = world.getChunkFromChunkCoords(b.x >> 4, b.z >> 4);
            *///?}
            if (chunk == null || chunk.isEmpty()) continue;
            ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
            int index = b.y >> 4;
            if (b.y < 0 || index >= storage.length) continue;
            ExtendedBlockStorage section = storage[index];
            boolean created = section == null;
            if (created) {
                section = new ExtendedBlockStorage(index << 4, true);
                storage[index] = section;
            }
            int lx = b.x & 15;
            int ly = b.y & 15;
            int lz = b.z & 15;
            IBlockState previous = section.get(lx, ly, lz);
            section.set(lx, ly, lz, state);
            placed.add(new Placed(storage, index, section, created, lx, ly, lz, previous));
        }
        if (placed.isEmpty()) return null;
        return new Runnable() {
            @Override
            public void run() {
                for (int i = placed.size() - 1; i >= 0; i--) {
                    Placed p = placed.get(i);
                    p.section.set(p.x, p.y, p.z, p.previous);
                    if (p.created && p.storage[p.index] == p.section) p.storage[p.index] = null;
                }
            }
        };
    }

    public static IBlockState state(String spec) {
        Object r = resolve(spec);
        return r instanceof IBlockState ? (IBlockState) r : null;
    }

    public static String error(String spec) {
        Object r = resolve(spec);
        return r instanceof String ? (String) r : null;
    }

    public static String describe(IBlockState state) {
        Map<String, String> props = new LinkedHashMap<String, String>();
        for (Object o : state.getProperties().entrySet()) {
            Map.Entry e = (Map.Entry) o;
            IProperty p = (IProperty) e.getKey();
            props.put(p.getName(), p.getName((Comparable) e.getValue()));
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
        Block block = Block.getBlockFromName(parsed.id);
        if (block == null) return "Unknown block " + parsed.id;
        IBlockState state = block.getDefaultState();
        for (Map.Entry<String, String> e : parsed.properties.entrySet()) {
            IProperty property = null;
            for (Object o : block.getBlockState().getProperties()) {
                IProperty p = (IProperty) o;
                if (p.getName().equals(e.getKey())) property = p;
            }
            if (property == null) return parsed.id + " has no state '" + e.getKey() + "'";
            Comparable value = null;
            for (Object v : property.getAllowedValues()) {
                if (property.getName((Comparable) v).equals(e.getValue())) value = (Comparable) v;
            }
            if (value == null) return "'" + e.getKey() + "' cannot be '" + e.getValue() + "'";
            state = state.withProperty(property, value);
        }
        return state;
    }

    private static String idOf(Block block) {
        //? if >=1.12 {
        return String.valueOf(Block.REGISTRY.getNameForObject(block));
        //?} else {
        /*return String.valueOf(Block.blockRegistry.getNameForObject(block));
        *///?}
    }
}
