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
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The floating Angle Solver window: whole-problem inputs (start / goal tick, axis, goal),
 * the default per-tick state, the Solve / Apply actions, and the result panel.
 * Toggled from View > Angle Solver.
 */
public final class AngleSolverWindow implements RenderInterface {

    private static final String WINDOW_ID = "###angle_solver";
    private static final String TITLE = "Angle Solver";
    private static final String APPLY_POPUP_ID = "###angle_solver_apply";

    private static final String[] AXES = {"X", "Z"};
    private static final String[] GOALS = {"MAX", "MIN"};
    private static final String[] INPUTS = {"Keep", "Force 45"};
    private static final String[] SPRINTS = {"Always", "Derive"};
    private static final String[] SPRINT_TIPS = {null,
            "WARNING: derives each tick's sprint state from the current recorded path.\n"
                    + "The path is the source of truth here: a recording that hits a wall loses\n"
                    + "sprint from that tick on, and the solve inherits it, so a broken path can\n"
                    + "make a solvable segment report no solution until the route is re-recorded."};
    private static final String[] EFFORTS = {"Fast", "Thorough", "Custom"};
    private static final String[] POLISH_DEPTHS = {"Light", "Exhaustive"};
    private static final String[] MULTI_JUMP = {"Window", "Global"};

    private static final String[] FORM_LABELS =
            {"Start tick", "Goal tick", "Axis", "Goal", "Inputs", "Sprint", "Slipperiness", "Potion"};

    /** Unscaled; lines the details table up under the toggle title and sets it off from the solved values. */
    private static final float DETAIL_INDENT = 13f;

    private static final int LONG_SPAN_WARN_TICKS = 100;

    private static final String LONG_SPAN_TIP =
            "A long span is solved a window of ~10 jumps at a time: each window is solved exactly, its"
            + " first jumps are committed, and the window slides forward. A commit is guaranteed safe for"
            + " the jumps the next window can see, but not beyond that lookahead, so on a long run an"
            + " early commit can leave a much later jump with no feasible angle and the solve gets stuck,"
            + " reporting no solution even though a route exists. The more windows a span needs, the more"
            + " chances to get stuck; up to ~300 ticks usually still works. If the early part of the run"
            + " is already the way you want it, move the start tick forward and solve just the remaining"
            + " segment.";

    private final AngleSolverState state;
    private final Settings settings;
    private final IntSupplier rowCountSupplier;
    private final AngleSolverEngine engine;
    private final VelocityMapWidget velocityMap;
    private final ImInt startTickBuf = new ImInt();
    private final ImInt goalTickBuf = new ImInt();
    private final ImInt slipBuf = new ImInt();
    private final ImInt doseCombo = new ImInt();
    private final ImInt levelBuf = new ImInt();
    private final int[] restartsBuf = new int[1];
    private final int[] maxEvalBuf = new int[1];
    private final int[] polishCountBuf = new int[1];
    private final int[] timeBudgetBuf = new int[1];
    private final int[] windowBuf = new int[1];
    private final int[] commitBuf = new int[1];
    private final String[] slipItems;

    private boolean yawsExpanded;
    private boolean detailsExpanded;
    private boolean solverExpanded;
    private boolean outcomesExpanded = true;
    private boolean problemExpanded = true;
    private boolean solveForExpanded = true;
    private boolean defaultStateExpanded = true;
    private boolean advancedExpanded;
    private int doseToRemove;

    public AngleSolverWindow(AngleSolverState state, Settings settings,
                             IntSupplier rowCountSupplier, AngleSolverEngine engine,
                             VelocityMapWidget velocityMap) {
        this.state = state;
        this.settings = settings;
        this.rowCountSupplier = rowCountSupplier;
        this.engine = engine;
        this.velocityMap = velocityMap;
        this.slipItems = Slipperiness.comboItems();
    }

    @Override
    public void render(ImGuiIO io) {
        if (velocityMap != null) {
            velocityMap.setWindowOpen(settings.viewVelocityMap);
            velocityMap.renderWindow(ThemeManager.uiScale());
            settings.viewVelocityMap = velocityMap.isWindowOpen();
        }
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

        problemExpanded = sectionToggle("Problem", "problem", problemExpanded, scale);
        if (problemExpanded) {
            tickRow("Start tick", true, rowCount, labelW);
            tickRow("Goal tick", false, rowCount, labelW);
        }
        int span = state.getLandingTick() - state.getStartTick();
        if (span > LONG_SPAN_WARN_TICKS) longSpanWarning(span, scale);

        ThemeManager.sectionSpacing();

        solveForExpanded = sectionToggle("Solve for", "solvefor", solveForExpanded, scale);
        if (solveForExpanded) {
            int ax = segmentedRow("Axis", "axis", AXES, state.getAxis().ordinal(), labelW);
            if (ax >= 0) state.setAxis(AngleSolverState.Axis.values()[ax]);
            int gl = segmentedRow("Goal", "goal", GOALS, state.getGoal().ordinal(), labelW);
            if (gl >= 0) state.setGoal(AngleSolverState.Goal.values()[gl]);
        }

        ThemeManager.sectionSpacing();

        defaultStateExpanded = sectionToggle("Default state", "defaultstate", defaultStateExpanded, scale);
        if (defaultStateExpanded) {
            int im = segmentedRow("Inputs", "inputs", INPUTS, state.getDefaultInputs().ordinal(), labelW);
            if (im >= 0) state.setDefaultInputs(AngleSolverState.InputMode.values()[im]);

            int sp = segmentedRow("Sprint", "sprint", SPRINTS, SPRINT_TIPS, state.getDefaultSprint().ordinal(), labelW);
            if (sp >= 0) state.setDefaultSprint(AngleSolverState.SprintMode.values()[sp]);

            slipperinessRow(labelW);
            potionRow(labelW);
        }
        state.pruneRedundantOverrides();

        ThemeManager.sectionSpacing();
        renderAdvanced(labelW, scale);

        ThemeManager.paddedSeparator();

        if (state.getResult() != null) {
            renderResultPanel(io, state.getResult(), scale);
            autoApplyDisabledWarning(state.getResult());
            ThemeManager.sectionSpacing();
        }

        renderActions();
        renderApplyModal();
    }

    private void longSpanWarning(int span, float scale) {
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.text(span + "t span: solves can be unreliable");
        ThemeManager.popTextColor();
        ImGui.sameLine();

        float lineH = ImGui.getTextLineHeight();
        float r = lineH * 0.42f;
        ImVec2 p = ImGui.getCursorScreenPos();
        ImGui.invisibleButton("##spanInfo", 2f * r + 4f * scale, lineH);
        int col = ImGui.isItemHovered() ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        // (i) drawn as shapes; the in-game font has no info glyph.
        ImDrawList dl = ImGui.getWindowDrawList();
        float cx = p.x + r + 2f * scale;
        float cy = p.y + lineH * 0.5f;
        dl.addCircle(cx, cy, r, col, 16, Math.max(1f, 1.2f * scale));
        dl.addCircleFilled(cx, cy - r * 0.45f, Math.max(1f, r * 0.14f), col, 8);
        float bw = Math.max(1f, r * 0.18f);
        dl.addRectFilled(cx - bw * 0.5f, cy - r * 0.1f, cx + bw * 0.5f, cy + r * 0.55f, col);
        TooltipUtil.onHover(LONG_SPAN_TIP);
    }

    private void autoApplyDisabledWarning(SolveResult r) {
        if (settings.autoApplySolve) return;
        if (!r.isSuccess() || r.getYaws().isEmpty()) return;
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.text("You have auto apply disabled.");
        ThemeManager.popTextColor();
    }

    /** Collapsible section header (triangle + title); returns the new expanded state. */
    private boolean sectionToggle(String title, String id, boolean expanded, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton("##" + id + "_toggle", ImGui.getContentRegionAvail().x, rowH)) {
            expanded = !expanded;
        }
        int col = ImGui.isItemHovered() ? ThemeManager.textColor() : ThemeManager.textDimColor();
        float cy = origin.y + rowH * 0.5f;
        if (expanded) SolverWidgets.triangleDown(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        else SolverWidgets.triangleRight(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        dl.addText(origin.x + 13f * scale, origin.y, col, title);
        if (expanded) ThemeManager.bottomPaddedSeparator();
        return expanded;
    }

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    /** Base width, widened so the expanded result tables fit without clipping; collapsed sections don't hold the window wide. */
    private float windowWidth(SolveResult r, float scale) {
        float base = 320f * scale;
        float labelW = labelColumnWidth(scale);
        float inner = formInner(labelW);

        if (r != null) {
            float cellPad = ImGui.getStyle().getCellPadding().x;
            Fonts.pushBold();
            inner = Math.max(inner, ImGui.calcTextSize(resultHeader(r)).x);
            Fonts.popBold();
            inner = Math.max(inner, resultTablesWidth(r, scale, cellPad));
        }

        float chrome = 2f * ThemeManager.LG * scale + 2f * ThemeManager.SM * scale + 2f + ThemeManager.SM * scale;
        return Math.max(base, inner + chrome);
    }

    private float formInner(float labelW) {
        float w = segmentedRowWidth("Axis", AXES, labelW);
        w = Math.max(w, segmentedRowWidth("Goal", GOALS, labelW));
        w = Math.max(w, segmentedRowWidth("Inputs", INPUTS, labelW));
        w = Math.max(w, segmentedRowWidth("Sprint", SPRINTS, labelW));
        if (advancedExpanded) {
            w = Math.max(w, segmentedRowWidth("Effort", EFFORTS, labelW));
            if (state.getEffort() == AngleSolverState.Effort.CUSTOM) {
                w = Math.max(w, segmentedRowWidth("Polish depth", POLISH_DEPTHS, labelW));
                w = Math.max(w, segmentedRowWidth("Multi-jump", MULTI_JUMP, labelW));
            }
        }
        return w;
    }

    private float segmentedRowWidth(String label, String[] items, float labelW) {
        float lw = Math.max(labelW, ImGui.calcTextSize(label).x + ImGui.getStyle().getItemSpacing().x);
        return lw + SolverWidgets.segmentedMinWidth(items);
    }

    private float resultTablesWidth(SolveResult r, float scale, float cellPad) {
        float inner = 0f;
        if (outcomesExpanded) {
            float[] col = new float[5];
            for (SolveResult.Outcome o : r.getOutcomes()) {
                col[0] = Math.max(col[0], ImGui.calcTextSize(o.field).x);
                col[1] = Math.max(col[1], ImGui.calcTextSize("@ " + o.tick).x);
                col[2] = Math.max(col[2], ImGui.calcTextSize(o.relation).x);
                col[3] = Math.max(col[3], ImGui.calcTextSize(o.found).x);
                col[4] = Math.max(col[4], ImGui.calcTextSize(o.margin).x);
            }
            float outcomesW = DETAIL_INDENT * scale;
            for (float c : col) outcomesW += c + 2f * cellPad;
            inner = Math.max(inner, outcomesW);
        }

        if (yawsExpanded) {
            float yawA = 0f, yawB = 0f;
            for (SolveResult.YawEntry y : r.getYaws()) {
                yawA = Math.max(yawA, ImGui.calcTextSize("T" + y.tick).x);
                yawB = Math.max(yawB, ImGui.calcTextSize(ConstraintText.fixedYaw(y.yaw) + "°").x);
            }
            inner = Math.max(inner, yawA + yawB + 4f * cellPad + DETAIL_INDENT * scale);
        }

        if (detailsExpanded) {
            float dLabel = 0f, dValue = 0f;
            for (SolveResult.Detail d : detailRows(r)) {
                dLabel = Math.max(dLabel, ImGui.calcTextSize(d.label).x);
                dValue = Math.max(dValue, ImGui.calcTextSize(d.value).x);
            }
            inner = Math.max(inner, dLabel + dValue + 4f * cellPad + DETAIL_INDENT * scale);

            if (solverExpanded) {
                List<String> steps = solverSteps(r);
                float numW = 0f, stepW = 0f;
                for (int i = 0; i < steps.size(); i++) {
                    numW = Math.max(numW, ImGui.calcTextSize((i + 1) + ".").x);
                    stepW = Math.max(stepW, ImGui.calcTextSize(steps.get(i)).x);
                }
                inner = Math.max(inner, numW + stepW + 4f * cellPad + 2f * DETAIL_INDENT * scale);
            }
        }

        return inner;
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
        return segmentedRow(label, id, items, null, selected, labelW);
    }

    private int segmentedRow(String label, String id, String[] items, String[] tooltips, int selected, float labelW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, labelW);
        int clicked = SolverWidgets.segmented(id, items, tooltips, selected, ImGui.getContentRegionAvail().x);
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

    private void renderAdvanced(float labelW, float scale) {
        advancedExpanded = sectionToggle("Advanced", "adv", advancedExpanded, scale);
        if (!advancedExpanded) return;

        int e = segmentedRow("Effort", "effort", EFFORTS, state.getEffort().ordinal(), labelW);
        if (e >= 0) state.setEffort(AngleSolverState.Effort.values()[e]);

        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(state.getEffort().hint);
        ThemeManager.popTextColor();

        if (state.getEffort() == AngleSolverState.Effort.CUSTOM) renderCustomBudget(labelW);
    }

    private void renderCustomBudget(float labelW) {
        AngleSolverState.SolveBudget b = state.getSolveBudget();

        restartsBuf[0] = b.getRestarts();
        if (sliderIntRow("Restarts", "##restarts", restartsBuf,
                AngleSolverState.MIN_RESTARTS, AngleSolverState.MAX_RESTARTS, "%d", labelW, RESTARTS_TIP)) {
            b.setRestarts(restartsBuf[0]);
        }

        maxEvalBuf[0] = b.getMaxEval();
        if (sliderIntRow("Max evals", "##maxEval", maxEvalBuf,
                AngleSolverState.MIN_MAX_EVAL, AngleSolverState.MAX_MAX_EVAL, "%d", labelW, MAX_EVALS_TIP)) {
            b.setMaxEval(maxEvalBuf[0]);
        }

        polishCountBuf[0] = b.getPolishCount();
        if (sliderIntRow("Polish basins", "##polishCount", polishCountBuf,
                AngleSolverState.MIN_POLISH_COUNT, AngleSolverState.MAX_POLISH_COUNT, "%d", labelW, POLISH_BASINS_TIP)) {
            b.setPolishCount(polishCountBuf[0]);
        }

        ImGui.beginGroup();
        int pd = segmentedRow("Polish depth", "polishDepth", POLISH_DEPTHS, b.getPolishDepth().ordinal(), labelW);
        ImGui.endGroup();
        TooltipUtil.onHover(POLISH_DEPTH_TIP);
        if (pd >= 0) b.setPolishDepth(AngleSolverState.PolishDepth.values()[pd]);

        timeBudgetBuf[0] = b.getTimeBudgetSeconds();
        if (sliderIntRow("Time budget", "##timeBudget", timeBudgetBuf,
                AngleSolverState.MIN_TIME_BUDGET, AngleSolverState.MAX_TIME_BUDGET,
                timeBudgetBuf[0] == 0 ? "Off" : "%d s", labelW, TIME_BUDGET_TIP)) {
            b.setTimeBudgetSeconds(timeBudgetBuf[0]);
        }

        ImGui.beginGroup();
        int mj = segmentedRow("Multi-jump", "multiJump", MULTI_JUMP, b.getUseWindowSolver() ? 0 : 1, labelW);
        ImGui.endGroup();
        TooltipUtil.onHover(MULTI_JUMP_TIP);
        if (mj >= 0) b.setUseWindowSolver(mj == 0);

        boolean windowDisabled = !b.getUseWindowSolver();
        if (windowDisabled) ImGui.beginDisabled(true);

        windowBuf[0] = b.getWindow();
        if (sliderIntRow("Window", "##window", windowBuf,
                AngleSolverState.MIN_WINDOW, AngleSolverState.MAX_WINDOW, "%d", labelW, WINDOW_TIP)) {
            b.setWindow(windowBuf[0]);
        }

        commitBuf[0] = b.getCommit();
        if (sliderIntRow("Commit", "##commit", commitBuf,
                AngleSolverState.MIN_COMMIT, Math.max(AngleSolverState.MIN_COMMIT, b.getWindow() - 1), "%d", labelW, COMMIT_TIP)) {
            b.setCommit(commitBuf[0]);
        }

        if (windowDisabled) ImGui.endDisabled();

        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Defaults reproduce Fast.");
        ThemeManager.popTextColor();
    }

    private static final String RESTARTS_TIP =
            "How many independent searches run per solve, each starting from a different random facing and"
            + " running in parallel across CPU cores. The feasible angles for a hard jump split into several"
            + " disconnected regions, and a single search can land in the wrong one or in none, so more"
            + " restarts means more chances to find a feasible region and to find the best one. Raise this"
            + " first when a jump you expect to be possible reports no solution, or when a solved path is"
            + " feasible but not reaching as far as it should. Time cost grows with the count, though the"
            + " runs share all cores. Default 16 matches Fast; Thorough uses 48.";

    private static final String MAX_EVALS_TIP =
            "The most trajectory evaluations a single restart may spend before it stops. A restart that has"
            + " reached the right region still needs evaluations to converge onto the exact angles, and"
            + " stopping early leaves it a little short of the true optimum. Raise this when the solve finds"
            + " a feasible path but the reached distance stalls just under what you expect, meaning the"
            + " search is in the right place but not finishing it. It rarely rescues a jump that fails"
            + " outright; add restarts for those instead. Default 4500 matches Fast; Thorough uses 12000.";

    private static final String POLISH_BASINS_TIP =
            "After the searches finish, this many of the best feasible results are each refined by the exact"
            + " polish in parallel, and only the single best polished result is kept. Because Minecraft snaps"
            + " every movement angle to a fixed grid of discrete steps, the search result with the highest"
            + " raw score is not always the one that polishes to the furthest reach, so polishing several"
            + " keeps the true best from being thrown away. Raise this when you are chasing the last fraction"
            + " of a block and want maximum reach. It costs more time but never makes the result worse."
            + " Default 2 matches Fast; Thorough uses 16.";

    private static final String POLISH_DEPTH_TIP =
            "How hard the final polish works on each kept result. Light runs a couple of narrow refinement"
            + " passes and relies on having several results to compare; it is what Fast uses. Exhaustive adds"
            + " wide passes that can move a wall-bound tick from one feasible angle step to a better"
            + " neighboring one, a fine settling pass, and a few seeded retries, so it reaches optimums a"
            + " light pass cannot, at a real time cost per result. Choose Exhaustive for tight wall-hugging"
            + " jumps where the best angles sit in a different discrete step than the search found. This is"
            + " the polish Thorough uses.";

    private static final String TIME_BUDGET_TIP =
            "An optional wall-clock limit on the search. At 0 (off) the solve runs exactly the fixed number"
            + " of restarts above, once. Above 0 it becomes a race against the clock: it keeps launching"
            + " fresh restart batches until the time runs out, then returns the best feasible result found,"
            + " so you trade guessing a restart count for simply giving the solver as long as you are willing"
            + " to wait. Use it on a stubborn single jump when you would rather set thirty seconds and walk"
            + " away than tune restarts by hand. It bounds only the search phase; jumps the instant"
            + " closed-form path already solves are unaffected.";

    private static final String MULTI_JUMP_TIP =
            "Which strategy solves a span containing more than one jump. Window (the default) slides a"
            + " short window along the run, solving a few jumps at a time exactly and committing the"
            + " leading ones; it is the only approach that holds up over long runs of many jumps. Global"
            + " instead throws the multistart search above (Restarts, Max evals, Polish basins, Time budget)"
            + " at every jump in the span at once, which are otherwise unused on multi-jump spans because"
            + " the window solver settles them on its own. Switch to Global on a short multi-jump span (a"
            + " handful of jumps) when you want to spend a large budget chasing a better result than the"
            + " window solver found; expect it to report no solution on long spans, where searching all"
            + " jumps jointly is exactly what the window solver exists to avoid. The Window and Commit"
            + " sliders below apply only to Window. Single jumps ignore this entirely.";

    private static final String WINDOW_TIP =
            "Used only for multi-jump spans, which are solved by sliding a window along the run. This sets"
            + " how many jumps that window solves together, exactly, at once. A larger window sees further"
            + " ahead, so the angles it fixes are less likely to strand a much later jump with no usable"
            + " option, but each window is harder to solve exactly and a very large one may not converge at"
            + " all. Raise it when a long run reports no solution because an early jump boxed in a later one."
            + " The exact solver reliably handles about 10 to 13 jumps. Default 10. Single jumps and short"
            + " spans ignore this.";

    private static final String COMMIT_TIP =
            "Used only for multi-jump spans. After a window is solved, this many of its leading jumps are"
            + " locked in before the window slides forward; the rest are re-solved by the next, overlapping"
            + " window. A smaller commit keeps more overlap and more lookahead at each step, which is safer"
            + " on hard runs but solves more windows and takes longer; a larger commit is faster but more"
            + " likely to lock in a jump that strands a later one. Lower this when a long run gets stuck. It"
            + " is capped one below the window. Default 3; the solver already retries internally at 1 if a"
            + " run gets stuck. Single jumps and short spans ignore this.";

    private boolean sliderIntRow(String label, String id, int[] buf, int lo, int hi, String fmt, float labelW, String tip) {
        Controls.pushInputFrameHeight();
        ImGui.beginGroup();
        SolverWidgets.rowLabel(label, labelW);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        boolean changed = Controls.sliderInt(id, buf, lo, hi, fmt);
        ImGui.endGroup();
        Controls.popInputFrameHeight();
        if (tip != null) TooltipUtil.onHover(tip);
        return changed;
    }

    private void renderActions() {
        if (engine.isSolving()) {
            renderSolvingIndicator();
            return;
        }
        if (Controls.secondaryButton("Solve")) {
            yawsExpanded = false;
            detailsExpanded = false;
            solverExpanded = false;
            outcomesExpanded = true;
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
        String deviation = state.getApplyDeviation();
        // A diverged apply is not a clean solve: the whole panel goes warning, not yellow-on-green.
        boolean diverged = r.isSuccess() && deviation != null;
        int accent = !r.isSuccess() ? ThemeManager.dangerColor()
                : diverged ? ThemeManager.warningColor() : ThemeManager.okColor();
        int bg = !r.isSuccess() ? ThemeManager.dangerTintColor(0.10f)
                : diverged ? ThemeManager.warningTintColor(0.10f) : ThemeManager.okTintColor(0.10f);
        int border = !r.isSuccess() ? ThemeManager.dangerTintColor(0.45f)
                : diverged ? ThemeManager.warningTintColor(0.45f) : ThemeManager.okTintColor(0.45f);

        float lineH = ImGui.getTextLineHeightWithSpacing();
        float pad = ThemeManager.SM * scale;
        int devLines = deviation == null ? 0
                : wrappedLineEstimate(deviation, ImGui.getContentRegionAvail().x - 2f * pad);
        List<SolveResult.Detail> details = detailRows(r);
        List<String> steps = solverSteps(r);
        int solverLines = steps.isEmpty() ? 0 : 1 + (solverExpanded ? steps.size() : 0);
        int detailLines = details.isEmpty() && steps.isEmpty() ? 0
                : 1 + (detailsExpanded ? details.size() + solverLines : 0);
        int outcomeLines = r.getOutcomes().isEmpty() ? 0 : 1 + (outcomesExpanded ? r.getOutcomes().size() : 0);
        int rows = 2 + detailLines + devLines + outcomeLines + 1 + (yawsExpanded ? r.getYaws().size() : 0);
        float fullH = rows * lineH + 2f * pad;
        float h = Math.min(fullH, io.getDisplaySizeY() * 0.4f); // cap so the pane scrolls instead of growing off-screen

        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg);
        ImGui.pushStyleColor(ImGuiCol.Border, border);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.beginChild("##solve_result", ImGui.getContentRegionAvail().x, h, true);

        ThemeManager.pushTextColor(accent);
        Fonts.pushBold();
        ImGui.text(resultHeader(r));
        Fonts.popBold();
        ThemeManager.popTextColor();
        ThemeManager.bottomPaddedSeparator();

        if (deviation != null) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(deviation);
            ThemeManager.popTextColor();
            String tip = deviationTip(state.getApplyDeviationKind());
            if (tip != null) TooltipUtil.onHover(tip);
        }
        renderOutcomes(r, scale);
        renderDetails(details, steps, scale);
        renderYawList(r, scale);

        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);
    }

    private static final String WALL_TIP =
            "The solver searches over thousands of candidate paths per solve, so it runs on a fast"
            + " collision-free movement model; checking world collisions on every candidate would make"
            + " the search orders of magnitude slower. Walls only show up when the real sim replays the"
            + " applied angles, which is what happened here. Add an X or Z constraint at the colliding"
            + " tick to route around the wall, then re-solve.";

    private static final String SNEAK_TIP =
            "Sneak is not a pure key effect: when the slowdown kicks in, and how long the crouch pose"
            + " lasts, depends on where the player is standing (edge clipping, blocks overhead). The"
            + " solve reuses the per-tick movement inputs sampled from the recorded run, so a sneak that"
            + " now happens at a different position produces different inputs than the sample."
            + " Re-solving from this run refreshes the samples.";

    private static String deviationTip(AngleSolverState.DeviationKind kind) {
        if (kind == AngleSolverState.DeviationKind.WALL) return WALL_TIP;
        if (kind == AngleSolverState.DeviationKind.SNEAK) return SNEAK_TIP;
        return null;
    }

    private String resultHeader(SolveResult r) {
        if (!r.isSuccess()) return "No solution · " + r.getMet() + "/" + r.getTotal() + " constraints met";
        if (state.getApplyDeviation() != null) return "Solved · sim diverged";
        return "Solved · " + r.getMet() + "/" + r.getTotal() + " constraints met";
    }

    /** Engine-filled details, or rows synthesized from the flat stats fields for results from older saves.
     *  "Solver" rows (written by older versions) are dropped: the chain has its own numbered section. */
    private List<SolveResult.Detail> detailRows(SolveResult r) {
        List<SolveResult.Detail> rows = new ArrayList<>();
        if (!r.getDetails().isEmpty()) {
            for (SolveResult.Detail d : r.getDetails()) {
                if (!"Solver".equals(d.label)) rows.add(d);
            }
            return rows;
        }
        if (r.getFinishedAt() != null) {
            long nanos = r.getDurationNanos() > 0 ? r.getDurationNanos() : r.getDurationMs() * 1_000_000L;
            rows.add(new SolveResult.Detail("Runtime", ConstraintText.duration(nanos)));
            rows.add(new SolveResult.Detail("Finished", r.getFinishedAt()));
        }
        if (r.hasObjective()) {
            String goal = state.getGoal() == AngleSolverState.Goal.MAX ? "max" : "min";
            rows.add(new SolveResult.Detail("Objective",
                    goal + " " + state.getAxis().name() + " = " + ConstraintText.fixedStat(r.getObjectiveValue())));
        }
        return rows;
    }

    /** The solver chain split at its fallthrough arrows, one numbered step per row. */
    private static List<String> solverSteps(SolveResult r) {
        if (r.getSolver() == null || r.getSolver().isEmpty()) return Collections.emptyList();
        return Arrays.asList(r.getSolver().split(" -> "));
    }

    private void renderSolverSection(List<String> steps, float scale) {
        if (steps.isEmpty()) return;
        solverExpanded = resultToggle("solvertoggle", "Solver (" + steps.size() + ")", solverExpanded, scale);
        if (!solverExpanded) return;
        ImGui.indent(DETAIL_INDENT * scale);
        if (ThemeManager.beginStandardFormTable("##sv_solver", 2)) {
            for (int i = 0; i < steps.size(); i++) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text((i + 1) + ".");
                ThemeManager.popTextColor();
                ImGui.tableNextColumn();
                ImGui.text(steps.get(i));
            }
            ThemeManager.endStandardFormTable();
        }
        ImGui.unindent(DETAIL_INDENT * scale);
    }

    /** The collapsible-row header shared by Solver / Details / Solved values / Yaws; returns the new expanded state. */
    private boolean resultToggle(String id, String title, boolean expanded, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton(id, ImGui.getContentRegionAvail().x, rowH)) expanded = !expanded;
        int col = ImGui.isItemHovered() ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        float cy = origin.y + rowH * 0.5f;
        if (expanded) SolverWidgets.triangleDown(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        else SolverWidgets.triangleRight(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        dl.addText(origin.x + 13f * scale, origin.y, col, title);
        return expanded;
    }

    private void renderDetails(List<SolveResult.Detail> details, List<String> steps, float scale) {
        if (details.isEmpty() && steps.isEmpty()) return;
        detailsExpanded = resultToggle("detailstoggle", "Details", detailsExpanded, scale);
        if (!detailsExpanded) return;
        // Indented so the debug stats read as a sub-block, distinct from the solved values above.
        ImGui.indent(DETAIL_INDENT * scale);
        renderSolverSection(steps, scale);
        if (ThemeManager.beginStandardFormTable("##sv_details", 2)) {
            for (SolveResult.Detail d : details) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text(d.label);
                ThemeManager.popTextColor();
                ImGui.tableNextColumn();
                textRightInCell(d.value);
            }
            ThemeManager.endStandardFormTable();
        }
        ImGui.unindent(DETAIL_INDENT * scale);
    }


    private void renderOutcomes(SolveResult r, float scale) {
        if (r.getOutcomes().isEmpty()) return;
        outcomesExpanded = resultToggle("outcomestoggle", "Solved values (" + r.getOutcomes().size() + ")",
                outcomesExpanded, scale);
        if (!outcomesExpanded) return;
        ImGui.indent(DETAIL_INDENT * scale);
        // field | @ tick | relation | found (right) | margin (right, green): own columns so every part aligns vertically.
        if (ThemeManager.beginStandardFormTable("##sv_outcomes", 5)) {
            for (SolveResult.Outcome o : r.getOutcomes()) {
                ImGui.tableNextRow();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.tableNextColumn();
                ImGui.text(o.field);
                ImGui.tableNextColumn();
                ImGui.text("@ " + o.tick);
                ImGui.tableNextColumn();
                ImGui.text(o.relation);
                ThemeManager.popTextColor();
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
        ImGui.unindent(DETAIL_INDENT * scale);
    }

    private void renderYawList(SolveResult r, float scale) {
        yawsExpanded = resultToggle("yawtoggle", "Yaws found (" + r.getYaws().size() + ")", yawsExpanded, scale);
        if (!yawsExpanded) return;
        ImGui.indent(DETAIL_INDENT * scale);
        if (ThemeManager.beginStandardFormTable("##sv_yaws", 2)) {
            for (SolveResult.YawEntry y : r.getYaws()) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text("T" + y.tick);
                ThemeManager.popTextColor();
                ImGui.tableNextColumn();
                textRightInCell(ConstraintText.fixedYaw(y.yaw) + "°");
            }
            ThemeManager.endStandardFormTable();
        }
        ImGui.unindent(DETAIL_INDENT * scale);
    }

    private static int wrappedLineEstimate(String s, float width) {
        if (width <= 0f) return 1;
        return (int) Math.ceil(ImGui.calcTextSize(s).x / width);
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
