package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Immediate-mode widgets the Angle Solver needs that aren't in {@code Controls}:
 * segmented control, spinner, delete-x, grip dots, triangles, row label. All chrome
 * is drawn from {@code ThemeManager} colors; directional glyphs are drawn as triangles
 * because the in-game font only rasterizes Latin / Cyrillic / Japanese ranges.
 */
public final class SolverWidgets {

    private static final float ROUND = 3f;
    private static final float SEG_PAD_X = 12f;

    private static float s() {
        return ThemeManager.uiScale();
    }

    private static float textY(float mnY, float h) {
        return mnY + (h - ImGui.getFontSize()) * 0.5f;
    }

    public static void triangleDown(ImDrawList dl, float cx, float cy, float r, int col) {
        dl.addTriangleFilled(cx - r, cy - r * 0.6f, cx + r, cy - r * 0.6f, cx, cy + r * 0.7f, col);
    }

    public static void triangleRight(ImDrawList dl, float cx, float cy, float r, int col) {
        dl.addTriangleFilled(cx - r * 0.6f, cy - r, cx - r * 0.6f, cy + r, cx + r * 0.7f, cy, col);
    }

    public static void gripDots(ImDrawList dl, float x, float cy, int col) {
        float scale = s();
        float dot = 1.1f * scale;
        float gx = 2.2f * scale;
        float gy = 3.0f * scale;
        for (int c = 0; c < 2; c++) {
            for (int r = 0; r < 3; r++) {
                dl.addCircleFilled(x + c * gx, cy + (r - 1) * gy, dot, col, 6);
            }
        }
    }

    public static float gripWidth() {
        return 4f * s();
    }

    public static float deleteXWidth() {
        return ImGui.calcTextSize("×").x + 8f * s();
    }

    public static boolean deleteX(String id) {
        float scale = s();
        float h = ImGui.getFrameHeight();
        boolean clicked = ImGui.invisibleButton(id, deleteXWidth(), h);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImGui.getWindowDrawList().addText(mn.x + 4f * scale, mn.y + (h - ImGui.getFontSize()) * 0.5f,
                hover ? ThemeManager.dangerColor() : ThemeManager.textDimColor(), "×");
        return clicked;
    }

    /** Rotating ring of dots (a busy indicator); glyph-free so it renders on any loader font. {@code t}
     *  is elapsed seconds, used only to advance the head. */
    public static void spinner(ImDrawList dl, float cx, float cy, float radius, float dotR, int color, double t) {
        int n = 8;
        int head = (int) (t * 10.0) % n;
        if (head < 0) head += n;
        int rgb = color & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2.0 + (2.0 * Math.PI * i) / n;
            float x = cx + (float) (Math.cos(ang) * radius);
            float y = cy + (float) (Math.sin(ang) * radius);
            int dist = (head - i + n) % n;
            float alpha = 0.15f + 0.85f * (1.0f - dist / (float) n);
            int col = rgb | (((int) (alpha * 255f) & 0xFF) << 24);
            dl.addCircleFilled(x, y, dotR, col, 8);
        }
    }

    public static void rowLabel(String text, float minWidth) {
        float startX = ImGui.getCursorPosX();
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
        ImGui.sameLine();
        if (ImGui.getCursorPosX() < startX + minWidth) {
            ImGui.setCursorPosX(startX + minWidth);
        }
    }

    public static float segmentedMinWidth(String[] labels) {
        float maxText = 0f;
        for (String l : labels) maxText = Math.max(maxText, ImGui.calcTextSize(l).x);
        return labels.length * (maxText + 2f * SEG_PAD_X * s());
    }

    /** Single-choice segmented control. {@code fillWidth} > 0 splits that total width evenly across the segments so the control aligns to a form column. Returns the clicked index, or -1 if none this frame. */
    public static int segmented(String id, String[] labels, int selected, float fillWidth) {
        return segmented(id, labels, null, selected, fillWidth);
    }

    /** Segmented control with an optional per-segment hover tooltip; null array or null entries show none. */
    public static int segmented(String id, String[] labels, String[] tooltips, int selected, float fillWidth) {
        float scale = s();
        float h = ImGui.getFrameHeight();
        ImDrawList dl = ImGui.getWindowDrawList();
        int onBg = ThemeManager.accentColor();
        int offBg = ThemeManager.hoverColor();
        int border = ThemeManager.borderColor();
        int onText = ThemeManager.bgDarkColor();
        int offText = ThemeManager.textMutedColor();

        int clicked = -1;
        float left = 0, right = 0, top = 0, bottom = 0;
        float segW = fillWidth > 0f ? fillWidth / labels.length : 0f;
        ImGui.pushID(id);
        for (int i = 0; i < labels.length; i++) {
            float w = segW > 0f ? segW : ImGui.calcTextSize(labels[i]).x + 2f * SEG_PAD_X * scale;
            if (i > 0) ImGui.sameLine(0, 0);
            if (ImGui.invisibleButton("seg" + i, w, h)) clicked = i;
            ImVec2 mn = ImGui.getItemRectMin();
            ImVec2 mx = ImGui.getItemRectMax();
            if (i == 0) { left = mn.x; top = mn.y; bottom = mx.y; }
            right = mx.x;
            boolean on = i == selected;
            boolean hover = ImGui.isItemHovered();
            if (hover && tooltips != null && i < tooltips.length && tooltips[i] != null) {
                ImGui.setTooltip(tooltips[i]);
            }
            int fill = on ? onBg : (hover ? ThemeManager.accentTintColor(0.18f) : offBg);
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, fill, 0f);
            if (i > 0) dl.addLine(mn.x, mn.y, mn.x, mx.y, border, 1f);
            ImVec2 ts = ImGui.calcTextSize(labels[i]);
            float tx = mn.x + (mx.x - mn.x - ts.x) * 0.5f;
            float ty = textY(mn.y, h);
            if (on) Fonts.pushBold();
            dl.addText(tx, ty, on ? onText : offText, labels[i]);
            if (on) Fonts.popBold();
        }
        dl.addRect(left, top, right, bottom, border, ROUND * scale, 0, 1f);
        ImGui.popID();
        return clicked;
    }
}
