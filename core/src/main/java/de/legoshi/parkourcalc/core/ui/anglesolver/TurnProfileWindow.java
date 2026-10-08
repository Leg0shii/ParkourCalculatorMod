package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.TurnTiming;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.Locale;

public final class TurnProfileWindow implements RenderInterface {

    private static final String WINDOW_ID = "###onejump";
    private static final String TITLE = "Onejump Turn Profile";
    private static final float WIN_W = 720f;
    private static final float WIN_H = 380f;
    private static final float MIN_W = 420f;
    private static final float MIN_H = 260f;
    private static final float GRAPH_MIN_H = 120f;
    private static final float PAD_LEFT = 44f;
    private static final float PAD_RIGHT = 14f;
    private static final float PAD_TOP = 14f;
    private static final float PAD_BOTTOM = 26f;
    private static final float DOT_RADIUS = 3.5f;
    private static final float LINE_WIDTH = 2f;
    private static final float SAMPLE_SIZE = 1.5f;
    private static final float SAMPLE_SPREAD = 0.3f;
    private static final float DENSITY_REACH = 3f;
    private static final float DENSITY_ALPHA_MIN = 0.22f;
    private static final double MIN_SPAN_DEG = 20.0;

    private final TurnProfileController controller;
    private final AttemptTracker tracker;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final ImBoolean open = new ImBoolean(false);

    public TurnProfileWindow(TurnProfileController controller, AttemptTracker tracker, Settings settings,
                             Runnable onSettingsChanged) {
        this.controller = controller;
        this.tracker = tracker;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        render(io, false);
    }

    @Override
    public void renderDetached(ImGuiIO io) {
        render(io, true);
    }

    private void render(ImGuiIO io, boolean graphOnly) {
        open.set(settings.viewTurnProfile);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible;
        if (graphOnly) {
            visible = ImGui.begin(WINDOW_ID, open, flags | ImGuiWindowFlags.NoTitleBar);
        } else {
            ThemeManager.pushHeaderChrome();
            visible = ImGui.begin(WINDOW_ID, open, flags);
            if (visible) ThemeManager.drawModalTitle(TITLE);
            ThemeManager.popHeaderChrome();
        }
        if (visible) body(scale);
        ImGui.end();
        if (settings.viewTurnProfile != open.get()) {
            settings.viewTurnProfile = open.get();
            onSettingsChanged.run();
        }
    }

    private void body(float scale) {
        TurnProfileController.Current cur = controller.current();
        float graphH = Math.max(GRAPH_MIN_H * scale, ImGui.getContentRegionAvail().y);
        if (cur == null || cur.n == 0) {
            placeholder(graphH, cur != null && cur.isFast() ? "Fast onejump: mark Keys and Face ticks in the input table for a turn profile"
                    : controller.lastError() != null ? controller.lastError()
                    : "No reference yet: set the onejump up in the Onejump Setup window");
            return;
        }
        graph(cur, scale, graphH, tracker.shownAttempt());
    }

    private void placeholder(float h, String text) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        float w = ImGui.getContentRegionAvail().x;
        ImGui.invisibleButton("##onejumpEmpty", Math.max(1f, w), h);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(origin.x, origin.y, origin.x + w, origin.y + h, ThemeManager.bgDarkColor(), 0f);
        ImVec2 ts = ImGui.calcTextSize(text);
        dl.addText(origin.x + (w - ts.x) * 0.5f, origin.y + (h - ts.y) * 0.5f, ThemeManager.textDimColor(), text);
    }

    private static double youError(TurnProfileController.Current cur, TurnAttempt you, int t) {
        return you.errorAt(cur, cur.startTick + t);
    }

    private static float[] tickX(TurnProfileController.Current cur, float plotX, float plotW, float[] dxOut) {
        int n = cur.n;
        int m = 0;
        for (int t = 0; t < n; t++) if (cur.checkYaw[t]) m++;
        boolean all = m < 2;
        int count = all ? n : m;
        float dx = count > 1 ? plotW / (count - 1) : 0f;
        float[] xs = new float[n];
        int idx = 0;
        for (int t = 0; t < n; t++) {
            if (all || cur.checkYaw[t]) {
                xs[t] = plotX + idx * dx;
                idx++;
            } else {
                xs[t] = Float.NaN;
            }
        }
        dxOut[0] = dx;
        return xs;
    }

    private static double[] unwrapped(double[] facing) {
        double[] out = new double[facing.length];
        for (int t = 0; t < facing.length; t++) {
            out[t] = t == 0 ? facing[0] : out[t - 1] + Angles.wrapDelta(facing[t] - facing[t - 1]);
        }
        return out;
    }

    private void graph(TurnProfileController.Current cur, float scale, float ch, TurnAttempt you) {
        double[] facing = unwrapped(cur.facing);
        AttemptSampler.Stats st = settings.turnProfileShowRating ? cur.attempts : null;
        float cw = Math.max(80f, ImGui.getContentRegionAvail().x);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x, y0 = origin.y;
        ImGui.invisibleButton("##onejumpGraph", cw, ch);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x0 + cw, y0 + ch, ThemeManager.bgDarkColor(), 0f);

        float labelH = ImGui.getTextLineHeight() + 2f * scale;
        float padL = PAD_LEFT * scale, padR = PAD_RIGHT * scale, padT = PAD_TOP * scale + labelH, padB = PAD_BOTTOM * scale;
        float markY = y0 + 1f + labelH;
        float plotX = x0 + padL, plotW = cw - padL - padR;
        float plotY = y0 + padT, plotH = ch - padT - padB;
        int n = cur.n;
        float[] dxOut = new float[1];
        float[] xs = tickX(cur, plotX, plotW, dxOut);
        float dx = dxOut[0];
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < n; t++) {
            if (Float.isNaN(xs[t])) continue;
            lo = Math.min(lo, facing[t]);
            hi = Math.max(hi, facing[t]);
        }
        if (st != null) {
            for (double[][] set : new double[][][]{st.landed, st.failed}) {
                for (double[] a : set) for (int t = 0; t < n; t++) {
                    if (Float.isNaN(xs[t])) continue;
                    double v = facing[t] + Angles.wrapDelta(a[t] - facing[t]);
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
        }
        if (you != null) {
            for (int t = 0; t < n; t++) {
                if (Float.isNaN(xs[t])) continue;
                double e = youError(cur, you, t);
                if (Double.isNaN(e)) continue;
                lo = Math.min(lo, facing[t] + e);
                hi = Math.max(hi, facing[t] + e);
            }
        }
        if (hi - lo < MIN_SPAN_DEG) {
            double mid = 0.5 * (hi + lo);
            lo = mid - MIN_SPAN_DEG / 2;
            hi = mid + MIN_SPAN_DEG / 2;
        }
        double span = hi - lo;
        lo -= span * 0.08;
        hi += span * 0.08;
        final double yLo = lo, yHi = hi;

        double step = gridStep(yHi - yLo);
        int textCol = ThemeManager.textMutedColor();
        int gridCol = ThemeManager.borderColor();
        for (double g = Math.ceil(yLo / step) * step; g <= yHi; g += step) {
            float gy = yOf(g, yLo, yHi, plotY, plotH);
            dl.addLine(plotX, gy, plotX + plotW, gy, gridCol, 1f);
            String lbl = String.format(Locale.ROOT, "%.0f", Angles.wrap(g));
            dl.addText(plotX - 6f * scale - ImGui.calcTextSize(lbl).x, gy - ImGui.getTextLineHeight() * 0.5f, textCol, lbl);
        }

        if (st != null) {
            int areaFill = ThemeManager.okTintColor(0.18f);
            int areaEdge = ThemeManager.okTintColor(0.7f);
            if (st.landings > 0) {
                int prev = -1;
                for (int t = 0; t < n; t++) {
                    if (Float.isNaN(xs[t])) continue;
                    if (prev >= 0) {
                        float xa = xs[prev], xb = xs[t];
                        float ta = yOf(facing[prev] + st.landedHi[prev], yLo, yHi, plotY, plotH);
                        float tb = yOf(facing[t] + st.landedHi[t], yLo, yHi, plotY, plotH);
                        float ba = yOf(facing[prev] + st.landedLo[prev], yLo, yHi, plotY, plotH);
                        float bb = yOf(facing[t] + st.landedLo[t], yLo, yHi, plotY, plotH);
                        dl.addQuadFilled(xa, ta, xb, tb, xb, bb, xa, ba, areaFill);
                        dl.addLine(xa, ta, xb, tb, areaEdge, 1f);
                        dl.addLine(xa, ba, xb, bb, areaEdge, 1f);
                    }
                    prev = t;
                }
            }
            float half = SAMPLE_SIZE * scale;
            float spread = SAMPLE_SPREAD * dx;
            scatter(dl, st.failed, cur, facing, xs, spread, half, yLo, yHi, plotY, plotH, ThemeManager.dangerTintColor(0.35f));
            scatter(dl, st.landed, cur, facing, xs, spread, half, yLo, yHi, plotY, plotH, ThemeManager.okTintColor(0.85f));
        }

        int jumpCol = ThemeManager.peachTintColor(0.7f);
        for (int t = 0; t < n; t++) {
            if (!cur.jumpTicks[t] || Float.isNaN(xs[t])) continue;
            dl.addLine(xs[t], plotY, xs[t], plotY + plotH, jumpCol, 1f);
            dl.addText(xs[t] - ImGui.calcTextSize("jump").x * 0.5f, markY, jumpCol, "jump");
        }
        if (cur.landing != null) {
            int lt = cur.landing.tick - cur.startTick;
            if (lt >= 0 && lt < n && !Float.isNaN(xs[lt])) {
                int landCol = ThemeManager.okTintColor(0.6f);
                dl.addLine(xs[lt], plotY, xs[lt], plotY + plotH, landCol, 1f);
                dl.addText(xs[lt] - ImGui.calcTextSize("land").x * 0.5f, markY, landCol, "land");
            }
        }
        if (you != null && you.failedTick() >= 0) {
            int lt = you.failedTick() - cur.startTick;
            if (lt >= 0 && lt < n && !Float.isNaN(xs[lt])) {
                int failedCol = ThemeManager.dangerTintColor(0.8f);
                dl.addLine(xs[lt], plotY, xs[lt], plotY + plotH, failedCol, 1f);
                dl.addText(xs[lt] - ImGui.calcTextSize("failed").x * 0.5f, plotY + 2f * scale, failedCol, "failed");
            }
        }
        if (you != null && you.hasForecast() && settings.onejumpOffsetLive) {
            String label = offsetLabel(you);
            if (label != null) {
                boolean failed = you.bestMarginAt(you.lastForecastTick()) > 0.0;
                int col = failed ? ThemeManager.dangerColor() : ThemeManager.okColor();
                Fonts.pushBold();
                dl.addText(x0 + 6f * scale, y0 + 1f, col, label);
                Fonts.popBold();
            }
        }

        int lineCol = ThemeManager.accentColor();
        int prev = -1;
        for (int t = 0; t < n; t++) {
            if (Float.isNaN(xs[t])) continue;
            if (prev >= 0) {
                dl.addLine(xs[prev], yOf(facing[prev], yLo, yHi, plotY, plotH), xs[t],
                        yOf(facing[t], yLo, yHi, plotY, plotH), lineCol, LINE_WIDTH * scale);
            }
            prev = t;
        }
        for (int t = 0; t < n; t++) {
            if (Float.isNaN(xs[t])) continue;
            dl.addCircleFilled(xs[t], yOf(facing[t], yLo, yHi, plotY, plotH), DOT_RADIUS * scale, dotColor(cur, t), 12);
            if (cur.still[t]) {
                dl.addCircle(xs[t], yOf(facing[t], yLo, yHi, plotY, plotH), DOT_RADIUS * 1.8f * scale,
                        ThemeManager.textMutedColor(), 16, 1f * scale);
            }
        }

        if (you != null) {
            int youCol = youColor(you);
            boolean timed = settings.onejumpTurnTiming && you.hasTiming();
            float lineW = LINE_WIDTH * scale;
            float prevX = 0f, prevY = 0f;
            boolean havePrev = false;
            for (int t = 0; t < n; t++) {
                boolean shown = !Float.isNaN(xs[t]);
                float x = shown ? xs[t] : timed ? timeX(xs, t, 0f) : Float.NaN;
                if (Float.isNaN(x)) continue;
                double e = youError(cur, you, t);
                if (Double.isNaN(e)) {
                    havePrev = false;
                    continue;
                }
                float y = yOf(facing[t] + e, yLo, yHi, plotY, plotH);
                if (havePrev) dl.addLine(prevX, prevY, x, y, youCol, lineW);
                if (shown) dl.addCircleFilled(x, y, DOT_RADIUS * 0.7f * scale, youCol, 10);
                prevX = x;
                prevY = y;
                havePrev = true;
                if (!timed) continue;
                int tick = cur.startTick + t;
                float on = you.turnStartAt(tick);
                if (Float.isNaN(on)) continue;
                float[] tr = you.traceAt(tick);
                if (tr != null) {
                    for (int i = 0; i + 1 < tr.length; i += 2) {
                        float px = timeX(xs, t, tr[i]);
                        if (Float.isNaN(px)) continue;
                        float py = yOf(facing[t] + Angles.wrapDelta(tr[i + 1] - facing[t]), yLo, yHi, plotY, plotH);
                        dl.addLine(prevX, prevY, px, py, youCol, lineW);
                        prevX = px;
                        prevY = py;
                    }
                } else if (t + 1 < n && !Double.isNaN(youError(cur, you, t + 1))) {
                    float ax = timeX(xs, t, on), bx = timeX(xs, t, you.turnEndAt(tick));
                    if (!Float.isNaN(ax) && !Float.isNaN(bx)) {
                        float ny = yOf(facing[t + 1] + youError(cur, you, t + 1), yLo, yHi, plotY, plotH);
                        dl.addLine(prevX, prevY, ax, y, youCol, lineW);
                        dl.addLine(ax, y, bx, ny, youCol, lineW);
                        prevX = bx;
                        prevY = ny;
                    }
                }
                float ox = timeX(xs, t, on);
                if (!Float.isNaN(ox)) dl.addCircle(ox, y, DOT_RADIUS * 0.9f * scale, youCol, 12, 1.5f * scale);
            }
        }

        int labelEvery = Math.max(1, (int) Math.ceil(ImGui.calcTextSize("000").x * 1.4f / Math.max(1f, dx)));
        int shownIdx = 0;
        int lastShown = -1;
        for (int t = n - 1; t >= 0; t--) if (!Float.isNaN(xs[t])) { lastShown = t; break; }
        for (int t = 0; t < n; t++) {
            if (Float.isNaN(xs[t])) continue;
            boolean label = shownIdx % labelEvery == 0 || t == lastShown;
            shownIdx++;
            if (!label) continue;
            String lbl = Integer.toString(cur.tasTick(t) + 1);
            dl.addText(xs[t] - ImGui.calcTextSize(lbl).x * 0.5f, plotY + plotH + 4f * scale,
                    cur.jumpTicks[t] ? jumpCol : textCol, lbl);
        }

        if (hovered && n > 0) {
            float mx = ImGui.getMousePosX();
            int t = -1;
            for (int i = 0; i < n; i++) {
                if (Float.isNaN(xs[i])) continue;
                if (t < 0 || Math.abs(xs[i] - mx) < Math.abs(xs[t] - mx)) t = i;
            }
            if (t >= 0) {
                dl.addLine(xs[t], plotY, xs[t], plotY + plotH, ThemeManager.textDimColor(), 1f);
                tooltip(cur, t, you);
            }
        }
    }

    private static void scatter(ImDrawList dl, double[][] samples, TurnProfileController.Current cur, double[] facing,
                                float[] xs, float spread, float half, double yLo, double yHi, float plotY, float plotH,
                                int col) {
        for (int i = 0; i < samples.length; i++) {
            double[] a = samples[i];
            float jitter = ((i * 7919) % 1000 / 1000f - 0.5f) * 2f * spread;
            for (int t = 0; t < cur.n; t++) {
                if (!cur.checkYaw[t] || Float.isNaN(xs[t])) continue;
                double v = facing[t] + Angles.wrapDelta(a[t] - facing[t]);
                float x = xs[t] + jitter;
                float y = yOf(v, yLo, yHi, plotY, plotH);
                dl.addRectFilled(x - half, y - half, x + half, y + half, col, 0f);
            }
        }
    }

    private static int youColor(TurnAttempt you) {
        if (!you.complete) return ThemeManager.textColor();
        if (you.failed()) return ThemeManager.warningColor();
        return you.landed ? ThemeManager.okColor() : ThemeManager.dangerColor();
    }

    private static int dotColor(TurnProfileController.Current cur, int t) {
        return cur.checkYaw[t] ? ThemeManager.accentColor() : ThemeManager.textDimColor();
    }

    private void tooltip(TurnProfileController.Current cur, int t, TurnAttempt you) {
        AttemptSampler.Stats st = cur.attempts;
        double pixelDeg = cur.pixelDeg;
        int abs = cur.startTick + t;
        ImGui.beginTooltip();
        Fonts.pushBold();
        ImGui.text(String.format(Locale.ROOT, "T%d%s", abs + 1, cur.still[t] ? "   still" : ""));
        Fonts.popBold();
        float labelW = ImGui.calcTextSize("Window").x + ImGui.getStyle().getItemSpacing().x * 3f;
        tooltipRow("Ref", String.format(Locale.ROOT, "%.2f°", cur.facing[t]), ThemeManager.textColor(), labelW);
        if (cur.checkYaw[t]) {
            if (you != null) {
                double e = youError(cur, you, t);
                if (!Double.isNaN(e)) {
                    tooltipRow("You", String.format(Locale.ROOT, "%.2f°", cur.facing[t] + e), ThemeManager.textColor(), labelW);
                    tooltipRow("Error", TurnAttempt.turnText(e, pixelDeg), youColor(you), labelW);
                }
            }
            if (st != null && st.landings > 0) {
                tooltipRow("Window", TurnAttempt.turnText(st.landedLo[t], pixelDeg) + " to "
                        + TurnAttempt.turnText(st.landedHi[t], pixelDeg), ThemeManager.textColor(), labelW);
            }
            if (you != null && you.hasForecast() && settings.onejumpOffsetHover) {
                double best = you.bestMarginAt(abs);
                if (!Double.isNaN(best)) {
                    tooltipRow("Offset", TurnAttempt.signedMargin(best),
                            best <= 0.0 ? ThemeManager.okColor() : ThemeManager.dangerColor(), labelW);
                }
            }
        }
        if (you != null && you.failTick == abs) {
            if (you.turnFailure) {
                tooltipRow("Preturn", TurnAttempt.turnText(you.failTurn, pixelDeg), ThemeManager.dangerColor(), labelW);
            } else if (you.inputFailure) {
                tooltipRow("Inputs", TurnReference.describe(you.failKeys) + ", expected " + TurnReference.describe(you.expectedKeys),
                        ThemeManager.dangerColor(), labelW);
            }
        }
        ImGui.endTooltip();
    }

    private static void tooltipRow(String label, String value, int color, float labelW) {
        float x = ImGui.getCursorPosX();
        ImGui.textDisabled(label);
        ImGui.sameLine();
        ImGui.setCursorPosX(x + labelW);
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.text(value);
        ImGui.popStyleColor();
    }

    private static String offsetLabel(TurnAttempt you) {
        int tick = you.lastForecastTick();
        if (tick < 0) return null;
        double best = you.bestMarginAt(tick);
        return "offset " + TurnAttempt.signedMargin(best);
    }

    private static double gridStep(double span) {
        double raw = span / 5.0;
        double mag = Math.pow(10.0, Math.floor(Math.log10(Math.max(raw, 1e-9))));
        double norm = raw / mag;
        double step = norm < 1.5 ? 1.0 : norm < 3.5 ? 2.0 : norm < 7.5 ? 5.0 : 10.0;
        return step * mag;
    }

    private static float timeX(float[] xs, int t, float phase) {
        double tau = t + phase;
        int a = -1, b = -1;
        for (int i = 0; i < xs.length; i++) {
            if (Float.isNaN(xs[i])) continue;
            if (i <= tau) a = i;
            if (i >= tau) {
                b = i;
                break;
            }
        }
        if (a < 0 || b < 0) return Float.NaN;
        if (a == b) return xs[a];
        return (float) (xs[a] + (tau - a) / (b - a) * (xs[b] - xs[a]));
    }

    private static float yOf(double v, double lo, double hi, float plotY, float plotH) {
        double f = (v - lo) / (hi - lo);
        return (float) (plotY + plotH - f * plotH);
    }
}
