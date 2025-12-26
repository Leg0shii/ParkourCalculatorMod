package de.legoshi.ui;

import imgui.ImGui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles drag-selection for toggling keys across multiple rows.
 */
public class KeyDragSelect {

    private InputRow.Key activeColumn = null;
    private int startRow = -1;
    private int currentRow = -1;
    private boolean targetValue = true;

    private final Map<Integer, Float> rowMinY = new HashMap<>();
    private final Map<Integer, Float> rowMaxY = new HashMap<>();

    public void recordRowBounds(int rowIndex, float minY, float maxY) {
        rowMinY.put(rowIndex, minY);
        rowMaxY.put(rowIndex, maxY);
    }

    public void clearRowBounds() {
        rowMinY.clear();
        rowMaxY.clear();
    }

    public void startDrag(InputRow.Key column, int rowIndex, boolean currentValue) {
        activeColumn = column;
        startRow = rowIndex;
        currentRow = rowIndex;
        targetValue = !currentValue;
    }

    public boolean isActive() {
        return activeColumn != null;
    }

    public boolean isInDragRange(InputRow.Key key, int rowIndex) {
        if (activeColumn != key || startRow == -1) {
            return false;
        }

        int minRow = Math.min(startRow, currentRow);
        int maxRow = Math.max(startRow, currentRow);
        return rowIndex >= minRow && rowIndex <= maxRow;
    }

    public boolean getTargetValue() {
        return targetValue;
    }

    /**
     * Returns the display value for a key cell, accounting for drag preview.
     */
    public boolean getDisplayValue(InputRow.Key key, int rowIndex, boolean actualValue) {
        if (isInDragRange(key, rowIndex)) {
            return targetValue;
        }
        return actualValue;
    }

    /**
     * Updates drag state based on mouse position. Call each frame while dragging.
     */
    public void update() {
        if (activeColumn == null) {
            return;
        }

        if (ImGui.isMouseDown(0)) {
            updateCurrentRow();
        } else {
            // Mouse released - will be handled by apply()
        }
    }

    private void updateCurrentRow() {
        float mouseY = ImGui.getMousePos().y;
        for (Map.Entry<Integer, Float> entry : rowMinY.entrySet()) {
            int rowIndex = entry.getKey();
            Float maxY = rowMaxY.get(rowIndex);
            if (maxY != null && mouseY >= entry.getValue() && mouseY <= maxY) {
                currentRow = rowIndex;
                break;
            }
        }
    }

    /**
     * Applies the drag selection to the data if mouse was released.
     * Returns true if changes were applied.
     */
    public boolean applyIfReleased(List<InputRow> rows) {
        if (activeColumn == null || ImGui.isMouseDown(0)) {
            return false;
        }

        int minRow = Math.min(startRow, currentRow);
        int maxRow = Math.max(startRow, currentRow);

        for (int i = minRow; i <= maxRow; i++) {
            if (i >= 0 && i < rows.size()) {
                rows.get(i).setKeyActive(activeColumn, targetValue);
            }
        }

        reset();
        return true;
    }

    private void reset() {
        activeColumn = null;
        startRow = -1;
        currentRow = -1;
    }
}