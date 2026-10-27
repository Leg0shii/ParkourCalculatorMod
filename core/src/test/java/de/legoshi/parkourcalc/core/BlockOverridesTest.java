package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
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
    public void activeTicksAreOneBasedAndInclusive() {
        BlockOverride b = new BlockOverride(3, 5, 0, 64, 0, "air");
        assertFalse(b.activeAtRow(1));
        assertTrue(b.activeAtRow(2));
        assertTrue(b.activeAtRow(4));
        assertFalse(b.activeAtRow(5));
    }

    @Test
    public void simulatorSeesOnlyTheBlocksActiveOnEachTickAndNoneAfterThePass() {
        InputData data = rows(6);
        BlockOverride door = new BlockOverride(2, 3, 1, 64, 1, "air");
        BlockOverride bad = new BlockOverride(1, 6, 2, 64, 2, "[");
        data.getBlockOverrides().add(door);
        data.getBlockOverrides().add(bad);
        final List<Integer> perTick = new ArrayList<Integer>();
        final List<BlockOverride>[] current = new List[] {Collections.emptyList()};
        FakeSimulator sim = new FakeSimulator() {
            @Override
            public void setBlockOverrides(List<BlockOverride> active) {
                current[0] = active;
            }

            @Override
            public void tick() {
                perTick.add(current[0].size());
                if (!current[0].isEmpty()) assertEquals(door, current[0].get(0));
            }
        };
        SimulationRunner runner = new SimulationRunner(sim);
        runner.simulate(data);
        assertEquals(java.util.Arrays.asList(0, 1, 1, 0, 0, 0), perTick);
        assertTrue(current[0].isEmpty());

        perTick.clear();
        runner.simulateFrom(2, data);
        assertEquals(java.util.Arrays.asList(1, 0, 0, 0), perTick);
        assertTrue(current[0].isEmpty());
    }

    @Test
    public void playbackHandsTheReplicaEachTicksBlocksAndClearsThemOnStop() {
        InputData data = rows(4);
        BlockOverride door = new BlockOverride(2, 2, 1, 64, 1, "air");
        data.getBlockOverrides().add(door);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        pc.start(0, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, new de.legoshi.parkourcalc.core.sim.Checkpoint() {
        });
        pc.tick();
        assertTrue(bridge.blockOverrides.isEmpty());
        pc.tick();
        assertEquals(Collections.singletonList(door), bridge.blockOverrides);
        pc.tick();
        assertTrue(bridge.blockOverrides.isEmpty());
        pc.tick();
        pc.stop();
        assertTrue(bridge.blockOverrides.isEmpty());
    }

    @Test
    public void blockOverridesRoundTripThroughTheSaveAndChangeTheUndoSignature() throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(Files.createTempDirectory("pkc-overrides"), "test", "1.8.9", () -> null);
        InputData in = rows(3);
        String before = SaveIO.undoSignature(in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null);
        in.getBlockOverrides().add(new BlockOverride(1, 3, -5, 70, 12, "minecraft:oak_trapdoor[open=true]"));
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
