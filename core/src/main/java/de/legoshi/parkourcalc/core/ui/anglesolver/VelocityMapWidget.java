package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public final class VelocityMapWidget {

    private enum View { TWO_D, THREE_D }

    private static final int RES_MIN = 32;
    private static final int RES_MAX = 320;
    private static final int RES_DEFAULT = 160;
    private static final int REFINE_CELLS = 64;
    private static final float MAX_SUPPORT = 0.3f;
    private static final float CLICK_SLOP = 3f;
    private static final double ZOOM_BASE = 1.2;
    private static final double HEIGHT_SCALE = 0.85;
    private static final double ROT_SPEED = 0.01;
    private static final double FIELD_NEG_FLOOR = -1.0e-4;
    private static final long RESAMPLE_SETTLE_NANOS = 120_000_000L;

    private static final String[] VIEW_LABELS = {"2D", "3D"};
    private static final String[] VIEW_TIPS = {
            "Top-down heatmap of the velocity plane.",
            "3D surface of landing quality; drag to orbit."};
    private static final String[] ACC_LABELS = {"Fast", "Accurate", "Hyper"};
    private static final String[] ACC_TIPS = {
            "Fast: closed-form only. Quickest, but can miss grazing solutions.",
            "Accurate: closed-form with a fallback solve. Good default.",
            "Hyper: full solver on every cell. Most thorough, much slower."};
    private static final String RES_TIP = "Grid resolution: more cells give a finer map but a slower sweep.";
    private static final String[] FORM_LABELS = {"View", "Resolution", "Accuracy"};

    private final Supplier<VelocityFinder> finderFactory;
    private final Supplier<VelocityFinder.Grid> gridSupplier;
    private final Consumer<VelocityFinder.Candidate> onApply;
    private final Supplier<double[]> markerV0;
    private final BooleanSupplier tempActive;
    private final Runnable onRestoreTemp;
    private final Runnable onKeepTemp;
    private final Function<String, String> onSaveCopyAs;
    private final int threads;

    private final ImString copyNameBuf = new ImString(64);
    private String copyStatus;

    private final ImBoolean windowOpen = new ImBoolean(false);
    private View view = View.TWO_D;

    private final ImString rangeVxLoBuf = new ImString(16);
    private final ImString rangeVxHiBuf = new ImString(16);
    private final ImString rangeVzLoBuf = new ImString(16);
    private final ImString rangeVzHiBuf = new ImString(16);
    private final ImBoolean rangeEnabled = new ImBoolean(false);
    private double rangeVxLo, rangeVxHi, rangeVzLo, rangeVzHi;
    private boolean rangeValid;

    private int fieldRes = RES_DEFAULT;
    private final int[] resBuf = {RES_DEFAULT};
    private boolean pendingFind;
    private VelocityFinder.Accuracy accuracy = VelocityFinder.Accuracy.ACCURATE;
    private double lastVx, lastVz, lastPx;
    private long lastViewMoveNanos;

    private VelocityFinder finder;
    private VelocityFinder.Grid grid;

    private volatile FieldSnap field;
    private volatile boolean fieldRunning;
    private volatile boolean currentSweepIsResample;
    private long fieldStartNanos;
    private final AtomicLongArray perCellNanos = new AtomicLongArray(3);
    private Thread fieldWorker;
    private volatile AtomicBoolean currentCancel;
    private boolean wasWindowOpen;
    private double lastResampleVx = Double.NaN, lastResampleVz = Double.NaN, lastResamplePx = Double.NaN;

    private boolean viewInit;
    private double centerVx, centerVz, pxPerUnit;

    private boolean view3dInit;
    private double orbitYaw = -0.7, orbitPitch = 0.62, cube3dScale;
    private boolean rotating;
    private float rotAnchorMx, rotAnchorMy;
    private double rotAnchorYaw, rotAnchorPitch;

    private boolean panning, movedWhilePanning;
    private float panAnchorMx, panAnchorMy;
    private double panAnchorVx, panAnchorVz;

    private boolean marquee;
    private float marqStartX, marqStartY, marqCurX, marqCurY;

    private double[] hoverCell;
    private double[] markerV0Applied;

    private volatile VelocityFinder.Candidate[] refineCells;
    private volatile int refineCols, refineRows;
    private volatile VelocityFinder.Grid refineGrid;
    private Thread refineWorker;
    private final AtomicBoolean refineCancel = new AtomicBoolean();

    private static final class FieldSnap {
        final float[] z;
        final VelocityFinder.Candidate[] cells;
        final int cols, rows;
        final AtomicInteger done = new AtomicInteger();
        final double vxLo, vxHi, vzLo, vzHi;
        volatile double negMin;
        volatile double negMax;
        volatile double posMax;

        FieldSnap(float[] z, VelocityFinder.Candidate[] cells, int cols, int rows,
                  double vxLo, double vxHi, double vzLo, double vzHi, double negMin, double negMax, double posMax) {
            this.z = z;
            this.cells = cells;
            this.cols = cols;
            this.rows = rows;
            this.vxLo = vxLo;
            this.vxHi = vxHi;
            this.vzLo = vzLo;
            this.vzHi = vzHi;
            this.negMin = negMin;
            this.negMax = negMax;
            this.posMax = posMax;
        }
    }

    public VelocityMapWidget(Supplier<VelocityFinder> finderFactory,
                             Supplier<VelocityFinder.Grid> gridSupplier,
                             Consumer<VelocityFinder.Candidate> onApply,
                             Supplier<double[]> markerV0,
                             BooleanSupplier tempActive,
                             Runnable onRestoreTemp,
                             Runnable onKeepTemp,
                             Function<String, String> onSaveCopyAs,
                             int threads) {
        this.finderFactory = finderFactory;
        this.gridSupplier = gridSupplier;
        this.onApply = onApply;
        this.markerV0 = markerV0;
        this.tempActive = tempActive;
        this.onRestoreTemp = onRestoreTemp;
        this.onKeepTemp = onKeepTemp;
        this.onSaveCopyAs = onSaveCopyAs;
        this.threads = Math.max(1, threads);
        rangeVxLoBuf.set("-0.250");
        rangeVxHiBuf.set("0.250");
        rangeVzLoBuf.set("-0.250");
        rangeVzHiBuf.set("0.250");
    }

    public void setWindowOpen(boolean open) {
        windowOpen.set(open);
    }

    public boolean isWindowOpen() {
        return windowOpen.get();
    }

    private void renderTempBanner(float scale) {
        if (tempActive == null || !tempActive.getAsBoolean()) {
            copyStatus = null;
            return;
        }
        ThemeManager.paddedSeparator();
        ImGui.pushTextWrapPos(0f);
        ThemeManager.pushTextColor(ThemeManager.warningColor());
        ImGui.text("Temp trajectory applied. Auto-save is paused.");
        ThemeManager.popTextColor();
        ImGui.popTextWrapPos();
        boolean reapply = Controls.secondaryButton("Reapply original");
        TooltipUtil.onHover("Discard the temp trajectory and restore the original.");
        if (reapply) {
            if (onRestoreTemp != null) onRestoreTemp.run();
            copyStatus = null;
            return;
        }
        ImGui.sameLine();
        boolean keep = Controls.secondaryButton("Keep");
        TooltipUtil.onHover("Commit the temp trajectory as the new baseline; auto-save resumes.");
        if (keep) {
            if (onKeepTemp != null) onKeepTemp.run();
            copyStatus = null;
            return;
        }
        Controls.inputTextHint("##velcopyname", "save copy name", copyNameBuf, 150f * scale);
        ImGui.sameLine();
        boolean saveCopy = Controls.secondaryButton("Save copy");
        TooltipUtil.onHover("Save the temp trajectory to a new file without switching the active save.");
        if (saveCopy && onSaveCopyAs != null) {
            copyStatus = onSaveCopyAs.apply(copyNameBuf.get());
        }
        if (copyStatus != null && !copyStatus.isEmpty()) {
            ImGui.pushTextWrapPos(0f);
            ImGui.textDisabled(copyStatus);
            ImGui.popTextWrapPos();
        }
    }

    public void renderWindow(float scale) {
        boolean open = windowOpen.get();
        if (wasWindowOpen && !open) stop();
        wasWindowOpen = open;
        if (!open) return;
        ImGui.setNextWindowSize(480f * scale, 440f * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(340f * scale, 300f * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        if (ImGui.begin("Velocity Map", windowOpen, flags)) {
            renderToolbar(scale);
            if (field != null || pendingFind) {
                ThemeManager.sectionSpacing();
                float ch = ImGui.getContentRegionAvail().y - footerHeight(scale);
                drawCanvas(scale, ch);
            }
            renderTempBanner(scale);
        }
        ImGui.end();
    }

    private float footerHeight(float scale) {
        if (tempActive == null || !tempActive.getAsBoolean()) return 0f;
        float fh = ImGui.getFrameHeightWithSpacing();
        float line = ImGui.getTextLineHeightWithSpacing();
        float h = line + 2f * line + 2f * fh;
        if (copyStatus != null && !copyStatus.isEmpty()) h += line;
        return h + 8f * scale;
    }

    private void renderToolbar(float scale) {
        if (field == null) {
            ImGui.textDisabled("Set up the jump in the Angle Solver, then Find velocities.");
            ThemeManager.sectionSpacing();
            renderActionRow();
            return;
        }

        float labelW = labelColumnWidth(scale);
        float controlW = Math.max(SolverWidgets.segmentedMinWidth(VIEW_LABELS),
                SolverWidgets.segmentedMinWidth(ACC_LABELS));

        int v = segmentedRow("View", "velview", VIEW_LABELS, VIEW_TIPS, view.ordinal(), labelW, controlW);
        if (v >= 0) view = View.values()[v];

        resolutionRow(labelW, controlW);

        int acc = segmentedRow("Accuracy", "velacc", ACC_LABELS, ACC_TIPS, accuracy.ordinal(), labelW, controlW);
        if (acc >= 0 && acc != accuracy.ordinal()) {
            accuracy = VelocityFinder.Accuracy.values()[acc];
            requestFullSweep(false);
        }

        if (view == View.TWO_D) {
            ThemeManager.sectionSpacing();
            renderRangeControls(scale, labelW);
        }

        ThemeManager.sectionSpacing();
        renderActionRow();
    }

    private void renderActionRow() {
        boolean find = Controls.primaryButton(field == null ? "Find velocities" : "Re-find");
        TooltipUtil.onHover("Sweep the initial-velocity plane; each cell shows whether that start velocity lands on the pad.");
        if (find) requestFullSweep(true);
        if (fieldRunning) {
            ImGui.sameLine();
            boolean cancel = Controls.secondaryButton("Cancel");
            TooltipUtil.onHover("Stop the current sweep.");
            if (cancel) cancelCurrentSweep();
            ImGui.sameLine();
            ImGui.alignTextToFramePadding();
            ImGui.textDisabled(progressText());
        }
    }

    private float labelColumnWidth(float scale) {
        float max = 0f;
        for (String l : FORM_LABELS) max = Math.max(max, ImGui.calcTextSize(l).x);
        return max + ThemeManager.SM * scale;
    }

    private int segmentedRow(String label, String id, String[] items, String[] tips, int selected,
                             float labelW, float controlW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, labelW);
        int clicked = SolverWidgets.segmented(id, items, tips, selected, controlW);
        Controls.popInputFrameHeight();
        return clicked;
    }

    private void resolutionRow(float labelW, float controlW) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel("Resolution", labelW);
        ImGui.setNextItemWidth(controlW);
        if (ImGui.sliderInt("##velres", resBuf, RES_MIN, RES_MAX)) fieldRes = resBuf[0];
        boolean deactivated = ImGui.isItemDeactivatedAfterEdit();
        TooltipUtil.onHover(RES_TIP);
        Controls.popInputFrameHeight();
        if (deactivated) pendingFind = true;
    }

    public void stop() {
        cancelCurrentSweep();
        refineCancel.set(true);
        if (fieldWorker != null) fieldWorker.interrupt();
        if (refineWorker != null) refineWorker.interrupt();
    }

    private synchronized void cancelCurrentSweep() {
        AtomicBoolean c = currentCancel;
        if (c != null) c.set(true);
        fieldRunning = false;
    }

    private void requestFullSweep(boolean resetView) {
        if (tempActive != null && tempActive.getAsBoolean() && onRestoreTemp != null) {
            onRestoreTemp.run();
        }
        VelocityFinder f = finderFactory.get();
        VelocityFinder.Grid g = gridSupplier.get();
        if (f == null || g == null) return;
        finder = f;
        grid = g;
        finder.setAccuracy(accuracy);
        refineCancel.set(true);
        refineCells = null;
        refineGrid = null;
        if (resetView) viewInit = false;
        pendingFind = true;
    }

    private void startSweep(double vxLo, double vxHi, double vzLo, double vzHi, boolean seed, boolean resample) {
        if (finder == null) return;
        if (rangeEnabled.get() && rangeValid) {
            vxLo = Math.max(vxLo, rangeVxLo);
            vxHi = Math.min(vxHi, rangeVxHi);
            vzLo = Math.max(vzLo, rangeVzLo);
            vzHi = Math.min(vzHi, rangeVzHi);
        }
        if (vxHi - vxLo < 1e-9 || vzHi - vzLo < 1e-9) return;
        final int n = Math.max(2, fieldRes);
        final double vxSpan = vxHi - vxLo, vzSpan = vzHi - vzLo;
        final double step = Math.max(vxSpan, vzSpan) / (n - 1);
        final int cols = Math.max(2, (int) Math.round(vxSpan / step) + 1);
        final int rows = Math.max(2, (int) Math.round(vzSpan / step) + 1);
        final FieldSnap old = field;
        final FieldSnap snap = seed && old != null
                ? seededSnap(old, cols, rows, vxLo, vxHi, vzLo, vzHi)
                : blankSnap(cols, rows, vxLo, vxHi, vzLo, vzHi);
        final AtomicBoolean myCancel = new AtomicBoolean(false);
        final long startNanos = System.nanoTime();
        synchronized (this) {
            AtomicBoolean prev = currentCancel;
            if (prev != null) prev.set(true);
            currentCancel = myCancel;
            currentSweepIsResample = resample;
            field = snap;
            fieldStartNanos = startNanos;
            fieldRunning = true;
        }
        lastResampleVx = centerVx;
        lastResampleVz = centerVz;
        lastResamplePx = pxPerUnit;

        final VelocityFinder activeFinder = finder;
        final VelocityFinder.Grid vg = new VelocityFinder.Grid(vxLo, vxHi, 1.0, vzLo, vzHi, 1.0);
        final VelocityFinder.Accuracy acc = accuracy;
        Thread worker = new Thread(() -> {
            try {
                activeFinder.sweepFieldParallel(vg, cols, rows, threads, myCancel, (r, c, f, cand) -> fillCell(snap, r, c, f, cand));
            } finally {
                synchronized (VelocityMapWidget.this) {
                    if (currentCancel == myCancel) {
                        fieldRunning = false;
                        if (!myCancel.get()) {
                            recordSweepCost(acc, (long) cols * rows, System.nanoTime() - startNanos);
                        }
                    }
                }
            }
        }, "velocity-field");
        worker.setDaemon(true);
        fieldWorker = worker;
        worker.start();
    }

    private void recordSweepCost(VelocityFinder.Accuracy acc, long cells, long wallNanos) {
        if (cells <= 0 || wallNanos <= 0) return;
        long perCell = wallNanos / cells;
        long prior = perCellNanos.get(acc.ordinal());
        long blended = prior <= 0 ? perCell : (prior * 2 + perCell) / 3;
        perCellNanos.set(acc.ordinal(), blended);
    }

    private FieldSnap blankSnap(int cols, int rows, double vxLo, double vxHi, double vzLo, double vzHi) {
        float[] z = new float[cols * rows];
        VelocityFinder.Candidate[] cells = new VelocityFinder.Candidate[cols * rows];
        java.util.Arrays.fill(z, Float.NaN);
        return new FieldSnap(z, cells, cols, rows, vxLo, vxHi, vzLo, vzHi, FIELD_NEG_FLOOR, Double.NEGATIVE_INFINITY, 0.0);
    }

    private FieldSnap seededSnap(FieldSnap old, int cols, int rows, double vxLo, double vxHi, double vzLo, double vzHi) {
        float[] z = new float[cols * rows];
        VelocityFinder.Candidate[] cells = new VelocityFinder.Candidate[cols * rows];
        double dvx = cols > 1 ? (vxHi - vxLo) / (cols - 1) : 0.0;
        double dvz = rows > 1 ? (vzHi - vzLo) / (rows - 1) : 0.0;
        double neg = FIELD_NEG_FLOOR;
        double negHi = Double.NEGATIVE_INFINITY;
        double pos = 0.0;
        for (int r = 0; r < rows; r++) {
            double vz = vzLo + r * dvz;
            for (int c = 0; c < cols; c++) {
                Float f = sampleField(old, vxLo + c * dvx, vz);
                float v = f == null ? Float.NaN : f;
                z[r * cols + c] = v;
                cells[r * cols + c] = fieldCellAt(old, vxLo + c * dvx, vz);
                if (!Float.isNaN(v) && v < neg) neg = v;
                if (!Float.isNaN(v) && v < 0f && v > negHi) negHi = v;
                if (!Float.isNaN(v) && v > pos) pos = v;
            }
        }
        return new FieldSnap(z, cells, cols, rows, vxLo, vxHi, vzLo, vzHi, neg, negHi, pos);
    }

    private String progressText() {
        FieldSnap s = field;
        if (s == null) return "";
        String base = s.done.get() + " / " + s.z.length;
        String eta = etaText(s);
        return eta.isEmpty() ? base : base + "   " + eta;
    }

    private String etaText(FieldSnap s) {
        int total = s.z.length;
        int done = s.done.get();
        if (total <= 0 || done >= total) return "";
        long remNanos;
        if (done > 4) {
            long elapsed = System.nanoTime() - fieldStartNanos;
            remNanos = (long) ((double) elapsed / done * (total - done));
        } else {
            long per = perCellNanos.get(accuracy.ordinal());
            if (per <= 0) return "";
            remNanos = per * (total - done);
        }
        return "~" + fmtDuration(remNanos) + " left";
    }

    private static String fmtDuration(long nanos) {
        double s = nanos / 1.0e9;
        if (s < 1.0) return String.format(Locale.ROOT, "%.1fs", s);
        if (s < 60.0) return String.format(Locale.ROOT, "%.0fs", s);
        long m = (long) (s / 60.0);
        long sec = Math.round(s - m * 60.0);
        return m + "m" + sec + "s";
    }

    private void fillCell(FieldSnap snap, int r, int c, double f, VelocityFinder.Candidate cand) {
        int idx = r * snap.cols + c;
        if (idx < 0 || idx >= snap.z.length) return;
        snap.z[idx] = (float) f;
        snap.cells[idx] = cand;
        if (f < snap.negMin) snap.negMin = f;
        if (f < 0.0 && f > snap.negMax) snap.negMax = f;
        if (f > snap.posMax) snap.posMax = f;
        snap.done.incrementAndGet();
    }

    private void drawCanvas(float scale, float chArg) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 origin = ImGui.getCursorScreenPos();
        float cw = Math.max(40f, ImGui.getContentRegionAvail().x);
        float ch = Math.max(80f, chArg);
        float x0 = origin.x, y0 = origin.y, x1 = x0 + cw, y1 = y0 + ch;

        if (!viewInit) initView(cw, ch);
        if (pendingFind) {
            pendingFind = false;
            startSweep(screenToVx(x0, x0, cw), screenToVx(x1, x0, cw),
                    screenToVz(y1, y0, ch), screenToVz(y0, y0, ch), false, false);
        }

        ImGui.invisibleButton("##velcanvas", cw, ch);
        boolean hovered = ImGui.isItemHovered();
        ImGui.pushClipRect(x0, y0, x1, y1, true);
        dl.addRectFilled(x0, y0, x1, y1, ThemeManager.bgDarkColor(), 0f);

        FieldSnap snap = field;
        if (snap != null) {
            if (view == View.TWO_D) {
                handleInput2D(hovered, x0, y0, cw, ch);
                maybeResample(x0, y0, cw, ch);
                draw2D(dl, snap, x0, y0, cw, ch);
                drawZeroAxes(dl, x0, y0, cw, ch);
                drawOrigin2D(dl, x0, y0, cw, ch);
                drawRefineOverlay(dl, x0, y0, cw, ch);
                drawHoverCell(dl, x0, y0, cw, ch);
                drawMarker(dl, x0, y0, cw, ch);
                if (marquee) drawMarquee(dl);
                drawAxes2D(dl, x0, y0, cw, ch);
            } else {
                if (!view3dInit) init3DView(cw, ch);
                handleInput3D(hovered, x0, y0, cw, ch);
                draw3D(dl, snap, x0, y0, cw, ch);
            }
            drawLegend(dl, snap, x0, y0, cw, ch);
        }
        ImGui.popClipRect();
        dl.addRect(x0, y0, x1, y1, ThemeManager.borderColor(), 0f, 0, 1f);
    }

    private void initView(float cw, float ch) {
        boolean ranged = rangeEnabled.get() && rangeValid;
        double loX = ranged ? rangeVxLo : grid.vxLo, hiX = ranged ? rangeVxHi : grid.vxHi;
        double loZ = ranged ? rangeVzLo : grid.vzLo, hiZ = ranged ? rangeVzHi : grid.vzHi;
        centerVx = (loX + hiX) * 0.5;
        centerVz = (loZ + hiZ) * 0.5;
        double spanVx = Math.max(1e-9, hiX - loX);
        double spanVz = Math.max(1e-9, hiZ - loZ);
        pxPerUnit = Math.min(cw / spanVx, ch / spanVz) * 0.9;
        viewInit = true;
    }

    private void renderRangeControls(float scale, float labelW) {
        boolean rangeToggle = Controls.checkbox("Limit to range", rangeEnabled.get());
        TooltipUtil.onHover("Restrict the sweep to the vx/vz range below.");
        if (rangeToggle) {
            if (rangeEnabled.get()) onRangeDisable();
            else onRangeApply();
        }
        float w = 62f * scale;
        int flags = ImGuiInputTextFlags.CharsDecimal;
        rangeRow("vx", "##rngVxLo", rangeVxLoBuf, "##rngVxHi", rangeVxHiBuf, labelW, w, flags);
        rangeRow("vz", "##rngVzLo", rangeVzLoBuf, "##rngVzHi", rangeVzHiBuf, labelW, w, flags);

        ImGui.setCursorPosX(ImGui.getCursorPosX() + labelW);
        boolean applyRange = Controls.secondaryButton("Apply");
        TooltipUtil.onHover("Apply the typed range and reframe the view to it.");
        if (applyRange) onRangeApply();
        if (rangeEnabled.get()) {
            ImGui.textDisabled("Showing only the range; zoom in to refine its resolution.");
        }
    }

    private void rangeRow(String label, String idLo, ImString lo, String idHi, ImString hi,
                          float labelW, float w, int flags) {
        Controls.pushInputFrameHeight();
        SolverWidgets.rowLabel(label, labelW);
        Controls.inputTextHint(idLo, "lo", lo, w, flags);
        ImGui.sameLine();
        Controls.inputTextHint(idHi, "hi", hi, w, flags);
        Controls.popInputFrameHeight();
    }

    private boolean parseRange() {
        double a = parseDoubleOrNaN(rangeVxLoBuf.get());
        double b = parseDoubleOrNaN(rangeVxHiBuf.get());
        double cc = parseDoubleOrNaN(rangeVzLoBuf.get());
        double d = parseDoubleOrNaN(rangeVzHiBuf.get());
        if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(cc) || Double.isNaN(d)) return false;
        double vxLo = Math.min(a, b), vxHi = Math.max(a, b);
        double vzLo = Math.min(cc, d), vzHi = Math.max(cc, d);
        if (vxHi - vxLo < 1e-9 || vzHi - vzLo < 1e-9) return false;
        rangeVxLo = vxLo;
        rangeVxHi = vxHi;
        rangeVzLo = vzLo;
        rangeVzHi = vzHi;
        return true;
    }

    private void onRangeApply() {
        if (parseRange()) {
            rangeValid = true;
            rangeEnabled.set(true);
            refineCancel.set(true);
            refineCells = null;
            refineGrid = null;
            viewInit = false;
            pendingFind = true;
        } else {
            rangeEnabled.set(false);
            rangeValid = false;
        }
    }

    private void onRangeDisable() {
        rangeEnabled.set(false);
        rangeValid = false;
        refineCancel.set(true);
        refineCells = null;
        refineGrid = null;
        viewInit = false;
        pendingFind = true;
    }

    private static double parseDoubleOrNaN(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void handleInput2D(boolean hovered, float x0, float y0, float cw, float ch) {
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        hoverCell = null;

        if (hovered && !panning && !marquee) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                double mvx = screenToVx(mx, x0, cw), mvz = screenToVz(my, y0, ch);
                pxPerUnit = Math.max(1.0, Math.min(1.0e7, pxPerUnit * Math.pow(ZOOM_BASE, wheel)));
                centerVx += mvx - screenToVx(mx, x0, cw);
                centerVz += mvz - screenToVz(my, y0, ch);
            }
        }

        if (hovered && !marquee && ImGui.isMouseClicked(0)) {
            panning = true;
            movedWhilePanning = false;
            panAnchorMx = mx;
            panAnchorMy = my;
            panAnchorVx = centerVx;
            panAnchorVz = centerVz;
        }
        if (panning) {
            if (ImGui.isMouseDown(0)) {
                float dx = mx - panAnchorMx, dy = my - panAnchorMy;
                if (Math.abs(dx) > CLICK_SLOP || Math.abs(dy) > CLICK_SLOP) movedWhilePanning = true;
                centerVx = panAnchorVx - dx / pxPerUnit;
                centerVz = panAnchorVz + dy / pxPerUnit;
            } else {
                panning = false;
                if (!movedWhilePanning) clickApply(panAnchorMx, panAnchorMy, x0, y0, cw, ch);
            }
        }

        if (hovered && !panning && ImGui.isMouseClicked(1)) {
            marquee = true;
            marqStartX = mx;
            marqStartY = my;
            marqCurX = mx;
            marqCurY = my;
        }
        if (marquee) {
            marqCurX = mx;
            marqCurY = my;
            if (ImGui.isMouseReleased(1)) {
                marquee = false;
                commitMarquee(x0, y0, cw, ch);
            }
        }

        if (hovered && !panning && !marquee) {
            double[] cell = snapCell(screenToVx(mx, x0, cw), screenToVz(my, y0, ch));
            if (cell != null) {
                hoverCell = cell;
                showHoverTooltip(cell[0], cell[1]);
            }
        }
    }

    private static int[] fieldNode(FieldSnap s, double vx, double vz) {
        if (s == null || s.cols < 1 || s.rows < 1) return null;
        if (vx < s.vxLo || vx > s.vxHi || vz < s.vzLo || vz > s.vzHi) return null;
        double dvx = (s.vxHi - s.vxLo) / Math.max(1, s.cols - 1);
        double dvz = (s.vzHi - s.vzLo) / Math.max(1, s.rows - 1);
        int c = Math.max(0, Math.min(s.cols - 1, (int) Math.round((vx - s.vxLo) / dvx)));
        int r = Math.max(0, Math.min(s.rows - 1, (int) Math.round((vz - s.vzLo) / dvz)));
        return new int[]{c, r};
    }

    private int[] refineNode(double vx, double vz) {
        VelocityFinder.Candidate[] cells = refineCells;
        VelocityFinder.Grid g = refineGrid;
        if (cells == null || g == null) return null;
        if (vx < g.vxLo - g.vxStep * 0.5 || vx > g.vxHi + g.vxStep * 0.5) return null;
        if (vz < g.vzLo - g.vzStep * 0.5 || vz > g.vzHi + g.vzStep * 0.5) return null;
        int c = (int) Math.round((vx - g.vxLo) / g.vxStep);
        int r = (int) Math.round((vz - g.vzLo) / g.vzStep);
        if (c < 0 || c >= refineCols || r < 0 || r >= refineRows) return null;
        return new int[]{c, r};
    }

    private double[] snapCell(double vx, double vz) {
        int[] ri = refineNode(vx, vz);
        VelocityFinder.Grid g = refineGrid;
        if (ri != null && g != null) {
            return new double[]{g.vxLo + ri[0] * g.vxStep, g.vzLo + ri[1] * g.vzStep, g.vxStep * 0.5, g.vzStep * 0.5};
        }
        FieldSnap s = field;
        int[] fi = fieldNode(s, vx, vz);
        if (fi == null) return null;
        double dvx = (s.vxHi - s.vxLo) / Math.max(1, s.cols - 1);
        double dvz = (s.vzHi - s.vzLo) / Math.max(1, s.rows - 1);
        return new double[]{s.vxLo + fi[0] * dvx, s.vzLo + fi[1] * dvz, dvx * 0.5, dvz * 0.5};
    }

    private void draw2D(ImDrawList dl, FieldSnap s, float x0, float y0, float cw, float ch) {
        int cols = s.cols, rows = s.rows;
        if (cols < 1 || rows < 1) return;
        double dvx = cols > 1 ? (s.vxHi - s.vxLo) / (cols - 1) : (s.vxHi - s.vxLo);
        double dvz = rows > 1 ? (s.vzHi - s.vzLo) / (rows - 1) : (s.vzHi - s.vzLo);
        for (int r = 0; r < rows; r++) {
            double vz = s.vzLo + r * dvz;
            float yTop = vzToScreen(vz + dvz * 0.5, y0, ch);
            float yBot = vzToScreen(vz - dvz * 0.5, y0, ch);
            if (yBot < y0 || yTop > y0 + ch) continue;
            for (int c = 0; c < cols; c++) {
                double vx = s.vxLo + c * dvx;
                float xL = vxToScreen(vx - dvx * 0.5, x0, cw);
                float xR = vxToScreen(vx + dvx * 0.5, x0, cw);
                if (xR < x0 || xL > x0 + cw) continue;
                int idx = r * cols + c;
                float z = s.z[idx];
                int col = Float.isNaN(z)
                        ? (s.cells[idx] != null ? ThemeManager.panelColor() : ThemeManager.bgDarkColor())
                        : colorForField(z, s.negMin, s.negMax, s.posMax);
                dl.addRectFilled(xL, yTop, xR, yBot, col, 0f);
            }
        }
    }

    private void drawZeroAxes(ImDrawList dl, float x0, float y0, float cw, float ch) {
        int col = withAlpha(ThemeManager.borderColor(), 160);
        float sx = vxToScreen(0.0, x0, cw);
        if (sx >= x0 && sx <= x0 + cw) dl.addLine(sx, y0, sx, y0 + ch, col, 1f);
        float sy = vzToScreen(0.0, y0, ch);
        if (sy >= y0 && sy <= y0 + ch) dl.addLine(x0, sy, x0 + cw, sy, col, 1f);
    }

    private void drawOrigin2D(ImDrawList dl, float x0, float y0, float cw, float ch) {
        float sx = vxToScreen(0.0, x0, cw);
        float sy = vzToScreen(0.0, y0, ch);
        if (sx < x0 || sx > x0 + cw || sy < y0 || sy > y0 + ch) return;
        dl.addLine(sx - 6.5f, sy, sx + 6.5f, sy, ThemeManager.peachColor(), 1f);
        dl.addLine(sx, sy - 6.5f, sx, sy + 6.5f, ThemeManager.peachColor(), 1f);
        dl.addCircle(sx, sy, 4.5f, ThemeManager.peachColor(), 16, 1.5f);
        dl.addCircleFilled(sx, sy, 1.3f, ThemeManager.peachColor(), 8);
    }

    private void drawRefineOverlay(ImDrawList dl, float x0, float y0, float cw, float ch) {
        VelocityFinder.Candidate[] cells = refineCells;
        VelocityFinder.Grid g = refineGrid;
        if (cells == null || g == null) return;
        double dvx = g.vxStep, dvz = g.vzStep;
        for (int r = 0; r < refineRows; r++) {
            double vz = g.vzLo + r * dvz;
            float yTop = vzToScreen(vz + dvz * 0.5, y0, ch);
            float yBot = vzToScreen(vz - dvz * 0.5, y0, ch);
            for (int c = 0; c < refineCols; c++) {
                double vx = g.vxLo + c * dvx;
                float xL = vxToScreen(vx - dvx * 0.5, x0, cw);
                float xR = vxToScreen(vx + dvx * 0.5, x0, cw);
                dl.addRectFilled(xL, yTop, xR, yBot, colorFor(cells[r * refineCols + c]), 0f);
            }
        }
        float rx0 = vxToScreen(g.vxLo - dvx * 0.5, x0, cw);
        float rx1 = vxToScreen(g.vxHi + dvx * 0.5, x0, cw);
        float ry0 = vzToScreen(g.vzHi + dvz * 0.5, y0, ch);
        float ry1 = vzToScreen(g.vzLo - dvz * 0.5, y0, ch);
        dl.addRect(rx0, ry0, rx1, ry1, ThemeManager.focusColor(), 0f, 0, 1.5f);
    }

    private void drawHoverCell(ImDrawList dl, float x0, float y0, float cw, float ch) {
        double[] cell = hoverCell;
        if (cell == null) return;
        float xL = vxToScreen(cell[0] - cell[2], x0, cw);
        float xR = vxToScreen(cell[0] + cell[2], x0, cw);
        float yTop = vzToScreen(cell[1] + cell[3], y0, ch);
        float yBot = vzToScreen(cell[1] - cell[3], y0, ch);
        dl.addRect(xL, yTop, xR, yBot, ThemeManager.textColor(), 0f, 0, 1f);
    }

    private void drawMarker(ImDrawList dl, float x0, float y0, float cw, float ch) {
        double[] applied = markerV0Applied;
        double[] entry = markerV0 != null ? markerV0.get() : null;
        double[] m = applied != null ? applied : entry;
        if (m == null) return;
        double[] cell = snapCell(m[0], m[1]);
        if (cell == null) return;
        float xL = vxToScreen(cell[0] - cell[2], x0, cw);
        float xR = vxToScreen(cell[0] + cell[2], x0, cw);
        float yTop = vzToScreen(cell[1] + cell[3], y0, ch);
        float yBot = vzToScreen(cell[1] - cell[3], y0, ch);
        dl.addRect(xL, yTop, xR, yBot, ThemeManager.lockedColor(), 0f, 0, 2f);
    }

    private void drawAxes2D(ImDrawList dl, float x0, float y0, float cw, float ch) {
        float lh = ImGui.getTextLineHeight();
        int name = ThemeManager.textMutedColor();
        int val = ThemeManager.textDimColor();
        String sVxL = fmt(screenToVx(x0, x0, cw)), sVxR = fmt(screenToVx(x0 + cw, x0, cw));
        String sVzB = fmt(screenToVz(y0 + ch, y0, ch)), sVzT = fmt(screenToVz(y0, y0, ch));
        float botY = y0 + ch - lh - 2f;
        dl.addText(x0 + 3f, botY, val, sVxL);
        dl.addText(x0 + cw - ImGui.calcTextSize(sVxR).x - 3f, botY, val, sVxR);
        dl.addText(x0 + cw * 0.5f - ImGui.calcTextSize("vx").x * 0.5f, botY, name, "vx");
        dl.addText(x0 + 3f, y0 + 2f, val, sVzT);
        dl.addText(x0 + 3f, y0 + ch - 2f * lh - 3f, val, sVzB);
        dl.addText(x0 + 3f, y0 + ch * 0.5f, name, "vz");
    }

    private void drawLegend(ImDrawList dl, FieldSnap s, float x0, float y0, float cw, float ch) {
        float lh = ImGui.getTextLineHeight();
        float pad = 6f, gap = 5f, box = lh - 3f, barW = box;
        double best = s != null && s.negMin < 0.0 ? -s.negMin : 0.0;
        double worst = s != null && s.negMax < 0.0 && s.negMax > s.negMin ? -s.negMax : 0.0;
        String topLbl = "lands +" + ConstraintText.fixedStat(best);
        String botLbl = "lands +" + ConstraintText.fixedStat(worst);
        String[] cats = {"misses", "no aim"};
        int[] catCol = {missColor(0.45f), ThemeManager.panelColor()};

        float labelW = Math.max(ImGui.calcTextSize(topLbl).x, ImGui.calcTextSize(botLbl).x);
        for (String cc : cats) labelW = Math.max(labelW, box + gap + ImGui.calcTextSize(cc).x);
        float barH = Math.max(48f, Math.min(ch - 60f, 120f));
        float bw = pad + barW + gap + labelW + pad;
        float bh = pad * 2f + barH + 6f + cats.length * lh;
        float bx = x0 + cw - bw - 6f, by = y0 + 6f;
        dl.addRectFilled(bx, by, bx + bw, by + bh, withAlpha(ThemeManager.bgDarkColor(), 200), 3f);
        dl.addRect(bx, by, bx + bw, by + bh, withAlpha(ThemeManager.borderColor(), 150), 3f, 0, 1f);

        float barX = bx + pad, barTop = by + pad, barBot = barTop + barH;
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float t = 1f - (i + 0.5f) / steps;
            float yT = barTop + i * (barH / steps);
            dl.addRectFilled(barX, yT, barX + barW, yT + barH / steps + 0.5f, landColor(t), 0f);
        }
        dl.addRect(barX, barTop, barX + barW, barBot, withAlpha(ThemeManager.borderColor(), 150), 0f, 0, 1f);
        float lx = barX + barW + gap;
        dl.addText(lx, barTop - 2f, ThemeManager.textColor(), topLbl);
        dl.addText(lx, barBot - lh + 2f, ThemeManager.textMutedColor(), botLbl);

        float cy = barBot + 6f;
        for (int i = 0; i < cats.length; i++) {
            float ry = cy + i * lh;
            dl.addRectFilled(barX, ry + 1.5f, barX + box, ry + 1.5f + box, catCol[i], 2f);
            dl.addText(barX + box + gap, ry, ThemeManager.textMutedColor(), cats[i]);
        }
    }

    private String fmt(double v) {
        return String.format("%.3f", v);
    }

    private void init3DView(float cw, float ch) {
        cube3dScale = Math.min(cw, ch) * 0.32;
        view3dInit = true;
    }

    private void handleInput3D(boolean hovered, float x0, float y0, float cw, float ch) {
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        if (hovered) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0f) {
                cube3dScale = Math.max(8.0, Math.min(1.0e5, cube3dScale * Math.pow(ZOOM_BASE, wheel)));
            }
        }
        if (hovered && ImGui.isMouseClicked(0)) {
            rotating = true;
            movedWhilePanning = false;
            rotAnchorMx = mx;
            rotAnchorMy = my;
            rotAnchorYaw = orbitYaw;
            rotAnchorPitch = orbitPitch;
        }
        if (rotating) {
            if (ImGui.isMouseDown(0)) {
                float dx = mx - rotAnchorMx, dy = my - rotAnchorMy;
                if (Math.abs(dx) > CLICK_SLOP || Math.abs(dy) > CLICK_SLOP) movedWhilePanning = true;
                orbitYaw = rotAnchorYaw + dx * ROT_SPEED;
                orbitPitch = Math.max(0.08, Math.min(1.5, rotAnchorPitch - dy * ROT_SPEED));
            } else {
                rotating = false;
                if (!movedWhilePanning) {
                    VelocityFinder.Candidate cand = candidateAt3D(rotAnchorMx, rotAnchorMy, x0, y0, cw, ch);
                    applyCandidate(cand);
                }
            }
        }
        if (hovered && !rotating) {
            double[] vv = nearestVel3D(mx, my, x0, y0, cw, ch);
            if (vv != null) showHoverTooltip(vv[0], vv[1]);
        }
    }

    private void draw3D(ImDrawList dl, FieldSnap s, float x0, float y0, float cw, float ch) {
        int cols = s.cols, rows = s.rows;
        if (cols < 2 || rows < 2) return;
        double cx = x0 + cw * 0.5, cyc = y0 + ch * 0.5;
        double cp = Math.cos(orbitPitch), sp = Math.sin(orbitPitch);
        double cyaw = Math.cos(orbitYaw), syaw = Math.sin(orbitYaw);
        double halfX = (s.vxHi - s.vxLo) * 0.5, halfZ = (s.vzHi - s.vzLo) * 0.5;
        double vxMid = (s.vxLo + s.vxHi) * 0.5, vzMid = (s.vzLo + s.vzHi) * 0.5;
        double dvx = (s.vxHi - s.vxLo) / (cols - 1), dvz = (s.vzHi - s.vzLo) / (rows - 1);

        int nVerts = cols * rows;
        float[] px = new float[nVerts], py = new float[nVerts];
        double[] dep = new double[nVerts];
        double[] cnx = new double[nVerts], cny = new double[nVerts], cnz = new double[nVerts];
        boolean[] ok = new boolean[nVerts];
        for (int r = 0; r < rows; r++) {
            double ny = halfZ > 0 ? ((s.vzLo + r * dvz) - vzMid) / halfZ : 0;
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                float fv = s.z[i];
                if (Float.isNaN(fv)) continue;
                double nx = halfX > 0 ? ((s.vxLo + c * dvx) - vxMid) / halfX : 0;
                double nz = heightOf(fv, s);
                px[i] = (float) (cx + (nx * (-cyaw) + ny * syaw) * cube3dScale);
                py[i] = (float) (cyc - (nx * (-syaw * sp) + ny * (-cyaw * sp) + nz * cp) * cube3dScale);
                dep[i] = nx * (cp * syaw) + ny * (cp * cyaw) + nz * sp;
                cnx[i] = nx;
                cny[i] = ny;
                cnz[i] = nz;
                ok[i] = true;
            }
        }

        int cellsX = cols - 1, cellsY = rows - 1;
        int cap = cellsX * cellsY * 2;
        long[] keys = new long[cap];
        int[] ta = new int[cap], tb = new int[cap], tc = new int[cap];
        int nt = 0;
        for (int r = 0; r < cellsY; r++) {
            for (int c = 0; c < cellsX; c++) {
                int i00 = r * cols + c, i10 = i00 + 1, i01 = i00 + cols, i11 = i01 + 1;
                if (ok[i00] && ok[i10] && ok[i11]) nt = pushTri(keys, ta, tb, tc, nt, i00, i10, i11, dep);
                if (ok[i00] && ok[i11] && ok[i01]) nt = pushTri(keys, ta, tb, tc, nt, i00, i11, i01, dep);
            }
        }
        long[] order = Arrays.copyOf(keys, nt);
        Arrays.sort(order);
        for (long k : order) {
            int slot = (int) (k & 0xFFFFF);
            int a = ta[slot], b = tb[slot], cc = tc[slot];
            double avgField = (s.z[a] + s.z[b] + s.z[cc]) / 3.0;
            int col = shadeColor(colorForField(avgField, s.negMin, s.negMax, s.posMax),
                    faceShade(cnx, cny, cnz, a, b, cc));
            dl.addTriangleFilled(px[a], py[a], px[b], py[b], px[cc], py[cc], col);
        }

        drawCubeFrame(dl, cx, cyc, cp, sp, cyaw, syaw);
        drawOrigin3D(dl, cx, cyc, cp, sp, cyaw, syaw, halfX, halfZ, vxMid, vzMid);
        drawMarker3D(dl, s, cx, cyc, cp, sp, cyaw, syaw, halfX, halfZ, vxMid, vzMid);
        drawCubeLabels(dl, s, cx, cyc, cp, sp, cyaw, syaw);
    }

    private void drawCubeLabels(ImDrawList dl, FieldSnap s, double cx, double cyc,
                               double cp, double sp, double cyaw, double syaw) {
        double h = HEIGHT_SCALE;
        int name = ThemeManager.textMutedColor();
        int val = ThemeManager.textDimColor();
        float[] vxMid = projCube(0, -1, -h, cx, cyc, cp, sp, cyaw, syaw);
        float[] vxEnd = projCube(1, -1, -h, cx, cyc, cp, sp, cyaw, syaw);
        float[] vzMid = projCube(-1, 0, -h, cx, cyc, cp, sp, cyaw, syaw);
        float[] vzEnd = projCube(-1, 1, -h, cx, cyc, cp, sp, cyaw, syaw);
        float[] fTop = projCube(-1, -1, h, cx, cyc, cp, sp, cyaw, syaw);
        dl.addText(vxMid[0], vxMid[1], name, "vx");
        dl.addText(vxEnd[0], vxEnd[1], val, fmt(s.vxHi));
        dl.addText(vzMid[0], vzMid[1], name, "vz");
        dl.addText(vzEnd[0], vzEnd[1], val, fmt(s.vzHi));
        dl.addText(fTop[0], fTop[1], name, "field");
    }

    private int pushTri(long[] keys, int[] ta, int[] tb, int[] tc, int nt, int a, int b, int c, double[] dep) {
        double avgd = (dep[a] + dep[b] + dep[c]) / 3.0;
        int q = (int) ((avgd + 4.0) * 100000.0);
        if (q < 0) q = 0;
        if (q > 0xFFFFF) q = 0xFFFFF;
        keys[nt] = ((long) q << 20) | nt;
        ta[nt] = a;
        tb[nt] = b;
        tc[nt] = c;
        return nt + 1;
    }

    private double faceShade(double[] nx, double[] ny, double[] nz, int a, int b, int c) {
        double ux = nx[b] - nx[a], uy = ny[b] - ny[a], uz = nz[b] - nz[a];
        double vx = nx[c] - nx[a], vy = ny[c] - ny[a], vz = nz[c] - nz[a];
        double rx = uy * vz - uz * vy, ry = uz * vx - ux * vz, rz = ux * vy - uy * vx;
        double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-12) return 1.0;
        double ndl = Math.abs((rx * 0.351 + ry * 0.251 + rz * 0.902) / len);
        return 0.5 + 0.5 * ndl;
    }

    private double heightOf(double field, FieldSnap s) {
        double hNorm = Math.max(1e-6, Math.max(Math.sqrt(Math.abs(s.negMin)), Math.sqrt(Math.abs(s.posMax))));
        double comp = field < 0 ? -Math.sqrt(-field) : Math.sqrt(field);
        return (comp / hNorm) * HEIGHT_SCALE;
    }

    private float[] projCube(double nx, double ny, double nz, double cx, double cyc,
                            double cp, double sp, double cyaw, double syaw) {
        float sx = (float) (cx + (nx * (-cyaw) + ny * syaw) * cube3dScale);
        float sy = (float) (cyc - (nx * (-syaw * sp) + ny * (-cyaw * sp) + nz * cp) * cube3dScale);
        return new float[]{sx, sy};
    }

    private void drawCubeFrame(ImDrawList dl, double cx, double cyc, double cp, double sp, double cyaw, double syaw) {
        double h = HEIGHT_SCALE;
        double[][] cn = {
                {-1, -1, -h}, {1, -1, -h}, {1, 1, -h}, {-1, 1, -h},
                {-1, -1, h}, {1, -1, h}, {1, 1, h}, {-1, 1, h}
        };
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) p[i] = projCube(cn[i][0], cn[i][1], cn[i][2], cx, cyc, cp, sp, cyaw, syaw);
        int col = withAlpha(ThemeManager.borderColor(), 150);
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) dl.addLine(p[e[0]][0], p[e[0]][1], p[e[1]][0], p[e[1]][1], col, 1f);
    }

    private void drawOrigin3D(ImDrawList dl, double cx, double cyc, double cp, double sp,
                             double cyaw, double syaw, double halfX, double halfZ, double vxMid, double vzMid) {
        if (halfX <= 0 || halfZ <= 0) return;
        if (Math.abs(0.0 - vxMid) > halfX || Math.abs(0.0 - vzMid) > halfZ) return;
        double nx = (0.0 - vxMid) / halfX;
        double ny = (0.0 - vzMid) / halfZ;
        float[] pb = projCube(nx, ny, -HEIGHT_SCALE, cx, cyc, cp, sp, cyaw, syaw);
        dl.addCircle(pb[0], pb[1], 4f, ThemeManager.peachColor(), 14, 1.5f);
        dl.addCircleFilled(pb[0], pb[1], 1.3f, ThemeManager.peachColor(), 8);
    }

    private void drawMarker3D(ImDrawList dl, FieldSnap s, double cx, double cyc, double cp, double sp,
                             double cyaw, double syaw, double halfX, double halfZ, double vxMid, double vzMid) {
        if (markerV0 == null) return;
        double[] m = markerV0.get();
        if (m == null) return;
        double nx = halfX > 0 ? Math.max(-1.05, Math.min(1.05, (m[0] - vxMid) / halfX)) : 0;
        double ny = halfZ > 0 ? Math.max(-1.05, Math.min(1.05, (m[1] - vzMid) / halfZ)) : 0;
        float[] pf = projCube(nx, ny, -HEIGHT_SCALE, cx, cyc, cp, sp, cyaw, syaw);
        float[] pt = projCube(nx, ny, HEIGHT_SCALE, cx, cyc, cp, sp, cyaw, syaw);
        int col = withAlpha(ThemeManager.lockedColor(), 230);
        dl.addLine(pf[0], pf[1], pt[0], pt[1], col, 1.5f);
        dl.addCircleFilled(pt[0], pt[1], 2.5f, col, 10);
    }

    private double[] nearestVel3D(float mx, float my, float x0, float y0, float cw, float ch) {
        FieldSnap s = field;
        if (s == null || s.cols < 2 || s.rows < 2) return null;
        double cx = x0 + cw * 0.5, cyc = y0 + ch * 0.5;
        double cp = Math.cos(orbitPitch), sp = Math.sin(orbitPitch);
        double cyaw = Math.cos(orbitYaw), syaw = Math.sin(orbitYaw);
        double halfX = (s.vxHi - s.vxLo) * 0.5, halfZ = (s.vzHi - s.vzLo) * 0.5;
        double vxMid = (s.vxLo + s.vxHi) * 0.5, vzMid = (s.vzLo + s.vzHi) * 0.5;
        double dvx = (s.vxHi - s.vxLo) / (s.cols - 1), dvz = (s.vzHi - s.vzLo) / (s.rows - 1);
        double best = 30.0 * 30.0;
        int bc = -1, br = -1;
        for (int r = 0; r < s.rows; r++) {
            double ny = halfZ > 0 ? ((s.vzLo + r * dvz) - vzMid) / halfZ : 0;
            for (int c = 0; c < s.cols; c++) {
                float fv = s.z[r * s.cols + c];
                if (Float.isNaN(fv)) continue;
                double nx = halfX > 0 ? ((s.vxLo + c * dvx) - vxMid) / halfX : 0;
                double nz = heightOf(fv, s);
                float pxs = (float) (cx + (nx * (-cyaw) + ny * syaw) * cube3dScale);
                float pys = (float) (cyc - (nx * (-syaw * sp) + ny * (-cyaw * sp) + nz * cp) * cube3dScale);
                double d = (pxs - mx) * (pxs - mx) + (pys - my) * (pys - my);
                if (d < best) {
                    best = d;
                    bc = c;
                    br = r;
                }
            }
        }
        if (bc < 0) return null;
        return new double[]{s.vxLo + bc * dvx, s.vzLo + br * dvz};
    }

    private VelocityFinder.Candidate candidateAt3D(float mx, float my, float x0, float y0, float cw, float ch) {
        double[] vv = nearestVel3D(mx, my, x0, y0, cw, ch);
        return vv == null ? null : candidateFor(vv[0], vv[1]);
    }

    private VelocityFinder.Candidate candidateFor(double vx, double vz) {
        VelocityFinder.Candidate rc = refineCellAt(vx, vz);
        if (rc != null) return rc;
        return fieldCellAt(field, vx, vz);
    }

    private VelocityFinder.Candidate fieldCellAt(FieldSnap s, double vx, double vz) {
        int[] idx = fieldNode(s, vx, vz);
        if (idx == null || s.cells == null) return null;
        return s.cells[idx[1] * s.cols + idx[0]];
    }

    private int shadeColor(int c, double f) {
        int r = (int) ((c & 0xFF) * f);
        int g = (int) (((c >> 8) & 0xFF) * f);
        int b = (int) (((c >> 16) & 0xFF) * f);
        int a = (c >> 24) & 0xFF;
        return packRGBA(Math.min(255, r), Math.min(255, g), Math.min(255, b), a);
    }

    private void drawMarquee(ImDrawList dl) {
        float ax = Math.min(marqStartX, marqCurX), bx = Math.max(marqStartX, marqCurX);
        float ay = Math.min(marqStartY, marqCurY), by = Math.max(marqStartY, marqCurY);
        dl.addRectFilled(ax, ay, bx, by, ThemeManager.accentTintColor(0.15f), 0f);
        dl.addRect(ax, ay, bx, by, ThemeManager.focusColor(), 0f, 0, 1.2f);
    }

    private void commitMarquee(float x0, float y0, float cw, float ch) {
        double a = screenToVx(marqStartX, x0, cw), b = screenToVx(marqCurX, x0, cw);
        double cc = screenToVz(marqStartY, y0, ch), d = screenToVz(marqCurY, y0, ch);
        double vxLo = Math.min(a, b), vxHi = Math.max(a, b);
        double vzLo = Math.min(cc, d), vzHi = Math.max(cc, d);
        if (vxHi - vxLo < 1e-9 || vzHi - vzLo < 1e-9) return;
        startRefine(vxLo, vxHi, vzLo, vzHi);
    }

    private void startRefine(double vxLo, double vxHi, double vzLo, double vzHi) {
        if (finder == null) return;
        refineCancel.set(true);
        if (refineWorker != null) {
            try {
                refineWorker.join(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        refineCancel.set(false);
        double vxStep = (vxHi - vxLo) / (REFINE_CELLS - 1);
        double vzStep = (vzHi - vzLo) / (REFINE_CELLS - 1);
        VelocityFinder.Grid g = new VelocityFinder.Grid(vxLo, vxHi, vxStep, vzLo, vzHi, vzStep);
        int cols = VelocityFinder.cols(g), rows = VelocityFinder.rows(g);
        VelocityFinder.Candidate[] buf = new VelocityFinder.Candidate[cols * rows];
        refineCols = cols;
        refineRows = rows;
        refineGrid = g;
        refineCells = buf;
        refineWorker = new Thread(() -> finder.sweepParallel(g, threads, refineCancel,
                (r, c, cand) -> buf[r * cols + c] = cand), "velocity-refine");
        refineWorker.setDaemon(true);
        refineWorker.start();
    }

    private void maybeResample(float x0, float y0, float cw, float ch) {
        boolean viewMoving = centerVx != lastVx || centerVz != lastVz || pxPerUnit != lastPx;
        lastVx = centerVx;
        lastVz = centerVz;
        lastPx = pxPerUnit;
        long now = System.nanoTime();
        if (viewMoving) {
            lastViewMoveNanos = now;
            if (fieldRunning && currentSweepIsResample) cancelCurrentSweep();
        }
        FieldSnap snap = field;
        if (snap == null || finder == null || fieldRunning) return;
        if (panning || marquee) return;
        if (now - lastViewMoveNanos < RESAMPLE_SETTLE_NANOS) return;
        if (centerVx == lastResampleVx && centerVz == lastResampleVz && pxPerUnit == lastResamplePx) return;
        double vx0 = screenToVx(x0, x0, cw), vx1 = screenToVx(x0 + cw, x0, cw);
        double vz0 = screenToVz(y0 + ch, y0, ch), vz1 = screenToVz(y0, y0, ch);
        double cx0 = vx0, cx1 = vx1, cz0 = vz0, cz1 = vz1;
        if (rangeEnabled.get() && rangeValid) {
            cx0 = Math.max(cx0, rangeVxLo);
            cx1 = Math.min(cx1, rangeVxHi);
            cz0 = Math.max(cz0, rangeVzLo);
            cz1 = Math.min(cz1, rangeVzHi);
        }
        if (cx1 - cx0 < 1e-9 || cz1 - cz0 < 1e-9) return;
        if (!viewportDiffers(snap, cx0, cx1, cz0, cz1)) return;
        double mx = (vx1 - vx0) * 0.12, mz = (vz1 - vz0) * 0.12;
        startSweep(vx0 - mx, vx1 + mx, vz0 - mz, vz1 + mz, true, true);
    }

    private boolean viewportDiffers(FieldSnap s, double vx0, double vx1, double vz0, double vz1) {
        double sx = s.vxHi - s.vxLo, sz = s.vzHi - s.vzLo;
        if (sx <= 0 || sz <= 0) return true;
        double rx = (vx1 - vx0) / sx, rz = (vz1 - vz0) / sz;
        if (rx < 0.78 || rx > 1.25 || rz < 0.78 || rz > 1.25) return true;
        double cxShift = Math.abs((vx0 + vx1) * 0.5 - (s.vxLo + s.vxHi) * 0.5);
        double czShift = Math.abs((vz0 + vz1) * 0.5 - (s.vzLo + s.vzHi) * 0.5);
        return cxShift > sx * 0.2 || czShift > sz * 0.2;
    }

    private void clickApply(float sx, float sy, float x0, float y0, float cw, float ch) {
        applyCandidate(candidateAt(sx, sy, x0, y0, cw, ch));
    }

    private void applyCandidate(VelocityFinder.Candidate cand) {
        if (cand == null || !cand.lands) return;
        markerV0Applied = new double[]{cand.vx, cand.vz};
        onApply.accept(cand);
    }

    private VelocityFinder.Candidate candidateAt(float sx, float sy, float x0, float y0, float cw, float ch) {
        return candidateFor(screenToVx(sx, x0, cw), screenToVz(sy, y0, ch));
    }

    private VelocityFinder.Candidate refineCellAt(double vx, double vz) {
        int[] idx = refineNode(vx, vz);
        VelocityFinder.Candidate[] cells = refineCells;
        if (idx == null || cells == null) return null;
        return cells[idx[1] * refineCols + idx[0]];
    }

    private void showHoverTooltip(double vx, double vz) {
        VelocityFinder.Candidate rc = refineCellAt(vx, vz);
        if (rc != null) {
            showTooltip(vx, vz, rc);
            return;
        }
        VelocityFinder.Candidate cell = fieldCellAt(field, vx, vz);
        Float f = sampleField(field, vx, vz);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("v0 = (%+.4f, %+.4f)%n", vx, vz));
        if (cell != null && cell.constraintsMet) {
            appendLanding(sb, cell.landX, cell.landZ, cell.lands);
        } else if (f == null || Float.isNaN(f)) {
            sb.append("no solution");
        } else {
            sb.append("misses pad");
        }
        ImGui.setTooltip(sb.toString());
    }

    private String objAxisName() {
        return finder != null && !finder.objectiveIsX() ? "Z" : "X";
    }

    private void appendLanding(StringBuilder sb, double landX, double landZ, boolean lands) {
        if (finder == null || Double.isNaN(landX) || Double.isNaN(landZ)) {
            sb.append("no solution");
            return;
        }
        double off = Math.abs(finder.constraintOffset(landX, landZ));
        off = lands ? off : -off;
        String tail = lands ? "  (click to apply)" : "  (miss)";
        int p = ConstraintText.precision();
        String fmtStr = "goal  X=%." + p + "f  Z=%." + p + "f%noffset %+." + p + "f (%s, vs %." + p + "f)%s";
        sb.append(String.format(Locale.ROOT, fmtStr, landX, landZ, off, objAxisName(), finder.constraintEdge(), tail));
    }

    private Float sampleField(FieldSnap s, double vx, double vz) {
        int[] idx = fieldNode(s, vx, vz);
        if (idx == null || s.z == null) return null;
        return s.z[idx[1] * s.cols + idx[0]];
    }

    private void showTooltip(double vx, double vz, VelocityFinder.Candidate c) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("v0 = (%+.4f, %+.4f)%n", vx, vz));
        if (c == null || !c.constraintsMet) sb.append("no solution");
        else appendLanding(sb, c.landX, c.landZ, c.lands);
        ImGui.setTooltip(sb.toString());
    }

    private float vxToScreen(double vx, float x0, float cw) {
        return x0 + cw * 0.5f + (float) ((vx - centerVx) * pxPerUnit);
    }

    private float vzToScreen(double vz, float y0, float ch) {
        return y0 + ch * 0.5f - (float) ((vz - centerVz) * pxPerUnit);
    }

    private double screenToVx(float sx, float x0, float cw) {
        return centerVx + (sx - (x0 + cw * 0.5f)) / pxPerUnit;
    }

    private double screenToVz(float sy, float y0, float ch) {
        return centerVz - (sy - (y0 + ch * 0.5f)) / pxPerUnit;
    }

    private int colorForField(double f, double negMin, double negMax, double posMax) {
        if (Double.isNaN(f)) return ThemeManager.bgDarkColor();
        if (f < 0.0) {
            return landColor(supportT(f, negMin, negMax));
        }
        return missColor(missT(f, posMax));
    }

    private static float missT(double f, double posMax) {
        return posMax > 1e-9 ? (float) Math.min(1.0, f / posMax) : 0f;
    }

    private int missColor(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int lo = packRGBA(255, 90, 0, 255);
        int hi = packRGBA(120, 0, 0, 255);
        return lerpColor(lo, hi, t);
    }

    private static float supportT(double f, double negMin, double negMax) {
        if (negMax > negMin && negMax < 0.0) {
            return (float) ((f - negMax) / (negMin - negMax));
        }
        return negMin < 0.0 ? (float) (f / negMin) : 0f;
    }

    private int landColor(float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int lo = packRGBA(255, 120, 0, 255);
        int mid = packRGBA(240, 200, 0, 255);
        int hi = packRGBA(0, 210, 40, 255);
        return t < 0.5f ? lerpColor(lo, mid, t * 2f) : lerpColor(mid, hi, (t - 0.5f) * 2f);
    }

    private int colorFor(VelocityFinder.Candidate c) {
        if (c == null) return ThemeManager.bgDarkColor();
        if (!c.constraintsMet) return ThemeManager.panelColor();
        double f = finder == null ? 0.0 : finder.landingField(c.landX, c.landZ);
        FieldSnap s = field;
        double negMin = s != null ? s.negMin : -MAX_SUPPORT;
        double negMax = s != null ? s.negMax : Double.NEGATIVE_INFINITY;
        double posMax = s != null ? s.posMax : 0.0;
        return colorForField(f, negMin, negMax, posMax);
    }

    private static int packRGBA(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int lerpColor(int a, int b, float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int ra = a & 0xFF, ga = (a >> 8) & 0xFF, ba = (a >> 16) & 0xFF, aa = (a >> 24) & 0xFF;
        int rb = b & 0xFF, gb = (b >> 8) & 0xFF, bb = (b >> 16) & 0xFF, ab = (b >> 24) & 0xFF;
        return packRGBA((int) (ra + (rb - ra) * t), (int) (ga + (gb - ga) * t),
                (int) (ba + (bb - ba) * t), (int) (aa + (ab - aa) * t));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }
}
