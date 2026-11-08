package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
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

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void apply(List<BlockPatch> patches) {
            calls.add(new ArrayList<BlockPatch>(patches));
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
        assertEquals(0, world.viewRow());
        assertEquals(Collections.singletonList(patch(1, 64, 1, "stone")), patcher.last());
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
        in.getBlockOverrides().add(new BlockOverride(2, BlockOverride.NO_END, -5, 71, 12, "minecraft:stone", ""));
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
}
