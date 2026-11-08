package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.BlockOverrideWorld;
import de.legoshi.parkourcalc.core.BlockPlacing;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.ui.anglesolver.SolverWidgets;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiComboFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public final class BlockOverridesWindow implements RenderInterface {

    private static final String WINDOW_ID = "###blockOverrides";
    private static final String TITLE = "Block Overrides";
    private static final String TABLE_ID = "##blockOverrides";
    private static final String PICKER_ID = "##blockPicker";
    private static final float WIN_W = 760f;
    private static final float WIN_H = 300f;
    private static final float MIN_W = 600f;
    private static final float MIN_H = 160f;
    private static final float TICK_W = 62f;
    private static final float POS_W = 154f;
    private static final float PICKER_W = 300f;
    private static final float PICKER_PAD = 8f;
    private static final float STATE_LABEL_GAP = 8f;
    private static final float COMBO_PAD_X = 8f;
    private static final float COMBO_PAD_Y = 4f;
    private static final float STATE_PAD_X = 4f;
    private static final float STATE_PAD_TOP = 2f;
    private static final float TIP_W = 420f;
    private static final float TIP_PAD_X = 12f;
    private static final float TIP_PAD_Y = 10f;
    private static final float TIP_GAP = 4f;
    private static final float PICKER_LABEL_W = 70f;
    private static final float[] POPUP_BG = {0x18 / 255f, 0x18 / 255f, 0x25 / 255f};
    private static final String BEFORE_NOTE = "(the world block when the row was made)";
    private static final float[] LIST_BG = {0x1e / 255f, 0x1e / 255f, 0x2e / 255f};
    private static final int PICKER_ROWS = 5;
    private static final int PICKER_MAX_IDS = 200;
    private static final int POS_CHARS = 40;
    private static final int TICK_CHARS = 8;
    private static final int SEARCH_CHARS = 64;
    private static final String END_HINT = "stays";
    private static final String[] HEADERS = {"Start", "End", "Position", "Block", "Placed by", ""};
    private static final String[] PLACED_BY = {"Auto", "Player"};
    private static final String[] PLACED_BY_TIPS = {"The replay places it", "The TAS player places it"};
    private static final String HELP = "Place block: aim at a face and click. Change block: aim at a block and click.\n"
            + "Auto rows the replay places. Player rows the TAS player places with a right click.";
    private static final String SINGLEPLAYER_ONLY = "Singleplayer only";

    private final InputData inputData;
    private final MinecraftAccess mc;
    private final Settings settings;
    private final BlockOverrideWorld world;
    private final BlockPlacing placing;
    private final Runnable onSettingsChanged;
    private final IntConsumer onChanged;
    private final ImBoolean open = new ImBoolean(false);
    private final ImString search = new ImString(SEARCH_CHARS);
    private final List<ImString> posBuffers = new ArrayList<ImString>();
    private final List<ImString> endBuffers = new ArrayList<ImString>();
    private final List<ImString> startBuffers = new ArrayList<ImString>();
    private int editingRow = -1;
    private int editingField = -1;
    private int pickerRow = -1;
    private float pickerX;
    private float pickerY;

    public BlockOverridesWindow(InputData inputData, MinecraftAccess mc, Settings settings, BlockOverrideWorld world,
                                BlockPlacing placing, Runnable onSettingsChanged, IntConsumer onChanged) {
        this.inputData = inputData;
        this.mc = mc;
        this.settings = settings;
        this.world = world;
        this.placing = placing;
        this.onSettingsChanged = onSettingsChanged;
        this.onChanged = onChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewBlockOverrides);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, open, flags);
        if (visible) ThemeManager.drawModalTitle(TITLE);
        ThemeManager.popHeaderChrome();
        if (visible) body();
        ImGui.end();
        if (settings.viewBlockOverrides != open.get()) {
            settings.viewBlockOverrides = open.get();
            onSettingsChanged.run();
        }
    }

    private void body() {
        BlockOverrides blocks = inputData.getBlockOverrides();
        boolean available = world.isAvailable();
        if (available) {
            if (Controls.primaryButton("Place block")) placing.startPlace();
            TooltipUtil.onHover("Aim at a face and click. Uses the block in your hand.");
        } else {
            Controls.disabledButton("Place block");
        }
        ImGui.sameLine();
        if (available) {
            if (Controls.secondaryButton("Change block")) placing.startChange();
            TooltipUtil.onHover("Aim at a block and click. The row starts with that block.");
        } else {
            Controls.disabledButton("Change block");
        }
        ImGui.sameLine();
        Controls.pushInputFrameHeight();
        ImGui.alignTextToFramePadding();
        ImGui.textDisabled("(?)");
        TooltipUtil.onHover(HELP);
        if (!available) {
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.text(SINGLEPLAYER_ONLY);
            ThemeManager.popTextColor();
        } else if (!blocks.isEmpty()) {
            String shown = "World shows tick " + (world.viewRow() + 1);
            float w = ImGui.calcTextSize(shown).x;
            ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - w);
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(shown);
            ThemeManager.popTextColor();
        }
        Controls.popInputFrameHeight();
        ThemeManager.sectionSpacing();
        if (blocks.isEmpty()) {
            ImGui.textDisabled("No blocks.");
            return;
        }
        table(blocks);
    }

    private void table(BlockOverrides blocks) {
        syncBuffers(blocks);
        float scale = ThemeManager.uiScale();
        float rowH = ThemeManager.tableRowHeight();
        float headerH = ThemeManager.tableHeaderRowHeight();
        float wanted = headerH + blocks.size() * rowH + 2f * scale;
        float height = Math.max(headerH + rowH, ImGui.getContentRegionAvailY());
        int flags = ThemeManager.standardTableFlags();
        if (wanted <= height) flags &= ~ImGuiTableFlags.ScrollY;
        boolean ok = ThemeManager.beginStandardTableWithFlags(TABLE_ID, HEADERS.length, flags, 0f, height);
        if (!ok) return;
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        ImGui.tableSetupColumn(HEADERS[0], fixed, ThemeManager.tableLeftmostColumnWidth(HEADERS[0], TICK_W * scale));
        ImGui.tableSetupColumn(HEADERS[1], fixed, ThemeManager.tableColumnWidth(HEADERS[1], TICK_W * scale));
        ImGui.tableSetupColumn(HEADERS[2], fixed, ThemeManager.tableColumnWidth(HEADERS[2], POS_W * scale));
        ImGui.tableSetupColumn(HEADERS[3], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[4], fixed, ThemeManager.tableColumnWidth(HEADERS[4], SolverWidgets.segmentedMinWidth(PLACED_BY)));
        ImGui.tableSetupColumn(HEADERS[5], fixed, SolverWidgets.deleteXWidth());
        ImGui.tableSetupScrollFreeze(0, 1);
        header();
        int viewRow = world.viewRow();
        int removeAt = -1;
        float left = ImGui.getWindowPosX();
        float right = left + ImGui.getWindowWidth();
        boolean click = (ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1))
                && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows | ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
        float padY = ImGui.getStyle().getCellPaddingY();
        for (int i = 0; i < blocks.size(); i++) {
            ImGui.tableNextRow(0, rowH);
            ThemeManager.paintTableRowBg(i);
            ImGui.pushID(i);
            BlockOverride b = blocks.get(i);
            if (world.isSelected(b)) ThemeManager.paintTableRowTint(ThemeManager.rgbaTintColor(settings.blockOverrideSelectedRow));
            boolean problem = world.hasProblem(b);
            boolean dim = !problem && !b.activeAtRow(viewRow);
            if (problem) ThemeManager.pushTextColor(ThemeManager.dangerColor());
            if (dim) ThemeManager.pushTextColor(ThemeManager.textDimColor());
            boolean remove = row(blocks, i);
            float top = rowTop - padY;
            if (click && ImGui.isMouseHoveringRect(left, top, right, rowBottom + padY, false)) world.select(blocks.get(Math.min(i, blocks.size() - 1)));
            if (dim || problem) ThemeManager.popTextColor();
            if (pickerRow == i) picker(blocks, i);
            if (remove) removeAt = i;
            ImGui.popID();
        }
        ThemeManager.endStandardTable();
        if (removeAt >= 0) {
            blocks.remove(removeAt);
            posBuffers.remove(removeAt);
            endBuffers.remove(removeAt);
            startBuffers.remove(removeAt);
            if (editingRow == removeAt) editingRow = -1;
            if (pickerRow == removeAt) pickerRow = -1;
            changed(0);
        }
    }

    private void header() {
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        for (int c = 0; c < HEADERS.length; c++) {
            ImGui.tableSetColumnIndex(c);
            ThemeManager.tableHeaderCentered(HEADERS[c]);
        }
    }

    private static int rgba(float[] c) {
        return ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], 1f);
    }

    private float rowTop;
    private float rowBottom;

    private boolean row(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        ImGui.tableSetColumnIndex(0);
        rowTop = ImGui.getCursorScreenPosY();
        Integer start = startCell(i, b);
        rowTooltip(b);
        ImGui.tableSetColumnIndex(1);
        Integer end = endCell(i, b);
        rowTooltip(b);
        if (start != null || end != null) {
            int s = Math.max(1, start != null ? start : b.startTick);
            int e = end != null ? end : b.endTick;
            if (e != BlockOverride.NO_END && e < s) e = s;
            update(blocks, i, b.withTicks(s, e), b);
            return false;
        }
        ImGui.tableSetColumnIndex(2);
        positionCell(blocks, i);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(3);
        blockCell(blocks, i);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(4);
        int clicked = SolverWidgets.segmented("placed", PLACED_BY, PLACED_BY_TIPS, b.player ? 1 : 0, ImGui.getContentRegionAvailX());
        if (clicked >= 0 && (clicked == 1) != b.player) update(blocks, i, b.withPlayer(clicked == 1), b);
        ImGui.tableSetColumnIndex(5);
        boolean remove = SolverWidgets.deleteX("del");
        rowBottom = ImGui.getItemRectMax().y;
        rowTooltip(b);
        return remove;
    }

    private void rowTooltip(BlockOverride b) {
        if (!ImGui.isItemHovered()) return;
        float scale = ThemeManager.uiScale();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, TIP_PAD_X * scale, TIP_PAD_Y * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), TIP_GAP * scale);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, rgba(POPUP_BG));
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
        ImGui.beginTooltip();
        float wrapX = ImGui.getCursorPosX() + TIP_W * scale - 2f * TIP_PAD_X * scale;
        ImGui.pushTextWrapPos(wrapX);
        problemLine(world.simProblem(b), scale);
        String replay = world.replayProblem(b);
        if (replay != null) problemLine(replay + " (replay)", scale);
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Before: " + displayName(b.before));
        ImGui.sameLine();
        if (ImGui.getCursorPosX() + ImGui.calcTextSize(BEFORE_NOTE).x > wrapX) ImGui.newLine();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(BEFORE_NOTE);
        ThemeManager.popTextColor();
        ImGui.text("Full id: " + b.block);
        ThemeManager.popTextColor();
        ImGui.popTextWrapPos();
        ImGui.endTooltip();
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
    }

    private void problemLine(String text, float scale) {
        if (text == null) return;
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float cy = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
        float w = warningGlyph(dl, x, cy, scale);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + w + 8f * scale);
        ThemeManager.pushTextColor(ThemeManager.dangerColor());
        ImGui.text(text);
        ThemeManager.popTextColor();
    }

    private String displayName(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return spec.isEmpty() ? "?" : spec;
        return parsed.displayName(BlockOverride.Spec.parse(mc.defaultBlockState(parsed.id)));
    }

    private Integer startCell(int i, BlockOverride b) {
        ImString buf = startBuffers.get(i);
        if (!(editingRow == i && editingField == 0)) buf.set(String.valueOf(b.startTick));
        ImGui.setNextItemWidth(-1f);
        Controls.tableInputText("##start", buf, 0f, 0, null);
        if (ImGui.isItemActivated()) {
            editingRow = i;
            editingField = 0;
        }
        Integer result = null;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            result = parseStart(buf.get(), b.startTick);
            editingRow = -1;
        } else if (ImGui.isItemDeactivated()) {
            editingRow = -1;
        }
        return result != null && result != b.startTick ? result : null;
    }

    private static Integer parseStart(String text, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Integer endCell(int i, BlockOverride b) {
        ImString buf = endBuffers.get(i);
        if (!(editingRow == i && editingField == 1)) buf.set(b.endTick == BlockOverride.NO_END ? "" : String.valueOf(b.endTick));
        ImGui.setNextItemWidth(-1f);
        Controls.tableInputTextHint("##end", END_HINT, buf, 0f);
        if (ImGui.isItemActivated()) {
            editingRow = i;
            editingField = 1;
        }
        Integer result = null;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            result = parseEnd(buf.get(), b.endTick);
            editingRow = -1;
        } else if (ImGui.isItemDeactivated()) {
            editingRow = -1;
        }
        return result != null && result != b.endTick ? result : null;
    }

    private static Integer parseEnd(String text, int fallback) {
        String s = text.trim();
        if (s.isEmpty()) return BlockOverride.NO_END;
        try {
            return Math.max(BlockOverride.NO_END, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void positionCell(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        ImString buf = posBuffers.get(i);
        if (!(editingRow == i && editingField == 2)) buf.set(b.x + " " + b.y + " " + b.z);
        ImGui.setNextItemWidth(-1f);
        Controls.tableInputText("##pos", buf, 0f, 0, null);
        rowTooltip(b);
        if (ImGui.isItemActivated()) {
            editingRow = i;
            editingField = 2;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            editingRow = -1;
            int[] pos = parsePosition(buf.get());
            if (pos != null) update(blocks, i, b.withPos(pos[0], pos[1], pos[2]), b);
        } else if (ImGui.isItemDeactivated()) {
            editingRow = -1;
        }
    }

    private static int[] parsePosition(String text) {
        String[] parts = text.trim().split("[\\s,]+");
        if (parts.length != 3) return null;
        try {
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void blockCell(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        float scale = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        float w = ImGui.getContentRegionAvailX();
        boolean clicked = ImGui.invisibleButton("##block", Math.max(1f, w), h);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = hover ? ThemeManager.accentTintColor(0.18f) : ThemeManager.panelColor();
        float rounding = ImGui.getStyle().getFrameRounding();
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, bg, rounding);
        dl.addRect(mn.x, mn.y, mx.x, mx.y, ThemeManager.borderColor(), rounding);
        String error = errorOf(b.block);
        int textCol = ImGui.getColorU32(ImGuiCol.Text);
        boolean problem = world.hasProblem(b);
        boolean dim = !problem && !b.activeAtRow(world.viewRow());
        if (error != null) textCol = ThemeManager.dangerColor();
        BlockOverride.Spec spec = b.spec();
        String name = error != null ? b.block : spec.shortName();
        String states = error != null ? "" : displayName(b.block).substring(name.length());
        float padX = ImGui.getStyle().getFramePaddingX();
        float arrowW = SolverWidgets.dropdownArrowWidth();
        float textY = mn.y + (h - ImGui.getFontSize()) * 0.5f;
        float x = mn.x + padX;
        dl.pushClipRect(mn.x, mn.y, mx.x - arrowW, mx.y, true);
        if (problem) {
            x += warningGlyph(dl, x, mn.y + h * 0.5f, scale) + padX;
        }
        dl.addText(x, textY, textCol, name);
        if (!states.isEmpty()) {
            dl.addText(x + ImGui.calcTextSize(name).x, textY, problem || dim ? textCol : ThemeManager.textMutedColor(), states);
        }
        dl.popClipRect();
        SolverWidgets.triangleDown(dl, mx.x - arrowW * 0.5f, mn.y + h * 0.5f, 3.3f * scale, ThemeManager.textMutedColor());
        if (error != null) TooltipUtil.onHover(error);
        else rowTooltip(b);
        if (clicked) {
            pickerRow = i;
            pickerX = mn.x;
            pickerY = mx.y + 2f * scale;
            search.set("");
            ImGui.openPopup(PICKER_ID);
        }
    }

    private static float warningGlyph(ImDrawList dl, float x, float cy, float scale) {
        float w = 11f * scale;
        float hh = 5f * scale;
        int col = ThemeManager.dangerColor();
        float t = 1.3f * scale;
        dl.addTriangle(x + w * 0.5f, cy - hh, x + w, cy + hh, x, cy + hh, col, t);
        dl.addLine(x + w * 0.5f, cy - hh * 0.3f, x + w * 0.5f, cy + hh * 0.3f, col, t);
        dl.addLine(x + w * 0.5f, cy + hh * 0.55f, x + w * 0.5f, cy + hh * 0.7f, col, t);
        return w;
    }

    private void picker(BlockOverrides blocks, int i) {
        float scale = ThemeManager.uiScale();
        float pad = PICKER_PAD * scale;
        ImGui.setNextWindowPos(pickerX, pickerY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(PICKER_W * scale, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, pad, pad);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), pad);
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1f);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, rgba(POPUP_BG));
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
        boolean open = ImGui.beginPopup(PICKER_ID);
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(3);
        if (!open) {
            pickerRow = -1;
            return;
        }
        Controls.pushInputFrameHeight();
        BlockOverride b = blocks.get(i);
        searchField();
        String query = search.get().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String chosen = blockList(b, query, scale);
        if (chosen != null) {
            update(blocks, i, b.withBlock(chosen), b);
        }
        states(blocks, i);
        Controls.popInputFrameHeight();
        ImGui.endPopup();
    }

    private void searchField() {
        float scale = ThemeManager.uiScale();
        float iconW = 18f * scale;
        float padX = ImGui.getStyle().getFramePaddingX();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, padX + iconW, ImGui.getStyle().getFramePaddingY());
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        Controls.inputTextHint("##search", "Search", search, 0f);
        ImGui.popStyleVar();
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        ImDrawList dl = ImGui.getWindowDrawList();
        int col = ThemeManager.textMutedColor();
        float cx = mn.x + padX + 5f * scale;
        float cy = (mn.y + mx.y) * 0.5f - 1f * scale;
        float r = 3.6f * scale;
        dl.addCircle(cx, cy, r, col, 16, 1.4f * scale);
        dl.addLine(cx + r * 0.72f, cy + r * 0.72f, cx + r * 1.9f, cy + r * 1.9f, col, 1.4f * scale);
    }

    private String blockList(BlockOverride b, String query, float scale) {
        float rowH = ImGui.getFrameHeight();
        ImGui.pushStyleColor(ImGuiCol.ChildBg, rgba(LIST_BG));
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, ImGui.getStyle().getFrameRounding());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.beginChild("##list", -1f, rowH * PICKER_ROWS + 2f, true);
        String held = mc.isReady() ? mc.getHeldBlock() : null;
        String looked = null;
        if (mc.isReady()) {
            BlockHit hit = mc.getLookedAtHit();
            if (hit != null) looked = mc.describeBlockState(hit.x, hit.y, hit.z);
        }
        String chosen = null;
        String currentId = b.spec().valid() ? b.spec().id : "";
        if (held != null && matches(held, query) && pickerEntry("h", displayName(held), "in hand", sameId(held, currentId), rowH)) chosen = held;
        if (looked != null && matches(looked, query) && pickerEntry("l", displayName(looked), "looked at", sameId(looked, currentId), rowH)) chosen = looked;
        int shown = 0;
        for (String id : mc.blockIds()) {
            if (!matches(id, query)) continue;
            if (shown++ >= PICKER_MAX_IDS) break;
            if (pickerEntry(id, BlockOverride.Spec.parse(id).shortName(), null, id.equals(currentId), rowH)) {
                String def = mc.defaultBlockState(id);
                chosen = def != null ? def : id;
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(4);
        ImGui.popStyleColor(2);
        return chosen;
    }

    private static boolean sameId(String spec, String id) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        return parsed.valid() && parsed.id.equals(id);
    }

    private static boolean matches(String spec, String query) {
        if (query.isEmpty()) return true;
        String id = BlockOverride.Spec.parse(spec).id;
        return id.contains(query);
    }

    private boolean pickerEntry(String id, String label, String tag, boolean selected, float rowH) {
        float scale = ThemeManager.uiScale();
        float w = ImGui.getContentRegionAvailX();
        boolean clicked = ImGui.invisibleButton("##e" + id, Math.max(1f, w), rowH);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        ImDrawList dl = ImGui.getWindowDrawList();
        if (selected) {
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.45f));
        } else if (hover) {
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.25f));
        }
        float padX = 8f * scale;
        float ty = mn.y + (rowH - ImGui.getFontSize()) * 0.5f;
        dl.addText(mn.x + padX, ty, ThemeManager.textColor(), label);
        if (tag != null) {
            float tagW = ImGui.calcTextSize(tag).x;
            dl.addText(mx.x - padX - tagW, ty, ThemeManager.textDimColor(), tag);
        }
        return clicked;
    }

    private void states(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        BlockOverride.Spec spec = b.spec();
        if (!spec.valid()) return;
        Map<String, List<String>> options = mc.blockStateOptions(spec.id);
        if (options.isEmpty()) return;
        float scale = ThemeManager.uiScale();
        BlockOverride.Spec defaults = BlockOverride.Spec.parse(mc.defaultBlockState(spec.id));
        ImGui.setCursorPosY(ImGui.getCursorPosY() + STATE_PAD_TOP * scale);
        float labelX = ImGui.getCursorPosX() + STATE_PAD_X * scale;
        float right = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - STATE_PAD_X * scale;
        float arrowW = SolverWidgets.dropdownArrowWidth();
        float labelW = PICKER_LABEL_W * scale;
        for (String key : options.keySet()) {
            labelW = Math.max(labelW, ImGui.calcTextSize(key).x + STATE_LABEL_GAP * scale);
        }
        float rowH = ImGui.getFrameHeight();
        for (Map.Entry<String, List<String>> e : options.entrySet()) {
            String key = e.getKey();
            String current = spec.properties.get(key);
            if (current == null && defaults.valid()) current = defaults.properties.get(key);
            if (current == null) current = "";
            ImGui.setCursorPosX(labelX);
            ImGui.alignTextToFramePadding();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(key);
            ThemeManager.popTextColor();
            ImGui.sameLine(labelX + labelW);
            ImGui.setNextItemWidth(right - ImGui.getCursorPosX());
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, COMBO_PAD_Y * scale);
            ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1f);
            ImGui.pushStyleColor(ImGuiCol.PopupBg, rgba(LIST_BG));
            ImGui.pushStyleColor(ImGuiCol.Header, ThemeManager.accentTintColor(0.45f));
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, ThemeManager.accentTintColor(0.25f));
            ImGui.pushStyleColor(ImGuiCol.HeaderActive, ThemeManager.accentTintColor(0.35f));
            boolean open = ImGui.beginCombo("##" + key, current, ImGuiComboFlags.NoArrowButton);
            ImVec2 mx = ImGui.getItemRectMax();
            ImVec2 mn = ImGui.getItemRectMin();
            SolverWidgets.triangleDown(ImGui.getWindowDrawList(), mx.x - arrowW * 0.5f, (mn.y + mx.y) * 0.5f,
                    3.3f * scale, ThemeManager.textMutedColor());
            if (open) {
                ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
                ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, COMBO_PAD_X * scale, (rowH - ImGui.getFontSize()) * 0.5f);
                for (String value : e.getValue()) {
                    boolean picked = comboEntry(value, value.equals(current), rowH);
                    if (picked && !value.equals(current)) {
                        update(blocks, i, b.withBlock(spec.withProperty(key, value).text()), b);
                        b = blocks.get(i);
                        spec = b.spec();
                    }
                }
                ImGui.popStyleVar(2);
                ImGui.endCombo();
            }
            ImGui.popStyleColor(4);
            ImGui.popStyleVar(2);
        }
    }

    private boolean comboEntry(String value, boolean selected, float rowH) {
        float scale = ThemeManager.uiScale();
        float w = ImGui.getContentRegionAvailX();
        boolean clicked = ImGui.invisibleButton("##v" + value, Math.max(1f, w), rowH);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImVec2 mx = ImGui.getItemRectMax();
        ImDrawList dl = ImGui.getWindowDrawList();
        if (selected) {
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.45f));
        } else if (hover) {
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.25f));
        }
        dl.addText(mn.x + COMBO_PAD_X * scale, mn.y + (rowH - ImGui.getFontSize()) * 0.5f, ThemeManager.textColor(), value);
        if (clicked) ImGui.closeCurrentPopup();
        return clicked;
    }

    private String errorOf(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return parsed.error;
        return mc.blockOverrideError(spec);
    }

    private void update(BlockOverrides blocks, int i, BlockOverride next, BlockOverride prev) {
        if (next.equals(prev)) return;
        blocks.set(i, next);
        if (world.isSelected(prev)) world.select(next);
        boolean samePlace = next.x == prev.x && next.y == prev.y && next.z == prev.z
                && next.block.equals(prev.block) && next.before.equals(prev.before);
        changed(samePlace ? Math.min(next.startTick, prev.startTick) - 1 : 0);
    }

    private void changed(int dirtyRow) {
        onChanged.accept(Math.max(0, dirtyRow));
    }

    private void syncBuffers(BlockOverrides blocks) {
        while (posBuffers.size() < blocks.size()) posBuffers.add(new ImString(POS_CHARS));
        while (posBuffers.size() > blocks.size()) posBuffers.remove(posBuffers.size() - 1);
        while (endBuffers.size() < blocks.size()) endBuffers.add(new ImString(TICK_CHARS));
        while (endBuffers.size() > blocks.size()) endBuffers.remove(endBuffers.size() - 1);
        while (startBuffers.size() < blocks.size()) startBuffers.add(new ImString(TICK_CHARS));
        while (startBuffers.size() > blocks.size()) startBuffers.remove(startBuffers.size() - 1);
        if (editingRow >= blocks.size()) editingRow = -1;
        if (pickerRow >= blocks.size()) pickerRow = -1;
    }
}
