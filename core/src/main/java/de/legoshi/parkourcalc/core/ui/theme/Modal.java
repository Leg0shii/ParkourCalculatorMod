package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/**
 * Shared modal chrome: bold title, small header close glyph, and a full-width footer rule in the window border color.
 * Callers fill the body between {@link #begin} and {@link #footerSeparator()}, draw their footer buttons, then {@link #end()}.
 */
public final class Modal {

    private static final float CLOSE_ARM_HALF = 0.20f; // half-length of each close-glyph arm, in font-size units (smaller than the native close cross)

    private Modal() {}

    public static boolean begin(String title, String popupId) {
        return begin(title, popupId, 0);
    }

    public static boolean begin(String title, String popupId, int extraFlags) {
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize | extraFlags;
        ThemeManager.pushHeaderChrome();
        if (!ImGui.beginPopupModal(popupId, flags)) {
            ThemeManager.popHeaderChrome();
            return false;
        }
        ThemeManager.drawModalTitle(title);
        boolean close = drawHeaderClose();
        ThemeManager.popHeaderChrome();
        if (close) ImGui.closeCurrentPopup();
        return true;
    }

    public static void end() {
        ImGui.endPopup();
    }

    /** Full-width rule in the window border color, flush to both window edges, padded above and below. */
    public static void footerSeparator() {
        ThemeManager.sectionSpacing();
        fullWidthBorderLine();
        ThemeManager.sectionSpacing();
    }

    /** Right-aligns and draws the trailing footer button so it sits flush to the content edge without clipping. */
    public static boolean footerButton(String label) {
        Controls.cursorToRightAlignedButton(label);
        return Controls.secondaryButton(label);
    }

    private static boolean drawHeaderClose() {
        ImVec2 winPos = ImGui.getWindowPos();
        float winW = ImGui.getWindowWidth();
        float titleH = ImGui.getFrameHeight();
        float boxMinX = winPos.x + winW - titleH;
        float boxMaxX = winPos.x + winW;
        float cx = boxMinX + titleH * 0.5f;
        float cy = winPos.y + titleH * 0.5f;

        boolean hovered = ImGui.isMouseHoveringRect(boxMinX, winPos.y, boxMaxX, winPos.y + titleH, false);
        int color = hovered ? ThemeManager.textColor() : ThemeManager.textDimColor();
        float arm = ImGui.getFontSize() * CLOSE_ARM_HALF;
        float thickness = Math.max(1f, ThemeManager.uiScale());

        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRect(boxMinX, winPos.y, boxMaxX, winPos.y + titleH, false);
        dl.addLine(cx - arm, cy - arm, cx + arm, cy + arm, color, thickness);
        dl.addLine(cx - arm, cy + arm, cx + arm, cy - arm, color, thickness);
        dl.popClipRect();

        return hovered && ImGui.isMouseClicked(0);
    }

    private static void fullWidthBorderLine() {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 winPos = ImGui.getWindowPos();
        float winW = ImGui.getWindowWidth();
        float y = ImGui.getCursorScreenPos().y;
        dl.pushClipRect(winPos.x, y - 1f, winPos.x + winW, y + 2f, false);
        dl.addLine(winPos.x, y, winPos.x + winW, y, ThemeManager.borderColor(), 1f);
        dl.popClipRect();
        ImGui.dummy(0f, 1f);
    }
}
