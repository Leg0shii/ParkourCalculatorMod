package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.FakePlaybackBridge;
import de.legoshi.parkourcalc.core.FakeSimulator;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * gh-106: the macro must freeze while the game is paused. Client ticks keep firing through the
 * pause screen, but the world runs no physics, so consuming schedule rows there desyncs the
 * playback from the player. Paused ticks consume nothing; the row that was due plays on resume.
 */
public class PlaybackPauseTest {

    private static PlaybackController controller(FakePlaybackBridge bridge, InputData data) {
        SimulationRunner runner = new SimulationRunner(new FakeSimulator());
        PlaybackController pc = new PlaybackController(data, runner, new Settings());
        pc.setBridge(bridge);
        return pc;
    }

    @Test
    public void pausedTicksConsumeNothingAndResumeContinues() {
        InputData data = new InputData();
        for (int t = 0; t < 5; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(t == 2 ? InputRow.Key.A : InputRow.Key.W, true);
            data.getRows().add(row);
        }
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, data);

        pc.start();
        assertTrue(pc.isRunning());
        pc.tick(); // warmup 1
        pc.tick(); // warmup 2
        pc.tick(); // row 0
        pc.tick(); // row 1
        assertEquals(1, pc.currentTick());
        assertEquals(Boolean.TRUE, bridge.keys.get(InputRow.Key.W));

        bridge.paused = true;
        int releasesBefore = bridge.releaseAllCalls;
        for (int i = 0; i < 10; i++) pc.tick();
        assertEquals("paused ticks must not consume the schedule", 1, pc.currentTick());
        assertEquals("keys dropped exactly once on entering the pause",
                releasesBefore + 1, bridge.releaseAllCalls);
        assertFalse("no keys held into the pause screen", bridge.keys.containsKey(InputRow.Key.W));
        assertTrue("playback stays alive through the pause", pc.isRunning());

        bridge.paused = false;
        pc.tick(); // row 2, which was due during the pause
        assertEquals(2, pc.currentTick());
        assertEquals("the due row's keys are applied on resume", Boolean.TRUE, bridge.keys.get(InputRow.Key.A));
        assertEquals(Boolean.FALSE, bridge.keys.get(InputRow.Key.W));
    }

    @Test
    public void pauseDuringTheFinishEaseStillStops() throws Exception {
        InputData data = new InputData();
        data.getRows().add(new InputRow());
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, data);

        pc.start();
        pc.tick(); // warmup 1
        pc.tick(); // warmup 2
        pc.tick(); // row 0 (last row)

        bridge.paused = true;
        pc.tick();
        bridge.paused = false;

        // The finish window (one 50ms tick) must still elapse after the resume shift.
        long deadline = System.currentTimeMillis() + 2_000;
        while (pc.isRunning() && System.currentTimeMillis() < deadline) {
            pc.tick();
            Thread.sleep(5);
        }
        assertFalse("playback finishes after the pause", pc.isRunning());
    }
}
