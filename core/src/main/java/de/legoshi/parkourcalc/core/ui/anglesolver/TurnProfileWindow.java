package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnProfileDocument;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class TurnProfileWindow implements RenderInterface {

    private static final String WINDOW_ID = "###onejump";
    private static final String TITLE = "Onejump";
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
    private static final float SWARM_SPREAD = 0.2f;
    private static final float DENSITY_REACH = 3f;
    private static final float DENSITY_ALPHA_MIN = 0.22f;
    private static final double MIN_SPAN_DEG = 20.0;
    private static final double BLAME_HIGH = 0.4;
    private static final double BLAME_SOME = 0.1;
    private static final int DOT_LIMIT = 500;

    private final TurnProfileController controller;
    private final AttemptTracker tracker;
    private final Settings settings;
    private final Supplier<Float> sensitivity;
    private final ImBoolean open = new ImBoolean(false);
    private int dotsVersion = -1;
    private TurnProfileController.Current dotsCur;
    private double[][] dotsLanded = new double[0][];
    private double[][] dotsFailed = new double[0][];
    private boolean wasOpen;

    public TurnProfileWindow(TurnProfileController controller, AttemptTracker tracker, Settings settings,
                             Supplier<Float> sensitivity) {
        this.controller = controller;
        this.tracker = tracker;
        this.settings = settings;
        this.sensitivity = sensitivity;
    }

    @Override
    public void render(ImGuiIO io) {
        render(io, false);
    }

    @Override
    public void renderDetached(ImGuiIO io) {
        if (settings.keepTurnProfileOpen) render(io, true);
    }

    private void render(ImGuiIO io, boolean graphOnly) {
        open.set(settings.viewTurnProfile);
        if (open.get() && !wasOpen) controller.refresh();
        wasOpen = open.get();
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
        settings.viewTurnProfile = open.get();
    }

    private void body(float scale) {
        controller.sync();
        TurnProfileController.Current cur = controller.current();
        float graphH = Math.max(GRAPH_MIN_H * scale, ImGui.getContentRegionAvail().y);
        if (cur == null || cur.n == 0) {
            placeholder(graphH, controller.lastError() != null ? controller.lastError()
                    : "No reference yet: set the onejump up in the Onejump setup window");
            return;
        }
        graph(cur, scale, graphH, shownAttempt());
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

    private TurnAttempt shownAttempt() {
        TurnAttempt live = tracker.live();
        if (live != null) return live;
        TurnAttempt selected = controller.selectedAttempt();
        return selected != null ? selected : tracker.last();
    }

    private static double youError(TurnProfileController.Current cur, TurnAttempt you, int t) {
        int j = t + cur.startTick - you.firstTick;
        if (j < 0 || j >= you.recorded) return Double.NaN;
        return Angles.wrapDelta(you.yaws[j] - cur.facing[t]);
    }

    private static double[] aligned(TurnProfileController.Current cur, TurnAttempt a) {
        double[] v = new double[cur.n];
        for (int t = 0; t < cur.n; t++) {
            int j = t + cur.startTick - a.firstTick;
            v[t] = j < 0 || j >= a.recorded ? Double.NaN : a.yaws[j];
        }
        return v;
    }

    private void refreshDots(TurnProfileController.Current cur) {
        TurnProfileDocument doc = controller.document();
        if (dotsCur == cur && dotsVersion == doc.version()) return;
        dotsCur = cur;
        dotsVersion = doc.version();
        List<TurnAttempt> all = doc.attempts();
        List<double[]> landed = new ArrayList<double[]>();
        List<double[]> failed = new ArrayList<double[]>();
        int taken = 0;
        for (int i = all.size() - 1; i >= 0 && taken < DOT_LIMIT; i--) {
            TurnAttempt a = all.get(i);
            if (!a.judged()) continue;
            taken++;
            (a.landed ? landed : failed).add(aligned(cur, a));
        }
        dotsLanded = landed.toArray(new double[0][]);
        dotsFailed = failed.toArray(new double[0][]);
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

    private void graph(TurnProfileController.Current cur, float scale, float ch, TurnAttempt you) {
        double[] facing = cur.facing;
        AttemptSampler.Stats st = settings.turnProfileShowRating ? cur.attempts : null;
        float cw = Math.max(80f, ImGui.getContentRegionAvail().x);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x, y0 = origin.y;
        ImGui.invisibleButton("##onejumpGraph", cw, ch);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x0 + cw, y0 + ch, ThemeManager.bgDarkColor(), 0f);

        float padL = PAD_LEFT * scale, padR = PAD_RIGHT * scale, padT = PAD_TOP * scale, padB = PAD_BOTTOM * scale;
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
        double[][] yourLanded = new double[0][];
        double[][] yourFailed = new double[0][];
        if (settings.turnProfileShowAttempts) {
            refreshDots(cur);
            yourLanded = dotsLanded;
            yourFailed = dotsFailed;
        }
        for (double[][] set : new double[][][]{yourLanded, yourFailed}) {
            for (double[] a : set) for (int t = 0; t < n; t++) {
                if (Float.isNaN(xs[t]) || !cur.checkYaw[t] || Double.isNaN(a[t])) continue;
                double v = facing[t] + Angles.wrapDelta(a[t] - facing[t]);
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
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
            String lbl = String.format(Locale.ROOT, "%.0f", g);
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
            scatter(dl, st.failed, cur, xs, spread, half, yLo, yHi, plotY, plotH, ThemeManager.dangerTintColor(0.35f));
            scatter(dl, st.landed, cur, xs, spread, half, yLo, yHi, plotY, plotH, ThemeManager.okTintColor(0.85f));
        }

        int jumpCol = ThemeManager.peachTintColor(0.7f);
        for (int t = 0; t < n; t++) {
            if (!cur.jumpTicks[t] || Float.isNaN(xs[t])) continue;
            dl.addLine(xs[t], plotY, xs[t], plotY + plotH, jumpCol, 1f);
            dl.addText(xs[t] - ImGui.calcTextSize("jump").x * 0.5f, y0 + 1f, jumpCol, "jump");
        }
        if (cur.landing != null) {
            int lt = cur.landing.tick - cur.startTick;
            if (lt >= 0 && lt < n && !Float.isNaN(xs[lt])) {
                int landCol = ThemeManager.okTintColor(0.6f);
                dl.addLine(xs[lt], plotY, xs[lt], plotY + plotH, landCol, 1f);
                dl.addText(xs[lt] - ImGui.calcTextSize("land").x * 0.5f, y0 + 1f, landCol, "land");
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
        }

        swarm(dl, yourFailed, yourLanded, cur, xs, SWARM_SPREAD * dx, DOT_RADIUS * 0.6f * scale, yLo, yHi, plotY, plotH);

        if (you != null) {
            int youCol = youColor(you);
            float prevX = 0f, prevY = 0f;
            boolean havePrev = false;
            for (int t = 0; t < n; t++) {
                if (Float.isNaN(xs[t])) continue;
                double e = youError(cur, you, t);
                if (Double.isNaN(e)) {
                    havePrev = false;
                    continue;
                }
                float x = xs[t], y = yOf(facing[t] + e, yLo, yHi, plotY, plotH);
                if (havePrev) dl.addLine(prevX, prevY, x, y, youCol, LINE_WIDTH * scale);
                dl.addCircleFilled(x, y, DOT_RADIUS * 0.7f * scale, youCol, 10);
                prevX = x;
                prevY = y;
                havePrev = true;
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
            String lbl = Integer.toString(cur.startTick + t + 1);
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

    private static void scatter(ImDrawList dl, double[][] samples, TurnProfileController.Current cur, float[] xs,
                                float spread, float half, double yLo, double yHi, float plotY, float plotH, int col) {
        for (int i = 0; i < samples.length; i++) {
            double[] a = samples[i];
            float jitter = ((i * 7919) % 1000 / 1000f - 0.5f) * 2f * spread;
            for (int t = 0; t < cur.n; t++) {
                if (!cur.checkYaw[t] || Float.isNaN(xs[t])) continue;
                double v = cur.facing[t] + Angles.wrapDelta(a[t] - cur.facing[t]);
                float x = xs[t] + jitter;
                float y = yOf(v, yLo, yHi, plotY, plotH);
                dl.addRectFilled(x - half, y - half, x + half, y + half, col, 0f);
            }
        }
    }

    private static double[] tickValues(double[][] failed, double[][] landed, double[] facing, int t, boolean[] landedFlag) {
        int k = failed.length + landed.length;
        double[] v = new double[k];
        int i = 0;
        for (double[] a : failed) {
            v[i] = Double.isNaN(a[t]) ? Double.NaN : facing[t] + Angles.wrapDelta(a[t] - facing[t]);
            landedFlag[i] = false;
            i++;
        }
        for (double[] a : landed) {
            v[i] = Double.isNaN(a[t]) ? Double.NaN : facing[t] + Angles.wrapDelta(a[t] - facing[t]);
            landedFlag[i] = true;
            i++;
        }
        return v;
    }

    private static void swarm(ImDrawList dl, double[][] failed, double[][] landed, TurnProfileController.Current cur,
                              float[] xs, float maxSpread, float r, double yLo, double yHi, float plotY, float plotH) {
        int k = failed.length + landed.length;
        if (k == 0) return;
        boolean[] isLanded = new boolean[k];
        Integer[] order = new Integer[k];
        float[] ys = new float[k];
        float[] pxs = new float[k];
        int[] density = new int[k];
        float step = r * 2.1f;
        float reach = step * DENSITY_REACH;
        for (int t = 0; t < cur.n; t++) {
            if (!cur.checkYaw[t] || Float.isNaN(xs[t])) continue;
            double[] v = tickValues(failed, landed, cur.facing, t, isLanded);
            int m = 0;
            for (int i = 0; i < k; i++) if (!Double.isNaN(v[i])) order[m++] = i;
            if (m == 0) continue;
            final double[] vv = v;
            java.util.Arrays.sort(order, 0, m, (a, b) -> Double.compare(vv[a], vv[b]));
            int maxDensity = 1;
            for (int j = 0; j < m; j++) ys[order[j]] = yOf(v[order[j]], yLo, yHi, plotY, plotH);
            for (int j = 0; j < m; j++) {
                int c = 0;
                for (int q = j - 1; q >= 0 && ys[order[q]] - ys[order[j]] < reach; q--) c++;
                for (int q = j + 1; q < m && ys[order[j]] - ys[order[q]] < reach; q++) c++;
                density[order[j]] = c;
                maxDensity = Math.max(maxDensity, c);
            }
            for (int j = 0; j < m; j++) {
                int i = order[j];
                float y = ys[i];
                float x = 0f;
                for (int slot = 0; ; slot++) {
                    float cand = slot == 0 ? 0f : (slot % 2 == 1 ? 1 : -1) * ((slot + 1) / 2) * step;
                    if (Math.abs(cand) > maxSpread) {
                        x = cand > 0 ? maxSpread : -maxSpread;
                        break;
                    }
                    boolean free = true;
                    for (int q = j - 1; q >= 0; q--) {
                        int o = order[q];
                        if (ys[o] - y > step) break;
                        if (Math.abs(pxs[o] - cand) < step && Math.abs(ys[o] - y) < step) {
                            free = false;
                            break;
                        }
                    }
                    if (free) {
                        x = cand;
                        break;
                    }
                }
                pxs[i] = x;
                float alpha = DENSITY_ALPHA_MIN + (1f - DENSITY_ALPHA_MIN) * density[i] / (float) maxDensity;
                int col = isLanded[i] ? ThemeManager.okTintColor(alpha) : ThemeManager.dangerTintColor(alpha);
                dl.addCircleFilled(xs[t] + x, y, r, col, 8);
            }
        }
    }

    private static int youColor(TurnAttempt you) {
        if (!you.complete) return ThemeManager.textColor();
        if (you.inputFailure) return ThemeManager.warningColor();
        return you.landed ? ThemeManager.okColor() : ThemeManager.dangerColor();
    }

    private static int dotColor(TurnProfileController.Current cur, int t) {
        if (!cur.checkYaw[t]) return ThemeManager.textDimColor();
        AttemptSampler.Stats st = cur.attempts;
        if (st == null) return ThemeManager.accentColor();
        double b = st.blame[t];
        if (b >= BLAME_HIGH) return ThemeManager.dangerColor();
        if (b >= BLAME_SOME) return ThemeManager.warningColor();
        return ThemeManager.accentColor();
    }

    private void tooltip(TurnProfileController.Current cur, int t, TurnAttempt you) {
        AttemptSampler.Stats st = cur.attempts;
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        ImGui.beginTooltip();
        ImGui.text(String.format(Locale.ROOT, "tick %d  %.2f°  %s", cur.startTick + t + 1, cur.facing[t],
                TurnReference.keysText(cur.keys[t])));
        if (!cur.checkYaw[t]) {
            ImGui.textDisabled("facing not checked");
        } else if (st != null && st.landings > 0) {
            ImGui.pushStyleColor(ImGuiCol.Text, dotColor(cur, t));
            ImGui.text(String.format(Locale.ROOT, "landed within %s %s   %s %s",
                    signed(st.landedLo[t]), bracket(-st.landedLo[t], pixelDeg),
                    signed(st.landedHi[t]), bracket(st.landedHi[t], pixelDeg)));
            ImGui.popStyleColor();
            ImGui.textDisabled(String.format(Locale.ROOT, "%s, %.0f%% of fails",
                    st.flickTick[t] ? "flick" : "smooth", st.blame[t] * 100.0));
        }
        if (you != null && cur.checkYaw[t]) {
            double e = youError(cur, you, t);
            if (!Double.isNaN(e)) {
                ImGui.pushStyleColor(ImGuiCol.Text, youColor(you));
                ImGui.text(String.format(Locale.ROOT, "you %.2f°   %s %s", cur.facing[t] + e, signed(e), bracket(e, pixelDeg)));
                ImGui.popStyleColor();
            }
        }
        ImGui.endTooltip();
    }

    private static String signed(double deg) {
        return String.format(Locale.ROOT, "%s%.3f°", deg < 0 ? "-" : "+", Math.abs(deg));
    }

    private static String bracket(double deg, double pixelDeg) {
        return String.format(Locale.ROOT, "(%d px)", Math.round(Math.abs(deg) / pixelDeg));
    }

    private static double gridStep(double span) {
        double raw = span / 5.0;
        double mag = Math.pow(10.0, Math.floor(Math.log10(Math.max(raw, 1e-9))));
        double norm = raw / mag;
        double step = norm < 1.5 ? 1.0 : norm < 3.5 ? 2.0 : norm < 7.5 ? 5.0 : 10.0;
        return step * mag;
    }

    private static float yOf(double v, double lo, double hi, float plotY, float plotH) {
        double f = (v - lo) / (hi - lo);
        return (float) (plotY + plotH - f * plotH);
    }
}
