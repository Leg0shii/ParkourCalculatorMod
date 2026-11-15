package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.BlockOverrideOutlines;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxSelectController;
import de.legoshi.parkourcalc.core.ui.WorldPick;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlockOverridesTest {

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) data.getRows().add(new InputRow());
        return data;
    }

    private static final class FakePatcher implements BlockPatcher {
        final List<List<BlockPatch>> calls = new ArrayList<List<BlockPatch>>();
        boolean available = true;
        boolean complete = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean apply(List<BlockPatch> patches) {
            calls.add(new ArrayList<BlockPatch>(patches));
            return complete;
        }

        List<BlockPatch> last() {
            return calls.isEmpty() ? Collections.<BlockPatch>emptyList() : calls.get(calls.size() - 1);
        }
    }

    private static BlockPatch patch(int x, int y, int z, String block) {
        return new BlockPatch(x, y, z, block);
    }

    @Test
    public void specParsesIdAndStates() {
        BlockOverride.Spec s = BlockOverride.Spec.parse(" Oak_Door[open=true, half=lower] ");
        assertTrue(s.valid());
        assertEquals("minecraft:oak_door", s.id);
        assertEquals("true", s.properties.get("open"));
        assertEquals("lower", s.properties.get("half"));
        assertEquals("minecraft:oak_door[open=true,half=lower]", BlockOverride.Spec.format(s.id, s.properties));
        assertEquals("minecraft:air", BlockOverride.Spec.parse("air").id);
        assertFalse(BlockOverride.Spec.parse("").valid());
        assertFalse(BlockOverride.Spec.parse("oak_door[open=true").valid());
        assertFalse(BlockOverride.Spec.parse("oak_door[open]").valid());
    }

    @Test
    public void activeTicksAreOneBasedInclusiveAndNoEndMeansStays() {
        BlockOverride b = new BlockOverride(3, 5, 0, 64, 0, "stone", "air");
        assertFalse(b.activeAtRow(1));
        assertTrue(b.activeAtRow(2));
        assertTrue(b.activeAtRow(4));
        assertFalse(b.activeAtRow(5));
        assertTrue(b.aheadOfRow(0));
        assertFalse(b.aheadOfRow(2));
        BlockOverride stays = b.withTicks(3, BlockOverride.NO_END);
        assertTrue(stays.activeAtRow(500));
        assertEquals(patch(0, 64, 0, "stone"), b.patchAtRow(2));
        assertEquals(patch(0, 64, 0, "air"), b.patchAtRow(1));
        assertNull(b.withBefore("").patchAtRow(1));
    }

    @Test
    public void simulatorSeesEachTicksOwnStateAndNothingAfterThePass() {
        InputData data = rows(6);
        BlockOverride placed = new BlockOverride(2, 3, 1, 64, 1, "stone", "air");
        BlockOverride bad = new BlockOverride(1, 6, 2, 64, 2, "[", "air");
        data.getBlockOverrides().add(placed);
        data.getBlockOverrides().add(bad);
        final List<List<BlockPatch>> perTick = new ArrayList<List<BlockPatch>>();
        final List<BlockPatch>[] current = new List[] {Collections.emptyList()};
        FakeSimulator sim = new FakeSimulator() {
            @Override
            public void setBlockPatches(List<BlockPatch> patches) {
                current[0] = patches;
            }

            @Override
            public void tick() {
                perTick.add(current[0]);
            }
        };
        SimulationRunner runner = new SimulationRunner(sim);
        runner.simulate(data);
        List<BlockPatch> air = Collections.singletonList(patch(1, 64, 1, "air"));
        List<BlockPatch> stone = Collections.singletonList(patch(1, 64, 1, "stone"));
        assertEquals(Arrays.asList(air, stone, stone, air, air, air), perTick);
        assertTrue(current[0].isEmpty());

        perTick.clear();
        runner.simulateFrom(2, data);
        assertEquals(Arrays.asList(stone, air, air, air), perTick);
        assertTrue(current[0].isEmpty());
    }

    @Test
    public void worldFollowsTheSelectedTickAndRestoresMovedRows() {
        InputData data = rows(10);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        data.getBlockOverrides().add(new BlockOverride(4, BlockOverride.NO_END, 1, 64, 1, "stone", "air"));
        data.getBlockOverrides().add(new BlockOverride(7, 8, 5, 64, 5, "oak_door[open=true]", "oak_door[open=false]"));

        world.tick();
        assertEquals(0, world.viewRow());
        assertEquals(Arrays.asList(patch(1, 64, 1, "air"), patch(5, 64, 5, "oak_door[open=false]")), patcher.last());

        selection.selectOnly(10);
        world.tick();
        assertEquals(9, world.viewRow());
        assertEquals(Arrays.asList(patch(1, 64, 1, "stone"), patch(5, 64, 5, "oak_door[open=false]")), patcher.last());

        selection.selectOnly(2);
        world.tick();
        assertEquals(1, world.viewRow());
        assertEquals(Arrays.asList(patch(1, 64, 1, "air"), patch(5, 64, 5, "oak_door[open=false]")), patcher.last());

        int calls = patcher.calls.size();
        world.tick();
        assertEquals(calls, patcher.calls.size());

        selection.selectOnly(7);
        world.tick();
        assertEquals(Arrays.asList(patch(1, 64, 1, "stone"), patch(5, 64, 5, "oak_door[open=true]")), patcher.last());

        data.getBlockOverrides().set(0, data.getBlockOverrides().get(0).withPos(2, 64, 2));
        world.tick();
        assertEquals(Arrays.asList(patch(1, 64, 1, "air"), patch(2, 64, 2, "stone"), patch(5, 64, 5, "oak_door[open=true]")),
                patcher.last());

        data.getBlockOverrides().clear();
        world.tick();
        assertEquals(Arrays.asList(patch(2, 64, 2, "air"), patch(5, 64, 5, "oak_door[open=false]")), patcher.last());
        calls = patcher.calls.size();
        world.releaseAll();
        assertEquals(calls, patcher.calls.size());
    }

    @Test
    public void replayStepsTheWorldThroughTheTicksAndReturnsToTheSelection() {
        InputData data = rows(4);
        BlockOverride door = new BlockOverride(2, 2, 1, 64, 1, "air", "stone");
        data.getBlockOverrides().add(door);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        selection.selectOnly(1);
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        world.tick();
        assertEquals(Collections.singletonList(patch(1, 64, 1, "stone")), patcher.last());

        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        pc.setOnReplayRow(world::setReplayRow);
        pc.start(0, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, new de.legoshi.parkourcalc.core.sim.Checkpoint() {
        });
        assertTrue(world.isReplaying());
        settings.viewBlockOverrides = true;
        assertFalse(BlockOverrideOutlines.shouldRender(settings, world));
        settings.blockOverrideOutlinesDuringReplay = true;
        assertTrue(BlockOverrideOutlines.shouldRender(settings, world));
        settings.blockOverrideOutlinesDuringReplay = false;
        assertEquals(0, world.viewRow());
        pc.tick();
        assertEquals(0, world.viewRow());
        pc.tick();
        assertEquals(1, world.viewRow());
        assertEquals(Collections.singletonList(patch(1, 64, 1, "air")), patcher.last());
        pc.tick();
        assertEquals(2, world.viewRow());
        assertEquals(Collections.singletonList(patch(1, 64, 1, "stone")), patcher.last());
        pc.stop();
        assertFalse(world.isReplaying());
        assertTrue(BlockOverrideOutlines.shouldRender(settings, world));
        assertEquals(0, world.viewRow());
        assertEquals(Collections.singletonList(patch(1, 64, 1, "stone")), patcher.last());
    }

    @Test
    public void tableStaysSortedByStartAndKeepsTheSelectedRow() {
        InputData data = rows(40);
        BlockOverrides overrides = data.getBlockOverrides();
        BlockOverride late = new BlockOverride(30, BlockOverride.NO_END, 1, 64, 1, "stone", "air");
        BlockOverride early = new BlockOverride(3, BlockOverride.NO_END, 2, 64, 2, "stone", "air");
        BlockOverride mid = new BlockOverride(10, BlockOverride.NO_END, 3, 64, 3, "stone", "air");
        overrides.add(late);
        overrides.add(early);
        overrides.add(mid);
        assertEquals(Arrays.asList(early, mid, late), overrides.all());
        BlockOverride moved = mid.withTicks(35, BlockOverride.NO_END);
        overrides.set(1, moved);
        assertEquals(Arrays.asList(early, late, moved), overrides.all());
        assertEquals(Arrays.asList(early, late, moved), BlockOverrides.sorted(Arrays.asList(moved, late, early)));

        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        selection.selectOnly(1);
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        assertNull(world.selected());
        world.select(late);
        assertTrue(world.isSelected(late));
        assertEquals(late, world.rowAt(1, 64, 1));
        assertNull(world.rowAt(9, 9, 9));
        overrides.remove(overrides.indexOf(late));
        assertNull(world.selected());

        overrides.add(new BlockOverride(3, 5, 2, 64, 2, "dirt", "air"));
        selection.selectOnly(4);
        assertEquals("dirt", world.rowAt(2, 64, 2).block);
        selection.selectOnly(8);
        assertEquals(early, world.rowAt(2, 64, 2));
    }

    @Test
    public void aRayPicksTheNearestOverridePositionEvenWhenItIsAir() {
        InputData data = new InputData();
        data.addRows(0, 10);
        BlockOverrides overrides = data.getBlockOverrides();
        BlockOverride removed = new BlockOverride(2, 4, 5, 64, 0, "air", "stone");
        BlockOverride far = new BlockOverride(1, BlockOverride.NO_END, 8, 64, 0, "dirt", "air");
        overrides.add(removed);
        overrides.add(far);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        selection.selectOnly(2);
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        Vec3dCore eye = new Vec3dCore(0.5, 64.5, 0.5);
        Vec3dCore east = new Vec3dCore(1, 0, 0);
        assertEquals(removed, world.rowAlong(eye, east, 6.0));
        assertEquals(4.5, world.distanceAlong(eye, east, removed, 6.0), 1e-9);
        assertEquals(far, world.rowAlong(new Vec3dCore(6.5, 64.5, 0.5), east, 6.0));
        assertNull(world.rowAlong(eye, east, 4.0));
        assertNull(world.rowAlong(eye, new Vec3dCore(0, 0, 1), 6.0));
    }

    @Test
    public void theWorldPickCommitsOnThePressEvenWhileTheMouseMoves() {
        final List<WorldPick> picks = new ArrayList<WorldPick>();
        final int[] pickCalls = {0};
        BoxSelectController controller = new BoxSelectController((o, d) -> {
            pickCalls[0]++;
            return WorldPick.blockOverride(2);
        }, picks::add, true);
        Vec3dCore eye = new Vec3dCore(0, 64, 0);
        Vec3dCore dir = new Vec3dCore(1, 0, 0);
        controller.tick(eye, dir, false, 0, 0, false);
        controller.tick(eye, dir, true, 0, 0, false);
        assertEquals(1, picks.size());
        assertEquals(2, picks.get(0).index);
        controller.tick(eye, dir, true, 40, 30, false);
        controller.tick(eye, dir, false, 80, 60, false);
        assertEquals(1, picks.size());
        assertEquals(1, pickCalls[0]);
        controller.tick(eye, dir, true, 80, 60, true);
        assertEquals(1, picks.size());
    }

    @Test
    public void rowsOnTheSamePositionResolveToOneBlockPerTick() {
        BlockOverrides overrides = new BlockOverrides();
        overrides.add(new BlockOverride(15, BlockOverride.NO_END, 36, -60, 9, "acacia_trapdoor[half=top]", "air"));
        overrides.add(new BlockOverride(30, BlockOverride.NO_END, 36, -60, 9, "acacia_trapdoor[half=top,open=true]", "acacia_trapdoor[half=top]"));
        assertEquals(Collections.singletonList(patch(36, -60, 9, "air")), overrides.patchesAtRow(0));
        assertEquals(Collections.singletonList(patch(36, -60, 9, "acacia_trapdoor[half=top]")), overrides.patchesAtRow(14));
        assertEquals(Collections.singletonList(patch(36, -60, 9, "acacia_trapdoor[half=top,open=true]")), overrides.patchesAtRow(29));
        assertEquals(1, overrides.positionCount());
        assertEquals("air", overrides.basesByPosition().values().iterator().next().before);

        InputData data = rows(40);
        data.getBlockOverrides().replaceAll(overrides.all());
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        selection.selectOnly(1);
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        world.tick();
        assertEquals(Collections.singletonList(patch(36, -60, 9, "air")), patcher.last());
        data.getBlockOverrides().clear();
        world.tick();
        assertEquals(Collections.singletonList(patch(36, -60, 9, "air")), patcher.last());
    }

    @Test
    public void playerRowsAreLeftToThePlayerDuringTheReplayAndCheckedAfterTheirTick() {
        InputData data = rows(6);
        BlockOverride placed = new BlockOverride(3, BlockOverride.NO_END, 1, 64, 1, "stone", "air", true);
        data.getBlockOverrides().add(placed);
        data.getBlockOverrides().add(new BlockOverride(4, BlockOverride.NO_END, 2, 64, 2, "stone", "air"));
        assertEquals(Collections.singletonList(patch(2, 64, 2, "air")), data.getBlockOverrides().patchesAtRow(0, false));
        assertEquals(Arrays.asList(patch(1, 64, 1, "stone"), patch(2, 64, 2, "stone")), data.getBlockOverrides().patchesAtRow(5));

        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        selection.selectOnly(5);
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        final String[] worldBlock = {"minecraft:air"};
        world.setBlockReader((x, y, z) -> x == 1 && y == 64 && z == 1 ? worldBlock[0] : "minecraft:air");
        final List<String> warnings = new ArrayList<String>();
        world.setOnDesync((row, tick, expectedPlaced) -> warnings.add(tick + ":" + row.x + ":" + expectedPlaced));
        world.tick();
        assertEquals(Arrays.asList(patch(1, 64, 1, "stone"), patch(2, 64, 2, "stone")), patcher.last());

        world.setReplayRow(0);
        assertEquals(Arrays.asList(patch(1, 64, 1, "air"), patch(2, 64, 2, "air")), patcher.last());
        world.tick();
        world.setReplayRow(1);
        world.tick();
        assertTrue(warnings.isEmpty());
        world.setReplayRow(2);
        world.tick();
        assertEquals(Collections.singletonList(patch(2, 64, 2, "air")), patcher.last());
        assertTrue(warnings.isEmpty());
        world.setReplayRow(3);
        world.tick();
        assertEquals(Collections.singletonList(patch(2, 64, 2, "stone")), patcher.last());
        assertEquals(Collections.singletonList("3:1:true"), warnings);
        assertTrue(world.isMissing(placed));
        world.setReplayRow(4);
        world.tick();
        assertEquals(1, warnings.size());

        world.setReplayRow(-1);
        assertFalse(world.isMissing(placed));
        assertEquals(Arrays.asList(patch(1, 64, 1, "stone"), patch(2, 64, 2, "stone")), patcher.last());

        worldBlock[0] = "minecraft:stone[extra=yes]";
        world.setReplayRow(0);
        world.setReplayRow(1);
        world.setReplayRow(2);
        world.setReplayRow(3);
        world.tick();
        assertTrue(warnings.size() == 1);
    }

    @Test
    public void nothingIsPlacedWithoutAnIntegratedServer() {
        InputData data = rows(3);
        data.getBlockOverrides().add(new BlockOverride(1, BlockOverride.NO_END, 1, 64, 1, "stone", "air"));
        BlockOverrideWorld world = new BlockOverrideWorld(data, new SelectionManager(new FakeMinecraftAccess()));
        FakePatcher patcher = new FakePatcher();
        patcher.available = false;
        world.setPatcher(patcher);
        world.tick();
        world.releaseAll();
        assertTrue(patcher.calls.isEmpty());
        assertFalse(world.isAvailable());
    }

    @Test
    public void blockOverridesRoundTripThroughTheSaveAndChangeTheUndoSignature() throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(Files.createTempDirectory("pkc-overrides"), "test", "1.8.9", () -> null);
        InputData in = rows(3);
        String before = SaveIO.undoSignature(in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null);
        in.getBlockOverrides().add(new BlockOverride(1, 3, -5, 70, 12, "minecraft:oak_trapdoor[open=true]", "minecraft:oak_trapdoor[open=false]"));
        in.getBlockOverrides().add(new BlockOverride(2, BlockOverride.NO_END, -5, 71, 12, "minecraft:stone", "", true));
        String after = SaveIO.undoSignature(in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null);
        assertNotEquals(before, after);

        Result<String> saved = SaveIO.save(store, "run", in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, PlaybackController.DEFAULT_PITCH, null, null, false);
        assertTrue(saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue(loaded.ok);
        InputData out = new InputData();
        SaveIO.applyBlockOverridesTo(loaded.value, out);
        assertEquals(in.getBlockOverrides().all(), out.getBlockOverrides().all());
        assertTrue(SaveIO.blockOverridesMatch(loaded.value, in));

        InputData empty = rows(1);
        Result<String> plain = SaveIO.save(store, "plain", empty, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, PlaybackController.DEFAULT_PITCH, null, null, false);
        assertTrue(plain.ok);
        SaveFile plainFile = SaveIO.load(store, "plain").value;
        assertNull(plainFile.blockOverrides);
        SaveIO.applyBlockOverridesTo(plainFile, out);
        assertTrue(out.getBlockOverrides().isEmpty());
    }

    @Test
    public void outlinesFollowTheBlockShape() {
        InputData data = rows(10);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        world.setPatcher(new FakePatcher());
        world.setShapeSource(new BlockOverrideWorld.ShapeSource() {
            @Override
            public List<AABB> shape(String spec) {
                if (spec.startsWith("player_head")) {
                    return Collections.singletonList(new AABB(new Vec3dCore(0.25, 0, 0.25), new Vec3dCore(0.75, 0.5, 0.75)));
                }
                return Collections.<AABB>emptyList();
            }
        });
        data.getBlockOverrides().add(new BlockOverride(1, BlockOverride.NO_END, 10, 64, 10, "player_head", "air"));
        data.getBlockOverrides().add(new BlockOverride(1, BlockOverride.NO_END, 20, 64, 20, "stone", "air"));
        Settings settings = new Settings();
        settings.viewBlockOverrides = true;
        final List<AABB> drawn = new ArrayList<AABB>();
        BlockOverrideOutlines.render(new BoxRenderer() {
            @Override
            public void drawBox(AABB box, int argb) {
                drawn.add(box);
            }

            @Override
            public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            }

            @Override
            public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2,
                                     double x3, double y3, double z3, int argb) {
            }
        }, settings, world);

        double headTop = Double.NEGATIVE_INFINITY;
        double stoneTop = Double.NEGATIVE_INFINITY;
        for (AABB box : drawn) {
            if (box.min.x >= 10 && box.max.x <= 11) headTop = Math.max(headTop, box.max.y);
            if (box.min.x >= 19.9 && box.max.x <= 21.1) stoneTop = Math.max(stoneTop, box.max.y);
        }
        assertEquals(64.5, headTop, 0.05);
        assertEquals(65, stoneTop, 0.05);
    }

    @Test
    public void worldRetriesUntilTheChunkTookThePatchesAndNamesTheBlockItReplaces() {
        InputData data = rows(10);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        patcher.complete = false;
        world.setPatcher(patcher);
        data.getBlockOverrides().add(new BlockOverride(4, 6, 1, 64, 1, "creeper_wall_head[facing=south]", "air"));

        world.tick();
        world.tick();
        assertEquals(2, patcher.calls.size());
        assertEquals(patch(1, 64, 1, "air"), patcher.last().get(0));
        assertEquals("creeper_wall_head[facing=south]", patcher.last().get(0).replaces);

        patcher.complete = true;
        world.tick();
        assertEquals(3, patcher.calls.size());
        world.tick();
        assertEquals(3, patcher.calls.size());

        selection.selectOnly(5);
        world.tick();
        assertEquals(patch(1, 64, 1, "creeper_wall_head[facing=south]"), patcher.last().get(0));
        assertEquals("air", patcher.last().get(0).replaces);
    }

    @Test
    public void outlinesAreDashedOnEveryRowNotActiveAtTheViewTick() {
        InputData data = rows(30);
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        world.setPatcher(new FakePatcher());
        data.getBlockOverrides().add(new BlockOverride(5, 16, 10, 64, 10, "stone", "air"));
        Settings settings = new Settings();
        settings.viewBlockOverrides = true;
        selection.selectOnly(17);
        final List<AABB> drawn = new ArrayList<AABB>();
        BlockOverrideOutlines.render(new BoxRenderer() {
            @Override
            public void drawBox(AABB box, int argb) {
                drawn.add(box);
            }

            @Override
            public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            }

            @Override
            public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2,
                                     double x3, double y3, double z3, int argb) {
            }
        }, settings, world);
        assertEquals(17, world.viewRow() + 1);
        assertEquals(12 * 4, drawn.size());
    }

    private static final String OPEN = "minecraft:trapdoor[facing=east,half=bottom,open=true]";
    private static final String CLOSED = "minecraft:trapdoor[facing=east,half=bottom,open=false]";

    @Test
    public void anAutoRowLeavesTheBlockAloneOnceAPlayerRowHasChangedIt() {
        InputData data = rows(40);
        BlockOverrides overrides = data.getBlockOverrides();
        overrides.add(new BlockOverride(1, BlockOverride.NO_END, 5, 36, 5, OPEN, OPEN));
        overrides.add(new BlockOverride(20, BlockOverride.NO_END, 5, 36, 5, CLOSED, OPEN, true));
        overrides.add(new BlockOverride(26, BlockOverride.NO_END, 5, 36, 5, OPEN, CLOSED, true));
        List<BlockPatch> open = Collections.singletonList(patch(5, 36, 5, OPEN));
        List<BlockPatch> closed = Collections.singletonList(patch(5, 36, 5, CLOSED));
        assertEquals(open, overrides.patchesAtRow(18));
        assertEquals(closed, overrides.patchesAtRow(19));
        assertEquals(closed, overrides.patchesAtRow(24));
        assertEquals(open, overrides.patchesAtRow(25));
        assertEquals(open, overrides.patchesAtRow(0, false));
        assertEquals(open, overrides.patchesAtRow(18, false));
        assertTrue(overrides.patchesAtRow(19, false).isEmpty());
        assertTrue(overrides.patchesAtRow(25, false).isEmpty());
        assertTrue(overrides.patchesAtRow(39, false).isEmpty());

        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        final String[] worldBlock = {OPEN};
        world.setBlockReader((x, y, z) -> worldBlock[0]);
        final List<String> warnings = new ArrayList<String>();
        world.setOnDesync((row, tick, expectedPlaced) -> warnings.add(tick + ":" + expectedPlaced));
        world.setReplayRow(17);
        world.tick();
        world.setReplayRow(18);
        world.tick();
        assertEquals(open, patcher.last());
        int calls = patcher.calls.size();
        world.setReplayRow(19);
        world.tick();
        worldBlock[0] = CLOSED;
        world.setReplayRow(20);
        world.tick();
        world.setReplayRow(21);
        world.tick();
        assertEquals(calls, patcher.calls.size());
        assertTrue(warnings.isEmpty());
        world.setReplayRow(25);
        world.tick();
        worldBlock[0] = OPEN;
        for (int row = 26; row < 32; row++) {
            world.setReplayRow(row);
            world.tick();
        }
        assertEquals(calls, patcher.calls.size());
        assertTrue(warnings.isEmpty());
        assertFalse(world.hasProblem(overrides.get(1)));
        assertFalse(world.hasProblem(overrides.get(2)));
    }

    @Test
    public void aMissedClickOnAChainOfPlayerRowsIsReportedOnceOnTheRowThatMissed() {
        InputData data = rows(40);
        BlockOverrides overrides = data.getBlockOverrides();
        overrides.add(new BlockOverride(1, BlockOverride.NO_END, 5, 36, 5, OPEN, OPEN));
        overrides.add(new BlockOverride(20, BlockOverride.NO_END, 5, 36, 5, CLOSED, OPEN, true));
        overrides.add(new BlockOverride(26, 30, 5, 36, 5, OPEN, CLOSED, true));
        BlockOverride second = overrides.get(2);
        assertTrue(overrides.playerChangesAtRow(18).isEmpty());
        assertEquals(Collections.singletonMap(overrides.get(1), patch(5, 36, 5, CLOSED)), overrides.playerChangesAtRow(24));
        assertEquals(Collections.singletonMap(second, patch(5, 36, 5, OPEN)), overrides.playerChangesAtRow(25));
        assertEquals(Collections.singletonMap(second, patch(5, 36, 5, CLOSED)), overrides.playerChangesAtRow(30));

        BlockOverrideWorld world = new BlockOverrideWorld(data, new SelectionManager(new FakeMinecraftAccess()));
        world.setPatcher(new FakePatcher());
        final String[] worldBlock = {OPEN};
        world.setBlockReader((x, y, z) -> worldBlock[0]);
        final List<String> warnings = new ArrayList<String>();
        world.setOnDesync((row, tick, expectedPlaced) -> warnings.add(tick + ":" + row.startTick + ":" + expectedPlaced));
        world.setReplayRow(18);
        world.tick();
        world.setReplayRow(19);
        world.tick();
        worldBlock[0] = CLOSED;
        for (int row = 20; row < 29; row++) {
            world.setReplayRow(row);
            world.tick();
        }
        assertEquals(Collections.singletonList("26:26:true"), warnings);
        assertTrue(world.isMissing(second));
    }

    @Test
    public void aPlayerRowThatEndsIsNotPutBackByAnAutoRowButAnAutoRowThatEndsIs() {
        BlockOverrides overrides = new BlockOverrides();
        overrides.add(new BlockOverride(1, BlockOverride.NO_END, 5, 36, 5, OPEN, OPEN));
        overrides.add(new BlockOverride(20, 25, 5, 36, 5, CLOSED, OPEN, true));
        assertTrue(overrides.patchesAtRow(24, false).isEmpty());
        assertTrue(overrides.patchesAtRow(25, false).isEmpty());
        assertEquals(Collections.singletonList(patch(5, 36, 5, OPEN)), overrides.patchesAtRow(25));

        BlockOverrides auto = new BlockOverrides();
        auto.add(new BlockOverride(10, BlockOverride.NO_END, 5, 36, 5, CLOSED, OPEN, true));
        auto.add(new BlockOverride(20, 25, 5, 36, 5, OPEN, CLOSED));
        assertTrue(auto.patchesAtRow(8, false).isEmpty());
        assertTrue(auto.patchesAtRow(15, false).isEmpty());
        assertEquals(Collections.singletonList(patch(5, 36, 5, OPEN)), auto.patchesAtRow(19, false));
        assertEquals(Collections.singletonList(patch(5, 36, 5, CLOSED)), auto.patchesAtRow(25, false));
    }

    @Test
    public void aReplayThatStartsOnAClickTickShowsTheBlockBeforeTheClick() {
        InputData data = rows(40);
        data.getBlockOverrides().add(new BlockOverride(20, BlockOverride.NO_END, 5, 36, 5, CLOSED, OPEN, true));
        data.getBlockOverrides().add(new BlockOverride(20, BlockOverride.NO_END, 6, 36, 5, "stone", "air"));
        SelectionManager selection = new SelectionManager(new FakeMinecraftAccess());
        BlockOverrideWorld world = new BlockOverrideWorld(data, selection);
        FakePatcher patcher = new FakePatcher();
        world.setPatcher(patcher);
        selection.selectOnly(20);
        world.tick();
        assertEquals(Arrays.asList(patch(5, 36, 5, CLOSED), patch(6, 36, 5, "stone")), patcher.last());
        world.setReplayRow(19);
        assertEquals(Arrays.asList(patch(5, 36, 5, OPEN), patch(6, 36, 5, "stone")), patcher.last());
        world.setReplayRow(-1);
        world.setReplayRow(20);
        assertEquals(Arrays.asList(patch(5, 36, 5, CLOSED), patch(6, 36, 5, "stone")), patcher.last());
    }

    private static BlockPatch rowByRow(List<BlockOverride> all, int x, int y, int z, int row) {
        BlockOverride base = null;
        BlockOverride active = null;
        for (BlockOverride b : all) {
            if (b.x != x || b.y != y || b.z != z) continue;
            if (base == null || b.startTick < base.startTick) base = b;
            if (b.activeAtRow(row) && (active == null || b.startTick >= active.startTick)) active = b;
        }
        if (base == null) return null;
        String spec = active != null ? active.block : base.before;
        return BlockOverride.Spec.parse(spec).valid() ? patch(x, y, z, spec) : null;
    }

    @Test
    public void theCachedTimelineMatchesTheRowByRowResolution() {
        java.util.Random random = new java.util.Random(20261110L);
        String[] specs = {"stone", "air", OPEN, CLOSED, "["};
        for (int round = 0; round < 200; round++) {
            BlockOverrides overrides = new BlockOverrides();
            int count = 1 + random.nextInt(8);
            for (int i = 0; i < count; i++) {
                int start = 1 + random.nextInt(12);
                int end = random.nextInt(3) == 0 ? BlockOverride.NO_END : start + random.nextInt(6);
                overrides.add(new BlockOverride(start, end, random.nextInt(3), 64, 0,
                        specs[random.nextInt(specs.length)], specs[random.nextInt(specs.length)], random.nextBoolean()));
            }
            for (int row = -2; row < 24; row++) {
                List<BlockPatch> expected = new ArrayList<BlockPatch>();
                for (int x = 0; x < 3; x++) {
                    BlockPatch p = rowByRow(overrides.all(), x, 64, 0, row);
                    if (p != null) expected.add(p);
                }
                List<BlockPatch> actual = overrides.patchesAtRow(row);
                assertEquals(expected.size(), actual.size());
                assertTrue(actual.containsAll(expected));
                for (int x = 0; x < 3; x++) {
                    assertEquals(rowByRow(overrides.all(), x, 64, 0, row), patchAt(overrides, x, 64, 0, row));
                }
            }
            assertTrue(overrides.patchesAtRow(5) == overrides.patchesAtRow(5));
        }
    }

    @Test
    public void anEditReportsTheFirstTickWhoseBlocksChange() {
        BlockOverride door = new BlockOverride(10, 20, 1, 64, 1, "stone", "air");
        BlockOverride other = new BlockOverride(30, BlockOverride.NO_END, 2, 64, 2, "stone", "air");
        List<BlockOverride> before = Arrays.asList(door, other);
        assertEquals(-1, BlockOverrides.firstChangedRow(before, Arrays.asList(door, other)));
        assertEquals(-1, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withPlayer(true), other)));
        assertEquals(20, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withTicks(10, 25), other)));
        assertEquals(15, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withTicks(10, 15), other)));
        assertEquals(4, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withTicks(5, 20), other)));
        assertEquals(9, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withBlock("dirt"), other)));
        assertEquals(0, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withBefore("dirt"), other)));
        assertEquals(0, BlockOverrides.firstChangedRow(before, Collections.singletonList(door)));
        assertEquals(0, BlockOverrides.firstChangedRow(before, Arrays.asList(door.withPos(3, 64, 3), other)));
        assertEquals(39, BlockOverrides.firstChangedRow(before,
                Arrays.asList(door, other, new BlockOverride(40, BlockOverride.NO_END, 2, 64, 2, "dirt", "stone"))));

        InputData data = rows(50);
        data.getBlockOverrides().replaceAll(before);
        SimulationRunner runner = new SimulationRunner(new FakeSimulator());
        assertEquals(0, runner.firstRowChangedBy(data.getBlockOverrides()));
        runner.simulate(data);
        assertEquals(-1, runner.firstRowChangedBy(data.getBlockOverrides()));
        data.getBlockOverrides().set(1, other.withTicks(35, BlockOverride.NO_END));
        assertEquals(29, runner.firstRowChangedBy(data.getBlockOverrides()));
        runner.simulateFrom(29, data);
        assertEquals(-1, runner.firstRowChangedBy(data.getBlockOverrides()));
        runner.invalidate();
        assertEquals(0, runner.firstRowChangedBy(data.getBlockOverrides()));
    }

    @Test
    public void aSliceKeepsTheOverridesOnItsOwnTicks() {
        BlockOverride before = new BlockOverride(3, BlockOverride.NO_END, 1, 64, 1, CLOSED, OPEN, true);
        BlockOverride inside = new BlockOverride(12, 14, 2, 64, 2, "stone", "air", true);
        BlockOverride ended = new BlockOverride(2, 8, 3, 64, 3, "stone", "air");
        BlockOverride later = new BlockOverride(30, BlockOverride.NO_END, 4, 64, 4, "stone", "air");
        BlockOverride across = new BlockOverride(5, 11, 5, 64, 5, "stone", "air");
        List<Integer> sourceRows = Arrays.asList(9, 10, 11, 12, 13, 14, 15);
        List<BlockOverride> sliced = BlockOverrides.slice(Arrays.asList(ended, before, across, inside, later), sourceRows);
        assertEquals(Arrays.asList(
                new BlockOverride(1, BlockOverride.NO_END, 1, 64, 1, CLOSED, OPEN, false),
                new BlockOverride(1, 2, 5, 64, 5, "stone", "air", false),
                new BlockOverride(3, 5, 2, 64, 2, "stone", "air", true)), sliced);

        BlockOverrides source = new BlockOverrides();
        source.replaceAll(Arrays.asList(ended, before, across, inside, later));
        BlockOverrides target = new BlockOverrides();
        target.replaceAll(sliced);
        for (int j = 0; j < sourceRows.size(); j++) {
            List<BlockPatch> expected = new ArrayList<BlockPatch>();
            for (BlockPatch p : source.patchesAtRow(sourceRows.get(j))) {
                if (patchAt(target, p.x, p.y, p.z, j) != null) expected.add(p);
            }
            assertEquals(expected, target.patchesAtRow(j));
        }
        assertTrue(BlockOverrides.slice(Arrays.asList(ended, later), sourceRows).isEmpty());
    }

    private static BlockPatch patchAt(BlockOverrides overrides, int x, int y, int z, int row) {
        for (BlockPatch p : overrides.patchesAtRow(row)) {
            if (p.x == x && p.y == y && p.z == z) return p;
        }
        return null;
    }
}
