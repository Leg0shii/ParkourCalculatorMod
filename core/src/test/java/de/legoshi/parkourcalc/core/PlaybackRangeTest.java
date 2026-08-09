package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackRangeTest {

    private static InputData rows(int n) {
        InputRow.Key[] keys = InputRow.Key.values();
        InputData data = new InputData();
        for (int i = 0; i < n; i++) {
            InputRow row = new InputRow();
            row.setKeyActive(keys[i % keys.length], true);
            data.getRows().add(row);
        }
        return data;
    }

    private static InputRow.Key keyFor(int rowIndex) {
        InputRow.Key[] keys = InputRow.Key.values();
        return keys[rowIndex % keys.length];
    }

    private static PlaybackController controller(FakePlaybackBridge bridge, SimulationRunner runner, InputData data) {
        PlaybackController pc = new PlaybackController(data, runner, new Settings());
        pc.setBridge(bridge);
        return pc;
    }

    @Test
    public void startsAtTheChosenTickWithNoWarmupAndTeleportsToTheGivenState() {
        InputData data = rows(6);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new SimulationRunner(new FakeSimulator()), data);

        Vec3dCore pos = new Vec3dCore(10, 64, -5);
        Vec3dCore vel = new Vec3dCore(0.1, -0.2, 0.3);
        pc.start(3, data.size(), pos, vel, 90f);

        assertTrue(pc.isRunning());
        assertEquals("teleports to the supplied pre-tick state", pos, bridge.teleportPos);
        assertEquals(vel, bridge.teleportVel);
        assertEquals(90f, bridge.teleportYaw, 0f);
        assertEquals(1, bridge.teleportCalls);

        assertEquals(-1, pc.currentTick());
        pc.tick();
        assertEquals(3, pc.currentTick());
        assertEquals("row 3's key is applied first", Boolean.TRUE, bridge.keys.get(keyFor(3)));
    }

    @Test
    public void rangeStopsAfterItsLastTickAndNeverPlaysBeyondIt() throws Exception {
        InputData data = rows(8);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new SimulationRunner(new FakeSimulator()), data);

        pc.start(2, 5, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        pc.tick();
        assertEquals(2, pc.currentTick());
        pc.tick();
        pc.tick();
        assertEquals("playback advances exactly to the range's last tick", 4, pc.currentTick());

        long deadline = System.currentTimeMillis() + 2_000;
        while (pc.isRunning() && System.currentTimeMillis() < deadline) {
            assertTrue("never schedules a tick beyond the range", pc.currentTick() <= 4);
            pc.tick();
            Thread.sleep(5);
        }
        assertFalse("playback stops after the range's last tick", pc.isRunning());
    }

    @Test
    public void noArgStartWithoutResolverReplaysWholeTasFromTheTopWithWarmup() {
        InputData data = rows(3);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        SimulationRunner runner = new SimulationRunner(new FakeSimulator());
        runner.setStartPosition(new Vec3dCore(1, 2, 3));
        runner.setStartYaw(45f);
        PlaybackController pc = controller(bridge, runner, data);

        pc.start();

        assertTrue(pc.isRunning());
        assertEquals("teleports to the runner's start position", new Vec3dCore(1, 2, 3), bridge.teleportPos);
        assertEquals(45f, bridge.teleportYaw, 0f);
        pc.tick();
        assertEquals(-1, pc.currentTick());
        pc.tick();
        assertEquals(-1, pc.currentTick());
        pc.tick();
        assertEquals(0, pc.currentTick());
    }

    @Test
    public void startRangeResolverDrivesTheNoArgStart() {
        InputData data = rows(6);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new SimulationRunner(new FakeSimulator()), data);

        Vec3dCore pos = new Vec3dCore(7, 8, 9);
        pc.setStartRangeResolver(() -> new PlaybackController.StartRange(4, 6, pos, Vec3dCore.ZERO, 12f, null));
        pc.start();

        assertEquals(pos, bridge.teleportPos);
        pc.tick();
        assertEquals("resolver's start index is honoured by the no-arg start()", 4, pc.currentTick());
    }

    @Test
    public void statusHintDescribesTheRangeButNotAFullFromTopReplay() {
        InputData data = rows(10);
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        PlaybackController pc = controller(bridge, new SimulationRunner(new FakeSimulator()), data);

        assertEquals("idle has no hint", "", pc.statusHint());

        pc.start(0, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        assertEquals("a full from-the-top replay needs no hint", "", pc.statusHint());
        pc.stop();

        pc.start(4, 9, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        assertEquals("Replaying ticks 5-9", pc.statusHint());
        pc.stop();

        pc.start(4, data.size(), Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        assertEquals("Replaying from tick 5", pc.statusHint());
        pc.stop();

        pc.start(4, 5, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f);
        assertEquals("Replaying tick 5", pc.statusHint());

        pc.stop();
        assertEquals("idle again after stop", "", pc.statusHint());
    }
}
