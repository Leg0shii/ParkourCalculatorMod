package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTableRowFlags;

/** Catppuccin Mocha palette + standard table helpers. */
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
    private static final float[] SELECTED     = rgb(0x89, 0xb4, 0xfa, 1.00f);
    private static final float[] WARNING      = rgb(0xf9, 0xe2, 0xaf, 1.00f);
    private static final float[] DANGER       = rgb(0xf3, 0x8b, 0xa8, 1.00f);
    private static final float[] OK           = rgb(0xa6, 0xe3, 0xa1, 1.00f);
    private static final float[] FOCUS        = rgb(0xb4, 0xbe, 0xfe, 1.00f);
    private static final float[] STATUS_BG    = rgb(0x18, 0x18, 0x25, 1.00f);

    // Button state fills, lifted byte-exact from kit.css .btn--primary / .btn--danger swatches.
    private static final float[] ACCENT_HOVER  = rgb(0x9d, 0xc4, 0xff, 1.00f);
    private static final float[] ACCENT_ACTIVE = rgb(0x74, 0x99, 0xd5, 1.00f);
    private static final float[] ACCENT_BORDER = rgb(0x6a, 0x9b, 0xf0, 1.00f);
    private static final float[] DANGER_HOVER  = rgb(0xff, 0xa3, 0xbd, 1.00f);
    private static final float[] DANGER_ACTIVE = rgb(0xd0, 0x76, 0x8f, 1.00f);
    private static final float[] DANGER_BORDER = rgb(0xe0, 0x6a, 0x8c, 1.00f);

    // Table tokens that restate a chrome value are aliases, not second literals (design system 4.2 / 6 C2).
    private static final float[] TABLE_ROW_BASE         = PANEL;
    private static final float[] TABLE_ROW_ALT          = rgb(0x2a, 0x2a, 0x3c, 1.00f);
    private static final float[] TABLE_ROW_HOVER        = rgb(0x39, 0x3b, 0x4d, 1.00f);
    private static final float[] TABLE_ROW_SELECTED     = rgb(0x89, 0xb4, 0xfa, 0.45f);
    private static final float[] TABLE_HEADER_BG        = BG_DARK;
    private static final float[] TABLE_HEADER_TEXT      = TEXT_MUTED;
    private static final float[] TABLE_CELL_BORDER      = BORDER;
    private static final float[] TABLE_FOCUS_RING       = FOCUS;
    private static final float[] TABLE_POPULATED_BORDER = PANEL_ACTIVE;

    private static final float HAIR = 1.0f;
    private static final float XXS = 2.0f;
    public static final float XS = 4.0f;
    public static final float SM = 8.0f;
    private static final float MD = 12.0f;
    public static final float LG = 16.0f;
    private static final float SCROLLBAR_SIZE = 9f;

    // Per-component padding tokens (px @ 1x, scaled by appliedScale). One knob per element type.
    private static final float HEADER_CELL_PAD_Y = 4f;            // extra vertical padding per side in table header cells
    private static final float SECTION_SPACING   = 8f;            // sectionSpacing() gap
    private static final float MENU_PAD_X = 12f, MENU_PAD_Y = 6f; // padding inside each menu bar / dropdown element
    private static final float MENU_POPUP_PAD_Y = 8f;            // taller dropdown rows (menu bar stays at MENU_PAD_Y); ItemSpacing.y untouched so separators keep their padding
    private static final float MENU_POPUP_HILITE_SPACING_X = 2f * LG; // ItemSpacing.x inside the popup; ImGui draws the row highlight as the content rect grown by half of this each side, so 2*WindowPadding makes it reach the popup edge without moving the text
    private static final float HEADER_EXTRA_HEIGHT = 8f;          // px @1x added to a window/modal title bar over a default frame (split across both sides)
    private static final float HEADER_TEXT_PAD_X = 8f;           // px @1x inset of title-bar text from the window edges (matches design .modal__title)

    private static float appliedScale = 1f;

    public enum HAlign { LEFT, CENTER, RIGHT }

    private static boolean applied;

    private ThemeManager() {}

    public static void apply(float uiScale) {
        appliedScale = uiScale;
        ImGui.styleColorsDark();

        ImGuiStyle s = ImGui.getStyle();

        s.setWindowPadding(LG * uiScale, LG * uiScale);
        s.setFramePadding(XS * uiScale, XXS * uiScale);
        s.setItemSpacing(SM * uiScale, XS * uiScale);
        s.setItemInnerSpacing(XS * uiScale, XS * uiScale);
        s.setCellPadding(XS * uiScale, HAIR * uiScale);
        s.setScrollbarSize(SCROLLBAR_SIZE * uiScale);
        s.setGrabMinSize(MD * uiScale);

        // Hairline borders stay 1px at every scale; scaling would fatten them (design system 5.5).
        s.setWindowBorderSize(1.0f);
        s.setFrameBorderSize(1.0f);
        s.setWindowRounding(4.0f * uiScale);
        s.setFrameRounding(3.0f * uiScale);
        s.setTabRounding(3.0f * uiScale);
        s.setScrollbarRounding(3.0f * uiScale);
        s.setGrabRounding(3.0f * uiScale);
        s.setPopupRounding(4.0f * uiScale);

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
        setColor(ImGuiCol.TableHeaderBg, BG_DARK);
        setColor(ImGuiCol.TableBorderStrong, BORDER);
        setColor(ImGuiCol.TableBorderLight, BORDER);
        setColor(ImGuiCol.TextSelectedBg, ACCENT_DIM);
        setColor(ImGuiCol.DragDropTarget, ACCENT);
        setColor(ImGuiCol.NavHighlight, FOCUS);

        applied = true;
    }

    public static boolean isApplied() {
        return applied;
    }

    /** UI Scale captured at the last apply(); lets component helpers scale their own padding tokens. */
    public static float uiScale() {
        return appliedScale;
    }

    public static int standardTableFlags() {
        return ImGuiTableFlags.RowBg
                | ImGuiTableFlags.SizingFixedFit
                | ImGuiTableFlags.ScrollY
                | ImGuiTableFlags.BordersOuter
                | ImGuiTableFlags.BordersInnerV;
    }

    /** Header row whose height every table inherits; HEADER_CELL_PAD_Y is the single knob for header-cell vertical padding. */
    public static void tableHeaderRow() {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers, tableHeaderRowHeight());
    }

    public static float tableHeaderRowHeight() {
        return ImGui.getTextLineHeight() + 2f * HEADER_CELL_PAD_Y * appliedScale;
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

    /** Plain fixed-fit table for label/control forms: no striping or scroll, so an auto-resize modal hugs content. */
    public static boolean beginStandardFormTable(String id, int columnCount) {
        return ImGui.beginTable(id, columnCount, ImGuiTableFlags.SizingFixedFit);
    }

    public static void endStandardFormTable() {
        ImGui.endTable();
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
        renderAlignedHeaderOverlay(label, alignment);
        Fonts.popBold();
    }

    public static void tableHeaderCentered(String label) {
        tableHeader(label, HAlign.CENTER);
    }

    public static void tableHeaderRight(String label) {
        tableHeader(label, HAlign.RIGHT);
    }

    /** Right-aligns a bold header so its right edge sits above the right edge of a centerNextItem'd cell control of width itemWidth (e.g. the Yaw input). */
    public static void tableHeaderRightOverCenteredItem(String label, float itemWidth) {
        Fonts.pushBold();
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float avail = ImGui.getContentRegionAvail().x;
        ImGui.tableHeader("##" + label);
        float textW = ImGui.calcTextSize(label).x;
        float itemRight = (avail + itemWidth) * 0.5f; // right edge of a centered control, relative to the cell's left
        float dx = Math.min(Math.max(0f, itemRight - textW), Math.max(0f, avail - textW));
        drawHeaderText(cellOrigin.x + dx, label);
        Fonts.popBold();
    }

    private static void renderAlignedHeaderOverlay(String label, HAlign alignment) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float avail = ImGui.getContentRegionAvail().x; // reliable cell width inside a table (getColumnWidth is the legacy columns API)
        ImGui.tableHeader("##" + label);
        float textW = ImGui.calcTextSize(label).x;
        float dx;
        if (alignment == HAlign.LEFT) dx = 0f;
        else if (alignment == HAlign.CENTER) dx = (avail - textW) * 0.5f;
        else dx = avail - textW;
        if (dx < 0f) dx = 0f;
        drawHeaderText(cellOrigin.x + dx, label);
    }

    /** Draws bold header text vertically centered in the header cell, so taller header rows keep the label centered. */
    private static void drawHeaderText(float x, String label) {
        ImVec2 rmin = ImGui.getItemRectMin();
        ImVec2 rmax = ImGui.getItemRectMax();
        float ty = rmin.y + (rmax.y - rmin.y - ImGui.getTextLineHeight()) * 0.5f;
        ImGui.getWindowDrawList().addText(x, ty, u32(TABLE_HEADER_TEXT), label);
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
        ImGui.dummy(0f, SECTION_SPACING * appliedScale);
    }

    public static void verticalSpace(float height) {
        ImGui.dummy(0f, height);
    }

    public static void paddedSeparator() {
        sectionSpacing();
        ImGui.separator();
        sectionSpacing();
    }

    public static void bottomPaddedSeparator() {
        ImGui.separator();
        sectionSpacing();
    }

    /**
     * Taller title bar without fattening body widgets: push before begin()/beginPopupModal() (where ImGui sizes the bar)
     * and keep it through the custom title draw, then pop before the body. getFrameHeight() reflects the taller bar in between.
     */
    public static void pushHeaderChrome() {
        ImVec2 fp = ImGui.getStyle().getFramePadding();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, fp.x, fp.y + HEADER_EXTRA_HEIGHT * 0.5f * appliedScale);
    }

    public static void popHeaderChrome() {
        ImGui.popStyleVar(1);
    }

    /** Pads every menu bar entry and dropdown row from one token pair (FramePadding is shared, so it's pushed only around the menus). */
    public static void pushMenuChrome() {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, MENU_PAD_X * appliedScale, MENU_PAD_Y * appliedScale);
    }

    public static void popMenuChrome() {
        ImGui.popStyleVar(1);
    }

    /** Extra vertical breathing room inside each expanded-dropdown row, plus a wide ItemSpacing.x so row highlights reach the popup edge while text keeps its WindowPadding inset (scoped to the popup, not the menu bar; ItemSpacing.y stays put so separators are unaffected). */
    public static void pushMenuPopupChrome() {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, MENU_PAD_X * appliedScale, MENU_POPUP_PAD_Y * appliedScale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, MENU_POPUP_HILITE_SPACING_X * appliedScale, XS * appliedScale);
    }

    public static void popMenuPopupChrome() {
        ImGui.popStyleVar(2);
    }

    /** Makes a menu-bar entry's built-in (text-height) highlight invisible so a full-band one can be drawn in its place. */
    public static void pushTransparentMenuHeader() {
        ImGui.pushStyleColor(ImGuiCol.Header, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0f, 0f, 0f, 0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0f, 0f, 0f, 0f);
    }

    public static void popTransparentMenuHeader() {
        ImGui.popStyleColor(3);
    }

    /** Horizontal inset of title-bar text from the window edge (scaled). */
    public static float headerTextPadX() {
        return HEADER_TEXT_PAD_X * appliedScale;
    }

    /** Draws a bold modal/window title over the (empty) native title-bar strip. */
    public static void drawModalTitle(String title) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 winPos = ImGui.getWindowPos();
        float winW = ImGui.getWindowWidth();
        float titleH = ImGui.getFrameHeight();
        float fontSize = ImGui.getFontSize();
        float y = winPos.y + (titleH - fontSize) * 0.5f;
        dl.pushClipRect(winPos.x, winPos.y, winPos.x + winW, winPos.y + titleH, false);
        Fonts.pushBold();
        dl.addText(winPos.x + headerTextPadX(), y, u32(TEXT), title);
        Fonts.popBold();
        dl.popClipRect();
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
            // Inset from the inner edge by the leftmost-column edge inset so left/right padding stays symmetric.
            float tx = cellOrigin.x + (cellW - textSize.x) - tableEdgeCellInset();
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
        return ImGui.getStyle().getScrollbarSize();
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
        return Math.max(0f, ImGui.getStyle().getScrollbarSize() - ImGui.getStyle().getCellPadding().x);
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

    public static void pushStatusStripChrome(float opacity) {
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, opacity);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, SM, 0f);
    }

    public static void popStatusStripChrome() {
        ImGui.popStyleVar(2);
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
        pushButtonColors(DANGER, DANGER_HOVER, DANGER_ACTIVE, DANGER_BORDER);
    }

    public static void popDangerButton() {
        ImGui.popStyleColor(5);
    }

    public static void pushPrimaryButton() {
        pushButtonColors(ACCENT, ACCENT_HOVER, ACCENT_ACTIVE, ACCENT_BORDER);
    }

    public static void popPrimaryButton() {
        ImGui.popStyleColor(5);
    }

    /** Disabled = muted frame (PANEL fill + BORDER + TEXT_MUTED), not a blanket opacity drop. */
    public static void pushDisabledButton() {
        ImGui.pushStyleColor(ImGuiCol.Button, PANEL[0], PANEL[1], PANEL[2], PANEL[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PANEL[0], PANEL[1], PANEL[2], PANEL[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PANEL[0], PANEL[1], PANEL[2], PANEL[3]);
        ImGui.pushStyleColor(ImGuiCol.Border, BORDER[0], BORDER[1], BORDER[2], BORDER[3]);
        ImGui.pushStyleColor(ImGuiCol.Text, TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2], TEXT_MUTED[3]);
    }

    public static void popDisabledButton() {
        ImGui.popStyleColor(5);
    }

    private static void pushButtonColors(float[] base, float[] hover, float[] active, float[] border) {
        ImGui.pushStyleColor(ImGuiCol.Button, base[0], base[1], base[2], base[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hover[0], hover[1], hover[2], hover[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, active[0], active[1], active[2], active[3]);
        ImGui.pushStyleColor(ImGuiCol.Border, border[0], border[1], border[2], border[3]);
        ImGui.pushStyleColor(ImGuiCol.Text, BG_DARK[0], BG_DARK[1], BG_DARK[2], 1.0f);
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
