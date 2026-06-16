package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class StartDragController implements BoxDragController.StartDragHandler {

    private final StartDragGate gate;
    private final StartDragGate.SimProbe probe;
    private final SimulationRunner runner;
    private final BoxController boxController;
    private final SelectionManager selection;
    private final Runnable markDirty;

    public StartDragController(SimulationRunner runner, BoxController boxController, SelectionManager selection,
                               Runnable markDirty, IntConsumer reSimulate, double tolerance) {
        this.runner = runner;
        this.boxController = boxController;
        this.selection = selection;
        this.markDirty = markDirty;
        this.probe = new StartDragGate.SimProbe() {
            @Override
            public void simulateAt(Vec3dCore start) {
                runner.setStartPosition(start);
                reSimulate.accept(-1);
            }

            @Override
            public Vec3dCore tickPosition(int index) {
                return boxController.getPosition(index);
            }
        };
        this.gate = new StartDragGate(probe, tolerance);
    }

    public boolean isDragActive() {
        return gate.isActive();
    }

    @Override
    public void onBegin(boolean rigid) {
        if (!rigid) return;
        List<Integer> indices = new ArrayList<>();
        List<Vec3dCore> positions = new ArrayList<>();
        for (int idx : selection.getSelected()) {
            Vec3dCore p = boxController.getPosition(idx);
            if (p == null) continue;
            indices.add(idx);
            positions.add(p);
        }
        gate.begin(runner.getStartPosition(), indices, positions);
    }

    @Override
    public void onMove(Vec3dCore position, boolean rigid) {
        if (!rigid || !gate.isActive()) {
            probe.simulateAt(position);
            markDirty.run();
            return;
        }
        gate.move(position);
    }

    @Override
    public void onEnd(boolean rigid) {
        if (!rigid || !gate.isActive()) return;
        if (gate.end()) {
            markDirty.run();
        }
        selection.retainBelow(boxController.size());
    }
}
