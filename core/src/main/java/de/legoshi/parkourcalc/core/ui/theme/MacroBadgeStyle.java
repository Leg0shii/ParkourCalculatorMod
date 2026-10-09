package de.legoshi.parkourcalc.core.ui.theme;

/** Shared constants for the playback MACRO HUD badge across all loaders. */
public final class MacroBadgeStyle {

    public static final String LABEL = "MACRO";
    public static final int COLOR_ARGB = 0x4DFF6060;

    public static final String TELEPORT_LABEL = "Teleported";
    private static final int TELEPORT_COLOR_RGB = 0x00FF6060;

    public static int teleportColorArgb(float alpha) {
        float a = alpha < 0f ? 0f : (alpha > 1f ? 1f : alpha);
        int ai = Math.round(a * 255f);
        return (ai << 24) | TELEPORT_COLOR_RGB;
    }

}
