package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.function.IntConsumer;

public final class ChipFlow {

    public static final float CHIP_PAD = 5f;
    public static final float CHIP_GAP = 4f;
    public static final float CHIP_SPACING = 5f;
    public static final float CHIP_ROUNDING = 3f;

    private ChipFlow() {
    }

    public static final class Layout {
        public int[] lineOf;
        public float[] lineW;
        public float cellH;
        public int lines;
    }

    public static Layout compute(float cellW, float rowH, float[] chipW) {
        float s = ThemeManager.uiScale();
        float gap = CHIP_SPACING * s;
        float lineH = ImGui.getFrameHeight();
        float spacingY = ImGui.getStyle().getItemSpacing().y;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        int n = chipW.length;

        Layout layout = new Layout();
        layout.lineOf = new int[n];
        layout.lineW = new float[Math.max(1, n)];
        int lines = 1;
        float usedX = 0f;
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                usedX = chipW[i];
            } else if (usedX + gap + chipW[i] <= cellW) {
                usedX += gap + chipW[i];
            } else {
                lines++;
                usedX = chipW[i];
            }
            layout.lineOf[i] = lines - 1;
            layout.lineW[lines - 1] = usedX;
        }
        layout.lines = lines;
        layout.cellH = Math.max(rowH, lines * lineH + (lines - 1) * spacingY + 2f * cellPadY);
        return layout;
    }

    public static void place(ImVec2 origin, float cellW, float grownRowH, Layout layout, IntConsumer renderer) {
        float s = ThemeManager.uiScale();
        float gap = CHIP_SPACING * s;
        float lineH = ImGui.getFrameHeight();
        float spacingY = ImGui.getStyle().getItemSpacing().y;
        float blockH = layout.lines * lineH + (layout.lines - 1) * spacingY;
        float cellPadY = ImGui.getStyle().getCellPadding().y;
        float yOff = Math.max(0f, (grownRowH - 2f * cellPadY - blockH) * 0.5f);
        int curLine = -1;
        for (int i = 0; i < layout.lineOf.length; i++) {
            int line = layout.lineOf[i];
            if (line != curLine) {
                curLine = line;
                float lx = origin.x + Math.max(0f, (cellW - layout.lineW[line]) * 0.5f);
                float ly = origin.y + yOff + line * (lineH + spacingY);
                ImGui.setCursorScreenPos(lx, ly);
            } else {
                ImGui.sameLine(0, gap);
            }
            renderer.accept(i);
        }
    }

    public static void dropShadow(ImDrawList dl, ImVec2 mn, ImVec2 mx) {
        float s = ThemeManager.uiScale();
        for (int k = 1; k <= 3; k++) {
            float o = k * s;
            dl.addRectFilled(mn.x + o, mn.y + o, mx.x + o, mx.y + o, ThemeManager.shadowColor(0.10f), CHIP_ROUNDING * s + o);
        }
    }
}
