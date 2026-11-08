package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.SelectionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockOverrideWorld {

    private final InputData inputData;
    private final SelectionManager selection;
    private BlockPatcher patcher;
    private int replayRow = -1;
    private int appliedRow = Integer.MIN_VALUE;
    private int appliedModCount = -1;
    private final Map<Long, String> appliedBefore = new LinkedHashMap<Long, String>();

    public BlockOverrideWorld(InputData inputData, SelectionManager selection) {
        this.inputData = inputData;
        this.selection = selection;
    }

    public void setPatcher(BlockPatcher patcher) {
        this.patcher = patcher;
    }

    public boolean isAvailable() {
        return patcher != null && patcher.isAvailable();
    }

    public int viewRow() {
        if (replayRow >= 0) return replayRow;
        int highest = -1;
        for (int row : selection.getSelectedRows()) {
            if (row > highest) highest = row;
        }
        return highest >= 0 ? highest : inputData.size() - 1;
    }

    public List<BlockOverride> rows() {
        return inputData.getBlockOverrides().all();
    }

    public boolean isReplaying() {
        return replayRow >= 0;
    }

    public void setReplayRow(int row) {
        replayRow = Math.max(-1, row);
        refresh(true);
    }

    public void clearReplayRow() {
        setReplayRow(-1);
    }

    public void tick() {
        refresh(false);
    }

    public void refresh(boolean force) {
        if (!isAvailable()) return;
        BlockOverrides overrides = inputData.getBlockOverrides();
        int row = viewRow();
        if (!force && row == appliedRow && overrides.modCount() == appliedModCount && appliedBefore.size() == overrides.size()) return;
        appliedRow = row;
        appliedModCount = overrides.modCount();
        apply(overrides.patchesAtRow(row), overrides.all());
    }

    public void releaseAll() {
        if (appliedBefore.isEmpty() || !isAvailable()) {
            appliedBefore.clear();
            appliedRow = Integer.MIN_VALUE;
            return;
        }
        List<BlockPatch> restore = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String> e : appliedBefore.entrySet()) {
            BlockPatch p = unpack(e.getKey(), e.getValue());
            if (p != null) restore.add(p);
        }
        appliedBefore.clear();
        appliedRow = Integer.MIN_VALUE;
        appliedModCount = -1;
        if (!restore.isEmpty()) patcher.apply(restore);
    }

    private void apply(List<BlockPatch> patches, List<BlockOverride> rows) {
        Map<Long, String> nextBefore = new LinkedHashMap<Long, String>();
        for (BlockOverride b : rows) {
            if (!b.beforeSpec().valid()) continue;
            nextBefore.put(BlockPatch.posKey(b.x, b.y, b.z), b.before);
        }
        List<BlockPatch> out = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String> e : appliedBefore.entrySet()) {
            if (nextBefore.containsKey(e.getKey())) continue;
            BlockPatch p = unpack(e.getKey(), e.getValue());
            if (p != null) out.add(p);
        }
        out.addAll(patches);
        appliedBefore.clear();
        appliedBefore.putAll(nextBefore);
        if (!out.isEmpty()) patcher.apply(out);
    }

    private static BlockPatch unpack(long key, String spec) {
        int x = (int) (key >> 38);
        int y = (int) ((key << 26) >> 52);
        int z = (int) ((key << 38) >> 38);
        return new BlockPatch(x, y, z, spec);
    }

    public List<BlockPatch> appliedPositions() {
        List<BlockPatch> out = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String> e : appliedBefore.entrySet()) {
            out.add(unpack(e.getKey(), e.getValue()));
        }
        return Collections.unmodifiableList(out);
    }
}
