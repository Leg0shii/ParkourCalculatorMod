package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratFinder;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class StratFinderWidget {

    public interface SweepStarter {
        StratFinder start(long budgetMs);
    }

    private static final String COL_VARIANT = "Variant";
    private static final String COL_EDITS = "Edits";
    private static final String COL_SOLVE = "Solve";
    private static final int MAX_VISIBLE_ROWS = 10;
    private static final int BUDGET_MIN_MS = 250;
    private static final int BUDGET_MAX_MS = 10000;
    private static final int BUDGET_DEFAULT_MS = 2000;

    private static final String FIND_TIP =
            "Enumerate one-tick key-timing shifts of the loaded strat, no-turn shapes (nt: camera"
                    + " pinned through the last jump press; ja: one turn allowed at the jump angle;"
                    + " nt45: Force 45 under a pinned momentum camera) and canonical strafe patterns"
                    + " nt[momentum|air]. Each variant is re-solved against the placed constraints;"
                    + " click a row to preview it in the sim.";
    private static final String BUDGET_TIP =
            "Per-variant solve budget. Tight strats need 2000 ms to re-solve their own line;"
                    + " lower budgets mark them infeasible.";
    private static final String ORIGINAL_LABEL = "original";

    private final SweepStarter starter;
    private final Runnable onPrepareFind;
    private final Consumer<StratFinder.Item> onApply;
    private final Runnable onApplyOriginal;
    private final BooleanSupplier tempActive;
    private final Runnable onRestoreTemp;
    private final Runnable onKeepTemp;

    private StratFinder finder;
    private final int[] budgetBuf = {BUDGET_DEFAULT_MS};
    private String selectedLabel;
    private boolean startFailed;
    private final ImBoolean windowOpen = new ImBoolean(false);
    private boolean wasWindowOpen;

    public StratFinderWidget(SweepStarter starter, Runnable onPrepareFind,
                             Consumer<StratFinder.Item> onApply,
                             Runnable onApplyOriginal, BooleanSupplier tempActive,
                             Runnable onRestoreTemp, Runnable onKeepTemp) {
        this.starter = starter;
        this.onPrepareFind = onPrepareFind;
        this.onApply = onApply;
        this.onApplyOriginal = onApplyOriginal;
        this.tempActive = tempActive;
        this.onRestoreTemp = onRestoreTemp;
        this.onKeepTemp = onKeepTemp;
    }

    public void setWindowOpen(boolean open) {
        windowOpen.set(open);
    }

    public boolean isWindowOpen() {
        return windowOpen.get();
    }

    public void renderWindow(float scale) {
        boolean open = windowOpen.get();
        if (wasWindowOpen && !open && finder != null) {
            finder.cancel();
        }
        wasWindowOpen = open;
        if (!open) return;
        ImGui.setNextWindowPos(80f * scale, 120f * scale, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar;
        if (ImGui.begin("Strat Finder", windowOpen, flags)) {
            renderBody(scale);
        }
        ImGui.end();
    }

    private void renderBody(float scale) {
        float labelW = ImGui.calcTextSize("Budget").x + ThemeManager.SM * scale;
        budgetRow(labelW);
        actionRow();
        if (startFailed) {
            ImGui.textDisabled("Set up the solve segment first (start and goal tick).");
        }
        StratFinder f = finder;
        if (f == null) return;
        if (f.canaryFailed()) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.pushTextWrapPos(0f);
            ImGui.text("The original strat failed its own re-solve; raise the budget before trusting this list.");
            ImGui.popTextWrapPos();
            ThemeManager.popTextColor();
        }
        List<StratFinder.Item> ranked = f.ranked();
        if (ranked.isEmpty()) {
            if (!f.isRunning() && f.total() == 0) {
                ImGui.textDisabled("No variants: the segment needs a recorded jump with editable keys.");
            }
            return;
        }
        renderTable(ranked, scale);
        if (!f.isRunning()) {
            renderSweepSummary(ranked);
        }
        handleArrowKeys(ranked);
        renderTempControls();
    }

    private void budgetRow(float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Budget", labelW);
        ImGui.setNextItemWidth(120f * ThemeManager.uiScale());
        if (ImGui.sliderInt("##sfbudget", budgetBuf, BUDGET_MIN_MS, BUDGET_MAX_MS, "%d ms")) {
            budgetBuf[0] = Math.max(BUDGET_MIN_MS, Math.min(BUDGET_MAX_MS, budgetBuf[0]));
        }
        TooltipUtil.onHover(BUDGET_TIP);
        Controls.popInputFrameHeight();
    }

    private void actionRow() {
        boolean find = Controls.primaryButton(finder == null ? "Find strats" : "Re-find");
        TooltipUtil.onHover(FIND_TIP);
        if (find) requestSweep();
        StratFinder f = finder;
        if (f != null && f.isRunning()) {
            ImGui.sameLine();
            boolean cancel = Controls.secondaryButton("Cancel");
            TooltipUtil.onHover("Stop the sweep; found variants stay listed.");
            if (cancel) f.cancel();
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(progressText(f));
        }
    }

    private void requestSweep() {
        if (finder != null) finder.cancel();
        onPrepareFind.run();
        selectedLabel = null;
        StratFinder started = starter.start(budgetBuf[0]);
        startFailed = started == null;
        if (started != null) finder = started;
    }

    private String progressText(StratFinder f) {
        int total = f.total();
        if (total < 0) return "enumerating";
        return f.done() + " / " + total;
    }

    private void renderTable(List<StratFinder.Item> ranked, float scale) {
        float maxLabelW = ImGui.calcTextSize(COL_VARIANT).x;
        for (StratFinder.Item it : ranked) {
            maxLabelW = Math.max(maxLabelW, ImGui.calcTextSize(displayLabel(it)).x);
        }
        float maxSolveW = ImGui.calcTextSize(COL_SOLVE).x;
        for (StratFinder.Item it : ranked) {
            maxSolveW = Math.max(maxSolveW, ImGui.calcTextSize(solveText(it)).x);
        }
        float rowH = ThemeManager.tableRowHeight();
        float tableH = rowH * (Math.min(ranked.size(), MAX_VISIBLE_ROWS) + 1.6f);
        if (!ThemeManager.beginStandardClickableRowsTable("##sf_table", 3, 0, 0f, tableH)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        ImGui.tableSetupColumn(COL_VARIANT, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableLeftmostColumnWidth(COL_VARIANT, maxLabelW));
        ImGui.tableSetupColumn(COL_EDITS, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableColumnWidth(COL_EDITS, ImGui.calcTextSize(COL_EDITS).x));
        ImGui.tableSetupColumn(COL_SOLVE, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableRightmostColumnWidth(COL_SOLVE, maxSolveW, ThemeManager.tableScrollbarSlack()));
        renderHeader();

        int rowIndex = 0;
        for (StratFinder.Item it : ranked) {
            renderRow(it, rowIndex++, rowH);
        }
        ThemeManager.endStandardTable();
    }

    private void renderHeader() {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_VARIANT);
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader(COL_EDITS);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_SOLVE);
    }

    private void renderRow(StratFinder.Item it, int rowIndex, float rowH) {
        ImGui.tableNextRow(0, rowH);
        ThemeManager.paintTableRowBg(rowIndex);
        boolean selected = displayLabel(it).equals(selectedLabel);
        boolean applicable = it.original || it.feasible;
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        ImGui.alignTextToFramePadding();
        if (ImGui.selectable("##sf_row_" + it.label, selected, ImGuiSelectableFlags.SpanAllColumns) && applicable) {
            applyRow(it);
        }
        if (!applicable) TooltipUtil.onHover("No feasible solve within the budget.");
        else if (it.original) TooltipUtil.onHover("The loaded strat; click to restore it after previewing a variant.");
        float labelY = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
        int textColor = applicable ? ThemeManager.tableCellText(selected) : ThemeManager.textMutedColor();
        ImGui.getWindowDrawList().addText(cellOrigin.x, labelY, textColor, displayLabel(it));

        ImGui.tableSetColumnIndex(1);
        ImGui.alignTextToFramePadding();
        ImGui.text(Integer.toString(it.edits));
        ImGui.tableSetColumnIndex(2);
        ImGui.alignTextToFramePadding();
        ImGui.text(solveText(it));
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private void applyRow(StratFinder.Item it) {
        selectedLabel = displayLabel(it);
        if (it.original) {
            if (tempActive.getAsBoolean()) onApplyOriginal.run();
        } else {
            onApply.accept(it);
        }
    }

    private void handleArrowKeys(List<StratFinder.Item> ranked) {
        if (selectedLabel == null) return;
        if (!ImGui.isWindowFocused() || ImGui.getIO().getWantTextInput()) return;
        boolean down = ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.DownArrow), false);
        boolean up = ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.UpArrow), false);
        if (!down && !up) return;
        int current = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (displayLabel(ranked.get(i)).equals(selectedLabel)) {
                current = i;
                break;
            }
        }
        if (current < 0) return;
        int step = down ? 1 : -1;
        for (int i = current + step; i >= 0 && i < ranked.size(); i += step) {
            StratFinder.Item it = ranked.get(i);
            if (it.original || it.feasible) {
                applyRow(it);
                return;
            }
        }
    }

    private void renderSweepSummary(List<StratFinder.Item> ranked) {
        if (ranked.size() <= 1) {
            ImGui.pushTextWrapPos(0f);
            ImGui.textDisabled("Only the original: no W/SPRINT/A/D/S press or release to shift within 12"
                    + " ticks after a jump, and the facing shape is already pinned by dF constraints, so"
                    + " there is nothing to vary.");
            ImGui.popTextWrapPos();
            return;
        }
        int feasible = 0;
        for (StratFinder.Item it : ranked) {
            if (!it.original && it.feasible) feasible++;
        }
        ImGui.textDisabled(feasible + " of " + (ranked.size() - 1) + " variants feasible");
    }

    private void renderTempControls() {
        if (!tempActive.getAsBoolean()) return;
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.pushTextWrapPos(0f);
        ImGui.text("Variant applied as temp trajectory. Auto-save is paused.");
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();
        boolean reapply = Controls.secondaryButton("Reapply original");
        TooltipUtil.onHover("Discard the variant and restore the original strat.");
        if (reapply) {
            onRestoreTemp.run();
            selectedLabel = ORIGINAL_LABEL;
            return;
        }
        ImGui.sameLine();
        boolean keep = Controls.secondaryButton("Keep");
        TooltipUtil.onHover("Commit the applied variant as the new baseline; auto-save resumes.");
        if (keep) onKeepTemp.run();
    }

    private static String displayLabel(StratFinder.Item it) {
        return it.original ? ORIGINAL_LABEL : it.label;
    }

    private static String solveText(StratFinder.Item it) {
        if (!it.feasible) return "-";
        return String.format(Locale.ROOT, "%dms", it.elapsedMs);
    }
}
