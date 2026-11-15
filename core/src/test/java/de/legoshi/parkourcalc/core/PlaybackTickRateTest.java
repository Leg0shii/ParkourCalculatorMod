package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackTickRateTest {

    private static final long MS = 1_000_000L;

    private long now = 1_000 * MS;

    private PlaybackController controller(FakePlaybackBridge bridge, int rows) {
        InputData data = new InputData();
        for (int t = 0; t < rows; t++) {
            InputRow row = new InputRow();
            if (t == 3) row.setYaw(40f);
            data.getRows().add(row);
        }
        Settings settings = new Settings();
        settings.replayStartDelayTicks = 0;
        settings.yawFlickSpeed = 1.0e6f;
        PlaybackController pc = new PlaybackController(data, new SimulationRunner(new FakeSimulator()), settings);
        pc.setBridge(bridge);
        pc.setClock(() -> now);
        return pc;
    }

    private void tickEvery(PlaybackController pc, long intervalMs, int ticks) {
        for (int i = 0; i < ticks; i++) {
            pc.tick();
            now += intervalMs * MS;
        }
        now -= intervalMs * MS;
    }

    @Test
    public void turnEasesOverTheWholeTickAtAQuarterOfTheTickRate() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, 6);
        pc.start();
        tickEvery(pc, 200, 6);
        assertEquals(200 * MS, pc.tickNanos());
        pc.renderFrame();
        now += 50 * MS;
        pc.renderFrame();
        now += 50 * MS;
        pc.renderFrame();
        assertEquals(20f, bridge.yaw, 1.0e-3f);
        now += 100 * MS;
        pc.renderFrame();
        assertEquals(40f, bridge.yaw, 1.0e-3f);
    }

    @Test
    public void oneLateTickDoesNotStretchTheEase() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, 8);
        pc.start();
        tickEvery(pc, 50, 4);
        now += 400 * MS;
        pc.tick();
        assertEquals(50 * MS, pc.tickNanos());
    }

    @Test
    public void defaultsToTwentyTicksPerSecondBeforeTwoIntervalsAreMeasured() {
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, 4);
        pc.start();
        pc.tick();
        assertEquals(50 * MS, pc.tickNanos());
    }
}
