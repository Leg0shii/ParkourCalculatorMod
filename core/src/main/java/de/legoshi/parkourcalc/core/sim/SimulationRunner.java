package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a Simulator through the rows of an InputData and collects the tick-by-tick
 * positions. The iteration logic is identical across all loaders; only the per-tick
 * mechanics differ, which the Simulator port abstracts.
 */
public final class SimulationRunner {

    private final Simulator simulator;

    public SimulationRunner(Simulator simulator) {
        this.simulator = simulator;
    }

    public List<Vec3dCore> simulate(InputData inputData) {
        simulator.resetToStart();

        List<Vec3dCore> path = new ArrayList<Vec3dCore>();
        path.add(simulator.getStartPosition());

        for (InputRow row : inputData.getRows()) {
            simulator.applyInput(row);
            simulator.tick();
            path.add(simulator.getCurrentPosition());
        }

        return path;
    }

    public Vec3dCore getStartPosition() {
        return simulator.getStartPosition();
    }

    public void setStartPosition(Vec3dCore pos) {
        simulator.setStartPosition(pos);
    }
}
