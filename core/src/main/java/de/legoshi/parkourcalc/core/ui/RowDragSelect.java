package de.legoshi.parkourcalc.core.ui;

import imgui.ImGui;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class RowDragSelect {

    private static final float SCROLL_ROWS_PER_SECOND = 15f;

    private int pressedRow = -1;
    private boolean pressedRowSelected;
    private boolean active;
    private int targetRow = -1;
    private final Set<Integer> keptPaths = new TreeSet<>();
    private final TreeMap<Integer, float[]> rowBounds = new TreeMap<>();

    public void clearRowBounds() {
        rowBounds.clear();
    }

    public void recordRowBounds(int rowIndex, float minY, float maxY) {
        rowBounds.put(rowIndex, new float[]{minY, maxY});
    }

    public void press(int rowIndex, boolean rowSelected, Collection<Integer> pathsToKeep) {
        pressedRow = rowIndex;
        pressedRowSelected = rowSelected;
        targetRow = rowIndex;
        active = false;
        keptPaths.clear();
        keptPaths.addAll(pathsToKeep);
    }

    public boolean movesRow(int rowIndex) {
        return pressedRow == rowIndex && pressedRowSelected;
    }

    public boolean isActive() {
        return active;
    }

    public void update(SelectionManager selection, float viewTop, float viewBot, float rowH) {
        if (pressedRow < 0) return;
        if (!ImGui.isMouseDown(0)) {
            reset();
            return;
        }
        if (pressedRowSelected) return;
        if (!active && !ImGui.isMouseDragging(0)) return;
        active = true;
        float y = ImGui.getMousePos().y;
        int row = rowAt(y, viewTop, viewBot);
        if (row >= 0) targetRow = row;
        autoScroll(y, viewTop, viewBot, rowH);
        Set<Integer> paths = new TreeSet<>(keptPaths);
        for (int i = Math.min(pressedRow, targetRow); i <= Math.max(pressedRow, targetRow); i++) paths.add(i + 1);
        selection.select(paths, pressedRow + 1);
    }

    private int rowAt(float y, float viewTop, float viewBot) {
        if (rowBounds.isEmpty()) return -1;
        if (y < viewTop) return rowBounds.firstKey();
        if (y > viewBot) return rowBounds.lastKey();
        for (Map.Entry<Integer, float[]> e : rowBounds.entrySet()) {
            if (y >= e.getValue()[0] && y < e.getValue()[1]) return e.getKey();
        }
        return -1;
    }

    private void autoScroll(float y, float viewTop, float viewBot, float rowH) {
        float step = SCROLL_ROWS_PER_SECOND * rowH * ImGui.getIO().getDeltaTime();
        if (y < viewTop) ImGui.setScrollY(Math.max(0f, ImGui.getScrollY() - step));
        else if (y > viewBot) ImGui.setScrollY(Math.min(ImGui.getScrollMaxY(), ImGui.getScrollY() + step));
    }

    private void reset() {
        pressedRow = -1;
        pressedRowSelected = false;
        active = false;
        targetRow = -1;
        keptPaths.clear();
    }
}
