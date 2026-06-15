package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class SimulationRunner {

    private final Simulator simulator;

    // path[i] and checkpoints[i] are the snapshot+state captured at the same moment:
    // path[0] is post-reset (before any row applies); path[i>=1] is after row[i-1] ticked.
    // checkpoints[i] is the simulator state needed to resume just before row[i] is applied.
    private final List<TickState> path = new ArrayList<>();
    private final List<Checkpoint> checkpoints = new ArrayList<>();

    public SimulationRunner(Simulator simulator) {
        this.simulator = simulator;
    }

    public List<TickState> simulate(InputData inputData) {
        path.clear();
        checkpoints.clear();
        simulator.resetToStart();
        path.add(snapshot());
        checkpoints.add(simulator.saveCheckpoint());

        replayFrom(0, inputData.getRows());
        return path;
    }

    /** Restore cached state at dirtyTick, then replay rows[dirtyTick..end]. Falls back to a full
     *  simulate if the cache is empty or dirtyTick is out of range. */
    public List<TickState> simulateFrom(int dirtyTick, InputData inputData) {
        if (dirtyTick <= 0 || checkpoints.isEmpty() || dirtyTick >= checkpoints.size()) {
            return simulate(inputData);
        }

        simulator.restoreCheckpoint(checkpoints.get(dirtyTick));
        while (path.size() > dirtyTick + 1) {
            path.remove(path.size() - 1);
            checkpoints.remove(checkpoints.size() - 1);
        }

        replayFrom(dirtyTick, inputData.getRows());
        return path;
    }

    private void replayFrom(int startRow, List<InputRow> rows) {
        for (int i = startRow; i < rows.size(); i++) {
            simulator.applyInput(rows.get(i));
            simulator.tick();
            path.add(snapshot());
            checkpoints.add(simulator.saveCheckpoint());
        }
    }

    public boolean firstTickOnGround() {
        if (path.isEmpty()) {
            simulator.resetToStart();
            path.add(snapshot());
            checkpoints.add(simulator.saveCheckpoint());
        }
        return path.get(0).onGround;
    }

    private TickState snapshot() {
        return new TickState(
                simulator.getCurrentPosition(),
                simulator.isCurrentOnGround(),
                simulator.isCurrentSneaking(),
                simulator.isCurrentWallCollision(),
                simulator.getCurrentYaw(),
                simulator.getCurrentSubtickPath(),
                simulator.getCurrentVelocity(),
                simulator.isCurrentSoftCollision(),
                simulator.getCurrentCollisionAngleDegrees(),
                simulator.isCurrentSprinting(),
                simulator.getCurrentMoveForward(),
                simulator.getCurrentMoveStrafe()
        );
    }

    public Checkpoint getCheckpoint(int index) {
        if (index < 0 || index >= checkpoints.size()) return null;
        return checkpoints.get(index);
    }

    /** Drop the cached entity and any state captured against the old world. */
    public void invalidate() {
        path.clear();
        checkpoints.clear();
        simulator.invalidate();
    }

    public Vec3dCore getStartPosition() {
        return simulator.getStartPosition();
    }

    public void setStartPosition(Vec3dCore pos) {
        simulator.setStartPosition(pos);
    }

    public Vec3dCore getStartVelocity() {
        return simulator.getStartVelocity();
    }

    public void setStartVelocity(Vec3dCore vel) {
        simulator.setStartVelocity(vel);
    }

    public float getStartYaw() {
        return simulator.getStartYaw();
    }

    public void setStartYaw(float yaw) {
        simulator.setStartYaw(yaw);
    }
}
