package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientSidedReplaySettingTest {

    private static PlaybackController controller(FakePlaybackBridge bridge, Settings settings) {
        InputData data = new InputData();
        data.getRows().add(new InputRow());
        data.getRows().add(new InputRow());
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        return pc;
    }

    @Test
    public void defaultsToOff() {
        assertFalse(new Settings().clientSidedReplayInSingleplayer);
    }

    @Test
    public void persistsAndResets() throws Exception {
        Path file = Files.createTempDirectory("pkc-client-replay").resolve("settings.json");
        Settings saved = new Settings();
        saved.clientSidedReplayInSingleplayer = true;
        SettingsIO.save(file, saved);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);
        assertTrue(loaded.clientSidedReplayInSingleplayer);

        loaded.reset();
        assertFalse(loaded.clientSidedReplayInSingleplayer);
    }

    @Test
    public void startHandsTheSettingToTheBridgeBeforeTheFirstTeleport() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.clientSidedReplayInSingleplayer = true;
        PlaybackController pc = controller(bridge, settings);

        pc.start();
        assertTrue(bridge.clientSidedReplay);
        assertEquals(1, bridge.teleportCalls);
        assertTrue(bridge.clientSidedAtFirstTeleport);

        pc.stop();
        assertFalse(bridge.clientSidedReplay);
    }

    @Test
    public void settingOffKeepsTheServerReplay() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new Settings());

        pc.start();
        assertFalse(bridge.clientSidedReplay);
        assertFalse(bridge.clientSidedAtFirstTeleport);
    }

    @Test
    public void teleportToTickNeverRunsClientSided() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.clientSidedReplayInSingleplayer = true;
        PlaybackController pc = controller(bridge, settings);

        pc.start();
        pc.stop();
        assertTrue(pc.teleportToTick(new Vec3dCore(1, 2, 3), 0f, 0f));
        assertFalse(bridge.clientSidedReplay);
    }
}
