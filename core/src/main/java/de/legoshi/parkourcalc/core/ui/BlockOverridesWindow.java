package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.BlockOverrideWorld;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class BlockOverridesWindow implements RenderInterface {

    private static final String WINDOW_ID = "Block Overrides";
    private static final String TABLE_ID = "##blockOverrides";
    private static final float WIN_W = 820f;
    private static final float WIN_H = 300f;
    private static final int BLOCK_CHARS = 256;
    private static final int TICK_CHARS = 8;
    private static final String END_HINT = "stays";
    private static final String[] HEADERS = {"Start", "End", "X", "Y", "Z", "Before", "Block", ""};
    private static final String HELP = "Each row is a block at X Y Z that appears at the Start tick and goes away after "
            + "the End tick (input table ticks, both included; an empty End means it stays). Before is the block that is "
            + "there otherwise, usually air. The world shows the rows of the selected tick as real blocks, the simulation "
            + "sees every tick's own state, and the replay places and removes them as it goes. Singleplayer only.";
    private static final String MULTIPLAYER_NOTE = "Block overrides work in singleplayer only. On a server the rows are "
            + "kept but the world, the simulation and the replay ignore them.";
    private static final String FOOTNOTE = "Rows ahead of the shown tick are dim; their blocks are not in the world yet.";

    private final InputData inputData;
    private final MinecraftAccess mc;
    private final Settings settings;
    private final BlockOverrideWorld world;
    private final Runnable onSettingsChanged;
    private final IntConsumer onChanged;
    private final ImBoolean open = new ImBoolean(false);
    private final ImInt intHolder = new ImInt();
    private final List<ImString> blockBuffers = new ArrayList<ImString>();
    private final List<ImString> beforeBuffers = new ArrayList<ImString>();
    private final List<ImString> endBuffers = new ArrayList<ImString>();
    private int editingRow = -1;
    private int editingField = -1;

    public BlockOverridesWindow(InputData inputData, MinecraftAccess mc, Settings settings, BlockOverrideWorld world,
                                Runnable onSettingsChanged, IntConsumer onChanged) {
        this.inputData = inputData;
        this.mc = mc;
        this.settings = settings;
        this.world = world;
        this.onSettingsChanged = onSettingsChanged;
        this.onChanged = onChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewBlockOverrides);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW_ID, open, ImGuiWindowFlags.NoCollapse)) {
            body();
        }
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
            if (Controls.primaryButton("Add block")) addBlock(blocks);
            TooltipUtil.onHover("Adds a block where a right click would place it on the face you look at, with the "
                    + "looked-at block's type, from the tick after the selected one. With nothing in reach it uses the "
                    + "block at your feet.");
        } else {
            Controls.disabledButton("Add block");
        }
        ImGui.sameLine();
        ImGui.textDisabled("(?)");
        TooltipUtil.onHover(HELP);
        if (available && !blocks.isEmpty()) {
            String shown = "World shows tick " + (world.viewRow() + 1);
            float w = ImGui.calcTextSize(shown).x;
            ImGui.sameLine(ImGui.getWindowContentRegionMaxX() - w);
            ImGui.text(shown);
        }
        ImGui.spacing();
        if (!available) {
            ThemeManager.pushTextColor(ThemeManager.warningColor());
            ImGui.textWrapped(MULTIPLAYER_NOTE);
            ThemeManager.popTextColor();
            ImGui.spacing();
        }
        if (blocks.isEmpty()) {
            ImGui.textDisabled("No block overrides.");
            return;
        }
        table(blocks);
        ImGui.textDisabled(FOOTNOTE);
    }

    private void addBlock(BlockOverrides blocks) {
        BlockHit hit = mc.isReady() ? mc.getLookedAtHit() : null;
        int[] pos;
        String block;
        if (hit != null) {
            pos = placementTarget(hit);
            String looked = mc.describeBlockState(hit.x, hit.y, hit.z);
            block = looked != null ? looked : BlockOverride.DEFAULT_BLOCK;
        } else {
            Vec3dCore p = mc.isReady() ? mc.getPlayerPosition() : Vec3dCore.ZERO;
            pos = new int[] {(int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)};
            block = BlockOverride.DEFAULT_BLOCK;
        }
        String before = mc.isReady() ? mc.describeBlockState(pos[0], pos[1], pos[2]) : null;
        if (before == null) before = BlockOverride.AIR;
        int start = Math.max(1, Math.min(world.viewRow() + 2, Math.max(1, inputData.size())));
        blocks.add(new BlockOverride(start, BlockOverride.NO_END, pos[0], pos[1], pos[2], block, before));
        changed(0);
    }

    private static int[] placementTarget(BlockHit hit) {
        int x = hit.x;
        int y = hit.y;
        int z = hit.z;
        if (hit.face == Face.POS_X) x++;
        if (hit.face == Face.NEG_X) x--;
        if (hit.face == Face.POS_Y) y++;
        if (hit.face == Face.NEG_Y) y--;
        if (hit.face == Face.POS_Z) z++;
        if (hit.face == Face.NEG_Z) z--;
        return new int[] {x, y, z};
    }

    private void table(BlockOverrides blocks) {
        syncBuffers(blocks);
        if (!ThemeManager.beginStandardTable(TABLE_ID, HEADERS.length)) return;
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float tickW = ImGui.calcTextSize("999999").x + 2f * ImGui.getStyle().getFramePaddingX();
        float coordW = ImGui.calcTextSize("-3000000").x + 2f * ImGui.getStyle().getFramePaddingX();
        ImGui.tableSetupColumn(HEADERS[0], fixed, tickW);
        ImGui.tableSetupColumn(HEADERS[1], fixed, tickW);
        ImGui.tableSetupColumn(HEADERS[2], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[3], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[4], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[5], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[6], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[7], fixed, actionsWidth());
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
            boolean dim = !blocks.get(i).activeAtRow(viewRow);
            if (dim) ThemeManager.pushTextColor(ThemeManager.textDimColor());
            boolean remove = row(blocks, i);
            if (dim) ThemeManager.popTextColor();
            if (remove) removeAt = i;
            ImGui.popID();
        }
        ThemeManager.endStandardTable();
        if (removeAt >= 0) {
            blocks.remove(removeAt);
            blockBuffers.remove(removeAt);
            beforeBuffers.remove(removeAt);
            endBuffers.remove(removeAt);
            if (editingRow == removeAt) editingRow = -1;
            changed(0);
        }
    }

    private boolean row(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        ImGui.tableSetColumnIndex(0);
        Integer start = intCell("##start", b.startTick);
        ImGui.tableSetColumnIndex(1);
        Integer end = endCell(i, b);
        ImGui.tableSetColumnIndex(2);
        Integer x = intCell("##x", b.x);
        ImGui.tableSetColumnIndex(3);
        Integer y = intCell("##y", b.y);
        ImGui.tableSetColumnIndex(4);
        Integer z = intCell("##z", b.z);
        if (start != null || end != null) {
            int s = Math.max(1, start != null ? start : b.startTick);
            int e = end != null ? end : b.endTick;
            if (e != BlockOverride.NO_END && e < s) e = s;
            update(blocks, i, b.withTicks(s, e), b);
        } else if (x != null || y != null || z != null) {
            update(blocks, i, b.withPos(x != null ? x : b.x, y != null ? y : b.y, z != null ? z : b.z), b);
        }
        ImGui.tableSetColumnIndex(5);
        specCell(blocks, i, 0);
        ImGui.tableSetColumnIndex(6);
        specCell(blocks, i, 1);
        ImGui.tableSetColumnIndex(7);
        return actions(blocks, i);
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
        if (!(editingRow == i && editingField == 2)) buf.set(b.endTick == BlockOverride.NO_END ? "" : String.valueOf(b.endTick));
        ImGui.setNextItemWidth(-1f);
        Controls.pushInputFrameHeight();
        ImGui.inputTextWithHint("##end", END_HINT, buf);
        Controls.popInputFrameHeight();
        TooltipUtil.onHover("Last tick the block is there. Leave it empty and the block stays until the end of the TAS.");
        if (ImGui.isItemActivated()) {
            editingRow = i;
            editingField = 2;
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

    private void specCell(BlockOverrides blocks, int i, int field) {
        BlockOverride b = blocks.get(i);
        boolean beforeField = field == 0;
        ImString buf = (beforeField ? beforeBuffers : blockBuffers).get(i);
        String value = beforeField ? b.before : b.block;
        if (!(editingRow == i && editingField == field)) buf.set(value);
        String error = errorOf(value);
        if (error != null) ThemeManager.pushTextColor(ThemeManager.dangerColor());
        ImGui.setNextItemWidth(-1f);
        Controls.pushInputFrameHeight();
        ImGui.inputTextWithHint(beforeField ? "##before" : "##block", beforeField ? BlockOverride.AIR : BlockOverride.DEFAULT_BLOCK, buf);
        Controls.popInputFrameHeight();
        if (error != null) ThemeManager.popTextColor();
        if (ImGui.isItemActivated()) {
            editingRow = i;
            editingField = field;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            editingRow = -1;
            update(blocks, i, beforeField ? b.withBefore(buf.get()) : b.withBlock(buf.get()), b);
        } else if (ImGui.isItemDeactivated()) {
            editingRow = -1;
        }
        String hint = beforeField
                ? "The block that is there outside the Start to End range, usually air."
                : "Block id with optional states, like minecraft:oak_door[open=true]. A missing state keeps the block's default.";
        TooltipUtil.onHover(error != null ? error : hint);
    }

    private boolean actions(BlockOverrides blocks, int i) {
        BlockOverride b = blocks.get(i);
        if (Controls.secondaryButton("Pick")) {
            BlockHit hit = mc.isReady() ? mc.getLookedAtHit() : null;
            if (hit != null) {
                String before = mc.describeBlockState(hit.x, hit.y, hit.z);
                BlockOverride next = b.withPos(hit.x, hit.y, hit.z);
                if (before != null) next = next.withBefore(before);
                update(blocks, i, next, b);
                b = blocks.get(i);
            }
        }
        TooltipUtil.onHover("Moves this row onto the block you look at and takes that block as Before.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Copy")) {
            String current = mc.isReady() ? mc.describeBlockState(b.x, b.y, b.z) : null;
            if (current != null) {
                if (editingRow == i) editingRow = -1;
                update(blocks, i, b.withBlock(current), b);
            }
        }
        TooltipUtil.onHover("Copies the block that is in the world at X Y Z with all its states into Block, so you only "
                + "have to change the state you need (open=true, powered=true, ...).");
        ImGui.sameLine();
        return Controls.dangerButton("X");
    }

    private float actionsWidth() {
        float gap = ImGui.getStyle().getItemSpacingX();
        return Controls.buttonWidth("Pick") + Controls.buttonWidth("Copy") + Controls.buttonWidth("X") + 2f * gap;
    }

    private String errorOf(String spec) {
        BlockOverride.Spec parsed = BlockOverride.Spec.parse(spec);
        if (!parsed.valid()) return parsed.error;
        return mc.blockOverrideError(spec);
    }

    private void update(BlockOverrides blocks, int i, BlockOverride next, BlockOverride prev) {
        if (next.equals(prev)) return;
        blocks.set(i, next);
        boolean ticksOnly = next.x == prev.x && next.y == prev.y && next.z == prev.z
                && next.block.equals(prev.block) && next.before.equals(prev.before);
        changed(ticksOnly ? Math.min(next.startTick, prev.startTick) - 1 : 0);
    }

    private void changed(int dirtyRow) {
        onChanged.accept(Math.max(0, dirtyRow));
    }

    private void syncBuffers(BlockOverrides blocks) {
        while (blockBuffers.size() < blocks.size()) blockBuffers.add(new ImString(BLOCK_CHARS));
        while (blockBuffers.size() > blocks.size()) blockBuffers.remove(blockBuffers.size() - 1);
        while (beforeBuffers.size() < blocks.size()) beforeBuffers.add(new ImString(BLOCK_CHARS));
        while (beforeBuffers.size() > blocks.size()) beforeBuffers.remove(beforeBuffers.size() - 1);
        while (endBuffers.size() < blocks.size()) endBuffers.add(new ImString(TICK_CHARS));
        while (endBuffers.size() > blocks.size()) endBuffers.remove(endBuffers.size() - 1);
        if (editingRow >= blocks.size()) editingRow = -1;
    }
}
