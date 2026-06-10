package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.Potion;
import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The floating Angle Solver window: whole-problem inputs (start / goal tick, axis, goal),
 * the default per-tick state, the Solve / Apply actions, and the result panel.
 * Toggled from View > Angle Solver; the title-bar close acts the same as unchecking it.
 */
public final class AngleSolverWindow implements RenderInterface {

    private static final String WINDOW_ID = "###angle_solver";
    private static final String TITLE = "Angle Solver";
    private static final String APPLY_POPUP_ID = "###angle_solver_apply";

    private static final String[] AXES = {"X", "Z"};
    private static final String[] GOALS = {"MAX", "MIN"};
    private static final String[] INPUTS = {"Keep", "Force 45"};
    private static final String[] EFFORTS = {"Fast", "Balanced", "Thorough"};

    private static final String[] FORM_LABELS =
            {"Start tick", "Goal tick", "Axis", "Goal", "Inputs", "Slipperiness", "Potion"};

    private final AngleSolverState state;
    private final Settings settings;
    private final IntSupplier rowCountSupplier;
    private final AngleSolverEngine engine;
    private final ImInt startTickBuf = new ImInt();
    private final ImInt goalTickBuf = new ImInt();
    private final ImInt slipBuf = new ImInt();
    private final ImInt doseCombo = new ImInt();
    private final ImInt levelBuf = new ImInt();
    private final String[] slipItems;

    private boolean yawsExpanded;
    private boolean advancedExpanded;
    private int doseToRemove;

    public AngleSolverWindow(AngleSolverState state, Settings settings,
                             IntSupplier rowCountSupplier, AngleSolverEngine engine) {
        this.state = state;
        this.settings = settings;
        this.rowCountSupplier = rowCountSupplier;
        this.engine = engine;
        this.slipItems = Slipperiness.comboItems();
    }

    @Override
    public void render(ImGuiIO io) {
        if (!settings.viewAngleSolver) return;
        boolean wasSolving = engine.isSolving();
        engine.poll(); // publish a finished background solve on the main thread
        if (wasSolving && !engine.isSolving() && settings.autoApplySolve) {
            SolveResult done = state.getResult();
            if (done != null && done.isSuccess() && !done.getYaws().isEmpty()) engine.apply();
        }
        int rowCount = Math.max(1, rowCountSupplier.getAsInt());
        state.clampTicks(rowCount);

        float scale = ThemeManager.uiScale();
        float w = windowWidth(state.getResult(), scale);
        float px = Math.max(40f, io.getDisplaySizeX() - w - 40f);
        ImGui.setNextWindowPos(px, 90f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(w, 0f, w, Float.MAX_VALUE);

        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar;

        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, flags);
        if (visible) drawTitleBar(scale);
        ThemeManager.popHeaderChrome();
        if (visible) renderBody(io, rowCount, scale);
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
        dl.addText(wp.x + ThemeManager.headerTextPadX() + tw + 10f * scale, fy,
                ThemeManager.textDimColor(), "drag to move");
    }

    private void renderBody(ImGuiIO io, int rowCount, float scale) {
        float labelW = labelColumnWidth(scale);

        sectionHeader("Problem");
        tickRow("Start tick", true, rowCount, labelW);
        tickRow("Goal tick", false, rowCount, labelW);

        ThemeManager.sectionSpacing();

        sectionHeader("Solve for");
        int ax = segmentedRow("Axis", "axis", AXES, state.getAxis().ordinal(), labelW);
        if (ax >= 0) state.setAxis(AngleSolverState.Axis.values()[ax]);
        int gl = segmentedRow("Goal", "goal", GOALS, state.getGoal().ordinal(), labelW);
        if (gl >= 0) state.setGoal(AngleSolverState.Goal.values()[gl]);

        ThemeManager.sectionSpacing();

        sectionHeader("Default state");
        int im = segmentedRow("Inputs", "inputs", INPUTS, state.getDefaultInputs().ordinal(), labelW);
        if (im >= 0) state.setDefaultInputs(AngleSolverState.InputMode.values()[im]);

        slipperinessRow(labelW);
        potionRow(labelW);
        state.pruneRedundantOverrides();

        ThemeManager.sectionSpacing();
        renderAdvanced(labelW, scale);

        ThemeManager.paddedSeparator();

        if (state.getResult() != null) {
            renderResultPanel(io, state.getResult(), scale);
            ThemeManager.sectionSpacing();
        }

        renderActions();
        renderApplyModal();
    }

    private void sectionHeader(String title) {
        ImGui.textDisabled(title);
        ThemeManager.bottomPaddedSeparator();
    }

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    /** Base width, widened so the result table (which can be wider than the form) fits without clipping. */
    private float windowWidth(SolveResult r, float scale) {
        float base = 320f * scale;
        if (r == null) return base;
        float cellPad = ImGui.getStyle().getCellPadding().x;

        float[] col = new float[5];
        for (SolveResult.Outcome o : r.getOutcomes()) {
            col[0] = Math.max(col[0], ImGui.calcTextSize(o.field).x);
            col[1] = Math.max(col[1], ImGui.calcTextSize("@ " + o.tick).x);
            col[2] = Math.max(col[2], ImGui.calcTextSize(o.relation).x);
            col[3] = Math.max(col[3], ImGui.calcTextSize(o.found).x);
            col[4] = Math.max(col[4], ImGui.calcTextSize(o.margin).x);
        }
        float outcomesW = 0f;
        for (float c : col) outcomesW += c + 2f * cellPad;

        float yawA = 0f, yawB = 0f;
        for (SolveResult.YawEntry y : r.getYaws()) {
            yawA = Math.max(yawA, ImGui.calcTextSize("T" + y.tick).x);
            yawB = Math.max(yawB, ImGui.calcTextSize(ConstraintText.fixed6(y.yaw) + "°").x);
        }
        float yawsW = yawA + yawB + 4f * cellPad;

        Fonts.pushBold();
        float headerW = ImGui.calcTextSize("Solved · " + r.getMet() + "/" + r.getTotal() + " constraints met").x;
        Fonts.popBold();

        float inner = Math.max(Math.max(outcomesW, yawsW), headerW);
        float chrome = 2f * ThemeManager.LG * scale + 2f * ThemeManager.SM * scale + 2f + ThemeManager.SM * scale;
        return Math.max(base, inner + chrome);
    }

    private void tickRow(String label, boolean start, int rowCount, float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, labelW);
        int current = start ? state.getStartTick() : state.getLandingTick();
        ImInt buf = start ? startTickBuf : goalTickBuf;
        buf.set(current + 1); // ticks are 0-based internally, shown 1-based
        if (Controls.inputInt(start ? "##startTick" : "##goalTick", buf, ImGui.getContentRegionAvail().x)) {
            int next = Math.max(0, Math.min(rowCount - 1, buf.get() - 1));
            if (start) state.setStartTick(next);
            else state.setLandingTick(next);
        }
        Controls.popInputFrameHeight();
    }

    private int segmentedRow(String label, String id, String[] items, int selected, float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, labelW);
        int clicked = SolverWidgets.segmented(id, items, selected, ImGui.getContentRegionAvail().x);
        Controls.popInputFrameHeight();
        return clicked;
    }

    private void slipperinessRow(float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Slipperiness", labelW);
        slipBuf.set(state.getDefaultSlipperiness().ordinal());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        if (Controls.combo("##slip", slipBuf, slipItems)) {
            state.setDefaultSlipperiness(Slipperiness.values()[slipBuf.get()]);
        }
        Controls.popInputFrameHeight();
    }

    private void potionRow(float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Potion", labelW);
        float controlX = ImGui.getCursorPosX();
        doseToRemove = -1;

        List<PotionDose> doses = state.getDefaultPotions();
        for (int i = 0; i < doses.size(); i++) {
            if (i > 0) ImGui.setCursorPosX(controlX);
            renderDoseRow(i, doses.get(i));
        }
        if (doseToRemove >= 0) state.removeDefaultPotion(doseToRemove);

        if (!doses.isEmpty()) ImGui.setCursorPosX(controlX);
        if (state.nextUnusedDefaultPotion() == null) {
            Controls.disabledButton("+ add");
        } else if (Controls.secondaryButton("+ add")) {
            state.addNextDefaultPotion();
        }
        Controls.popInputFrameHeight();
    }

    private void renderDoseRow(int index, PotionDose dose) {
        float scale = ThemeManager.uiScale();
        float gap = ImGui.getStyle().getItemSpacing().x;
        float avail = ImGui.getContentRegionAvail().x;
        float levelW = 70f * scale;
        float removeW = SolverWidgets.deleteXWidth();
        float comboW = Math.max(70f * scale, avail - levelW - removeW - 2f * gap);

        List<Potion> options = state.availableDefaultPotions(index);
        String[] items = new String[options.size()];
        for (int i = 0; i < items.length; i++) items[i] = options.get(i).label;
        doseCombo.set(Math.max(0, options.indexOf(dose.potion)));
        ImGui.setNextItemWidth(comboW);
        if (Controls.combo("##dose" + index, doseCombo, items)) {
            dose.potion = options.get(doseCombo.get());
        }
        ImGui.sameLine();
        levelBuf.set(dose.level);
        ImGui.setNextItemWidth(levelW);
        // step 0 hides the +/- buttons; at this width they would consume the whole field and leave nothing to type in.
        if (ImGui.inputInt("##lvl" + index, levelBuf, 0, 0)) {
            dose.level = Math.max(1, Math.min(10, levelBuf.get()));
        }
        ImGui.sameLine();
        if (SolverWidgets.deleteX("rm" + index)) doseToRemove = index;
    }

    /** Collapsible Advanced section: the solve-effort selector (speed vs the last micrometers of objective). */
    private void renderAdvanced(float labelW, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton("##advtoggle", ImGui.getContentRegionAvail().x, rowH)) {
            advancedExpanded = !advancedExpanded;
        }
        boolean hover = ImGui.isItemHovered();
        int col = hover ? ThemeManager.textColor() : ThemeManager.textDimColor();
        float cy = origin.y + rowH * 0.5f;
        if (advancedExpanded) SolverWidgets.triangleDown(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        else SolverWidgets.triangleRight(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        dl.addText(origin.x + 13f * scale, origin.y, col, "Advanced");
        if (!advancedExpanded) return;

        ThemeManager.bottomPaddedSeparator();
        int e = segmentedRow("Effort", "effort", EFFORTS, state.getEffort().ordinal(), labelW);
        if (e >= 0) state.setEffort(AngleSolverState.Effort.values()[e]);

        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(effortHint(state.getEffort()));
        ThemeManager.popTextColor();
    }

    private static String effortHint(AngleSolverState.Effort e) {
        switch (e) {
            case FAST: return e.hint + ", smaller search";
            case THOROUGH: return e.hint + ", widest search";
            default: return e.hint + ", recommended";
        }
    }

    private void renderActions() {
        if (engine.isSolving()) {
            renderSolvingIndicator();
            return;
        }
        if (Controls.secondaryButton("Solve")) {
            yawsExpanded = false;
            engine.solve();
        }
        ImGui.sameLine();
        SolveResult r = state.getResult();
        // Only a feasible solve can be applied: an unmet constraint is a wall, so applying it always collides.
        boolean hasSolution = r != null && r.isSuccess() && !r.getYaws().isEmpty();
        if (hasSolution) {
            if (Controls.secondaryButton("Apply")) ImGui.openPopup(APPLY_POPUP_ID);
        } else {
            Controls.disabledButton("Apply");
        }
    }

    private void renderSolvingIndicator() {
        float scale = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        ImVec2 p = ImGui.getCursorScreenPos();
        SolverWidgets.spinner(ImGui.getWindowDrawList(), p.x + h * 0.5f, p.y + h * 0.5f, h * 0.30f,
                1.8f * scale, ThemeManager.accentColor(), engine.elapsedSeconds());
        ImGui.dummy(h, h);
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(String.format(Locale.ROOT, "Solving... %.1fs", engine.elapsedSeconds()));
        ThemeManager.popTextColor();

        ImGui.sameLine();
        float xW = SolverWidgets.deleteXWidth();
        float avail = ImGui.getContentRegionAvail().x;
        if (avail > xW) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - xW);
        if (SolverWidgets.deleteX("##cancelSolve")) engine.cancel();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Cancel search");
    }

    private void renderResultPanel(ImGuiIO io, SolveResult r, float scale) {
        int accent = r.isSuccess() ? ThemeManager.okColor() : ThemeManager.dangerColor();
        int bg = r.isSuccess() ? ThemeManager.okTintColor(0.10f) : ThemeManager.dangerTintColor(0.10f);
        int border = r.isSuccess() ? ThemeManager.okTintColor(0.45f) : ThemeManager.dangerTintColor(0.45f);

        float lineH = ImGui.getTextLineHeightWithSpacing();
        int statsLines = (r.getFinishedAt() != null ? 1 : 0) + (r.hasObjective() ? 1 : 0);
        int rows = 2 + statsLines + r.getOutcomes().size() + 1 + (yawsExpanded ? r.getYaws().size() : 0);
        float pad = ThemeManager.SM * scale;
        float fullH = rows * lineH + 2f * pad;
        float h = Math.min(fullH, io.getDisplaySizeY() * 0.4f); // cap so the pane scrolls instead of growing off-screen

        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg);
        ImGui.pushStyleColor(ImGuiCol.Border, border);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.beginChild("##solve_result", ImGui.getContentRegionAvail().x, h, true);

        ThemeManager.pushTextColor(accent);
        Fonts.pushBold();
        ImGui.text((r.isSuccess() ? "Solved · " : "No solution · ") + r.getMet() + "/" + r.getTotal() + " constraints met");
        Fonts.popBold();
        ThemeManager.popTextColor();
        ThemeManager.bottomPaddedSeparator();

        renderStats(r);
        renderOutcomes(r);
        renderYawList(r, scale);

        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);
    }

    private void renderStats(SolveResult r) {
        if (r.getFinishedAt() == null && !r.hasObjective()) return;
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        if (r.getFinishedAt() != null) {
            long nanos = r.getDurationNanos() > 0 ? r.getDurationNanos() : r.getDurationMs() * 1_000_000L;
            ImGui.text("Took " + fmtDuration(nanos) + " · finished " + r.getFinishedAt());
        }
        if (r.hasObjective()) {
            String goal = state.getGoal() == AngleSolverState.Goal.MAX ? "max" : "min";
            ImGui.text(goal + " " + state.getAxis().name() + " = " + ConstraintText.fixed7(r.getObjectiveValue()));
        }
        ThemeManager.popTextColor();
    }

    private static String fmtDuration(long nanos) {
        if (nanos >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fs", nanos / 1.0e9);
        if (nanos >= 1_000_000L) return String.format(Locale.ROOT, "%.1fms", nanos / 1.0e6);
        if (nanos >= 1_000L) return String.format(Locale.ROOT, "%.1fµs", nanos / 1.0e3);
        return nanos + "ns";
    }

    private void renderOutcomes(SolveResult r) {
        if (r.getOutcomes().isEmpty()) return;
        // field | @ tick | relation | found (right) | margin (right, green): own columns so every part aligns vertically.
        if (!ThemeManager.beginStandardFormTable("##sv_outcomes", 5)) return;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.text(o.field);
            ImGui.tableNextColumn();
            ImGui.text("@ " + o.tick);
            ImGui.tableNextColumn();
            ImGui.text(o.relation);
            ImGui.tableNextColumn();
            textRightInCell(o.found);
            ImGui.tableNextColumn();
            if (!o.margin.isEmpty()) {
                ThemeManager.pushTextColor(ThemeManager.okColor());
                textRightInCell(o.margin);
                ThemeManager.popTextColor();
            }
        }
        ThemeManager.endStandardFormTable();
    }

    private void renderYawList(SolveResult r, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton("yawtoggle", ImGui.getContentRegionAvail().x, rowH)) yawsExpanded = !yawsExpanded;
        boolean hover = ImGui.isItemHovered();
        int col = hover ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        float cy = origin.y + rowH * 0.5f;
        if (yawsExpanded) SolverWidgets.triangleDown(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        else SolverWidgets.triangleRight(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        dl.addText(origin.x + 13f * scale, origin.y, col, "Yaws found (" + r.getYaws().size() + ")");

        if (!yawsExpanded) return;
        if (!ThemeManager.beginStandardFormTable("##sv_yaws", 2)) return;
        for (SolveResult.YawEntry y : r.getYaws()) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("T" + y.tick);
            ThemeManager.popTextColor();
            ImGui.tableNextColumn();
            textRightInCell(ConstraintText.fixed6(y.yaw) + "°");
        }
        ThemeManager.endStandardFormTable();
    }

    /** Right-aligns within the current table cell without the frame-padding offset textRight adds, so it stays baseline-aligned with the plain-text columns. */
    private static void textRightInCell(String s) {
        float avail = ImGui.getContentRegionAvail().x;
        float tw = ImGui.calcTextSize(s).x;
        if (avail > tw) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - tw);
        ImGui.text(s);
    }

    private void renderApplyModal() {
        if (!Modal.begin("Overwrite inputs?", APPLY_POPUP_ID)) return;
        SolveResult r = state.getResult();
        int from = r != null ? Math.min(r.getStartTick(), r.getLandingTick()) : 1;
        int to = r != null ? Math.max(r.getStartTick(), r.getLandingTick()) : 1;

        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.text("Applying overwrites existing inputs.");
        ThemeManager.popTextColor();
        ImGui.textWrapped("The solved yaws will replace the inputs on ticks T" + from + " to T" + to
                + ". Save a copy first if you want to keep the current inputs.");

        Modal.footerSeparator();
        if (Controls.secondaryButton("Save as Copy")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        Controls.cursorToRightAlignedButton("Apply");
        if (Controls.secondaryButton("Apply")) {
            engine.apply();
            ImGui.closeCurrentPopup();
        }
        Modal.end();
    }
}
