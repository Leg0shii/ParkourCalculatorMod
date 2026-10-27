package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.GhostBlock;
import de.legoshi.parkourcalc.core.sim.GhostBlocks;
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

public final class GhostBlocksWindow implements RenderInterface {

    private static final String WINDOW_ID = "Ghost Blocks";
    private static final String TABLE_ID = "##ghostBlocks";
    private static final float WIN_W = 640f;
    private static final float WIN_H = 260f;
    private static final int BLOCK_CHARS = 256;
    private static final String[] HEADERS = {"Start", "End", "X", "Y", "Z", "Block", ""};
    private static final String HELP = "Each row puts a block at X Y Z for the simulator and the multiplayer replay "
            + "from the start tick to the end tick (input table ticks, both included). The world itself is not changed. "
            + "Use it for redstone doors, trapdoors and pistons: air or an opened door while the redstone would be on.";

    private final InputData inputData;
    private final MinecraftAccess mc;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final IntConsumer onChanged;
    private final ImBoolean open = new ImBoolean(false);
    private final ImInt intHolder = new ImInt();
    private final List<ImString> blockBuffers = new ArrayList<ImString>();
    private int editingRow = -1;

    public GhostBlocksWindow(InputData inputData, MinecraftAccess mc, Settings settings, Runnable onSettingsChanged,
                             IntConsumer onChanged) {
        this.inputData = inputData;
        this.mc = mc;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
        this.onChanged = onChanged;
    }

    @Override
    public void render(ImGuiIO io) {
        open.set(settings.viewGhostBlocks);
        if (!open.get()) return;
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        if (ImGui.begin(WINDOW_ID, open, ImGuiWindowFlags.NoCollapse)) {
            body();
        }
        ImGui.end();
        if (settings.viewGhostBlocks != open.get()) {
            settings.viewGhostBlocks = open.get();
            onSettingsChanged.run();
        }
    }

    private void body() {
        GhostBlocks blocks = inputData.getGhostBlocks();
        if (Controls.primaryButton("Add block")) addBlock(blocks);
        TooltipUtil.onHover("Adds the block you look at, or the block at your feet, for every tick of the TAS.");
        ImGui.sameLine();
        ImGui.textDisabled("(?)");
        TooltipUtil.onHover(HELP);
        ImGui.spacing();
        if (blocks.isEmpty()) {
            ImGui.textDisabled("No ghost blocks.");
            return;
        }
        table(blocks);
    }

    private void addBlock(GhostBlocks blocks) {
        int[] pos = targetBlock();
        int end = Math.max(1, inputData.size());
        blocks.add(new GhostBlock(1, end, pos[0], pos[1], pos[2], GhostBlock.DEFAULT_BLOCK));
        changed(0);
    }

    private int[] targetBlock() {
        BlockHit hit = mc.isReady() ? mc.getLookedAtHit() : null;
        if (hit != null) return new int[] {hit.x, hit.y, hit.z};
        Vec3dCore p = mc.isReady() ? mc.getPlayerPosition() : Vec3dCore.ZERO;
        return new int[] {(int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)};
    }

    private void table(GhostBlocks blocks) {
        syncBuffers(blocks);
        if (!ThemeManager.beginStandardTable(TABLE_ID, HEADERS.length)) return;
        int fixed = ImGuiTableColumnFlags.WidthFixed;
        float tickW = ImGui.calcTextSize("999999").x + 2f * ImGui.getStyle().getFramePaddingX();
        float coordW = ImGui.calcTextSize("-30000000").x + 2f * ImGui.getStyle().getFramePaddingX();
        ImGui.tableSetupColumn(HEADERS[0], fixed, tickW);
        ImGui.tableSetupColumn(HEADERS[1], fixed, tickW);
        ImGui.tableSetupColumn(HEADERS[2], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[3], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[4], fixed, coordW);
        ImGui.tableSetupColumn(HEADERS[5], ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn(HEADERS[6], fixed, actionsWidth());
        ImGui.tableSetupScrollFreeze(0, 1);
        ThemeManager.tableHeaderRow();
        ThemeManager.paintTableHeader();
        for (int c = 0; c < HEADERS.length; c++) {
            ImGui.tableSetColumnIndex(c);
            ThemeManager.tableHeader(HEADERS[c]);
        }
        int removeAt = -1;
        for (int i = 0; i < blocks.size(); i++) {
            ImGui.tableNextRow();
            ImGui.pushID(i);
            if (row(blocks, i)) removeAt = i;
            ImGui.popID();
        }
        ThemeManager.endStandardTable();
        if (removeAt >= 0) {
            int dirty = blocks.get(removeAt).startTick - 1;
            blocks.remove(removeAt);
            blockBuffers.remove(removeAt);
            if (editingRow == removeAt) editingRow = -1;
            changed(dirty);
        }
    }

    private boolean row(GhostBlocks blocks, int i) {
        GhostBlock b = blocks.get(i);
        ImGui.tableSetColumnIndex(0);
        Integer start = intCell("##start", b.startTick);
        ImGui.tableSetColumnIndex(1);
        Integer end = intCell("##end", b.endTick);
        ImGui.tableSetColumnIndex(2);
        Integer x = intCell("##x", b.x);
        ImGui.tableSetColumnIndex(3);
        Integer y = intCell("##y", b.y);
        ImGui.tableSetColumnIndex(4);
        Integer z = intCell("##z", b.z);
        if (start != null || end != null) {
            int s = Math.max(1, start != null ? start : b.startTick);
            int e = Math.max(s, end != null ? end : b.endTick);
            update(blocks, i, b.withTicks(s, e), b);
        } else if (x != null || y != null || z != null) {
            update(blocks, i, b.withPos(x != null ? x : b.x, y != null ? y : b.y, z != null ? z : b.z), b);
        }
        ImGui.tableSetColumnIndex(5);
        blockCell(blocks, i);
        ImGui.tableSetColumnIndex(6);
        return actions(blocks, i);
    }

    private Integer intCell(String id, int value) {
        intHolder.set(value);
        ImGui.setNextItemWidth(-1f);
        boolean changed = ImGui.inputInt(id, intHolder, 0, 0);
        return changed && intHolder.get() != value ? intHolder.get() : null;
    }

    private void blockCell(GhostBlocks blocks, int i) {
        GhostBlock b = blocks.get(i);
        ImString buf = blockBuffers.get(i);
        if (editingRow != i) buf.set(b.block);
        String error = errorOf(b.block);
        if (error != null) ThemeManager.pushTextColor(ThemeManager.dangerColor());
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##block", GhostBlock.DEFAULT_BLOCK, buf);
        if (error != null) ThemeManager.popTextColor();
        if (ImGui.isItemActivated()) editingRow = i;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            editingRow = -1;
            update(blocks, i, b.withBlock(buf.get()), b);
        } else if (ImGui.isItemDeactivated()) {
            editingRow = -1;
        }
        TooltipUtil.onHover(error != null ? error
                : "Block id with optional states, like minecraft:oak_door[open=true]. A missing state keeps the block's default.");
    }

    private boolean actions(GhostBlocks blocks, int i) {
        GhostBlock b = blocks.get(i);
        if (Controls.secondaryButton("Pick")) {
            int[] pos = targetBlock();
            update(blocks, i, b.withPos(pos[0], pos[1], pos[2]), b);
            b = blocks.get(i);
        }
        TooltipUtil.onHover("Moves this row to the block you look at.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Copy")) {
            String current = mc.isReady() ? mc.describeBlockState(b.x, b.y, b.z) : null;
            if (current != null) {
                if (editingRow == i) editingRow = -1;
                update(blocks, i, b.withBlock(current), b);
            }
        }
        TooltipUtil.onHover("Copies the block that is in the world at X Y Z with all its states, so you only have to "
                + "change the state you need (open=true, powered=true, ...).");
        ImGui.sameLine();
        return Controls.dangerButton("X");
    }

    private float actionsWidth() {
        float gap = ImGui.getStyle().getItemSpacingX();
        return Controls.buttonWidth("Pick") + Controls.buttonWidth("Copy") + Controls.buttonWidth("X") + 2f * gap;
    }

    private String errorOf(String spec) {
        GhostBlock.Spec parsed = GhostBlock.Spec.parse(spec);
        if (!parsed.valid()) return parsed.error;
        return mc.ghostBlockError(spec);
    }

    private void update(GhostBlocks blocks, int i, GhostBlock next, GhostBlock prev) {
        if (next.equals(prev)) return;
        blocks.set(i, next);
        changed(Math.min(next.startTick, prev.startTick) - 1);
    }

    private void changed(int dirtyRow) {
        onChanged.accept(Math.max(0, dirtyRow));
    }

    private void syncBuffers(GhostBlocks blocks) {
        while (blockBuffers.size() < blocks.size()) blockBuffers.add(new ImString(BLOCK_CHARS));
        while (blockBuffers.size() > blocks.size()) blockBuffers.remove(blockBuffers.size() - 1);
        if (editingRow >= blocks.size()) editingRow = -1;
    }
}
