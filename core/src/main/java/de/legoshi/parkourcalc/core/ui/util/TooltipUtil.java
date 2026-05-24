package de.legoshi.parkourcalc.core.ui.util;

import imgui.ImGui;

// ImGui native setTooltip() doesn't wrap; this is the replacement.
public final class TooltipUtil {

    private static final float WRAP_WIDTH = 1050.0f;

    private TooltipUtil() {}

    public static void wrappedTooltip(String text) {
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(WRAP_WIDTH);
        ImGui.text(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
    }
}
