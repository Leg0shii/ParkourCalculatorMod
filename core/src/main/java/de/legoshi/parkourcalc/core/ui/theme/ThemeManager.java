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

    // Table tokens. SINGLE SOURCE OF TRUTH for all table styling in the app.
    // After the UI refactor, no table call site outside this file should reference
    // any other color for header background, row stripes, row hover, selected row,
    // cell borders, input focus ring, or populated-input borders. The legacy
    // BG/PANEL pair stays used elsewhere (window backgrounds, scrollbar track,
    // alt-button states) but is no longer the canonical table row stripe.
    //
    // Stripe luminance delta (BASE vs ALT) is held to 3-5% per spec; the previous
    // BG/PANEL pair was at ~150% luminance ratio (way too loud). New pair is
    // narrow enough to read as banding without competing with header / selection.
    //
    // Surface ladder placement (Mocha): base(BG) < TABLE_ROW_ALT < TABLE_ROW_BASE
    // (= surface0) < TABLE_ROW_HOVER < TABLE_HEADER_BG (= surface1) < surface2.
    // Every state has its own tier; no two of {ALT, BASE, HOVER, HEADER} collide.

    // The "normal" data row background. The BRIGHTER of the two zebra stripes.
    // Mocha surface0 (#313244, sRGB relative L = 0.0334).
    // Consumed by: paintTableRowBg(rowIndex) for even rows; pushSelectedFrameBg's
    //              text-flip target color (the dark text rendered atop the mauve
    //              selection fill matches this row's surface).
    private static final float[] TABLE_ROW_BASE         = rgb(0x31, 0x32, 0x44, 1.00f);

    // Alternating zebra stripe. ~4.5% darker than BASE in sRGB relative luminance
    // (BASE L = 0.0334, ALT L = 0.0319, ratio 0.9549). Stays within the 3-5%
    // window the spec mandates. Custom value, not a stock Mocha step (the next
    // stock tier down, base #1e1e2e, would land at ~58% darker, the old too-loud
    // banding the refactor removed).
    // Mocha-derived (#2f3142, sRGB relative L = 0.0319).
    // Consumed by: paintTableRowBg(rowIndex) for odd rows.
    private static final float[] TABLE_ROW_ALT          = rgb(0x2f, 0x31, 0x42, 1.00f);

    // Hover state on clickable rows. Lifted above BASE/ALT, distinctly below
    // HEADER so a hovered row never reads as "promoted to header status."
    // Distinct from SELECTED (mauve) and from any zebra stripe.
    // Mocha-derived (#393b4d, sRGB relative L = 0.0453, ~36% brighter than BASE,
    // ~44% darker than HEADER).
    // Consumed by: beginStandardTable's ImGuiCol.HeaderHovered push for tables
    //              whose Selectable spans the row (currently the Open TAS dialog).
    //              Tables whose rows are NOT click targets (input grid, perf,
    //              tick info) keep Header* transparent and do not surface hover.
    private static final float[] TABLE_ROW_HOVER        = rgb(0x39, 0x3b, 0x4d, 1.00f);

    // Selection fill, drawn as a row-spanning tint. Mocha mauve at 0.75 alpha so
    // it dominates the underlying zebra stripe AND any populated-input border
    // beneath it. SELECTED RGB intentionally reuses the existing palette token so
    // the row tint matches drag-source tint and any other selection signal.
    // Mocha mauve (#cba6f7) @ alpha 0.75.
    // Consumed by: selectedTintColor(0.75f) inside the row-bg painter; flips
    //              FrameBg of input cells in the selected row via pushSelectedFrameBg.
    private static final float[] TABLE_ROW_SELECTED     = rgb(0xcb, 0xa6, 0xf7, 0.75f);

    // Header row background. One tier brighter than BASE on the Mocha ladder
    // (surface1, the same tier as window borders). Distinct from both row
    // stripes; the data area and the header bar read as different planes.
    // Replaces the prior transparent-header look: paintTableHeader() will fill
    // the header row with this color instead of being a no-op.
    //
    // Intentionally EQUAL to TABLE_CELL_BORDER. See note on TABLE_CELL_BORDER
    // for the rationale; do not split these into separate values without
    // changing the header's intended visual treatment.
    //
    // Mocha surface1 (#45475a, sRGB relative L = 0.0651).
    // Consumed by: paintTableHeader() via tableSetBgColor(RowBg0) on the header row.
    private static final float[] TABLE_HEADER_BG        = rgb(0x45, 0x47, 0x5a, 1.00f);

    // Header text color. Body-text white; bold weight (NOT 75% opacity) per the
    // Phase-2 decision. Headers stay bold + opaque atop the painted header bg.
    // Mocha text (#cdd6f4) — same RGB as TEXT; named separately so a future
    // tweak (e.g., a slight desaturation specifically in headers) lives in one
    // place without touching the global TEXT token.
    // Consumed by: tableHeader() and its centered variant, via the Text color
    //              used in the drawList.addText overlay.
    private static final float[] TABLE_HEADER_TEXT      = rgb(0xcd, 0xd6, 0xf4, 1.00f);

    // 1px border between cells when a table opts into BordersInnerV.
    //
    // Intentionally EQUAL to TABLE_HEADER_BG. Cell borders are visible against
    // the data row stripes (BASE / ALT) but VANISH into the header bar,
    // making the header read as one continuous band rather than a grid of
    // header cells. Do not split these into separate values without changing
    // the header's intended visual treatment.
    //
    // Tables that drop BordersInnerV (currently only Tick Info) ignore this
    // token entirely.
    // Mocha surface1 (#45475a).
    // Consumed by: setColor(ImGuiCol.TableBorderStrong / TableBorderLight) in
    //              apply(); not pushed per-table.
    private static final float[] TABLE_CELL_BORDER      = rgb(0x45, 0x47, 0x5a, 1.00f);

    // 2px outline drawn around the active edit target (input field with
    // keyboard focus). Mocha lavender; same RGB as the existing FOCUS token,
    // aliased here so future "table-focus is different from app-focus" tweaks
    // have a hook. Drawn inset of the 1px frame border by Controls.drawFocusRingIfActive.
    // Mocha lavender (#b4befe).
    // Consumed by: Controls.drawFocusRingIfActive (already in place).
    private static final float[] TABLE_FOCUS_RING       = rgb(0xb4, 0xbe, 0xfe, 1.00f);

    // Border around inputs that contain data, lifting them visually above
    // empty placeholder inputs. Mocha surface2 (two tiers above BASE, one tier
    // above the global Border = surface1).
    //
    // NOTE on spec deviation: the Phase-2 spec literally says "one tier brighter
    // than TABLE_ROW_BASE", which would land on surface1 (#45475a). That value
    // equals the global Border (and TABLE_CELL_BORDER, and TABLE_HEADER_BG)
    // already in use, so populated and empty input borders would become
    // visually identical, defeating the populated/empty distinction the
    // pushPopulatedFrameBorder helper exists to draw. Picking surface2 keeps
    // the existing populated-vs-empty step intact. Flag if the spec genuinely
    // intends to collapse this distinction.
    // Mocha surface2 (#58, 0x5b, 0x70).
    // Consumed by: pushPopulatedFrameBorder() (used by InputOverlay yaw input).
    private static final float[] TABLE_POPULATED_BORDER = rgb(0x58, 0x5b, 0x70, 1.00f);

    private static final float XXS = 2.0f;
    private static final float XS = 4.0f;
    private static final float SM = 8.0f;
    private static final float MD = 12.0f;
    private static final float LG = 16.0f;

    // Scrollbar bumped above MD; user-facing chunkier grab.
    private static final float SCROLLBAR_SIZE = 18.0f;

    /** Horizontal alignment for header and data cells, consumed by
     *  {@link #tableHeader(String, HAlign)} and the text* alignment helpers. */
    public enum HAlign { LEFT, CENTER, RIGHT }

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
        // Note: NOT setting ImGuiCol.TableRowBg / TableRowBgAlt. imgui-java's
        // ImGuiCol constants are compile-time inlined ints from core's 1.86.11
        // dependency, but Fabric runs against 1.90.0 where the enum slot numbers
        // shifted (Tab/Docking entries inserted). Setting RowBg / RowBgAlt by
        // enum lands on TableBorderStrong / TableBorderLight at runtime on 1.90
        // and corrupts borders. All tables paint their own row backgrounds via
        // tableSetBgColor + the accessors below.
        // TableHeaderBg is safe: slot 44 in 1.86 (TableHeaderBg) but slot 44 in
        // 1.90 is PlotHistogram, which we never render. On Fabric the header bg
        // is set indirectly via the TableBorderLight write below, which lands on
        // 1.90's TableHeaderBg slot. Explicit set here makes Forge match.
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

    /** Standard flags for any tabular data view in the app. Borderless, alt-row
     *  banded, vertically scrollable, content-sized columns.
     *
     *  CONTRACT: every table using these flags MUST also:
     *  <ul>
     *    <li>size every fixed-width middle column via {@link #tableColumnWidth} so
     *        bold headers never clip;</li>
     *    <li>size the leftmost column via {@link #tableLeftmostColumnWidth} and call
     *        {@link #tableLeftmostCellPad} at the start of every leftmost cell
     *        (header + each data row) so the left gutter equals SCROLLBAR_SIZE;</li>
     *    <li>size the rightmost column via {@link #tableRightmostColumnWidth} and
     *        call {@link #tableRightmostCellTrailingPad} at the end of every
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

    // Standard table begin/end. Single entry point for every table in the app.
    // Pushes the four selection-chrome colors (Header / HeaderHovered /
    // HeaderActive / DragDropTarget) so a Selectable inside a row does not
    // double-paint over the table's own row-tint signal. endStandardTable()
    // pops exactly those four; pairing is mandatory.
    //
    // Row backgrounds (TABLE_ROW_BASE / TABLE_ROW_ALT) are NOT pushed here:
    // the underlying ImGuiCol.TableRowBg / TableRowBgAlt enum slots drift
    // between imgui-java 1.86.11 (core compile-time) and 1.90.0 (Fabric
    // runtime) and corrupt borders if set by enum. Tables still call
    // {@link #paintTableRowBg(int)} per row; that helper now sources its
    // colors from the new tokens.
    //
    // Header background (TABLE_HEADER_BG) is painted by
    // {@link #paintTableHeader()} via tableSetBgColor on the header row,
    // for the same cross-version reason. Cell borders (TABLE_CELL_BORDER)
    // come from the global setColor in apply() and need no per-table push.

    /** Begin a standard read-only data table. Rows are not click targets
     *  (no hover signal). Header / Selectable selection chrome is suppressed
     *  so the table's own row tint owns the visual. */
    public static boolean beginStandardTable(String id, int columnCount) {
        return beginStandardTable(id, columnCount, 0, 0f, 0f);
    }

    /** Begin a standard read-only data table with extra ImGuiTableFlags and
     *  explicit outer width / height. */
    public static boolean beginStandardTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(false);
        return ImGui.beginTable(id, columnCount, standardTableFlags() | extraFlags, outerWidth, outerHeight);
    }

    /** Begin a table whose rows themselves are the click target (file picker,
     *  list-select). Lifts each row to {@link #TABLE_ROW_HOVER} on mouse-over
     *  so users see which row they are about to click. The {@code Header} slot
     *  carries the SELECTED mauve so the Selectable paints selection in the
     *  same rect it paints hover, keeping the two states coextensive (don't
     *  paint a separate row-spanning selection tint, it leaks into the cell
     *  padding bands and hover can't fully overdraw it). */
    public static boolean beginStandardClickableRowsTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(true);
        return ImGui.beginTable(id, columnCount, standardTableFlags() | extraFlags, outerWidth, outerHeight);
    }

    /** Begin a key-value readout table. Identical to
     *  {@link #beginStandardTable} except {@code BordersInnerV} is dropped:
     *  key-value lists' column splits are internal scaffolding, not
     *  user-visible structure, so vertical separators would imply a grouping
     *  that is not there. */
    public static boolean beginStandardKeyValueTable(String id, int columnCount, int extraFlags, float outerWidth, float outerHeight) {
        pushTableSelectionChrome(false);
        int flags = (standardTableFlags() & ~ImGuiTableFlags.BordersInnerV) | extraFlags;
        return ImGui.beginTable(id, columnCount, flags, outerWidth, outerHeight);
    }

    /** Pop the selection-chrome colors pushed by every {@code beginStandard*}
     *  variant and close the table. MUST be paired with exactly one
     *  {@code beginStandard*} call. */
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

    /** Bold table header, left-aligned. Equivalent to
     *  {@code tableHeader(label, HAlign.LEFT)}. */
    public static void tableHeader(String label) {
        tableHeader(label, HAlign.LEFT);
    }

    /** Bold table header with explicit alignment. LEFT uses
     *  {@link ImGui#tableHeader(String)} natively; CENTER and RIGHT hide the
     *  native label (empty visible part) and overlay the bold text manually,
     *  matching the native Y. The native tableHeader call always runs so it
     *  remains the last item — {@code isItemHovered()} in the caller still
     *  drives tooltips regardless of alignment. */
    public static void tableHeader(String label, HAlign alignment) {
        Fonts.pushBold();
        if (alignment == HAlign.LEFT) {
            ImGui.tableHeader(label);
        } else {
            renderAlignedHeaderOverlay(label, alignment);
        }
        Fonts.popBold();
    }

    /** Sugar for {@code tableHeader(label, HAlign.CENTER)}. */
    public static void tableHeaderCentered(String label) {
        tableHeader(label, HAlign.CENTER);
    }

    /** Sugar for {@code tableHeader(label, HAlign.RIGHT)}. */
    public static void tableHeaderRight(String label) {
        tableHeader(label, HAlign.RIGHT);
    }

    // Caller must have pushed bold font already.
    private static void renderAlignedHeaderOverlay(String label, HAlign alignment) {
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        float colW = ImGui.getColumnWidth();
        float cellPad = ImGui.getStyle().getCellPadding().x;
        ImGui.tableHeader("##" + label);
        ImVec2 textSize = ImGui.calcTextSize(label);
        // colW includes both cellPaddings. cursor sits at content start (left
        // cellPad already consumed). To put text geometric center at column
        // geometric center: offset = (colW - textW)/2 - cellPad. For RIGHT,
        // align text's right edge with content area's right edge:
        // offset = colW - textW - 2*cellPad.
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

    // Per-table alignment policy (canonical reference). Each data table in the
    // app uses one of LEFT / CENTER / RIGHT per column based on the column's
    // content semantics. Rules are NOT uniform across tables; the convention
    // below captures the rationale per call site so future readers do not have
    // to re-derive it.
    //
    //  PerfOverlay (read-only metrics):
    //    Section header + data        LEFT     text column, reads left-to-right
    //    last/ema/max us + n/frame    RIGHT    numeric columns, digit-right-aligned
    //                                          so the ones place stays in a fixed x
    //
    //  TickInfoPanel (status readout):
    //    Field column (labels)        LEFT     forms a clean vertical scan line down
    //                                          the left edge; centered labels jitter
    //                                          horizontally as label lengths vary
    //    Triplet rows (X/Y/Z values)  CENTER   each value in its own X / Y / Z column,
    //                                          dot-aligned across rows via "%12.5f"
    //                                          fixed-width formatting
    //    Single-value rows            CENTER   value rendered in the Y (middle) value
    //                                          column, X and Z left empty. Y's geometric
    //                                          center approximates the X+Y+Z geometric
    //                                          center, visually associating the value
    //                                          with the row rather than anchoring it
    //                                          to the leftmost column. Earlier attempts
    //                                          to span the text across X+Y+Z via
    //                                          drawList or pushClipRect failed against
    //                                          ImGui's per-cell clip rect (pushed on
    //                                          every tableNextColumn, independent of
    //                                          BordersInnerV).
    //
    //  FileMenu Open dialog (file picker):
    //    All columns headers + data   LEFT     all four columns (Filename, Date, MC,
    //                                          World) are text columns; dates render
    //                                          as text not numbers, version strings
    //                                          like "1.21.10" are not numeric
    //
    //  InputOverlay (TAS editing grid):
    //    All headers                  CENTER   labels acting as captions above
    //                                          narrow flag/toggle cells; centering
    //                                          reads cleanly under single-glyph data
    //    Tick column (row numbers)    RIGHT    numeric column, ones-place stays in
    //                                          a fixed x across mixed-digit counts
    //                                          (1, 10, 100, 1000). Header stays CENTER
    //                                          (the slight header-vs-data alignment
    //                                          mismatch is conventional for numeric
    //                                          columns and reads more naturally than
    //                                          right-aligned narrow header above a
    //                                          right-aligned data column)
    //    W / A / S / D / Spr/Snk/Spc  CENTER   single bold glyph or short label;
    //                                          centering reads correctly under
    //                                          centered headers
    //    Yaw                          CENTER   InputText widget centered in the cell
    //                                          via centerNextItem. Per-row decimal
    //                                          alignment achieved via the format
    //                                          string "% 10.6f" (leading space flag
    //                                          reserves a sign-position column,
    //                                          fixed-width formatting puts decimals
    //                                          in a single vertical column). NOT via
    //                                          widget-level right-alignment: ImGui
    //                                          InputText does not support per-text
    //                                          alignment, so format-string padding
    //                                          achieves the same visual outcome with
    //                                          no new helper. Edit format switches to
    //                                          unpadded "%.6f" while the cell has
    //                                          keyboard focus (tracked via
    //                                          ImGui.isItemActivated / Deactivated)
    //                                          so users type into a normal-looking
    //                                          buffer
    //    Speed / Jump Boost           CENTER   combo widget centered via centerNextItem
    //
    // Helper choices that fall out of this:
    //   tableHeader(label) / tableHeader(label, HAlign.LEFT)         text headers
    //   tableHeaderCentered(label)                                   single-glyph or
    //                                                                short-label headers
    //   tableHeaderRight(label)                                      numeric headers
    //   textLeft(text)                                               text data cells
    //   textCenter(text)                                             centered data
    //                                                                (within a single cell)
    //   textRight(text)                                              right-aligned data
    //   centeredSelectable(...)                                      span-all-columns
    //                                                                row selectable with
    //                                                                centered label
    //   rightAlignedSelectable(...)                                  span-all-columns
    //                                                                row selectable with
    //                                                                right-aligned label

    /** Data-cell text, left-aligned. Equivalent to a vertically-padded
     *  {@code ImGui.text}. Use inside a TableNextColumn / TableSetColumnIndex. */
    public static void textLeft(String text) {
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    /** Data-cell text, horizontally centered within the column's geometric
     *  bounds (NOT the content-region rect, which gets clipped in narrow
     *  columns and shifts the visible glyph off-center). Use inside a
     *  TableNextColumn / TableSetColumnIndex.
     *
     *  Math: getColumnWidth() returns full column width (both cellPads
     *  included). Cursor sits at content-area start, so the offset that
     *  places the text's geometric center on the column's geometric center
     *  is {@code (colW - textW) / 2 - cellPadX}. */
    public static void textCenter(String text) {
        float colW = ImGui.getColumnWidth();
        float textW = ImGui.calcTextSize(text).x;
        float cellPad = ImGui.getStyle().getCellPadding().x;
        float offset = (colW - textW) * 0.5f - cellPad;
        if (offset > 0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
    }

    /** Data-cell text, right-aligned to the column's content-area right edge.
     *  Use inside a TableNextColumn / TableSetColumnIndex. */
    public static void textRight(String text) {
        float avail = ImGui.getContentRegionAvail().x;
        float textW = ImGui.calcTextSize(text).x;
        if (avail > textW) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - textW);
        }
        ImGui.alignTextToFramePadding();
        ImGui.text(text);
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

    /** Selectable whose visible label is drawn flush-right within the cell's
     *  content area, inset by cellPaddingX so the rightmost glyph does not
     *  kiss the cell border. Structure parallels {@link #centeredSelectable}:
     *  click region is determined by {@code flags} (pass
     *  ImGuiSelectableFlags.SpanAllColumns to keep row-wide hit testing) and
     *  is independent of where the visible label renders.
     *
     *  Use for numeric columns where consistent ones-place alignment matters:
     *  tick numbers 1, 10, 100, 1000 all end at the same x and read as
     *  vertically aligned even though their widths differ. */
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

    /** Width to pass to tableSetupColumn for the RIGHTMOST column.
     *  Formula: {@code max(boldHeader, dataWidth) + 2*cellPadX + scrollbarSlack}.
     *  Callers pass {@link #tableScrollbarSlack()} when the table has ScrollY
     *  enabled (the standard case); pass 0 only if the table is non-scrolling
     *  AND there is no vertical scrollbar to clear. */
    public static float tableRightmostColumnWidth(String headerLabel, float dataWidth, float scrollbarSlack) {
        float content = Math.max(boldTextWidth(headerLabel), dataWidth);
        float cellPad = ImGui.getStyle().getCellPadding().x;
        return content + 2f * cellPad + scrollbarSlack;
    }

    /** Width to pass to tableSetupColumn for a MIDDLE numeric column.
     *  Formula: {@code max(boldHeader, dataWidth) + 2*cellPadX}. Identical to
     *  {@link #tableRightmostColumnWidth} minus the scrollbar slack term,
     *  which only applies to the rightmost column. {@link #tableColumnWidth}
     *  omits the {@code 2*cellPadX} term and is kept for the existing narrow
     *  flag-column callers in InputOverlay where content is one bold glyph
     *  and the extra padding would visibly inflate the column; new numeric
     *  middle columns should prefer this helper. */
    public static float tableNumericColumnWidth(String headerLabel, float dataWidth) {
        float content = Math.max(boldTextWidth(headerLabel), dataWidth);
        float cellPad = ImGui.getStyle().getCellPadding().x;
        return content + 2f * cellPad;
    }

    /** Trailing reservation a rightmost column needs so its content does not
     *  clip into the vertical scrollbar. Equals SCROLLBAR_SIZE. */
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

    /** Call inside the leftmost cell of every row (header + data), immediately
     *  after positioning on column 0 and BEFORE rendering the cell content.
     *  Lands the visible content at SCROLLBAR_SIZE from the panel inner edge. */
    public static void tableLeftmostCellPad() {
        emitEdgeDummy(true);
    }

    /** Call inside the rightmost cell of every row (header + data), AFTER the
     *  cell's content is rendered. Mirrors the leftmost gutter so the table
     *  reads visually centered in its panel. */
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

    // Table row painters. Centralized here because ImGuiCol.TableRowBg/TableRowBgAlt/
    // TableHeaderBg can't be set via setColor: their compile-time int constants from
    // imgui-java 1.86.11 land on wrong slots in Fabric's 1.90.0 runtime. tableSetBgColor's
    // target enum (RowBg0/RowBg1) is stable across versions, so paint per row instead.
    // All tables MUST call these helpers; do not call tableSetBgColor inline.

    /** Header row background. Call immediately after tableNextRow(Headers).
     *  Paints TABLE_HEADER_BG over the entire header row via tableSetBgColor,
     *  which is the only cross-version-safe mechanism (ImGuiCol slot indices
     *  for header bg drift between imgui-java 1.86.11 and 1.90.0). */
    public static void paintTableHeader() {
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, u32(TABLE_HEADER_BG));
    }

    /** Alt-row banding. Call once per data row, after tableNextRow(...).
     *  Even rows take TABLE_ROW_BASE (brighter), odd rows take TABLE_ROW_ALT
     *  (slightly darker, ~4.5% luminance delta). */
    public static void paintTableRowBg(int rowIndex) {
        int bg = (rowIndex & 1) == 0 ? u32(TABLE_ROW_BASE) : u32(TABLE_ROW_ALT);
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, bg);
    }

    /** Optional tint layer on top of the banding (selection, playback marker, drag
     *  source). Pass a packed u32 color; pass 0 for no tint. */
    public static void paintTableRowTint(int tint) {
        if (tint != 0) ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg1, tint);
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

    /** Color for table-cell text. {@code rowIsSelected} indicates whether the
     *  current row has a selection tint painted beneath; today both states
     *  return the body-text color, but routing through this helper sets up
     *  a future selected-row text-color flip without touching call sites. */
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
