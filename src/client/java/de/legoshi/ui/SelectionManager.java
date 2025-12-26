package de.legoshi.ui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Manages row selection state with support for single, range, and toggle selection.
 */
public class SelectionManager {

    private final Set<Integer> selectedRows = new TreeSet<>();
    private int lastClickedRow = -1;

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

    /**
     * Gets selected indices as a list, sorted in descending order for safe deletion.
     */
    public List<Integer> getSelectedDescending() {
        List<Integer> list = new ArrayList<>(selectedRows);
        list.sort(Collections.reverseOrder());
        return list;
    }

    private ModifierState getModifierState() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        return new ModifierState(ctrl, shift);
    }

    private record ModifierState(boolean ctrl, boolean shift) {}
}