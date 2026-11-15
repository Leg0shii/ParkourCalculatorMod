package de.legoshi.parkourcalc.core.ui.util;

import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;

// ImGui native setTooltip() doesn't wrap; this is the replacement.
public final class TooltipUtil {

    private static final float WRAP_WIDTH = 1050.0f;

    public static void wrappedTooltip(String text) {
        float pad = ThemeManager.LG * ThemeManager.uiScale();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(WRAP_WIDTH);
        ImGui.text(text);
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
        ImGui.popStyleVar();
    }

    public static void onHover(String text) {
        if (ImGui.isItemHovered()) wrappedTooltip(text);
    }
}
