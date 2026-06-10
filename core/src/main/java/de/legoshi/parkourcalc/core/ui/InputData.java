package de.legoshi.parkourcalc.core.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InputData {

    private final List<InputRow> rows = new ArrayList<>();

    public void clear() {
        rows.clear();
    }

    public List<InputRow> getRows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public InputRow get(int index) {
        return rows.get(index);
    }

    /** Move a row to the drop-line gap {@code to}; returns whether anything actually moved (so
     *  callers can shift row-anchored data, e.g. the angle solver's per-tick state). */
    public boolean moveRow(int from, int to) {
        if (from < 0 || from >= rows.size() || to < 0 || to > rows.size()) {
            return false;
        }
        if (from == to || from == to - 1) {
            return false;
        }

        InputRow moved = rows.remove(from);
        int insertAt = from < to ? to - 1 : to;
        rows.add(insertAt, moved);
        return true;
    }

    public void addRowAt(int index) {
        int clampedIndex = Math.max(0, Math.min(index, rows.size()));
        rows.add(clampedIndex, new InputRow());
    }

    public void insertRow(int index, InputRow row) {
        int clampedIndex = Math.max(0, Math.min(index, rows.size()));
        rows.add(clampedIndex, row);
    }

    public void addRows(int index, int count) {
        for (int i = 0; i < count; i++) {
            addRowAt(index + i);
        }
    }

    public void removeRowAt(int index) {
        if (index >= 0 && index < rows.size()) {
            rows.remove(index);
        }
    }

    public void removeRows(List<Integer> indices) {
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Collections.reverseOrder());
        for (int index : sorted) {
            removeRowAt(index);
        }
    }
}