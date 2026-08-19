package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.Potion;
import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphFactory;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
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
import imgui.type.ImString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The floating Angle Solver window: whole-problem inputs (start / goal tick, axis, goal),
 * the default per-tick state, the Solve action, and the result panel.
 * Toggled from View > Angle Solver.
 */
public final class AngleSolverWindow implements RenderInterface {

    private static final String WINDOW_ID = "###angle_solver";
    private static final String TITLE = "Angle Solver";

    private static final String[] AXES = {"X", "Z"};
    private static final String[] GOALS = {"MAX", "MIN"};
    private static final String[] INPUTS = {"Keep", "Force 45"};
    private static final String[] SPRINTS = {"Always", "Derive"};
    private static final String[] SPRINT_TIPS = {null,
            "WARNING: derives each tick's sprint state from the current recorded path.\n"
                    + "The path is the source of truth here: a recording that hits a wall loses\n"
                    + "sprint from that tick on, and the solve inherits it, so a broken path can\n"
                    + "make a solvable segment report no solution until the route is re-recorded."};
    private static final String[] EFFORTS = {"Fast", "Optimize", "Custom"};
    private static final String LEGACY_PRESET_ITEM = "Legacy budget";

    private static final String[] FORM_LABELS =
            {"Start tick", "Goal tick", "Axis", "Goal", "Target angle", "Inputs", "Sprint", "Slipperiness", "Potion"};

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
    private final FileSystemSaveStore graphStore;
    private final GraphEditorWindow graphEditor;
    private final ImInt startTickBuf = new ImInt();
    private final ImInt goalTickBuf = new ImInt();
    private final ImInt slipBuf = new ImInt();
    private final ImInt doseCombo = new ImInt();
    private final ImInt levelBuf = new ImInt();
    private final int[] optimizeSecondsBuf = new int[1];
    private final ImInt presetBuf = new ImInt();
    private final ImString presetNameInput = new ImString(64);
    private final String[] slipItems;
    private String[] presetNames;
    private String presetError;
    private Runnable applySurfaceState = () -> { };

    private boolean yawsExpanded;
    private boolean detailsExpanded;
    private boolean solverExpanded;
    private boolean outcomesExpanded = true;
    private boolean problemExpanded = true;
    private boolean solveForExpanded = true;
    private boolean defaultStateExpanded = true;
    private boolean advancedExpanded;
    private int doseToRemove;
    private java.util.function.DoubleSupplier playerYawSupplier = () -> 0.0;

    public void setPlayerYawSupplier(java.util.function.DoubleSupplier supplier) {
        this.playerYawSupplier = supplier != null ? supplier : () -> 0.0;
    }

    private static final float IMPROVE_FADE_SECS = 1.6f;
    private double improveTrackValue = Double.NaN;
    private double improveFlashStart = -1e9;
    private double improveFlashDelta;

    public AngleSolverWindow(AngleSolverState state, Settings settings,
                             IntSupplier rowCountSupplier, AngleSolverEngine engine,
                             VelocityMapWidget velocityMap, FileSystemSaveStore graphStore,
                             GraphEditorWindow graphEditor) {
        this.state = state;
        this.settings = settings;
        this.rowCountSupplier = rowCountSupplier;
        this.engine = engine;
        this.velocityMap = velocityMap;
        this.graphStore = graphStore;
        this.graphEditor = graphEditor;
        if (graphEditor != null) graphEditor.setSaveHandler(this::writePreset);
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
        int rowCount = Math.max(1, rowCountSupplier.getAsInt());
        state.clampTicks(rowCount);

        float scale = ThemeManager.uiScale();
        SolveResult sizingResult = engine.isSolving() ? engine.liveBestResult() : state.getResult();
        float w = windowWidth(sizingResult, scale);
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
            if (!state.isCustomAngle()) {
                int ax = segmentedRow("Axis", "axis", AXES, state.getAxis().ordinal(), labelW);
                if (ax >= 0) state.setAxis(AngleSolverState.Axis.values()[ax]);
                int gl = segmentedRow("Goal", "goal", GOALS, state.getGoal().ordinal(), labelW);
                if (gl >= 0) state.setGoal(AngleSolverState.Goal.values()[gl]);
            } else {
                Controls.pushInputFrameHeight();
                ImGui.beginGroup();
                SolverWidgets.rowLabel("Target angle", labelW);

                float btnW = ImGui.getFrameHeight();
                float spacing = ImGui.getStyle().getItemInnerSpacing().x;
                float inputW = ImGui.getContentRegionAvail().x - btnW - spacing;

                ImGui.setNextItemWidth(inputW);
                imgui.type.ImString angStr = new imgui.type.ImString(ConstraintText.num(state.getCustomAngleDeg()), 32);
                if (ImGui.inputText("##customAngleInput", angStr, imgui.flag.ImGuiInputTextFlags.CharsDecimal)) {
                    try {
                        state.setCustomAngleDeg(Double.parseDouble(angStr.get().trim()));
                    } catch (NumberFormatException ignored) {}
                }
                TooltipUtil.onHover("Target facing angle in degrees to maximize distance towards (e.g. 45.0° for diagonal).");

                ImGui.sameLine(0, spacing);
                if (ImGui.button("P##setPlayerFacing", btnW, btnW)) {
                    state.setCustomAngleDeg(playerYawSupplier.getAsDouble());
                }
                TooltipUtil.onHover("Set target angle to player's current facing yaw.");

                ImGui.endGroup();
                Controls.popInputFrameHeight();
            }

            ImGui.spacing();
            if (Controls.checkbox("Custom angle##customAngleToggle", state.isCustomAngle())) {
                state.setCustomAngle(!state.isCustomAngle());
            }
            TooltipUtil.onHover("Optimize trajectory distance towards a custom facing angle instead of a fixed X/Z axis.\n"
                    + "NOTE: The fast closed-form solver is position-axis only; custom angle solves rely on the search optimizer.\n"
                    + "Using 'Optimize' effort is recommended for maximum reach.");

            ImGui.spacing();
            boolean fastTier = state.getEffort() == AngleSolverState.Effort.FAST;
            boolean optimizeTier = state.getEffort() == AngleSolverState.Effort.THOROUGH;
            boolean forced = fastTier || optimizeTier;
            boolean shownChecked = fastTier || (!optimizeTier && state.isStopOnFeasible());
            if (forced) ImGui.beginDisabled(true);
            if (Controls.checkbox("Stop on first feasible", shownChecked) && !forced) {
                state.setStopOnFeasible(!state.isStopOnFeasible());
            }
            if (forced) ImGui.endDisabled();
            TooltipUtil.onHover(STOP_ON_FEASIBLE_TIP);

            if (Controls.checkbox("Smooth (TAS)", state.getSmoothLambda() > 0.0)) {
                state.setSmoothLambda(state.getSmoothLambda() > 0.0 ? 0.0 : AngleSolverState.TASER_SMOOTH_LAMBDA);
            }
            TooltipUtil.onHover(SMOOTH_TAS_TIP);

            String legalWall = engine.legalGoalWallLabel();
            if (legalWall != null || state.isLegalMode()) {
                if (Controls.checkbox("Legal record mode", state.isLegalMode())) {
                    state.setLegalMode(!state.isLegalMode());
                }
                TooltipUtil.onHover(LEGAL_MODE_TIP + (legalWall != null ? " Goal wall: " + legalWall + "." : ""));
            }
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

        SolveResult panel = engine.isSolving() ? engine.liveBestResult() : state.getResult();
        trackObjectiveImprovement(panel);
        if (panel != null) {
            renderResultPanel(io, panel, scale);
            ThemeManager.sectionSpacing();
        }

        renderActions();
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
                float pad = 2f * ImGui.getStyle().getFramePadding().x;
                float gap = ImGui.getStyle().getItemSpacing().x;
                float buttons = ImGui.calcTextSize("Reload").x + pad
                        + ImGui.calcTextSize("Duplicate").x + pad
                        + ImGui.calcTextSize("Open editor").x + pad + 2f * gap;
                w = Math.max(w, buttons);
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
            if (engine.isSolving()) col[4] = Math.max(col[4], ImGui.calcTextSize(improvementText(improveFlashDelta)).x);
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

        if (state.getEffort() == AngleSolverState.Effort.THOROUGH) {
            optimizeSecondsBuf[0] = state.getOptimizeSeconds();
            if (sliderIntRow("Time budget", "##optimizeSeconds", optimizeSecondsBuf,
                    AngleSolverState.MIN_OPTIMIZE_SECONDS, AngleSolverState.MAX_OPTIMIZE_SECONDS,
                    "%d s", labelW, OPTIMIZE_TIME_TIP)) {
                state.setOptimizeSeconds(optimizeSecondsBuf[0]);
            }
        }
        if (state.getEffort() == AngleSolverState.Effort.CUSTOM) renderPresetSection(labelW);
    }

    private static final String OPTIMIZE_TIME_TIP =
            "How long Optimize keeps improving the result. It launches fresh search batches, and on"
            + " multi-jump spans the exhaustive reach stages (seam sweep, pattern branch and bound, ILS),"
            + " until the time runs out, then returns the best byte-exact result found. Cutting it short"
            + " with Stop still returns the best found so far.";

    private static final String STOP_ON_FEASIBLE_TIP =
            "Returns the first solution that satisfies every constraint, instead of spending the rest of the"
            + " search hunting for the furthest-reaching one. Much faster on a jump you only need to land,"
            + " not to maximize. The reached value will usually be lower than a full solve's. The closed-form"
            + " path already short-circuits when it lands feasible, so simple jumps finish almost instantly."
            + " Fast effort always works this way and Optimize never does; the toggle applies to Custom.";

    private static final String SMOOTH_TAS_TIP =
            "Prefers smooth yaw paths among equally feasible solutions: search scoring trades a little"
            + " objective margin for steadier turn rates (less yaw jerk), including the turn out of the"
            + " tick before the solve. Feasibility is never traded; whether the jump lands is decided"
            + " exactly as without this. Best combined with Optimize effort when crafting a TAS; leave"
            + " off to purely verify or maximize a jump.";

    private static final String LEGAL_MODE_TIP =
            "Record hunting: drops the single tightest goal wall on the objective axis at the goal tick and"
            + " maximizes toward it while every other constraint stays hard. The result reports how far short"
            + " of the dropped wall the run lands (the legal shortfall). Available only while exactly one"
            + " qualifying goal wall exists.";

    private void renderPresetSection(float labelW) {
        if (graphStore == null) {
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("Graph presets unavailable.");
            ThemeManager.popTextColor();
            return;
        }
        if (presetNames == null) refreshPresets();

        String current = state.getGraphPresetName();
        int currentIdx = indexOfPreset(current);
        boolean missing = current != null && currentIdx < 0;
        String[] items = new String[presetNames.length + (missing ? 2 : 1)];
        items[0] = LEGACY_PRESET_ITEM;
        System.arraycopy(presetNames, 0, items, 1, presetNames.length);
        if (missing) items[items.length - 1] = current + " (missing)";
        int selected = current == null ? 0 : (missing ? items.length - 1 : currentIdx + 1);

        Controls.pushInputFrameHeight();
        ImGui.beginGroup();
        SolverWidgets.rowLabel("Preset", labelW);
        presetBuf.set(selected);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        if (Controls.combo("##graphPreset", presetBuf, items)) {
            int pick = presetBuf.get();
            if (pick == 0) {
                state.setGraphPresetName(null);
                state.setCustomGraph(null);
                presetError = null;
            } else if (pick <= presetNames.length) {
                selectPreset(presetNames[pick - 1]);
            }
        }
        ImGui.endGroup();
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(PRESET_TIP);

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Save as", labelW);
        float gap = ImGui.getStyle().getItemSpacing().x;
        float saveW = ImGui.calcTextSize("Save").x + 2f * ImGui.getStyle().getFramePadding().x;
        Controls.inputTextHint("##presetName", "preset name", presetNameInput,
                Math.max(60f, ImGui.getContentRegionAvail().x - saveW - gap));
        ImGui.sameLine();
        if (Controls.secondaryButton("Save")) savePresetAs();
        Controls.popInputFrameHeight();

        if (Controls.secondaryButton("Reload")) {
            refreshPresets();
            if (indexOfPreset(state.getGraphPresetName()) >= 0) selectPreset(state.getGraphPresetName());
        }
        TooltipUtil.onHover("Re-read the preset list and the selected preset from disk.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Duplicate")) duplicatePreset();
        TooltipUtil.onHover("Save a copy of the selected graph as a new preset.");
        ImGui.sameLine();
        if (graphEditor != null) {
            if (Controls.secondaryButton("Open editor")) {
                graphEditor.open(currentCustomGraph(),
                        state.getCustomGraph() != null ? state.getGraphPresetName() : null);
            }
            TooltipUtil.onHover("Edit the selected graph on a node canvas. Legacy budget opens as an"
                    + " unsaved draft; save it under a name to keep changes.");
        } else {
            Controls.disabledButton("Open editor");
        }

        if (presetError != null) {
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.textWrapped(presetError);
            ThemeManager.popTextColor();
        }
    }

    private static final String PRESET_TIP =
            "Which solver graph a Custom solve runs. Legacy budget rebuilds the graph from this save's"
            + " Custom knobs, matching the pre-preset behavior byte for byte. Presets are JSON files under"
            + " parkourcalculator/graphs/ in the game folder: save the current graph, hand-edit the file,"
            + " then Reload to pick up the changes. The selected preset name travels with the save file.";

    private void refreshPresets() {
        List<SaveInfo> infos = graphStore.list();
        String[] names = new String[infos.size()];
        for (int i = 0; i < infos.size(); i++) names[i] = infos.get(i).name;
        Arrays.sort(names);
        presetNames = names;
    }

    private int indexOfPreset(String name) {
        if (presetNames == null || name == null) return -1;
        for (int i = 0; i < presetNames.length; i++) {
            if (presetNames[i].equals(name)) return i;
        }
        return -1;
    }

    private void selectPreset(String name) {
        Result<SolverGraph> graph = GraphPresetIO.loadGraph(graphStore, name);
        if (graph.ok) {
            state.setGraphPresetName(name);
            state.setCustomGraph(graph.value);
            presetError = null;
        } else {
            presetError = graph.error;
        }
    }

    private SolverGraph currentCustomGraph() {
        SolverGraph graph = state.getCustomGraph();
        return graph != null ? graph : GraphFactory.legacyCustom(state);
    }

    private void savePresetAs() {
        String name = SaveIO.sanitize(presetNameInput.get());
        if (name == null) {
            presetError = "Invalid preset name. Use letters, numbers, dashes, or underscores.";
            return;
        }
        if (writePreset(name, currentCustomGraph())) presetNameInput.set("");
    }

    private void duplicatePreset() {
        String base = state.getGraphPresetName() != null ? state.getGraphPresetName() : "custom";
        String name = base + "-copy";
        int n = 2;
        while (graphStore.exists(name)) {
            name = base + "-copy-" + n++;
        }
        writePreset(name, currentCustomGraph());
    }

    boolean writePreset(String name, SolverGraph graph) {
        GraphPresetFile file = GraphPresetIO.fromGraph(graph);
        file.name = name;
        file.createdAt = SaveIO.nowIso8601();
        file.modVersion = graphStore.getModVersion();
        try {
            graphStore.write(name, GraphPresetIO.toJson(file));
        } catch (IOException e) {
            presetError = "Failed to write preset: " + e.getMessage();
            return false;
        }
        refreshPresets();
        selectPreset(name);
        return true;
    }


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

    public void setApplySurfaceState(Runnable action) {
        applySurfaceState = action != null ? action : () -> { };
    }

    private void renderActions() {
        if (engine.isSolving()) {
            renderSolvingIndicator();
            return;
        }
        if (Controls.secondaryButton("Apply state")) {
            applySurfaceState.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Capture each tick's surface state (ground + medium) from the simulation into the overrides, for the solve range (H).");
        }
        ImGui.sameLine();
        if (Controls.secondaryButton("Solve")) {
            yawsExpanded = false;
            detailsExpanded = false;
            solverExpanded = false;
            outcomesExpanded = true;
            engine.solve();
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
        Controls.cursorToRightAlignedButton("Cancel");
        if (Controls.secondaryButton("Cancel")) engine.stopAndUseBest();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Stop the search and keep the best solution found so far.");
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
        String notice = r.getNotice();
        int noticeLines = notice == null ? 0
                : wrappedLineEstimate(notice, ImGui.getContentRegionAvail().x - 2f * pad);
        List<SolveResult.Detail> details = detailRows(r);
        List<String> steps = solverSteps(r);
        int solverLines = steps.isEmpty() ? 0 : 1 + (solverExpanded ? steps.size() : 0);
        int detailLines = details.isEmpty() && steps.isEmpty() ? 0
                : 1 + (detailsExpanded ? details.size() + solverLines : 0);
        int outcomeLines = r.getOutcomes().isEmpty() ? 0 : 1 + (outcomesExpanded ? r.getOutcomes().size() : 0);
        int rows = 2 + detailLines + devLines + noticeLines + outcomeLines + 1 + (yawsExpanded ? r.getYaws().size() : 0);
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
        if (notice != null) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(notice);
            ThemeManager.popTextColor();
            TooltipUtil.onHover(DIRECTION_TIP);
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

    private static final String DIRECTION_TIP =
            "A delta-facing (dF) constraint pins the change in the player's facing between ticks, which the"
            + " deterministic direction-optimizer (a position-linear method) cannot represent, so it is"
            + " skipped and the general search optimizes the direction instead. This does not affect landing,"
            + " only whether the shown path is the exact directional optimum. dF constraints are rare;"
            + " ordinary keep-out walls are position constraints and are not affected.";

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
        if (engine.isSolving()) return "Solving · " + r.getMet() + "/" + r.getTotal() + " constraints met";
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


    private void trackObjectiveImprovement(SolveResult panel) {
        if (!engine.isSolving() || panel == null || !panel.hasObjective()) {
            improveTrackValue = Double.NaN;
            return;
        }
        double v = panel.getObjectiveValue();
        if (!Double.isNaN(improveTrackValue) && v != improveTrackValue) {
            boolean max = state.getGoal() == AngleSolverState.Goal.MAX;
            double delta = v - improveTrackValue;
            if (max ? delta > 0 : delta < 0) {
                improveFlashStart = ImGui.getTime();
                improveFlashDelta = Math.abs(delta);
            }
        }
        improveTrackValue = v;
    }

    private float improvementAlpha() {
        double t = ImGui.getTime() - improveFlashStart;
        if (t < 0 || t >= IMPROVE_FADE_SECS) return 0f;
        return (float) (1.0 - t / IMPROVE_FADE_SECS);
    }

    private static String improvementText(double delta) {
        return "+" + ConstraintText.fixedStat(delta);
    }

    private void renderOutcomes(SolveResult r, float scale) {
        if (r.getOutcomes().isEmpty()) return;
        outcomesExpanded = resultToggle("outcomestoggle", "Solved values (" + r.getOutcomes().size() + ")",
                outcomesExpanded, scale);
        if (!outcomesExpanded) return;
        ImGui.indent(DETAIL_INDENT * scale);
        // field | @ tick | relation | found (right) | margin (right, green): own columns so every part aligns vertically.
        if (ThemeManager.beginStandardFormTable("##sv_outcomes", 5)) {
            int idx = 0;
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
                float flashAlpha = idx == 0 && engine.isSolving() ? improvementAlpha() : 0f;
                if (flashAlpha > 0f) {
                    ThemeManager.pushTextColor(ThemeManager.okTintColor(flashAlpha));
                    textRightInCell(improvementText(improveFlashDelta));
                    ThemeManager.popTextColor();
                } else if (!o.margin.isEmpty()) {
                    ThemeManager.pushTextColor(o.met ? ThemeManager.okColor() : ThemeManager.dangerColor());
                    textRightInCell(o.margin);
                    ThemeManager.popTextColor();
                }
                idx++;
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

}
