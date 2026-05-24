package de.legoshi.parkourcalc.core.ui.theme;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableFlags;

/**
 * Central source for UI chrome colors and spacing. See docs/UI_REDESIGN.md
 * for the token table and the Visual quality contract. apply() is idempotent
 * and may be called any number of times after ImGui context creation.
 *
 * Palette: Catppuccin Mocha (https://catppuccin.com/palette/). Picked for its
 * subtle surface steps (~6% lift between base and surface0 for alt-row banding),
 * legible muted text, and pastel accents that don't fight the data.
 */
public final class ThemeManager {

    // Tokens. Each token has one semantic job; do not reuse one for two states.
    // Surface ladder (Catppuccin Mocha): base -> surface0 -> surface1 -> surface2 -> overlay0.
    // Luminance encodes interactivity. Inputs MUST sit one tier above alt-row
    // banding so their silhouette is preserved regardless of which zebra band
    // they land on; scrollbar grab sits two tiers above so chrome reads as
    // distinct from any data row.
    //   BG           window background, dark zebra band, scrollbar track (Mocha base)
    //   BG_DARK      title bar, popup background (Mocha crust)
    //   PANEL        alt-row banding ONLY (Mocha surface0). Do not reuse for input fills.
    //   PANEL_HOVER  input/slider/checkbox/button fill at rest (Mocha surface1)
    //   PANEL_FOCUS  focused (typing) input fill, between PANEL_HOVER and PANEL_ACTIVE
    //   PANEL_ACTIVE input hover fill, scrollbar grab at rest (Mocha surface2)
    //   OVERLAY0     scrollbar grab hover/active (Mocha overlay0)
    //   BORDER       panel borders, separators, 1px frame outline at rest (Mocha surface1)
    //   TEXT         primary body (Mocha text)
    //   TEXT_MUTED   secondary, disabled labels (Mocha subtext0)
    //   TEXT_DIM     very-low-emphasis text (Mocha overlay0). Same RGB as OVERLAY0 but text-semantic.
    //   ACCENT       primary buttons, slider grab, separators, drag-drop, nav (Mocha blue)
    //   ACCENT_DIM   slider grab inactive, drag-source row tint (Mocha blue @ 0.30)
    //   SELECTED     selected-row tint (Mocha mauve, distinct from WARNING)
    //   WARNING      dirty marker, drop indicator, playback tick (Mocha yellow)
    //   DANGER       destructive actions, error text (Mocha red)
    //   OK           success toast/status text (Mocha green)
    //   FOCUS        2px outline on focused inputs, drawn inset of the 1px frame border (Mocha lavender)
    //   STATUS_BG    background for the status-message panel at the bottom of MainWindowOverlay
    //                (~midpoint of BG and BG_DARK so the zone is discoverable when empty)
    // BG must be fully opaque or the in-game terrain/sky bleeds through small
    // auto-resize windows (Perf, TickInfo) and tints them light. The input table
    // in the large MainWindow rarely shows it because its surface covers most of
    // the viewport, but small floating panels become unreadable on a bright world.
    private static final float[] BG           = rgb(0x1e, 0x1e, 0x2e, 1.00f);
    private static final float[] BG_DARK      = rgb(0x11, 0x11, 0x1b, 1.00f);
    // Menu bar sits one tier brighter than the title bar so the chrome reads as
    // a layered stack: title (crust) -> menu (mantle) -> body (base).
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

    private static final float XXS = 2.0f;
    private static final float XS = 4.0f;
    private static final float SM = 8.0f;
    private static final float MD = 12.0f;
    private static final float LG = 16.0f;

    // Scrollbar bumped above MD; user-facing chunkier grab.
    private static final float SCROLLBAR_SIZE = 18.0f;

    private static boolean applied;

    private ThemeManager() {}

    public static void apply() {
        // Force a known baseline before our overrides. Without this, unset slots
        // inherit from whichever default the underlying imgui-java binding chose,
        // and Forge (1.86.11 + lwjgl2 shim) vs Fabric (1.90.0 + lwjgl3) end up with
        // visibly different colors on the slots we don't explicitly override.
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
        // PopupBg matches WindowBg so tables look identical whether they live in a
        // main window or inside a modal popup. Modals still pop visually via the
        // BG_DARK title bar and the 1px window border.
        setColor(ImGuiCol.PopupBg, BG);
        setColor(ImGuiCol.Border, BORDER);
        // Inputs sit one tier above alt-row banding so their silhouette is
        // preserved regardless of which zebra band they land on. Active (focused)
        // lifts to PANEL_FOCUS and is doubly marked by the 2px lavender ring drawn
        // inset of the 1px frame border (see Controls.drawFocusRingIfActive).
        setColor(ImGuiCol.FrameBg, PANEL_HOVER);
        setColor(ImGuiCol.FrameBgHovered, PANEL_ACTIVE);
        setColor(ImGuiCol.FrameBgActive, PANEL_FOCUS);
        setColor(ImGuiCol.TitleBg, BG_DARK);
        setColor(ImGuiCol.TitleBgActive, BG_DARK);
        setColor(ImGuiCol.TitleBgCollapsed, BG_DARK);
        setColor(ImGuiCol.MenuBarBg, BG_MENU);
        // Scrollbar track sits at BG (= dark zebra) so the chrome recedes into
        // the panel. Grab sits two tiers above (surface2) so it stands alone in
        // its luminance band, making it visually obvious as the draggable handle.
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
        // Note: NOT setting ImGuiCol.TableHeaderBg / TableRowBg / TableRowBgAlt.
        // imgui-java's ImGuiCol constants are compile-time inlined ints from core's
        // 1.86.11 dependency, but Fabric runs against 1.90.0 where the enum slot
        // numbers shifted (Tab variants and Multi-Select were inserted). Setting
        // them by enum lands on wrong slots at runtime. All tables paint their
        // own row backgrounds via tableSetBgColor + the accessors below.
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

    /** Standard flags for any tabular data view in the app. Borderless, alt-row
     *  banded, vertically scrollable, content-sized columns.
     *
     *  CONTRACT: every table using these flags MUST also:
     *  <ul>
     *    <li>size every fixed-width middle column via {@link #tableColumnWidth} so
     *        bold headers never clip;</li>
     *    <li>size the leftmost column via {@link #tableLeftmostColumnWidth} and call
     *        {@link #emitTableLeftmostCellPad} at the start of every leftmost cell
     *        (header + each data row) so the left gutter equals SCROLLBAR_SIZE;</li>
     *    <li>size the rightmost column via {@link #tableRightmostColumnWidth} and
     *        call {@link #emitTableRightmostCellTrailingPad} at the end of every
     *        rightmost cell so the right gutter mirrors the left;</li>
     *    <li>render header labels through {@link #tableHeader} (bold).</li>
     *  </ul>
     */
    public static int standardTableFlags() {
        return ImGuiTableFlags.RowBg
                | ImGuiTableFlags.SizingFixedFit
                | ImGuiTableFlags.ScrollY
                | ImGuiTableFlags.BordersInnerV;
    }

    /** Bold table header. Use in place of {@link ImGui#tableHeader(String)} so
     *  every standard table has the same heavy header weight. */
    public static void tableHeader(String label) {
        Fonts.pushBold();
        ImGui.tableHeader(label);
        Fonts.popBold();
    }

    /** Bold table header, horizontally centered. {@link ImGui#tableHeader(String)}
     *  writes the label at its column's origin X regardless of cursor, so we hide
     *  the native label (empty visible part) and overlay the bold text manually,
     *  matching the native Y. Last item remains the tableHeader so isItemHovered()
     *  in the caller still drives tooltips. Use only on tables that opt in to
     *  centered cell content (currently the TAS input table). */
    public static void tableHeaderCentered(String label) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        ImGui.tableHeader("##" + label);
        Fonts.pushBold();
        ImVec2 textSize = ImGui.calcTextSize(label);
        float tx = cellOrigin.x + (cellW - textSize.x) * 0.5f;
        float ty = cellOrigin.y;
        ImGui.getWindowDrawList().addText(tx, ty, ImGui.getColorU32(ImGuiCol.Text), label);
        Fonts.popBold();
    }

    /** Position cursor X so an item of the given width centers within the
     *  remaining cell space. Call before submitting a non-selectable widget
     *  (inputText, combo, text) you want centered. */
    public static void centerNextItem(float itemWidth) {
        float avail = ImGui.getContentRegionAvail().x;
        if (itemWidth > 0f && itemWidth < avail) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - itemWidth) * 0.5f);
        }
    }

    /** Render plain text centered in the current cell. Vertically aligned with
     *  frame padding so the line lines up with neighbor widgets in the row. */
    public static void textCentered(String label) {
        centerNextItem(ImGui.calcTextSize(label).x);
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
    }

    /** One vertical gap between logical sections inside a pane or modal. Use this
     *  between groups of related widgets (e.g. input row -> info text -> button row)
     *  so every popup gets consistent breathing room. Retune in one place. */
    public static void sectionSpacing() {
        ImGui.spacing();
    }

    /** Render a selectable whose visible label is drawn centered within the cell.
     *  Pass {@code idSuffix} for the ImGui ID (no leading "##"); pass {@code label}
     *  separately as the text to display. SelectableTextAlign would have done this
     *  via a style var, but its enum index drifts between imgui-java 1.86.11 (core
     *  compile) and 1.90.0 (Fabric runtime) and lands on a float-typed slot at
     *  runtime, so we overlay the label manually with drawList.addText using only
     *  enum-stable APIs. alignTextToFramePadding before the selectable keeps the
     *  click rect (and therefore V positioning) identical to a plain selectable. */
    public static boolean centeredSelectable(String idSuffix, String label, boolean selected, int flags) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float cellW = ImGui.getContentRegionAvail().x;
        ImGui.alignTextToFramePadding();
        boolean clicked = ImGui.selectable("##" + idSuffix, selected, flags);
        if (label != null && !label.isEmpty()) {
            ImVec2 textSize = ImGui.calcTextSize(label);
            float tx = cellOrigin.x + (cellW - textSize.x) * 0.5f;
            // Match the Y position ImGui's selectable would use for its own label
            // after alignTextToFramePadding: cellOrigin.y + framePadding.y.
            float ty = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
            ImGui.getWindowDrawList().addText(tx, ty, ImGui.getColorU32(ImGuiCol.Text), label);
        }
        return clicked;
    }

    public static boolean centeredSelectable(String idSuffix, String label, boolean selected) {
        return centeredSelectable(idSuffix, label, selected, 0);
    }

    /** Width to pass to tableSetupColumn for a MIDDLE column. Uses the max of
     *  the bold-rendered header label width and the caller-specified data width.
     *  Required because tableSetupColumn auto-sizes with the DEFAULT font, but
     *  {@link #tableHeader} renders bold — auto-sized columns clip headers. */
    public static float tableColumnWidth(String headerLabel, float dataWidth) {
        return Math.max(boldTextWidth(headerLabel), dataWidth);
    }

    /** Width to pass to tableSetupColumn for the LEFTMOST column. Adds the
     *  leading inset on top of max(header, data). */
    public static float tableLeftmostColumnWidth(String headerLabel, float dataWidth) {
        return tableColumnWidth(headerLabel, dataWidth) + tableEdgeCellInset();
    }

    /** Width to pass to tableSetupColumn for the RIGHTMOST column. Adds the
     *  trailing inset so the right gutter (cellPad + inset) equals the left. */
    public static float tableRightmostColumnWidth(String headerLabel, float dataWidth) {
        return tableColumnWidth(headerLabel, dataWidth) + tableEdgeCellInset();
    }

    private static float boldTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;
        Fonts.pushBold();
        float w = ImGui.calcTextSize(text).x;
        Fonts.popBold();
        return w;
    }

    /** Call inside the leftmost cell of every row (header + data), immediately
     *  after positioning on column 0 and BEFORE rendering the cell content.
     *  Lands the visible content at SCROLLBAR_SIZE from the panel inner edge. */
    public static void emitTableLeftmostCellPad() {
        emitEdgeDummy(true);
    }

    /** Call inside the rightmost cell of every row (header + data), AFTER the
     *  cell's content is rendered. Mirrors the leftmost gutter so the table
     *  reads visually centered in its panel. */
    public static void emitTableRightmostCellTrailingPad() {
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

    // Table row painters. Centralized here because ImGuiCol.TableRowBg/TableRowBgAlt/
    // TableHeaderBg can't be set via setColor: their compile-time int constants from
    // imgui-java 1.86.11 land on wrong slots in Fabric's 1.90.0 runtime. tableSetBgColor's
    // target enum (RowBg0/RowBg1) is stable across versions, so paint per row instead.
    // All tables MUST call these helpers; do not call tableSetBgColor inline.

    /** Header row background. Call immediately after tableNextRow(Headers). Currently
     *  no-op (transparent header, per UI_REDESIGN.md "Tables"). Change here to restyle
     *  every table header in the app. */
    public static void paintTableHeader() {
        // intentionally empty: transparent header pattern
    }

    /** Alt-row banding. Call once per data row, after tableNextRow(...). */
    public static void paintTableRowBg(int rowIndex) {
        int bg = (rowIndex & 1) == 0 ? u32(BG) : u32(PANEL);
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, bg);
    }

    /** Optional tint layer on top of the banding (selection, playback marker, drag
     *  source). Pass a packed u32 color; pass 0 for no tint. */
    public static void paintTableRowTint(int tint) {
        if (tint != 0) ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg1, tint);
    }

    public static void pushTransparentHeader() {
        // The selectable Header tint covers row text on the input grid; keep all three
        // states transparent (Header / HeaderHovered / HeaderActive) so the selection
        // background drawn by setRowBackground is the only visual signal.
        ImGui.pushStyleColor(ImGuiCol.Header, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static void popTransparentHeader() {
        ImGui.popStyleColor(4);
    }

    // Tints the input frame to the selection hue so the purple row band reads
    // continuous through input cells. Alpha high enough that the mauve dominates
    // over the opaque PANEL_HOVER/PANEL_FOCUS frame fills; lower alpha left a
    // visible seam where the input composited darker than the row band. Text
    // flips to BG (Mocha base) so digits stay readable on the light mauve fill.
    public static void pushSelectedFrameBg() {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, SELECTED[0], SELECTED[1], SELECTED[2], 0.70f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, SELECTED[0], SELECTED[1], SELECTED[2], 0.80f);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, SELECTED[0], SELECTED[1], SELECTED[2], 0.80f);
        ImGui.pushStyleColor(ImGuiCol.Text, BG[0], BG[1], BG[2], BG[3]);
    }

    public static void popSelectedFrameBg() {
        ImGui.popStyleColor(4);
    }

    // Lifts the 1px frame border one tier (BORDER -> PANEL_ACTIVE) so populated
    // yaw cells read with more weight than empty ones. PANEL_HOVER shares RGB
    // with BORDER in this palette, so PANEL_ACTIVE is the next visible step.
    // Combine with the selected-row push: selected outermost, populated inner,
    // pop in reverse.
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

    public static int textMutedColor() {
        return u32(TEXT_MUTED);
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

    /** Warning tint with custom alpha, for row backgrounds and similar overlays. */
    public static int warningTintColor(float alpha) {
        return ImGui.colorConvertFloat4ToU32(WARNING[0], WARNING[1], WARNING[2], alpha);
    }

    /** Selection tint with custom alpha, for selected row backgrounds. */
    public static int selectedTintColor(float alpha) {
        return ImGui.colorConvertFloat4ToU32(SELECTED[0], SELECTED[1], SELECTED[2], alpha);
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
        // Text on top of the bright accent fill needs a dark color for legibility.
        // BG_DARK (Mocha mantle) is dark enough to read against the pastel blue.
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
