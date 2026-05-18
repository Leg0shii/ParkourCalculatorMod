package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class SimulationRunner {

    private final Simulator simulator;

    public SimulationRunner(Simulator simulator) {
        this.simulator = simulator;
    }

    public List<TickState> simulate(InputData inputData) {
        simulator.resetToStart();

        List<TickState> path = new ArrayList<TickState>();
        path.add(snapshot());

        for (InputRow row : inputData.getRows()) {
            simulator.applyInput(row);
            simulator.tick();
            path.add(snapshot());
        }

        return path;
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
                simulator.isCurrentSoftCollision()
        );
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
