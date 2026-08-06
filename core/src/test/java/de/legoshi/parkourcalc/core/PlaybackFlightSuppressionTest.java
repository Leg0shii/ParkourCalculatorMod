package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackFlightSuppressionTest {

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) {
            data.getRows().add(new InputRow());
        }
        return data;
    }

    private static PlaybackController controller(FakePlaybackBridge bridge, Settings settings, InputData data) {
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        return pc;
    }

    @Test
    public void defaultsToOn() {
        assertTrue(new Settings().disableFlightDuringPlayback);
    }

    @Test
    public void suppressesEveryRunningTickIncludingWarmupAndTail() {
        InputData data = rows(2);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new Settings(), data);

        pc.tick();
        assertEquals("idle ticks never suppress", 0, bridge.suppressFlightCalls);

        pc.start();
        pc.tick();
        pc.tick();
        assertEquals("warmup ticks suppress", 2, bridge.suppressFlightCalls);
        pc.tick();
        pc.tick();
        assertEquals(4, bridge.suppressFlightCalls);
        pc.tick();
        assertEquals("the tail wait after the last row still suppresses", 5, bridge.suppressFlightCalls);

        pc.stop();
        pc.tick();
        assertEquals(5, bridge.suppressFlightCalls);
    }

    @Test
    public void settingOffNeverSuppresses() {
        InputData data = rows(2);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        Settings settings = new Settings();
        settings.disableFlightDuringPlayback = false;
        PlaybackController pc = controller(bridge, settings, data);

        pc.start();
        pc.tick();
        pc.tick();
        pc.tick();
        pc.tick();
        assertEquals(0, bridge.suppressFlightCalls);
    }

    @Test
    public void pausedTicksDoNotSuppress() {
        InputData data = rows(2);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new Settings(), data);

        pc.start();
        bridge.paused = true;
        pc.tick();
        pc.tick();
        assertEquals(0, bridge.suppressFlightCalls);

        bridge.paused = false;
        pc.tick();
        assertEquals(1, bridge.suppressFlightCalls);
    }
}
