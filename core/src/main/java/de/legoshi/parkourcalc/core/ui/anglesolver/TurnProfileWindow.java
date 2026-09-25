package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
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

import java.util.Locale;
import java.util.function.Supplier;

public final class TurnProfileWindow implements RenderInterface {

    private static final String WINDOW_ID = "Turn Profile";
    private static final float WIN_W = 720f;
    private static final float WIN_H = 360f;
    private static final float MIN_W = 420f;
    private static final float MIN_H = 220f;
    private static final float LEGEND_W = 150f;
    private static final float PAD_LEFT = 44f;
    private static final float PAD_RIGHT = 14f;
    private static final float PAD_TOP = 14f;
    private static final float PAD_BOTTOM = 26f;
    private static final float DOT_RADIUS = 3.5f;
    private static final float LINE_WIDTH = 2f;
    private static final double MIN_SPAN_DEG = 20.0;

    private final TurnProfileController controller;
    private final Settings settings;
    private final Supplier<Float> sensitivity;
    private final ImBoolean open = new ImBoolean(false);
    private boolean wasOpen;

    public TurnProfileWindow(TurnProfileController controller, Settings settings, Supplier<Float> sensitivity) {
        this.controller = controller;
        this.settings = settings;
        this.sensitivity = sensitivity;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewTurnProfile);
        if (open.get() && !wasOpen) controller.refresh();
        wasOpen = open.get();
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        if (ImGui.begin(WINDOW_ID, open, flags)) {
            TurnProfileController.Current cur = controller.current();
            if (cur == null) {
                ImGui.textDisabled(controller.isComputing() ? "Computing" : "No path in the solver range");
            } else if (!cur.profile.lands) {
                ImGui.textDisabled("The current path does not meet the constraints");
            } else {
                draw(cur, scale);
            }
        }
        ImGui.end();
        settings.viewTurnProfile = open.get();
    }

    private void draw(TurnProfileController.Current cur, float scale) {
        TurnProfile p = cur.profile;
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        ImVec2 avail = ImGui.getContentRegionAvail();
        float legendW = LEGEND_W * scale;
        float cw = Math.max(80f, avail.x - legendW);
        float ch = Math.max(60f, avail.y);
        ImVec2 origin = ImGui.getCursorScreenPos();
        float x0 = origin.x, y0 = origin.y;
        ImGui.invisibleButton("##turnprofile", cw, ch);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x0, y0, x0 + cw, y0 + ch, ThemeManager.bgDarkColor(), 0f);

        float padL = PAD_LEFT * scale, padR = PAD_RIGHT * scale, padT = PAD_TOP * scale, padB = PAD_BOTTOM * scale;
        float plotX = x0 + padL, plotW = cw - padL - padR;
        float plotY = y0 + padT, plotH = ch - padT - padB;
        int n = p.n;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < n; t++) {
            lo = Math.min(lo, p.facing[t] - p.below[t]);
            hi = Math.max(hi, p.facing[t] + p.above[t]);
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
        float dx = n > 1 ? plotW / (n - 1) : 0f;

        double step = gridStep(yHi - yLo);
        int textCol = ThemeManager.textMutedColor();
        int gridCol = ThemeManager.borderColor();
        for (double g = Math.ceil(yLo / step) * step; g <= yHi; g += step) {
            float gy = yOf(g, yLo, yHi, plotY, plotH);
            dl.addLine(plotX, gy, plotX + plotW, gy, gridCol, 1f);
            String lbl = String.format(Locale.ROOT, "%.0f", g);
            dl.addText(plotX - 6f * scale - ImGui.calcTextSize(lbl).x, gy - ImGui.getTextLineHeight() * 0.5f, textCol, lbl);
        }

        int bandFill = ThemeManager.okTintColor(0.22f);
        int bandEdge = ThemeManager.okTintColor(0.8f);
        for (int t = 0; t + 1 < n; t++) {
            if (p.held[t] || p.held[t + 1]) continue;
            float xa = plotX + t * dx, xb = plotX + (t + 1) * dx;
            float ta = yOf(p.facing[t] + p.above[t], yLo, yHi, plotY, plotH);
            float tb = yOf(p.facing[t + 1] + p.above[t + 1], yLo, yHi, plotY, plotH);
            float ba = yOf(p.facing[t] - p.below[t], yLo, yHi, plotY, plotH);
            float bb = yOf(p.facing[t + 1] - p.below[t + 1], yLo, yHi, plotY, plotH);
            dl.addQuadFilled(xa, ta, xb, tb, xb, bb, xa, ba, bandFill);
            dl.addLine(xa, ta, xb, tb, bandEdge, 1f);
            dl.addLine(xa, ba, xb, bb, bandEdge, 1f);
        }

        int jumpCol = ThemeManager.peachTintColor(0.7f);
        for (int t = 0; t < n; t++) {
            if (!cur.jumpTicks[t]) continue;
            float jx = plotX + t * dx;
            dl.addLine(jx, plotY, jx, plotY + plotH, jumpCol, 1f);
            dl.addText(jx - ImGui.calcTextSize("jump").x * 0.5f, y0 + 1f, jumpCol, "jump");
        }

        int lineCol = ThemeManager.accentColor();
        for (int t = 0; t + 1 < n; t++) {
            dl.addLine(plotX + t * dx, yOf(p.facing[t], yLo, yHi, plotY, plotH),
                    plotX + (t + 1) * dx, yOf(p.facing[t + 1], yLo, yHi, plotY, plotH), lineCol, LINE_WIDTH * scale);
        }
        for (int t = 0; t < n; t++) {
            dl.addCircleFilled(plotX + t * dx, yOf(p.facing[t], yLo, yHi, plotY, plotH), DOT_RADIUS * scale,
                    classColor(p.classify(t, pixelDeg)), 12);
        }

        int labelEvery = Math.max(1, (int) Math.ceil(ImGui.calcTextSize("000").x * 1.4f / Math.max(1f, dx)));
        for (int t = 0; t < n; t++) {
            if (t % labelEvery != 0 && t != n - 1) continue;
            String lbl = Integer.toString(cur.startTick + t + 1);
            dl.addText(plotX + t * dx - ImGui.calcTextSize(lbl).x * 0.5f, plotY + plotH + 4f * scale,
                    cur.jumpTicks[t] ? jumpCol : textCol, lbl);
        }

        if (hovered && n > 0) {
            float mx = ImGui.getMousePosX();
            int t = Math.round((mx - plotX) / Math.max(1f, dx));
            t = Math.max(0, Math.min(n - 1, t));
            float hx = plotX + t * dx;
            dl.addLine(hx, plotY, hx, plotY + plotH, ThemeManager.textDimColor(), 1f);
            tooltip(cur, t, pixelDeg);
        }

        drawLegend(dl, x0 + cw, y0, legendW, ch, scale, pixelDeg);
    }

    private void tooltip(TurnProfileController.Current cur, int t, double pixelDeg) {
        TurnProfile p = cur.profile;
        ImGui.beginTooltip();
        ImGui.text(String.format(Locale.ROOT, "T%d  %.2f°", cur.startTick + t + 1, p.facing[t]));
        if (p.held[t]) {
            ImGui.textDisabled("held facing");
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, classColor(p.classify(t, pixelDeg)));
            ImGui.text(String.format(Locale.ROOT, "-%.3f° %s   +%.3f° %s",
                    p.below[t], bracket(p.below[t], pixelDeg), p.above[t], bracket(p.above[t], pixelDeg)));
            ImGui.popStyleColor();
        }
        ImGui.endTooltip();
    }

    private static String bracket(double deg, double pixelDeg) {
        return String.format(Locale.ROOT, "(%d px, %d sig)", (int) Math.floor(deg / pixelDeg),
                (int) Math.floor(deg / TurnProfile.SIG_ANGLE_DEG));
    }

    private void drawLegend(ImDrawList dl, float x, float y, float w, float h, float scale, double pixelDeg) {
        float lh = ImGui.getTextLineHeight();
        float gap = 6f * scale;
        float lx = x + 14f * scale;
        float ly = y + 8f * scale;
        int text = ThemeManager.textMutedColor();
        dl.addLine(x + 4f * scale, y, x + 4f * scale, y + h, ThemeManager.borderColor(), 1f);

        dl.addRectFilled(lx, ly + lh * 0.5f - 1.5f * scale, lx + 16f * scale, ly + lh * 0.5f + 1.5f * scale, ThemeManager.accentColor(), 0f);
        dl.addText(lx + 24f * scale, ly, text, "facing");
        ly += lh + gap;
        dl.addRectFilled(lx, ly + 2f * scale, lx + 16f * scale, ly + lh - 2f * scale, ThemeManager.okTintColor(0.3f), 0f);
        dl.addText(lx + 24f * scale, ly, text, "lands");
        ly += lh + gap;
        ly = legendDot(dl, lx, ly, lh, gap, scale, ThemeManager.dangerColor(), "pinned", text);
        ly = legendDot(dl, lx, ly, lh, gap, scale, ThemeManager.warningColor(), "< 1 px", text);
        ly = legendDot(dl, lx, ly, lh, gap, scale, ThemeManager.accentColor(), "free", text);
        legendDot(dl, lx, ly, lh, gap, scale, ThemeManager.textDimColor(), "held", text);

        String sens = String.format(Locale.ROOT, "sens %d%%", Math.round(sensitivity.get() * 200f));
        String px = String.format(Locale.ROOT, "1 px = %.3f°", pixelDeg);
        dl.addText(lx, y + h - 2f * lh - 4f * scale, ThemeManager.textDimColor(), sens);
        dl.addText(lx, y + h - lh - 4f * scale, ThemeManager.textDimColor(), px);
    }

    private static float legendDot(ImDrawList dl, float lx, float ly, float lh, float gap, float scale, int col, String label, int text) {
        dl.addCircleFilled(lx + 8f * scale, ly + lh * 0.5f, DOT_RADIUS * scale, col, 12);
        dl.addText(lx + 24f * scale, ly, text, label);
        return ly + lh + gap;
    }

    private static int classColor(TurnProfile.TickClass c) {
        switch (c) {
            case PINNED: return ThemeManager.dangerColor();
            case SUB_PIXEL: return ThemeManager.warningColor();
            case HELD: return ThemeManager.textDimColor();
            default: return ThemeManager.accentColor();
        }
    }

    private static float yOf(double deg, double lo, double hi, float plotY, float plotH) {
        return (float) (plotY + (hi - deg) / (hi - lo) * plotH);
    }

    private static double gridStep(double span) {
        double raw = span / 5.0;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double nice = norm < 1.5 ? 1 : norm < 3.5 ? 2 : norm < 7.5 ? 5 : 10;
        return nice * mag;
    }
}
