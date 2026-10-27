package de.legoshi.parkourcalc.core.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        modCount++;
    }

    public void set(int index, BlockOverride block) {
        if (index < 0 || index >= blocks.size() || blocks.get(index).equals(block)) return;
        blocks.set(index, block);
        modCount++;
    }

    public void remove(int index) {
        if (index < 0 || index >= blocks.size()) return;
        blocks.remove(index);
        modCount++;
    }

    public void replaceAll(List<BlockOverride> next) {
        if (blocks.equals(next)) return;
        blocks.clear();
        if (next != null) blocks.addAll(next);
        modCount++;
    }

    public void clear() {
        if (blocks.isEmpty()) return;
        blocks.clear();
        modCount++;
    }

    public List<BlockOverride> activeAtRow(int row) {
        List<BlockOverride> active = null;
        for (BlockOverride b : blocks) {
            if (!b.activeAtRow(row) || !b.spec().valid()) continue;
            if (active == null) active = new ArrayList<BlockOverride>();
            active.add(b);
        }
        return active == null ? Collections.<BlockOverride>emptyList() : active;
    }
}
