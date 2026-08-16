package de.legoshi.parkourcalc.forge8.sim.paired;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldJournal {

    public static final class Entry {
        public final int tick;
        public final BlockPos pos;
        public final IBlockState before;
        public final IBlockState after;

        Entry(int tick, BlockPos pos, IBlockState before, IBlockState after) {
            this.tick = tick;
            this.pos = pos;
            this.before = before;
            this.after = after;
        }
    }

    private final WorldServer level;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final Map<BlockPos, IBlockState> clientView = new HashMap<BlockPos, IBlockState>();
    private List<IWorldAccess> suppressedAccesses;
    private int currentTick;

    public WorldJournal(WorldServer level) {
        this.level = level;
    }

    public void beginWindow(int tick) {
        currentTick = tick;
        if (suppressedAccesses == null) {
            suppressedAccesses = new ArrayList<IWorldAccess>(level.worldAccesses);
            level.worldAccesses.clear();
        }
    }

    public void endWindow() {
        if (suppressedAccesses != null) {
            level.worldAccesses.addAll(suppressedAccesses);
            suppressedAccesses = null;
        }
    }

    public void record(BlockPos pos, IBlockState before, IBlockState after) {
        entries.add(new Entry(currentTick, pos, before, after));
        if (!clientView.containsKey(pos)) {
            clientView.put(pos, before);
        }
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public void revertTo(int size) {
        if (entries.size() <= size) return;
        for (int i = entries.size() - 1; i >= size; i--) {
            Entry entry = entries.get(i);
            level.setBlockState(entry.pos, entry.before, 0);
        }
        entries.subList(size, entries.size()).clear();
    }

    public void rewindForReplay(int startTick) {
        if (entries.isEmpty()) return;
        for (Entry entry : entries) {
            if (entry.tick < startTick) {
                level.setBlockState(entry.pos, entry.after, 0);
            }
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.tick >= startTick) {
                level.setBlockState(entry.pos, entry.before, 0);
            }
        }
        notifyNetChanges();
    }

    public void reapplyAfterReplay() {
        if (entries.isEmpty()) return;
        for (Entry entry : entries) {
            level.setBlockState(entry.pos, entry.after, 0);
        }
        notifyNetChanges();
    }

    public void notifyNetChanges() {
        for (Map.Entry<BlockPos, IBlockState> viewed : clientView.entrySet()) {
            IBlockState current = level.getBlockState(viewed.getKey());
            if (current != viewed.getValue()) {
                level.markBlockForUpdate(viewed.getKey());
                viewed.setValue(current);
            }
        }
    }

    public void shutdown() {
        endWindow();
        revertTo(0);
        notifyNetChanges();
        clientView.clear();
    }
}
