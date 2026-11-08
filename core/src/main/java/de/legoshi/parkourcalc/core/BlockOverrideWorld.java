package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
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
    private BlockReader reader;
    private ShapeSource shapes = new ShapeSource() {
        @Override
        public List<AABB> shape(String spec) {
            return Collections.singletonList(FULL_CUBE);
        }
    };
    private DesyncListener onDesync = new DesyncListener() {
        @Override
        public void desync(BlockOverride row, int tick, boolean expectedPlaced) {
        }
    };
    private int replayRow = -1;
    private boolean replayStepped;
    private final Map<Long, String> replayProblems = new LinkedHashMap<Long, String>();
    private Map<BlockOverride, String> simProblems = Collections.emptyMap();
    private int[] preview;
    private int previewArgb;
    private BlockOverride selected;
    private int appliedRow = Integer.MIN_VALUE;
    private int appliedModCount = -1;
    private final Map<Long, String[]> appliedBefore = new LinkedHashMap<Long, String[]>();

    public BlockOverrideWorld(InputData inputData, SelectionManager selection) {
        this.inputData = inputData;
        this.selection = selection;
    }

    public interface BlockReader {
        String blockAt(int x, int y, int z);
    }

    public interface ShapeSource {
        List<AABB> shape(String spec);
    }

    public static final AABB FULL_CUBE = new AABB(new Vec3dCore(0, 0, 0), new Vec3dCore(1, 1, 1));

    public void setShapeSource(ShapeSource source) {
        this.shapes = source;
    }

    public List<AABB> shape(String spec) {
        List<AABB> boxes = shapes.shape(spec);
        if (boxes == null || boxes.isEmpty()) return Collections.singletonList(FULL_CUBE);
        return boxes;
    }

    public interface DesyncListener {
        void desync(BlockOverride row, int tick, boolean expectedPlaced);
    }

    public void setPatcher(BlockPatcher patcher) {
        this.patcher = patcher;
    }

    public void setBlockReader(BlockReader reader) {
        this.reader = reader;
    }

    public void setOnDesync(DesyncListener listener) {
        this.onDesync = listener;
    }

    public boolean isMissing(BlockOverride row) {
        return replayProblems.containsKey(BlockPatch.posKey(row.x, row.y, row.z));
    }

    public String replayProblem(BlockOverride row) {
        return replayProblems.get(BlockPatch.posKey(row.x, row.y, row.z));
    }

    public String simProblem(BlockOverride row) {
        return simProblems.get(row);
    }

    public boolean hasProblem(BlockOverride row) {
        return simProblem(row) != null || isMissing(row);
    }

    public void setSimProblems(Map<BlockOverride, String> problems) {
        simProblems = problems == null ? Collections.<BlockOverride, String>emptyMap() : problems;
    }

    public void setPreview(int[] pos, int argb) {
        preview = pos == null ? null : pos.clone();
        previewArgb = argb;
    }

    public int[] preview() {
        return preview;
    }

    public int previewArgb() {
        return previewArgb;
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

    public void select(BlockOverride row) {
        selected = row;
    }

    public BlockOverride selected() {
        if (selected != null && inputData.getBlockOverrides().indexOf(selected) < 0) selected = null;
        return selected;
    }

    public boolean isSelected(BlockOverride row) {
        return row != null && selected() == row;
    }

    public BlockOverride rowAt(int x, int y, int z) {
        BlockOverride first = null;
        BlockOverride active = null;
        int row = viewRow();
        for (BlockOverride b : rows()) {
            if (b.x != x || b.y != y || b.z != z) continue;
            if (first == null) first = b;
            if (b.activeAtRow(row) && (active == null || b.startTick >= active.startTick)) active = b;
        }
        return active != null ? active : first;
    }

    public boolean isReplaying() {
        return replayRow >= 0;
    }

    public void setReplayRow(int row) {
        int next = Math.max(-1, row);
        replayStepped = next >= 0 && replayRow >= 0;
        if (replayRow < 0 || next < 0) replayProblems.clear();
        replayRow = next;
        refresh(true);
    }

    public void clearReplayRow() {
        setReplayRow(-1);
    }

    public void tick() {
        refresh(false);
        if (replayStepped) checkPlayerRows();
    }

    private void checkPlayerRows() {
        if (reader == null || replayRow < 1) return;
        int row = replayRow - 1;
        for (BlockOverride b : rows()) {
            if (!b.player || b.aheadOfRow(row) || isMissing(b)) continue;
            String expected = b.activeAtRow(row) ? b.block : b.before;
            BlockOverride.Spec want = BlockOverride.Spec.parse(expected);
            if (!want.valid()) continue;
            String actual = reader.blockAt(b.x, b.y, b.z);
            if (actual == null || matches(want, BlockOverride.Spec.parse(actual))) continue;
            boolean expectedPlaced = b.activeAtRow(row);
            replayProblems.put(BlockPatch.posKey(b.x, b.y, b.z), (expectedPlaced ? "Not placed at tick " : "Still there at tick ") + (row + 1));
            onDesync.desync(b, row + 1, expectedPlaced);
        }
    }

    static boolean matches(BlockOverride.Spec want, BlockOverride.Spec have) {
        if (!have.valid() || !want.id.equals(have.id)) return false;
        for (Map.Entry<String, String> e : want.properties.entrySet()) {
            if (!e.getValue().equals(have.properties.get(e.getKey()))) return false;
        }
        return true;
    }

    public void refresh(boolean force) {
        if (!isAvailable()) return;
        BlockOverrides overrides = inputData.getBlockOverrides();
        int row = viewRow();
        if (!force && row == appliedRow && overrides.modCount() == appliedModCount && appliedBefore.size() == overrides.positionCount()) return;
        appliedRow = row;
        appliedModCount = overrides.modCount();
        if (!apply(overrides.patchesAtRow(row, !replayStepped), overrides.basesByPosition())) appliedRow = Integer.MIN_VALUE;
    }

    public void releaseAll() {
        if (appliedBefore.isEmpty() || !isAvailable()) {
            appliedBefore.clear();
            appliedRow = Integer.MIN_VALUE;
            return;
        }
        List<BlockPatch> restore = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String[]> e : appliedBefore.entrySet()) {
            restore.add(unpack(e.getKey(), e.getValue()));
        }
        appliedBefore.clear();
        appliedRow = Integer.MIN_VALUE;
        appliedModCount = -1;
        if (!restore.isEmpty()) patcher.apply(restore);
    }

    private boolean apply(List<BlockPatch> patches, Map<Long, BlockOverride> bases) {
        Map<Long, String[]> nextBefore = new LinkedHashMap<Long, String[]>();
        for (Map.Entry<Long, BlockOverride> e : bases.entrySet()) {
            BlockOverride b = e.getValue();
            if (!b.beforeSpec().valid()) continue;
            nextBefore.put(e.getKey(), new String[] {b.before, b.block});
        }
        List<BlockPatch> out = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String[]> e : appliedBefore.entrySet()) {
            if (nextBefore.containsKey(e.getKey())) continue;
            out.add(unpack(e.getKey(), e.getValue()));
        }
        out.addAll(patches);
        appliedBefore.clear();
        appliedBefore.putAll(nextBefore);
        return out.isEmpty() || patcher.apply(out);
    }

    private static BlockPatch unpack(long key, String[] specs) {
        int x = (int) (key >> 38);
        int y = (int) ((key << 26) >> 52);
        int z = (int) ((key << 38) >> 38);
        return new BlockPatch(x, y, z, specs[0], specs[1]);
    }

    public List<BlockPatch> appliedPositions() {
        List<BlockPatch> out = new ArrayList<BlockPatch>();
        for (Map.Entry<Long, String[]> e : appliedBefore.entrySet()) {
            out.add(unpack(e.getKey(), e.getValue()));
        }
        return Collections.unmodifiableList(out);
    }
}
