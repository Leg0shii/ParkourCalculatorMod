package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class SimulationRunner {

    private static final InputRow TELEPORT_NOOP_ROW = new InputRow();

    private final Simulator simulator;

    // path[i] and checkpoints[i] are the snapshot+state captured at the same moment:
    // path[0] is post-reset (before any row applies); path[i>=1] is after row[i-1] ticked.
    // checkpoints[i] is the simulator state needed to resume just before row[i] is applied.
    private final List<TickState> path = new ArrayList<>();
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final List<ServerSimEvent> serverEvents = new ArrayList<>();

    private float startPitch = PlaybackController.DEFAULT_PITCH;

    public static final double DEFAULT_DRAG_TOLERANCE = 1.0e-6;

    public SimulationRunner(Simulator simulator) {
        this.simulator = simulator;
    }

    public List<TickState> simulate(InputData inputData) {
        path.clear();
        checkpoints.clear();
        serverEvents.clear();
        simulator.resetToStart();
        simulator.takeServerSimEvents();
        path.add(snapshot());
        checkpoints.add(simulator.saveCheckpoint());

        replayFrom(0, inputData.getRows());
        simulator.onPassEnd();
        return path;
    }

    public boolean canResumeFrom(int dirtyTick) {
        return dirtyTick > 0 && !checkpoints.isEmpty() && dirtyTick < checkpoints.size();
    }

    /** Restore cached state at dirtyTick, then replay rows[dirtyTick..end]. Falls back to a full
     *  simulate if the cache is empty or dirtyTick is out of range. */
    public List<TickState> simulateFrom(int dirtyTick, InputData inputData) {
        if (!canResumeFrom(dirtyTick)) {
            return simulate(inputData);
        }

        simulator.restoreCheckpoint(checkpoints.get(dirtyTick));
        while (path.size() > dirtyTick + 1) {
            path.remove(path.size() - 1);
            checkpoints.remove(checkpoints.size() - 1);
        }
        for (int i = serverEvents.size() - 1; i >= 0; i--) {
            if (serverEvents.get(i).tick >= dirtyTick) {
                serverEvents.remove(i);
            }
        }
        simulator.takeServerSimEvents();

        replayFrom(dirtyTick, inputData.getRows());
        simulator.onPassEnd();
        return path;
    }

    private void replayFrom(int startRow, List<InputRow> rows) {
        for (int i = startRow; i < rows.size(); i++) {
            InputRow row = rows.get(i);
            if (row.isTeleportEnabled()) {
                simulator.teleport(
                        new Vec3dCore(row.getTeleportX(), row.getTeleportY(), row.getTeleportZ()),
                        Vec3dCore.GROUND_REST_VELOCITY);
                simulator.applyInput(TELEPORT_NOOP_ROW);
            } else {
                simulator.applyInput(row);
            }
            simulator.tick();
            serverEvents.addAll(simulator.takeServerSimEvents());
            path.add(snapshot());
            checkpoints.add(simulator.saveCheckpoint());
        }
    }

    public List<ServerSimEvent> getServerEvents() {
        return java.util.Collections.unmodifiableList(serverEvents);
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
                simulator.getCurrentMoveStrafe(),
                simulator.getCurrentTickMedium(),
                simulator.getCurrentTickGroundFriction(),
                simulator.getCurrentTickSoulsandCells()
        );
    }

    public Checkpoint getCheckpoint(int index) {
        if (index < 0 || index >= checkpoints.size()) return null;
        return checkpoints.get(index);
    }

    public List<TickState> getPath() {
        return java.util.Collections.unmodifiableList(path);
    }

    /** Drop the cached entity and any state captured against the old world. */
    public void invalidate() {
        path.clear();
        checkpoints.clear();
        serverEvents.clear();
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

    public StartResumeState getStartResumeState() {
        return simulator.getStartResumeState();
    }

    public void setStartResumeState(StartResumeState resume) {
        simulator.setStartResumeState(resume);
    }

    public StartResumeState describeResumeAt(int index) {
        Checkpoint c = getCheckpoint(index);
        return c == null ? null : simulator.describeCheckpoint(c);
    }

    public float getStartPitch() {
        return startPitch;
    }

    public void setStartPitch(float pitch) {
        startPitch = Math.max(-90f, Math.min(90f, pitch));
        simulator.setStartPitch(startPitch);
    }

    public void onReplayStart(int startTick) {
        simulator.onReplayStart(startTick);
    }

    public void onReplayEnd() {
        simulator.onReplayEnd();
    }
}
