package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImFont;
import imgui.ImGui;

/** Loader-published bold font, null-safe before the loader registers. */
public final class Fonts {

    private static ImFont boldFont;

    private Fonts() {}

    public static void setBoldFont(ImFont font) {
        boldFont = font;
    }

    public static void pushBold() {
        if (boldFont != null) ImGui.pushFont(boldFont);
    }

    public static void popBold() {
        if (boldFont != null) ImGui.popFont();
    }

    /** Bold face for manual draw-list text; falls back to the active font before the loader registers. */
    public static ImFont boldOrDefault() {
        return boldFont != null ? boldFont : ImGui.getFont();
    }
}
