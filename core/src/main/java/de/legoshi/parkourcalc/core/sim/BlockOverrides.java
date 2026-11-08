package de.legoshi.parkourcalc.core.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockOverrides {

    private final List<BlockOverride> blocks = new ArrayList<BlockOverride>();
    private int modCount;

    public List<BlockOverride> all() {
        return Collections.unmodifiableList(blocks);
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public BlockOverride get(int index) {
        return blocks.get(index);
    }

    public int modCount() {
        return modCount;
    }

    public void add(BlockOverride block) {
        blocks.add(block);
        sort(blocks);
        modCount++;
    }

    public void set(int index, BlockOverride block) {
        if (index < 0 || index >= blocks.size() || blocks.get(index).equals(block)) return;
        blocks.set(index, block);
        sort(blocks);
        modCount++;
    }

    public int indexOf(BlockOverride block) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i) == block) return i;
        }
        return -1;
    }

    public static List<BlockOverride> sorted(List<BlockOverride> rows) {
        List<BlockOverride> out = new ArrayList<BlockOverride>(rows);
        sort(out);
        return out;
    }

    private static void sort(List<BlockOverride> rows) {
        Collections.sort(rows, BY_START);
    }

    private static final Comparator<BlockOverride> BY_START = new Comparator<BlockOverride>() {
        @Override
        public int compare(BlockOverride a, BlockOverride b) {
            return a.startTick < b.startTick ? -1 : a.startTick == b.startTick ? 0 : 1;
        }
    };

    public void remove(int index) {
        if (index < 0 || index >= blocks.size()) return;
        blocks.remove(index);
        modCount++;
    }

    public void replaceAll(List<BlockOverride> next) {
        List<BlockOverride> ordered = next == null ? Collections.<BlockOverride>emptyList() : sorted(next);
        if (blocks.equals(ordered)) return;
        blocks.clear();
        blocks.addAll(ordered);
        modCount++;
    }

    public void clear() {
        if (blocks.isEmpty()) return;
        blocks.clear();
        modCount++;
    }

    public List<BlockPatch> patchesAtRow(int row) {
        return patchesAtRow(row, true);
    }

    public List<BlockPatch> patchesAtRow(int row, boolean includePlayer) {
        if (blocks.isEmpty()) return Collections.emptyList();
        Map<Long, List<BlockOverride>> byPosition = groupByPosition(includePlayer);
        List<BlockPatch> patches = new ArrayList<BlockPatch>(byPosition.size());
        for (List<BlockOverride> rows : byPosition.values()) {
            BlockPatch p = patchAtRow(rows, row);
            if (p != null) patches.add(p);
        }
        return patches;
    }

    public Map<Long, BlockOverride> basesByPosition() {
        Map<Long, BlockOverride> bases = new LinkedHashMap<Long, BlockOverride>();
        for (Map.Entry<Long, List<BlockOverride>> e : groupByPosition(true).entrySet()) {
            bases.put(e.getKey(), base(e.getValue()));
        }
        return bases;
    }

    public int positionCount() {
        return groupByPosition(true).size();
    }

    private Map<Long, List<BlockOverride>> groupByPosition(boolean includePlayer) {
        Map<Long, List<BlockOverride>> byPosition = new LinkedHashMap<Long, List<BlockOverride>>();
        for (BlockOverride b : blocks) {
            if (b.player && !includePlayer) continue;
            long key = BlockPatch.posKey(b.x, b.y, b.z);
            List<BlockOverride> rows = byPosition.get(key);
            if (rows == null) {
                rows = new ArrayList<BlockOverride>(2);
                byPosition.put(key, rows);
            }
            rows.add(b);
        }
        return byPosition;
    }

    private static BlockPatch patchAtRow(List<BlockOverride> rows, int row) {
        if (rows.size() == 1) return rows.get(0).patchAtRow(row);
        BlockOverride base = base(rows);
        BlockOverride active = null;
        BlockOverride latest = rows.get(0);
        for (BlockOverride b : rows) {
            if (b.startTick >= latest.startTick) latest = b;
            if (b.activeAtRow(row) && (active == null || b.startTick >= active.startTick)) active = b;
        }
        String spec = active != null ? active.block : base.before;
        if (!BlockOverride.Spec.parse(spec).valid()) return null;
        return new BlockPatch(base.x, base.y, base.z, spec, active != null ? base.before : latest.block);
    }

    private static BlockOverride base(List<BlockOverride> rows) {
        BlockOverride base = rows.get(0);
        for (BlockOverride b : rows) {
            if (b.startTick < base.startTick) base = b;
        }
        return base;
    }
}
