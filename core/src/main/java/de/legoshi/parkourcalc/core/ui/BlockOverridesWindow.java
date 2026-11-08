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
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
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

    private static final String WINDOW_ID = "Block Overrides";
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
    private static final float PICKER_W = 300f;
    private static final float PICKER_LABEL_W = 96f;
    private static final int PICKER_ROWS = 6;
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
        if (!ThemeManager.beginStandardTable(TABLE_ID, HEADERS.length)) return;
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        ImGui.tableSetupColumn(HEADERS[0], fixed, TICK_W * scale);
        ImGui.tableSetupColumn(HEADERS[1], fixed, TICK_W * scale);
        ImGui.tableSetupColumn(HEADERS[2], fixed, POS_W * scale);
        ImGui.tableSetupColumn(HEADERS[3], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[4], fixed, PLACED_W * scale);
        ImGui.tableSetupColumn(HEADERS[5], fixed, SolverWidgets.deleteXWidth());
        ImGui.tableSetupScrollFreeze(0, 1);
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        for (int c = 0; c < HEADERS.length; c++) {
            ImGui.tableSetColumnIndex(c);
            ThemeManager.tableHeader(HEADERS[c]);
        }
        int viewRow = world.viewRow();
        int removeAt = -1;
        for (int i = 0; i < blocks.size(); i++) {
            ImGui.tableNextRow();
            ImGui.pushID(i);
            BlockOverride b = blocks.get(i);
            boolean problem = world.hasProblem(b);
            boolean dim = !problem && !b.activeAtRow(viewRow);
            if (problem) ThemeManager.pushTextColor(ThemeManager.dangerColor());
            if (dim) ThemeManager.pushTextColor(ThemeManager.textDimColor());
            boolean remove = row(blocks, i);
            if (dim || problem) ThemeManager.popTextColor();
            if (remove) removeAt = i;
            ImGui.popID();
        }
        ThemeManager.endStandardTable();
        if (removeAt >= 0) {
            blocks.remove(removeAt);
            posBuffers.remove(removeAt);
            endBuffers.remove(removeAt);
            if (editingRow == removeAt) editingRow = -1;
            if (pickerRow == removeAt) pickerRow = -1;
            changed(0);
        }
    }

    private boolean row(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        String info = rowInfo(b);
        ImGui.tableSetColumnIndex(0);
        Integer start = intCell("##start", b.startTick);
        TooltipUtil.onHover(info);
        ImGui.tableSetColumnIndex(1);
        Integer end = endCell(i, b);
        TooltipUtil.onHover(info);
        if (start != null || end != null) {
            int s = Math.max(1, start != null ? start : b.startTick);
            int e = end != null ? end : b.endTick;
            if (e != BlockOverride.NO_END && e < s) e = s;
            update(blocks, i, b.withTicks(s, e), b);
            b = blocks.get(i);
        }
        ImGui.tableSetColumnIndex(2);
        positionCell(blocks, i, info);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(3);
        blockCell(blocks, i, info);
        b = blocks.get(i);
        ImGui.tableSetColumnIndex(4);
        int clicked = SolverWidgets.segmented("##placedBy", PLACED_BY, PLACED_BY_TIPS, b.player ? 1 : 0,
                ImGui.getContentRegionAvailX());
        if (clicked >= 0 && (clicked == 1) != b.player) update(blocks, i, b.withPlayer(clicked == 1), b);
        ImGui.tableSetColumnIndex(5);
        boolean remove = SolverWidgets.deleteX("##delete");
        TooltipUtil.onHover(info);
        return remove;
    }

    private String rowInfo(BlockOverride b) {
        StringBuilder sb = new StringBuilder();
        String sim = world.simProblem(b);
        if (sim != null) sb.append(sim).append('\n');
        String replay = world.replayProblem(b);
        if (replay != null) sb.append(replay).append(" (replay)\n");
        sb.append("Before: ").append(displayName(b.before));
        return sb.toString();
    }

    private String displayName(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return spec.isEmpty() ? "?" : spec;
        return parsed.displayName(BlockOverride.Spec.parse(mc.defaultBlockState(parsed.id)));
    }

    private Integer intCell(String id, int value) {
        intHolder.set(value);
        ImGui.setNextItemWidth(-1f);
        Controls.pushInputFrameHeight();
        boolean changed = ImGui.inputInt(id, intHolder, 0, 0);
        Controls.popInputFrameHeight();
        return changed && intHolder.get() != value ? intHolder.get() : null;
    }

    private Integer endCell(int i, BlockOverride b) {
        ImString buf = endBuffers.get(i);
        if (!(editingRow == i && editingField == 1)) buf.set(b.endTick == BlockOverride.NO_END ? "" : String.valueOf(b.endTick));
        ImGui.setNextItemWidth(-1f);
        Controls.pushInputFrameHeight();
        ImGui.inputTextWithHint("##end", END_HINT, buf);
        Controls.popInputFrameHeight();
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

    private void positionCell(BlockOverrides blocks, int i, String info) {
        BlockOverride b = blocks.get(i);
        ImString buf = posBuffers.get(i);
        if (!(editingRow == i && editingField == 2)) buf.set(b.x + " " + b.y + " " + b.z);
        float h = ImGui.getFrameHeight();
        float gap = ImGui.getStyle().getItemSpacingX();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - h - gap);
        Controls.pushInputFrameHeight();
        ImGui.inputText("##pos", buf);
        Controls.popInputFrameHeight();
        TooltipUtil.onHover(info);
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
        ImGui.sameLine();
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
        boolean clicked = ImGui.invisibleButton(id, h, h);
        boolean hover = ImGui.isItemHovered();
        ImVec2 mn = ImGui.getItemRectMin();
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg = hover ? ThemeManager.accentTintColor(0.18f) : ThemeManager.panelColor();
        dl.addRectFilled(mn.x, mn.y, mn.x + h, mn.y + h, bg, ImGui.getStyle().getFrameRounding());
        int col = hover ? ThemeManager.textColor() : ThemeManager.textMutedColor();
        float cx = mn.x + h * 0.5f;
        float cy = mn.y + h * 0.5f;
        float r = h * 0.26f;
        float tick = 3f * scale;
        dl.addCircle(cx, cy, r, col, 16, 1.2f * scale);
        dl.addLine(cx - r - tick, cy, cx - r + tick, cy, col, 1.2f * scale);
        dl.addLine(cx + r - tick, cy, cx + r + tick, cy, col, 1.2f * scale);
        dl.addLine(cx, cy - r - tick, cx, cy - r + tick, col, 1.2f * scale);
        dl.addLine(cx, cy + r - tick, cx, cy + r + tick, col, 1.2f * scale);
        return clicked;
    }

    private void blockCell(BlockOverrides blocks, int i, String info) {
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
        dl.addRectFilled(mn.x, mn.y, mx.x, mx.y, bg, ImGui.getStyle().getFrameRounding());
        String error = errorOf(b.block);
        int textCol = error != null ? ThemeManager.dangerColor() : ImGui.getColorU32(ImGuiCol.Text);
        String label = error != null ? b.block : displayName(b.block);
        float padX = ImGui.getStyle().getFramePaddingX();
        float arrowW = h;
        dl.pushClipRect(mn.x, mn.y, mx.x - arrowW, mx.y, true);
        dl.addText(mn.x + padX, mn.y + (h - ImGui.getFontSize()) * 0.5f, textCol, label);
        dl.popClipRect();
        SolverWidgets.triangleDown(dl, mx.x - arrowW * 0.5f, mn.y + h * 0.5f, 3.3f * scale, ThemeManager.textMutedColor());
        if (hover) TooltipUtil.onHover((error != null ? error : b.block) + "\n" + info);
        if (clicked) {
            pickerRow = i;
            search.set("");
            ImGui.openPopup(PICKER_ID);
        }
        if (pickerRow == i) picker(blocks, i);
    }

    private void picker(BlockOverrides blocks, int i) {
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(PICKER_W * scale, 0f);
        if (!ImGui.beginPopup(PICKER_ID)) {
            pickerRow = -1;
            return;
        }
        BlockOverride b = blocks.get(i);
        if (ImGui.isWindowAppearing()) ImGui.setKeyboardFocusHere();
        Controls.inputTextHint("##search", "Search", search, -1f);
        String query = search.get().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        float rowH = ImGui.getTextLineHeightWithSpacing();
        ImGui.beginChild("##list", -1f, rowH * PICKER_ROWS, false);
        String held = mc.isReady() ? mc.getHeldBlock() : null;
        String looked = null;
        if (mc.isReady()) {
            BlockHit hit = mc.getLookedAtHit();
            if (hit != null) looked = mc.describeBlockState(hit.x, hit.y, hit.z);
        }
        String chosen = null;
        if (held != null && matches(held, query) && pickerEntry(displayName(held), "in hand", held.equals(b.block))) chosen = held;
        if (looked != null && matches(looked, query) && pickerEntry(displayName(looked), "looked at", looked.equals(b.block))) chosen = looked;
        int shown = 0;
        String currentId = b.spec().valid() ? b.spec().id : "";
        for (String id : mc.blockIds()) {
            if (!matches(id, query)) continue;
            if (shown++ >= PICKER_MAX_IDS) break;
            String def = mc.defaultBlockState(id);
            String spec = def != null ? def : id;
            if (pickerEntry(BlockOverride.Spec.parse(id).shortName(), null, id.equals(currentId))) chosen = spec;
        }
        ImGui.endChild();
        if (chosen != null) {
            update(blocks, i, b.withBlock(chosen), b);
            b = blocks.get(i);
        }
        states(blocks, i);
        ImGui.endPopup();
    }

    private static boolean matches(String spec, String query) {
        if (query.isEmpty()) return true;
        String id = BlockOverride.Spec.parse(spec).id;
        return id.contains(query);
    }

    private boolean pickerEntry(String label, String tag, boolean selected) {
        boolean clicked = ImGui.selectable(label, selected);
        if (tag != null) {
            float tagW = ImGui.calcTextSize(tag).x;
            ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - tagW);
            ThemeManager.pushTextColor(ThemeManager.textDimColor());
            ImGui.text(tag);
            ThemeManager.popTextColor();
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
        ImGui.spacing();
        for (Map.Entry<String, List<String>> e : options.entrySet()) {
            String key = e.getKey();
            String current = spec.properties.get(key);
            if (current == null && defaults.valid()) current = defaults.properties.get(key);
            if (current == null) current = "";
            ImGui.alignTextToFramePadding();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text(key);
            ThemeManager.popTextColor();
            ImGui.sameLine(PICKER_LABEL_W * scale);
            ImGui.setNextItemWidth(-1f);
            Controls.pushInputFrameHeight();
            if (ImGui.beginCombo("##" + key, current)) {
                for (String value : e.getValue()) {
                    if (ImGui.selectable(value, value.equals(current)) && !value.equals(current)) {
                        update(blocks, i, b.withBlock(spec.withProperty(key, value).text()), b);
                        b = blocks.get(i);
                        spec = b.spec();
                    }
                }
                ImGui.endCombo();
            }
            Controls.popInputFrameHeight();
        }
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
