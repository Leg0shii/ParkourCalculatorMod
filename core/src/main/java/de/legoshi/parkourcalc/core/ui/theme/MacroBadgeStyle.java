package de.legoshi.parkourcalc.core.ui.theme;

/**
 * Constants for the "MACRO" badge rendered on the Minecraft HUD while playback
 * drives the real player. Shared across all loader HUD renderers so the badge
 * looks identical on Fabric and both Forge variants. Not part of ThemeManager
 * because the badge is drawn through Minecraft's text/fill APIs, not ImGui.
 */
public final class MacroBadgeStyle {

    public static final String LABEL = "MACRO";

    /** ARGB. Pale red, ~30% alpha. */
    public static final int COLOR_ARGB = 0x4DFF6060;

    private MacroBadgeStyle() {}
}
