package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

public final class StratfinderWindow implements RenderInterface {

    public interface Host {
        boolean isSearching();

        String stage();

        double fraction();

        double elapsedSeconds();

        String outcome();

        List<NoTurnResult> results();

        NoTurnResult selected();

        int startTick();

        boolean playable();

        void setPlayable(boolean value);

        void start();

        void cancel();

        void select(NoTurnResult result);
    }

    private static final String WINDOW_ID = "###stratfinder";
    private static final String TITLE = "Stratfinder";
    private static final String TABLE_ID = "##stratfinder_results";
    private static final String COL_RANK = "#";
    private static final String COL_EDGES = "Presses";
    private static final String COL_SPRINT = "Sprint";
    private static final String COL_JA = "JA";
    private static final String COL_OBJ = "Objective";
    private static final String COL_KEYS = "Keys";
    private static final String SEARCH_TIP = "Search key schedules (cold) for a byte-exact NO-TURN: a single settable facing across the run-up, then the turn. Mark the no-turn ticks with dF = 0, set the landing constraints, the jump rows, and a free-start box.";
    private static final String HUMAN_YAWS_TIP = "Solve the air yaws for a human: aim for clearance instead of the last"
            + " fraction of distance, then straighten the turn so it never flicks out and back. Lines stay byte-exact and"
            + " still land; results with fewer turn reversals rank first. Off: hug the objective like the angle solver.";

    private final Host host;
    private boolean open;

    public StratfinderWindow(Host host) {
        this.host = host;
    }

    public void open() {
        open = true;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!open) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(600f * scale, 400f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(io.getDisplaySizeX() * 0.5f - 300f * scale, 120f, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, flags);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) renderBody(scale);
        ImGui.end();
    }

    private void renderBody(float scale) {
        boolean searching = host.isSearching();
        if (searching) {
            if (Controls.dangerButton("Cancel")) host.cancel();
            TooltipUtil.onHover("Stop the search. Everything found so far stays in the list.");
        } else {
            if (Controls.primaryButton("Search")) host.start();
            TooltipUtil.onHover(SEARCH_TIP);
        }
        ImGui.sameLine();
        if (Controls.checkbox("Human yaws", host.playable())) host.setPlayable(!host.playable());
        TooltipUtil.onHover(HUMAN_YAWS_TIP);
        ImGui.sameLine();
        Controls.cursorToRightAlignedButton("Close");
        if (Controls.secondaryButton("Close")) open = false;

        renderProgress(searching);
        renderResults(scale);
        renderDetails();
    }

    private void renderProgress(boolean searching) {
        float frac = (float) Math.max(0.0, Math.min(1.0, host.fraction()));
        String elapsed = String.format(Locale.ROOT, "%.1f s", host.elapsedSeconds());
        String overlay;
        if (searching) {
            overlay = Math.round(frac * 100f) + "%  " + elapsed;
        } else {
            String outcome = host.outcome();
            overlay = outcome == null || outcome.isEmpty() ? "idle" : outcome + "  " + elapsed;
        }
        ImGui.progressBar(frac, -1f, 0f, overlay);
        String stage = host.stage();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.textWrapped(searching || (stage != null && !stage.isEmpty()) ? stage : " ");
        ThemeManager.popTextColor();
    }

    private void renderResults(float scale) {
        List<NoTurnResult> results = host.results();
        NoTurnResult selected = host.selected();
        ImGui.text(results.size() + " found" + (host.isSearching() ? " so far" : ""));
        float reserve = ImGui.getTextLineHeightWithSpacing() * 3f + ImGui.getStyle().getItemSpacingY();
        float tableH = Math.max(ImGui.getTextLineHeightWithSpacing() * 4f, ImGui.getContentRegionAvail().y - reserve);
        if (!ThemeManager.beginStandardClickableRowsTable(TABLE_ID, 6, 0, 0f, tableH)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float numW = ImGui.calcTextSize("9999").x;
        float objW = ImGui.calcTextSize("-99999.999999").x;
        ImGui.tableSetupColumn(COL_RANK, fixed, ThemeManager.tableLeftmostColumnWidth(COL_RANK, numW));
        ImGui.tableSetupColumn(COL_EDGES, fixed, ThemeManager.tableColumnWidth(COL_EDGES, numW));
        ImGui.tableSetupColumn(COL_SPRINT, fixed, ThemeManager.tableColumnWidth(COL_SPRINT, numW));
        ImGui.tableSetupColumn(COL_JA, fixed, ThemeManager.tableColumnWidth(COL_JA, numW));
        ImGui.tableSetupColumn(COL_OBJ, fixed, ThemeManager.tableColumnWidth(COL_OBJ, objW));
        ImGui.tableSetupColumn(COL_KEYS, ImGuiTableColumnFlags.WidthStretch, 0f);
        ImGui.tableHeadersRow();
        float rowH = ThemeManager.tableRowHeight();
        int startTick = host.startTick();
        for (int i = 0; i < results.size(); i++) {
            NoTurnResult r = results.get(i);
            boolean isSelected = r == selected;
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(i);
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            if (ImGui.selectable((i + 1) + "##strat" + i, isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                host.select(r);
            }
            TooltipUtil.onHover(r.warm ? "Certified from the current inputs." : "Cold search result. Click to apply and view it.");
            ImGui.tableSetColumnIndex(1);
            ImGui.alignTextToFramePadding();
            ImGui.text(Integer.toString(r.pressCount >= 0 ? r.pressCount : NoTurnKeys.countPresses(r.combos)));
            ImGui.tableSetColumnIndex(2);
            ImGui.alignTextToFramePadding();
            ImGui.text(r.sprintEngage >= 0 ? "T" + (startTick + r.sprintEngage + 1) : "-");
            ImGui.tableSetColumnIndex(3);
            ImGui.alignTextToFramePadding();
            ImGui.text(r.ja ? "yes" : "no");
            ImGui.tableSetColumnIndex(4);
            ImGui.alignTextToFramePadding();
            ImGui.text(String.format(Locale.ROOT, "%.6f", r.objective));
            ImGui.tableSetColumnIndex(5);
            ImGui.alignTextToFramePadding();
            ImGui.text(NoTurnKeys.describe(r.combos) + (r.warm ? " (current)" : ""));
        }
        ThemeManager.endStandardTable();
    }

    private void renderDetails() {
        NoTurnResult r = host.selected();
        if (r == null) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.textWrapped(host.results().isEmpty() ? "Select a result to apply and view it." : "Click a row to apply and view it.");
            ThemeManager.popTextColor();
            return;
        }
        ImGui.textWrapped("Facing " + facingSummary(r.yaws, host.startTick()));
        ImGui.textWrapped(String.format(Locale.ROOT, "Start X %.6f  Z %.6f  |  violation %.2e", r.startX, r.startZ, r.violation));
    }

    private static String facingSummary(double[] yaws, int startTick) {
        if (yaws == null || yaws.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        int t = 0;
        while (t < yaws.length) {
            int run = t;
            while (run + 1 < yaws.length && Math.abs(yaws[run + 1] - yaws[t]) < 1.0e-9) run++;
            if (sb.length() > 0) sb.append("  ·  ");
            int from = startTick + t + 1;
            int to = startTick + run + 1;
            sb.append('T').append(from);
            if (to != from) sb.append('-').append(to);
            sb.append(' ').append(String.format(Locale.ROOT, "%.4f", yaws[t]));
            t = run + 1;
        }
        return sb.toString();
    }
}
