package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReplayStartDelayTest {

    private static PlaybackController controller(FakePlaybackBridge bridge, InputData data, Settings settings) {
        SimulationRunner runner = new SimulationRunner(new FakeSimulator());
        PlaybackController pc = new PlaybackController(data, runner, settings);
        pc.setBridge(bridge);
        return pc;
    }

    private static InputData threeRowsFirstPressesW() {
        InputData data = new InputData();
        for (int t = 0; t < 3; t++) {
            InputRow row = new InputRow();
            if (t == 0) row.setKeyActive(InputRow.Key.W, true);
            data.getRows().add(row);
        }
        return data;
    }

    @Test
    public void zeroDelayStartsAfterWarmupOnly() {
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, threeRowsFirstPressesW(), settings);

        pc.start();
        pc.tick();
        pc.tick();
        assertEquals(-1, pc.currentTick());
        pc.tick();
        assertEquals(0, pc.currentTick());
        assertEquals(Boolean.TRUE, bridge.keys.get(InputRow.Key.W));
    }

    @Test
    public void delayHoldsTheMacroForExtraTicks() {
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 3;
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, threeRowsFirstPressesW(), settings);

        pc.start();
        for (int i = 0; i < Settings.MIN_REPLAY_START_DELAY_TICKS + 5; i++) {
            pc.tick();
            assertEquals(-1, pc.currentTick());
            assertFalse(bridge.keys.containsKey(InputRow.Key.W));
        }
        pc.tick();
        assertEquals(0, pc.currentTick());
        assertEquals(Boolean.TRUE, bridge.keys.get(InputRow.Key.W));
    }
}
