package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SimulatorPitchTest {

    private static final class Entity {
        float pitch;
    }

    private static final class PitchCheckpoint implements Checkpoint {
        final float pitch;
        PitchCheckpoint(float pitch) { this.pitch = pitch; }
    }

    private static final class PitchRecordingSimulator extends LazyEntitySimulator<Entity> {
        final List<Float> tickPitch = new ArrayList<>();
        private float startPitch;

        @Override public void setStartPitch(float pitch) { startPitch = pitch; }

        @Override protected Entity createEntity(Vec3dCore s, Vec3dCore v, Float y) { return new Entity(); }

        @Override protected void resetEntity(Entity e, StartResumeState resume) { e.pitch = startPitch; }

        @Override protected StartResumeState describeResume(Checkpoint checkpoint) { return null; }

        @Override protected void setInput(Entity e, InputRow row) { }

        @Override protected void applyYaw(Entity e, float yaw) { }

        @Override protected void setYawAbsolute(Entity e, float yaw) { }

        @Override protected float getEntityPitch(Entity e) { return e.pitch; }

        @Override protected void setEntityPitch(Entity e, float pitch) { e.pitch = pitch; }

        @Override protected void applyTickEffects(Entity e, int s, int j) { }

        @Override protected void tickEntity(Entity e) { tickPitch.add(e.pitch); }

        @Override protected Vec3dCore getPos(Entity e) { return Vec3dCore.ZERO; }
        @Override protected boolean isOnGround(Entity e) { return false; }
        @Override protected boolean isSneaking(Entity e) { return false; }
        @Override protected boolean isSprinting(Entity e) { return false; }
        @Override protected float getMoveForward(Entity e) { return Float.NaN; }
        @Override protected float getMoveStrafe(Entity e) { return Float.NaN; }
        @Override protected boolean isWallCollision(Entity e) { return false; }
        @Override protected Vec3dCore getVelocity(Entity e) { return Vec3dCore.ZERO; }
        @Override protected boolean isSoftCollision(Entity e) { return false; }
        @Override protected double getCollisionAngleDegrees(Entity e) { return Double.NaN; }
        @Override protected de.legoshi.parkourcalc.core.anglesolver.Medium getTickMedium(Entity e) { return null; }
        @Override protected double getTickGroundFriction(Entity e) { return Double.NaN; }
        @Override protected int getTickSoulsandCells(Entity e) { return 0; }
        @Override protected float getYaw(Entity e) { return 0f; }
        @Override protected List<Vec3dCore> getSubtickPath(Entity e) { return Collections.emptyList(); }
        @Override protected Vec3dCore getStart(Entity e) { return Vec3dCore.ZERO; }
        @Override protected void setStart(Entity e, Vec3dCore pos) { }
        @Override protected Vec3dCore getStartVel(Entity e) { return Vec3dCore.ZERO; }
        @Override protected void setStartVel(Entity e, Vec3dCore vel) { }
        @Override protected float getStartYawValue(Entity e) { return 0f; }
        @Override protected void setStartYawValue(Entity e, float yaw) { }
        @Override protected Checkpoint saveCheckpoint(Entity e) { return new PitchCheckpoint(e.pitch); }
        @Override protected void restoreCheckpoint(Entity e, Checkpoint c) { e.pitch = ((PitchCheckpoint) c).pitch; }
    }

    private static InputRow deltaRow(float delta) {
        InputRow r = new InputRow();
        r.setPitch(delta);
        return r;
    }

    private static InputRow lockedRow(float absolute) {
        InputRow r = new InputRow();
        r.setPitchLocked(true);
        r.setPitch(absolute);
        return r;
    }

    private static InputData data(InputRow... rows) {
        InputData d = new InputData();
        for (InputRow r : rows) d.getRows().add(r);
        return d;
    }

    @Test
    public void feedsFoldedPitchToEntityEachTick() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        InputData d = data(deltaRow(10f), new InputRow(), deltaRow(-5f), lockedRow(12f));
        runner.simulate(d);

        assertEquals(4, sim.tickPitch.size());
        assertEquals(50f, sim.tickPitch.get(0), 0f);
        assertEquals(50f, sim.tickPitch.get(1), 0f);
        assertEquals(45f, sim.tickPitch.get(2), 0f);
        assertEquals(12f, sim.tickPitch.get(3), 0f);
    }

    @Test
    public void startPitchReachesEntityWithNoPitchRows() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(-25f);

        runner.simulate(data(new InputRow(), new InputRow()));

        assertEquals(-25f, sim.tickPitch.get(0), 0f);
        assertEquals(-25f, sim.tickPitch.get(1), 0f);
    }

    @Test
    public void pitchClampedToRange() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        runner.simulate(data(deltaRow(100f)));

        assertEquals(90f, sim.tickPitch.get(0), 0f);
    }

    @Test
    public void incrementalResumeRestoresFoldedPitchBase() {
        PitchRecordingSimulator sim = new PitchRecordingSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        runner.setStartPitch(40f);

        InputData d = data(deltaRow(10f), new InputRow(), deltaRow(-5f), lockedRow(12f));
        runner.simulate(d);

        sim.tickPitch.clear();
        runner.simulateFrom(2, d);

        assertEquals(2, sim.tickPitch.size());
        assertEquals(45f, sim.tickPitch.get(0), 0f);
        assertEquals(12f, sim.tickPitch.get(1), 0f);
    }
}
