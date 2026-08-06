package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlaybackFlightSuppressionTest {

    private static final class FakeBridge implements PlaybackBridge {
        boolean paused;
        int suppressFlightCalls;

        @Override public boolean isSingleplayer() { return true; }
        @Override public boolean isGamePaused() { return paused; }
        @Override public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) { }
        @Override public void setKey(InputRow.Key key, boolean pressed) { }
        @Override public void setYaw(float absoluteYaw) { }
        @Override public void releaseAllKeys() { }
        @Override public void suppressFlight() { suppressFlightCalls++; }
        @Override public void closeUI() { }
        @Override public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) { }
    }

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

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) {
            data.getRows().add(new InputRow());
        }
        return data;
    }

    private static PlaybackController controller(FakeBridge bridge, Settings settings, InputData data) {
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
        FakeBridge bridge = new FakeBridge();
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
        FakeBridge bridge = new FakeBridge();
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
        FakeBridge bridge = new FakeBridge();
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
