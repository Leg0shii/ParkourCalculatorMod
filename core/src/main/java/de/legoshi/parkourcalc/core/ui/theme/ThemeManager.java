package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableFlags;

/** Catppuccin Mocha palette + standard table helpers. See docs/UI_REDESIGN.md. */
public final class ThemeManager {

    private static final float[] BG           = rgb(0x1e, 0x1e, 0x2e, 1.00f);
    private static final float[] BG_DARK      = rgb(0x11, 0x11, 0x1b, 1.00f);
    private static final float[] BG_MENU      = rgb(0x18, 0x18, 0x25, 1.00f);
    private static final float[] PANEL        = rgb(0x31, 0x32, 0x44, 1.00f);
    private static final float[] PANEL_HOVER  = rgb(0x45, 0x47, 0x5a, 1.00f);
    private static final float[] PANEL_FOCUS  = rgb(0x4e, 0x51, 0x66, 1.00f);
    private static final float[] PANEL_ACTIVE = rgb(0x58, 0x5b, 0x70, 1.00f);
    private static final float[] OVERLAY0     = rgb(0x6c, 0x70, 0x86, 1.00f);
    private static final float[] BORDER       = rgb(0x45, 0x47, 0x5a, 1.00f);
    private static final float[] TEXT         = rgb(0xcd, 0xd6, 0xf4, 1.00f);
    private static final float[] TEXT_MUTED   = rgb(0xa6, 0xad, 0xc8, 1.00f);
    private static final float[] TEXT_DIM     = rgb(0x6c, 0x70, 0x86, 1.00f);
    private static final float[] ACCENT       = rgb(0x89, 0xb4, 0xfa, 1.00f);
    private static final float[] ACCENT_DIM   = rgb(0x89, 0xb4, 0xfa, 0.30f);
    private static final float[] SELECTED     = rgb(0xcb, 0xa6, 0xf7, 1.00f);
    private static final float[] WARNING      = rgb(0xf9, 0xe2, 0xaf, 1.00f);
    private static final float[] DANGER       = rgb(0xf3, 0x8b, 0xa8, 1.00f);
    private static final float[] OK           = rgb(0xa6, 0xe3, 0xa1, 1.00f);
    private static final float[] FOCUS        = rgb(0xb4, 0xbe, 0xfe, 1.00f);
    private static final float[] STATUS_BG    = rgb(0x18, 0x18, 0x25, 1.00f);

    private static final float[] TABLE_ROW_BASE         = rgb(0x31, 0x32, 0x44, 1.00f);
    private static final float[] TABLE_ROW_ALT          = rgb(0x2f, 0x31, 0x42, 1.00f);
    private static final float[] TABLE_ROW_HOVER        = rgb(0x39, 0x3b, 0x4d, 1.00f);
    private static final float[] TABLE_ROW_SELECTED     = rgb(0xcb, 0xa6, 0xf7, 0.75f);
    private static final float[] TABLE_HEADER_BG        = rgb(0x45, 0x47, 0x5a, 1.00f);
    private static final float[] TABLE_HEADER_TEXT      = rgb(0xcd, 0xd6, 0xf4, 1.00f);
    private static final float[] TABLE_CELL_BORDER      = rgb(0x45, 0x47, 0x5a, 1.00f);
    private static final float[] TABLE_FOCUS_RING       = rgb(0xb4, 0xbe, 0xfe, 1.00f);
    private static final float[] TABLE_POPULATED_BORDER = rgb(0x58, 0x5b, 0x70, 1.00f);

    private static final float XXS = 2.0f;
    private static final float XS = 4.0f;
    private static final float SM = 8.0f;
    private static final float MD = 12.0f;
    private static final float LG = 16.0f;
    private static final float SCROLLBAR_SIZE = 18.0f;

    public enum HAlign { LEFT, CENTER, RIGHT }

    private static boolean applied;

    private ThemeManager() {}

    public static void apply() {
        ImGui.styleColorsDark();

        ImGuiStyle s = ImGui.getStyle();

        s.setWindowPadding(LG, LG);
        s.setFramePadding(MD, SM);
        s.setItemSpacing(SM, SM);
        s.setItemInnerSpacing(XS, XS);
        s.setCellPadding(SM, XS);
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

        setColor(ImGuiCol.Text, TEXT);
        setColor(ImGuiCol.TextDisabled, TEXT_DIM);
        setColor(ImGuiCol.WindowBg, BG);
        setColor(ImGuiCol.ChildBg, BG);
        setColor(ImGuiCol.PopupBg, BG);
        setColor(ImGuiCol.Border, BORDER);
        setColor(ImGuiCol.FrameBg, PANEL_HOVER);
        setColor(ImGuiCol.FrameBgHovered, PANEL_ACTIVE);
        setColor(ImGuiCol.FrameBgActive, PANEL_FOCUS);
        setColor(ImGuiCol.TitleBg, BG_DARK);
        setColor(ImGuiCol.TitleBgActive, BG_DARK);
        setColor(ImGuiCol.TitleBgCollapsed, BG_DARK);
        setColor(ImGuiCol.MenuBarBg, BG_MENU);
        setColor(ImGuiCol.ScrollbarBg, BG);
        setColor(ImGuiCol.ScrollbarGrab, PANEL_ACTIVE);
        setColor(ImGuiCol.ScrollbarGrabHovered, OVERLAY0);
        setColor(ImGuiCol.ScrollbarGrabActive, OVERLAY0);
        setColor(ImGuiCol.CheckMark, ACCENT);
        setColor(ImGuiCol.SliderGrab, ACCENT);
        setColor(ImGuiCol.SliderGrabActive, ACCENT);
        setColor(ImGuiCol.Button, PANEL);
        setColor(ImGuiCol.ButtonHovered, PANEL_HOVER);
        setColor(ImGuiCol.ButtonActive, PANEL_ACTIVE);
        setColor(ImGuiCol.Header, PANEL);
        setColor(ImGuiCol.HeaderHovered, PANEL_HOVER);
        setColor(ImGuiCol.HeaderActive, PANEL_ACTIVE);
        setColor(ImGuiCol.Separator, BORDER);
        setColor(ImGuiCol.SeparatorHovered, ACCENT_DIM);
        setColor(ImGuiCol.SeparatorActive, ACCENT);
        setColor(ImGuiCol.ResizeGrip, PANEL);
        setColor(ImGuiCol.ResizeGripHovered, PANEL_HOVER);
        setColor(ImGuiCol.ResizeGripActive, ACCENT);
        setColor(ImGuiCol.Tab, BG_DARK);
        setColor(ImGuiCol.TabHovered, PANEL_HOVER);
        setColor(ImGuiCol.TabActive, PANEL);
        setColor(ImGuiCol.TabUnfocused, BG_DARK);
        setColor(ImGuiCol.TabUnfocusedActive, PANEL);
        // RowBg/RowBgAlt enum slot drifts between imgui-java 1.86 (Forge) and 1.90 (Fabric); paint rows per-row via tableSetBgColor instead.
        setColor(ImGuiCol.TableHeaderBg, BORDER);
        setColor(ImGuiCol.TableBorderStrong, BORDER);
        setColor(ImGuiCol.TableBorderLight, BORDER);
        setColor(ImGuiCol.TextSelectedBg, ACCENT_DIM);
        setColor(ImGuiCol.DragDropTarget, ACCENT);
        setColor(ImGuiCol.NavHighlight, ACCENT);

        applied = true;
    }

    public static boolean isApplied() {
        return applied;
    }

    public static int standardTableFlags() {
        return ImGuiTableFlags.RowBg
                | ImGuiTableFlags.SizingFixedFit
                | ImGuiTableFlags.ScrollY
                | ImGuiTableFlags.BordersInnerV;
    }

    public static boolean beginStandardTable(String id, int columnCount) {
        return beginStandardTable(id, columnCount, 0, 0f, 0f);
    }

    public static boolean beginStandardTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(false);
        boolean ok = ImGui.beginTable(id, columnCount, standardTableFlags() | extraFlags, outerWidth, outerHeight);
        if (!ok) popTableSelectionChrome();
        return ok;
    }

    public static boolean beginStandardClickableRowsTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(true);
        boolean ok = ImGui.beginTable(id, columnCount, standardTableFlags() | extraFlags, outerWidth, outerHeight);
        if (!ok) popTableSelectionChrome();
        return ok;
    }

    public static boolean beginStandardKeyValueTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(false);
        int flags = (standardTableFlags() & ~ImGuiTableFlags.BordersInnerV) | extraFlags;
        boolean ok = ImGui.beginTable(id, columnCount, flags, outerWidth, outerHeight);
        if (!ok) popTableSelectionChrome();
        return ok;
    }

    public static void endStandardTable() {
        ImGui.endTable();
        popTableSelectionChrome();
    }

    private static void pushTableSelectionChrome(boolean rowsClickable) {
        if (rowsClickable) {
            float[] s = TABLE_ROW_SELECTED;
            ImGui.pushStyleColor(ImGuiCol.Header, s[0], s[1], s[2], s[3]);
            float[] h = TABLE_ROW_HOVER;
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, h[0], h[1], h[2], h[3]);
            ImGui.pushStyleColor(ImGuiCol.HeaderActive, h[0], h[1], h[2], h[3]);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Header, 0f, 0f, 0f, 0f);
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0f, 0f, 0f, 0f);
            ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0f, 0f, 0f, 0f);
        }
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f);
    }

    private static void popTableSelectionChrome() {
        ImGui.popStyleColor(4);
    }

    public static void tableHeader(String label) {
        tableHeader(label, HAlign.LEFT);
    }

    public static void tableHeader(String label, HAlign alignment) {
        Fonts.pushBold();
        if (alignment == HAlign.LEFT) {
            ImGui.tableHeader(label);
        } else {
            renderAlignedHeaderOverlay(label, alignment);
        }
        Fonts.popBold();
    }

    public static void tableHeaderCentered(String label) {
        tableHeader(label, HAlign.CENTER);
    }

    public static void tableHeaderRight(String label) {
        tableHeader(label, HAlign.RIGHT);
    }

    private static void renderAlignedHeaderOverlay(String label, HAlign alignment) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float colW = ImGui.getColumnWidth();
        float cellPad = ImGui.getStyle().getCellPadding().x;
        ImGui.tableHeader("##" + label);
        ImVec2 textSize = ImGui.calcTextSize(label);
        float dx;
        if (alignment == HAlign.CENTER) {
            dx = (colW - textSize.x) * 0.5f - cellPad;
        } else {
            dx = colW - textSize.x - 2f * cellPad;
        }
        if (dx < 0f) dx = 0f;
        float tx = cellOrigin.x + dx;
        float ty = cellOrigin.y;
        ImGui.getWindowDrawList().addText(tx, ty, u32(TABLE_HEADER_TEXT), label);
    }

    public static void centerNextItem(float itemWidth) {
        float avail = ImGui.getContentRegionAvail().x;
        if (itemWidth > 0f && itemWidth < avail) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - itemWidth) * 0.5f);
        }
    }

    public static void textCentered(String label) {
        centerNextItem(ImGui.calcTextSize(label).x);
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
    }

    public static void textLeft(String text) {
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    public static void textCenter(String text) {
        float colW = ImGui.getColumnWidth();
        float textW = ImGui.calcTextSize(text).x;
        float cellPad = ImGui.getStyle().getCellPadding().x;
        float offset = (colW - textW) * 0.5f - cellPad;
        if (offset > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    public static void textRight(String text) {
        float avail = ImGui.getContentRegionAvail().x;
        float textW = ImGui.calcTextSize(text).x;
        if (avail > textW) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - textW);
        }
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    public static void sectionSpacing() {
        ImGui.spacing();
    }

    public static boolean centeredSelectable(String idSuffix, String label, boolean selected, int flags) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        ImGui.alignTextToFramePadding();
        boolean clicked = ImGui.selectable("##" + idSuffix, selected, flags);
        if (label != null && !label.isEmpty()) {
            ImVec2 textSize = ImGui.calcTextSize(label);
            float tx = cellOrigin.x + (cellW - textSize.x) * 0.5f;
            float ty = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
            ImGui.getWindowDrawList().addText(tx, ty, ImGui.getColorU32(ImGuiCol.Text), label);
        }
        return clicked;
    }

    public static boolean centeredSelectable(String idSuffix, String label, boolean selected) {
        return centeredSelectable(idSuffix, label, selected, 0);
    }

    public static boolean rightAlignedSelectable(String idSuffix, String label, boolean selected, int flags) {
        return rightAlignedSelectable(idSuffix, label, selected, flags, 0f, 0f);
    }

    public static boolean rightAlignedSelectable(String idSuffix, String label, boolean selected, int flags, float sizeX, float sizeY) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        ImGui.alignTextToFramePadding();
        boolean clicked = ImGui.selectable("##" + idSuffix, selected, flags, sizeX, sizeY);
        if (label != null && !label.isEmpty()) {
            ImVec2 textSize = ImGui.calcTextSize(label);
            float tx = cellOrigin.x + (cellW - textSize.x);
            float ty = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
            ImGui.getWindowDrawList().addText(tx, ty, ImGui.getColorU32(ImGuiCol.Text), label);
        }
        return clicked;
    }

    public static float tableColumnWidth(String headerLabel, float dataWidth) {
        return Math.max(boldTextWidth(headerLabel), dataWidth);
    }

    public static float tableLeftmostColumnWidth(String headerLabel, float dataWidth) {
        return tableColumnWidth(headerLabel, dataWidth) + tableEdgeCellInset();
    }

    public static float tableRightmostColumnWidth(String headerLabel, float dataWidth, float scrollbarSlack) {
        float content = Math.max(boldTextWidth(headerLabel), dataWidth);
        float cellPad = ImGui.getStyle().getCellPadding().x;
        return content + 2f * cellPad + scrollbarSlack;
    }

    public static float tableNumericColumnWidth(String headerLabel, float dataWidth) {
        float content = Math.max(boldTextWidth(headerLabel), dataWidth);
        float cellPad = ImGui.getStyle().getCellPadding().x;
        return content + 2f * cellPad;
    }

    public static float tableScrollbarSlack() {
        return SCROLLBAR_SIZE;
    }

    private static float boldTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;
        Fonts.pushBold();
        float w = ImGui.calcTextSize(text).x;
        Fonts.popBold();
        return w;
    }

    public static void tableLeftmostCellPad() {
        emitEdgeDummy(true);
    }

    public static void tableRightmostCellTrailingPad() {
        emitEdgeDummy(false);
    }

    private static void emitEdgeDummy(boolean leading) {
        float pad = tableEdgeCellInset();
        if (pad <= 0f) return;
        if (leading) {
            ImGui.dummy(pad, 0f);
            ImGui.sameLine(0f, 0f);
        } else {
            ImGui.sameLine(0f, 0f);
            ImGui.dummy(pad, 0f);
        }
    }

    private static float tableEdgeCellInset() {
        return Math.max(0f, SCROLLBAR_SIZE - ImGui.getStyle().getCellPadding().x);
    }

    public static void paintTableHeader() {
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, u32(TABLE_HEADER_BG));
    }

    public static void paintTableRowBg(int rowIndex) {
        int bg = (rowIndex & 1) == 0 ? u32(TABLE_ROW_BASE) : u32(TABLE_ROW_ALT);
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, bg);
    }

    public static void paintTableRowTint(int tint) {
        if (tint != 0) ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg1, tint);
    }

    public static void pushSelectedFrameBg() {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, SELECTED[0], SELECTED[1], SELECTED[2], 0.70f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, SELECTED[0], SELECTED[1], SELECTED[2], 0.80f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, SELECTED[0], SELECTED[1], SELECTED[2], 0.80f);
        ImGui.pushStyleColor(ImGuiCol.Text, BG[0], BG[1], BG[2], BG[3]);
    }

    public static void popSelectedFrameBg() {
        ImGui.popStyleColor(4);
    }

    public static void pushPopulatedFrameBorder() {
        ImGui.pushStyleColor(ImGuiCol.Border, PANEL_ACTIVE[0], PANEL_ACTIVE[1], PANEL_ACTIVE[2], PANEL_ACTIVE[3]);
    }

    public static void popPopulatedFrameBorder() {
        ImGui.popStyleColor(1);
    }

    public static void pushStatusAreaChildBg() {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, STATUS_BG[0], STATUS_BG[1], STATUS_BG[2], STATUS_BG[3]);
    }

    public static void popStatusAreaChildBg() {
        ImGui.popStyleColor(1);
    }

    public static int textColor() {
        return u32(TEXT);
    }

    public static int tableCellText(boolean rowIsSelected) {
        return u32(TEXT);
    }

    public static int textMutedColor() {
        return u32(TEXT_MUTED);
    }

    public static int textDimColor() {
        return u32(TEXT_DIM);
    }

    public static int dangerColor() {
        return u32(DANGER);
    }

    public static int warningColor() {
        return u32(WARNING);
    }

    public static int okColor() {
        return u32(OK);
    }

    public static int accentColor() {
        return u32(ACCENT);
    }

    public static int accentDimColor() {
        return u32(ACCENT_DIM);
    }

    public static int focusColor() {
        return u32(FOCUS);
    }

    public static int hoverColor() {
        return u32(PANEL_HOVER);
    }

    public static int borderColor() {
        return u32(BORDER);
    }

    public static int warningTintColor(float alpha) {
        return ImGui.colorConvertFloat4ToU32(WARNING[0], WARNING[1], WARNING[2], alpha);
    }

    public static int selectedTintColor(float alpha) {
        return ImGui.colorConvertFloat4ToU32(SELECTED[0], SELECTED[1], SELECTED[2], alpha);
    }

    public static int rgbaTintColor(float[] rgba) {
        return ImGui.colorConvertFloat4ToU32(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    public static void pushTextColor(int color) {
        ImGui.pushStyleColor(ImGuiCol.Text, color);
    }

    public static void popTextColor() {
        ImGui.popStyleColor();
    }

    public static void pushDangerButton() {
        ImGui.pushStyleColor(ImGuiCol.Button, DANGER[0], DANGER[1], DANGER[2], DANGER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, DANGER[0] * 1.2f, DANGER[1] * 1.2f, DANGER[2] * 1.2f, DANGER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, DANGER[0] * 0.8f, DANGER[1] * 0.8f, DANGER[2] * 0.8f, DANGER[3]);
    }

    public static void popDangerButton() {
        ImGui.popStyleColor(3);
    }

    public static void pushPrimaryButton() {
        ImGui.pushStyleColor(ImGuiCol.Button, ACCENT[0], ACCENT[1], ACCENT[2], ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ACCENT[0] * 1.15f, ACCENT[1] * 1.15f, ACCENT[2] * 1.15f, ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ACCENT[0] * 0.85f, ACCENT[1] * 0.85f, ACCENT[2] * 0.85f, ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.Text, BG_DARK[0], BG_DARK[1], BG_DARK[2], 1.0f);
    }

    public static void popPrimaryButton() {
        ImGui.popStyleColor(4);
    }

    private static float[] rgb(int r, int g, int b, float a) {
        return new float[]{r / 255f, g / 255f, b / 255f, a};
    }

    private static int u32(float[] c) {
        return ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], c[3]);
    }

    private static void setColor(int idx, float[] rgba) {
        ImGui.getStyle().setColor(idx, rgba[0], rgba[1], rgba[2], rgba[3]);
    }
}
