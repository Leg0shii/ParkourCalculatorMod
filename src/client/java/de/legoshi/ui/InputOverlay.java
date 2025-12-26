package de.legoshi.ui;

import de.legoshi.imgui.RenderInterface;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;

public final class InputOverlay implements RenderInterface {

    private static final float TABLE_HEIGHT = 200;
    private static final String DRAG_DROP_TYPE = "INPUT_ROW";
    private static final int COLUMN_COUNT = 9;

    private final InputData data;
    private final Runnable onDataChanged;
    private final Runnable onSetPlayerPosition;

    private final SelectionManager selection = new SelectionManager();
    private final KeyDragSelect keyDragSelect = new KeyDragSelect();
    private final ImString yawInput = new ImString(32);
    private final ImInt rowsToAdd = new ImInt(1);

    private int draggingRowIndex = -1;

    public InputOverlay(InputData data, Runnable onDataChanged, Runnable onSetPlayerPosition) {
        this.data = data;
        this.onDataChanged = onDataChanged;
        this.onSetPlayerPosition = onSetPlayerPosition;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!ImGui.begin("TAS Inputs", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.end();
            return;
        }

        pushTableStyles();

        if (ImGui.beginTable("tas-table", COLUMN_COUNT, tableFlags(), 0, TABLE_HEIGHT)) {
            setupColumns();
            renderAllRows();
            ImGui.endTable();
        }

        renderContextMenu();
        handleKeyboardShortcuts();

        keyDragSelect.update();
        if (keyDragSelect.applyIfReleased(data.getRows())) {
            onDataChanged.run();
        }

        popTableStyles();
        ImGui.end();
    }

    private int tableFlags() {
        return ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.RowBg;
    }

    private void pushTableStyles() {
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0, 0, 0, 0);
        ImGui.pushStyleColor(ImGuiCol.Header, 0, 0, 0, 0);
    }

    private void popTableStyles() {
        ImGui.popStyleColor(3);
    }

    private void setupColumns() {
        ImGui.tableSetupColumn("#");
        for (InputRow.Key key : InputRow.Key.values()) {
            ImGui.tableSetupColumn(key.name());
        }
        ImGui.tableSetupColumn("YAW", ImGuiTableColumnFlags.WidthFixed, 160);
        ImGui.tableHeadersRow();
    }

    private void renderAllRows() {
        List<InputRow> rows = data.getRows();
        ImDrawList drawList = ImGui.getWindowDrawList();
        keyDragSelect.clearRowBounds();

        DragDropState dragDrop = new DragDropState();

        for (int i = 0; i < rows.size(); i++) {
            renderRow(i, rows.get(i), dragDrop);
        }

        renderDropIndicator(drawList, dragDrop);
        applyDragDrop(dragDrop);
    }

    private void renderRow(int index, InputRow row, DragDropState dragDrop) {
        ImGui.pushID(row.getId());
        ImGui.tableNextRow();

        setRowBackground(index);

        ImGui.tableNextColumn();
        renderRowNumber(index);

        ImVec2 rowMin = ImGui.getItemRectMin();
        ImVec2 rowMax = ImGui.getItemRectMax();
        keyDragSelect.recordRowBounds(index, rowMin.y, rowMax.y);

        handleRowDragDrop(index, rowMin, rowMax, dragDrop);
        renderKeyColumns(row, index);
        renderYawColumn(row);

        ImGui.popID();
    }

    private void setRowBackground(int rowIndex) {
        int color = 0;
        if (selection.isSelected(rowIndex)) {
            color = ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.7f, 0.5f);
        } else if (draggingRowIndex == rowIndex) {
            color = ImGui.colorConvertFloat4ToU32(0.3f, 0.5f, 0.8f, 0.4f);
        }

        if (color != 0) {
            ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, color);
        }
    }

    private void renderRowNumber(int rowIndex) {
        boolean isSelected = selection.isSelected(rowIndex);
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowOverlap;

        if (ImGui.selectable((rowIndex + 1) + ".##row", isSelected, flags)) {
            selection.handleClick(rowIndex);
        }
    }

    private void handleRowDragDrop(int index, ImVec2 rowMin, ImVec2 rowMax, DragDropState state) {
        if (selection.size() > 1) {
            return; // Don't allow drag when multiple rows selected
        }

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            draggingRowIndex = index;
            ImGui.setDragDropPayload(DRAG_DROP_TYPE, new byte[]{(byte) index}, 1);
            ImGui.endDragDropSource();
        }

        if (ImGui.beginDragDropTarget()) {
            float mouseY = ImGui.getMousePos().y;
            float rowMidY = (rowMin.y + rowMax.y) / 2;
            boolean insertAbove = mouseY < rowMidY;

            state.dropLineY = insertAbove ? rowMin.y : rowMax.y;
            state.rowMinX = rowMin.x;
            state.rowMaxX = rowMax.x;

            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE);
            if (payload != null && payload.length > 0) {
                state.moveFrom = payload[0] & 0xFF;
                state.moveTo = insertAbove ? index : index + 1;
            }
            ImGui.endDragDropTarget();
        }
    }

    private void renderDropIndicator(ImDrawList drawList, DragDropState state) {
        if (draggingRowIndex != -1 && state.dropLineY > 0) {
            int color = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.0f, 1.0f);
            drawList.addLine(state.rowMinX, state.dropLineY, state.rowMaxX, state.dropLineY, color, 2.0f);
        }
    }

    private void applyDragDrop(DragDropState state) {
        if (!ImGui.isMouseDragging(0)) {
            draggingRowIndex = -1;
        }

        if (state.moveFrom != -1 && state.moveTo != -1 && state.moveFrom != state.moveTo) {
            data.moveRow(state.moveFrom, state.moveTo);
            draggingRowIndex = -1;
            selection.clear();
            onDataChanged.run();
        }
    }

    private void renderKeyColumns(InputRow row, int rowIndex) {
        for (InputRow.Key key : InputRow.Key.values()) {
            ImGui.tableNextColumn();

            boolean actualValue = row.isKeyActive(key);
            boolean displayValue = keyDragSelect.getDisplayValue(key, rowIndex, actualValue);

            float brightness = displayValue ? 0.75f : 0f;
            float alpha = displayValue ? 1.0f : 0f;
            ImGui.pushStyleColor(ImGuiCol.Text, brightness, brightness, brightness, alpha);

            ImGui.selectable(key.name() + "##" + key.name(), displayValue);

            if (ImGui.isItemClicked(0)) {
                keyDragSelect.startDrag(key, rowIndex, actualValue);
            }

            ImGui.popStyleColor();
        }
    }

    private void renderYawColumn(InputRow row) {
        ImGui.tableNextColumn();

        Float yaw = row.getYaw();
        yawInput.set(yaw == null ? "" : String.format(Locale.US, "%.5f", yaw));

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 0);
        ImGui.setNextItemWidth(150);

        if (ImGui.inputText("##yaw", yawInput)) {
            parseAndSetYaw(row);
            onDataChanged.run();
        }

        ImGui.popStyleVar();
    }

    private void parseAndSetYaw(InputRow row) {
        String text = yawInput.get().trim();
        if (text.isEmpty()) {
            row.setYaw(null);
        } else {
            try {
                row.setYaw(Float.parseFloat(text));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void renderContextMenu() {
        if (!ImGui.beginPopupContextWindow("context_menu", ImGuiPopupFlags.MouseButtonRight)) {
            return;
        }

        if (ImGui.menuItem("Set to user position")) {
            onSetPlayerPosition.run();
            onDataChanged.run();
        }

        ImGui.separator();
        renderRowCountInput();
        ImGui.separator();
        renderAddRowOptions();
        renderDeleteOption();

        ImGui.endPopup();
    }

    private void renderRowCountInput() {
        ImGui.text("Rows:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(80);
        ImGui.inputInt("##rows_to_add", rowsToAdd);
        rowsToAdd.set(Math.clamp(rowsToAdd.get(), 1, 100));
    }

    private void renderAddRowOptions() {
        int count = rowsToAdd.get();

        if (ImGui.menuItem("Add " + count + " row(s) at end")) {
            data.addRows(data.size(), count);
            onDataChanged.run();
        }

        if (selection.size() == 1) {
            int selected = selection.getSelected().iterator().next();

            if (ImGui.menuItem("Add " + count + " row(s) above")) {
                data.addRows(selected, count);
                selection.adjustForInsert(selected, count);
                onDataChanged.run();
            }

            if (ImGui.menuItem("Add " + count + " row(s) below")) {
                data.addRows(selected + 1, count);
                selection.adjustForInsert(selected + 1, count);
                onDataChanged.run();
            }
        }
    }

    private void renderDeleteOption() {
        if (selection.isEmpty()) {
            return;
        }

        ImGui.separator();
        if (ImGui.menuItem("Delete " + selection.size() + " row(s)", "Del")) {
            deleteSelectedRows();
        }
    }

    private void deleteSelectedRows() {
        data.removeRows(selection.getSelectedDescending());
        selection.clear();
        onDataChanged.run();
    }

    private void handleKeyboardShortcuts() {
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && !selection.isEmpty()) {
            deleteSelectedRows();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            selection.clear();
        }
    }

    private static class DragDropState {
        int moveFrom = -1;
        int moveTo = -1;
        float dropLineY = -1;
        float rowMinX = 0;
        float rowMaxX = 0;
    }
}