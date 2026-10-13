package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BudgetText;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphFactory;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetFile;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunState;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeStatus;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksControls;
import de.legoshi.parkourcalc.core.anglesolver.runticks.RunTicksSettings;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
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
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
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
 * The floating Angle Solver window: the solve span, the goal, the default per-tick state, the time
 * budget, the Solve action, and the result panel. Toggled from View > Angle Solver.
 */
public final class AngleSolverWindow implements RenderInterface {

    private static final String WINDOW_ID = "###angle_solver";
    private static final String TITLE = "Angle Solver";
    private static final String TELEPORT_SOLVE_BLOCKED =
            "Can't solve: the solve range crosses a tick with a teleport, which overrides that tick's movement.";

    private static final String[] GOALS = {"+X", "-X", "+Z", "-Z", "Angle"};
    private static final String[] GOAL_TIPS = {
            "Maximize X at the goal tick.",
            "Minimize X at the goal tick.",
            "Maximize Z at the goal tick.",
            "Minimize Z at the goal tick.",
            "Optimize toward a custom facing angle instead of an axis. Best with a time budget above zero."};
    private static final int GOAL_ANGLE = 4;
    private static final String[] INPUTS = {"Keep", "Force 45"};
    private static final String[] SPRINTS = {"Always", "Derive"};
    private static final String[] SPRINT_TIPS = {null,
            "WARNING: derives each tick's sprint state from the current recorded path.\n"
                    + "The path is the source of truth here: a recording that hits a wall loses\n"
                    + "sprint from that tick on, and the solve inherits it, so a broken path can\n"
                    + "make a solvable segment report no solution until the route is re-recorded."};
    private static final String[] ANGLE_TYPES = {"Pos", "Mot"};
    private static final String[] ANGLE_TYPE_TIPS = {
            "Position: maximize horizontal displacement along the target angle.",
            "Motion: maximize horizontal velocity along the target angle at the goal tick."};
    private static final String FAST_PRESET_ITEM = BuiltinGraphs.FAST_PRESET;
    private static final String OPTIMIZE_PRESET_ITEM = BuiltinGraphs.OPTIMIZE_PRESET;
    private static final String MULTI_START_PRESET_ITEM = BuiltinGraphs.MULTI_START_PRESET;
    private static final int BUILTIN_PRESET_COUNT = 3;

    private static final String[] FORM_LABELS = {"Ticks", "Goal", "Angle", "Inputs", "Sprint", "Budget"};
    private static final String[] RUN_TICKS_LABELS =
            {"Max ticks", "Step timeout", "Step growth", "Safety factor", "Safety margin", "Smooth give-back"};

    /** Unscaled; lines the details table up under the toggle title and sets it off from the solved values. */
    private static final float DETAIL_INDENT = 13f;
    private static final float OPTION_INDENT = 26f;

    private static final int LONG_SPAN_WARN_TICKS = 100;
    private static final String RUNNING = "Running";
    private static final double FINISH_FILL_SECS = 0.25;
    private static final double FINISH_FADE_SECS = 0.45;
    private boolean finishArmed;
    private double finishStart = -1e9;
    private String lastStage = "";
    private float lastFill = Float.NaN;

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
    private final ImInt runTicksMaxBuf = new ImInt();
    private final ImInt runTicksTimeoutBuf = new ImInt();
    private final ImInt runTicksGrowthBuf = new ImInt();
    private final ImFloat runTicksSafetyMultBuf = new ImFloat();
    private final ImInt runTicksSafetyMarginBuf = new ImInt();
    private final ImInt smoothGiveBackBuf = new ImInt();
    private final float[] budgetSliderBuf = new float[1];
    private final ImString budgetInput = new ImString(16);
    private int lastSyncedBudgetMs = -1;
    private boolean budgetFieldActive;
    private final ImInt presetBuf = new ImInt();
    private final ImString customAngleBuf = new ImString(32);
    private double lastSyncedCustomAngle = Double.NaN;
    private String[] presetNames;
    private String presetError;
    private Runnable applySurfaceState = () -> { };

    private boolean yawsExpanded;
    private boolean detailsExpanded;
    private boolean solverExpanded;
    private boolean outcomesExpanded = true;
    private boolean generalExpanded = true;
    private boolean moreExpanded;
    private RunTicksControls runTicks = RunTicksControls.NONE;
    private java.util.function.DoubleSupplier playerYawSupplier = () -> 0.0;
    private java.util.function.IntPredicate teleportAtRow = t -> false;

    public void setPlayerYawSupplier(java.util.function.DoubleSupplier supplier) {
        this.playerYawSupplier = supplier != null ? supplier : () -> 0.0;
    }

    public void setTeleportAtRow(java.util.function.IntPredicate predicate) {
        this.teleportAtRow = predicate != null ? predicate : t -> false;
    }

    public void setRunTicksControls(RunTicksControls controls) {
        this.runTicks = controls != null ? controls : RunTicksControls.NONE;
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
        float maxH = Math.max(200f * scale, io.getDisplaySizeY() - 90f - 40f * scale);
        ImGui.setNextWindowPos(px, 90f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(w, 0f, w, maxH);

        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize;

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

        generalExpanded = sectionToggle("General", "general", generalExpanded, scale);
        if (generalExpanded) renderGeneral(rowCount, labelW, scale);

        ThemeManager.sectionSpacing();
        renderActions();

        ThemeManager.sectionSpacing();
        moreExpanded = sectionToggle("More options", "more", moreExpanded, scale);
        if (moreExpanded) renderMoreOptions(scale);

        boolean solving = engine.isSolving();
        SolveResult panel = solving ? engine.liveBestResult() : state.getResult();
        trackObjectiveImprovement(panel);
        if (solving) {
            finishArmed = true;
        } else if (finishArmed && panel != null) {
            finishArmed = false;
            finishStart = ImGui.getTime();
        }
        if (panel != null) {
            ThemeManager.paddedSeparator();
            renderResultPanel(io, panel, scale);
        } else if (solving) {
            ThemeManager.paddedSeparator();
            renderRunningPanel(io, scale);
        }
    }

    private void renderGeneral(int rowCount, float labelW, float scale) {
        ticksRow(rowCount, labelW, scale);
        int span = state.getLandingTick() - state.getStartTick();
        if (span > LONG_SPAN_WARN_TICKS) longSpanWarning(span, scale);

        goalRow(labelW);
        if (state.isCustomAngle()) angleRow(labelW, scale);

        int im = segmentedRow("Inputs", "inputs", INPUTS, state.getDefaultInputs().ordinal(), labelW);
        if (im >= 0) state.setDefaultInputs(AngleSolverState.InputMode.values()[im]);

        int sp = segmentedRow("Sprint", "sprint", SPRINTS, SPRINT_TIPS, state.getDefaultSprint().ordinal(), labelW);
        if (sp >= 0) state.setDefaultSprint(AngleSolverState.SprintMode.values()[sp]);
        state.pruneRedundantOverrides();

        if (state.isCustomBudget()) presetRow(labelW, scale);
        else budgetRow(labelW, scale);
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
        return expanded;
    }

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    private float runTicksLabelWidth(float scale) {
        float max = 0f;
        for (String l : RUN_TICKS_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    /** Base width, widened so the expanded result tables fit without clipping; collapsed sections don't hold the window wide. */
    private float windowWidth(SolveResult r, float scale) {
        float base = 320f * scale;
        float labelW = labelColumnWidth(scale);
        float inner = formInner(labelW, scale);

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

    private float formInner(float labelW, float scale) {
        float w = segmentedRowWidth("Goal", GOALS, labelW);
        w = Math.max(w, segmentedRowWidth("Inputs", INPUTS, labelW));
        w = Math.max(w, segmentedRowWidth("Sprint", SPRINTS, labelW));
        w = Math.max(w, labelW + 150f * scale + budgetFieldWidth(scale));
        if (moreExpanded && state.getRunTicks().isEnabled()) {
            float boxW = ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacing().x;
            float checkColW = boxW + Math.max(ImGui.calcTextSize("Min").x, ImGui.calcTextSize("Auto").x);
            w = Math.max(w, OPTION_INDENT * scale + runTicksLabelWidth(scale) + 110f * scale + checkColW);
        }
        return w;
    }

    private float segmentedRowWidth(String label, String[] items, float labelW) {
        float lw = Math.max(labelW, ImGui.calcTextSize(label).x + ImGui.getStyle().getItemSpacing().x);
        return lw + SolverWidgets.segmentedMinWidth(items);
    }

    private float budgetFieldWidth(float scale) {
        return ImGui.calcTextSize("600 ms").x + 2f * ImGui.getStyle().getFramePadding().x + 10f * scale;
    }

    private float resultTablesWidth(SolveResult r, float scale, float cellPad) {
        float inner = 0f;
        if (outcomesExpanded) {
            float[] col = new float[5];
            for (SolveResult.Outcome o : visibleOutcomes(r)) {
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

    private void ticksRow(int rowCount, float labelW, float scale) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Ticks", labelW);
        float gap = ImGui.getStyle().getItemInnerSpacing().x;
        float toW = ImGui.calcTextSize("to").x;
        float fieldW = Math.max(40f * scale, (ImGui.getContentRegionAvail().x - toW - 2f * gap) * 0.5f);
        startTickBuf.set(state.getStartTick() + 1); // ticks are 0-based internally, shown 1-based
        if (Controls.inputInt("##startTick", startTickBuf, fieldW)) {
            state.setStartTick(Math.max(0, Math.min(rowCount - 1, startTickBuf.get() - 1)));
        }
        TooltipUtil.onHover("Start tick of the solve span.");
        ImGui.sameLine(0, gap);
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.alignTextToFramePadding();
        ImGui.text("to");
        ThemeManager.popTextColor();
        ImGui.sameLine(0, gap);
        goalTickBuf.set(state.getLandingTick() + 1);
        if (Controls.inputInt("##goalTick", goalTickBuf, fieldW)) {
            state.setLandingTick(Math.max(0, Math.min(rowCount - 1, goalTickBuf.get() - 1)));
        }
        TooltipUtil.onHover("Goal tick: the tick whose position the objective and the landing constraints judge.");
        Controls.popInputFrameHeight();
    }

    private void goalRow(float labelW) {
        int selected = state.isCustomAngle() ? GOAL_ANGLE
                : state.getAxis().ordinal() * 2 + state.getGoal().ordinal();
        int pick = segmentedRow("Goal", "goal", GOALS, GOAL_TIPS, selected, labelW);
        if (pick < 0) return;
        if (pick == GOAL_ANGLE) {
            state.setCustomAngle(true);
            return;
        }
        state.setCustomAngle(false);
        state.setAxis(AngleSolverState.Axis.values()[pick / 2]);
        state.setGoal(AngleSolverState.Goal.values()[pick % 2]);
    }

    private void angleRow(float labelW, float scale) {
        Controls.pushInputFrameHeight();
        ImGui.beginGroup();
        SolverWidgets.rowLabel("Angle", labelW);

        float btnW = ImGui.getFrameHeight();
        float spacing = ImGui.getStyle().getItemInnerSpacing().x;
        float segW = SolverWidgets.segmentedMinWidth(ANGLE_TYPES);
        float inputW = Math.max(30f, ImGui.getContentRegionAvail().x - btnW - segW - 2f * spacing);

        if (state.getCustomAngleDeg() != lastSyncedCustomAngle && !ImGui.isItemActive()) {
            customAngleBuf.set(formatAngle(state.getCustomAngleDeg()));
            lastSyncedCustomAngle = state.getCustomAngleDeg();
        }

        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputText("##customAngleInput", customAngleBuf, ImGuiInputTextFlags.CharsDecimal)) {
            try {
                String s = customAngleBuf.get().trim();
                if (!s.isEmpty() && !s.equals("-") && !s.equals(".")) {
                    state.setCustomAngleDeg(Double.parseDouble(s));
                    lastSyncedCustomAngle = state.getCustomAngleDeg();
                }
            } catch (NumberFormatException ignored) {
            }
        }
        TooltipUtil.onHover("Target facing angle in degrees to optimize towards (e.g. 45 for a diagonal).");

        ImGui.sameLine(0, spacing);
        if (ImGui.button("P##setPlayerFacing", btnW, btnW)) {
            double yaw = Angles.wrap(playerYawSupplier.getAsDouble());
            state.setCustomAngleDeg(yaw);
            customAngleBuf.set(formatAngle(yaw));
            lastSyncedCustomAngle = state.getCustomAngleDeg();
        }
        TooltipUtil.onHover("Set the target angle to the player's current facing.");

        ImGui.sameLine(0, spacing);
        int typePick = SolverWidgets.segmented("##customType", ANGLE_TYPES, ANGLE_TYPE_TIPS,
                state.getCustomAngleType().ordinal(), segW);
        if (typePick >= 0) state.setCustomAngleType(AngleSolverState.CustomAngleType.values()[typePick]);

        ImGui.endGroup();
        Controls.popInputFrameHeight();
    }

    private static String formatAngle(double deg) {
        return String.format(Locale.ROOT, "%.4f", deg).replaceAll("0+$", "").replaceAll("\\.$", "");
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

    private static final String BUDGET_TIP =
            "How long a solve may run. 0 returns the first feasible path in one quick pass. Below one"
            + " second the quick pass spends that much time on its seed stage. From one second on the"
            + " solver keeps improving the result until the budget runs out, then returns the best"
            + " byte-exact path found; Stop keeps the best found so far. The field takes any value,"
            + " in seconds or milliseconds: 250ms, 0.5s, 45.";

    private void budgetRow(float labelW, float scale) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Budget", labelW);
        float gap = ImGui.getStyle().getItemInnerSpacing().x;
        float fieldW = budgetFieldWidth(scale);
        float sliderW = Math.max(40f * scale, ImGui.getContentRegionAvail().x - fieldW - gap);

        int ms = state.getBudgetMs();
        float sliderMax = AngleSolverState.SLIDER_BUDGET_MS / 1000f;
        budgetSliderBuf[0] = Math.min(ms, AngleSolverState.SLIDER_BUDGET_MS) / 1000f;
        ImGui.setNextItemWidth(sliderW);
        if (Controls.sliderFloat("##budgetSlider", budgetSliderBuf, 0f, sliderMax, "%.1f s")) {
            state.setBudgetMs(Math.round(budgetSliderBuf[0] * 10f) * 100);
        }
        TooltipUtil.onHover(BUDGET_TIP);

        ImGui.sameLine(0, gap);
        if (!budgetFieldActive && state.getBudgetMs() != lastSyncedBudgetMs) {
            budgetInput.set(BudgetText.format(state.getBudgetMs()));
            lastSyncedBudgetMs = state.getBudgetMs();
        }
        ImGui.setNextItemWidth(fieldW);
        boolean entered = ImGui.inputText("##budgetField", budgetInput,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        budgetFieldActive = ImGui.isItemActive();
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            int parsed = BudgetText.parseMs(budgetInput.get());
            if (parsed >= 0) state.setBudgetMs(parsed);
            lastSyncedBudgetMs = -1;
        }
        TooltipUtil.onHover("Type a budget outside the slider range, in seconds or milliseconds.");
        Controls.popInputFrameHeight();
    }

    private static final String SMOOTH_TIP =
            "Smooths the turn of the solved path: fewer direction changes and a steadier turn rate. The jump"
            + " keeps landing; the smoothing may give back the share of the offset past the goal wall set"
            + " under More options. Stops early once nothing improves.";

    private static final String SMOOTH_DISABLED_TIP =
            "Smooths the turn of the solved path. Solve the range first.";

    private static final String SMOOTH_GIVE_BACK_TIP =
            "How much of the landing offset the Smooth button may trade for a smoother turn. The offset is"
            + " the margin past the goal wall at the goal tick, the + value in the result table. 100 %"
            + " lets it land anywhere past the wall, 0 % keeps the objective value at the goal tick."
            + " Every other tick may still move within the constraints, at any percentage: a ladder grab,"
            + " a wall hit or anything else the solver cannot see needs a constraint on its tick.";

    private static final String LEGAL_MODE_TIP =
            "Record hunting: drops the single tightest goal wall on the objective axis at the goal tick and"
            + " maximizes toward it while every other constraint stays hard. The result reports how far short"
            + " of the dropped wall the run lands (the legal shortfall). Available only while exactly one"
            + " qualifying goal wall exists.";

    private static final String CUSTOM_BUDGET_TIP =
            "Run a solver graph preset instead of the plain time budget. Pick a preset in the Budget row and"
            + " open it in the editor to change its stages and per-stage budgets.";

    private static final String RUN_TICKS_TIP =
            "Before solving, try adding up to N extra running ticks in front of each jump and keep the"
            + " combination with the best objective. Every combination is solved as a quick pass. The search"
            + " owns the grounded ticks in the range: it drops them first, so counts never stack and Max"
            + " ticks 0 strips the run ticks the range already has. If no combination lands the whole"
            + " range, the path you started from is put back unchanged. Put an RT constraint on a jump tick"
            + " to restrict how many ticks that jump may get.";

    private static final String RUN_TICKS_MAX_TIP =
            "Largest number of extra running ticks the search may hand out across all jumps together."
            + "\nIn Min mode, the search starts from this count upwards.";

    private static final String RUN_TICKS_MIN_TIP =
            "Search from the configured run ticks count upwards (n, n+1, n+2 ...) and stop at the first count that solves, so the result"
            + " uses the fewest run ticks instead of the best objective.";

    private static final String RUN_TICKS_TIMEOUT_TIP =
            "How long one combination may be solved before it is written off as infeasible.";

    private static final String RUN_TICKS_AUTO_TIP =
            "Derive the step timeout from measured solve times instead of the fixed value. Intended for"
            + " quick passes: a tight timeout writes off combinations that would have solved given longer,"
            + " so for a thorough search turn Auto off and set a generous fixed timeout.";

    private void renderMoreOptions(float scale) {
        String legalWall = engine.legalGoalWallLabel();
        if (legalWall != null || state.isLegalMode()) {
            if (Controls.checkbox("Legal record mode", state.isLegalMode())) {
                state.setLegalMode(!state.isLegalMode());
            }
            TooltipUtil.onHover(LEGAL_MODE_TIP + (legalWall != null ? " Goal wall: " + legalWall + "." : ""));
        }

        if (Controls.checkbox("Custom budget", state.isCustomBudget())) {
            state.setCustomBudget(!state.isCustomBudget());
            if (state.isCustomBudget()) refreshPresets();
        }
        TooltipUtil.onHover(CUSTOM_BUDGET_TIP);

        RunTicksSettings cfg = state.getRunTicks();
        if (Controls.checkbox("Run ticks", cfg.isEnabled())) cfg.setEnabled(!cfg.isEnabled());
        TooltipUtil.onHover(RUN_TICKS_TIP);
        if (cfg.isEnabled()) {
            ImGui.indent(OPTION_INDENT * scale);
            renderRunTicksRows(cfg, runTicksLabelWidth(scale), scale);
            ImGui.unindent(OPTION_INDENT * scale);
        }

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Smooth give-back", runTicksLabelWidth(scale));
        smoothGiveBackBuf.set((int) Math.round(state.getSmoothGiveBack() * 100.0));
        ImGui.setNextItemWidth(Math.max(40f * scale, ImGui.getContentRegionAvail().x));
        if (ImGui.inputInt("##smoothGiveBack", smoothGiveBackBuf, 5, 25)) {
            state.setSmoothGiveBack(Math.max(0, Math.min(100, smoothGiveBackBuf.get())) / 100.0);
        }
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(SMOOTH_GIVE_BACK_TIP);
    }

    private void renderRunTicksRows(RunTicksSettings cfg, float labelW, float scale) {
        float spacing = ImGui.getStyle().getItemInnerSpacing().x;
        float boxW = ImGui.getFrameHeight() + spacing;
        float checkColW = boxW + Math.max(ImGui.calcTextSize("Min").x, ImGui.calcTextSize("Auto").x);

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Max ticks", labelW);
        float inputW = Math.max(40f * scale, ImGui.getContentRegionAvail().x - checkColW - spacing);
        runTicksMaxBuf.set(cfg.getMaxTicks());
        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputInt("##runTicksMax", runTicksMaxBuf, 1, 5)) {
            cfg.setMaxTicks(Math.max(0, runTicksMaxBuf.get()));
        }
        TooltipUtil.onHover(RUN_TICKS_MAX_TIP);
        ImGui.sameLine(0, spacing);
        if (Controls.checkbox("Min##runTicksMinimize", cfg.isMinimize())) cfg.setMinimize(!cfg.isMinimize());
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(RUN_TICKS_MIN_TIP);

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Step timeout", labelW);
        boolean auto = cfg.isAdaptiveTimeout();
        runTicksTimeoutBuf.set(auto ? runTicks.liveTimeoutMs() : cfg.getTimeoutMs());
        if (auto) ImGui.beginDisabled(true);
        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputInt("##runTicksTimeout", runTicksTimeoutBuf, RunTicksSettings.TIMEOUT_NUDGE_MS, 100)) {
            cfg.setTimeoutMs(runTicksTimeoutBuf.get());
        }
        if (auto) ImGui.endDisabled();
        TooltipUtil.onHover(RUN_TICKS_TIMEOUT_TIP);
        ImGui.sameLine(0, spacing);
        if (Controls.checkbox("Auto##runTicksAuto", auto)) cfg.setAdaptiveTimeout(!auto);
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(RUN_TICKS_AUTO_TIP);
        if (!auto) return;

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Step growth", labelW);
        runTicksGrowthBuf.set(cfg.getAddPerJumpMs());
        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputInt("##runTicksGrowth", runTicksGrowthBuf, 10, 50)) {
            cfg.setAddPerJumpMs(Math.max(0, runTicksGrowthBuf.get()));
        }
        Controls.popInputFrameHeight();
        TooltipUtil.onHover("Added to the timeout for each jump deeper the search goes (+ms per jump).");

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Safety factor", labelW);
        runTicksSafetyMultBuf.set((float) cfg.getSafetyMult());
        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputFloat("##runTicksMult", runTicksSafetyMultBuf, 0.1f, 0.5f, "%.2f")) {
            float val = Math.max(1.0f, Math.round(runTicksSafetyMultBuf.get() * 100.0f) / 100.0f);
            cfg.setSafetyMult(val);
        }
        Controls.popInputFrameHeight();
        TooltipUtil.onHover("Multiplier applied to the measured solve time before it becomes the next timeout.");

        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Safety margin", labelW);
        runTicksSafetyMarginBuf.set(cfg.getSafetyMarginMs());
        ImGui.setNextItemWidth(inputW);
        if (ImGui.inputInt("##runTicksMargin", runTicksSafetyMarginBuf, 10, 50)) {
            cfg.setSafetyMarginMs(Math.max(0, runTicksSafetyMarginBuf.get()));
        }
        Controls.popInputFrameHeight();
        TooltipUtil.onHover("Flat buffer added on top of the derived timeout (ms).");
    }

    private static final String PRESET_TIP =
            "Which solver graph a custom budget runs. Fast and Optimize are the built-in graphs the plain"
            + " budget uses; they cannot be overwritten, but the editor saves a copy under a new name. User"
            + " presets are JSON files under parkourcalculator/graphs/ in the game folder; the list is"
            + " re-read every time it opens. The selected preset name travels with the save file.";

    private void presetRow(float labelW, float scale) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Budget", labelW);
        if (graphStore == null) {
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.alignTextToFramePadding();
            ImGui.text("Graph presets unavailable.");
            ThemeManager.popTextColor();
            Controls.popInputFrameHeight();
            return;
        }
        if (presetNames == null) refreshPresets();
        String current = state.getGraphPresetName();
        int diskIdx = indexOfPreset(current);
        boolean builtinOptimize = OPTIMIZE_PRESET_ITEM.equals(current);
        boolean builtinMulti = MULTI_START_PRESET_ITEM.equals(current);
        boolean missing = current != null && !FAST_PRESET_ITEM.equals(current) && !builtinOptimize
                && !builtinMulti && diskIdx < 0;
        String[] items = new String[BUILTIN_PRESET_COUNT + presetNames.length + (missing ? 1 : 0)];
        items[0] = FAST_PRESET_ITEM;
        items[1] = OPTIMIZE_PRESET_ITEM;
        items[2] = MULTI_START_PRESET_ITEM;
        System.arraycopy(presetNames, 0, items, BUILTIN_PRESET_COUNT, presetNames.length);
        int missingIdx = items.length - 1;
        if (missing) items[missingIdx] = current + " (missing)";
        int selected;
        if (builtinOptimize) selected = 1;
        else if (builtinMulti) selected = 2;
        else if (diskIdx >= 0) selected = BUILTIN_PRESET_COUNT + diskIdx;
        else if (missing) selected = missingIdx;
        else selected = 0;

        float gap = ImGui.getStyle().getItemInnerSpacing().x;
        float editW = ImGui.calcTextSize("Edit").x + 2f * ImGui.getStyle().getFramePadding().x + 4f * scale;
        presetBuf.set(selected);
        ImGui.setNextItemWidth(Math.max(60f * scale, ImGui.getContentRegionAvail().x - editW - gap));
        if (Controls.combo("##graphPreset", presetBuf, items)) {
            int pick = presetBuf.get();
            if (pick == 0) selectBuiltinPreset(FAST_PRESET_ITEM);
            else if (pick == 1) selectBuiltinPreset(OPTIMIZE_PRESET_ITEM);
            else if (pick == 2) selectBuiltinPreset(MULTI_START_PRESET_ITEM);
            else if (pick < BUILTIN_PRESET_COUNT + presetNames.length) selectPreset(presetNames[pick - BUILTIN_PRESET_COUNT]);
        }
        if (ImGui.isItemClicked()) refreshPresets();
        TooltipUtil.onHover(PRESET_TIP);
        ImGui.sameLine(0, gap);
        if (graphEditor != null) {
            if (Controls.secondaryButton("Edit")) {
                graphEditor.open(currentCustomGraph(),
                        state.getCustomGraph() != null ? state.getGraphPresetName() : null);
            }
            TooltipUtil.onHover("Edit the selected graph on a node canvas. A built-in opens as an"
                    + " unsaved draft; save it under a name to keep changes.");
        } else {
            Controls.disabledButton("Edit");
        }
        Controls.popInputFrameHeight();

        if (presetError != null) {
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.textWrapped(presetError);
            ThemeManager.popTextColor();
        }
    }

    private void refreshPresets() {
        if (graphStore == null) {
            presetNames = new String[0];
            return;
        }
        List<SaveInfo> infos = graphStore.list();
        List<String> names = new ArrayList<>();
        for (SaveInfo info : infos) {
            if (!BuiltinGraphs.isBuiltinPreset(info.name)) names.add(info.name);
        }
        Collections.sort(names);
        presetNames = names.toArray(new String[0]);
    }

    private void selectBuiltinPreset(String name) {
        state.setGraphPresetName(name);
        state.setCustomGraph(null);
        presetError = null;
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
        return GraphFactory.forState(state, AngleSolverState.Effort.CUSTOM);
    }

    boolean writePreset(String name, SolverGraph graph) {
        if (BuiltinGraphs.isBuiltinPreset(name)) {
            presetError = "\"" + name + "\" is a built-in preset. Choose another name.";
            return false;
        }
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

    public void setApplySurfaceState(Runnable action) {
        applySurfaceState = action != null ? action : () -> { };
    }

    private void renderActions() {
        boolean busy = engine.isSolving() || runTicks.isRunning();
        boolean teleportBlocked = !busy && solveRangeCrossesTeleport();
        if (busy) {
            if (Controls.secondaryButton("Stop")) {
                if (runTicks.isRunning()) runTicks.cancel();
                else engine.stopAndUseBest();
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Stop the search and keep the best solution found so far.");
        } else if (teleportBlocked) {
            Controls.disabledButton("Solve");
        } else if (Controls.primaryButton("Solve")) {
            runTicks.start();
        }
        ImGui.sameLine();
        if (!busy && engine.canSmooth()) {
            if (Controls.primaryButton("Smooth")) engine.smooth();
            TooltipUtil.onHover(SMOOTH_TIP + " Runs for up to " + seconds(engine.smoothBudgetNanos())
                    + " at " + Math.round(state.getSmoothGiveBack() * 100.0) + " % give-back.");
        } else {
            Controls.disabledButton("Smooth");
            if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) TooltipUtil.wrappedTooltip(SMOOTH_DISABLED_TIP);
        }
        ImGui.sameLine();
        if (Controls.secondaryButton("Apply state")) {
            applySurfaceState.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Capture each tick's surface state (ground + medium) from the simulation into the overrides, for the solve range (H).");
        }
        if (teleportBlocked) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(TELEPORT_SOLVE_BLOCKED);
            ThemeManager.popTextColor();
        }
    }

    private boolean solveRangeCrossesTeleport() {
        int lo = Math.min(state.getStartTick(), state.getLandingTick());
        int hi = Math.max(state.getStartTick(), state.getLandingTick());
        for (int t = lo; t <= hi; t++) {
            if (teleportAtRow.test(t)) return true;
        }
        return false;
    }

    private static final String CLOSEST_MISS_TIP =
            "The smallest worst-constraint violation any stage has reached so far, in blocks."
            + " A value that keeps shrinking means the search is converging; one that sits still means it is not.";

    private static final float BAR_ALPHA = 0.28f;

    private static final class RunView {
        final String stage;
        final float fill;
        final String clock;
        final String chain;

        RunView(String stage, float fill, String clock, String chain) {
            this.stage = stage;
            this.fill = fill;
            this.clock = clock;
            this.chain = chain;
        }
    }

    private RunView runView() {
        GraphRunState rs = engine.isGraphSolving() ? engine.graphRunState() : null;
        long now = System.nanoTime();
        String stage = "starting";
        float fill = Float.NaN;
        StringBuilder chain = new StringBuilder();
        if (rs != null) {
            List<GraphRunState.Step> steps = rs.steps();
            String active = rs.activeNodeId();
            NodeStatus running = active != null ? rs.status(active) : null;
            if (running != null) {
                stage = running.label;
                if (running.budgetNanos > 0) {
                    fill = (float) Math.min(1.0, (now - running.startNanos) / (double) running.budgetNanos);
                }
            }
            for (int i = 0; i < steps.size(); i++) {
                GraphRunState.Step s = steps.get(i);
                NodeStatus st = rs.status(s.nodeId);
                boolean live = i == steps.size() - 1 && s.taken == null && running != null;
                long nanos = live ? now - running.startNanos : st != null ? st.elapsedNanos : 0L;
                if (i > 0) chain.append('\n');
                chain.append(i + 1).append(". ").append(s.label).append("  ").append(seconds(nanos));
                if (live && running.budgetNanos > 0) chain.append(" / ").append(seconds(running.budgetNanos));
                if (live && running.visits > 1) chain.append("  visit ").append(running.visits);
                if (live && running.evals > 0) chain.append("  evals ").append(running.evals);
            }
        }
        double elapsed = engine.elapsedSeconds();
        String clock = seconds((long) (elapsed * 1.0e9));
        long overall = engine.liveOverallDeadlineNanos();
        if (overall > 0) {
            double total = elapsed + Math.max(0L, overall - now) / 1.0e9;
            clock += String.format(Locale.ROOT, " / %.0fs", total);
        }
        return new RunView(stage, fill, clock, chain.length() == 0 ? "starting" : chain.toString());
    }

    private void beginResultChild(ImGuiIO io, int bg, int border, int rows, float pad) {
        float lineH = ImGui.getTextLineHeightWithSpacing();
        float fullH = rows * lineH + 2f * pad;
        float h = Math.min(fullH, io.getDisplaySizeY() * 0.4f);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bg);
        ImGui.pushStyleColor(ImGuiCol.Border, border);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.beginChild("##solve_result", ImGui.getContentRegionAvail().x, h, true);
    }

    private static void endResultChild() {
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);
    }

    private void drawPanelHeader(String text, int textColor, float fill, float fillAlpha, boolean spinner,
                                 String clock, String tooltip, float pad, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 win = ImGui.getWindowPos();
        float winW = ImGui.getWindowSizeX();
        float lineH = ImGui.getTextLineHeight();
        float headerH = pad + lineH + pad * 0.5f;
        if (!Float.isNaN(fill) && fill > 0f && fillAlpha > 0f) {
            dl.pushClipRect(win.x, win.y, win.x + winW, win.y + headerH, false);
            dl.addRectFilled(win.x, win.y, win.x + winW * Math.min(1f, fill), win.y + headerH,
                    ThemeManager.accentTintColor(fillAlpha));
            dl.popClipRect();
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        float rowW = ImGui.getContentRegionAvail().x;
        if (spinner) {
            float r = lineH * 0.30f;
            SolverWidgets.spinner(dl, origin.x + r, origin.y + lineH * 0.5f, r, 1.8f * scale, textColor,
                    ImGui.getTime());
            ImGui.dummy(lineH * 0.75f, lineH);
            ImGui.sameLine();
        }
        ThemeManager.pushTextColor(textColor);
        Fonts.pushBold();
        ImGui.text(text);
        Fonts.popBold();
        if (clock != null) {
            ImGui.sameLine();
            textRightInCell(clock);
        }
        ThemeManager.popTextColor();
        if (tooltip != null && ImGui.isMouseHoveringRect(origin.x, origin.y, origin.x + rowW, origin.y + lineH)) {
            ImGui.setTooltip(tooltip);
        }
        ThemeManager.bottomPaddedSeparator();
    }

    private void renderRunningPanel(ImGuiIO io, float scale) {
        RunView rv = runView();
        lastStage = rv.stage;
        lastFill = rv.fill;
        double miss = engine.liveClosestMissViolation();
        boolean hasMiss = !Double.isInfinite(miss) && !Double.isNaN(miss);
        float pad = ThemeManager.SM * scale;
        beginResultChild(io, ThemeManager.accentTintColor(0.10f), ThemeManager.accentTintColor(0.45f),
                2 + (hasMiss ? 1 : 0), pad);
        drawPanelHeader(RUNNING + " · " + rv.stage, ThemeManager.textColor(), rv.fill, BAR_ALPHA, true,
                rv.clock, rv.chain, pad, scale);
        if (hasMiss && ThemeManager.beginStandardFormTable("##sv_miss", 2)) {
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("Closest miss");
            ThemeManager.popTextColor();
            TooltipUtil.onHover(CLOSEST_MISS_TIP);
            ImGui.tableNextColumn();
            textRightInCell(ConstraintText.fixedStat(miss));
            ThemeManager.endStandardFormTable();
        }
        endResultChild();
    }

    private static int lerpColor(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            int c = Math.round(ca + (cb - ca) * t);
            out |= (c & 0xFF) << shift;
        }
        return out;
    }

    private static float smoothstep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static String seconds(long nanos) {
        double s = nanos / 1.0e9;
        if (s == Math.rint(s)) return String.format(Locale.ROOT, "%.0fs", s);
        return String.format(Locale.ROOT, "%.1fs", s);
    }

    private void renderResultPanel(ImGuiIO io, SolveResult r, float scale) {
        String deviation = state.getApplyDeviation();
        // A diverged apply is not a clean solve: the whole panel goes warning, not yellow-on-green.
        boolean diverged = r.isSuccess() && deviation != null;
        boolean running = engine.isSolving();
        int finalAccent = !r.isSuccess() ? ThemeManager.dangerColor()
                : diverged ? ThemeManager.warningColor() : ThemeManager.okColor();
        int finalBg = !r.isSuccess() ? ThemeManager.dangerTintColor(0.10f)
                : diverged ? ThemeManager.warningTintColor(0.10f) : ThemeManager.okTintColor(0.10f);
        int finalBorder = !r.isSuccess() ? ThemeManager.dangerTintColor(0.45f)
                : diverged ? ThemeManager.warningTintColor(0.45f) : ThemeManager.okTintColor(0.45f);

        RunView rv = running ? runView() : null;
        if (rv != null) {
            lastStage = rv.stage;
            lastFill = rv.fill;
        }
        double ft = ImGui.getTime() - finishStart;
        boolean filling = !running && ft < FINISH_FILL_SECS;
        boolean fading = !running && !filling && ft < FINISH_FILL_SECS + FINISH_FADE_SECS;
        float mix = fading ? smoothstep((float) ((ft - FINISH_FILL_SECS) / FINISH_FADE_SECS)) : 1f;
        boolean runningLook = running || filling;
        int accent = runningLook ? ThemeManager.textColor() : lerpColor(ThemeManager.textColor(), finalAccent, mix);
        int bg = runningLook ? ThemeManager.accentTintColor(0.10f)
                : lerpColor(ThemeManager.accentTintColor(0.10f), finalBg, mix);
        int border = runningLook ? ThemeManager.accentTintColor(0.45f)
                : lerpColor(ThemeManager.accentTintColor(0.45f), finalBorder, mix);
        float fill;
        float fillAlpha;
        if (running) {
            fill = rv.fill;
            fillAlpha = BAR_ALPHA;
        } else if (filling) {
            float from = Float.isNaN(lastFill) ? 0f : lastFill;
            float u = (float) (ft / FINISH_FILL_SECS);
            fill = from + (1f - from) * (1f - (1f - u) * (1f - u));
            fillAlpha = BAR_ALPHA;
        } else {
            fill = 1f;
            fillAlpha = BAR_ALPHA * (1f - mix);
        }
        String header = runningLook ? RUNNING + " · " + lastStage : resultHeader(r);
        String clock = running ? rv.clock : null;
        String headerTip = running ? rv.chain : null;

        float pad = ThemeManager.SM * scale;
        int devLines = deviation == null ? 0
                : wrappedLineEstimate(deviation, ImGui.getContentRegionAvail().x - 2f * pad);
        String notice = r.getNotice();
        String noticeLabel = r.getNoticeLabel();
        if (noticeLabel == null && AngleSolverEngine.DF_DIRECTION_NOTICE.equals(notice)) {
            noticeLabel = AngleSolverEngine.DF_DIRECTION_LABEL;
        }
        int noticeLines = notice == null ? 0
                : noticeLabel != null ? 1
                : wrappedLineEstimate(notice, ImGui.getContentRegionAvail().x - 2f * pad);
        List<SolveResult.Detail> details = detailRows(r);
        List<String> steps = solverSteps(r);
        int solverLines = steps.isEmpty() ? 0 : 1 + (solverExpanded ? steps.size() : 0);
        int detailLines = details.isEmpty() && steps.isEmpty() ? 0
                : 1 + (detailsExpanded ? details.size() + solverLines : 0);
        List<SolveResult.Outcome> outcomes = visibleOutcomes(r);
        int outcomeLines = outcomes.isEmpty() ? 0 : 1 + (outcomesExpanded ? outcomes.size() : 0);
        int rows = 2 + (running ? 1 : 0) + detailLines + devLines + noticeLines + outcomeLines + 1
                + (yawsExpanded ? r.getYaws().size() : 0);
        beginResultChild(io, bg, border, rows, pad);
        drawPanelHeader(header, accent, fill, fillAlpha, runningLook, clock, headerTip, pad, scale);
        if (running) ImGui.text(r.getMet() + "/" + r.getTotal() + " constraints met");

        if (deviation != null) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(deviation);
            ThemeManager.popTextColor();
            String tip = deviationTip(state.getApplyDeviationKind());
            if (tip != null) TooltipUtil.onHover(tip);
        }
        if (notice != null && noticeLabel != null) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.text(noticeLabel);
            ThemeManager.popTextColor();
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("(?)");
            ThemeManager.popTextColor();
            TooltipUtil.onHover(notice);
        } else if (notice != null) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(notice);
            ThemeManager.popTextColor();
        }
        renderOutcomes(outcomes, scale);
        renderDetails(details, steps, scale);
        renderYawList(r, scale);
        endResultChild();
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
            if (state.isCustomAngle()) {
                String mode = state.getCustomAngleType() == AngleSolverState.CustomAngleType.MOTION ? "mot" : "pos";
                rows.add(new SolveResult.Detail("Objective",
                        String.format(Locale.ROOT, "%s yaw %.1f° = ", mode, state.getCustomAngleDeg())
                                + ConstraintText.fixedStat(r.getObjectiveValue())));
            } else {
                String goal = state.getGoal() == AngleSolverState.Goal.MAX ? "max" : "min";
                rows.add(new SolveResult.Detail("Objective",
                        goal + " " + state.getAxis().name() + " = " + ConstraintText.fixedStat(r.getObjectiveValue())));
            }
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
            boolean max = state.isCustomAngle() || state.getGoal() == AngleSolverState.Goal.MAX;
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

    private static final String HELD_FACING_RELATION = Constraint.Op.EQ.glyph + " " + ConstraintText.num(0.0);

    private static boolean isHeldFacing(SolveResult.Outcome o) {
        return o.met && Constraint.Field.DF.label.equals(o.field) && HELD_FACING_RELATION.equals(o.relation);
    }

    private static List<SolveResult.Outcome> visibleOutcomes(SolveResult r) {
        List<SolveResult.Outcome> shown = new ArrayList<>(r.getOutcomes().size());
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if (!isHeldFacing(o)) shown.add(o);
        }
        return shown;
    }

    private void renderOutcomes(List<SolveResult.Outcome> outcomes, float scale) {
        if (outcomes.isEmpty()) return;
        outcomesExpanded = resultToggle("outcomestoggle", "Solved values (" + outcomes.size() + ")",
                outcomesExpanded, scale);
        if (!outcomesExpanded) return;
        ImGui.indent(DETAIL_INDENT * scale);
        // field | @ tick | relation | found (right) | margin (right, green): own columns so every part aligns vertically.
        if (ThemeManager.beginStandardFormTable("##sv_outcomes", 5)) {
            int idx = 0;
            for (SolveResult.Outcome o : outcomes) {
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
