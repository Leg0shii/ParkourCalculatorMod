package de.legoshi.parkourcalc.core.ui;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public final class ConstraintSelection {

    private final Set<Long> focus = new LinkedHashSet<>();

    public void clear() {
        focus.clear();
    }

    public void remapTick(IntUnaryOperator mapper) {
        Set<Long> next = new LinkedHashSet<>();
        for (long k : focus) {
            int tick = (int) (k >> 32);
            int index = (int) k;
            int mapped = mapper.applyAsInt(tick);
            if (mapped >= 0) next.add(key(mapped, index));
        }
        focus.clear();
        focus.addAll(next);
    }

    public void focusOne(int tick, int index) {
        focus.clear();
        focus.add(key(tick, index));
    }

    public void focus(int tick, int[] indices) {
        focus.clear();
        if (indices != null) {
            for (int index : indices) focus.add(key(tick, index));
        }
    }

    public boolean hasFocus() {
        return !focus.isEmpty();
    }

    public boolean isFocused(int tick, int index) {
        return focus.contains(key(tick, index));
    }

    public boolean highlights(int tick, int index, SelectionManager selection) {
        if (hasEffectiveFocus(selection)) {
            return isFocused(tick, index) && selection.isSelected(tick + 1);
        }
        return selection.isSelected(tick + 1);
    }

    private boolean hasEffectiveFocus(SelectionManager selection) {
        for (long k : focus) {
            if (selection.isSelected((int) (k >> 32) + 1)) return true;
        }
        return false;
    }

    public long revision() {
        long h = 1L;
        for (long k : focus) h = 31 * h + k;
        return h;
    }

    private static long key(int tick, int index) {
        return ((long) tick << 32) | (index & 0xFFFFFFFFL);
    }
}
