package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.GhostBlock;
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

public class GhostBlocksTest {

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) data.getRows().add(new InputRow());
        return data;
    }

    @Test
    public void specParsesIdAndStates() {
        GhostBlock.Spec s = GhostBlock.Spec.parse(" Oak_Door[open=true, half=lower] ");
        assertTrue(s.valid());
        assertEquals("minecraft:oak_door", s.id);
        assertEquals("true", s.properties.get("open"));
        assertEquals("lower", s.properties.get("half"));
        assertEquals("minecraft:oak_door[open=true,half=lower]", GhostBlock.Spec.format(s.id, s.properties));
        assertEquals("minecraft:air", GhostBlock.Spec.parse("air").id);
        assertFalse(GhostBlock.Spec.parse("").valid());
        assertFalse(GhostBlock.Spec.parse("oak_door[open=true").valid());
        assertFalse(GhostBlock.Spec.parse("oak_door[open]").valid());
    }

    @Test
    public void activeTicksAreOneBasedAndInclusive() {
        GhostBlock b = new GhostBlock(3, 5, 0, 64, 0, "air");
        assertFalse(b.activeAtRow(1));
        assertTrue(b.activeAtRow(2));
        assertTrue(b.activeAtRow(4));
        assertFalse(b.activeAtRow(5));
    }

    @Test
    public void simulatorSeesOnlyTheBlocksActiveOnEachTickAndNoneAfterThePass() {
        InputData data = rows(6);
        GhostBlock door = new GhostBlock(2, 3, 1, 64, 1, "air");
        GhostBlock bad = new GhostBlock(1, 6, 2, 64, 2, "[");
        data.getGhostBlocks().add(door);
        data.getGhostBlocks().add(bad);
        final List<Integer> perTick = new ArrayList<Integer>();
        final List<GhostBlock>[] current = new List[] {Collections.emptyList()};
        FakeSimulator sim = new FakeSimulator() {
            @Override
            public void setGhostBlocks(List<GhostBlock> active) {
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
        GhostBlock door = new GhostBlock(2, 2, 1, 64, 1, "air");
        data.getGhostBlocks().add(door);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        pc.start(0, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, new de.legoshi.parkourcalc.core.sim.Checkpoint() {
        });
        pc.tick();
        assertTrue(bridge.ghostBlocks.isEmpty());
        pc.tick();
        assertEquals(Collections.singletonList(door), bridge.ghostBlocks);
        pc.tick();
        assertTrue(bridge.ghostBlocks.isEmpty());
        pc.tick();
        pc.stop();
        assertTrue(bridge.ghostBlocks.isEmpty());
    }

    @Test
    public void ghostBlocksRoundTripThroughTheSaveAndChangeTheUndoSignature() throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(Files.createTempDirectory("pkc-ghost"), "test", "1.8.9", () -> null);
        InputData in = rows(3);
        String before = SaveIO.undoSignature(in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null);
        in.getGhostBlocks().add(new GhostBlock(1, 3, -5, 70, 12, "minecraft:oak_trapdoor[open=true]"));
        String after = SaveIO.undoSignature(in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null);
        assertNotEquals(before, after);

        Result<String> saved = SaveIO.save(store, "run", in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, PlaybackController.DEFAULT_PITCH, null, null, false);
        assertTrue(saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue(loaded.ok);
        InputData out = new InputData();
        SaveIO.applyGhostBlocksTo(loaded.value, out);
        assertEquals(in.getGhostBlocks().all(), out.getGhostBlocks().all());
        assertTrue(SaveIO.ghostBlocksMatch(loaded.value, in));

        InputData empty = rows(1);
        Result<String> plain = SaveIO.save(store, "plain", empty, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, PlaybackController.DEFAULT_PITCH, null, null, false);
        assertTrue(plain.ok);
        SaveFile plainFile = SaveIO.load(store, "plain").value;
        assertNull(plainFile.ghostBlocks);
        SaveIO.applyGhostBlocksTo(plainFile, out);
        assertTrue(out.getGhostBlocks().isEmpty());
    }
}
