package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImFont;
import imgui.ImGui;

/**
 * Loader-published font handles. Loaders register the bold font matching the
 * current UI scale; core widgets push it around emphasized text (table headers).
 * Null-safe: push/pop are no-ops until the loader registers, so headless tests
 * and the brief window before init don't NPE.
 */
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
}
