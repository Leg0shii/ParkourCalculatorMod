package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.callback.ImListClipperCallback;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class InputOverlay {

    private static final String ID_TABLE = "tas-table";
    private static final String ID_CONTEXT_MENU = "context_menu";
    private static final String ID_YAW_INPUT = "##yaw";
    private static final String ID_ROW_SUFFIX = "##row";
    private static final String ID_KEY_SUFFIX = "##";

    private static final String COL_INDEX = "Tick";
    private static final String COL_YAW = "Yaw";
    private static final String COL_SPEED = "Speed";
    private static final String COL_JUMP_BOOST = "Jump";
    private static final float INDEX_DIGIT_WIDTH = 32f;
    private static final float INDEX_DIGIT_SLACK = 6f;
    private static final float KEY_COL_MIN_WIDTH = 36f;
    private static final float MOD_COL_MIN_WIDTH = 48f;
    private static final float TABLE_MIN_HEIGHT = 80f;
    private static final String YAW_WIDTH_SAMPLE = " -1234.000000";
    private static final InputRow.Key[] MOVEMENT_KEYS = {
            InputRow.Key.W, InputRow.Key.A, InputRow.Key.S, InputRow.Key.D
    };
    private static final InputRow.Key[] MODIFIER_KEYS = {
            InputRow.Key.SPRINT, InputRow.Key.SNEAK, InputRow.Key.JUMP
    };

    private static final String ID_SPEED_SUFFIX = "##speed";
    private static final String ID_JUMP_SUFFIX = "##jump";
    private static final String ID_BULK_SPEED = "##bulk_speed";
    private static final String ID_BULK_JUMP = "##bulk_jump";

    private static final String LABEL_SET_ALL_SPEED = "Set all Speed:";
    private static final String LABEL_SET_ALL_JUMP = "Set all Jump:";

    private static final String[] AMP_LABELS = {
            "none", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"
    };
    private static final float AMP_CELL_WIDTH = 110;
    private static final float AMP_TOOLBAR_WIDTH = 110;
    private static final float AMP_COLUMN_WIDTH = 120;

    private static final String MENU_SET_TO_PLAYER = "Set to user position";
    private static final String LABEL_ROWS = "Rows:";
    private static final String MENU_ADD_AT_END = "Add %d row(s) at end";
    private static final String MENU_ADD_ABOVE = "Add %d row(s) above";
    private static final String MENU_ADD_BELOW = "Add %d row(s) below";
    private static final String MENU_DELETE = "Delete %d row(s)";
    private static final String MENU_DELETE_SHORTCUT = "Del";

    private static final String BTN_CLEAR_ALL = "Clear All";
    private static final String BTN_CANCEL = "Cancel";
    private static final String MENU_DUPLICATE = "Duplicate selected";
    private static final String MENU_CLEAR_ALL = "Clear all rows";
    private static final String POPUP_CLEAR_CONFIRM = "Clear all rows?##clear_confirm";
    private static final String CLEAR_CONFIRM_FMT = "Delete all %d rows? This cannot be undone.";

    private static final String YAW_FORMAT_DISPLAY = "% 12.6f";
    private static final String YAW_FORMAT_EDIT = "%.6f";

    private static final String DRAG_DROP_TYPE = "INPUT_ROW";

    private static final int BASE_COLUMN_COUNT = 9;
    private static final int POTION_COLUMN_COUNT = 2;
    private static final float ROW_COUNT_INPUT_WIDTH = 240;

    private static final String WARN_MULTIPLAYER =
            "Multiplayer: simulator only sees blocks inside your render distance.";

    private static final String EMPTY_HINT = "Right-click to add ticks";

    private final InputData data;
    private final Settings settings;
    private final IntConsumer onDataChangedAt;
    private final Runnable onSetPlayerPosition;
    private final PlaybackController playback;
    private final MinecraftAccess mc;

    private final SelectionManager selection;
    private final KeyDragSelect keyDragSelect = new KeyDragSelect();
    private final ImString yawInput = new ImString(32);
    private final ImInt rowsToAdd = new ImInt(1);
    private final ImInt ampBuf = new ImInt();
    private final ImInt bulkSpeedBuf = new ImInt();
    private final ImInt bulkJumpBuf = new ImInt();

    private int draggingRowIndex = -1;
    private int editingYawRow = -1;

    private Supplier<Float> footerHeightProvider = () -> 0f;

    public void setFooterHeightProvider(Supplier<Float> provider) {
        this.footerHeightProvider = provider;
    }

    public InputOverlay(InputData data, Settings settings, SelectionManager selection,
                        IntConsumer onDataChangedAt, Runnable onSetPlayerPosition,
                        PlaybackController playback, MinecraftAccess mc) {
        this.data = data;
        this.settings = settings;
        this.selection = selection;
        this.onDataChangedAt = onDataChangedAt;
        this.onSetPlayerPosition = onSetPlayerPosition;
        this.playback = playback;
        this.mc = mc;
    }

    private void notifyChange(int dirtyTick) {
        onDataChangedAt.accept(dirtyTick);
    }

    private void notifyFullResim() {
        onDataChangedAt.accept(-1);
    }

    private float indexColumnDataWidth() {
        int maxRowNum = Math.max(1, data.size());
        float textW = ImGui.calcTextSize(String.valueOf(maxRowNum)).x;
        float cellPadX = ImGui.getStyle().getCellPadding().x;
        return Math.max(INDEX_DIGIT_WIDTH, textW + 2f * cellPadX + INDEX_DIGIT_SLACK);
    }

    private static float yawInputWidth() {
        float textW = ImGui.calcTextSize(YAW_WIDTH_SAMPLE).x;
        float framePadX = ImGui.getStyle().getFramePadding().x;
        return textW + 2f * framePadX;
    }

    private static float yawColumnWidth() {
        return yawInputWidth() + ThemeManager.tableScrollbarSlack() + ImGui.getStyle().getFramePadding().x;
    }

    public float desiredPaneWidth() {
        boolean potion = settings.showPotionColumns;
        int columnCount = BASE_COLUMN_COUNT + (potion ? POTION_COLUMN_COUNT : 0);

        ImGuiStyle style = ImGui.getStyle();
        float cellPadX = style.getCellPadding().x;
        float winPadX = style.getWindowPadding().x;
        float borderSlop = 2f;

        float columnSum = ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth())
                + ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth());
        for (InputRow.Key key : MOVEMENT_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), KEY_COL_MIN_WIDTH);
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), MOD_COL_MIN_WIDTH);
        }
        if (potion) {
            columnSum += ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH)
                    + ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH, ThemeManager.tableScrollbarSlack());
        }

        float contentW = columnSum + 2f * cellPadX * columnCount + borderSlop;
        return contentW + 2f * winPadX;
    }

    public void renderBody() {
        long t0 = Perf.now();
        try {
            renderBodyInternal();
        } finally {
            Perf.stop("InputOverlay.renderBody", t0);
        }
    }

    private void renderBodyInternal() {
        renderMultiplayerWarning();

        boolean potionColumns = settings.showPotionColumns;
        if (potionColumns) {
            renderPotionToolbar();
        }
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0);

        float footerH = footerHeightProvider.get();
        float tableH = Math.max(TABLE_MIN_HEIGHT, ImGui.getContentRegionAvail().y - footerH);
        if (ThemeManager.beginStandardTable(ID_TABLE, columnCount, 0, 0f, tableH)) {
            setupColumns(potionColumns);
            renderAllRows(potionColumns);
            ThemeManager.endStandardTable();
            if (data.getRows().isEmpty()) {
                renderEmptyTableHint();
            }
        }

        renderContextMenu();
        handleKeyboardShortcuts();

        keyDragSelect.update();
        int dragChangeStart = keyDragSelect.applyIfReleased(data.getRows());
        if (dragChangeStart >= 0) {
            notifyChange(dragChangeStart);
        }

        renderClearConfirmPopup();
    }

    private void renderEmptyTableHint() {
        ImVec2 rectMin = ImGui.getItemRectMin();
        ImVec2 rectMax = ImGui.getItemRectMax();
        ImVec2 textSize = ImGui.calcTextSize(EMPTY_HINT);
        float x = (rectMin.x + rectMax.x - textSize.x) * 0.5f;
        float y = (rectMin.y + rectMax.y - textSize.y) * 0.5f;
        ImGui.getWindowDrawList().addText(x, y, ThemeManager.textDimColor(), EMPTY_HINT);
    }

    private void renderMultiplayerWarning() {
        if (mc == null || !mc.isReady() || mc.isSinglePlayer()) return;
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.textWrapped(WARN_MULTIPLAYER);
        ThemeManager.popTextColor();
    }

    private void setupColumns(boolean potionColumns) {
        ImGui.tableSetupColumn(COL_INDEX,
                ImGuiTableColumnFlags.WidthFixed | ImGuiTableColumnFlags.NoResize,
                ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth()));
        for (InputRow.Key key : MOVEMENT_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(label, KEY_COL_MIN_WIDTH));
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(label, MOD_COL_MIN_WIDTH));
        }
        ImGui.tableSetupColumn(COL_YAW, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth()));
        if (potionColumns) {
            ImGui.tableSetupColumn(COL_SPEED, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH));
            ImGui.tableSetupColumn(COL_JUMP_BOOST, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH, ThemeManager.tableScrollbarSlack()));
        }
        ImGui.tableSetupScrollFreeze(0, 1);
        renderColumnHeadersWithTooltips(potionColumns);
    }

    private void renderColumnHeadersWithTooltips(boolean potionColumns) {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers);
        ThemeManager.paintTableHeader();
        int col = 0;
        ImGui.tableSetColumnIndex(col++);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeaderCentered(COL_INDEX);
        if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerColTooltip(COL_INDEX));
        for (InputRow.Key key : MOVEMENT_KEYS) {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(headerLabel(key));
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerTooltip(key));
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(headerLabel(key));
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerTooltip(key));
        }
        ImGui.tableSetColumnIndex(col++);
        ThemeManager.tableHeaderCentered(COL_YAW);
        if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerColTooltip(COL_YAW));
        if (!potionColumns) {
            ThemeManager.tableRightmostCellTrailingPad();
        } else {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(COL_SPEED);
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerColTooltip(COL_SPEED));
            ImGui.tableSetColumnIndex(col);
            ThemeManager.tableHeaderCentered(COL_JUMP_BOOST);
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerColTooltip(COL_JUMP_BOOST));
            ThemeManager.tableRightmostCellTrailingPad();
        }
    }

    private static String headerColTooltip(String col) {
        switch (col) {
            case COL_INDEX:      return "Tick number (1-based). Each row is one game tick.";
            case COL_YAW:        return "Yaw in degrees (-180 to 180). Empty = inherit previous tick's yaw.";
            case COL_SPEED:      return "Speed potion amplifier (none = no effect).";
            case COL_JUMP_BOOST: return "Jump Boost potion amplifier (none = no effect).";
            default:             return col;
        }
    }

    private static String headerLabel(InputRow.Key key) {
        switch (key) {
            case SPRINT: return "Spr";
            case SNEAK:  return "Snk";
            case JUMP:   return "Spc";
            default:     return key.name();
        }
    }

    private static String headerTooltip(InputRow.Key key) {
        switch (key) {
            case W:      return "Forward (W)";
            case A:      return "Strafe left (A)";
            case S:      return "Backward (S)";
            case D:      return "Strafe right (D)";
            case SPRINT: return "Sprint hold (Ctrl)";
            case SNEAK:  return "Sneak hold (Shift)";
            case JUMP:   return "Jump (Space)";
            default:     return key.name();
        }
    }

    private void renderAllRows(final boolean potionColumns) {
        final List<InputRow> rows = data.getRows();
        final ImDrawList drawList = ImGui.getWindowDrawList();
        keyDragSelect.clearRowBounds();

        final DragDropState dragDrop = new DragDropState();

        if (selection.consumeScrollRequest() && !selection.isEmpty()) {
            int target = selection.getSelected().iterator().next();
            float rowH = ImGui.getFrameHeightWithSpacing();
            float viewportH = ImGui.getWindowHeight();
            ImGui.setScrollY(Math.max(0f, target * rowH - viewportH * 0.5f));
        }

        ImGuiListClipper.forEach(rows.size(), new ImListClipperCallback() {
            @Override
            public void accept(int i) {
                renderRow(i, rows.get(i), dragDrop, potionColumns);
            }
        });

        renderDropIndicator(drawList, dragDrop);
        applyDragDrop(dragDrop);
    }

    private void renderRow(int index, InputRow row, DragDropState dragDrop, boolean potionColumns) {
        ImGui.pushID(row.getId());
        float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
        ImGui.tableNextRow(0, rowH);

        setRowBackground(index);

        ImGui.tableNextColumn();
        renderRowNumber(index);

        ImVec2 rowMin = ImGui.getItemRectMin();
        ImVec2 rowMax = ImGui.getItemRectMax();
        keyDragSelect.recordRowBounds(index, rowMin.y, rowMax.y);

        handleRowDragDrop(index, rowMin, rowMax, dragDrop);
        renderKeyColumns(row, index);
        renderYawColumn(row, index);
        if (potionColumns) {
            renderPotionColumns(row, index);
        } else {
            ThemeManager.tableRightmostCellTrailingPad();
        }

        ImGui.popID();
    }

    private void setRowBackground(int rowIndex) {
        ThemeManager.paintTableRowBg(rowIndex);
        int tint = 0;
        if (selection.isSelected(rowIndex)) {
            tint = ThemeManager.selectedTintColor(0.75f);
        } else if (draggingRowIndex == rowIndex) {
            tint = ThemeManager.selectedTintColor(0.75f);
        } else if (playback != null && playback.currentTick() == rowIndex) {
            tint = ThemeManager.warningTintColor(0.25f);
        }
        ThemeManager.paintTableRowTint(tint);
    }

    private void renderRowNumber(int rowIndex) {
        ThemeManager.tableLeftmostCellPad();
        boolean isSelected = selection.isSelected(rowIndex);
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap;
        if (ThemeManager.rightAlignedSelectable("row" + rowIndex, String.valueOf(rowIndex + 1), isSelected, flags)) {
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

            float gapInset = ImGui.getStyle().getCellPadding().y;
            state.dropLineY = insertAbove ? rowMin.y - gapInset : rowMax.y + gapInset;
            state.rowMinX = rowMin.x;
            state.rowMaxX = rowMax.x;

            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE, ImGuiDragDropFlags.AcceptNoDrawDefaultRect);
            if (payload != null && payload.length > 0) {
                state.moveFrom = payload[0] & 0xFF;
                state.moveTo = insertAbove ? index : index + 1;
            }
            ImGui.endDragDropTarget();
        }
    }

    private void renderDropIndicator(ImDrawList drawList, DragDropState state) {
        if (draggingRowIndex != -1 && state.dropLineY > 0) {
            drawList.addLine(state.rowMinX, state.dropLineY, state.rowMaxX, state.dropLineY,
                    ThemeManager.warningColor(), 2.0f);
        }
    }

    private void applyDragDrop(DragDropState state) {
        if (!ImGui.isMouseDragging(0)) {
            draggingRowIndex = -1;
        }

        if (state.moveFrom != -1 && state.moveTo != -1 && state.moveFrom != state.moveTo) {
            int dirtyTick = Math.min(state.moveFrom, state.moveTo);
            data.moveRow(state.moveFrom, state.moveTo);
            draggingRowIndex = -1;
            selection.clear();
            notifyChange(dirtyTick);
        }
    }

    private void renderKeyColumns(InputRow row, int rowIndex) {
        for (InputRow.Key key : MOVEMENT_KEYS) {
            renderKeyCell(row, rowIndex, key);
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            renderKeyCell(row, rowIndex, key);
        }
    }

    private void renderKeyCell(InputRow row, int rowIndex, InputRow.Key key) {
        ImGui.tableNextColumn();

        boolean actualValue = row.isKeyActive(key);
        boolean displayValue = keyDragSelect.getDisplayValue(key, rowIndex, actualValue);

        String cellLabel = displayValue ? headerLabel(key) : "";
        int color = displayValue ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        ThemeManager.pushTextColor(color);

        ThemeManager.centeredSelectable("key" + key.name(), cellLabel, displayValue);

        if (ImGui.isItemClicked(0)) {
            keyDragSelect.startDrag(key, rowIndex, actualValue);
        }

        ThemeManager.popTextColor();
    }

    private void renderYawColumn(InputRow row, int rowIndex) {
        ImGui.tableNextColumn();

        Float yaw = row.getYaw();
        boolean isEditing = editingYawRow == rowIndex;
        if (yaw == null) {
            yawInput.set("");
        } else if (isEditing) {
            yawInput.set(String.format(Locale.ROOT, YAW_FORMAT_EDIT, yaw));
        } else {
            yawInput.set(String.format(Locale.ROOT, YAW_FORMAT_DISPLAY, yaw));
        }

        boolean selectedRow = selection.isSelected(rowIndex);
        boolean populated = yaw != null;
        if (selectedRow) ThemeManager.pushSelectedFrameBg();
        if (populated) ThemeManager.pushPopulatedFrameBorder();
        float inputW = yawInputWidth();
        ThemeManager.centerNextItem(inputW);
        boolean changed = Controls.inputText(ID_YAW_INPUT, yawInput, inputW);
        if (ImGui.isItemActivated()) {
            editingYawRow = rowIndex;
        }
        if (editingYawRow == rowIndex && ImGui.isItemDeactivated()) {
            editingYawRow = -1;
        }
        if (populated) ThemeManager.popPopulatedFrameBorder();
        if (selectedRow) ThemeManager.popSelectedFrameBg();

        if (changed) {
            parseAndSetYaw(row);
            notifyChange(rowIndex);
        }
    }

    private void renderPotionColumns(InputRow row, int rowIndex) {
        ImGui.tableNextColumn();
        ampBuf.set(row.getSpeedAmplifier());
        ThemeManager.centerNextItem(AMP_CELL_WIDTH);
        if (Controls.combo(ID_SPEED_SUFFIX, ampBuf, AMP_LABELS, AMP_CELL_WIDTH)) {
            row.setSpeedAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }

        ImGui.tableNextColumn();
        ampBuf.set(row.getJumpBoostAmplifier());
        ThemeManager.centerNextItem(AMP_CELL_WIDTH);
        boolean jumpChanged = Controls.combo(ID_JUMP_SUFFIX, ampBuf, AMP_LABELS, AMP_CELL_WIDTH);
        ThemeManager.tableRightmostCellTrailingPad();
        if (jumpChanged) {
            row.setJumpBoostAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }
    }

    private void renderPotionToolbar() {
        if (Controls.combo(LABEL_SET_ALL_SPEED, bulkSpeedBuf, AMP_LABELS, AMP_TOOLBAR_WIDTH)) {
            applyAmplifierToAll(true, bulkSpeedBuf.get());
            notifyFullResim();
        }
        ImGui.sameLine();
        if (Controls.combo(LABEL_SET_ALL_JUMP, bulkJumpBuf, AMP_LABELS, AMP_TOOLBAR_WIDTH)) {
            applyAmplifierToAll(false, bulkJumpBuf.get());
            notifyFullResim();
        }
    }

    private void applyAmplifierToAll(boolean speed, int amplifier) {
        for (InputRow row : data.getRows()) {
            if (speed) {
                row.setSpeedAmplifier(amplifier);
            } else {
                row.setJumpBoostAmplifier(amplifier);
            }
        }
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

    private void renderClearConfirmPopup() {
        if (!ImGui.beginPopupModal(POPUP_CLEAR_CONFIRM, ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.text(String.format(CLEAR_CONFIRM_FMT, data.size()));
        ThemeManager.sectionSpacing();
        ImGui.separator();
        if (Controls.dangerButton(BTN_CLEAR_ALL)) {
            clearAllRows();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    public void addRowsAtEnd(int count) {
        if (count <= 0) return;
        int dirtyTick = data.size();
        data.addRows(data.size(), count);
        notifyChange(dirtyTick);
    }

    public void duplicateSelectedRows() {
        List<Integer> descending = selection.getSelectedDescending();
        if (descending.isEmpty()) return;
        int dirtyTick = Integer.MAX_VALUE;
        for (int idx : descending) {
            if (idx < 0 || idx >= data.size()) continue;
            data.insertRow(idx + 1, data.get(idx).copy());
            if (idx < dirtyTick) dirtyTick = idx;
        }
        selection.clear();
        notifyChange(dirtyTick == Integer.MAX_VALUE ? -1 : dirtyTick);
    }

    public void requestClearAll() {
        if (data.getRows().isEmpty()) return;
        ImGui.openPopup(POPUP_CLEAR_CONFIRM);
    }

    private void clearAllRows() {
        if (data.getRows().isEmpty()) return;
        data.clear();
        selection.clear();
        notifyChange(-1);
    }

    private void renderContextMenu() {

        if (ImGui.isMouseReleased(1)
                && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByPopup)) {
            ImGui.openPopup(ID_CONTEXT_MENU);
        }
        if (!ImGui.beginPopup(ID_CONTEXT_MENU)) {
            return;
        }

        if (ImGui.menuItem(MENU_SET_TO_PLAYER)) {
            onSetPlayerPosition.run();
            notifyFullResim();
        }

        ImGui.separator();
        renderRowCountInput();
        ImGui.separator();
        renderAddRowOptions();
        renderDeleteOption();
        renderDuplicateAndClearOptions();

        ImGui.endPopup();
    }

    private void renderDuplicateAndClearOptions() {
        boolean hasSelection = !selection.isEmpty();
        boolean hasRows = !data.getRows().isEmpty();
        if (!hasSelection && !hasRows) return;

        ImGui.separator();
        ImGui.beginDisabled(!hasSelection);
        if (ImGui.menuItem(MENU_DUPLICATE)) {
            duplicateSelectedRows();
        }
        ImGui.endDisabled();

        ImGui.beginDisabled(!hasRows);
        if (ImGui.menuItem(MENU_CLEAR_ALL)) {
            requestClearAll();
        }
        ImGui.endDisabled();
    }

    private void renderRowCountInput() {
        Controls.inputInt(LABEL_ROWS, rowsToAdd, ROW_COUNT_INPUT_WIDTH);
        rowsToAdd.set(Math.max(1, Math.min(1000, rowsToAdd.get())));
    }

    private void renderAddRowOptions() {
        int count = rowsToAdd.get();

        if (ImGui.menuItem(String.format(MENU_ADD_AT_END, count))) {
            int dirtyTick = data.size();
            data.addRows(data.size(), count);
            notifyChange(dirtyTick);
        }

        if (selection.size() == 1) {
            int selected = selection.getSelected().iterator().next();

            if (ImGui.menuItem(String.format(MENU_ADD_ABOVE, count))) {
                data.addRows(selected, count);
                selection.adjustForInsert(selected, count);
                notifyChange(selected);
            }

            if (ImGui.menuItem(String.format(MENU_ADD_BELOW, count))) {
                data.addRows(selected + 1, count);
                selection.adjustForInsert(selected + 1, count);
                notifyChange(selected + 1);
            }
        }
    }

    private void renderDeleteOption() {
        if (selection.isEmpty()) {
            return;
        }

        ImGui.separator();
        if (ImGui.menuItem(String.format(MENU_DELETE, selection.size()), MENU_DELETE_SHORTCUT)) {
            deleteSelectedRows();
        }
    }

    public void deleteSelectedRows() {
        Set<Integer> selected = selection.getSelected();
        int dirtyTick = selected.isEmpty() ? -1 : java.util.Collections.min(selected);
        data.removeRows(selection.getSelectedDescending());
        selection.clear();
        notifyChange(Math.max(0, dirtyTick));
    }

    private void handleKeyboardShortcuts() {
        if (ImGui.isKeyPressed(ImGuiKey.Delete) && !selection.isEmpty()) {
            deleteSelectedRows();
        }
        if (ImGui.isKeyPressed(ImGuiKey.Insert)) {
            insertRowsAtSelectionOrEnd(rowsToAdd.get());
        }
    }

    private void insertRowsAtSelectionOrEnd(int count) {
        if (count <= 0) return;
        if (selection.size() == 1) {
            int selected = selection.getSelected().iterator().next();
            data.addRows(selected, count);
            selection.adjustForInsert(selected, count);
            notifyChange(selected);
        } else {
            addRowsAtEnd(count);
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
