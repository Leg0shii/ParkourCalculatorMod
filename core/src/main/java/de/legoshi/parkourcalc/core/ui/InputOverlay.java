package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiListClipper;
import imgui.ImVec2;
import imgui.callback.ImListClipperCallback;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

public final class InputOverlay implements RenderInterface {

    private static final String WINDOW_TITLE = "Parkour TAS##parkour-tas";
    private static final String TITLE_TEXT = "Parkour TAS";
    private static final float VERSION_PADDING_RIGHT = 8f;
    // Gap between title text and version label, plus slack for window frame padding on both sides.
    private static final float TITLE_VERSION_GAP = 48f;

    private final String versionLabel;

    private static final String ID_TABLE = "tas-table";
    private static final String ID_CONTEXT_MENU = "context_menu";
    private static final String ID_YAW_INPUT = "##yaw";
    private static final String ID_ROW_SUFFIX = ".##row";
    private static final String ID_KEY_SUFFIX = "##";

    private static final String COL_INDEX = "#";
    private static final String COL_YAW = "Yaw";
    private static final String COL_SPEED = "Speed";
    private static final String COL_JUMP_BOOST = "Jump";
    // U+00B7 middle dot. Centered visually, doesn't read as punctuation like an ASCII period.
    private static final String CELL_OFF = "·";
    // Spacer columns visually separate logical key groups (WASD | modifiers | Yaw).
    // 6px content width + 4px cell padding each side = ~14px visual gap, per Visual quality contract.
    private static final String COL_GAP_KEYS = "##gap_keys";
    private static final String COL_GAP_YAW = "##gap_yaw";
    private static final float COL_GAP_WIDTH = 6.0f;
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

    private static final String LABEL_ADD_N = "Add N";
    private static final float TOOLBAR_STEPPER_WIDTH = 140f;
    private static final String TOOLTIP_ADD_N = "Rows added each time you click Add. Persists across the session.";
    private static final String BTN_ADD = "Add";
    private static final String BTN_DUPLICATE = "Duplicate";
    private static final String BTN_DELETE = "Delete";
    private static final String BTN_CLEAR_ALL = "Clear All";
    private static final String BTN_CANCEL = "Cancel";
    private static final String POPUP_CLEAR_CONFIRM = "Clear all rows?##clear_confirm";
    private static final String CLEAR_CONFIRM_FMT = "Delete all %d rows? This cannot be undone.";

    private static final String YAW_FORMAT = "%.5f";

    private static final String DRAG_DROP_TYPE = "INPUT_ROW";

    private static final float TABLE_MAX_HEIGHT = 900;
    private static final float TABLE_MIN_HEIGHT = 60;
    private static final float RESIZE_HANDLE_HEIGHT = 6;
    private static final int BASE_COLUMN_COUNT = 11;
    private static final int POTION_COLUMN_COUNT = 2;
    // inputInt reserves this width for the text field + the two +/- step buttons combined,
    // so it must comfortably exceed 2 * frame_height (~40 px) to leave room to type.
    private static final float ROW_COUNT_INPUT_WIDTH = 240;

    private static final String WARN_MULTIPLAYER =
            "Multiplayer: simulator only sees blocks inside your render distance.";

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
    private float userTableHeight = -1f;

    public InputOverlay(InputData data, Settings settings, SelectionManager selection,
                        IntConsumer onDataChangedAt, Runnable onSetPlayerPosition,
                        PlaybackController playback, MinecraftAccess mc, String modVersion) {
        this.data = data;
        this.settings = settings;
        this.selection = selection;
        this.onDataChangedAt = onDataChangedAt;
        this.onSetPlayerPosition = onSetPlayerPosition;
        this.playback = playback;
        this.mc = mc;
        this.versionLabel = "v" + modVersion;
    }

    private void notifyChange(int dirtyTick) {
        onDataChangedAt.accept(dirtyTick);
    }

    private void notifyFullResim() {
        onDataChangedAt.accept(-1);
    }

    @Override
    public void render(ImGuiIO io) {
        long t0 = Perf.now();
        try {
            renderInternal(io);
        } finally {
            Perf.stop("InputOverlay.render", t0);
        }
    }

    private void renderInternal(ImGuiIO io) {
        float minTitleBarWidth = ImGui.calcTextSize(TITLE_TEXT).x
                + ImGui.calcTextSize(versionLabel).x
                + TITLE_VERSION_GAP;
        ImGui.setNextWindowSizeConstraints(minTitleBarWidth, 0, Float.MAX_VALUE, Float.MAX_VALUE);
        boolean visible = ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.AlwaysAutoResize);
        renderVersionInTitleBar();
        if (!visible) {
            ImGui.end();
            return;
        }

        renderBody(computeTableHeight());
        renderResizeHandle();

        ImGui.end();
    }

    /** Public so MainWindowOverlay can host the editor in its body. tableHeight=0 means
     *  auto-fill available vertical space. */
    public void renderBody(float tableHeight) {
        renderMultiplayerWarning();
        ThemeManager.pushTransparentHeader();
        renderToolbar();

        boolean potionColumns = settings.showPotionColumns;
        if (potionColumns) {
            renderPotionToolbar();
        }
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0);

        if (ImGui.beginTable(ID_TABLE, columnCount, tableFlags(), 0, tableHeight)) {
            setupColumns(potionColumns);
            renderAllRows(potionColumns);
            ImGui.endTable();
        }

        renderContextMenu();
        handleKeyboardShortcuts();

        keyDragSelect.update();
        int dragChangeStart = keyDragSelect.applyIfReleased(data.getRows());
        if (dragChangeStart >= 0) {
            notifyChange(dragChangeStart);
        }

        renderClearConfirmPopup();
        ThemeManager.popTransparentHeader();
    }

    private void renderMultiplayerWarning() {
        if (mc == null || !mc.isReady() || mc.isSinglePlayer()) return;
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.textWrapped(WARN_MULTIPLAYER);
        ThemeManager.popTextColor();
    }

    private void renderVersionInTitleBar() {
        ImVec2 textSize = ImGui.calcTextSize(versionLabel);
        float titleBarH = ImGui.getFrameHeight();
        ImVec2 winPos = ImGui.getWindowPos();
        float winW = ImGui.getWindowWidth();
        float x = winPos.x + winW - textSize.x - VERSION_PADDING_RIGHT;
        float y = winPos.y + (titleBarH - textSize.y) * 0.5f;
        int color = ImGui.getColorU32(ImGuiCol.Text);
        ImGui.getForegroundDrawList().addText(x, y, color, versionLabel);
    }

    private int tableFlags() {
        return ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.RowBg | ImGuiTableFlags.ScrollY;
    }

    private float computeTableHeight() {
        if (userTableHeight > 0) {
            return Math.max(TABLE_MIN_HEIGHT, userTableHeight);
        }
        float rowHeight = ImGui.getFrameHeightWithSpacing();
        float desired = (data.size() + 1) * rowHeight + 8;
        return Math.min(desired, TABLE_MAX_HEIGHT);
    }

    private void renderResizeHandle() {
        // invisibleButton asserts both dims > 0; contentRegionAvail.x can be 0 when the window
        // is collapsed past the table's content width.
        float width = Math.max(1.0f, ImGui.getContentRegionAvail().x);
        ImGui.invisibleButton("##table_resize", width, RESIZE_HANDLE_HEIGHT);

        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();

        if (hovered || active) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
        }
        if (active) {
            // Lock in the current computed value the first frame of a drag so subsequent
            // deltas accumulate from the visible size rather than snapping to the cap.
            if (userTableHeight <= 0) {
                userTableHeight = computeTableHeight();
            }
            userTableHeight = Math.max(TABLE_MIN_HEIGHT, userTableHeight + ImGui.getIO().getMouseDeltaY());
        }
        if (hovered && ImGui.isMouseDoubleClicked(0)) {
            userTableHeight = -1f;
        }

        ImVec2 min = ImGui.getItemRectMin();
        ImVec2 max = ImGui.getItemRectMax();
        float midY = (min.y + max.y) / 2f;
        float intensity = active ? 0.9f : hovered ? 0.7f : 0.4f;
        int color = ImGui.colorConvertFloat4ToU32(intensity, intensity, intensity, 1.0f);
        ImGui.getWindowDrawList().addLine(min.x + 4, midY, max.x - 4, midY, color, 2.0f);
    }

    private void setupColumns(boolean potionColumns) {
        int spacerFlags = ImGuiTableColumnFlags.NoHeaderLabel | ImGuiTableColumnFlags.WidthFixed;
        ImGui.tableSetupColumn(COL_INDEX);
        for (InputRow.Key key : MOVEMENT_KEYS) {
            ImGui.tableSetupColumn(headerLabel(key));
        }
        ImGui.tableSetupColumn(COL_GAP_KEYS, spacerFlags, COL_GAP_WIDTH);
        for (InputRow.Key key : MODIFIER_KEYS) {
            ImGui.tableSetupColumn(headerLabel(key));
        }
        ImGui.tableSetupColumn(COL_GAP_YAW, spacerFlags, COL_GAP_WIDTH);
        ImGui.tableSetupColumn(COL_YAW, ImGuiTableColumnFlags.WidthFixed, 160);
        if (potionColumns) {
            ImGui.tableSetupColumn(COL_SPEED, ImGuiTableColumnFlags.WidthFixed, AMP_COLUMN_WIDTH);
            ImGui.tableSetupColumn(COL_JUMP_BOOST, ImGuiTableColumnFlags.WidthFixed, AMP_COLUMN_WIDTH);
        }
        renderColumnHeadersWithTooltips(potionColumns);
    }

    private void renderColumnHeadersWithTooltips(boolean potionColumns) {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers);
        int col = 0;
        ImGui.tableSetColumnIndex(col++);
        ImGui.tableHeader(COL_INDEX);
        for (InputRow.Key key : MOVEMENT_KEYS) {
            ImGui.tableSetColumnIndex(col++);
            ImGui.tableHeader(headerLabel(key));
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerTooltip(key));
        }
        col++; // gap_keys spacer
        for (InputRow.Key key : MODIFIER_KEYS) {
            ImGui.tableSetColumnIndex(col++);
            ImGui.tableHeader(headerLabel(key));
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(headerTooltip(key));
        }
        col++; // gap_yaw spacer
        ImGui.tableSetColumnIndex(col++);
        ImGui.tableHeader(COL_YAW);
        if (potionColumns) {
            ImGui.tableSetColumnIndex(col++);
            ImGui.tableHeader(COL_SPEED);
            ImGui.tableSetColumnIndex(col);
            ImGui.tableHeader(COL_JUMP_BOOST);
        }
    }

    private static String headerLabel(InputRow.Key key) {
        switch (key) {
            case SPRINT: return "Sprt";
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
            case JUMP:   return "Jump (Space) — column abbreviated Spc";
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
        ImGui.tableNextRow();

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
        }

        ImGui.popID();
    }

    private void setRowBackground(int rowIndex) {
        int color = 0;
        if (selection.isSelected(rowIndex)) {
            color = ThemeManager.accentDimColor();
        } else if (draggingRowIndex == rowIndex) {
            color = ThemeManager.accentDimColor();
        } else if (playback != null && playback.currentTick() == rowIndex) {
            color = ThemeManager.warningTintColor(0.25f);
        }

        if (color != 0) {
            ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, color);
        }
    }

    private void renderRowNumber(int rowIndex) {
        boolean isSelected = selection.isSelected(rowIndex);
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap;

        if (ImGui.selectable((rowIndex + 1) + ID_ROW_SUFFIX, isSelected, flags)) {
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
        ImGui.tableNextColumn(); // gap_keys spacer
        for (InputRow.Key key : MODIFIER_KEYS) {
            renderKeyCell(row, rowIndex, key);
        }
        ImGui.tableNextColumn(); // gap_yaw spacer
    }

    private void renderKeyCell(InputRow row, int rowIndex, InputRow.Key key) {
        ImGui.tableNextColumn();

        boolean actualValue = row.isKeyActive(key);
        boolean displayValue = keyDragSelect.getDisplayValue(key, rowIndex, actualValue);

        String cellLabel = displayValue ? headerLabel(key) : CELL_OFF;
        int color = displayValue ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        ThemeManager.pushTextColor(color);

        ImGui.selectable(cellLabel + ID_KEY_SUFFIX + key.name(), displayValue);

        if (ImGui.isItemClicked(0)) {
            keyDragSelect.startDrag(key, rowIndex, actualValue);
        }

        ThemeManager.popTextColor();
    }

    private void renderYawColumn(InputRow row, int rowIndex) {
        ImGui.tableNextColumn();

        Float yaw = row.getYaw();
        yawInput.set(yaw == null ? "" : String.format(Locale.US, YAW_FORMAT, yaw));

        if (Controls.inputText(ID_YAW_INPUT, yawInput, 150)) {
            parseAndSetYaw(row);
            notifyChange(rowIndex);
        }
    }

    private void renderPotionColumns(InputRow row, int rowIndex) {
        ImGui.tableNextColumn();
        ampBuf.set(row.getSpeedAmplifier());
        if (Controls.combo(ID_SPEED_SUFFIX, ampBuf, AMP_LABELS, AMP_CELL_WIDTH)) {
            row.setSpeedAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }

        ImGui.tableNextColumn();
        ampBuf.set(row.getJumpBoostAmplifier());
        if (Controls.combo(ID_JUMP_SUFFIX, ampBuf, AMP_LABELS, AMP_CELL_WIDTH)) {
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

    private void renderToolbar() {
        Controls.inputInt(LABEL_ADD_N, rowsToAdd, TOOLBAR_STEPPER_WIDTH);
        if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(TOOLTIP_ADD_N);
        rowsToAdd.set(Math.max(1, Math.min(1000, rowsToAdd.get())));

        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_ADD)) {
            addRowsAtEnd(rowsToAdd.get());
        }

        boolean hasSelection = !selection.isEmpty();
        ImGui.sameLine();
        ImGui.beginDisabled(!hasSelection);
        if (Controls.secondaryButton(BTN_DUPLICATE)) {
            duplicateSelectedRows();
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_DELETE)) {
            deleteSelectedRows();
        }
        ImGui.endDisabled();

        boolean hasRows = !data.getRows().isEmpty();
        ImGui.sameLine();
        ImGui.beginDisabled(!hasRows);
        if (Controls.secondaryButton(BTN_CLEAR_ALL)) {
            requestClearAll();
        }
        ImGui.endDisabled();
    }

    private void renderClearConfirmPopup() {
        if (!ImGui.beginPopupModal(POPUP_CLEAR_CONFIRM, ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.text(String.format(CLEAR_CONFIRM_FMT, data.size()));
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

        ImGui.endPopup();
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
        // ESC is owned by the loader's tick handler so it can decide
        // clear-selection vs close-overlay atomically.
    }

    private static class DragDropState {
        int moveFrom = -1;
        int moveTo = -1;
        float dropLineY = -1;
        float rowMinX = 0;
        float rowMaxX = 0;
    }
}
