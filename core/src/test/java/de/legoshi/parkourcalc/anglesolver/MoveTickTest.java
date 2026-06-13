package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MoveTickTest {

    private static final double STEP = 0.2;
    private static final double EPS = 1.0e-9;

    private static final class KinematicSimulator implements Simulator {
        private Vec3dCore start = Vec3dCore.ZERO;
        private Vec3dCore startVel = Vec3dCore.ZERO;
        private float startYaw;
        private final double wallX;

        private double x, y, z;

        KinematicSimulator(double wallX) {
            this.wallX = wallX;
        }

        private double clamp(double rawX) {
            return rawX > wallX ? wallX : rawX;
        }

        @Override public void resetToStart() {
            x = clamp(start.x);
            y = start.y;
            z = start.z;
        }
        @Override public void applyInput(InputRow row) { }
        @Override public void tick() {
            x = clamp(x + STEP);
            z = z + STEP;
        }
        @Override public Vec3dCore getCurrentPosition() { return new Vec3dCore(x, y, z); }
        @Override public boolean isCurrentOnGround() { return false; }
        @Override public boolean isCurrentSneaking() { return false; }
        @Override public boolean isCurrentSprinting() { return false; }
        @Override public float getCurrentMoveForward() { return Float.NaN; }
        @Override public float getCurrentMoveStrafe() { return Float.NaN; }
        @Override public boolean isCurrentWallCollision() { return false; }
        @Override public Vec3dCore getCurrentVelocity() { return Vec3dCore.ZERO; }
        @Override public boolean isCurrentSoftCollision() { return false; }
        @Override public double getCurrentCollisionAngleDegrees() { return Double.NaN; }
        @Override public float getCurrentYaw() { return startYaw; }
        @Override public List<Vec3dCore> getCurrentSubtickPath() { return Collections.emptyList(); }
        @Override public Vec3dCore getStartPosition() { return start; }
        @Override public void setStartPosition(Vec3dCore pos) { start = pos; }
        @Override public Vec3dCore getStartVelocity() { return startVel; }
        @Override public void setStartVelocity(Vec3dCore vel) { startVel = vel; }
        @Override public float getStartYaw() { return startYaw; }
        @Override public void setStartYaw(float yaw) { startYaw = yaw; }
        @Override public Checkpoint saveCheckpoint() { return null; }
        @Override public void restoreCheckpoint(Checkpoint checkpoint) { }
        @Override public void invalidate() { }
    }

    private static InputData rows(int n) {
        InputData data = new InputData();
        for (int i = 0; i < n; i++) data.getRows().add(new InputRow());
        return data;
    }

    private static Vec3dCore[] snapshot(List<TickState> path) {
        Vec3dCore[] out = new Vec3dCore[path.size()];
        for (int i = 0; i < out.length; i++) out[i] = path.get(i).position;
        return out;
    }

    @Test
    public void collisionFreeMoveShiftsStartExactlyAndLandsTickOnTarget() {
        SimulationRunner runner = new SimulationRunner(new KinematicSimulator(Double.POSITIVE_INFINITY));
        runner.setStartPosition(new Vec3dCore(0.2938, 0.0, 0.29923));
        InputData data = rows(5);
        Vec3dCore[] beforePath = snapshot(runner.simulate(data));

        int tick = 3;
        Vec3dCore before = beforePath[tick];
        Vec3dCore target = new Vec3dCore(0.3, 0.0, 0.3);
        Vec3dCore expectedOffset = target.sub(before);

        SimulationRunner.MoveTickResult result =
                runner.tryMoveTick(tick, target, SimulationRunner.DEFAULT_MOVE_TICK_TOLERANCE, data);

        assertTrue("collision-free move must be applied", result.applied());
        assertEquals(expectedOffset.x, result.offset.x, EPS);
        assertEquals(expectedOffset.y, result.offset.y, EPS);
        assertEquals(expectedOffset.z, result.offset.z, EPS);
        assertEquals(0.2938 + expectedOffset.x, runner.getStartPosition().x, EPS);
        assertEquals(0.29923 + expectedOffset.z, runner.getStartPosition().z, EPS);

        List<TickState> moved = runner.getPath();
        assertEquals(target.x, moved.get(tick).position.x, EPS);
        assertEquals(target.z, moved.get(tick).position.z, EPS);
        for (int i = 0; i < beforePath.length; i++) {
            assertEquals(beforePath[i].x + expectedOffset.x, moved.get(i).position.x, EPS);
            assertEquals(beforePath[i].z + expectedOffset.z, moved.get(i).position.z, EPS);
        }
    }

    @Test
    public void wallDivertedMoveRevertsAndReportsCollision() {
        SimulationRunner runner = new SimulationRunner(new KinematicSimulator(0.5));
        Vec3dCore start = new Vec3dCore(0.0, 0.0, 0.0);
        runner.setStartPosition(start);
        InputData data = rows(5);
        Vec3dCore[] beforePath = snapshot(runner.simulate(data));

        int tick = 4;
        assertEquals(0.5, beforePath[tick].x, EPS);

        Vec3dCore target = new Vec3dCore(0.9, 0.0, beforePath[tick].z);
        SimulationRunner.MoveTickResult result =
                runner.tryMoveTick(tick, target, SimulationRunner.DEFAULT_MOVE_TICK_TOLERANCE, data);

        assertEquals(SimulationRunner.MoveTickStatus.COLLISION_CHANGED_PATH, result.status);
        assertTrue("rejected move must not be applied", !result.applied());
        assertEquals(start.x, runner.getStartPosition().x, EPS);
        assertEquals(start.z, runner.getStartPosition().z, EPS);
        assertEquals(beforePath[tick].x, runner.getPath().get(tick).position.x, EPS);
        assertNotEquals(target.x, runner.getPath().get(tick).position.x, EPS);
    }

    @Test
    public void outOfRangeIndexIsInvalidAndLeavesStartUntouched() {
        SimulationRunner runner = new SimulationRunner(new KinematicSimulator(Double.POSITIVE_INFINITY));
        Vec3dCore start = new Vec3dCore(1.0, 2.0, 3.0);
        runner.setStartPosition(start);
        InputData data = rows(2);
        runner.simulate(data);

        SimulationRunner.MoveTickResult result =
                runner.tryMoveTick(99, new Vec3dCore(0, 0, 0), SimulationRunner.DEFAULT_MOVE_TICK_TOLERANCE, data);

        assertEquals(SimulationRunner.MoveTickStatus.INVALID_INDEX, result.status);
        assertEquals(start.x, runner.getStartPosition().x, EPS);
    }
}
