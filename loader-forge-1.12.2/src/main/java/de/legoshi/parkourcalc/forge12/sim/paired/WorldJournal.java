package de.legoshi.parkourcalc.forge12.sim.paired;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
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

    private static final int SILENT_FLAGS = 16;

    private final WorldServer level;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final Map<BlockPos, IBlockState> clientView = new HashMap<BlockPos, IBlockState>();
    private List<IWorldEventListener> suppressedListeners;
    private int currentTick;

    public WorldJournal(WorldServer level) {
        this.level = level;
    }

    public void beginWindow(int tick) {
        currentTick = tick;
        if (suppressedListeners == null) {
            suppressedListeners = new ArrayList<IWorldEventListener>(level.eventListeners);
            level.eventListeners.clear();
        }
    }

    public void endWindow() {
        if (suppressedListeners != null) {
            level.eventListeners.addAll(suppressedListeners);
            suppressedListeners = null;
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
            level.setBlockState(entry.pos, entry.before, SILENT_FLAGS);
        }
        entries.subList(size, entries.size()).clear();
    }

    public void rewindForReplay(int startTick) {
        if (entries.isEmpty()) return;
        for (Entry entry : entries) {
            if (entry.tick < startTick) {
                level.setBlockState(entry.pos, entry.after, SILENT_FLAGS);
            }
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.tick >= startTick) {
                level.setBlockState(entry.pos, entry.before, SILENT_FLAGS);
            }
        }
        notifyNetChanges();
    }

    public void reapplyAfterReplay() {
        if (entries.isEmpty()) return;
        for (Entry entry : entries) {
            level.setBlockState(entry.pos, entry.after, SILENT_FLAGS);
        }
        notifyNetChanges();
    }

    public void notifyNetChanges() {
        for (Map.Entry<BlockPos, IBlockState> viewed : clientView.entrySet()) {
            IBlockState current = level.getBlockState(viewed.getKey());
            if (current != viewed.getValue()) {
                level.notifyBlockUpdate(viewed.getKey(), current, current, 3);
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
