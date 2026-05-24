package de.legoshi.parkourcalc.core.ui.util;

import imgui.ImGui;

/**
 * Tooltip helpers that wrap text to a fixed pixel width. Wrap width chosen so
 * two sentences fit on a 1080p screen at 1.5x UI scale. ImGui native
 * setTooltip() does not wrap; this is the single replacement.
 */
public final class TooltipUtil {

    private static final float WRAP_WIDTH = 350.0f;

    private TooltipUtil() {}

    public static void wrappedTooltip(String text) {
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(WRAP_WIDTH);
        ImGui.text(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }
}
