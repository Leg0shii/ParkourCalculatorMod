package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratFinder;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratVariants;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class StratFinderWidget {

    public interface SweepStarter {
        StratFinder start(long budgetMs, StratVariants.Filter filter);
    }

    private static final String WINDOW_ID = "###strat_finder";
    private static final String TITLE = "Strat Finder";
    private static final String COL_VARIANT = "Variant";
    private static final String COL_EDITS = "Edits";
    private static final String COL_SOLVE = "Solve";
    private static final int BUDGET_MIN_MS = 250;
    private static final int BUDGET_MAX_MS = 10000;
    private static final int BUDGET_DEFAULT_MS = 2000;
    private static final float MIN_TABLE_ROWS = 3f;

    private static final String[] FORM_LABELS = {"Budget", "Shape", "Strats"};
    private static final String[] SHAPE_LABELS = {"any", "nt", "ja"};
    private static final String[] SHAPE_TIPS = {
            "Keep every camera shape.",
            "Only no-turn variants: camera pinned from the segment start through the last jump"
                    + " press (includes nt45 and the nt[momentum|air] strafe patterns).",
            "Only jump-angle variants: one turn allowed at the last jump press."};
    private static final String[] FAMILY_TIPS = {
            "Camera shapes and strafe patterns of the strat as loaded, no key-pattern family"
                    + " applied.",
            "One-tick shifts of existing W/SPRINT/A/D/S presses and releases within 12 ticks"
                    + " of each jump.",
            "Unsprinted momentum jump: fire on W+JUMP, SPRINT engages k ticks after the fire,"
                    + " mid-air.",
            "Fire on JUMP alone; W+SPRINT re-engage k ticks after the fire.",
            "d-tick W+SPRINT runway into the jump at the fire tick.",
            "Strafe-only fire (JUMP plus A or D); W+SPRINT re-engage k ticks after.",
            "Backwards-momentum arc into the fire, composed with jam, fmm or pessi timings."};
    private static final String SOLO_TIP = " Right-click: only this family.";

    private static final String FIND_TIP =
            "Enumerate variants of the loaded strat per the filters above: one-tick key-timing"
                    + " shifts, strat families (fmm, pessi, run+jam, mark, bwmm) applied at the"
                    + " existing jump ticks, no-turn shapes (nt, ja, nt45) and canonical strafe"
                    + " patterns nt[momentum|air]. Each variant is re-solved against the placed"
                    + " constraints; click a row to preview it in the sim.";
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
    private final boolean[] familyOn = new boolean[StratVariants.Filter.FAMILIES.size()];
    private int shapeSel;
    private String sweepKey;
    private boolean sweepFiltered;
    private long sweepStartNanos;
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
        Arrays.fill(familyOn, true);
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
        ImGui.setNextWindowSize(440f * scale, 460f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(360f * scale, 320f * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoScrollWithMouse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, windowOpen, flags);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) renderBody(scale);
        ImGui.end();
    }

    private void renderBody(float scale) {
        float labelW = labelColumnWidth(scale);
        float controlW = Math.max(SolverWidgets.segmentedMinWidth(SHAPE_LABELS), 160f * scale);
        budgetRow(labelW, controlW);
        shapeRow(labelW, controlW);
        familyRow(labelW);
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
        staleSweepNote(f);
        List<StratFinder.Item> ranked = f.ranked();
        if (ranked.isEmpty()) {
            if (!f.isRunning() && f.total() == 0) {
                ImGui.textDisabled("No variants: the segment needs a recorded jump with editable keys.");
            }
            return;
        }
        renderTable(ranked, scale);
        renderSweepSummary(f, ranked);
        handleArrowKeys(ranked);
        renderTempControls();
    }

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    private void budgetRow(float labelW, float controlW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Budget", labelW);
        ImGui.setNextItemWidth(controlW);
        if (ImGui.sliderInt("##sfbudget", budgetBuf, BUDGET_MIN_MS, BUDGET_MAX_MS, "%d ms")) {
            budgetBuf[0] = Math.max(BUDGET_MIN_MS, Math.min(BUDGET_MAX_MS, budgetBuf[0]));
        }
        TooltipUtil.onHover(BUDGET_TIP);
        Controls.popInputFrameHeight();
    }

    private void shapeRow(float labelW, float controlW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Shape", labelW);
        int clicked = SolverWidgets.segmented("##sfshape", SHAPE_LABELS, SHAPE_TIPS, shapeSel, controlW);
        Controls.popInputFrameHeight();
        if (clicked >= 0) shapeSel = clicked;
    }

    private void familyRow(float labelW) {
        List<String> families = StratVariants.Filter.FAMILIES;
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Strats", labelW);
        float startX = ImGui.getCursorPosX();
        float wrapX = startX + ImGui.getContentRegionAvail().x;
        for (int i = 0; i < families.size(); i++) {
            String label = families.get(i);
            if (i > 0) {
                ImGui.sameLine();
                float w = ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacing().x
                        + ImGui.calcTextSize(label).x;
                if (ImGui.getCursorPosX() + w > wrapX) {
                    ImGui.newLine();
                    ImGui.setCursorPosX(startX);
                }
            }
            if (Controls.checkbox(label, familyOn[i])) {
                familyOn[i] = !familyOn[i];
            }
            if (ImGui.isItemClicked(1)) {
                Arrays.fill(familyOn, false);
                familyOn[i] = true;
            }
            TooltipUtil.onHover(FAMILY_TIPS[i] + SOLO_TIP);
        }
        if (anyFamilyOff()) {
            ImGui.sameLine();
            if (ImGui.getCursorPosX() + ImGui.calcTextSize("all").x > wrapX) {
                ImGui.newLine();
                ImGui.setCursorPosX(startX);
            }
            ImGui.alignTextToFramePadding();
            if (Controls.hyperlink("all")) {
                Arrays.fill(familyOn, true);
            }
            TooltipUtil.onHover("Re-enable every strat family.");
        }
        Controls.popInputFrameHeight();
    }

    private boolean anyFamilyOff() {
        for (boolean on : familyOn) {
            if (!on) return true;
        }
        return false;
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
            float h = ImGui.getFrameHeight();
            ImVec2 p = ImGui.getCursorScreenPos();
            SolverWidgets.spinner(ImGui.getWindowDrawList(), p.x + h * 0.5f, p.y + h * 0.5f,
                    h * 0.30f, 1.8f * ThemeManager.uiScale(), ThemeManager.accentColor(),
                    (System.nanoTime() - sweepStartNanos) / 1e9);
            ImGui.dummy(h, h);
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(progressText(f));
        }
    }

    private void requestSweep() {
        if (finder != null) finder.cancel();
        onPrepareFind.run();
        selectedLabel = null;
        StratFinder started = starter.start(budgetBuf[0], buildFilter());
        startFailed = started == null;
        if (started != null) {
            finder = started;
            sweepKey = settingsKey();
            sweepFiltered = shapeSel != 0 || anyFamilyOff();
            sweepStartNanos = System.nanoTime();
        }
    }

    private StratVariants.Filter buildFilter() {
        Set<String> families = null;
        if (anyFamilyOff()) {
            families = new LinkedHashSet<String>();
            for (int i = 0; i < familyOn.length; i++) {
                if (familyOn[i]) families.add(StratVariants.Filter.FAMILIES.get(i));
            }
        }
        StratVariants.Filter.Shape shape = shapeSel == 1 ? StratVariants.Filter.Shape.NT
                : shapeSel == 2 ? StratVariants.Filter.Shape.JA
                : StratVariants.Filter.Shape.ANY;
        return new StratVariants.Filter(families, shape);
    }

    private String settingsKey() {
        StringBuilder sb = new StringBuilder();
        for (boolean on : familyOn) sb.append(on ? '1' : '0');
        return sb.append('|').append(shapeSel).append('|').append(budgetBuf[0]).toString();
    }

    private void staleSweepNote(StratFinder f) {
        if (f.isRunning() || sweepKey == null || sweepKey.equals(settingsKey())) return;
        ImGui.textDisabled("Filters or budget changed since this sweep; Re-find to apply.");
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
        float contentH = rowH * (ranked.size() + 1.6f);
        float availH = ImGui.getContentRegionAvail().y - footerHeight(scale);
        float tableH = Math.max(rowH * (MIN_TABLE_ROWS + 1.6f), Math.min(availH, contentH));
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

    private float footerHeight(float scale) {
        float line = ImGui.getTextLineHeightWithSpacing();
        float h = line;
        if (tempActive.getAsBoolean()) {
            h += 2f * line + ImGui.getFrameHeightWithSpacing();
        }
        return h + ThemeManager.XS * scale;
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

    private void renderSweepSummary(StratFinder f, List<StratFinder.Item> ranked) {
        if (f.isRunning()) return;
        if (ranked.size() <= 1) {
            ImGui.pushTextWrapPos(0f);
            if (sweepFiltered) {
                ImGui.textDisabled("Only the original: the active filters leave nothing to vary here.");
            } else {
                ImGui.textDisabled("Only the original: no W/SPRINT/A/D/S press or release to shift within 12"
                        + " ticks after a jump, and the facing shape is already pinned by dF constraints, so"
                        + " there is nothing to vary.");
            }
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
