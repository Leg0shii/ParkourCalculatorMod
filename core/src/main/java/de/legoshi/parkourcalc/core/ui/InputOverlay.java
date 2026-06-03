package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.ImGuiListClipper;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.callback.ImGuiInputTextCallback;
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
    private static final String START_LABEL = "Start";
    private static final String COL_YAW = "Yaw";
    private static final String COL_SPEED = "Speed";
    private static final String COL_JUMP_BOOST = "Jump";
    private static final float INDEX_DIGIT_WIDTH = 32f;
    private static final float INDEX_DIGIT_SLACK = 6f;
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

    private static final String MENU_APPLY_SPEED_TO_ALL = "Apply tick 1 Speed to all rows";
    private static final String MENU_APPLY_JUMP_TO_ALL = "Apply tick 1 Jump to all rows";

    private static final String[] AMP_LABELS = {
            "none", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"
    };
    private static final float AMP_CELL_WIDTH = 110;
    private static final float AMP_COLUMN_WIDTH = 120;

    private static final String MENU_SET_TO_PLAYER = "Set to user position";
    private static final String LABEL_ROWS = "Rows:";
    private static final String MENU_ADD_AT_END = "Add %d row(s) at end";
    private static final String MENU_ADD_ABOVE = "Add %d row(s) above";
    private static final String MENU_ADD_BELOW = "Add %d row(s) below";
    private static final String MENU_DELETE = "Delete %d row(s)";
    private static final String MENU_DELETE_SHORTCUT = "Del";

    private static final String MENU_DUPLICATE = "Duplicate selected";

    private static final String MENU_LOCK_YAW = "Lock yaw value";
    private static final String MENU_UNLOCK_YAW = "Unlock yaw value";

    private static final String YAW_FORMAT_DISPLAY = "% 12.6f";

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
    private final BoxController boxController;

    private final SelectionManager selection;
    private final KeyDragSelect keyDragSelect = new KeyDragSelect();
    private final ImString yawInput = new ImString(32);
    private final ImInt rowsToAdd = new ImInt(1);
    private final ImInt ampBuf = new ImInt();

    private int draggingRowIndex = -1;
    private int editingYawRow = -1;
    private int pendingYawFocusRow = -1;
    private int collapseYawSelectionRow = -1;
    private int collapseYawFramesLeft;
    private int yawCallbackRow = -1;
    private int carryYawCursorPos;
    private int hoveredRow = -1;
    private int pendingLockToggleRow = -1;

    private static final int CALLBACK_ALWAYS = ImGuiInputTextFlags.CallbackAlways;
    private static final int CALLBACK_CHAR_FILTER = ImGuiInputTextFlags.CallbackCharFilter;
    private static final int COLLAPSE_FRAMES = 4;

    /** Swallow the current input char so it is not typed (e.g. F toggles the row lock instead). */
    private static void discardEventChar(ImGuiInputTextCallbackData data) {
        data.setEventChar((char) 0);
    }

    private final ImGuiInputTextCallback yawSelectionCallback = new ImGuiInputTextCallback() {
        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            if (data.getEventFlag() == CALLBACK_CHAR_FILTER) {
                char c = (char) data.getEventChar();
                if (c == 'f' || c == 'F') {
                    discardEventChar(data); // swallow it; F toggles the row lock instead of typing
                    if (yawCallbackRow >= 0) pendingLockToggleRow = yawCallbackRow;
                }
                return;
            }
            if (yawCallbackRow >= 0 && yawCallbackRow == collapseYawSelectionRow) {
                int pos = Math.min(carryYawCursorPos, data.getBuf().length());
                data.setCursorPos(pos);
                data.setSelectionStart(pos);
                data.setSelectionEnd(pos);
                if (--collapseYawFramesLeft <= 0) {
                    collapseYawSelectionRow = -1;
                }
            } else {
                carryYawCursorPos = data.getCursorPos();
            }
        }
    };

    private Supplier<Float> footerHeightProvider = () -> 0f;

    public void setFooterHeightProvider(Supplier<Float> provider) {
        this.footerHeightProvider = provider;
    }

    public InputOverlay(InputData data, Settings settings, SelectionManager selection,
                        IntConsumer onDataChangedAt, Runnable onSetPlayerPosition,
                        PlaybackController playback, MinecraftAccess mc,
                        BoxController boxController) {
        this.data = data;
        this.settings = settings;
        this.selection = selection;
        this.onDataChangedAt = onDataChangedAt;
        this.onSetPlayerPosition = onSetPlayerPosition;
        this.playback = playback;
        this.mc = mc;
        this.boxController = boxController;
    }

    private void notifyChange(int dirtyTick) {
        onDataChangedAt.accept(dirtyTick);
    }

    private void notifyFullResim() {
        onDataChangedAt.accept(-1);
    }

    private float uiScale() {
        int idx = settings.scaleIndex;
        if (idx < 0 || idx >= Settings.PRESET_SCALES.length) idx = Settings.DEFAULT_SCALE_INDEX;
        return Settings.PRESET_SCALES[idx];
    }

    private float indexColumnDataWidth() {
        int maxRowNum = Math.max(1, data.size());
        float scale = uiScale();
        float textW = Math.max(ImGui.calcTextSize(String.valueOf(maxRowNum)).x,
                ImGui.calcTextSize(START_LABEL).x);
        float cellPadX = ImGui.getStyle().getCellPadding().x;
        return Math.max(INDEX_DIGIT_WIDTH * scale, textW + 2f * cellPadX + INDEX_DIGIT_SLACK * scale);
    }

    private static float yawInputWidth() {
        float textW = ImGui.calcTextSize(YAW_WIDTH_SAMPLE).x;
        float framePadX = ImGui.getStyle().getFramePadding().x;
        return textW + 2f * framePadX;
    }

    private static float yawColumnWidth() {
        return yawInputWidth() + yawLockStripWidth() + ThemeManager.tableScrollbarSlack() + ImGui.getStyle().getFramePadding().x;
    }

    /** Reserved strip on the left of the yaw cell where the lock padlock is drawn, so it never overlaps the value. */
    private static float yawLockStripWidth() {
        return ImGui.getFrameHeight() * 0.8f;
    }

    public float desiredPaneWidth() {
        boolean potion = settings.showPotionColumns;
        int columnCount = BASE_COLUMN_COUNT + (potion ? POTION_COLUMN_COUNT : 0);

        ImGuiStyle style = ImGui.getStyle();
        float cellPadX = style.getCellPadding().x;
        float winPadX = style.getWindowPadding().x;
        float borderSlop = 2f;
        float scale = uiScale();

        float columnSum = ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth())
                + ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth());
        for (InputRow.Key key : MOVEMENT_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), 0f);
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), 0f);
        }
        if (potion) {
            columnSum += ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH * scale)
                    + ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH * scale, ThemeManager.tableScrollbarSlack());
        }

        float contentW = columnSum + 2f * cellPadX * columnCount + borderSlop;
        return contentW + 2f * winPadX;
    }

    // Smallest width that still shows the frozen Tick column plus W/A/S/D; everything
    // past it (modifiers, yaw, potions) is reached by the table's horizontal scroll.
    public float minUsablePaneWidth() {
        int columnCount = 1 + MOVEMENT_KEYS.length;

        ImGuiStyle style = ImGui.getStyle();
        float cellPadX = style.getCellPadding().x;
        float winPadX = style.getWindowPadding().x;
        float borderSlop = 2f;

        float columnSum = ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth());
        for (InputRow.Key key : MOVEMENT_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), 0f);
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
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0);

        float footerH = footerHeightProvider.get();
        float avail = Math.max(TABLE_MIN_HEIGHT, ImGui.getContentRegionAvail().y - footerH);
        // Fit the table to its rows so the inner column borders stop at the last row
        // instead of extending through empty space below it; fall back to filling the
        // pane (and scrolling) once the rows outgrow the available height.
        boolean hasStart = hasStartRow();
        int displayRowCount = data.size() + (hasStart ? 1 : 0);
        float tableH = (data.getRows().isEmpty() && !hasStart)
                ? avail
                : Math.min(avail, tableContentHeight(displayRowCount));
        if (ThemeManager.beginStandardTable(ID_TABLE, columnCount, ImGuiTableFlags.ScrollX, 0f, tableH)) {
            setupColumns(potionColumns);
            renderAllRows(potionColumns);
            ThemeManager.endStandardTable();
            if (data.getRows().isEmpty() && !hasStart) {
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
    }

    private float tableContentHeight(int rowCount) {
        float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
        float borderSlop = 4f; // outer borders + guard so an exact fit doesn't trip the scrollbar
        return ThemeManager.tableHeaderRowHeight() + rowCount * rowH + borderSlop;
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
        float scale = uiScale();
        ImGui.tableSetupColumn(COL_INDEX,
                ImGuiTableColumnFlags.WidthFixed | ImGuiTableColumnFlags.NoResize,
                ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth()));
        for (InputRow.Key key : MOVEMENT_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableNumericColumnWidth(label, 0f));
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableNumericColumnWidth(label, 0f));
        }
        ImGui.tableSetupColumn(COL_YAW, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth()));
        if (potionColumns) {
            ImGui.tableSetupColumn(COL_SPEED, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH * scale));
            ImGui.tableSetupColumn(COL_JUMP_BOOST, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH * scale, ThemeManager.tableScrollbarSlack()));
        }
        ImGui.tableSetupScrollFreeze(1, 1);
        renderColumnHeadersWithTooltips(potionColumns);
    }

    private void renderColumnHeadersWithTooltips(boolean potionColumns) {
        ThemeManager.tableHeaderRow();
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
        hoveredRow = -1;

        final DragDropState dragDrop = new DragDropState();

        int startOffset = hasStartRow() ? 1 : 0;
        if (selection.consumeScrollRequest() && !selection.isEmpty()) {
            int target = selection.getSelected().iterator().next();
            float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
            float viewportH = ImGui.getWindowHeight();
            ImGui.setScrollY(Math.max(0f, (target + startOffset) * rowH - viewportH * 0.5f));
        }
        scrollPendingYawFocusIntoView(startOffset);
        scrollPlaybackTickIntoView(startOffset);

        renderStartRow(potionColumns);

        ImGuiListClipper.forEach(rows.size(), new ImListClipperCallback() {
            @Override
            public void accept(int i) {
                renderRow(i, rows.get(i), dragDrop, potionColumns);
            }
        });

        renderDropIndicator(drawList, dragDrop);
        applyDragDrop(dragDrop);
    }

    private boolean hasStartRow() {
        return boxController != null && boxController.getState(0) != null;
    }

    /** Start state (states[0]), shown as a disabled, non-selectable anchor row above the editable Tick rows. */
    private void renderStartRow(boolean potionColumns) {
        if (!hasStartRow()) return;
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0);
        float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
        ImGui.tableNextRow(0, rowH);
        ThemeManager.paintTableRowBg(0);

        ImGui.beginDisabled(true);
        ImGui.tableNextColumn();
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.rightAlignedSelectable("startrow", START_LABEL, false, 0);
        for (int c = 1; c < columnCount; c++) {
            ImGui.tableNextColumn();
        }
        ThemeManager.tableRightmostCellTrailingPad();
        ImGui.endDisabled();
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
        if (ImGui.isMouseHoveringRect(rowMin.x, rowMin.y, rowMax.x, rowMax.y)) {
            hoveredRow = index;
        }

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
        } else if (settings.highlightOnGroundRows && isOnGroundAtTick(rowIndex)) {
            tint = ThemeManager.rgbaTintColor(settings.tickGroundHighlight);
        }
        ThemeManager.paintTableRowTint(tint);
    }


    private boolean isOnGroundAtTick(int rowIndex) {
        if (boxController == null) return false;
        TickState s = boxController.getState(rowIndex);
        return s != null && s.onGround;
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

        if (yaw == null) {
            yawInput.set("");
        } else {
            yawInput.set(String.format(Locale.ROOT, YAW_FORMAT_DISPLAY, yaw));
        }

        boolean selectedRow = selection.isSelected(rowIndex);
        boolean populated = yaw != null;
        boolean locked = row.isYawLocked();
        if (selectedRow) ThemeManager.pushSelectedFrameBg();
        if (populated) ThemeManager.pushPopulatedFrameBorder();
        float inputW = yawInputWidth();
        float lockStripX = ImGui.getCursorScreenPos().x;
        ImGui.setCursorPosX(ImGui.getCursorPosX() + yawLockStripWidth());
        if (pendingYawFocusRow == rowIndex) {
            ImGui.setKeyboardFocusHere();
            collapseYawSelectionRow = rowIndex; // keyboard focus auto-selects all; collapse it to a cursor instead
            collapseYawFramesLeft = COLLAPSE_FRAMES;
            pendingYawFocusRow = -1;
        }
        yawCallbackRow = rowIndex;
        boolean changed = Controls.tableInputText(ID_YAW_INPUT, yawInput, inputW,
                CALLBACK_ALWAYS | CALLBACK_CHAR_FILTER, yawSelectionCallback);
        if (locked) drawYawLockIcon(lockStripX);
        if (ImGui.isItemActivated()) {
            editingYawRow = rowIndex;
        }
        if (editingYawRow == rowIndex && ImGui.isItemDeactivated()) {
            editingYawRow = -1;
        }
        if (populated) ThemeManager.popPopulatedFrameBorder();
        if (selectedRow) ThemeManager.popSelectedFrameBg();

        if (pendingLockToggleRow == rowIndex) {
            row.setYawLocked(!row.isYawLocked());
            pendingLockToggleRow = -1;
            notifyChange(rowIndex);
        }
        if (changed) {
            parseAndSetYaw(row);
            notifyChange(rowIndex);
        }
    }

    /** Small padlock centered in the reserved strip left of the yaw input, so it never overlaps the value. */
    private void drawYawLockIcon(float stripLeftX) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        int color = ThemeManager.lockedColor();
        float h = mx.y - mn.y;
        float bodyW = h * 0.42f;
        float bodyH = h * 0.34f;
        float shackleR = bodyW * 0.32f;
        float glyphH = bodyH + shackleR; // body plus the shackle that rises above it
        float cx = stripLeftX + yawLockStripWidth() * 0.5f; // centered in the strip
        float cy = mn.y + h * 0.5f; // centered on the input
        float bodyLeft = cx - bodyW * 0.5f;
        float bodyTop = cy - glyphH * 0.5f + shackleR;
        dl.addRectFilled(bodyLeft, bodyTop, bodyLeft + bodyW, bodyTop + bodyH, color, 2f);
        dl.addCircle(cx, bodyTop, shackleR, color, 12, 1.5f);
    }

    public boolean isEditingYaw() {
        return editingYawRow >= 0;
    }

    public void navigateYaw(boolean forward) {
        int from = editingYawRow;
        if (from < 0) return;
        int to = -1;
        if (forward && from < data.size() - 1) {
            to = from + 1;
        } else if (!forward && from > 0) {
            to = from - 1;
        }
        if (to >= 0) {
            pendingYawFocusRow = to;
        }
    }

    private void scrollPlaybackTickIntoView(int startOffset) {
        if (playback == null) return;
        int tick = playback.currentTick();
        if (tick < 0) return;
        float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
        float top = (tick + startOffset) * rowH;
        float viewportH = ImGui.getWindowHeight();
        float anchor = viewportH / 3f;
        ImGui.setScrollY(Math.max(0f, top - anchor));
    }

    private void scrollPendingYawFocusIntoView(int startOffset) {
        if (pendingYawFocusRow < 0) return;
        // Match the row stride in renderRow; getFrameHeightWithSpacing drifts per row.
        float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
        float top = (pendingYawFocusRow + startOffset) * rowH;
        float bottom = top + rowH;
        float scroll = ImGui.getScrollY();
        float viewportH = ImGui.getWindowHeight();
        if (top < scroll) {
            ImGui.setScrollY(top);
        } else if (bottom > scroll + viewportH) {
            ImGui.setScrollY(bottom - viewportH);
        }
    }

    private void renderPotionColumns(InputRow row, int rowIndex) {
        float ampW = AMP_CELL_WIDTH * uiScale();
        ImGui.tableNextColumn();
        ampBuf.set(row.getSpeedAmplifier());
        ThemeManager.centerNextItem(ampW);
        if (Controls.tableCombo(ID_SPEED_SUFFIX, ampBuf, AMP_LABELS, ampW)) {
            row.setSpeedAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }

        ImGui.tableNextColumn();
        ampBuf.set(row.getJumpBoostAmplifier());
        ThemeManager.centerNextItem(ampW);
        boolean jumpChanged = Controls.tableCombo(ID_JUMP_SUFFIX, ampBuf, AMP_LABELS, ampW);
        ThemeManager.tableRightmostCellTrailingPad();
        if (jumpChanged) {
            row.setJumpBoostAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }
    }

    private void renderApplyPotionOptions() {
        if (!settings.showPotionColumns || data.getRows().isEmpty()) return;

        InputRow first = data.get(0);
        ThemeManager.paddedSeparator();
        if (ImGui.menuItem(MENU_APPLY_SPEED_TO_ALL)) {
            applyAmplifierToAll(true, first.getSpeedAmplifier());
            notifyFullResim();
        }
        if (ImGui.menuItem(MENU_APPLY_JUMP_TO_ALL)) {
            applyAmplifierToAll(false, first.getJumpBoostAmplifier());
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

    private void renderContextMenu() {

        if (ImGui.isMouseReleased(1)
                && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByPopup)) {
            if (hoveredRow >= 0 && !selection.isSelected(hoveredRow)) {
                selection.selectOnly(hoveredRow);
            }
            ImGui.openPopup(ID_CONTEXT_MENU);
        }
        if (!ImGui.beginPopup(ID_CONTEXT_MENU)) {
            return;
        }

        if (ImGui.menuItem(MENU_SET_TO_PLAYER)) {
            onSetPlayerPosition.run();
            notifyFullResim();
        }

        renderApplyPotionOptions();
        renderYawLockOption();

        ThemeManager.paddedSeparator();
        renderRowCountInput();
        ThemeManager.paddedSeparator();
        renderAddRowOptions();
        renderDeleteOption();
        renderDuplicateOption();

        ImGui.endPopup();
    }

    private void renderYawLockOption() {
        if (selection.isEmpty()) return;
        boolean anyUnlocked = false;
        for (int idx : selection.getSelected()) {
            if (idx >= 0 && idx < data.size() && !data.get(idx).isYawLocked()) {
                anyUnlocked = true;
                break;
            }
        }
        ThemeManager.paddedSeparator();
        if (anyUnlocked) {
            if (ImGui.menuItem(MENU_LOCK_YAW)) setYawLockForSelection(true);
        } else {
            if (ImGui.menuItem(MENU_UNLOCK_YAW)) setYawLockForSelection(false);
        }
    }

    private void setYawLockForSelection(boolean locked) {
        int dirtyTick = Integer.MAX_VALUE;
        for (int idx : selection.getSelected()) {
            if (idx < 0 || idx >= data.size()) continue;
            data.get(idx).setYawLocked(locked);
            if (idx < dirtyTick) dirtyTick = idx;
        }
        if (dirtyTick != Integer.MAX_VALUE) notifyChange(dirtyTick);
    }

    private void renderDuplicateOption() {
        boolean hasSelection = !selection.isEmpty();
        if (!hasSelection) return;

        ThemeManager.paddedSeparator();
        ImGui.beginDisabled(!hasSelection);
        if (ImGui.menuItem(MENU_DUPLICATE)) {
            duplicateSelectedRows();
        }
        ImGui.endDisabled();
    }

    private void renderRowCountInput() {
        Controls.inputInt(LABEL_ROWS, rowsToAdd, ROW_COUNT_INPUT_WIDTH * uiScale());
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

        ThemeManager.paddedSeparator();
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
