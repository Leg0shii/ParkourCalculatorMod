package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImFont;
import imgui.ImGui;

/** Loader-published bold font, null-safe before the loader registers. */
public final class Fonts {

    private static ImFont boldFont;

    public static void setBoldFont(ImFont font) {
        boldFont = font;
    }

    public static void pushBold() {
        if (boldFont != null) ImGui.pushFont(boldFont);
    }

    public static void popBold() {
        if (boldFont != null) ImGui.popFont();
    }
}
