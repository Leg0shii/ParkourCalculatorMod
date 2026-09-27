package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnRanking;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Locale;

public final class StratfinderWindow implements RenderInterface {

    public enum Phase { IDLE, SEARCH, OPTIMIZE }

    public enum Ending { NONE, FOUND, CANCELLED, EMPTY }

    public interface Host {
        boolean isBusy();

        Phase phase();

        Ending ending();

        String stage();

        double fraction();

        double elapsedSeconds();

        String outcome();

        List<NoTurnResult> results();

        NoTurnResult selected();

        int startTick();

        NoTurnRanking.Mode rankMode();

        void setRankMode(NoTurnRanking.Mode mode);

        int budgetSeconds();

        void setBudgetSeconds(int seconds);

        int unoptimizedCount();

        boolean playable();

        void setPlayable(boolean value);

        double offsetOf(NoTurnResult result);

        String goalWallLabel();

        void start();

        void optimize();

        void cancel();

        void select(NoTurnResult result);
    }

    private static final String WINDOW_ID = "###stratfinder";
    private static final String TITLE = "Stratfinder";
    private static final String SUBTITLE = "no-turn lines";
    private static final String TABLE_ID = "##stratfinder_lines";
    private static final String COL_RANK = "#";
    private static final String COL_INPUTS = "Inputs";
    private static final String COL_SPRINT = "Sprint";
    private static final String COL_OFFSET = "Offset";
    private static final String COL_KEYS = "Keys";
    private static final String SEARCH_TIP = "Cold search over key schedules for a byte-exact no-turn line: one"
            + " settable facing across the run-up, then the turn. Mark the no-turn ticks with dF = 0, set the landing"
            + " constraints, the jump rows and a free-start box. Lines appear while the search runs; click one to"
            + " apply it.";
    private static final String OPTIMIZE_TIP = "Optimize every line that has not been optimized yet for the budget."
            + " During a search this stops the search first and optimizes the lines found so far. The selected line"
            + " is re-applied when it improves.";
    private static final String BUDGET_TIP = "Time each line is optimized for. Lines run in parallel, the list"
            + " re-sorts as they finish, and lines already optimized are skipped.";
    private static final String CANCEL_TIP = "Stop now. Everything found so far stays in the list.";
    private static final String HUMAN_YAWS_TIP = "Solve the yaws for a human: aim for clearance instead of the last"
            + " fraction of distance, then straighten the turn so it never flicks out and back. Lines stay byte-exact and"
            + " still land; results with fewer turn reversals rank first. Off: hug the objective like the angle solver.";
    private static final String INPUTS_TIP = "Input changes a player makes: key presses and releases across the"
            + " run-up (including the first press and the air hold) plus sprint toggles. A key change on a jump"
            + " tick is free: space is the cue to release the strafe (WAD). Lower is easier.\n\nClick to sort easiest"
            + " first: no jump-angle flick before one, then the fewest input changes, then the fewest backward ticks,"
            + " then the smallest turn. Offset breaks ties.";
    private static final String OFFSET_TIP = "Landing offset past the goal wall on the objective axis, in blocks."
            + " Bright once the line has been optimized for the budget; muted while it is only certified.\n\nClick"
            + " to sort furthest first; ties fall back to the easiest order.";
    private static final float WIN_W = 640f;
    private static final float WIN_H = 540f;
    private static final float MIN_W = 480f;
    private static final float MIN_H = 420f;
    private static final int MAX_FACING_SEGMENTS = 4;

    private final Host host;
    private final int[] budgetBuf = new int[1];
    private boolean open;

    public StratfinderWindow(Host host) {
        this.host = host;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!open) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(io.getDisplaySizeX() * 0.5f - WIN_W * 0.5f * scale, 120f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, flags);
        if (visible) drawTitleBar(scale);
        ThemeManager.popHeaderChrome();
        if (visible) renderBody();
        ImGui.end();
    }

    private void drawTitleBar(float scale) {
        ThemeManager.drawModalTitle(TITLE);
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        float titleH = ImGui.getFrameHeight();
        float fy = wp.y + (titleH - ImGui.getFontSize()) * 0.5f;
        Fonts.pushBold();
        float tw = ImGui.calcTextSize(TITLE).x;
        Fonts.popBold();
        dl.addText(wp.x + ThemeManager.headerTextPadX() + tw + 10f * scale, fy, ThemeManager.textDimColor(), SUBTITLE);
    }

    private void renderBody() {
        renderLines();
        renderSelected();
        ThemeManager.sectionSpacing();
        renderProgress();
        renderActions();
    }

    private void renderActions() {
        boolean busy = host.isBusy();
        if (busy) {
            if (Controls.secondaryButton("Cancel")) host.cancel();
            TooltipUtil.onHover(CANCEL_TIP);
        } else {
            if (Controls.primaryButton("Search")) host.start();
            TooltipUtil.onHover(SEARCH_TIP);
        }
        ImGui.sameLine();
        if (busy) ImGui.beginDisabled(true);
        if (Controls.checkbox("Human yaws", host.playable())) host.setPlayable(!host.playable());
        if (busy) ImGui.endDisabled();
        TooltipUtil.onHover(HUMAN_YAWS_TIP);
        ImGui.sameLine();
        boolean any = !host.results().isEmpty();
        int todo = host.unoptimizedCount();
        boolean canOptimize = todo > 0 && (!busy || host.phase() == Phase.SEARCH);
        if (!canOptimize) ImGui.beginDisabled(true);
        if (Controls.secondaryButton(todo > 0 ? "Optimize " + todo : "Optimize")) host.optimize();
        if (!canOptimize) ImGui.endDisabled();
        TooltipUtil.onHover(OPTIMIZE_TIP);
        ImGui.sameLine();
        float sliderW = ImGui.getContentRegionAvail().x - Controls.buttonWidth("Close")
                - ImGui.getStyle().getItemSpacingX();
        boolean lockBudget = !any || (busy && host.phase() == Phase.OPTIMIZE);
        budgetBuf[0] = host.budgetSeconds();
        Controls.pushInputFrameHeight();
        ImGui.setNextItemWidth(sliderW);
        if (lockBudget) ImGui.beginDisabled(true);
        boolean changed = Controls.sliderInt("##strat_budget", budgetBuf, 1, 120, "%d s per line");
        if (lockBudget) ImGui.endDisabled();
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(BUDGET_TIP);
        if (changed) host.setBudgetSeconds(budgetBuf[0]);
        ImGui.sameLine();
        if (Controls.secondaryButton("Close")) open = false;
    }

    private void renderProgress() {
        boolean busy = host.isBusy();
        float frac = (float) host.fraction();
        String label;
        int fill;
        if (busy) {
            label = String.format(Locale.ROOT, "%d%%   %.1f s", Math.round(frac * 100f), host.elapsedSeconds());
            fill = ThemeManager.accentTintColor(0.35f);
        } else {
            Ending ending = host.ending();
            String outcome = host.outcome();
            if (ending == Ending.NONE) {
                label = outcome == null || outcome.isEmpty() ? "" : outcome;
                frac = 0f;
                fill = 0;
            } else {
                double elapsed = host.elapsedSeconds();
                label = outcome + (elapsed > 0.0 ? String.format(Locale.ROOT, " after %.1f s", elapsed) : "");
                frac = 1f;
                fill = ending == Ending.FOUND ? ThemeManager.okTintColor(0.30f)
                        : ending == Ending.CANCELLED ? ThemeManager.warningTintColor(0.30f)
                        : ThemeManager.dangerTintColor(0.30f);
            }
        }
        SolverWidgets.progressStrip("##strat_progress", frac, fill, label);
        String stage = host.stage();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.textWrapped(busy && stage != null && !stage.isEmpty() ? stage : " ");
        ThemeManager.popTextColor();
    }

    private void renderLines() {
        List<NoTurnResult> results = host.results();
        NoTurnResult selected = host.selected();
        boolean busy = host.isBusy();
        Fonts.pushBold();
        ImGui.text("Lines");
        Fonts.popBold();
        ImGui.sameLine();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(countText(results.size(), busy));
        ThemeManager.popTextColor();

        float reserve = selectedHeight(selected != null) + footerHeight();
        float tableH = Math.max(ImGui.getTextLineHeightWithSpacing() * 4f, ImGui.getContentRegionAvail().y - reserve);
        if (!ThemeManager.beginStandardClickableRowsTable(TABLE_ID, 5, 0, 0f, tableH)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float rankW = ImGui.calcTextSize("999").x;
        float numW = ImGui.calcTextSize("99").x;
        float tickW = ImGui.calcTextSize("T999").x;
        float offW = ImGui.calcTextSize("+" + ConstraintText.fixedStat(99.0)).x;
        float mark = ImGui.getFontSize() * 0.7f;
        ImGui.tableSetupColumn(COL_RANK, fixed, ThemeManager.tableLeftmostColumnWidth(COL_RANK, rankW));
        ImGui.tableSetupColumn(COL_INPUTS, fixed, ThemeManager.tableNumericColumnWidth(COL_INPUTS, numW) + mark);
        ImGui.tableSetupColumn(COL_SPRINT, fixed, ThemeManager.tableColumnWidth(COL_SPRINT, tickW));
        ImGui.tableSetupColumn(COL_OFFSET, fixed, ThemeManager.tableNumericColumnWidth(COL_OFFSET, offW) + mark);
        ImGui.tableSetupColumn(COL_KEYS, ImGuiTableColumnFlags.WidthStretch, 0f);
        renderHeader();
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
            if (ImGui.isItemHovered()) TooltipUtil.wrappedTooltip(rowTip(r));
            ImGui.tableSetColumnIndex(1);
            ThemeManager.textRight(Integer.toString(NoTurnRanking.inputChanges(r)));
            ImGui.tableSetColumnIndex(2);
            ThemeManager.textLeft(r.sprintEngage >= 0 ? "T" + (startTick + r.sprintEngage + 1) : "-");
            ImGui.tableSetColumnIndex(3);
            ThemeManager.pushTextColor(r.optimized ? ThemeManager.okColor() : ThemeManager.textMutedColor());
            ThemeManager.textRight(offsetText(host.offsetOf(r)));
            ThemeManager.popTextColor();
            ImGui.tableSetColumnIndex(4);
            ThemeManager.textLeft(NoTurnKeys.describe(r.combos) + (r.warm ? "  (current)" : ""));
        }
        ThemeManager.endStandardTable();
    }

    private void renderHeader() {
        NoTurnRanking.Mode mode = host.rankMode();
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_RANK);
        ImGui.tableSetColumnIndex(1);
        if (ThemeManager.tableSortHeader(COL_INPUTS, ThemeManager.HAlign.RIGHT, mode == NoTurnRanking.Mode.EASIEST)) {
            host.setRankMode(NoTurnRanking.Mode.EASIEST);
        }
        TooltipUtil.onHover(INPUTS_TIP);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_SPRINT);
        TooltipUtil.onHover("Tick the sprint key is first pressed.");
        ImGui.tableSetColumnIndex(3);
        if (ThemeManager.tableSortHeader(COL_OFFSET, ThemeManager.HAlign.RIGHT, mode == NoTurnRanking.Mode.FURTHEST)) {
            host.setRankMode(NoTurnRanking.Mode.FURTHEST);
        }
        TooltipUtil.onHover(OFFSET_TIP);
        ImGui.tableSetColumnIndex(4);
        ThemeManager.tableHeader(COL_KEYS);
        TooltipUtil.onHover("Run-up key schedule, one token per held combination; x N is the tick count.");
    }

    private void renderSelected() {
        NoTurnResult r = host.selected();
        if (r == null) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.alignTextToFramePadding();
            ImGui.text(host.results().isEmpty() ? "Lines you can apply appear here." : "Click a line to apply and view it.");
            ThemeManager.popTextColor();
            return;
        }
        int startTick = host.startTick();
        String wall = host.goalWallLabel();
        String offset = offsetText(host.offsetOf(r)) + (wall != null ? "  vs " + wall : "");
        String inputs = NoTurnRanking.inputChanges(r) + "  (" + NoTurnRanking.keyChanges(r) + " key, "
                + NoTurnRanking.sprintToggles(r) + " sprint)";
        String start = "X " + ConstraintText.fixedStat(r.startX) + "   Z " + ConstraintText.fixedStat(r.startZ);
        if (ThemeManager.beginStandardFormTable("##strat_selected", 2)) {
            ImGui.tableSetupColumn("##strat_detail_label", ImGuiTableColumnFlags.WidthFixed, 0f);
            ImGui.tableSetupColumn("##strat_detail_value", ImGuiTableColumnFlags.WidthStretch, 0f);
            detailRow("Facing", facingSummary(r.yaws, startTick));
            detailRow("Start", start);
            detailRow("Offset", offset);
            detailRow("Inputs", inputs);
            ThemeManager.endStandardFormTable();
        }
    }

    private static void detailRow(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(label);
        ThemeManager.popTextColor();
        ImGui.tableNextColumn();
        ImGui.textWrapped(value);
    }

    private static float selectedHeight(boolean hasSelection) {
        float spacing = ImGui.getStyle().getItemSpacingY();
        if (!hasSelection) return ImGui.getFrameHeight() + spacing;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        return 5f * (ImGui.getTextLineHeightWithSpacing() + 2f * cellPadY) + spacing;
    }

    private static float footerHeight() {
        float spacing = ImGui.getStyle().getItemSpacingY();
        return ThemeManager.sectionSpacingHeight() + spacing
                + ImGui.getFrameHeight() + spacing
                + ImGui.getTextLineHeight() + spacing
                + Controls.buttonHeight() + spacing;
    }

    private static String countText(int count, boolean busy) {
        if (count == 0) return busy ? "none yet" : "none";
        return count + (busy ? " so far" : "");
    }

    private static String offsetText(double offset) {
        if (Double.isNaN(offset)) return "-";
        return (offset >= 0 ? "+" : "") + ConstraintText.fixedStat(offset);
    }

    private static String rowTip(NoTurnResult r) {
        String origin = r.warm ? "Certified from the current inputs." : "Cold search result.";
        String state = r.optimized ? " Optimized for the budget." : " Certified only; not optimized yet.";
        return origin + state + " Click to apply and view it.";
    }

    private static String facingSummary(double[] yaws, int startTick) {
        if (yaws == null || yaws.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        int segments = 0;
        int t = 0;
        while (t < yaws.length) {
            int run = t;
            while (run + 1 < yaws.length && Math.abs(yaws[run + 1] - yaws[t]) < 1.0e-9) run++;
            if (segments == MAX_FACING_SEGMENTS) {
                int more = 0;
                for (int u = t; u < yaws.length; u++) if (u == t || Math.abs(yaws[u] - yaws[u - 1]) >= 1.0e-9) more++;
                sb.append("   +").append(more).append(" more");
                break;
            }
            if (sb.length() > 0) sb.append("   ");
            int from = startTick + t + 1;
            int to = startTick + run + 1;
            sb.append('T').append(from);
            if (to != from) sb.append('-').append(to);
            sb.append(' ').append(ConstraintText.fixedYaw(yaws[t]).trim()).append('°');
            segments++;
            t = run + 1;
        }
        return sb.toString();
    }
}
