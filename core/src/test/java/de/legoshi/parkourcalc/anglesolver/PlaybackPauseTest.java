package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * gh-106: the macro must freeze while the game is paused. Client ticks keep firing through the
 * pause screen, but the world runs no physics, so consuming schedule rows there desyncs the
 * playback from the player. Paused ticks consume nothing; the row that was due plays on resume.
 */
public class PlaybackPauseTest {

    /** Records key writes; pause state is a settable flag. */
    private static final class FakeBridge implements PlaybackBridge {
        boolean paused;
        int releaseAllCalls;
        final Map<InputRow.Key, Boolean> keys = new EnumMap<InputRow.Key, Boolean>(InputRow.Key.class);

        @Override
        public boolean isSingleplayer() {
            return true;
        }

        @Override
        public boolean isGamePaused() {
            return paused;
        }

        @Override
        public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw) {
        }

        @Override
        public void setKey(InputRow.Key key, boolean pressed) {
            keys.put(key, pressed);
        }

        @Override
        public void setYaw(float absoluteYaw) {
        }

        @Override
        public void releaseAllKeys() {
            releaseAllCalls++;
            keys.clear();
        }

        @Override
        public void closeUI() {
        }

        @Override
        public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        }
    }

    /** The controller only reads the launch state from the runner; everything else is unused here. */
    private static final class FakeSimulator implements Simulator {
        private Vec3dCore startPos = Vec3dCore.ZERO;
        private Vec3dCore startVel = Vec3dCore.ZERO;
        private float startYaw;

        @Override public void resetToStart() { }
        @Override public void applyInput(InputRow row) { }
        @Override public void tick() { }
        @Override public Vec3dCore getCurrentPosition() { return Vec3dCore.ZERO; }
        @Override public boolean isCurrentOnGround() { return false; }
        @Override public boolean isCurrentSneaking() { return false; }
        @Override public boolean isCurrentSprinting() { return false; }
        @Override public float getCurrentMoveForward() { return Float.NaN; }
        @Override public float getCurrentMoveStrafe() { return Float.NaN; }
        @Override public boolean isCurrentWallCollision() { return false; }
        @Override public Vec3dCore getCurrentVelocity() { return Vec3dCore.ZERO; }
        @Override public boolean isCurrentSoftCollision() { return false; }
        @Override public double getCurrentCollisionAngleDegrees() { return Double.NaN; }
        @Override public float getCurrentYaw() { return 0f; }
        @Override public java.util.List<Vec3dCore> getCurrentSubtickPath() { return java.util.Collections.emptyList(); }
        @Override public Vec3dCore getStartPosition() { return startPos; }
        @Override public void setStartPosition(Vec3dCore pos) { startPos = pos; }
        @Override public Vec3dCore getStartVelocity() { return startVel; }
        @Override public void setStartVelocity(Vec3dCore vel) { startVel = vel; }
        @Override public float getStartYaw() { return startYaw; }
        @Override public void setStartYaw(float yaw) { startYaw = yaw; }
        @Override public de.legoshi.parkourcalc.core.sim.Checkpoint saveCheckpoint() { return null; }
        @Override public void restoreCheckpoint(de.legoshi.parkourcalc.core.sim.Checkpoint checkpoint) { }
        @Override public void invalidate() { }
    }

    private static PlaybackController controller(FakeBridge bridge, InputData data) {
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
        FakeBridge bridge = new FakeBridge();
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
        for (int i = 0; i < 10; i++) pc.tick(); // ten client ticks under the pause screen
        assertEquals("paused ticks must not consume the schedule", 1, pc.currentTick());
        assertEquals("keys dropped exactly once on entering the pause",
                releasesBefore + 1, bridge.releaseAllCalls);
        assertFalse("no keys held into the pause screen", bridge.keys.containsKey(InputRow.Key.W));
        assertTrue("playback stays alive through the pause", pc.isRunning());

        bridge.paused = false;
        pc.tick(); // the row that was due (row 2) plays now
        assertEquals(2, pc.currentTick());
        assertEquals("the due row's keys are applied on resume", Boolean.TRUE, bridge.keys.get(InputRow.Key.A));
        assertEquals(Boolean.FALSE, bridge.keys.get(InputRow.Key.W));
    }

    @Test
    public void pauseDuringTheFinishEaseStillStops() throws Exception {
        InputData data = new InputData();
        data.getRows().add(new InputRow());
        FakeBridge bridge = new FakeBridge();
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
