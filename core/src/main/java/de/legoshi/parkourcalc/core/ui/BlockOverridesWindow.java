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
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiComboFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTableRowFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
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
    private static final float POS_W = 186f;
    private static final float PLACED_W = 154f;
    private static final float DELETE_COL_W = 42f;
    private static final float DELETE_W = 34f;
    private static final float CROSS_W = 28f;
    private static final float POS_GAP = 4f;
    private static final float FRAME_H = 24f;
    private static final float ROW_H = 32f;
    private static final float HEADER_H = 26f;
    private static final float CELL_PAD_X = 3f;
    private static final float CELL_PAD_Y = 4f;
    private static final float FRAME_PAD_X = 6f;
    private static final float PICKER_W = 300f;
    private static final float PICKER_PAD = 8f;
    private static final float PICKER_ROW_H = 24f;
    private static final float SEARCH_H = 26f;
    private static final float STATE_H = 22f;
    private static final float STATE_GAP_Y = 6f;
    private static final float STATE_GAP_X = 8f;
    private static final float STATE_PAD_X = 4f;
    private static final float STATE_PAD_TOP = 2f;
    private static final float TIP_W = 420f;
    private static final float TIP_PAD_X = 12f;
    private static final float TIP_PAD_Y = 10f;
    private static final float TIP_GAP = 4f;
    private static final float PICKER_LABEL_W = 70f;
    private static final float[] ROW_BASE = {0x1e / 255f, 0x1e / 255f, 0x2e / 255f};
    private static final float[] ROW_ALT = {0x23 / 255f, 0x23 / 255f, 0x34 / 255f};
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
    private static final String HELP = "Place block: aim at a face, LMB. Change block: aim at a block, RMB.\n"
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
    private final ImInt intHolder = new ImInt();
    private final ImString search = new ImString(SEARCH_CHARS);
    private final List<ImString> posBuffers = new ArrayList<ImString>();
    private final List<ImString> endBuffers = new ArrayList<ImString>();
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
            TooltipUtil.onHover("Aim at a face and left click. Uses the block in your hand.");
            ImGui.sameLine();
            if (Controls.secondaryButton("Change block")) placing.startChange();
            TooltipUtil.onHover("Aim at a block and right click. The row starts with that block.");
        } else {
            Controls.disabledButton("Place block");
            ImGui.sameLine();
            Controls.disabledButton("Change block");
        }
        ImGui.sameLine();
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
        ImGui.spacing();
        if (blocks.isEmpty()) {
            ImGui.textDisabled("No blocks.");
            return;
        }
        table(blocks);
    }

    private void table(BlockOverrides blocks) {
        syncBuffers(blocks);
        float scale = ThemeManager.uiScale();
        float rowH = ROW_H * scale;
        float headerH = HEADER_H * scale;
        float wanted = headerH + blocks.size() * rowH + 2f * scale;
        float height = Math.min(wanted, Math.max(headerH + rowH, ImGui.getContentRegionAvailY()));
        int flags = ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.BordersOuter
                | (wanted > height ? ImGuiTableFlags.ScrollY : 0);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, CELL_PAD_X * scale, CELL_PAD_Y * scale);
        boolean ok = ThemeManager.beginStandardTableWithFlags(TABLE_ID, HEADERS.length, flags, 0f, height);
        if (!ok) {
            ImGui.popStyleVar();
            return;
        }
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        ImGui.tableSetupColumn(HEADERS[0], fixed, TICK_W * scale);
        ImGui.tableSetupColumn(HEADERS[1], fixed, TICK_W * scale);
        ImGui.tableSetupColumn(HEADERS[2], fixed, POS_W * scale);
        ImGui.tableSetupColumn(HEADERS[3], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[4], fixed, PLACED_W * scale);
        ImGui.tableSetupColumn(HEADERS[5], fixed, DELETE_COL_W * scale);
        ImGui.tableSetupScrollFreeze(0, 1);
        header(headerH);
        int viewRow = world.viewRow();
        int removeAt = -1;
        float framePadY = Math.max(0f, (FRAME_H * scale - ImGui.getFontSize()) * 0.5f);
        for (int i = 0; i < blocks.size(); i++) {
            ImGui.tableNextRow(0, rowH);
            ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, rgba((i & 1) == 0 ? ROW_BASE : ROW_ALT));
            ImGui.pushID(i);
            BlockOverride b = blocks.get(i);
            boolean problem = world.hasProblem(b);
            boolean dim = !problem && !b.activeAtRow(viewRow);
            if (problem) ThemeManager.pushTextColor(ThemeManager.dangerColor());
            if (dim) ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FRAME_PAD_X * scale, framePadY);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, ThemeManager.panelColor());
            ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
            boolean remove = row(blocks, i);
            ImGui.popStyleColor(2);
            ImGui.popStyleVar(2);
            if (dim || problem) ThemeManager.popTextColor();
            if (pickerRow == i) picker(blocks, i);
            if (remove) removeAt = i;
            ImGui.popID();
        }
        ThemeManager.endStandardTable();
        ImGui.popStyleVar();
        if (removeAt >= 0) {
            blocks.remove(removeAt);
            posBuffers.remove(removeAt);
            endBuffers.remove(removeAt);
            if (editingRow == removeAt) editingRow = -1;
            if (pickerRow == removeAt) pickerRow = -1;
            changed(0);
        }
    }

    private void header(float headerH) {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers, headerH);
        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, ThemeManager.bgDarkColor());
        ImDrawList dl = ImGui.getWindowDrawList();
        float padX = ImGui.getStyle().getCellPaddingX();
        float padY = ImGui.getStyle().getCellPaddingY();
        for (int c = 0; c < HEADERS.length; c++) {
            ImGui.tableSetColumnIndex(c);
            float x = ImGui.getCursorScreenPosX();
            float y = ImGui.getCursorScreenPosY() - padY;
            if (c > 0) dl.addLine(x - padX, y, x - padX, y + headerH, ThemeManager.borderColor(), 1f);
            dl.addText(x + 3f * ThemeManager.uiScale(), y + (headerH - ImGui.getFontSize()) * 0.5f,
                    ThemeManager.textMutedColor(), HEADERS[c]);
        }
    }

    private static int rgba(float[] c) {
        return ImGui.colorConvertFloat4ToU32(c[0], c[1], c[2], 1f);
    }

    private boolean row(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        ImGui.tableSetColumnIndex(0);
        Integer start = intCell("##start", b.startTick);
        rowTooltip(b);
        ImGui.tableSetColumnIndex(1);
        Integer end = endCell(i, b);
        rowTooltip(b);
        if (start != null || end != null) {
            int s = Math.max(1, start != null ? start : b.startTick);
            int e = end != null ? end : b.endTick;
            if (e != BlockOverride.NO_END && e < s) e = s;
            update(blocks, i, b.withTicks(s, e), b);
            b = blocks.get(i);
        }
        ImGui.tableSetColumnIndex(2);
        positionCell(blocks, i);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(3);
        blockCell(blocks, i);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(4);
        int clicked = placedBy(b.player ? 1 : 0);
        if (clicked >= 0 && (clicked == 1) != b.player) update(blocks, i, b.withPlayer(clicked == 1), b);
        ImGui.tableSetColumnIndex(5);
        boolean remove = deleteButton();
        rowTooltip(b);
        return remove;
    }

    private int placedBy(int selected) {
        float h = ImGui.getFrameHeight();
        float w = ImGui.getContentRegionAvailX();
        float segW = w / PLACED_BY.length;
        ImDrawList dl = ImGui.getWindowDrawList();
        float rounding = ImGui.getStyle().getFrameRounding();
        int textCol = ImGui.getColorU32(ImGuiCol.Text);
        int clicked = -1;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        dl.addRectFilled(left, top, left + w, top + h, ThemeManager.panelColor(), rounding);
        for (int k = 0; k < PLACED_BY.length; k++) {
            if (k > 0) ImGui.sameLine(0f, 0f);
            if (ImGui.invisibleButton("##seg" + k, segW, h)) clicked = k;
            boolean hover = ImGui.isItemHovered();
            if (hover) ImGui.setTooltip(PLACED_BY_TIPS[k]);
            ImVec2 mn = ImGui.getItemRectMin();
            ImVec2 mx = ImGui.getItemRectMax();
            boolean on = k == selected;
            if (on) {
                dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentColor(), rounding,
                        k == 0 ? ImDrawFlags.RoundCornersLeft : ImDrawFlags.RoundCornersRight);
            } else if (hover) {
                dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.18f), 0f);
            }
            if (k > 0) dl.addLine(mn.x, mn.y, mn.x, mx.y, ThemeManager.borderColor(), 1f);
            if (on) Fonts.pushBold();
            ImVec2 ts = ImGui.calcTextSize(PLACED_BY[k]);
            dl.addText(mn.x + (segW - ts.x) * 0.5f, mn.y + (h - ImGui.getFontSize()) * 0.5f,
                    on ? ThemeManager.bgDarkColor() : textCol, PLACED_BY[k]);
            if (on) Fonts.popBold();
        }
        dl.addRect(left, top, left + w, top + h, ThemeManager.borderColor(), rounding, 0, 1f);
        return clicked;
    }

    private boolean deleteButton() {
        float scale = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        float w = DELETE_W * scale;
        boolean clicked = ImGui.invisibleButton("##delete", w, h);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImDrawList dl = ImGui.getWindowDrawList();
        float rounding = ImGui.getStyle().getFrameRounding();
        dl.addRectFilled(mn.x, mn.y, mn.x + w, mn.y + h, ThemeManager.dangerColor(), rounding);
        if (hover) dl.addRectFilled(mn.x, mn.y, mn.x + w, mn.y + h, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.12f), rounding);
        float cx = mn.x + w * 0.5f;
        float cy = mn.y + h * 0.5f;
        float r = 3.5f * scale;
        int col = ThemeManager.bgDarkColor();
        dl.addLine(cx - r, cy - r, cx + r, cy + r, col, 1.8f * scale);
        dl.addLine(cx + r, cy - r, cx - r, cy + r, col, 1.8f * scale);
        return clicked;
    }

    private void rowTooltip(BlockOverride b) {
        if (!ImGui.isItemHovered()) return;
        float scale = ThemeManager.uiScale();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, TIP_PAD_X * scale, TIP_PAD_Y * scale);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ImGui.getStyle().getItemSpacingX(), TIP_GAP * scale);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, rgba(POPUP_BG));
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
        ImGui.beginTooltip();
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + TIP_W * scale - 2f * TIP_PAD_X * scale);
        problemLine(world.simProblem(b), scale);
        String replay = world.replayProblem(b);
        if (replay != null) problemLine(replay + " (replay)", scale);
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Before: " + displayName(b.before));
        ImGui.sameLine();
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

    private Integer intCell(String id, int value) {
        intHolder.set(value);
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.inputInt(id, intHolder, 0, 0);
        return changed && intHolder.get() != value ? intHolder.get() : null;
    }

    private Integer endCell(int i, BlockOverride b) {
        ImString buf = endBuffers.get(i);
        if (!(editingRow == i && editingField == 1)) buf.set(b.endTick == BlockOverride.NO_END ? "" : String.valueOf(b.endTick));
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##end", END_HINT, buf);
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
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - (CROSS_W + POS_GAP) * ThemeManager.uiScale());
        ImGui.inputText("##pos", buf);
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
        ImGui.sameLine(0f, POS_GAP * ThemeManager.uiScale());
        if (crosshairButton("##pick")) {
            BlockHit hit = mc.isReady() ? mc.getLookedAtHit() : null;
            if (hit != null) {
                b = blocks.get(i);
                String before = mc.describeBlockState(hit.x, hit.y, hit.z);
                BlockOverride next = b.withPos(hit.x, hit.y, hit.z);
                if (before != null) next = next.withBefore(before);
                update(blocks, i, next, b);
            }
        }
        TooltipUtil.onHover("Move to the looked-at block");
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

    private boolean crosshairButton(String id) {
        float scale = ThemeManager.uiScale();
        float h = ImGui.getFrameHeight();
        float w = CROSS_W * scale;
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = hover ? ThemeManager.accentTintColor(0.18f) : ThemeManager.panelColor();
        float rounding = ImGui.getStyle().getFrameRounding();
        dl.addRectFilled(mn.x, mn.y, mn.x + w, mn.y + h, bg, rounding);
        dl.addRect(mn.x, mn.y, mn.x + w, mn.y + h, ThemeManager.borderColor(), rounding);
        int col = hover ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        float cx = mn.x + w * 0.5f;
        float cy = mn.y + h * 0.5f;
        float r = 3.2f * scale;
        float tick = 2.6f * scale;
        dl.addCircle(cx, cy, r, col, 16, 1.2f * scale);
        dl.addLine(cx - r - tick, cy, cx - r + tick, cy, col, 1.2f * scale);
        dl.addLine(cx + r - tick, cy, cx + r + tick, cy, col, 1.2f * scale);
        dl.addLine(cx, cy - r - tick, cx, cy - r + tick, col, 1.2f * scale);
        dl.addLine(cx, cy + r - tick, cx, cy + r + tick, col, 1.2f * scale);
        return clicked;
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
        if (error != null) textCol = ThemeManager.dangerColor();
        BlockOverride.Spec spec = b.spec();
        String name = error != null ? b.block : spec.shortName();
        String states = error != null ? "" : displayName(b.block).substring(name.length());
        float padX = ImGui.getStyle().getFramePaddingX();
        float arrowW = 20f * scale;
        float textY = mn.y + (h - ImGui.getFontSize()) * 0.5f;
        float x = mn.x + padX;
        dl.pushClipRect(mn.x, mn.y, mx.x - arrowW, mx.y, true);
        if (problem) {
            x += warningGlyph(dl, x, mn.y + h * 0.5f, scale) + padX;
        }
        dl.addText(x, textY, textCol, name);
        if (!states.isEmpty()) {
            dl.addText(x + ImGui.calcTextSize(name).x, textY, problem ? textCol : ThemeManager.textMutedColor(), states);
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
        float framePadY = Math.max(0f, (FRAME_H * scale - ImGui.getFontSize()) * 0.5f);
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
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FRAME_PAD_X * scale, framePadY);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, ThemeManager.panelColor());
        ImGui.pushStyleColor(ImGuiCol.Border, ThemeManager.borderColor());
        BlockOverride b = blocks.get(i);
        searchField();
        String query = search.get().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String chosen = blockList(b, query, scale);
        if (chosen != null) {
            update(blocks, i, b.withBlock(chosen), b);
        }
        states(blocks, i);
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
        ImGui.endPopup();
    }

    private void searchField() {
        float scale = ThemeManager.uiScale();
        float iconW = 18f * scale;
        float padX = FRAME_PAD_X * scale;
        float padY = Math.max(0f, (SEARCH_H * scale - ImGui.getFontSize()) * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, padX + iconW, padY);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##search", "Search", search);
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
        float rowH = PICKER_ROW_H * scale;
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
            dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, ThemeManager.accentTintColor(0.18f));
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
        float framePadY = Math.max(0f, (STATE_H * scale - ImGui.getFontSize()) * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FRAME_PAD_X * scale, framePadY);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, STATE_GAP_X * scale, STATE_GAP_Y * scale);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + STATE_PAD_TOP * scale);
        float labelX = ImGui.getCursorPosX() + STATE_PAD_X * scale;
        float right = ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - STATE_PAD_X * scale;
        float arrowW = 20f * scale;
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
            ImGui.sameLine(labelX + PICKER_LABEL_W * scale);
            ImGui.setNextItemWidth(right - ImGui.getCursorPosX());
            boolean open = ImGui.beginCombo("##" + key, current, ImGuiComboFlags.NoArrowButton);
            ImVec2 mx = ImGui.getItemRectMax();
            ImVec2 mn = ImGui.getItemRectMin();
            SolverWidgets.triangleDown(ImGui.getWindowDrawList(), mx.x - arrowW * 0.5f, (mn.y + mx.y) * 0.5f,
                    3.3f * scale, ThemeManager.textMutedColor());
            if (open) {
                for (String value : e.getValue()) {
                    if (ImGui.selectable(value, value.equals(current)) && !value.equals(current)) {
                        update(blocks, i, b.withBlock(spec.withProperty(key, value).text()), b);
                        b = blocks.get(i);
                        spec = b.spec();
                    }
                }
                ImGui.endCombo();
            }
        }
        ImGui.popStyleVar(2);
    }

    private String errorOf(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return parsed.error;
        return mc.blockOverrideError(spec);
    }

    private void update(BlockOverrides blocks, int i, BlockOverride next, BlockOverride prev) {
        if (next.equals(prev)) return;
        blocks.set(i, next);
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
        if (editingRow >= blocks.size()) editingRow = -1;
        if (pickerRow >= blocks.size()) pickerRow = -1;
    }
}
