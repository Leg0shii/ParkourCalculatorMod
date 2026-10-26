package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayKeystrokesTest {

    private static PlaybackController controller(FakePlaybackBridge bridge, InputData data) {
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        return pc;
    }

    private static InputData rows(InputRow.Key... firstRowKeys) {
        InputData data = new InputData();
        InputRow first = new InputRow();
        for (InputRow.Key key : firstRowKeys) first.setKeyActive(key, true);
        data.getRows().add(first);
        data.getRows().add(new InputRow());
        return data;
    }

    private static FakePlaybackBridge multiplayer() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        bridge.singleplayer = false;
        bridge.multiplayerPlayback = true;
        return bridge;
    }

    @Test
    public void showsTheKeysOfTheReplayedRow() {
        PlaybackController pc = controller(multiplayer(), rows(InputRow.Key.W, InputRow.Key.JUMP, InputRow.Key.LEFT_CLICK));
        pc.start();
        pc.tick();
        pc.tick();
        assertFalse(pc.isKeyPressed(InputRow.Key.W));
        pc.tick();
        assertTrue(pc.isKeyPressed(InputRow.Key.W));
        assertTrue(pc.isKeyPressed(InputRow.Key.JUMP));
        assertTrue(pc.isKeyPressed(InputRow.Key.LEFT_CLICK));
        assertFalse(pc.isKeyPressed(InputRow.Key.A));
        pc.tick();
        assertFalse(pc.isKeyPressed(InputRow.Key.W));
    }

    @Test
    public void stopClearsTheKeys() {
        PlaybackController pc = controller(multiplayer(), rows(InputRow.Key.W));
        pc.start();
        pc.tick();
        pc.tick();
        pc.tick();
        assertTrue(pc.isKeyPressed(InputRow.Key.W));
        pc.stop();
        assertFalse(pc.isKeyPressed(InputRow.Key.W));
    }

    @Test
    public void pauseClearsTheKeys() {
        FakePlaybackBridge bridge = multiplayer();
        PlaybackController pc = controller(bridge, rows(InputRow.Key.W));
        pc.start();
        pc.tick();
        pc.tick();
        pc.tick();
        bridge.paused = true;
        pc.tick();
        assertFalse(pc.isKeyPressed(InputRow.Key.W));
    }

    @Test
    public void clientSidedSessionOnlyInMultiplayer() {
        assertTrue(controller(multiplayer(), rows()).isClientSidedSession());
        assertFalse(controller(new FakePlaybackBridge(), rows()).isClientSidedSession());
    }
}
