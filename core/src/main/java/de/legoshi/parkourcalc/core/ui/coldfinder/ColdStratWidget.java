package de.legoshi.parkourcalc.core.ui.coldfinder;

import de.legoshi.parkourcalc.core.ColdStratController.ColdStratJob;
import de.legoshi.parkourcalc.core.ColdStratController.Problem;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdStratFinder;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.ui.anglesolver.SolverWidgets;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ColdStratWidget {

    private static final String WINDOW_ID = "###strat_finder_v2";
    private static final String TITLE = "Strat Finder";
    private static final int[] ALL_COMBOS = {KeyLine.NONE, KeyLine.W, KeyLine.WA, KeyLine.WD,
            KeyLine.A, KeyLine.D, KeyLine.S, KeyLine.SA, KeyLine.SD};
    private static final int[] PRESET_RUN = {KeyLine.NONE, KeyLine.W, KeyLine.WA, KeyLine.WD};
    private static final String[] YAW_LABELS = {"no-turn", "ja"};

    private static final int[] FAMILY_FWD = {KeyLine.NONE, KeyLine.W};
    private static final int[] FAMILY_MARK = {KeyLine.NONE, KeyLine.W, KeyLine.A, KeyLine.D, KeyLine.WA, KeyLine.WD};
    private static final int[] FAMILY_BFLY = {KeyLine.NONE, KeyLine.WA, KeyLine.WD, KeyLine.SA, KeyLine.SD, KeyLine.W};

    public interface Starter {
        ColdStratJob start(SaveFile file, ColdStratFinder.Request req);
    }

    private final Supplier<Problem> inspector;
    private final Starter starter;
    private final Consumer<ColdResult> applier;
    private final Consumer<Boolean> freeYawWindow;

    private final ImBoolean windowOpen = new ImBoolean(false);
    private boolean wasWindowOpen;

    private Problem problem;
    private final List<SegUi> segs = new ArrayList<SegUi>();
    private boolean freeYaws;
    private ColdStratJob job;
    private String selectedLabel;
    private ColdStratFinder.Strat selectedStrat;
    private int focusedSegment = -1;

    public ColdStratWidget(Supplier<Problem> inspector, Starter starter, Consumer<ColdResult> applier,
                           Consumer<Boolean> freeYawWindow) {
        this.inspector = inspector;
        this.starter = starter;
        this.applier = applier;
        this.freeYawWindow = freeYawWindow;
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
        ImGui.setNextWindowPos(100f * scale, 130f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(440f * scale, 520f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(360f * scale, 280f * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, windowOpen, ImGuiWindowFlags.NoCollapse);
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
        selectedLabel = null;
        selectedStrat = null;
        focusedSegment = -1;
        problem = inspector.get();
        segs.clear();
        if (problem != null && problem.ok) {
            for (int i = 0; i < problem.segmentCount(); i++) segs.add(new SegUi());
        }
    }

    private void renderBody(float scale) {
        if (Controls.secondaryButton("Rescan")) rescan();
        TooltipUtil.onHover("Re-read the solve segment, press-cycles and constraints from the current inputs.");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        if (problem == null) {
            ImGui.textDisabled("Nothing loaded.");
            return;
        }
        if (!problem.ok) {
            ImGui.textDisabled("Not searchable");
            warn(problem.error);
            return;
        }
        ImGui.text(String.format(Locale.ROOT, "T%d-T%d · %d segments",
                problem.startTick + 1, problem.landingTick + 1, problem.segmentCount()));

        ThemeManager.sectionSpacing();
        renderSegments(scale);

        ThemeManager.sectionSpacing();
        if (Controls.checkbox("Free yaws (whole run)", freeYaws)) {
            freeYaws = !freeYaws;
            if (freeYawWindow != null) freeYawWindow.accept(freeYaws);
        }
        TooltipUtil.onHover("Off = structured no-turn/ja search (cold, byte-exact). On opens the free-yaw"
                + " Strat Finder (fast optimizer, per-tick yaws) in its own window.");

        if (!problem.hasEndConstraint) warn("Set an end constraint (a landing target) before running.");

        renderEstimate();

        ThemeManager.paddedSeparator();
        renderActions(scale);
        renderResults(scale);
    }

    private void renderEstimate() {
        if (freeYaws) return;
        int[] lengths = new int[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            lengths[i] = problem.segPress[i] - problem.segStart[i] + 1;
        }
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        ColdStratFinder.Estimate est = ColdStratFinder.estimate(lengths, currentSegConfigs(), 3, threads);
        String candText = est.candidates >= Long.MAX_VALUE / 4
                ? "> 2e18 candidates" : compact(est.candidates) + " candidates";
        String timeText = est.seconds < 1.0 ? "< 1s" : est.seconds < 90.0
                ? String.format(Locale.ROOT, "~%.0fs", est.seconds)
                : String.format(Locale.ROOT, "~%.1f min", est.seconds / 60.0);
        String text = "Search size: " + candText + " · " + timeText + " to build";
        if (est.tooBig) {
            warn(text + "  (large: narrow the keys or lower changes)");
        } else {
            ImGui.textDisabled(text);
        }
    }

    private List<ColdStratFinder.SegmentConfig> currentSegConfigs() {
        List<ColdStratFinder.SegmentConfig> out = new ArrayList<ColdStratFinder.SegmentConfig>();
        for (SegUi s : segs) {
            ColdStratFinder.SegmentConfig sc = new ColdStratFinder.SegmentConfig();
            sc.ja = s.ja;
            sc.alphabet = s.selectedAlphabet();
            sc.maxChanges = s.maxChanges.get();
            out.add(sc);
        }
        return out;
    }

    private static String compact(long n) {
        if (n < 1_000L) return Long.toString(n);
        if (n < 1_000_000L) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        if (n < 1_000_000_000L) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        return String.format(Locale.ROOT, "%.1fB", n / 1_000_000_000.0);
    }

    private void renderSegments(float scale) {
        for (int i = 0; i < segs.size(); i++) {
            SegUi s = segs.get(i);
            String header = String.format(Locale.ROOT, "Segment %d · T%d-T%d · L=%d",
                    i + 1, problem.startTick + problem.segStart[i] + 1,
                    problem.startTick + problem.segPress[i] + 1,
                    problem.segPress[i] - problem.segStart[i] + 1);
            if (i == focusedSegment) {
                ThemeManager.pushTextColor(ThemeManager.accentColor());
                ImGui.text(header);
                ThemeManager.popTextColor();
            } else {
                ImGui.text(header);
            }
            ImGui.indent(ThemeManager.SM * scale);
            SolverWidgets.rowLabel("Yaw", 52f * scale);
            int clicked = SolverWidgets.segmented("##yaw" + i, YAW_LABELS, s.ja ? 1 : 0,
                    Math.max(SolverWidgets.segmentedMinWidth(YAW_LABELS), 130f * scale));
            if (clicked >= 0) s.ja = clicked == 1;

            SolverWidgets.rowLabel("Keys", 52f * scale);
            float startX = ImGui.getCursorPosX();
            float wrapX = startX + ImGui.getContentRegionAvail().x;
            for (int k = 0; k < ALL_COMBOS.length; k++) {
                int combo = ALL_COMBOS[k];
                String name = KeyLine.COMBO_LABEL[combo];
                if (k > 0) {
                    ImGui.sameLine();
                    float wNeed = ImGui.getFrameHeight() + ImGui.getStyle().getItemInnerSpacing().x
                            + ImGui.calcTextSize(name).x + ImGui.getStyle().getItemSpacing().x;
                    if (ImGui.getCursorPosX() + wNeed > wrapX) {
                        ImGui.newLine();
                        ImGui.setCursorPosX(startX);
                    }
                }
                if (Controls.checkbox(name + "##k" + i + "_" + k, s.alpha[combo])) {
                    s.alpha[combo] = !s.alpha[combo];
                }
            }

            SolverWidgets.rowLabel("Changes", 52f * scale);
            ImGui.setNextItemWidth(52f * scale);
            if (ImGui.inputInt("##chg" + i, s.maxChanges, 0, 0)) {
                s.maxChanges.set(Math.max(0, Math.min(problem.segPress[i] - problem.segStart[i] + 1, s.maxChanges.get())));
            }
            TooltipUtil.onHover("How many times the keys may change within this segment. 0 = hold one combo the"
                    + " whole segment; higher = more freedom (and a bigger search).");
            ImGui.sameLine();
            if (Controls.secondaryButton("fmm##p" + i)) s.applyFamily(FAMILY_FWD, 1, false);
            ImGui.sameLine();
            if (Controls.secondaryButton("mark##p" + i)) s.applyFamily(FAMILY_MARK, 1, false);
            ImGui.sameLine();
            if (Controls.secondaryButton("bfly##p" + i)) s.applyFamily(FAMILY_BFLY, 2, true);
            ImGui.sameLine();
            if (Controls.secondaryButton("all##p" + i)) s.setAlphabet(ALL_COMBOS);
            TooltipUtil.onHover("Family presets set this segment's keys + changes + yaw: fmm/pessi (forward,"
                    + " late-sprint auto-searched), mark (sidestep), bfly (butterfly diagonals), all (every combo)."
                    + " The sprint-engage timing and held length are searched, so the family only fixes the keys.");
            ImGui.unindent(ThemeManager.SM * scale);
        }
    }

    private void renderActions(float scale) {
        boolean running = job != null && job.isRunning();
        boolean canRun = problem != null && problem.ok && problem.hasEndConstraint && !freeYaws;
        if (running) {
            if (Controls.secondaryButton("Cancel")) job.cancel();
            ImGui.sameLine();
            float h = ImGui.getFrameHeight();
            ImVec2 p = ImGui.getCursorScreenPos();
            SolverWidgets.spinner(ImGui.getWindowDrawList(), p.x + h * 0.5f, p.y + h * 0.5f,
                    h * 0.30f, 1.8f * scale, ThemeManager.accentColor(), job.elapsedSeconds());
            ImGui.dummy(h, h);
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(progressText());
        } else if (canRun) {
            if (Controls.primaryButton("Run")) startRun();
            TooltipUtil.onHover("Brute-force the configured families per segment, byte-exact certify, and"
                    + " list the feasible strats ranked easiest first.");
        } else if (freeYaws) {
            Controls.disabledButton("Run");
            TooltipUtil.onHover("Free-yaws path is not wired yet.");
        } else {
            Controls.disabledButton("Run");
        }
    }

    private String progressText() {
        ColdStratJob j = job;
        if (j == null) return "";
        if (j.built() < 0) return "building";
        return j.done() + " / " + j.total() + " · " + j.feasible() + " feasible";
    }

    private void startRun() {
        selectedLabel = null;
        selectedStrat = null;
        focusedSegment = -1;
        ColdStratFinder.Request req = new ColdStratFinder.Request();
        req.beam.threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        req.beam.bucketBudget = 6;
        req.segments = currentSegConfigs();
        ColdStratJob started = starter.start(problem.file, req);
        if (started != null) job = started;
    }

    private void renderResults(float scale) {
        ColdStratJob j = job;
        if (j == null) return;
        ColdStratFinder.Result r = j.result();
        if (r == null) return;
        if (r.strats.isEmpty()) {
            if (j.isFinished()) ImGui.textDisabled("No feasible strat for this configuration.");
            return;
        }
        ImGui.textDisabled(r.strats.size() + " strats · " + r.feasible + " lines");
        if (r.truncated) warn("Search truncated (budget/cap hit). Narrow the keys or lower changes for full coverage.");
        float rowH = ThemeManager.tableRowHeight();
        float tableH = Math.max(rowH * 4f, Math.min(ImGui.getContentRegionAvail().y, rowH * (r.strats.size() + 1.6f)));
        if (!ThemeManager.beginStandardClickableRowsTable("##cs_table", 3, 0, 0f, tableH)) return;
        ImGui.tableSetupScrollFreeze(0, 1);
        ImGui.tableSetupColumn("Strat", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("Diff", ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableColumnWidth("Diff", ImGui.calcTextSize("0.000").x));
        ImGui.tableSetupColumn("Lines", ImGuiTableColumnFlags.WidthFixed,
                ThemeManager.tableColumnWidth("Lines", ImGui.calcTextSize("Lines").x));
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader("Strat");
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader("Diff");
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader("Lines");

        int idx = 0;
        for (ColdStratFinder.Strat s : r.strats) {
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(idx);
            boolean selected = s.label().equals(selectedLabel);
            ImGui.tableSetColumnIndex(0);
            ThemeManager.tableLeftmostCellPad();
            ImGui.alignTextToFramePadding();
            if (ImGui.selectable(s.label() + "##cs" + idx, selected, ImGuiSelectableFlags.SpanAllColumns)) {
                selectedLabel = s.label();
                selectedStrat = s;
                focusedSegment = -1;
                applier.accept(s.representative);
            }
            TooltipUtil.onHover(String.format(Locale.ROOT,
                    "facing band %.3f deg  ·  %d turns  ·  %d feasible lines",
                    s.bandDeg, s.turns, s.feasibleCount));
            ImGui.tableSetColumnIndex(1);
            ImGui.alignTextToFramePadding();
            ImGui.text(String.format(Locale.ROOT, "%.3f", s.difficulty));
            ImGui.tableSetColumnIndex(2);
            ImGui.alignTextToFramePadding();
            ImGui.text(Integer.toString(s.feasibleCount));
            idx++;
        }
        ThemeManager.endStandardTable();

        render3dView(scale);
    }

    private void render3dView(float scale) {
        if (selectedStrat == null || selectedStrat.representative == null) return;
        ForwardPath path = selectedStrat.representative.path;
        if (path == null || path.posX == null || path.posX.length < 2) return;
        if (!ImGui.collapsingHeader("3D view")) return;

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
        ImGui.invisibleButton("##cs3d", w, h);
        boolean clicked = ImGui.isItemClicked();
        ImVec2 mouse = ImGui.getMousePos();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(org.x, org.y, org.x + w, org.y + h, ThemeManager.panelColor(), 4f * scale);

        float pad = 16f * scale;
        float spanX = Math.max(1e-3f, maxX - minX);
        float spanY = Math.max(1e-3f, maxY - minY);
        float sc = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY);
        float offX = org.x + pad - minX * sc + ((w - 2 * pad) - spanX * sc) * 0.5f;
        float offY = org.y + pad - minY * sc + ((h - 2 * pad) - spanY * sc) * 0.5f;

        float bestD = Float.MAX_VALUE;
        int bestSeg = -1;
        for (int i = 1; i < n; i++) {
            float ax = offX + sx[i - 1] * sc;
            float ay = offY + sy[i - 1] * sc;
            float bx = offX + sx[i] * sc;
            float by = offY + sy[i] * sc;
            int seg = segmentOfTick(i - 1);
            int col = seg < 0 ? ThemeManager.trajectoryMutedColor() : ThemeManager.trajectorySegmentColor(seg);
            dl.addLine(ax, ay, bx, by, col, 2.0f * scale);
            if (clicked) {
                float d = distToSeg(mouse.x, mouse.y, ax, ay, bx, by);
                if (d < bestD) {
                    bestD = d;
                    bestSeg = seg;
                }
            }
        }
        dl.addCircleFilled(offX + sx[0] * sc, offY + sy[0] * sc, 3.5f * scale, ThemeManager.lockedColor(), 8);

        drawBandFan(dl, offX, offY, sc, sinA, cosA, cx, cy, cz, path);

        if (clicked && bestSeg >= 0 && bestD < 14f * scale) focusedSegment = bestSeg;
        ImGui.textDisabled(String.format(Locale.ROOT,
                "auto-rotating · click a leg to focus its segment · band %.3f deg", selectedStrat.bandDeg));
    }

    private void drawBandFan(ImDrawList dl, float offX, float offY, float sc,
                             double sinA, double cosA, double cx, double cy, double cz, ForwardPath path) {
        int last = problem.segPress[problem.segPress.length - 1];
        int li = Math.min(last + 1, path.posX.length - 1);
        double lx = path.posX[li] - cx;
        double ly = path.posY[li] - cy;
        double lz = path.posZ[li] - cz;
        double baseFacing = selectedStrat.representative.facingDeg;
        double band = Math.max(0.02, Math.min(30.0, selectedStrat.bandDeg));
        int rays = 9;
        double len = 1.6;
        for (int r = 0; r <= rays; r++) {
            double yaw = Math.toRadians(baseFacing - band * 0.5 + band * r / rays);
            double dx = -Math.sin(yaw) * len;
            double dz = Math.cos(yaw) * len;
            double ex = lx + dx;
            double ez = lz + dz;
            float ax = offX + (float) (lx * cosA - lz * sinA) * sc;
            float ay = offY + (float) ((lx * sinA + lz * cosA) * 0.5 - ly) * sc;
            float bx = offX + (float) (ex * cosA - ez * sinA) * sc;
            float by = offY + (float) ((ex * sinA + ez * cosA) * 0.5 - ly) * sc;
            dl.addLine(ax, ay, bx, by, ThemeManager.trajectoryBandColor(), 1.2f);
        }
    }

    private int segmentOfTick(int tick) {
        for (int i = 0; i < problem.segPress.length; i++) {
            if (tick >= problem.segStart[i] && tick <= problem.segPress[i]) return i;
        }
        return -1;
    }

    private static float distToSeg(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len2 = dx * dx + dy * dy;
        float t = len2 <= 1e-6f ? 0f : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0f, Math.min(1f, t));
        float qx = ax + t * dx;
        float qy = ay + t * dy;
        return (float) Math.hypot(px - qx, py - qy);
    }

    private static void warn(String msg) {
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.pushTextWrapPos(0f);
        ImGui.text(msg);
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();
    }

    private static final class SegUi {
        boolean ja;
        final boolean[] alpha = new boolean[KeyLine.COMBO_COUNT];
        final imgui.type.ImInt maxChanges = new imgui.type.ImInt(2);

        SegUi() {
            setAlphabet(PRESET_RUN);
        }

        void setAlphabet(int[] combos) {
            java.util.Arrays.fill(alpha, false);
            for (int c : combos) alpha[c] = true;
        }

        void applyFamily(int[] combos, int changes, boolean turn) {
            setAlphabet(combos);
            maxChanges.set(changes);
            ja = turn;
        }

        int[] selectedAlphabet() {
            List<Integer> out = new ArrayList<Integer>();
            for (int combo : ALL_COMBOS) {
                if (alpha[combo]) out.add(combo);
            }
            if (out.isEmpty()) return PRESET_RUN.clone();
            int[] a = new int[out.size()];
            for (int i = 0; i < a.length; i++) a[i] = out.get(i);
            return a;
        }
    }
}
