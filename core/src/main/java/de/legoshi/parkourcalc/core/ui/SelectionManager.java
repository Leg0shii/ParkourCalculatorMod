package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;

import java.util.*;

/**
 * Manages selection over the path/box index space: index 0 is the start state, index k (k>=1)
 * is Tick k (input row k-1). Input-row consumers use {@link #getSelectedRows()} and friends,
 * which drop the start and shift back into input-row indices.
 * Reads modifier state from MinecraftAccess (direct GLFW/LWJGL2 poll); ImGui's
 * io.KeyCtrl/KeyShift derivation has proven unreliable here.
 */
public class SelectionManager {

    private final Set<Integer> selectedRows = new TreeSet<>();
    private final MinecraftAccess mc;
    private int lastClickedRow = -1;
    private boolean scrollIntoViewRequested = false;

    public SelectionManager(MinecraftAccess mc) {
        this.mc = mc;
    }

    public Set<Integer> getSelected() {
        return Collections.unmodifiableSet(selectedRows);
    }

    public boolean isSelected(int rowIndex) {
        return selectedRows.contains(rowIndex);
    }

    public boolean isEmpty() {
        return selectedRows.isEmpty();
    }

    public int size() {
        return selectedRows.size();
    }

    public void clear() {
        selectedRows.clear();
        lastClickedRow = -1;
    }

    public Set<Integer> getSelectedRows() {
        Set<Integer> rows = new TreeSet<>();
        for (int p : selectedRows) {
            if (p >= 1) rows.add(p - 1);
        }
        return rows;
    }

    public List<Integer> getSelectedRowsDescending() {
        List<Integer> list = new ArrayList<>(getSelectedRows());
        list.sort(Collections.reverseOrder());
        return list;
    }

    public int singleSelectedRow() {
        if (selectedRows.size() != 1) return -1;
        int p = selectedRows.iterator().next();
        return p >= 1 ? p - 1 : -1;
    }

    public void requestScrollIntoView() {
        scrollIntoViewRequested = true;
    }

    public boolean consumeScrollRequest() {
        boolean v = scrollIntoViewRequested;
        scrollIntoViewRequested = false;
        return v;
    }

    public void retainBelow(int exclusiveMax) {
        selectedRows.removeIf(idx -> idx >= exclusiveMax);
        if (lastClickedRow >= exclusiveMax) {
            lastClickedRow = -1;
        }
    }

    public void handleClick(int rowIndex) {
        ModifierState modifiers = getModifierState();

        if (modifiers.shift && lastClickedRow != -1) {
            selectRange(lastClickedRow, rowIndex, modifiers.ctrl);
        } else if (modifiers.ctrl) {
            toggleSelection(rowIndex);
        } else {
            selectSingle(rowIndex);
        }
    }

    /** Replace the selection with exactly one row, ignoring Ctrl/Shift (used by right-click targeting). */
    public void selectOnly(int rowIndex) {
        selectSingle(rowIndex);
    }

    private void selectRange(int fromRow, int toRow, boolean addToExisting) {
        if (!addToExisting) {
            selectedRows.clear();
        }

        int start = Math.min(fromRow, toRow);
        int end = Math.max(fromRow, toRow);

        for (int i = start; i <= end; i++) {
            selectedRows.add(i);
        }
    }

    private void toggleSelection(int rowIndex) {
        if (selectedRows.contains(rowIndex)) {
            selectedRows.remove(rowIndex);
        } else {
            selectedRows.add(rowIndex);
        }
        lastClickedRow = rowIndex;
    }

    private void selectSingle(int rowIndex) {
        selectedRows.clear();
        selectedRows.add(rowIndex);
        lastClickedRow = rowIndex;
    }

    /**
     * Adjusts selection indices after rows are inserted.
     */
    public void adjustForInsert(int insertIndex, int count) {
        Set<Integer> adjusted = new TreeSet<>();
        for (int selected : selectedRows) {
            adjusted.add(selected >= insertIndex ? selected + count : selected);
        }
        selectedRows.clear();
        selectedRows.addAll(adjusted);
    }

    private ModifierState getModifierState() {
        return new ModifierState(mc.isCtrlDown(), mc.isShiftDown());
    }

    private static final class ModifierState {
        final boolean ctrl;
        final boolean shift;

        ModifierState(boolean ctrl, boolean shift) {
            this.ctrl = ctrl;
            this.shift = shift;
        }
    }
}
