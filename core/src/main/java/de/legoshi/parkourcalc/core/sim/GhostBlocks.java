package de.legoshi.parkourcalc.core.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GhostBlocks {

    private final List<GhostBlock> blocks = new ArrayList<GhostBlock>();
    private int modCount;

    public List<GhostBlock> all() {
        return Collections.unmodifiableList(blocks);
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public GhostBlock get(int index) {
        return blocks.get(index);
    }

    public int modCount() {
        return modCount;
    }

    public void add(GhostBlock block) {
        blocks.add(block);
        modCount++;
    }

    public void set(int index, GhostBlock block) {
        if (index < 0 || index >= blocks.size() || blocks.get(index).equals(block)) return;
        blocks.set(index, block);
        modCount++;
    }

    public void remove(int index) {
        if (index < 0 || index >= blocks.size()) return;
        blocks.remove(index);
        modCount++;
    }

    public void replaceAll(List<GhostBlock> next) {
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

    public List<GhostBlock> activeAtRow(int row) {
        List<GhostBlock> active = null;
        for (GhostBlock b : blocks) {
            if (!b.activeAtRow(row) || !b.spec().valid()) continue;
            if (active == null) active = new ArrayList<GhostBlock>();
            active.add(b);
        }
        return active == null ? Collections.<GhostBlock>emptyList() : active;
    }
}
