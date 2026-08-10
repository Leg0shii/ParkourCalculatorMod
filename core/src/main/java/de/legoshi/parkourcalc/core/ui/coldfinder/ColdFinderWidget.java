package de.legoshi.parkourcalc.core.ui.coldfinder;

import de.legoshi.parkourcalc.core.ColdFinderController.ColdFinderJob;
import de.legoshi.parkourcalc.core.ColdFinderController.ProblemView;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.ui.anglesolver.SolverWidgets;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ColdFinderWidget {

    private static final String WINDOW_ID = "###cold_finder";
    private static final String TITLE = "Cold Strat Finder";

    private static final int[] COAST_OPTS = {KeyLine.NONE, KeyLine.S, KeyLine.A, KeyLine.D, KeyLine.SA,
            KeyLine.SD, KeyLine.W, KeyLine.WA, KeyLine.WD};
    private static final int[] GLIDE_OPTS = {KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.A, KeyLine.D,
            KeyLine.S, KeyLine.SA, KeyLine.SD};
    private static final int[] PRESS_OPTS = {KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.S, KeyLine.SA, KeyLine.SD};
    private static final int[] BRAKE_OPTS = {KeyLine.NONE, KeyLine.S, KeyLine.A, KeyLine.D, KeyLine.SA,
            KeyLine.SD, KeyLine.W, KeyLine.WA, KeyLine.WD};
    private static final int[] ENGAGE_OPTS = {KeyLine.W, KeyLine.WA, KeyLine.WD};

    private static final String[] SPRINT_LABELS = {"Always", "At cycle", "Sweep"};
    private static final String[] SPRINT_TIPS = {
            "Sprint is held from the first tick that can run (the old always-sprint behavior).",
            "Sprint stays off until the start of the chosen cycle, then engages when a tick can run."
                    + " This is the delayed-sprint / winged-neo dimension.",
            "Try every cycle boundary (and never) as the sprint-engage point. Much larger search."};

    private static final String RUN_TIP =
            "Cross-product each press-cycle's configured key families, filter by momentum + tail"
                    + " reachability, then byte-exact certify the survivors across threads. The first line"
                    + " that certifies (viol 0) is applied to the input rows.";
    private static final String FIXED_TIP =
            "Pin this cycle to a single family: the first coast/glide/press in its alphabet with the glide"
                    + " length below. Use it on the momentum-build cycle so only the setup and launch cycles"
                    + " are searched.";

    private final Supplier<ProblemView> inspector;
    private final Function<ColdBeamSolver.Config, ColdFinderJob> starter;
    private final Consumer<ColdResult> applier;

    private final ImBoolean windowOpen = new ImBoolean(false);
    private boolean wasWindowOpen;

    private ProblemView view;
    private final List<CycleUi> cycles = new ArrayList<CycleUi>();

    private final boolean[] coastSel = new boolean[KeyLine.COMBO_COUNT];
    private final boolean[] glideSel = new boolean[KeyLine.COMBO_COUNT];
    private final boolean[] pressSel = new boolean[KeyLine.COMBO_COUNT];
    private final boolean[] brakeSel = new boolean[KeyLine.COMBO_COUNT];
    private final boolean[] engageSel = new boolean[KeyLine.COMBO_COUNT];

    private int sprintMode;
    private final ImInt sprintCycleBuf = new ImInt(0);

    private boolean alphabetExpanded;
    private boolean advancedExpanded;
    private final ImInt glideMaxBuf = new ImInt(2);
    private final ImInt beamCapBuf = new ImInt(200000);
    private final ImInt certifyCapBuf = new ImInt(100000);
    private final ImInt bucketBudgetBuf = new ImInt(ColdBeamSolver.DEFAULT_BUCKET_BUDGET);
    private final ImInt threadsBuf = new ImInt(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    private final ImInt budgetSecBuf = new ImInt(600);

    private ColdFinderJob job;
    private boolean applied;

    public ColdFinderWidget(Supplier<ProblemView> inspector,
                            Function<ColdBeamSolver.Config, ColdFinderJob> starter,
                            Consumer<ColdResult> applier) {
        this.inspector = inspector;
        this.starter = starter;
        this.applier = applier;
        defaultSelection(coastSel, ColdBeamSolver.DEFAULT_COASTS);
        defaultSelection(glideSel, ColdBeamSolver.DEFAULT_GLIDES);
        defaultSelection(pressSel, ColdBeamSolver.DEFAULT_PRESSES);
        defaultSelection(brakeSel, ColdBeamSolver.DEFAULT_BRAKES);
        defaultSelection(engageSel, ColdBeamSolver.DEFAULT_ENGAGES);
    }

    private static void defaultSelection(boolean[] sel, int[] combos) {
        for (int c : combos) sel[c] = true;
    }

    public void setWindowOpen(boolean open) {
        windowOpen.set(open);
    }

    public boolean isWindowOpen() {
        return windowOpen.get();
    }

    public void renderWindow(float scale) {
        boolean open = windowOpen.get();
        if (!wasWindowOpen && open) rescan();
        if (wasWindowOpen && !open && job != null) job.cancel();
        wasWindowOpen = open;
        if (!open) return;

        ImGui.setNextWindowPos(120f * scale, 140f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(460f * scale, 560f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(380f * scale, 300f * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, windowOpen, flags);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) renderBody(scale);
        ImGui.end();
    }

    private void rescan() {
        if (job != null) {
            job.cancel();
            job = null;
        }
        applied = false;
        view = inspector.get();
        cycles.clear();
        if (view != null && view.ok) {
            for (int i = 0; i < view.cycleCount(); i++) {
                cycles.add(new CycleUi());
            }
            sprintCycleBuf.set(Math.min(sprintCycleBuf.get(), Math.max(0, view.cycleCount() - 1)));
        }
    }

    private void renderBody(float scale) {
        if (Controls.secondaryButton("Rescan")) rescan();
        TooltipUtil.onHover("Re-read the solve segment and press-cycles from the current inputs and constraints.");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        if (view == null) {
            ImGui.textDisabled("No problem loaded.");
            return;
        }
        if (!view.ok) {
            ImGui.textDisabled("Not searchable");
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.pushTextWrapPos(0f);
            ImGui.text(view.error);
            ImGui.popTextWrapPos();
            ThemeManager.popTextColor();
            return;
        }

        ImGui.text(String.format(Locale.ROOT, "T%d-T%d · %d ticks · %d cycles",
                view.startTick + 1, view.landingTick + 1, view.numTicks, view.cycleCount()));

        ThemeManager.sectionSpacing();
        renderCycles(scale);

        ThemeManager.sectionSpacing();
        alphabetExpanded = section("Alphabet", alphabetExpanded, scale);
        if (alphabetExpanded) {
            ImGui.pushTextWrapPos(0f);
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("Key families each cycle may draw from (unless a cycle overrides them).");
            ThemeManager.popTextColor();
            ImGui.popTextWrapPos();
            comboRow("Coasts", COAST_OPTS, coastSel);
            comboRow("Glides", GLIDE_OPTS, glideSel);
            comboRow("Presses", PRESS_OPTS, pressSel);
            comboRow("Engages", ENGAGE_OPTS, engageSel);
            comboRow("Brakes", BRAKE_OPTS, brakeSel);
        }

        ThemeManager.sectionSpacing();
        renderSprint(scale);

        ThemeManager.sectionSpacing();
        advancedExpanded = section("Advanced", advancedExpanded, scale);
        if (advancedExpanded) renderAdvanced();

        ThemeManager.paddedSeparator();
        renderActions();
        renderProgress();
        renderResult();
        maybeApply();
    }

    private void renderCycles(float scale) {
        for (int i = 0; i < cycles.size(); i++) {
            CycleUi c = cycles.get(i);
            int a = view.cycleStartSeg[i];
            int b = view.cyclePressSeg[i];
            int L = b - a + 1;
            ImGui.text(String.format(Locale.ROOT, "Cycle %d · T%d-T%d · L=%d",
                    i + 1, view.startTick + a + 1, view.startTick + b + 1, L));
            ImGui.indent(ThemeManager.SM * scale);
            if (Controls.checkbox("Fixed##fx" + i, c.fixed)) c.fixed = !c.fixed;
            TooltipUtil.onHover(FIXED_TIP);
            ImGui.sameLine();
            glideBand(c, i);
            if (Controls.checkbox("Override alphabet##ov" + i, c.override)) c.override = !c.override;
            TooltipUtil.onHover("Search this cycle from its own key families instead of the global alphabet.");
            if (c.override) {
                comboRow("Coasts##c" + i, COAST_OPTS, c.coasts);
                comboRow("Glides##g" + i, GLIDE_OPTS, c.glides);
                comboRow("Presses##p" + i, PRESS_OPTS, c.presses);
                comboRow("Engages##e" + i, ENGAGE_OPTS, c.engages);
                comboRow("Brakes##b" + i, BRAKE_OPTS, c.brakes);
            }
            ImGui.unindent(ThemeManager.SM * scale);
        }
    }

    private void glideBand(CycleUi c, int i) {
        float w = 44f * ThemeManager.uiScale();
        ImGui.alignTextToFramePadding();
        ImGui.text("glide");
        ImGui.sameLine();
        ImGui.setNextItemWidth(w);
        if (ImGui.inputInt("##glo" + i, c.glideLo, 0, 0)) {
            c.glideLo.set(Math.max(1, c.glideLo.get()));
        }
        if (!c.fixed) {
            ImGui.sameLine();
            ImGui.text("-");
            ImGui.sameLine();
            ImGui.setNextItemWidth(w);
            if (ImGui.inputInt("##ghi" + i, c.glideHi, 0, 0)) {
                c.glideHi.set(Math.max(1, c.glideHi.get()));
            }
        }
        TooltipUtil.onHover("Glide length band for this cycle (fixed uses the low value as the pinned length).");
    }

    private void comboRow(String label, int[] opts, boolean[] sel) {
        String shown = label;
        int hash = label.indexOf('#');
        if (hash >= 0) shown = label.substring(0, hash);
        float labelW = 62f * ThemeManager.uiScale();
        SolverWidgets.rowLabel(shown, labelW);
        float startX = ImGui.getCursorPosX();
        float wrapX = startX + ImGui.getContentRegionAvail().x;
        for (int k = 0; k < opts.length; k++) {
            int combo = opts[k];
            String name = KeyLine.COMBO_LABEL[combo];
            String id = name + label + "_" + k;
            if (k > 0) {
                ImGui.sameLine();
                float wNeed = ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacing().x
                        + ImGui.calcTextSize(name).x + ImGui.getStyle().getItemSpacing().x;
                if (ImGui.getCursorPosX() + wNeed > wrapX) {
                    ImGui.newLine();
                    ImGui.setCursorPosX(startX);
                }
            }
            if (Controls.checkbox(name + "##" + id, sel[combo])) sel[combo] = !sel[combo];
        }
    }

    private void renderSprint(float scale) {
        SolverWidgets.rowLabel("Sprint", 62f * scale);
        int clicked = SolverWidgets.segmented("##coldsprint", SPRINT_LABELS, SPRINT_TIPS, sprintMode,
                Math.max(SolverWidgets.segmentedMinWidth(SPRINT_LABELS), 180f * scale));
        if (clicked >= 0) sprintMode = clicked;
        if (sprintMode == 1 && view.cycleCount() > 0) {
            String[] items = new String[view.cycleCount()];
            for (int i = 0; i < items.length; i++) items[i] = "Cycle " + (i + 1);
            ImGui.setNextItemWidth(140f * scale);
            Controls.combo("##coldsprintcycle", sprintCycleBuf, items);
        }
    }

    private void renderAdvanced() {
        intField("Glide max", "##cgmax", glideMaxBuf, 1, 40,
                "Default glide upper bound for cycles without an explicit band.");
        intField("Beam cap", "##cbeam", beamCapBuf, 1000, 5_000_000,
                "Trim survivors to this many by feasibility width when a cycle overflows.");
        intField("Certify cap", "##ccert", certifyCapBuf, 100, 5_000_000,
                "Stop after this many byte-exact certifies.");
        intField("Bucket budget", "##cbud", bucketBudgetBuf, 1, 60,
                "Facing-bucket slice solves per certify. Lower is faster; very low can miss on hard jumps.");
        intField("Threads", "##cthr", threadsBuf, 1, Runtime.getRuntime().availableProcessors(),
                "Certify worker threads.");
        intField("Budget (s)", "##cbuds", budgetSecBuf, 5, 36000,
                "Wall-clock budget for the whole search.");
    }

    private void intField(String label, String id, ImInt buf, int lo, int hi, String tip) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, 100f * ThemeManager.uiScale());
        if (Controls.inputInt(id, buf, ImGui.getContentRegionAvail().x)) {
            buf.set(Math.max(lo, Math.min(hi, buf.get())));
        }
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(tip);
    }

    private void renderActions() {
        boolean running = job != null && job.isRunning();
        if (running) {
            if (Controls.secondaryButton("Cancel")) job.cancel();
            ImGui.sameLine();
            float h = ImGui.getFrameHeight();
            ImVec2 p = ImGui.getCursorScreenPos();
            SolverWidgets.spinner(ImGui.getWindowDrawList(), p.x + h * 0.5f, p.y + h * 0.5f,
                    h * 0.30f, 1.8f * ThemeManager.uiScale(), ThemeManager.accentColor(), job.elapsedSeconds());
            ImGui.dummy(h, h);
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(String.format(Locale.ROOT, "%.1fs", job.elapsedSeconds()));
        } else {
            if (Controls.primaryButton("Run")) startRun();
            TooltipUtil.onHover(RUN_TIP);
        }
    }

    private void startRun() {
        if (view == null || !view.ok) return;
        ColdFinderJob started = starter.apply(buildConfig());
        if (started != null) {
            job = started;
            applied = false;
        }
    }

    private void renderProgress() {
        ColdFinderJob j = job;
        if (j == null) return;
        String line = j.cycleLine();
        if (line != null && !line.isEmpty()) {
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(line);
            ThemeManager.popTextColor();
        }
        if (j.total() > 0 && (j.isRunning() || j.done() > 0)) {
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(String.format(Locale.ROOT, "certify %d / %d · %d certified",
                    j.done(), j.total(), j.certified()));
            ThemeManager.popTextColor();
        }
    }

    private void renderResult() {
        ColdFinderJob j = job;
        if (j == null || !j.isFinished()) return;
        ColdResult r = j.result();
        boolean solved = r != null && r.solved();
        int color = solved ? ThemeManager.okColor()
                : "cancelled".equals(j.status()) ? ThemeManager.warningColor() : ThemeManager.dangerColor();
        ThemeManager.pushTextColor(color);
        ImGui.text(solved ? "Solved · applied to inputs" : ("No solve · " + j.status()));
        ThemeManager.popTextColor();
        if (solved) {
            ImGui.pushTextWrapPos(0f);
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("sig " + r.line.signature());
            ImGui.text(String.format(Locale.ROOT, "start (%.6f, %.6f)", r.startX, r.startZ));
            ThemeManager.popTextColor();
            ImGui.popTextWrapPos();
        }
    }

    private void maybeApply() {
        ColdFinderJob j = job;
        if (j == null || applied || !j.isFinished()) return;
        applied = true;
        ColdResult r = j.result();
        if (r != null && r.solved()) applier.accept(r);
    }

    private boolean section(String title, boolean expanded, float scale) {
        float rowH = ImGui.getTextLineHeight();
        ImVec2 origin = ImGui.getCursorScreenPos();
        if (ImGui.invisibleButton("##sec_" + title, ImGui.getContentRegionAvail().x, rowH)) {
            expanded = !expanded;
        }
        int col = ImGui.isItemHovered() ? ThemeManager.textColor() : ThemeManager.textDimColor();
        float cy = origin.y + rowH * 0.5f;
        if (expanded) SolverWidgets.triangleDown(ImGui.getWindowDrawList(), origin.x + 4f * scale, cy, 3.3f * scale, col);
        else SolverWidgets.triangleRight(ImGui.getWindowDrawList(), origin.x + 4f * scale, cy, 3.3f * scale, col);
        ImGui.getWindowDrawList().addText(origin.x + 13f * scale, origin.y, col, title);
        if (expanded) ThemeManager.bottomPaddedSeparator();
        return expanded;
    }

    private ColdBeamSolver.Config buildConfig() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.coasts = collect(COAST_OPTS, coastSel, ColdBeamSolver.DEFAULT_COASTS);
        cfg.glides = collect(GLIDE_OPTS, glideSel, ColdBeamSolver.DEFAULT_GLIDES);
        cfg.presses = collect(PRESS_OPTS, pressSel, ColdBeamSolver.DEFAULT_PRESSES);
        cfg.engages = collect(ENGAGE_OPTS, engageSel, ColdBeamSolver.DEFAULT_ENGAGES);
        cfg.brakes = collect(BRAKE_OPTS, brakeSel, ColdBeamSolver.DEFAULT_BRAKES);
        cfg.glideMax = glideMaxBuf.get();
        cfg.beamCap = beamCapBuf.get();
        cfg.certifyCap = certifyCapBuf.get();
        cfg.bucketBudget = bucketBudgetBuf.get();
        cfg.threads = threadsBuf.get();
        cfg.budgetMs = (long) budgetSecBuf.get() * 1000L;
        cfg.probeGate = 999.0;

        cfg.cycles = new ArrayList<ColdBeamSolver.CycleConfig>();
        for (CycleUi c : cycles) {
            ColdBeamSolver.CycleConfig cc = new ColdBeamSolver.CycleConfig();
            cc.fixed = c.fixed;
            cc.glideLo = Math.max(1, c.glideLo.get());
            cc.glideHi = c.fixed ? cc.glideLo : Math.max(cc.glideLo, c.glideHi.get());
            if (c.override) {
                cc.coasts = collect(COAST_OPTS, c.coasts, ColdBeamSolver.DEFAULT_COASTS);
                cc.glides = collect(GLIDE_OPTS, c.glides, ColdBeamSolver.DEFAULT_GLIDES);
                cc.presses = collect(PRESS_OPTS, c.presses, ColdBeamSolver.DEFAULT_PRESSES);
                cc.engages = collect(ENGAGE_OPTS, c.engages, ColdBeamSolver.DEFAULT_ENGAGES);
                cc.brakes = collect(BRAKE_OPTS, c.brakes, ColdBeamSolver.DEFAULT_BRAKES);
            }
            cfg.cycles.add(cc);
        }

        cfg.sprintEngage = sprintMode == 2 ? ColdBeamSolver.SprintEngage.SWEEP
                : sprintMode == 1 ? ColdBeamSolver.SprintEngage.AT_CYCLE
                : ColdBeamSolver.SprintEngage.ALWAYS;
        cfg.sprintEngageCycle = sprintCycleBuf.get();
        return cfg;
    }

    private static int[] collect(int[] opts, boolean[] sel, int[] fallback) {
        List<Integer> picked = new ArrayList<Integer>();
        for (int c : opts) {
            if (sel[c]) picked.add(c);
        }
        if (picked.isEmpty()) return fallback.clone();
        int[] out = new int[picked.size()];
        for (int i = 0; i < out.length; i++) out[i] = picked.get(i);
        return out;
    }

    private static final class CycleUi {
        boolean fixed;
        boolean override;
        final ImInt glideLo = new ImInt(1);
        final ImInt glideHi = new ImInt(2);
        final boolean[] coasts = new boolean[KeyLine.COMBO_COUNT];
        final boolean[] glides = new boolean[KeyLine.COMBO_COUNT];
        final boolean[] presses = new boolean[KeyLine.COMBO_COUNT];
        final boolean[] brakes = new boolean[KeyLine.COMBO_COUNT];
        final boolean[] engages = new boolean[KeyLine.COMBO_COUNT];

        CycleUi() {
            defaultSelection(coasts, ColdBeamSolver.DEFAULT_COASTS);
            defaultSelection(glides, ColdBeamSolver.DEFAULT_GLIDES);
            defaultSelection(presses, ColdBeamSolver.DEFAULT_PRESSES);
            defaultSelection(engages, ColdBeamSolver.DEFAULT_ENGAGES);
            defaultSelection(brakes, ColdBeamSolver.DEFAULT_BRAKES);
        }
    }
}
