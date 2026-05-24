package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * Central source for UI chrome colors and spacing. See docs/UI_REDESIGN.md
 * for the token table and the Visual quality contract. apply() is idempotent
 * and may be called any number of times after ImGui context creation.
 */
public final class ThemeManager {

    // Tokens. Each token has one semantic job; do not reuse one for two states.
    //   BG          window background
    //   PANEL       sub-panels, frame fill at rest (inputs/sliders/checkbox bg)
    //   PANEL_ALT   alt-row banding ONLY; must differ from PANEL by >= 6% lightness
    //   HOVER       row/button/widget hover background (NOT alt-banding)
    //   FOCUS       accent at 0.30 alpha; 2px outline on focused inputs (never fill)
    //   BORDER      panel borders, separators, 1px frame outline at rest
    //   TEXT        primary body text
    //   TEXT_MUTED  secondary/disabled labels; still legible (>=3:1 vs BG)
    //   ACCENT      primary buttons, slider grab, selected row tint, focus ring
    //   ACCENT_DIM  selected fill, drag-drop preview line, button active
    //   DANGER      destructive actions, error text
    //   WARNING     dirty marker, validation hints
    //   OK          success toast/status text
    private static final float[] BG            = {0.10f, 0.10f, 0.11f, 0.95f};
    private static final float[] PANEL         = {0.13f, 0.13f, 0.15f, 1.00f};
    private static final float[] PANEL_ALT     = {0.21f, 0.21f, 0.23f, 1.00f};
    private static final float[] HOVER         = {0.20f, 0.20f, 0.23f, 1.00f};
    private static final float[] FOCUS         = {0.30f, 0.65f, 1.00f, 0.30f};
    private static final float[] BORDER        = {0.25f, 0.25f, 0.28f, 1.00f};
    private static final float[] TEXT          = {0.92f, 0.92f, 0.94f, 1.00f};
    private static final float[] TEXT_MUTED    = {0.60f, 0.60f, 0.65f, 1.00f};
    private static final float[] ACCENT        = {0.30f, 0.65f, 1.00f, 1.00f};
    private static final float[] ACCENT_DIM    = {0.20f, 0.45f, 0.80f, 0.45f};
    private static final float[] DANGER        = {0.80f, 0.25f, 0.25f, 1.00f};
    private static final float[] WARNING       = {0.95f, 0.65f, 0.20f, 1.00f};
    private static final float[] OK            = {0.35f, 0.80f, 0.40f, 1.00f};

    private static final float XXS = 2.0f;
    private static final float XS  = 4.0f;
    private static final float SM  = 8.0f;
    private static final float MD  = 12.0f;
    private static final float LG  = 16.0f;

    // Scrollbar bumped per contract; do not touch MD globally.
    private static final float SCROLLBAR_SIZE = 14.0f;

    private static boolean applied;

    private ThemeManager() {}

    public static void apply() {
        ImGuiStyle s = ImGui.getStyle();

        s.setWindowPadding(LG, LG);
        s.setFramePadding(SM, XS);
        s.setItemSpacing(SM, XS);
        s.setItemInnerSpacing(XS, XS);
        s.setCellPadding(XS, XXS);
        s.setScrollbarSize(SCROLLBAR_SIZE);
        s.setGrabMinSize(MD);

        s.setWindowBorderSize(1.0f);
        s.setFrameBorderSize(1.0f);
        s.setWindowRounding(4.0f);
        s.setFrameRounding(3.0f);
        s.setTabRounding(3.0f);
        s.setScrollbarRounding(3.0f);
        s.setGrabRounding(3.0f);
        s.setPopupRounding(4.0f);

        setColor(ImGuiCol.Text,                 TEXT);
        setColor(ImGuiCol.TextDisabled,         TEXT_MUTED);
        setColor(ImGuiCol.WindowBg,             BG);
        setColor(ImGuiCol.ChildBg,              BG);
        setColor(ImGuiCol.PopupBg,              PANEL);
        setColor(ImGuiCol.Border,               BORDER);
        setColor(ImGuiCol.FrameBg,              PANEL);
        setColor(ImGuiCol.FrameBgHovered,       HOVER);
        setColor(ImGuiCol.FrameBgActive,        HOVER);
        setColor(ImGuiCol.TitleBg,              PANEL);
        setColor(ImGuiCol.TitleBgActive,        PANEL_ALT);
        setColor(ImGuiCol.TitleBgCollapsed,     PANEL);
        setColor(ImGuiCol.MenuBarBg,            PANEL);
        setColor(ImGuiCol.ScrollbarBg,          BG);
        setColor(ImGuiCol.ScrollbarGrab,        PANEL_ALT);
        setColor(ImGuiCol.ScrollbarGrabHovered, HOVER);
        setColor(ImGuiCol.ScrollbarGrabActive,  ACCENT);
        setColor(ImGuiCol.CheckMark,            ACCENT);
        setColor(ImGuiCol.SliderGrab,           ACCENT);
        setColor(ImGuiCol.SliderGrabActive,     ACCENT);
        setColor(ImGuiCol.Button,               PANEL_ALT);
        setColor(ImGuiCol.ButtonHovered,        HOVER);
        setColor(ImGuiCol.ButtonActive,         ACCENT_DIM);
        setColor(ImGuiCol.Header,               ACCENT_DIM);
        setColor(ImGuiCol.HeaderHovered,        HOVER);
        setColor(ImGuiCol.HeaderActive,         ACCENT);
        setColor(ImGuiCol.Separator,            BORDER);
        setColor(ImGuiCol.SeparatorHovered,     ACCENT_DIM);
        setColor(ImGuiCol.SeparatorActive,      ACCENT);
        setColor(ImGuiCol.ResizeGrip,           PANEL_ALT);
        setColor(ImGuiCol.ResizeGripHovered,    HOVER);
        setColor(ImGuiCol.ResizeGripActive,     ACCENT);
        setColor(ImGuiCol.Tab,                  PANEL);
        setColor(ImGuiCol.TabHovered,           HOVER);
        setColor(ImGuiCol.TabActive,            ACCENT_DIM);
        setColor(ImGuiCol.TabUnfocused,         PANEL);
        setColor(ImGuiCol.TabUnfocusedActive,   PANEL_ALT);
        setColor(ImGuiCol.TableHeaderBg,        PANEL);
        setColor(ImGuiCol.TableBorderStrong,    BORDER);
        setColor(ImGuiCol.TableBorderLight,     BORDER);
        setColor(ImGuiCol.TableRowBg,           BG);
        setColor(ImGuiCol.TableRowBgAlt,        PANEL_ALT);
        setColor(ImGuiCol.TextSelectedBg,       ACCENT_DIM);
        setColor(ImGuiCol.DragDropTarget,       ACCENT);
        setColor(ImGuiCol.NavHighlight,         ACCENT);

        applied = true;
    }

    public static boolean isApplied() {
        return applied;
    }

    public static void pushTransparentHeader() {
        // The selectable Header tint covers row text on the input grid; keep it transparent
        // so selection background drawn by setRowBackground stays visible underneath.
        ImGui.pushStyleColor(ImGuiCol.Header,         0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered,  0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static void popTransparentHeader() {
        ImGui.popStyleColor(3);
    }

    public static int textColor()       { return u32(TEXT); }
    public static int textMutedColor()  { return u32(TEXT_MUTED); }
    public static int dangerColor()     { return u32(DANGER); }
    public static int warningColor()    { return u32(WARNING); }
    public static int okColor()         { return u32(OK); }
    public static int accentColor()     { return u32(ACCENT); }
    public static int accentDimColor()  { return u32(ACCENT_DIM); }
    public static int focusColor()      { return u32(FOCUS); }
    public static int hoverColor()      { return u32(HOVER); }
    public static int borderColor()     { return u32(BORDER); }

    /** Warning tint with custom alpha, for row backgrounds and similar overlays. */
    public static int warningTintColor(float alpha) {
        return ImGui.colorConvertFloat4ToU32(WARNING[0], WARNING[1], WARNING[2], alpha);
    }

    public static void pushTextColor(int color) {
        ImGui.pushStyleColor(ImGuiCol.Text, color);
    }

    public static void popTextColor() {
        ImGui.popStyleColor();
    }

    public static void pushDangerButton() {
        ImGui.pushStyleColor(ImGuiCol.Button,        DANGER[0],        DANGER[1],        DANGER[2],        DANGER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, DANGER[0] * 1.2f, DANGER[1] * 1.2f, DANGER[2] * 1.2f, DANGER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  DANGER[0] * 0.8f, DANGER[1] * 0.8f, DANGER[2] * 0.8f, DANGER[3]);
    }

    public static void popDangerButton() {
        ImGui.popStyleColor(3);
    }

    public static void pushPrimaryButton() {
        ImGui.pushStyleColor(ImGuiCol.Button,        ACCENT[0],        ACCENT[1],        ACCENT[2],        ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT[0] * 1.15f, ACCENT[1] * 1.15f, ACCENT[2] * 1.15f, ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  ACCENT[0] * 0.85f, ACCENT[1] * 0.85f, ACCENT[2] * 0.85f, ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.Text,          0.05f, 0.08f, 0.12f, 1.00f);
    }

    public static void popPrimaryButton() {
        ImGui.popStyleColor(4);
    }

    public static void pushCompactTable() {
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, XS, XXS);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, SM, XXS);
    }

    public static void popCompactTable() {
        ImGui.popStyleVar(2);
    }

    private static int u32(float[] c) {
        return ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], c[3]);
    }

    private static void setColor(int idx, float[] rgba) {
        ImGui.getStyle().setColor(idx, rgba[0], rgba[1], rgba[2], rgba[3]);
    }
}
