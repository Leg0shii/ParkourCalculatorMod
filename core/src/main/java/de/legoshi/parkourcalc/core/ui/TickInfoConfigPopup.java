package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;

import java.util.List;

public final class TickInfoConfigPopup {

    private static final String POPUP_ID = "###tick-info-config";
    private static final String TABLE_ID = "tick-info-config-table";
    private static final String TITLE = "Tick Info stats";
    private static final String SUBTITLE = "Toggle, set decimals, and drag to reorder.";
    private static final String RESET_BTN = "Reset to defaults";
    private static final String GRIP = "::";
    private static final String DRAG_DROP_TYPE = "TICK_INFO_STAT";

    private static final String COL_STAT = "Stat";
    private static final String COL_ON = "On";
    private static final String COL_DECIMALS = "Decimals";

    private static final float DECIMALS_SLIDER_EMS = 9f;
    private static final String TT_TOGGLE = "Show or hide this stat in the Tick Info panel.";
    private static final String TT_DECIMALS = "Decimal places used for this stat's numeric value(s).";
    private static final String TT_GRIP = "Drag to reorder.";

    private final Settings settings;
    private final int[] decimalsBuf = new int[1];

    private int draggingIndex = -1;
    private int dropTarget = -1;
    private float dropLineY = -1f;
    private float rowMinX, rowMaxX;
    private boolean openRequested;

    public TickInfoConfigPopup(Settings settings) {
        this.settings = settings;
    }

    public void handleOpenOnRightClick(int hoveredFlags) {
        if (ImGui.isMouseReleased(1) && ImGui.isWindowHovered(hoveredFlags)) {
            openRequested = true;
        }
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        if (!ImGui.beginPopup(POPUP_ID)) {
            draggingIndex = -1;
            return;
        }

        ImGui.textDisabled(SUBTITLE);
        ThemeManager.paddedSeparator();

        renderStatTable();

        ThemeManager.paddedSeparator();
        if (Controls.secondaryButton(RESET_BTN)) {
            settings.tickInfoStats = TickInfoConfig.defaultConfig(Settings.defaultTickInfoPrecision());
            draggingIndex = -1;
        }

        ImGui.endPopup();
    }

    private void renderStatTable() {
        List<TickInfoStatSetting> stats = settings.tickInfoStats != null ? settings.tickInfoStats.stats : null;
        if (stats == null || stats.isEmpty()) {
            ImGui.text(TITLE + ": none");
            return;
        }

        if (!ThemeManager.beginStandardKeyValueTable(TABLE_ID, 3, 0, 0f, 0f)) {
            return;
        }
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float cellPad = ImGui.getStyle().getCellPadding().x;
        float labelW = ImGui.calcTextSize(GRIP + "  Collision angle (deg)").x + 2f * cellPad;
        float onW = ImGui.calcTextSize(COL_ON).x + 2f * cellPad + ImGui.getFrameHeight();
        float decW = ImGui.getFontSize() * DECIMALS_SLIDER_EMS;
        ImGui.tableSetupColumn(COL_STAT, fixed, ThemeManager.tableLeftmostColumnWidth(COL_STAT, labelW));
        ImGui.tableSetupColumn(COL_ON, fixed, ThemeManager.tableColumnWidth(COL_ON, onW));
        ImGui.tableSetupColumn(COL_DECIMALS, fixed,
                ThemeManager.tableRightmostColumnWidth(COL_DECIMALS, decW, ThemeManager.tableFixedScrollbarSlack()));

        dropLineY = -1f;
        for (int i = 0; i < stats.size(); i++) {
            renderStatRow(i, stats.get(i));
        }

        drawDropIndicator();
        applyDragDrop(stats.size());

        ThemeManager.endStandardTable();
    }

    private void renderStatRow(int index, TickInfoStatSetting setting) {
        TickInfoStat stat = TickInfoStat.byId(setting.id);
        String label = stat != null ? stat.label() : setting.id;
        String tooltip = stat != null ? stat.tooltip() : "";

        ImGui.tableNextRow();
        ThemeManager.paintTableRowBg(index);
        if (draggingIndex == index) {
            ThemeManager.paintTableRowTint(ThemeManager.selectedTintColor(0.5f));
        }

        ImGui.tableNextColumn();
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.pushTextColor(draggingIndex == index ? ThemeManager.textColor() : ThemeManager.textMutedColor());
        ThemeManager.rightAlignedSelectable(
                "stat" + index, GRIP + "  " + label, draggingIndex == index,
                ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowItemOverlap);
        ThemeManager.popTextColor();
        TooltipUtil.onHover(tooltip.isEmpty() ? TT_GRIP : tooltip);
        if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        handleRowDragDrop(index);

        ImGui.tableNextColumn();
        if (Controls.checkbox("##on" + index, setting.enabled)) {
            setting.enabled = !setting.enabled;
        }
        TooltipUtil.onHover(TT_TOGGLE);

        ImGui.tableNextColumn();
        boolean numeric = usesDecimals(stat);
        if (!numeric) ImGui.beginDisabled(true);
        decimalsBuf[0] = setting.decimals;
        ImGui.setNextItemWidth(-1);
        if (Controls.sliderInt("##dec" + index, decimalsBuf,
                Settings.MIN_STAT_PRECISION, Settings.MAX_STAT_PRECISION, "%d") && numeric) {
            setting.decimals = decimalsBuf[0];
        }
        if (!numeric) ImGui.endDisabled();
        TooltipUtil.onHover(TT_DECIMALS);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void handleRowDragDrop(int index) {
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.SourceNoPreviewTooltip)) {
            draggingIndex = index;
            // Payload is never read back (imgui-java weak-refs it); the move is resolved from draggingIndex.
            ImGui.setDragDropPayload(DRAG_DROP_TYPE, Integer.valueOf(index));
            ImGui.endDragDropSource();
        }
        if (draggingIndex == -1) return;

        ImVec2 rowMin = ImGui.getItemRectMin();
        ImVec2 rowMax = ImGui.getItemRectMax();
        rowMinX = rowMin.x;
        rowMaxX = ImGui.getWindowPos().x + ImGui.getWindowWidth();

        ImVec2 mouse = ImGui.getMousePos();
        float gap = ImGui.getStyle().getCellPadding().y;
        if (mouse.y < rowMin.y - gap - 1f || mouse.y >= rowMax.y + gap + 1f) return;

        boolean insertAbove = mouse.y < (rowMin.y + rowMax.y) / 2f;
        dropLineY = insertAbove ? rowMin.y - gap : rowMax.y + gap;
        dropTarget = insertAbove ? index : index + 1;
    }

    private void drawDropIndicator() {
        if (draggingIndex == -1 || dropLineY <= 0f) return;
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addLine(rowMinX, dropLineY, rowMaxX, dropLineY, ThemeManager.warningColor(), 2.0f);
    }

    private void applyDragDrop(int size) {
        if (draggingIndex == -1) return;
        if (ImGui.isMouseReleased(0)) {
            int from = draggingIndex;
            int to = dropTarget;
            draggingIndex = -1;
            dropTarget = -1;
            if (to >= 0 && to <= size && settings.tickInfoStats != null) {
                settings.tickInfoStats.move(from, to);
            }
        } else if (!ImGui.isMouseDown(0)) {
            draggingIndex = -1;
            dropTarget = -1;
        }
    }

    private static boolean usesDecimals(TickInfoStat stat) {
        if (stat == null) return false;
        return stat.kind() == TickInfoStat.Kind.NUM
                || stat.kind() == TickInfoStat.Kind.TRIPLE
                || stat.kind() == TickInfoStat.Kind.XZ;
    }
}
