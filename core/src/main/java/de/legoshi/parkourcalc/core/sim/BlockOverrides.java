package de.legoshi.parkourcalc.core.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class BlockOverrides {

    private final List<BlockOverride> blocks = new ArrayList<BlockOverride>();
    private int modCount;
    private Map<Long, List<BlockOverride>> byPosition;
    private Timeline everyRow;
    private Timeline replayRows;

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
        changed();
    }

    public void set(int index, BlockOverride block) {
        if (index < 0 || index >= blocks.size() || blocks.get(index).equals(block)) return;
        blocks.set(index, block);
        sort(blocks);
        changed();
    }

    private void changed() {
        modCount++;
        byPosition = null;
        everyRow = null;
        replayRows = null;
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
        changed();
    }

    public void replaceAll(List<BlockOverride> next) {
        List<BlockOverride> ordered = next == null ? Collections.<BlockOverride>emptyList() : sorted(next);
        if (blocks.equals(ordered)) return;
        blocks.clear();
        blocks.addAll(ordered);
        changed();
    }

    public void clear() {
        if (blocks.isEmpty()) return;
        blocks.clear();
        changed();
    }

    public List<BlockPatch> patchesAtRow(int row) {
        return patchesAtRow(row, true);
    }

    public List<BlockPatch> patchesAtRow(int row, boolean includePlayer) {
        if (blocks.isEmpty()) return Collections.emptyList();
        if (includePlayer) {
            if (everyRow == null) everyRow = new Timeline(byPosition(), true);
            return everyRow.at(row);
        }
        if (replayRows == null) replayRows = new Timeline(byPosition(), false);
        return replayRows.at(row);
    }

    public List<BlockPatch> patchesBeforeClicksAtRow(int row) {
        if (blocks.isEmpty()) return Collections.emptyList();
        List<BlockPatch> out = new ArrayList<BlockPatch>();
        for (List<BlockOverride> position : byPosition().values()) {
            BlockPatch p = patchBeforeClicks(position, row);
            if (p != null) out.add(p);
        }
        return out;
    }

    public BlockPatch patchBeforeClicksAt(int x, int y, int z, int row) {
        if (blocks.isEmpty()) return null;
        List<BlockOverride> rows = byPosition().get(BlockPatch.posKey(x, y, z));
        return rows == null ? null : patchBeforeClicks(rows, row);
    }

    private static BlockPatch patchBeforeClicks(List<BlockOverride> rows, int row) {
        boolean clicked = false;
        for (BlockOverride b : rows) {
            if (b.player && b.startTick == row + 1) clicked = true;
        }
        return patchAtRow(rows, clicked ? row - 1 : row, true);
    }

    public Map<Long, BlockOverride> basesByPosition() {
        Map<Long, BlockOverride> bases = new LinkedHashMap<Long, BlockOverride>();
        for (Map.Entry<Long, List<BlockOverride>> e : byPosition().entrySet()) {
            bases.put(e.getKey(), base(e.getValue()));
        }
        return bases;
    }

    public int positionCount() {
        return byPosition().size();
    }

    public static int firstChangedRow(List<BlockOverride> before, List<BlockOverride> after) {
        BlockOverrides a = new BlockOverrides();
        a.replaceAll(before);
        BlockOverrides b = new BlockOverrides();
        b.replaceAll(after);
        TreeSet<Integer> starts = new TreeSet<Integer>();
        starts.add(Integer.MIN_VALUE);
        addChangeRows(starts, a.blocks);
        addChangeRows(starts, b.blocks);
        for (int row : starts) {
            if (!samePatches(a.patchesAtRow(row), b.patchesAtRow(row))) return Math.max(0, row);
        }
        return -1;
    }

    private static boolean samePatches(List<BlockPatch> a, List<BlockPatch> b) {
        return a.size() == b.size() && a.containsAll(b);
    }

    public static List<BlockOverride> slice(List<BlockOverride> rows, List<Integer> sourceRows) {
        List<BlockOverride> out = new ArrayList<BlockOverride>();
        if (rows == null || sourceRows == null || sourceRows.isEmpty()) return out;
        for (BlockOverride b : rows) {
            int first = -1;
            int last = -1;
            for (int j = 0; j < sourceRows.size(); j++) {
                if (!b.activeAtRow(sourceRows.get(j))) continue;
                if (first < 0) first = j;
                last = j;
            }
            if (first < 0) continue;
            boolean startsInside = sourceRows.get(first) + 1 == b.startTick;
            int end = last == sourceRows.size() - 1 && endsAfter(b, sourceRows.get(last)) ? BlockOverride.NO_END : last + 1;
            out.add(b.withTicks(first + 1, end).withPlayer(b.player && startsInside));
        }
        return sorted(out);
    }

    private static boolean endsAfter(BlockOverride b, int row) {
        return b.endTick == BlockOverride.NO_END || b.endTick > row + 1;
    }

    private Map<Long, List<BlockOverride>> byPosition() {
        if (byPosition != null) return byPosition;
        Map<Long, List<BlockOverride>> grouped = new LinkedHashMap<Long, List<BlockOverride>>();
        for (BlockOverride b : blocks) {
            long key = BlockPatch.posKey(b.x, b.y, b.z);
            List<BlockOverride> rows = grouped.get(key);
            if (rows == null) {
                rows = new ArrayList<BlockOverride>(2);
                grouped.put(key, rows);
            }
            rows.add(b);
        }
        byPosition = grouped;
        return grouped;
    }

    private static void addChangeRows(TreeSet<Integer> starts, List<BlockOverride> rows) {
        for (BlockOverride b : rows) {
            long placed = (long) b.startTick - 1;
            if (placed > Integer.MIN_VALUE && placed <= Integer.MAX_VALUE) starts.add((int) placed);
            if (b.endTick != BlockOverride.NO_END) starts.add(b.endTick);
        }
    }

    private static final class Timeline {

        private final int[] starts;
        private final List<List<BlockPatch>> patches;

        Timeline(Map<Long, List<BlockOverride>> byPosition, boolean includePlayer) {
            TreeSet<Integer> rows = new TreeSet<Integer>();
            for (List<BlockOverride> position : byPosition.values()) addChangeRows(rows, position);
            starts = new int[rows.size()];
            patches = new ArrayList<List<BlockPatch>>(starts.length + 1);
            int i = 0;
            for (int row : rows) starts[i++] = row;
            patches.add(resolve(byPosition, starts.length == 0 || starts[0] == Integer.MIN_VALUE ? Integer.MIN_VALUE : starts[0] - 1, includePlayer));
            for (int start : starts) patches.add(resolve(byPosition, start, includePlayer));
        }

        List<BlockPatch> at(int row) {
            int found = Arrays.binarySearch(starts, row);
            return patches.get(found >= 0 ? found + 1 : -found - 1);
        }

        private static List<BlockPatch> resolve(Map<Long, List<BlockOverride>> byPosition, int row, boolean includePlayer) {
            List<BlockPatch> out = new ArrayList<BlockPatch>(byPosition.size());
            for (List<BlockOverride> position : byPosition.values()) {
                BlockPatch p = patchAtRow(position, row, includePlayer);
                if (p != null) out.add(p);
            }
            return out.isEmpty() ? Collections.<BlockPatch>emptyList() : Collections.unmodifiableList(out);
        }
    }

    private static BlockPatch patchAtRow(List<BlockOverride> rows, int row, boolean includePlayer) {
        BlockOverride base = base(rows);
        BlockOverride active = null;
        BlockOverride latest = rows.get(0);
        for (BlockOverride b : rows) {
            if (b.startTick >= latest.startTick) latest = b;
            if (b.activeAtRow(row) && (active == null || b.startTick >= active.startTick)) active = b;
        }
        if (!includePlayer && leftToThePlayer(rows, row, active, base)) return null;
        BlockOverride.Spec spec = active != null ? active.spec() : base.beforeSpec();
        if (!spec.valid()) return null;
        return new BlockPatch(base.x, base.y, base.z, active != null ? active.block : base.before,
                active != null ? base.before : latest.block);
    }

    private static boolean leftToThePlayer(List<BlockOverride> rows, int row, BlockOverride active, BlockOverride base) {
        long tick = (long) row + 1;
        long lastEnd = Long.MIN_VALUE;
        boolean playerEnded = false;
        for (BlockOverride b : rows) {
            if (b.endTick == BlockOverride.NO_END || b.endTick < b.startTick || tick <= b.endTick) continue;
            long ended = (long) b.endTick + 1;
            if (ended > lastEnd) {
                lastEnd = ended;
                playerEnded = b.player;
            } else if (ended == lastEnd && b.player) {
                playerEnded = true;
            }
        }
        if (active != null && active.startTick >= lastEnd) return active.player;
        if (lastEnd != Long.MIN_VALUE) return playerEnded;
        return base.player;
    }

    private static BlockOverride base(List<BlockOverride> rows) {
        BlockOverride base = rows.get(0);
        for (BlockOverride b : rows) {
            if (b.startTick < base.startTick) base = b;
        }
        return base;
    }
}
