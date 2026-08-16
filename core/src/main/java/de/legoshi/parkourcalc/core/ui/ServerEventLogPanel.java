package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

public final class ServerEventLogPanel implements RenderInterface {

    private static final String WINDOW_ID = "###server-events";
    private static final String WINDOW_TITLE = "Server Events";
    private static final String TABLE_ID = "server-events-table";
    private static final String PLACEHOLDER_EMPTY = "No server events this run.";

    private static final String COL_TICK = "Tick";
    private static final String COL_EVENT = "Event";
    private static final String COL_DETAIL = "Detail";

    private final SimulationRunner runner;
    private final SelectionManager selection;

    public ServerEventLogPanel(SimulationRunner runner, SelectionManager selection) {
        this.runner = runner;
        this.selection = selection;
    }

    @Override
    public void render(ImGuiIO io) {
        float em = ImGui.getFontSize();
        ImGui.setNextWindowSize(em * 24f, em * 14f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(em * 14f, em * 5f, Float.MAX_VALUE, Float.MAX_VALUE);
        if (!ThemeManager.beginPanel(WINDOW_ID, WINDOW_TITLE, ImGuiWindowFlags.NoCollapse)) {
            return;
        }

        List<ServerSimEvent> events = runner.getServerEvents();
        if (events.isEmpty()) {
            ImGui.textDisabled(PLACEHOLDER_EMPTY);
            ImGui.end();
            return;
        }

        float tickW = ImGui.calcTextSize("T9999").x;
        float kindW = 0f;
        float detailW = ImGui.calcTextSize(COL_DETAIL).x;
        for (ServerSimEvent event : events) {
            kindW = Math.max(kindW, ImGui.calcTextSize(event.kind.label).x);
            detailW = Math.max(detailW, ImGui.calcTextSize(event.detail).x);
        }

        if (ThemeManager.beginStandardClickableRowsTable(TABLE_ID, 3, 0, 0f, 0f)) {
            ImGui.tableSetupScrollFreeze(0, 1);
            int fixed = ImGuiTableColumnFlags.WidthFixed;
            ImGui.tableSetupColumn(COL_TICK, fixed, ThemeManager.tableLeftmostColumnWidth(COL_TICK, tickW));
            ImGui.tableSetupColumn(COL_EVENT, fixed, ThemeManager.tableColumnWidth(COL_EVENT, kindW));
            ImGui.tableSetupColumn(COL_DETAIL, fixed,
                    ThemeManager.tableRightmostColumnWidth(COL_DETAIL, detailW, ThemeManager.tableScrollbarSlack()));
            renderHeader();

            float rowH = ThemeManager.tableRowHeight();
            for (int i = 0; i < events.size(); i++) {
                ServerSimEvent event = events.get(i);
                int pathIndex = event.tick + 1;
                boolean selected = selection.isSelected(pathIndex);
                ImGui.tableNextRow(0, rowH);
                ThemeManager.paintTableRowBg(i);
                ImGui.tableSetColumnIndex(0);
                ThemeManager.tableLeftmostCellPad();
                ImGui.alignTextToFramePadding();
                if (ImGui.selectable("T" + pathIndex + "##ev" + i, selected, ImGuiSelectableFlags.SpanAllColumns)) {
                    selection.selectOnly(pathIndex);
                    selection.requestScrollIntoView();
                }
                ImGui.tableSetColumnIndex(1);
                ImGui.alignTextToFramePadding();
                ImGui.text(event.kind.label);
                ImGui.tableSetColumnIndex(2);
                ImGui.alignTextToFramePadding();
                ImGui.text(event.detail);
            }
            ThemeManager.endStandardTable();
        }
        ImGui.end();
    }

    private void renderHeader() {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_TICK);
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader(COL_EVENT);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_DETAIL);
        ThemeManager.tableRightmostCellTrailingPad();
    }
}
