package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

/**
 * Immediate-mode widgets the Angle Solver mock needs that aren't in {@code Controls}:
 * segmented control, cycling combo, tick stepper, potion chip, "+ add" button. All chrome
 * is drawn from {@code ThemeManager} colors; directional glyphs are drawn as triangles
 * because the in-game font only rasterizes Latin / Cyrillic / Japanese ranges.
 */
public final class SolverWidgets {

    private SolverWidgets() {}

    private static final float ROUND = 3f;
    private static final float SEG_PAD_X = 12f;
    private static final float PAD_X = 7f;
    private static final float GAP = 6f;

    private static float s() {
        return ThemeManager.uiScale();
    }

    private static float textY(float mnY, float h) {
        return mnY + (h - ImGui.getFontSize()) * 0.5f;
    }

    // ---- shared glyph drawing (triangles, grip dots) ---------------------------

    public static void triangleDown(ImDrawList dl, float cx, float cy, float r, int col) {
        dl.addTriangleFilled(cx - r, cy - r * 0.6f, cx + r, cy - r * 0.6f, cx, cy + r * 0.7f, col);
    }

    public static void triangleRight(ImDrawList dl, float cx, float cy, float r, int col) {
        dl.addTriangleFilled(cx - r * 0.6f, cy - r, cx - r * 0.6f, cy + r, cx + r * 0.7f, cy, col);
    }

    /** Six-dot drag grip, drawn in two columns of three. */
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

    // ---- delete x (small, frameless) -------------------------------------------

    public static float deleteXWidth() {
        return ImGui.calcTextSize("×").x + 8f * s();
    }

    /** Small frameless "×" that reddens on hover; shared by the drawer rows and the solver window's potion rows. */
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

    /** Two-segment tick mark, drawn because the font lacks the check glyph. */
    public static void checkMark(ImDrawList dl, float x, float cy, float size, int col) {
        float t = Math.max(1.2f, 1.4f * s());
        dl.addLine(x, cy + size * 0.05f, x + size * 0.38f, cy + size * 0.5f, col, t);
        dl.addLine(x + size * 0.38f, cy + size * 0.5f, x + size, cy - size * 0.5f, col, t);
    }

    // ---- row label -------------------------------------------------------------

    public static void rowLabel(String text, float minWidth) {
        float startX = ImGui.getCursorPosX();
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
        ImGui.sameLine();
        if (ImGui.getCursorPosX() < startX + minWidth) {
            ImGui.setCursorPosX(startX + minWidth);
        }
    }

    // ---- segmented control ------------------------------------------------------

    public static int segmented(String id, String[] labels, int selected, boolean mauve) {
        return segmented(id, labels, selected, mauve, 0f);
    }

    /** Single-choice segmented control. {@code fillWidth} > 0 splits that total width evenly across the segments so the control aligns to a form column. Returns the clicked index, or -1 if none this frame. */
    public static int segmented(String id, String[] labels, int selected, boolean mauve, float fillWidth) {
        float scale = s();
        float h = ImGui.getFrameHeight();
        ImDrawList dl = ImGui.getWindowDrawList();
        int onBg = mauve ? ThemeManager.mauveColor() : ThemeManager.accentColor();
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

    /**
     * Small clickable box used by the drawer for the field selector, op button, and
     * bracket toggle. {@code chevron} appends a down triangle (field selector);
     * {@code center} centers the label (op / bracket). Returns true when clicked.
     */
    public static boolean miniBox(String id, String label, int labelColor, boolean chevron, boolean center, float minWidth) {
        float scale = s();
        float h = ImGui.getFrameHeight();
        ImDrawList dl = ImGui.getWindowDrawList();
        float pad = PAD_X * scale;
        float chevW = chevron ? 11f * scale : 0f;
        float w = Math.max(minWidth, ImGui.calcTextSize(label).x + 2f * pad + chevW);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        boolean hover = ImGui.isItemHovered();
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, hover ? ThemeManager.accentTintColor(0.18f) : ThemeManager.hoverColor(), ROUND * scale);
        dl.addRect(mn.x, mn.y, mx.x, mx.y, ThemeManager.borderColor(), ROUND * scale, 0, 1f);
        float ty = textY(mn.y, h);
        float ts = ImGui.calcTextSize(label).x;
        float tx = center ? mn.x + (mx.x - mn.x - ts) * 0.5f : mn.x + pad;
        dl.addText(tx, ty, labelColor, label);
        if (chevron) triangleDown(dl, mx.x - pad - 2f * scale, mn.y + h * 0.5f, 3.2f * scale, ThemeManager.textMutedColor());
        return clicked;
    }
}
