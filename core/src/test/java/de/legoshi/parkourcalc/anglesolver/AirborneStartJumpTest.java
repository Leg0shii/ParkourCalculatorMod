package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AirborneStartJumpTest {

    private static final double JUMP_IMPULSE = 0.42;

    private static final class JumpGatedSimulator implements Simulator {
        private final boolean launchOnGround;

        private Vec3dCore pos = Vec3dCore.ZERO;
        private Vec3dCore vel = Vec3dCore.ZERO;
        private boolean onGround;
        private boolean jumpHeld;

        JumpGatedSimulator(boolean launchOnGround) {
            this.launchOnGround = launchOnGround;
        }

        @Override public void resetToStart() {
            pos = Vec3dCore.ZERO;
            vel = Vec3dCore.ZERO;
            onGround = launchOnGround;
            jumpHeld = false;
        }

        @Override public void applyInput(InputRow row) {
            jumpHeld = row.isKeyActive(InputRow.Key.JUMP);
        }

        @Override public void tick() {
            if (jumpHeld && onGround) {
                vel = new Vec3dCore(vel.x, JUMP_IMPULSE, vel.z);
                onGround = false;
            } else {
                vel = new Vec3dCore(vel.x, (vel.y - 0.08) * 0.98, vel.z);
                onGround = false;
            }
            pos = pos.add(vel);
        }

        @Override public Vec3dCore getCurrentPosition() { return pos; }
        @Override public boolean isCurrentOnGround() { return onGround; }
        @Override public boolean isCurrentSneaking() { return false; }
        @Override public boolean isCurrentSprinting() { return false; }
        @Override public float getCurrentMoveForward() { return Float.NaN; }
        @Override public float getCurrentMoveStrafe() { return Float.NaN; }
        @Override public boolean isCurrentWallCollision() { return false; }
        @Override public Vec3dCore getCurrentVelocity() { return vel; }
        @Override public boolean isCurrentSoftCollision() { return false; }
        @Override public double getCurrentCollisionAngleDegrees() { return Double.NaN; }
        @Override public float getCurrentYaw() { return 0f; }
        @Override public List<Vec3dCore> getCurrentSubtickPath() { return Collections.emptyList(); }
        @Override public Vec3dCore getStartPosition() { return Vec3dCore.ZERO; }
        @Override public void setStartPosition(Vec3dCore p) { }
        @Override public Vec3dCore getStartVelocity() { return Vec3dCore.ZERO; }
        @Override public void setStartVelocity(Vec3dCore v) { }
        @Override public float getStartYaw() { return 0f; }
        @Override public void setStartYaw(float yaw) { }
        @Override public Checkpoint saveCheckpoint() { return null; }
        @Override public void restoreCheckpoint(Checkpoint checkpoint) { }
        @Override public void invalidate() { }
    }

    private static final class RecordingBridge implements PlaybackBridge {
        Boolean teleportedOnGround;

        @Override public boolean isSingleplayer() { return true; }
        @Override public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, boolean onGround) {
            teleportedOnGround = onGround;
        }
        @Override public void setKey(InputRow.Key key, boolean pressed) { }
        @Override public void setYaw(float absoluteYaw) { }
        @Override public void releaseAllKeys() { }
        @Override public void closeUI() { }
        @Override public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) { }
    }

    private static InputData jumpOnFirstRow() {
        InputData data = new InputData();
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.JUMP, true);
        data.getRows().add(row);
        return data;
    }

    @Test
    public void airborneStartJumpFallsInTheSimulation() {
        SimulationRunner runner = new SimulationRunner(new JumpGatedSimulator(false));
        List<TickState> path = runner.simulate(jumpOnFirstRow());

        assertFalse("the launch tick is airborne", runner.firstTickOnGround());
        assertFalse("path[0] reflects the airborne launch", path.get(0).onGround);
        assertTrue(
                "an airborne first-row JUMP must not jump; the player falls",
                path.get(1).velocity.y < 0.0
        );
    }

    @Test
    public void groundedStartJumpJumpsInTheSimulation() {
        SimulationRunner runner = new SimulationRunner(new JumpGatedSimulator(true));
        List<TickState> path = runner.simulate(jumpOnFirstRow());

        assertTrue("the launch tick is grounded", runner.firstTickOnGround());
        assertEquals("a grounded first-row JUMP jumps", JUMP_IMPULSE, path.get(1).velocity.y, 1e-9);
    }

    @Test
    public void playbackLaunchUsesTheSimsAirborneOnGround() {
        InputData data = jumpOnFirstRow();
        SimulationRunner runner = new SimulationRunner(new JumpGatedSimulator(false));
        runner.simulate(data);

        RecordingBridge bridge = new RecordingBridge();
        PlaybackController pc = new PlaybackController(data, runner, new Settings());
        pc.setBridge(bridge);
        pc.start();

        assertEquals(
                "playback seats the player with the sim's airborne launch onGround, not a forced true",
                Boolean.FALSE, bridge.teleportedOnGround
        );
        assertFalse("the loader's tick-0 hook reads the same airborne value", pc.firstTickOnGround());
    }

    @Test
    public void playbackLaunchUsesTheSimsGroundedOnGround() {
        InputData data = jumpOnFirstRow();
        SimulationRunner runner = new SimulationRunner(new JumpGatedSimulator(true));
        runner.simulate(data);

        RecordingBridge bridge = new RecordingBridge();
        PlaybackController pc = new PlaybackController(data, runner, new Settings());
        pc.setBridge(bridge);
        pc.start();

        assertEquals(
                "a grounded start still launches on the ground so the jump fires",
                Boolean.TRUE, bridge.teleportedOnGround
        );
        assertTrue(pc.firstTickOnGround());
    }
}
