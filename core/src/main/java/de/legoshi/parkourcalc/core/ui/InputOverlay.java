package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverTable;
import de.legoshi.parkourcalc.core.ui.anglesolver.SolverWidgets;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
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
    private static final int SOLVER_COLUMN_COUNT = 2; // Constraints + State
    private static final float SOLVER_CULL_OVERSCAN = 4f; // extra rows rendered above/below the viewport
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

    public InputOverlay(InputData data, Settings settings, SelectionManager selection, IntConsumer onDataChangedAt,
                        Runnable onSetPlayerPosition, PlaybackController playback, MinecraftAccess mc, BoxController boxController
    ) {
        this.data = data;
        this.settings = settings;
        this.selection = selection;
        this.onDataChangedAt = onDataChangedAt;
        this.onSetPlayerPosition = onSetPlayerPosition;
        this.playback = playback;
        this.mc = mc;
        this.boxController = boxController;
    }

    private AngleSolverTable angleSolver;

    public void setAngleSolver(AngleSolverTable angleSolver) {
        this.angleSolver = angleSolver;
    }

    private StartStateTable startState;

    public void setStartState(StartStateTable startState) {
        this.startState = startState;
    }

    private void solverRowsInserted(int index, int count) {
        if (angleSolver != null) angleSolver.onRowsInserted(index, count);
    }

    private boolean isSolverActive() {
        return angleSolver != null && angleSolver.isActive();
    }

    private int clampedExpandedRow() {
        if (!isSolverActive()) return -1;
        int r = angleSolver.expandedRow();
        return (r >= 0 && r < data.size()) ? r : -1;
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
        float textW = Math.max(ImGui.calcTextSize(String.valueOf(maxRowNum)).x, ImGui.calcTextSize(START_LABEL).x);
        float cellPadX = ImGui.getStyle().getCellPadding().x;
        float base = Math.max(INDEX_DIGIT_WIDTH * scale, textW + 2f * cellPadX + INDEX_DIGIT_SLACK * scale);
        if (isSolverActive()) base += angleSolver.gutterExtraWidth();
        return base;
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
        boolean solver = isSolverActive();
        int columnCount = BASE_COLUMN_COUNT + (potion ? POTION_COLUMN_COUNT : 0) + (solver ? SOLVER_COLUMN_COUNT : 0);

        ImGuiStyle style = ImGui.getStyle();
        float cellPadX = style.getCellPadding().x;
        float winPadX = style.getWindowPadding().x;
        float borderSlop = 2f;
        float scale = uiScale();

        float columnSum = ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth()) + ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth());
        for (InputRow.Key key : MOVEMENT_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), 0f);
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            columnSum += ThemeManager.tableColumnWidth(headerLabel(key), 0f);
        }
        if (potion) {
            columnSum += ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH * scale) + ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH * scale, ThemeManager.tableScrollbarSlack());
        }
        if (solver) {
            columnSum += angleSolver.minConstraintsColumnWidth() + angleSolver.minStateColumnWidth();
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

        boolean solverActive = isSolverActive();
        boolean potionColumns = settings.showPotionColumns;
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0) + (solverActive ? SOLVER_COLUMN_COUNT : 0);

        int drawerRow = clampedExpandedRow();
        float footerH = footerHeightProvider.get();

        float avail = Math.max(TABLE_MIN_HEIGHT, ImGui.getContentRegionAvail().y - footerH);
        renderInlineSolverBody(solverActive, drawerRow, potionColumns, columnCount, avail);

        renderContextMenu();
        handleKeyboardShortcuts();

        keyDragSelect.update();
        int dragChangeStart = keyDragSelect.applyIfReleased(data.getRows());
        if (dragChangeStart >= 0) {
            notifyChange(dragChangeStart);
        }
    }

    private float tableContentHeight(int rowCount) {
        float rowH = ThemeManager.tableRowHeight();
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

    // One scrolling child so expand/collapse keeps its scroll; an expanded tick splits the rows into two
    // segments around the inline drawer (else one culled segment).
    private void renderInlineSolverBody(boolean solverActive, int drawerRow, boolean potionColumns, int columnCount, float avail) {
        // Sticky header: a header-only twin table above the scroll child. The child always shows
        // its scrollbar so the twin's width (content minus scrollbar) matches the rows exactly.
        float headerTop = ImGui.getCursorPosY();
        float headerW = ImGui.getContentRegionAvail().x - ImGui.getStyle().getScrollbarSize();
        if (ThemeManager.beginStandardTableWithFlags("##tas-header", columnCount, ThemeManager.standardTableFlagsNoScroll(), headerW, 0f)) {
            setupColumns(potionColumns, solverActive, true, false);
            ThemeManager.endStandardTable();
        }
        ImGui.setCursorPosY(ImGui.getCursorPosY() - ImGui.getStyle().getItemSpacing().y); // rows sit flush under the header
        float bodyH = Math.max(TABLE_MIN_HEIGHT, avail - (ImGui.getCursorPosY() - headerTop));
        ImGui.beginChild("##solver_inline", 0f, bodyH, false, ImGuiWindowFlags.AlwaysVerticalScrollbar);
        ImVec2 clipMin = ImGui.getWindowPos();
        ImVec2 clipSize = ImGui.getWindowSize();

        keyDragSelect.clearRowBounds();
        hoveredRow = -1;
        if (solverActive) angleSolver.beginRows();

        handleAutoScroll(hasStartRow() ? 1 : 0);

        float overscan = SOLVER_CULL_OVERSCAN * ThemeManager.tableRowHeight();
        float viewTop = clipMin.y - overscan;
        float viewBot = clipMin.y + clipSize.y + overscan;
        int total = data.getRows().size();

        // One state across the segments so a drag can start in one and drop in the other (gh-119).
        DragDropState dragDrop = new DragDropState();
        float spacingY = ImGui.getStyle().getItemSpacing().y;
        boolean startOpen = startState != null && startState.isExpanded() && hasStartRow();
        boolean tickHead = !startOpen;

        if (startOpen) {
            renderInlineSegment("##tas-seg-start", columnCount, potionColumns, solverActive, true, 0, 0, viewTop, viewBot, dragDrop);
            ImGui.setCursorPosY(ImGui.getCursorPosY() - spacingY);
            float startDrawerTop = ImGui.getCursorScreenPos().y;
            startState.renderDrawer(ImGui.getContentRegionAvail().x);
            float startDrawerBottom = ImGui.getCursorScreenPos().y - spacingY;
            drawDrawerDecorations(startState.drawerDrawList(), clipMin, clipSize,
                    startGMinX, startGMinY, startGMaxX, startDrawerTop, startDrawerBottom);
            ImGui.setCursorPosY(ImGui.getCursorPosY() - spacingY);
        }

        if (drawerRow < 0) {
            renderInlineSegment("##tas-seg-a", columnCount, potionColumns, solverActive, tickHead, 0, total, viewTop, viewBot, dragDrop);
        } else {
            renderInlineSegment("##tas-seg-a", columnCount, potionColumns, solverActive, tickHead, 0, drawerRow + 1, viewTop, viewBot, dragDrop);
            // The expanded tick is the last row in segment A, so the gutter rect it carried out still bounds it.
            float unionTop = angleSolver.gutterMinY();
            float unionLeft = angleSolver.gutterMinX();
            float unionRight = angleSolver.gutterMaxX();
            ImGui.setCursorPosY(ImGui.getCursorPosY() - spacingY); // butt the drawer flush under the expanded tick
            float drawerTop = ImGui.getCursorScreenPos().y;
            angleSolver.renderDrawer(drawerRow, ImGui.getContentRegionAvail().x);
            float unionBottom = ImGui.getCursorScreenPos().y - spacingY;
            if (drawerRow + 1 < total) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() - spacingY);
                renderInlineSegment("##tas-seg-b", columnCount, potionColumns, solverActive, false, drawerRow + 1, total, viewTop, viewBot, dragDrop);
            }
            drawDrawerDecorations(angleSolver.drawerDrawList(), clipMin, clipSize,
                    unionLeft, unionTop, unionRight, drawerTop, unionBottom);
        }

        if (data.getRows().isEmpty() && !hasStartRow()) {
            renderEmptyTableHint();
        }
        renderDropIndicator(ImGui.getWindowDrawList(), dragDrop);
        applyDragDrop(dragDrop);
        if (solverActive) angleSolver.endRows();
        ImGui.endChild();
    }

    // Thin accent rail down the left edge plus top/bottom drop shadow, tying a row and its inline
    // drawer into one block. Drawn on the drawer child's own draw list (above the table content, below
    // every other window/popup) and clipped to the scroll region.
    private void drawDrawerDecorations(ImDrawList dl, ImVec2 clipMin, ImVec2 clipSize,
                                       float unionLeft, float unionTop, float unionRight, float drawerTop, float unionBottom) {
        if (dl == null) return;
        float s = ThemeManager.uiScale();
        dl.pushClipRect(clipMin.x, clipMin.y, clipMin.x + clipSize.x, clipMin.y + clipSize.y, false);
        float shadowH = 10f * s;
        dl.addRectFilled(unionLeft, drawerTop, unionRight, drawerTop + 1f * s, ThemeManager.shadowColor(0.85f));
        dl.addRectFilledMultiColor(unionLeft, drawerTop, unionRight, drawerTop + shadowH,
                color(ThemeManager.shadowColor(0.55f)), color(ThemeManager.shadowColor(0.55f)),
                color(ThemeManager.shadowColor(0f)), color(ThemeManager.shadowColor(0f))
        );
        dl.addRectFilled(unionLeft, unionBottom - 1f * s, unionRight, unionBottom, ThemeManager.shadowColor(0.85f));
        dl.addRectFilledMultiColor(unionLeft, unionBottom - shadowH, unionRight, unionBottom,
                color(ThemeManager.shadowColor(0f)), color(ThemeManager.shadowColor(0f)),
                color(ThemeManager.shadowColor(0.55f)), color(ThemeManager.shadowColor(0.55f))
        );
        dl.addRectFilled(unionLeft, unionTop, unionLeft + ThemeManager.XS * s, unionBottom, ThemeManager.accentColor());
        dl.popClipRect();
    }

    private void renderInlineSegment(String id, int columnCount, boolean potionColumns, boolean solverActive, boolean head, int from, int to, float viewTop, float viewBot, DragDropState dragDrop) {
        int flags = ThemeManager.standardTableFlagsNoScroll();
        if (!ThemeManager.beginStandardTableWithFlags(id, columnCount, flags, 0f, 0f)) return;
        setupColumns(potionColumns, solverActive, false, false); // headers live in the sticky twin table above the scroll child
        if (head && hasStartRow()) renderStartRowCells(potionColumns, true);
        int clampedTo = Math.min(to, data.getRows().size());
        renderSolverRowsCulled(from, clampedTo, viewTop, viewBot, dragDrop, potionColumns, solverActive);
        ThemeManager.endStandardTable();
    }

    // addRectFilledMultiColor takes unsigned-32 colors as long; mask off sign extension.
    private static long color(int u32) {
        return u32 & 0xFFFFFFFFL;
    }

    private void renderMultiplayerWarning() {
        if (mc == null || !mc.isReady() || mc.isSinglePlayer()) return;
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.textWrapped(WARN_MULTIPLAYER);
        ThemeManager.popTextColor();
    }

    private void setupColumns(boolean potionColumns, boolean solverActive, boolean renderHeaders, boolean scrollFreeze) {
        float scale = uiScale();
        ImGui.tableSetupColumn(COL_INDEX,
                ImGuiTableColumnFlags.WidthFixed | ImGuiTableColumnFlags.NoResize,
                ThemeManager.tableLeftmostColumnWidth(COL_INDEX, indexColumnDataWidth())
        );
        for (InputRow.Key key : MOVEMENT_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableNumericColumnWidth(label, 0f));
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            String label = headerLabel(key);
            ImGui.tableSetupColumn(label, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableNumericColumnWidth(label, 0f));
        }
        ImGui.tableSetupColumn(COL_YAW, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableColumnWidth(COL_YAW, yawColumnWidth()));
        if (potionColumns) {
            ImGui.tableSetupColumn(COL_SPEED, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableColumnWidth(COL_SPEED, AMP_COLUMN_WIDTH * scale));
            ImGui.tableSetupColumn(COL_JUMP_BOOST, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableRightmostColumnWidth(COL_JUMP_BOOST, AMP_COLUMN_WIDTH * scale, ThemeManager.tableScrollbarSlack()));
        }
        if (solverActive) {
            ImGui.tableSetupColumn(angleSolver.constraintsColumnHeaderLabel(), ImGuiTableColumnFlags.WidthStretch, 1.0f);
            ImGui.tableSetupColumn(angleSolver.stateColumnHeaderLabel(), ImGuiTableColumnFlags.WidthStretch, 0.8f);
        }
        if (scrollFreeze) ImGui.tableSetupScrollFreeze(1, 1);
        if (renderHeaders) renderColumnHeadersWithTooltips(potionColumns, solverActive);
    }

    private void renderColumnHeadersWithTooltips(boolean potionColumns, boolean solverActive) {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        int col = 0;
        ImGui.tableSetColumnIndex(col++);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeaderCentered(COL_INDEX);
        TooltipUtil.onHover(headerColTooltip(COL_INDEX));
        col = renderKeyColumnHeaders(MOVEMENT_KEYS, col);
        col = renderKeyColumnHeaders(MODIFIER_KEYS, col);
        ImGui.tableSetColumnIndex(col++);
        ThemeManager.tableHeaderCentered(COL_YAW);
        TooltipUtil.onHover(headerColTooltip(COL_YAW));
        if (potionColumns) {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(COL_SPEED);
            TooltipUtil.onHover(headerColTooltip(COL_SPEED));
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(COL_JUMP_BOOST);
            TooltipUtil.onHover(headerColTooltip(COL_JUMP_BOOST));
        }
        if (solverActive) {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(angleSolver.constraintsColumnHeaderLabel());
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(angleSolver.stateColumnHeaderLabel());
        }
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private int renderKeyColumnHeaders(InputRow.Key[] keys, int col) {
        for (InputRow.Key key : keys) {
            ImGui.tableSetColumnIndex(col++);
            ThemeManager.tableHeaderCentered(headerLabel(key));
            TooltipUtil.onHover(headerTooltip(key));
        }
        return col;
    }

    private static String headerColTooltip(String col) {
        switch (col) {
            case COL_INDEX: return "Tick number (1-based). Each row is one game tick.";
            case COL_YAW: return "Yaw in degrees (-180 to 180). Empty = inherit previous tick's yaw.";
            case COL_SPEED: return "Speed potion amplifier (none = no effect).";
            case COL_JUMP_BOOST: return "Jump Boost potion amplifier (none = no effect).";
            default: return col;
        }
    }

    private static String headerLabel(InputRow.Key key) {
        switch (key) {
            case SPRINT: return "Spr";
            case SNEAK: return "Snk";
            case JUMP: return "Spc";
            default: return key.name();
        }
    }

    private static String headerTooltip(InputRow.Key key) {
        switch (key) {
            case W: return "Forward (W)";
            case A: return "Strafe left (A)";
            case S: return "Backward (S)";
            case D: return "Strafe right (D)";
            case SPRINT: return "Sprint hold (Ctrl)";
            case SNEAK: return "Sneak hold (Shift)";
            case JUMP: return "Jump (Space)";
            default: return key.name();
        }
    }

    private void renderSolverRowsCulled(int from, int to, float viewTop, float viewBot, DragDropState dragDrop, boolean potionColumns, boolean solverActive) {
        final List<InputRow> rows = data.getRows();
        final float baseRowH = ThemeManager.tableRowHeight();

        float y = ImGui.getCursorScreenPos().y;
        int i = from;
        float lead = 0f;
        while (i < to) {
            float h = solverActive ? angleSolver.rowHeight(i, baseRowH) : baseRowH;
            if (y + h <= viewTop) { lead += h; y += h; i++; } else break;
        }
        if (lead > 0f) ImGui.tableNextRow(0, lead);

        while (i < to && y <= viewBot) {
            renderRow(i, rows.get(i), dragDrop, potionColumns, solverActive);
            y += solverActive ? angleSolver.rowHeight(i, baseRowH) : baseRowH;
            i++;
        }

        float trail = 0f;
        while (i < to) {
            trail += solverActive ? angleSolver.rowHeight(i, baseRowH) : baseRowH; i++;
        }
        if (trail > 0f) ImGui.tableNextRow(0, trail);
    }

    private void handleAutoScroll(int startOffset) {
        if (selection.consumeScrollRequest() && !selection.isEmpty()) {
            int target = selection.getSelected().iterator().next();
            float rowH = ThemeManager.tableRowHeight();
            float viewportH = ImGui.getWindowHeight();
            ImGui.setScrollY(Math.max(0f, target * rowH - viewportH * 0.5f));
        }
        scrollPendingYawFocusIntoView(startOffset);
        scrollPlaybackTickIntoView(startOffset);
    }

    private boolean hasStartRow() {
        return boxController != null && boxController.getState(0) != null;
    }

    private float startGMinX;
    private float startGMinY;
    private float startGMaxX;

    private void renderStartRowCells(boolean potionColumns, boolean withChevron) {
        int columnCount = BASE_COLUMN_COUNT + (potionColumns ? POTION_COLUMN_COUNT : 0) + (isSolverActive() ? SOLVER_COLUMN_COUNT : 0);
        float s = uiScale();
        float rowH = ThemeManager.tableRowHeight();
        ImGui.tableNextRow(0, rowH);
        boolean expanded = withChevron && startState != null && startState.isExpanded();
        if (expanded) {
            ThemeManager.paintExpandedDrawerRowBg();
        } else {
            ThemeManager.paintTableRowBg(0);
        }
        if (selection.isSelected(0)) {
            ThemeManager.paintTableRowTint(ThemeManager.selectedTintColor(0.38f));
        } else if (settings.highlightOnGroundRows && isOnGroundAtTick(0)) {
            ThemeManager.paintTableRowTint(ThemeManager.rgbaTintColor(settings.tickGroundHighlight));
        }
        ImGui.tableNextColumn();
        ThemeManager.tableLeftmostCellPad();
        ImVec2 origin = ImGui.getCursorScreenPos();
        float itemSpacingY = ImGui.getStyle().getItemSpacing().y;
        float gutterItemH = rowH - itemSpacingY;
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap;
        if (ThemeManager.rightAlignedSelectable("startrowsel", "", selection.isSelected(0), flags, 0f, gutterItemH)) {
            selection.handleClick(0);
        }
        ImVec2 selMin = ImGui.getItemRectMin();
        ImVec2 selMax = ImGui.getItemRectMax();
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        float centerH = rowH - 2f * cellPadY;
        startGMinX = selMin.x;
        startGMinY = selMin.y - cellPadY;
        startGMaxX = selMax.x;
        ImDrawList dl = ImGui.getWindowDrawList();
        float labelX = origin.x;
        float cy = origin.y + centerH * 0.5f;
        if (withChevron) {
            float chevW = 12f * s;
            ImGui.setCursorScreenPos(origin.x, origin.y);
            boolean chevClicked = ImGui.invisibleButton("startchev", chevW, gutterItemH);
            boolean open = startState != null && startState.isExpanded();
            int chevCol = open ? ThemeManager.accentColor() : ThemeManager.textDimColor();
            if (open) {
                SolverWidgets.triangleDown(dl, origin.x + chevW * 0.5f, cy, 3.3f * s, chevCol);
            } else {
                SolverWidgets.triangleRight(dl, origin.x + chevW * 0.5f, cy, 3.3f * s, chevCol);
            }
            if (chevClicked && startState != null) {
                startState.toggleExpanded();
            }
            labelX = origin.x + chevW;
        }
        float ty = origin.y + (centerH - ImGui.getFontSize()) * 0.5f;
        dl.addText(labelX + 4f * s, ty, ThemeManager.textMutedColor(), START_LABEL);
        for (int c = 1; c < columnCount; c++) {
            ImGui.tableNextColumn();
        }
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void renderRow(int index, InputRow row, DragDropState dragDrop, boolean potionColumns, boolean solverActive) {
        ImGui.pushID(row.getId());
        float baseRowH = ThemeManager.tableRowHeight();
        // Wrapping constraint/state chips grow the row; size it to last frame's content so single-line
        // cells can be centered within the taller row instead of clinging to the top.
        float rowH = solverActive ? angleSolver.rowHeight(index, baseRowH) : baseRowH;
        float centerY = (rowH - baseRowH) * 0.5f;
        ImGui.tableNextRow(0, rowH);

        setRowBackground(index);

        ImGui.tableNextColumn();
        renderRowNumber(index, solverActive, rowH, dragDrop);

        float rMinX, rMinY, rMaxX, rMaxY;
        if (solverActive) {
            rMinX = angleSolver.gutterMinX(); rMinY = angleSolver.gutterMinY();
            rMaxX = angleSolver.gutterMaxX(); rMaxY = angleSolver.gutterMaxY();
        } else {
            ImVec2 rowMin = ImGui.getItemRectMin();
            ImVec2 rowMax = ImGui.getItemRectMax();
            rMinX = rowMin.x; rMinY = rowMin.y; rMaxX = rowMax.x; rMaxY = rowMax.y;
            handleRowDragDrop(index, rMinX, rMinY, rMaxX, rMaxY, dragDrop);
        }

        keyDragSelect.recordRowBounds(index, rMinY, rMaxY);
        if (ImGui.isMouseHoveringRect(rMinX, rMinY, rMaxX, rMaxY)) {
            hoveredRow = index + 1;
        }

        renderKeyColumns(row, index, rowH);
        renderYawColumn(row, index, centerY);
        if (potionColumns) {
            renderPotionColumns(row, index);
        }
        if (solverActive) {
            ImGui.tableNextColumn();
            angleSolver.renderConstraintsCell(index, rowH);
            ImGui.tableNextColumn();
            angleSolver.renderStateCell(index, rowH);
        }
        ThemeManager.tableRightmostCellTrailingPad();

        if (solverActive) {
            angleSolver.drawStartLandingInset(index, rMinX, rMinY, rMaxX, rMaxY);
        }

        ImGui.popID();
    }

    private void setRowBackground(int rowIndex) {
        if (isSolverActive() && angleSolver.isExpanded(rowIndex)) {
            ThemeManager.paintExpandedDrawerRowBg();
        } else {
            ThemeManager.paintTableRowBg(rowIndex);
        }
        int tint = 0;
        // A light selection fill, so it doesn't clash with chips drawn over solver rows.
        float selAlpha = 0.38f;
        if (selection.isSelected(rowIndex + 1)) {
            tint = ThemeManager.selectedTintColor(selAlpha);
        } else if (draggingRowIndex == rowIndex) {
            tint = ThemeManager.selectedTintColor(selAlpha);
        } else if (playback != null && playback.currentTick() == rowIndex) {
            tint = ThemeManager.warningTintColor(0.25f);
        } else if (settings.highlightOnGroundRows && isOnGroundAtTick(rowIndex + 1)) {
            tint = ThemeManager.rgbaTintColor(settings.tickGroundHighlight);
        }
        ThemeManager.paintTableRowTint(tint);
    }


    private boolean isOnGroundAtTick(int pathIndex) {
        if (boxController == null) return false;
        TickState s = boxController.getState(pathIndex);
        return s != null && s.onGround;
    }

    private void renderRowNumber(int rowIndex, boolean solverActive, float rowH, DragDropState dragDrop) {
        if (solverActive) {
            angleSolver.renderGutter(rowIndex, rowH, () -> handleRowDragDrop(rowIndex,
                    angleSolver.gutterMinX(), angleSolver.gutterMinY(),
                    angleSolver.gutterMaxX(), angleSolver.gutterMaxY(), dragDrop));
            return;
        }
        ThemeManager.tableLeftmostCellPad();
        boolean isSelected = selection.isSelected(rowIndex + 1);
        int flags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap;
        if (ThemeManager.rightAlignedSelectable("row" + rowIndex, String.valueOf(rowIndex + 1), isSelected, flags)) {
            selection.handleClick(rowIndex + 1);
        }
    }

    private void handleRowDragDrop(int index, float rowMinX, float rowMinY, float rowMaxX, float rowMaxY, DragDropState state) {
        if (selection.size() > 1) {
            return; // Don't allow drag when multiple rows selected
        }

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            draggingRowIndex = index;
            // The payload is never read back: imgui-java only weak-references the Java object, so a
            // GC mid-drag silently drops it. The move is resolved from draggingRowIndex instead.
            ImGui.setDragDropPayload(DRAG_DROP_TYPE, Integer.valueOf(index));
            ImGui.endDragDropSource();
        }

        if (draggingRowIndex == -1) return;

        // Manual drop targeting. ImGui's item-rect target only covered the one-line gutter
        // selectable, leaving dead bands in the cell padding and on grown solver rows; judge the
        // mouse against the full row rect widened halfway into the gaps instead, and only within
        // the visible scroll region (culled overscan rows sit outside it).
        ImVec2 mouse = ImGui.getMousePos();
        float clipTop = ImGui.getWindowPos().y;
        float clipBot = clipTop + ImGui.getWindowHeight();
        if (mouse.y < clipTop || mouse.y > clipBot) return;
        if (mouse.x < rowMinX || mouse.x > rowMaxX) return;
        float gapInset = ImGui.getStyle().getCellPadding().y;
        if (mouse.y < rowMinY - gapInset - 1f || mouse.y >= rowMaxY + gapInset + 1f) return;

        boolean insertAbove = mouse.y < (rowMinY + rowMaxY) / 2;
        state.dropLineY = insertAbove ? rowMinY - gapInset : rowMaxY + gapInset;
        state.rowMinX = rowMinX;
        state.rowMaxX = rowMaxX;
        state.moveTo = insertAbove ? index : index + 1;
    }

    private void renderDropIndicator(ImDrawList drawList, DragDropState state) {
        if (draggingRowIndex != -1 && state.dropLineY > 0) {
            drawList.addLine(state.rowMinX, state.dropLineY, state.rowMaxX, state.dropLineY, ThemeManager.warningColor(), 2.0f);
        }
    }

    private void applyDragDrop(DragDropState state) {
        if (draggingRowIndex == -1) return;
        if (ImGui.isMouseReleased(0)) {
            int from = draggingRowIndex;
            int to = state.moveTo;
            draggingRowIndex = -1;
            if (to != -1 && data.moveRow(from, to)) {
                if (angleSolver != null) {
                    angleSolver.onRowMoved(from, to);
                }
                selection.clear();
                notifyChange(Math.min(from, to));
            }
        } else if (!ImGui.isMouseDown(0)) {
            draggingRowIndex = -1;
        }
    }

    private void renderKeyColumns(InputRow row, int rowIndex, float rowH) {
        for (InputRow.Key key : MOVEMENT_KEYS) {
            renderKeyCell(row, rowIndex, key, rowH);
        }
        for (InputRow.Key key : MODIFIER_KEYS) {
            renderKeyCell(row, rowIndex, key, rowH);
        }
    }

    private void renderKeyCell(InputRow row, int rowIndex, InputRow.Key key, float rowH) {
        ImGui.tableNextColumn();

        boolean actualValue = row.isKeyActive(key);
        boolean displayValue = keyDragSelect.getDisplayValue(key, rowIndex, actualValue);

        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        float cellPadY = ImGui.getStyle().getCellPadding().y;

        // Hitbox fills the row so a click anywhere in the cell toggles the key, not just the middle line.
        // Sized rowH - ItemSpacing.y so the fixed-height item doesn't inflate the row.
        float hitH = rowH - ImGui.getStyle().getItemSpacing().y;
        ImGui.alignTextToFramePadding();
        ImGui.selectable("##key" + key.name(), displayValue, 0, 0f, hitH);
        if (ImGui.isItemClicked(0)) {
            keyDragSelect.startDrag(key, rowIndex, actualValue);
        }

        if (displayValue) {
            String cellLabel = headerLabel(key);
            ImVec2 textSize = ImGui.calcTextSize(cellLabel);
            float tx = cellOrigin.x + (cellW - textSize.x) * 0.5f;
            float ty = cellOrigin.y + (rowH - 2f * cellPadY - textSize.y) * 0.5f;
            ImGui.getWindowDrawList().addText(tx, ty, ThemeManager.textColor(), cellLabel);
        }
    }

    private void renderYawColumn(InputRow row, int rowIndex, float centerY) {
        ImGui.tableNextColumn();
        if (centerY > 0f) ImGui.setCursorPosY(ImGui.getCursorPosY() + centerY);

        Float yaw = row.getYaw();

        if (yaw == null) {
            yawInput.set("");
        } else {
            yawInput.set(String.format(Locale.ROOT, YAW_FORMAT_DISPLAY, yaw));
        }

        boolean selectedRow = selection.isSelected(rowIndex + 1);
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
        boolean changed = Controls.tableInputText(ID_YAW_INPUT, yawInput, inputW, CALLBACK_ALWAYS | CALLBACK_CHAR_FILTER, yawSelectionCallback);
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

    public String playbackStatusHint() {
        return playback != null ? playback.statusHint() : "";
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
        float rowH = ThemeManager.tableRowHeight();
        float top = (tick + startOffset) * rowH;
        float viewportH = ImGui.getWindowHeight();
        float anchor = viewportH / 3f;
        ImGui.setScrollY(Math.max(0f, top - anchor));
    }

    private void scrollPendingYawFocusIntoView(int startOffset) {
        if (pendingYawFocusRow < 0) return;
        // Match the row stride in renderRow; getFrameHeightWithSpacing drifts per row.
        float rowH = ThemeManager.tableRowHeight();
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
        if (jumpChanged) {
            row.setJumpBoostAmplifier(ampBuf.get());
            notifyChange(rowIndex);
        }
    }

    private void renderApplyPotionOptions() {
        if (!settings.showPotionColumns || data.getRows().isEmpty()) return;

        InputRow first = data.get(0);
        ThemeManager.paddedSeparator();
        if (contextButton(MENU_APPLY_SPEED_TO_ALL)) {
            applyAmplifierToAll(true, first.getSpeedAmplifier());
            notifyFullResim();
        }
        if (contextButton(MENU_APPLY_JUMP_TO_ALL)) {
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
        solverRowsInserted(dirtyTick, count);
        notifyChange(dirtyTick);
    }

    public void duplicateSelectedRows() {
        List<Integer> descending = selection.getSelectedRowsDescending();
        if (descending.isEmpty()) return;
        int dirtyTick = Integer.MAX_VALUE;
        for (int idx : descending) {
            if (idx < 0 || idx >= data.size()) continue;
            data.insertRow(idx + 1, data.get(idx).copy());
            if (angleSolver != null) angleSolver.onRowDuplicated(idx);
            if (idx < dirtyTick) dirtyTick = idx;
        }
        selection.clear();
        notifyChange(dirtyTick == Integer.MAX_VALUE ? -1 : dirtyTick);
    }

    private void renderContextMenu() {

        if (ImGui.isMouseReleased(1) && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByPopup)) {
            if (hoveredRow >= 0 && !selection.isSelected(hoveredRow)) {
                selection.selectOnly(hoveredRow);
            }
            ImGui.openPopup(ID_CONTEXT_MENU);
        }
        if (!ImGui.beginPopup(ID_CONTEXT_MENU)) {
            return;
        }

        if (contextButton(MENU_SET_TO_PLAYER)) {
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
        for (int idx : selection.getSelectedRows()) {
            if (idx >= 0 && idx < data.size() && !data.get(idx).isYawLocked()) {
                anyUnlocked = true;
                break;
            }
        }
        ThemeManager.paddedSeparator();
        if (anyUnlocked) {
            if (contextButton(MENU_LOCK_YAW)) setYawLockForSelection(true);
        } else {
            if (contextButton(MENU_UNLOCK_YAW)) setYawLockForSelection(false);
        }
    }

    private void setYawLockForSelection(boolean locked) {
        int dirtyTick = Integer.MAX_VALUE;
        for (int idx : selection.getSelectedRows()) {
            if (idx < 0 || idx >= data.size()) continue;
            data.get(idx).setYawLocked(locked);
            if (idx < dirtyTick) dirtyTick = idx;
        }
        if (dirtyTick != Integer.MAX_VALUE) notifyChange(dirtyTick);
    }

    private void renderDuplicateOption() {
        if (selection.isEmpty()) return;

        ThemeManager.paddedSeparator();
        if (contextButton(MENU_DUPLICATE)) {
            duplicateSelectedRows();
        }
    }

    private void renderRowCountInput() {
        Controls.inputInt(LABEL_ROWS, rowsToAdd, ROW_COUNT_INPUT_WIDTH * uiScale());
        rowsToAdd.set(Math.max(1, Math.min(1000, rowsToAdd.get())));
    }

    private void renderAddRowOptions() {
        int count = rowsToAdd.get();

        if (contextButton(String.format(MENU_ADD_AT_END, count))) {
            int dirtyTick = data.size();
            data.addRows(data.size(), count);
            solverRowsInserted(dirtyTick, count);
            notifyChange(dirtyTick);
        }

        int selectedRow = selection.singleSelectedRow();
        if (selectedRow >= 0) {
            if (contextButton(String.format(MENU_ADD_ABOVE, count))) {
                data.addRows(selectedRow, count);
                solverRowsInserted(selectedRow, count);
                selection.adjustForInsert(selectedRow + 1, count);
                notifyChange(selectedRow);
            }

            if (contextButton(String.format(MENU_ADD_BELOW, count))) {
                data.addRows(selectedRow + 1, count);
                solverRowsInserted(selectedRow + 1, count);
                selection.adjustForInsert(selectedRow + 2, count);
                notifyChange(selectedRow + 1);
            }
        }
    }

    private void renderDeleteOption() {
        if (selection.isEmpty()) {
            return;
        }

        ThemeManager.paddedSeparator();
        String label = String.format(MENU_DELETE, selection.size()) + " (" + MENU_DELETE_SHORTCUT + ")";
        if (contextDangerButton(label)) {
            deleteSelectedRows();
        }
    }

    // menuItem closes the popup on click by itself; plain buttons don't, so these wrappers do.
    private boolean contextButton(String label) {
        boolean clicked = Controls.secondaryButton(label, ImGui.getContentRegionAvail().x);
        if (clicked) ImGui.closeCurrentPopup();
        return clicked;
    }

    private boolean contextDangerButton(String label) {
        boolean clicked = Controls.dangerButton(label, ImGui.getContentRegionAvail().x);
        if (clicked) ImGui.closeCurrentPopup();
        return clicked;
    }

    public void deleteSelectedRows() {
        Set<Integer> rows = selection.getSelectedRows();
        int dirtyTick = rows.isEmpty() ? -1 : java.util.Collections.min(rows);
        List<Integer> descending = selection.getSelectedRowsDescending();
        data.removeRows(descending);
        if (angleSolver != null) angleSolver.onRowsRemoved(descending);
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
        int selectedRow = selection.singleSelectedRow();
        if (selectedRow >= 0) {
            data.addRows(selectedRow, count);
            solverRowsInserted(selectedRow, count);
            selection.adjustForInsert(selectedRow + 1, count);
            notifyChange(selectedRow);
        } else {
            addRowsAtEnd(count);
        }
    }

    private static class DragDropState {
        int moveTo = -1;
        float dropLineY = -1;
        float rowMinX = 0;
        float rowMaxX = 0;
    }
}
