package de.legoshi.parkourcalc.fabric.sim.paired;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldJournal {

    public static final class Entry {
        public final int tick;
        public final BlockPos pos;
        public final BlockState before;
        public final BlockState after;

        Entry(int tick, BlockPos pos, BlockState before, BlockState after) {
            this.tick = tick;
            this.pos = pos;
            this.before = before;
            this.after = after;
        }
    }

    private record PendingWrite(BlockPos pos, BlockState before) {
    }

    private static WorldJournal active;

    private final ServerLevel level;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<BlockPos, BlockState> clientView = new HashMap<>();
    private final ArrayDeque<PendingWrite> pendingWrites = new ArrayDeque<>();
    private boolean recording;
    private int currentTick;

    public WorldJournal(ServerLevel level) {
        this.level = level;
    }

    public static boolean isSuppressing(Level level) {
        return active != null && active.level == level;
    }

    public static void onSetBlockHead(Level level, BlockPos pos) {
        if (active == null || active.level != level || !active.recording) return;
        active.pendingWrites.push(new PendingWrite(pos.immutable(), level.getBlockState(pos)));
    }

    public static void onSetBlockReturn(Level level, boolean changed) {
        if (active == null || active.level != level || !active.recording) return;
        PendingWrite write = active.pendingWrites.poll();
        if (write == null) return;
        BlockState current = level.getBlockState(write.pos());
        if (!changed || current == write.before()) return;
        active.entries.add(new Entry(active.currentTick, write.pos(), write.before(), current));
        active.clientView.putIfAbsent(write.pos(), write.before());
    }

    public void beginWindow(int tick, boolean record) {
        active = this;
        currentTick = tick;
        recording = record;
        pendingWrites.clear();
    }

    public void endWindow() {
        recording = false;
        pendingWrites.clear();
        active = null;
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public void revertTo(int size) {
        if (entries.size() <= size) return;
        beginWindow(currentTick, false);
        try {
            for (int i = entries.size() - 1; i >= size; i--) {
                Entry entry = entries.get(i);
                level.setBlock(entry.pos, entry.before, Block.UPDATE_KNOWN_SHAPE);
            }
        } finally {
            endWindow();
        }
        entries.subList(size, entries.size()).clear();
    }

    public void rewindForReplay(int startTick) {
        if (entries.isEmpty()) return;
        beginWindow(currentTick, false);
        try {
            for (Entry entry : entries) {
                if (entry.tick < startTick) {
                    level.setBlock(entry.pos, entry.after, Block.UPDATE_KNOWN_SHAPE);
                }
            }
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                if (entry.tick >= startTick) {
                    level.setBlock(entry.pos, entry.before, Block.UPDATE_KNOWN_SHAPE);
                }
            }
        } finally {
            endWindow();
        }
        notifyNetChanges();
    }

    public void reapplyAfterReplay() {
        if (entries.isEmpty()) return;
        beginWindow(currentTick, false);
        try {
            for (Entry entry : entries) {
                level.setBlock(entry.pos, entry.after, Block.UPDATE_KNOWN_SHAPE);
            }
        } finally {
            endWindow();
        }
        notifyNetChanges();
    }

    public void notifyNetChanges() {
        for (Map.Entry<BlockPos, BlockState> viewed : clientView.entrySet()) {
            BlockState current = level.getBlockState(viewed.getKey());
            if (current != viewed.getValue()) {
                level.getChunkSource().blockChanged(viewed.getKey());
                viewed.setValue(current);
            }
        }
    }

    public void shutdown() {
        revertTo(0);
        notifyNetChanges();
        clientView.clear();
    }
}
