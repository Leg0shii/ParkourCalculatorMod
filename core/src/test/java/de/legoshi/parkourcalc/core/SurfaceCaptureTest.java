package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.Medium;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurfaceCaptureTest {

    private static final class SurfaceSimulator implements Simulator {
        private final Medium[] mediums;
        private final double[] frictions;
        private final int[] soulsandCells;
        private int ticked;
        private Vec3dCore startPos = Vec3dCore.ZERO;
        private Vec3dCore startVel = Vec3dCore.ZERO;
        private float startYaw;

        SurfaceSimulator(Medium[] mediums, double[] frictions, int[] soulsandCells) {
            this.mediums = mediums;
            this.frictions = frictions;
            this.soulsandCells = soulsandCells;
        }

        @Override public void resetToStart() { ticked = 0; }
        @Override public void applyInput(InputRow row) { }
        @Override public void tick() { ticked++; }
        @Override public Vec3dCore getCurrentPosition() { return new Vec3dCore(ticked, 0, 0); }
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
        @Override public List<Vec3dCore> getCurrentSubtickPath() { return Collections.emptyList(); }
        @Override public Medium getCurrentTickMedium() { return ticked == 0 ? null : mediums[ticked - 1]; }
        @Override public double getCurrentTickGroundFriction() { return ticked == 0 ? Double.NaN : frictions[ticked - 1]; }
        @Override public int getCurrentTickSoulsandCells() { return ticked == 0 ? 0 : soulsandCells[ticked - 1]; }
        @Override public Vec3dCore getStartPosition() { return startPos; }
        @Override public void setStartPosition(Vec3dCore pos) { startPos = pos; }
        @Override public Vec3dCore getStartVelocity() { return startVel; }
        @Override public void setStartVelocity(Vec3dCore vel) { startVel = vel; }
        @Override public float getStartYaw() { return startYaw; }
        @Override public void setStartYaw(float yaw) { startYaw = yaw; }
        @Override public Checkpoint saveCheckpoint() { return null; }
        @Override public void restoreCheckpoint(Checkpoint checkpoint) { }
        @Override public void invalidate() { }
    }

    @Test
    public void recordsTickMediumIntoPostTickState() {
        Medium[] mediums = {Medium.WATER, Medium.SOULSAND, Medium.COBWEB};
        double[] frictions = {Double.NaN, 0.6, Double.NaN};
        int[] soulsandCells = {0, 2, 0};
        SimulationRunner runner = new SimulationRunner(new SurfaceSimulator(mediums, frictions, soulsandCells));
        InputData data = new InputData();
        data.addRows(0, 3);

        List<TickState> path = runner.simulate(data);

        assertEquals(4, path.size());
        assertFalse(path.get(0).hasSurfaceSample());
        assertEquals(Medium.WATER, path.get(1).medium);
        assertTrue(Double.isNaN(path.get(1).groundFriction));
        assertEquals(Medium.SOULSAND, path.get(2).medium);
        assertEquals(0.6, path.get(2).groundFriction, 0.0);
        assertEquals(2, path.get(2).soulsandCells);
        assertEquals(Medium.COBWEB, path.get(3).medium);
        assertTrue(path.get(1).hasSurfaceSample());
    }

    @Test
    public void fromFlagsFollowsBranchPriority() {
        assertEquals(Medium.COBWEB, Medium.fromFlags(true, true, true, true, true));
        assertEquals(Medium.WATER, Medium.fromFlags(false, true, true, true, true));
        assertEquals(Medium.LAVA, Medium.fromFlags(false, false, true, true, true));
        assertEquals(Medium.LADDER, Medium.fromFlags(false, false, false, true, true));
        assertEquals(Medium.SOULSAND, Medium.fromFlags(false, false, false, false, true));
        assertEquals(Medium.NONE, Medium.fromFlags(false, false, false, false, false));
    }
}
