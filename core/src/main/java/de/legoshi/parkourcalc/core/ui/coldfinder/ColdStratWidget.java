package de.legoshi.parkourcalc.core.ui.coldfinder;

import de.legoshi.parkourcalc.core.ColdStratController;
import de.legoshi.parkourcalc.core.ColdStratController.Job;
import de.legoshi.parkourcalc.core.ColdStratController.JumpInfo;
import de.legoshi.parkourcalc.core.ColdStratController.Request;
import de.legoshi.parkourcalc.core.ColdStratController.Row;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.JumpArcs;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.ProblemCompiler;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratDifficulty;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasure;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratMeasurements;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratProblem;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratVariants;
import de.legoshi.parkourcalc.core.ui.anglesolver.SolverWidgets;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ColdStratWidget {

    private static final String WINDOW_ID = "###strat_finder";
    private static final String TITLE = "Strat Finder";

    private static final int BUDGET_MIN = 250;
    private static final int BUDGET_MAX = 10000;
    private static final int BUDGET_DEFAULT = 2000;
    private static final int COLD_BUDGET_MIN = 5;
    private static final int COLD_BUDGET_MAX = 300;
    private static final int COLD_BUDGET_DEFAULT = 30;

    private static final String COL_STRAT = "Strat";
    private static final String COL_DIFF = "Difficulty";
    private static final String COL_SLACK = "Slack";

    private final ColdStratController controller;

    private final ImBoolean windowOpen = new ImBoolean(false);
    private boolean wasWindowOpen;

    private JumpInfo info;
    private Job job;
    private String lastConstraintSignature = "";
    private String problemSyncError;
    private final int[] budgetBuf = {BUDGET_DEFAULT};
    private final int[] coldBudgetBuf = {COLD_BUDGET_DEFAULT};
    private String selectedKey;
    private int appliedShiftEdge = -1;
    private int appliedShift;
    private int appliedPressIdx = -1;

    private boolean problemExpanded = true;
    private boolean advancedExpanded;
    private final Map<String, ImString> coordBufs = new HashMap<String, ImString>();

    public ColdStratWidget(ColdStratController controller) {
        this.controller = controller;
    }

    public void setWindowOpen(boolean open) {
        windowOpen.set(open);
    }

    public boolean isWindowOpen() {
        return windowOpen.get();
    }

    public void onFindHotkey() {
        if (job != null && job.isRunning()) {
            job.cancel();
            return;
        }
        if (problemValid()) {
            startShape();
        } else if (info != null && info.ready) {
            startRefine();
        }
    }

    public void renderWindow(float scale) {
        boolean open = windowOpen.get();
        if (wasWindowOpen && !open && job != null) {
            job.cancel();
        }
        wasWindowOpen = open;
        if (!open) {
            return;
        }
        ImGui.setNextWindowPos(120f * scale, 130f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(490f * scale, 620f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(400f * scale, 300f * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, windowOpen, ImGuiWindowFlags.NoCollapse);
        if (visible) {
            ThemeManager.drawModalTitle(TITLE);
        }
        ThemeManager.popHeaderChrome();
        if (visible) {
            renderBody(scale);
        }
        ImGui.end();
    }

    private void renderBody(float scale) {
        if (job == null || !job.isRunning()) {
            info = controller.inspect();
        }
        renderProblemSection(scale);
        ThemeManager.sectionSpacing();
        renderAdvancedSection(scale);
        ThemeManager.paddedSeparator();
        renderActionRow(scale);
        renderResult(scale);
        handleKeys();
    }

    private void renderProblemSection(float scale) {
        problemExpanded = sectionToggle("Jump", "problem", problemExpanded, scale);
        if (!problemExpanded) {
            return;
        }
        String sig = controller.constraintSignature();
        if (!sig.equals(lastConstraintSignature)) {
            lastConstraintSignature = sig;
            problemSyncError = controller.syncProblemFromConstraints();
        }
        if (problemSyncError != null) {
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.textWrapped(problemSyncError);
            ThemeManager.popTextColor();
            dimText("The jump reads itself from the recording's jump presses plus one footprint"
                    + " constraint per landing: select the landing row, look at the block, press B.");
            renderRecordingSummary();
            return;
        }
        StratProblem p = controller.problem();
        if (p.start == null || p.segments.isEmpty()) {
            dimText("No jump derived yet. Load a recording, then place one footprint constraint per"
                    + " landing: select the landing row, look at the block, press B.");
            renderRecordingSummary();
            return;
        }
        ImGui.spacing();
        for (int i = 0; i < p.segments.size(); i++) {
            renderSegmentEditor(p, i, scale);
        }
        renderProblemStatus();
        renderRecordingSummary();
    }

    private void renderSegmentEditor(StratProblem p, int idx, float scale) {
        StratProblem.Segment seg = p.segments.get(idx);
        ImGui.pushID("seg" + idx);
        ImGui.spacing();
        Fonts.pushBold();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Jump " + (idx + 1));
        ThemeManager.popTextColor();
        Fonts.popBold();

        StratProblem.Area land = seg.landings.isEmpty() ? null : seg.landings.get(0);
        if (land == null) {
            ImGui.popID();
            return;
        }
        renderAirTime(p, idx, land, scale);

        Controls.pushInputFrameHeight();
        float labelW = ImGui.calcTextSize("Ground").x + ThemeManager.SM * scale;
        SolverWidgets.rowLabel("Ground", labelW);
        ImInt lo = new ImInt(seg.groundLo);
        if (Controls.inputInt("##glo", lo, 100f * scale)) {
            seg.groundLo = Math.max(1, lo.get());
            seg.groundHi = Math.max(seg.groundLo, seg.groundHi);
            controller.problemEdited();
        }
        TooltipUtil.onHover("Fewest grounded ticks before this fire (runway or platform ticks).");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.text("to");
        ImGui.sameLine();
        ImInt hi = new ImInt(seg.groundHi);
        if (Controls.inputInt("##ghi", hi, 78f * scale)) {
            seg.groundHi = Math.max(seg.groundLo, hi.get());
            controller.problemEdited();
        }
        TooltipUtil.onHover("Most grounded ticks before this fire. Each count is its own schedule.");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.text("t");
        Controls.popInputFrameHeight();
        ImGui.popID();
    }

    private void renderAirTime(StratProblem p, int idx, StratProblem.Area land, float scale) {
        StratProblem.Area from = idx == 0 ? p.start
                : (p.segments.get(idx - 1).landings.isEmpty() ? null
                : p.segments.get(idx - 1).landings.get(0));
        if (from == null) {
            return;
        }
        double dy = land.top() - from.top();
        StratProblem.Segment seg = p.segments.get(idx);
        int d = seg.airTicks > 0 ? seg.airTicks
                : JumpArcs.duration(dy, JumpArcs.legacyThreshold(p.mcVersion));
        float labelW = ImGui.calcTextSize("Ground").x + ThemeManager.SM * scale;
        SolverWidgets.rowLabel("", labelW);
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(d + "t air" + (dy == 0.0 ? "" : String.format(Locale.ROOT, " (%+.1f)", dy)));
        ThemeManager.popTextColor();
        TooltipUtil.onHover("Press to landing, from your recording's timing. Timing variants"
                + " shift the whole jump; the air time stays.");
    }

    private void renderProblemStatus() {
        ProblemCompiler.Compilation comp = controller.compiledProblem();
        if (comp == null) {
            return;
        }
        if (comp.specs.isEmpty()) {
            warn(comp.notes.isEmpty() ? "No jumpable timing for this jump." : comp.notes.get(0));
            return;
        }
        int n = comp.specs.size();
        dimText(n + " timing" + (n == 1 ? "" : "s") + " to try"
                + (comp.notes.isEmpty() ? "" : ", " + comp.notes.get(0)));
        TooltipUtil.onHover("A timing is one concrete layout: how many grounded ticks before each"
                + " fire and which block each arc lands on. Air time follows from the block heights.");
    }

    private void renderRecordingSummary() {
        if (info == null || !info.loaded) {
            return;
        }
        ImGui.spacing();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(String.format(Locale.ROOT, "Recording: T%d-T%d, %d constraint%s%s",
                info.startTick + 1, info.landingTick + 1, info.constraints,
                info.constraints == 1 ? "" : "s",
                info.ready ? "" : ", not solvable yet"));
        ThemeManager.popTextColor();
        if (!info.ready && info.message != null) {
            warn(info.message);
        }
    }

    private void renderAdvancedSection(float scale) {
        advancedExpanded = sectionToggle("Advanced", "advanced", advancedExpanded, scale);
        if (!advancedExpanded) {
            return;
        }
        if (Controls.sliderInt("Block search", coldBudgetBuf, COLD_BUDGET_MIN, COLD_BUDGET_MAX, "%d s")) {
            coldBudgetBuf[0] = Math.max(COLD_BUDGET_MIN, Math.min(COLD_BUDGET_MAX, coldBudgetBuf[0]));
        }
        TooltipUtil.onHover("Total budget for the block search, split across the timings."
                + " A two-jump route wants 60 s or more to sweep everything.");
        if (Controls.sliderInt("Variant solve", budgetBuf, BUDGET_MIN, BUDGET_MAX, "%d ms")) {
            budgetBuf[0] = Math.max(BUDGET_MIN, Math.min(BUDGET_MAX, budgetBuf[0]));
        }
        TooltipUtil.onHover("Per-variant solve budget for the recording search.");
    }

    private boolean problemValid() {
        ProblemCompiler.Compilation comp = controller.compiledProblem();
        return comp != null && !comp.specs.isEmpty();
    }

    private void renderActionRow(float scale) {
        boolean running = job != null && job.isRunning();
        if (running) {
            if (Controls.secondaryButton("Cancel")) {
                job.cancel();
            }
            ImGui.sameLine();
            float h = ImGui.getFrameHeight();
            ImVec2 p = ImGui.getCursorScreenPos();
            SolverWidgets.spinner(ImGui.getWindowDrawList(), p.x + h * 0.5f, p.y + h * 0.5f,
                    h * 0.30f, 1.8f * scale, ThemeManager.accentColor(), job.elapsedSeconds());
            ImGui.dummy(h, h);
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(progressText());
            ThemeManager.popTextColor();
            return;
        }
        if (problemValid()) {
            if (Controls.primaryButton("Find strats")) {
                startShape();
            }
            TooltipUtil.onHover("Search the defined jump cold: every timing and key line from"
                    + " scratch, world collisions respected. Ranked easiest first. Hotkey: F.");
        } else {
            Controls.disabledButton("Find strats");
        }
        ImGui.sameLine();
        if (info != null && info.ready) {
            if (Controls.secondaryButton("Refine recording")) {
                startRefine();
            }
            TooltipUtil.onHover("Substitute variants of your loaded recording: shifted edges,"
                    + " reshaped lines, momentum families. Ranked easiest first.");
        } else {
            Controls.disabledButton("Refine recording");
        }
    }

    private String progressText() {
        Job j = job;
        if (j == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (j.total() > 0) {
            sb.append(j.done()).append(" / ").append(j.total());
        } else {
            sb.append("enumerating");
        }
        sb.append(", ").append(j.feasible()).append(" feasible");
        String cold = j.coldProgress();
        if (cold != null) {
            sb.append(", ").append(cold);
        }
        return sb.toString();
    }

    private void startShape() {
        launch(new Request(false, true, null, StratVariants.Filter.Shape.ANY, budgetBuf[0],
                coldBudgetBuf[0] * 1000L));
    }

    private void startRefine() {
        launch(new Request(true, false, null, StratVariants.Filter.Shape.ANY, budgetBuf[0],
                coldBudgetBuf[0] * 1000L));
    }

    private void launch(Request request) {
        if (job != null) {
            job.cancel();
        }
        selectedKey = null;
        clearShiftState();
        Job started = controller.start(request);
        if (started != null) {
            job = started;
        }
    }

    private void renderResult(float scale) {
        Job j = job;
        if (j == null) {
            return;
        }
        List<Row> items = j.items();
        if (j.isFinished()) {
            ThemeManager.sectionSpacing();
            renderBanner(j, items, scale);
        }
        if (!items.isEmpty()) {
            renderTable(items, scale);
            Row sel = selectedItem(items);
            if (sel != null) {
                renderLeniency(sel, scale);
                render3dView(sel, scale);
            }
        }
        if (controller.isTempActive()) {
            renderTempControls(selectedItem(items));
        }
    }

    private Row selectedItem(List<Row> items) {
        if (selectedKey == null) {
            return null;
        }
        for (Row it : items) {
            if (selectedKey.equals(it.key)) {
                return it;
            }
        }
        return null;
    }

    private void renderBanner(Job j, List<Row> items, float scale) {
        boolean noResult = items.isEmpty();
        int accent;
        int fill;
        int border;
        String header;
        String sub;
        if (noResult) {
            accent = ThemeManager.dangerColor();
            fill = ThemeManager.dangerTintColor(0.10f);
            border = ThemeManager.dangerTintColor(0.45f);
            header = "No strat found for this jump.";
            sub = j.notes().isEmpty()
                    ? "Widen the key presets or ground ranges, or raise the budget in Advanced."
                    : j.notes().get(0);
        } else {
            accent = ThemeManager.okColor();
            fill = ThemeManager.okTintColor(0.10f);
            border = ThemeManager.okTintColor(0.45f);
            int n = items.size();
            header = n + " strat" + (n == 1 ? "" : "s") + " solve" + (n == 1 ? "s" : "")
                    + " this jump, easiest first";
            sub = j.canaryFailed()
                    ? "Your current line did not re-solve within the budget; raise it in Advanced."
                    : null;
        }

        float pad = ThemeManager.SM * scale;
        float lineH = ImGui.getTextLineHeightWithSpacing();
        float availW = ImGui.getContentRegionAvail().x;
        int textLines = wrappedLines(header, availW - 2f * pad)
                + (sub != null ? wrappedLines(sub, availW - 2f * pad) : 0);
        float hgt = textLines * lineH + 2f * pad;

        ImGui.pushStyleColor(ImGuiCol.ChildBg, fill);
        ImGui.pushStyleColor(ImGuiCol.Border, border);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.beginChild("##sf_banner", availW, hgt, true);
        ThemeManager.pushTextColor(accent);
        Fonts.pushBold();
        ImGui.pushTextWrapPos(0f);
        ImGui.text(header);
        ImGui.popTextWrapPos();
        Fonts.popBold();
        ThemeManager.popTextColor();
        if (sub != null) {
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.pushTextWrapPos(0f);
            ImGui.text(sub);
            ImGui.popTextWrapPos();
            ThemeManager.popTextColor();
        }
        ImGui.endChild();
        ImGui.popStyleVar();
        ImGui.popStyleColor(2);
    }

    private void renderTable(List<Row> items, float scale) {
        float slackW = ImGui.calcTextSize(COL_SLACK).x;
        for (Row it : items) {
            slackW = Math.max(slackW, ImGui.calcTextSize(slackText(it)).x);
        }
        float rowH = ThemeManager.tableRowHeight();
        float contentH = rowH * (items.size() + 1.6f);
        float tableH = Math.max(rowH * (3f + 1.6f), Math.min(contentH, 300f * scale));
        float pip = 2f * 3f * scale + 5f * scale;

        ThemeManager.sectionSpacing();
        if (!ThemeManager.beginStandardClickableRowsTable("##sf_results", 3, 0, 0f, tableH)) {
            return;
        }
        ImGui.tableSetupScrollFreeze(0, 1);
        ImGui.tableSetupColumn(COL_STRAT, ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(COL_DIFF, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableNumericColumnWidth(COL_DIFF, ImGui.calcTextSize("-00.00").x + pip));
        ImGui.tableSetupColumn(COL_SLACK, ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableRightmostColumnWidth(COL_SLACK, slackW, ThemeManager.tableScrollbarSlack()));
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_STRAT);
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader(COL_DIFF);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_SLACK);

        for (int i = 0; i < items.size(); i++) {
            renderRow(items.get(i), i, rowH, scale);
        }
        ThemeManager.endStandardTable();
    }

    private void renderRow(Row item, int idx, float rowH, float scale) {
        ImGui.tableNextRow(0, rowH);
        ThemeManager.paintTableRowBg(idx);
        boolean selected = item.key.equals(selectedKey);

        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ImGui.alignTextToFramePadding();
        if (ImGui.selectable(item.label + "##sfrow" + idx, selected,
                ImGuiSelectableFlags.SpanAllColumns)) {
            applyItem(item);
        }
        TooltipUtil.onHover(rowTooltip(item));

        ImGui.tableSetColumnIndex(1);
        drawPip(diffColor(item.difficulty), rowH, scale);
        ImGui.alignTextToFramePadding();
        ImGui.text(Double.isNaN(item.difficulty) ? "-"
                : String.format(Locale.ROOT, "%.2f", item.difficulty));

        ImGui.tableSetColumnIndex(2);
        ImGui.alignTextToFramePadding();
        if (item.pressHi > item.pressLo) {
            ThemeManager.pushTextColor(ThemeManager.okColor());
        } else {
            int slack = worstSlack(item.measurements);
            ThemeManager.pushTextColor(item.measurements == null
                    ? ThemeManager.textDimColor() : slackColor(slack));
        }
        ImGui.text(slackText(item));
        ThemeManager.popTextColor();
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private static String rowTooltip(Row item) {
        StringBuilder sb = new StringBuilder();
        if (item.detail != null && !item.detail.isEmpty()) {
            sb.append(item.detail);
        }
        sb.append(sb.length() > 0 ? "\n" : "").append("Source: ")
                .append("recording".equals(item.origin) ? "variant of your recording" : "cold from the blocks");
        if (item.corpusEntries > 0) {
            sb.append('\n').append("Community corpus: ").append(item.corpusEntries)
                    .append(item.corpusEntries == 1 ? " entry." : " entries.");
            if (item.corpusExample != null && !item.corpusExample.isEmpty()) {
                sb.append('\n').append('"').append(trim(item.corpusExample, 140)).append('"');
            }
        }
        StratMeasurements m = item.measurements;
        if (m != null && !Double.isNaN(item.difficulty)) {
            sb.append('\n').append(String.format(Locale.ROOT,
                    "Difficulty %.2f: facing tolerance %.2f, timing demand %.2f",
                    item.difficulty, StratDifficulty.toleranceCore(m),
                    0.8 * StratDifficulty.effShiftDemandSum(m)));
            if (m.turnTicksJump > 0) {
                sb.append(String.format(Locale.ROOT, ", %d air turn tick%s",
                        m.turnTicksJump, m.turnTicksJump == 1 ? "" : "s"));
            }
            if (m.jumpAngle) {
                sb.append(", jump angle");
            }
            if (m.minMargin < StratDifficulty.TIGHT_MARGIN) {
                sb.append(", razor margin");
            }
        }
        if (!Double.isNaN(item.margin)) {
            sb.append('\n').append(String.format(Locale.ROOT, "Objective margin %.4f", item.margin));
        }
        return sb.toString();
    }

    private static String trim(String s, int max) {
        String flat = s.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max - 3) + "...";
    }

    private void applyItem(Row item) {
        selectedKey = item.key;
        clearShiftState();
        if (item.snapshotJson != null) {
            controller.apply(item.snapshotJson);
        }
    }

    private void clearShiftState() {
        appliedShiftEdge = -1;
        appliedShift = 0;
        appliedPressIdx = -1;
    }

    private void drawPip(int color, float rowH, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 cell = ImGui.getCursorScreenPos();
        float r = 3f * scale;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        dl.addCircleFilled(cell.x + r, cell.y - cellPadY + rowH * 0.5f, r, color, 12);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + 2f * r + 5f * scale);
    }

    private static int diffColor(double diff) {
        if (Double.isNaN(diff)) {
            return ThemeManager.textDimColor();
        }
        if (diff <= 0.5) {
            return ThemeManager.okColor();
        }
        if (diff <= 3.0) {
            return ThemeManager.warningColor();
        }
        return ThemeManager.dangerColor();
    }

    private static int worstSlack(StratMeasurements m) {
        if (m == null || m.shiftEdgeRow == null || m.shiftEdgeRow.length == 0) {
            return -1;
        }
        int worst = Integer.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
                continue;
            }
            any = true;
            worst = Math.min(worst, m.shiftLo[i] + m.shiftHi[i]);
        }
        return any ? worst : -1;
    }

    private static String slackText(Row item) {
        if (item.pressHi > item.pressLo) {
            return "T" + (item.pressLo + 1) + "-T" + (item.pressHi + 1);
        }
        if (item.measurements == null) {
            return "?";
        }
        int slack = worstSlack(item.measurements);
        if (slack < 0) {
            return "free";
        }
        if (slack >= StratMeasure.SHIFT_CAP_TICKS) {
            return StratMeasure.SHIFT_CAP_TICKS + "t+";
        }
        return slack + "t";
    }

    private static int slackColor(int slack) {
        if (slack < 0) {
            return ThemeManager.okColor();
        }
        if (slack == 0) {
            return ThemeManager.dangerColor();
        }
        if (slack == 1) {
            return ThemeManager.warningColor();
        }
        return ThemeManager.okColor();
    }

    private void renderLeniency(Row item, float scale) {
        ThemeManager.sectionSpacing();
        Fonts.pushBold();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Leniency");
        ThemeManager.popTextColor();
        Fonts.popBold();
        TooltipUtil.onHover("With the start position and the shown facing line fixed, how far each"
                + " input edge can move and still land. Red is frame perfect, yellow one tick,"
                + " green two or more. Click a shift to preview it.");
        if (item.pressHi > item.pressLo) {
            ThemeManager.pushTextColor(ThemeManager.okColor());
            ImGui.textWrapped("Press space anywhere T" + (item.pressLo + 1) + "-T" + (item.pressHi + 1)
                    + " from this exact position and facing, verified.");
            ThemeManager.popTextColor();
            if (item.windowSnapshots != null) {
                Controls.pushInputFrameHeight();
                for (int i = 0; i < item.windowSnapshots.length; i++) {
                    if (item.windowSnapshots[i] == null) {
                        continue;
                    }
                    if (i > 0) {
                        ImGui.sameLine();
                    }
                    String lbl = "T" + (item.pressLo + i + 1);
                    ImGui.pushID("press" + i);
                    boolean active = appliedPressIdx == i;
                    boolean clicked = active ? Controls.primaryButton(lbl)
                            : Controls.secondaryButton(lbl);
                    if (clicked) {
                        appliedShiftEdge = -1;
                        appliedShift = 0;
                        appliedPressIdx = i;
                        controller.apply(item.windowSnapshots[i]);
                    }
                    TooltipUtil.onHover("Preview this strat pressing space on " + lbl
                            + ", same position and facing.");
                    ImGui.popID();
                }
                Controls.popInputFrameHeight();
            }
        }
        StratMeasurements m = item.measurements;
        if (m == null) {
            dimText("No measurement for this strat.");
            return;
        }
        renderLeniencyStrip(item, m, scale);
        renderLeniencyEdges(item, m, scale);
    }

    private void renderLeniencyStrip(Row item, StratMeasurements m, float scale) {
        int n = m.numTicks;
        if (n <= 0) {
            return;
        }
        float w = ImGui.getContentRegionAvail().x;
        float h = 22f * scale;
        ImVec2 org = ImGui.getCursorScreenPos();
        ImGui.dummy(w, h);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(org.x, org.y, org.x + w, org.y + h, ThemeManager.panelColor(), 3f * scale);
        float per = w / n;
        if (per >= 5f * scale) {
            for (int k = 1; k < n; k++) {
                float x = org.x + per * k;
                dl.addLine(x, org.y + h * 0.72f, x, org.y + h, ThemeManager.borderColor(), 1f);
            }
        }
        float fx = org.x + per * (m.takeoffTick + 0.5f);
        dl.addTriangleFilled(fx - 3f * scale, org.y + h, fx + 3f * scale, org.y + h,
                fx, org.y + h - 5f * scale, ThemeManager.lockedColor());
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            int rel = m.shiftEdgeRow[i] - item.startTick;
            if (rel < 0 || rel > n) {
                continue;
            }
            float x = org.x + per * rel;
            int col = m.shiftLoFree[i] || m.shiftHiFree[i]
                    ? ThemeManager.okColor() : slackColor(m.shiftLo[i] + m.shiftHi[i]);
            dl.addRectFilled(x - 1.5f * scale, org.y + 2f * scale, x + 1.5f * scale,
                    org.y + h * 0.66f, col, 1f * scale);
        }
    }

    private void renderLeniencyEdges(Row item, StratMeasurements m, float scale) {
        Controls.pushInputFrameHeight();
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            int row = m.shiftEdgeRow[i];
            boolean free = m.shiftLoFree[i] || m.shiftHiFree[i];
            int col = free ? ThemeManager.okColor() : slackColor(m.shiftLo[i] + m.shiftHi[i]);
            ImDrawList dl = ImGui.getWindowDrawList();
            ImVec2 p = ImGui.getCursorScreenPos();
            float sq = 7f * scale;
            float fh = ImGui.getFrameHeight();
            dl.addRectFilled(p.x, p.y + (fh - sq) * 0.5f, p.x + sq, p.y + (fh + sq) * 0.5f, col, 1f * scale);
            ImGui.dummy(sq, fh);
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.text("T" + (row + 1) + " " + edgeWords(m.shiftEdgeKeys[i]));
            ImGui.sameLine();
            ImGui.pushID(i);
            if (free) {
                ThemeManager.pushTextColor(ThemeManager.textDimColor());
                ImGui.alignTextToFramePadding();
                ImGui.text("free");
                ThemeManager.popTextColor();
                TooltipUtil.onHover("This edge can move anywhere within the probe cap without"
                        + " breaking the jump.");
            } else {
                float wrapX = ImGui.getCursorPosX() + ImGui.getContentRegionAvail().x;
                for (int s = -m.shiftLo[i]; s <= m.shiftHi[i]; s++) {
                    if (s == 0) {
                        continue;
                    }
                    boolean active = appliedShiftEdge == row && appliedShift == s;
                    String chip = (s > 0 ? "+" : "") + s;
                    float chipW = ImGui.calcTextSize(chip).x + 2f * ImGui.getStyle().getFramePadding().x;
                    if (ImGui.getCursorPosX() + chipW > wrapX) {
                        ImGui.newLine();
                        ImGui.setCursorPosX(ImGui.getCursorPosX() + sq + 6f * scale);
                    }
                    boolean clicked = active ? Controls.primaryButton(chip)
                            : Controls.secondaryButton(chip);
                    if (clicked) {
                        if (controller.applyShifted(item.snapshotJson, row, s)) {
                            appliedShiftEdge = row;
                            appliedShift = s;
                        }
                    }
                    TooltipUtil.onHover("Move this edge by " + s + " tick" + (Math.abs(s) == 1 ? "" : "s")
                            + " and preview it. Still lands, byte-exact.");
                    ImGui.sameLine();
                }
                if (m.shiftLo[i] + m.shiftHi[i] == 0) {
                    ThemeManager.pushTextColor(ThemeManager.dangerColor());
                    ImGui.alignTextToFramePadding();
                    ImGui.text("frame perfect");
                    ThemeManager.popTextColor();
                } else if (appliedShiftEdge == row && appliedShift != 0) {
                    if (Controls.secondaryButton("reset")) {
                        if (controller.applyShifted(item.snapshotJson, row, 0)) {
                            appliedShiftEdge = -1;
                            appliedShift = 0;
                        }
                    }
                    TooltipUtil.onHover("Back to the unshifted strat.");
                } else {
                    ImGui.newLine();
                }
            }
            ImGui.popID();
        }
        Controls.popInputFrameHeight();
    }

    private static String edgeWords(String keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < keys.length()) {
            char sign = keys.charAt(i);
            int j = i + 1;
            while (j < keys.length() && keys.charAt(j) != '+' && keys.charAt(j) != '-') {
                j++;
            }
            String key = keys.substring(i + 1, j);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(sign == '+' ? "press " : "release ").append(keyWord(key));
            i = j;
        }
        return sb.toString();
    }

    private static String keyWord(String key) {
        if ("SPRINT".equals(key) || "SNEAK".equals(key) || "JUMP".equals(key)) {
            return key.toLowerCase(Locale.ROOT);
        }
        return key;
    }

    private void render3dView(Row item, float scale) {
        ForwardPath path = item.path;
        if (path == null || path.posX == null || path.posX.length < 2) {
            return;
        }
        ThemeManager.sectionSpacing();
        if (!ImGui.collapsingHeader("3D view")) {
            return;
        }

        int n = path.posX.length;
        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            cx += path.posX[i];
            cy += path.posY[i];
            cz += path.posZ[i];
        }
        cx /= n;
        cy /= n;
        cz /= n;

        double az = ImGui.getTime() * 0.6;
        double sinA = Math.sin(az);
        double cosA = Math.cos(az);
        float[] sx = new float[n];
        float[] sy = new float[n];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double x = path.posX[i] - cx;
            double y = path.posY[i] - cy;
            double z = path.posZ[i] - cz;
            double rx = x * cosA - z * sinA;
            double rz = x * sinA + z * cosA;
            sx[i] = (float) rx;
            sy[i] = (float) (rz * 0.5 - y);
            minX = Math.min(minX, sx[i]);
            maxX = Math.max(maxX, sx[i]);
            minY = Math.min(minY, sy[i]);
            maxY = Math.max(maxY, sy[i]);
        }

        float w = Math.max(160f * scale, ImGui.getContentRegionAvail().x);
        float h = Math.max(150f * scale, Math.min(260f * scale, w * 0.55f));
        ImVec2 org = ImGui.getCursorScreenPos();
        ImGui.dummy(w, h);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(org.x, org.y, org.x + w, org.y + h, ThemeManager.panelColor(), 4f * scale);

        float pad = 16f * scale;
        float spanX = Math.max(1e-3f, maxX - minX);
        float spanY = Math.max(1e-3f, maxY - minY);
        float sc = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
        float offX = org.x + pad - minX * sc + ((w - 2 * pad) - spanX * sc) * 0.5f;
        float offY = org.y + pad - minY * sc + ((h - 2 * pad) - spanY * sc) * 0.5f;

        int takeoffIdx = item.measurements != null ? item.measurements.takeoffTick + 1 : -1;
        for (int i = 1; i < n; i++) {
            float ax = offX + sx[i - 1] * sc;
            float ay = offY + sy[i - 1] * sc;
            float bx = offX + sx[i] * sc;
            float by = offY + sy[i] * sc;
            dl.addLine(ax, ay, bx, by, ThemeManager.accentColor(), 2.0f * scale);
        }
        dl.addCircleFilled(offX + sx[0] * sc, offY + sy[0] * sc, 3.5f * scale,
                ThemeManager.lockedColor(), 8);
        dl.addCircleFilled(offX + sx[n - 1] * sc, offY + sy[n - 1] * sc, 3.5f * scale,
                ThemeManager.okColor(), 8);
        if (takeoffIdx > 0 && takeoffIdx < n) {
            dl.addCircleFilled(offX + sx[takeoffIdx] * sc, offY + sy[takeoffIdx] * sc, 3.0f * scale,
                    ThemeManager.warningColor(), 8);
        }
        ImGui.textDisabled("auto-rotating, start blue, fire yellow, landing green");
    }

    private void renderTempControls(Row applied) {
        ThemeManager.sectionSpacing();
        warn("Strat applied as a temp trajectory. Auto-save is paused.");
        if (applied != null && appliedShift == 0) {
            boolean altPress = appliedPressIdx >= 0 && applied.pressHi > applied.pressLo
                    && appliedPressIdx != (applied.pressLo + applied.pressHi) / 2 - applied.pressLo;
            int div = altPress ? -1 : controller.divergenceTick(applied);
            if (div >= 0) {
                ThemeManager.pushTextColor(ThemeManager.dangerColor());
                ImGui.textWrapped("The in-game simulation leaves this line at tick " + div
                        + ", it likely hits a block there. Treat this strat as broken.");
                ThemeManager.popTextColor();
            }
        }
        if (Controls.secondaryButton("Reapply original")) {
            controller.reapplyOriginal();
            selectedKey = null;
            clearShiftState();
            return;
        }
        TooltipUtil.onHover("Discard the preview and restore your loaded inputs. Hotkey: Esc.");
        ImGui.sameLine();
        if (Controls.primaryButton("Keep")) {
            controller.keep();
        }
        TooltipUtil.onHover("Commit the preview as the new baseline; auto-save resumes. Hotkey: Enter.");
    }

    private void handleKeys() {
        if (!ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows) || ImGui.getIO().getWantTextInput()) {
            return;
        }
        Job j = job;
        if (j == null) {
            return;
        }
        boolean temp = controller.isTempActive();
        if (temp && (keyPressed(ImGuiKey.Escape) || keyPressed(ImGuiKey.Backspace))) {
            controller.reapplyOriginal();
            selectedKey = null;
            clearShiftState();
            return;
        }
        List<Row> items = j.items();
        if (items.isEmpty()) {
            return;
        }
        if (temp && keyPressed(ImGuiKey.Enter)) {
            controller.keep();
            return;
        }
        boolean down = keyPressed(ImGuiKey.DownArrow);
        boolean up = keyPressed(ImGuiKey.UpArrow);
        if (!down && !up) {
            return;
        }
        int current = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(selectedKey)) {
                current = i;
                break;
            }
        }
        int next;
        if (current < 0) {
            next = down ? 0 : items.size() - 1;
        } else {
            next = Math.max(0, Math.min(items.size() - 1, current + (down ? 1 : -1)));
        }
        applyItem(items.get(next));
    }

    private static boolean keyPressed(int key) {
        return ImGui.isKeyPressed(ImGui.getKeyIndex(key), false);
    }

    private static int wrappedLines(String s, float width) {
        if (width <= 0f) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(ImGui.calcTextSize(s).x / width));
    }

    private boolean sectionToggle(String title, String id, boolean expanded, float scale) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton("##" + id + "_toggle", ImGui.getContentRegionAvail().x, rowH)) {
            expanded = !expanded;
        }
        int col = ImGui.isItemHovered() ? ThemeManager.textColor() : ThemeManager.textDimColor();
        float cy = origin.y + rowH * 0.5f;
        if (expanded) {
            SolverWidgets.triangleDown(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        } else {
            SolverWidgets.triangleRight(dl, origin.x + 4f * scale, cy, 3.3f * scale, col);
        }
        dl.addText(origin.x + 13f * scale, origin.y, col, title);
        if (expanded) {
            ThemeManager.bottomPaddedSeparator();
        }
        return expanded;
    }

    private static void warn(String msg) {
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.pushTextWrapPos(0f);
        ImGui.text(msg);
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();
    }

    private static void dimText(String msg) {
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.pushTextWrapPos(0f);
        ImGui.text(msg);
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();
    }
}
