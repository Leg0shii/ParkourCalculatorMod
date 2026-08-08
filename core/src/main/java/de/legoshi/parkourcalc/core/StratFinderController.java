package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratFinder;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.anglesolver.StratFinderWidget;

import java.util.List;

public final class StratFinderController {

    private final AngleSolverState angleSolverState;
    private final BoxController boxController;
    private final SimulationRunner runner;
    private final SaveController saveController;
    private final InputData inputData;
    private final ExactJumpModel forwardModel;
    private final StratFinderWidget widget;
    private String lastAppliedSnapshotJson;

    public StratFinderController(AngleSolverState angleSolverState, BoxController boxController,
                                 SimulationRunner runner, SaveController saveController,
                                 InputData inputData, ExactJumpModel forwardModel) {
        this.angleSolverState = angleSolverState;
        this.boxController = boxController;
        this.runner = runner;
        this.saveController = saveController;
        this.inputData = inputData;
        this.forwardModel = forwardModel;
        this.widget = new StratFinderWidget(
                this::startSweep, this::prepareFind, this::applyItem, this::applyOriginal,
                saveController::isTempActive, saveController::restoreInitialTrajectory,
                saveController::clearTempTrajectory);
    }

    public StratFinderWidget widget() {
        return widget;
    }

    private StratFinder startSweep(long budgetMs) {
        FileSystemSaveStore store = saveController.getSaveStore();
        int st = angleSolverState.getStartTick();
        int lt = angleSolverState.getLandingTick();
        if (store == null || forwardModel == null || inputData.size() == 0
                || st < 0 || st >= boxController.size() || lt <= st) {
            return null;
        }
        SaveFile witness = SaveIO.buildSaveFile(store, inputData, runner.getStartPosition(),
                runner.getStartVelocity(), runner.getStartYaw(), runner.getStartPitch(),
                angleSolverState, boxController.getStates(), true);
        double[] edge = VelocityMapController.objectiveEdge(angleSolverState);
        boolean max = angleSolverState.getGoal() == AngleSolverState.Goal.MAX;
        StratFinder finder = new StratFinder(witness, forwardModel, store, budgetMs, edge[0], max);
        finder.start();
        return finder;
    }

    private void prepareFind() {
        if (!saveController.isTempActive()) return;
        if (liveMatchesLastApplied()) {
            saveController.restoreInitialTrajectory();
        } else {
            saveController.clearTempTrajectory();
        }
        lastAppliedSnapshotJson = null;
    }

    private void applyItem(StratFinder.Item item) {
        if (item == null || item.appliedSnapshotJson == null) return;
        if (saveController.isTempActive() && !liveMatchesLastApplied()) {
            saveController.clearTempTrajectory();
        }
        saveController.beginTempTrajectory();
        saveController.applySnapshotJson(item.appliedSnapshotJson);
        lastAppliedSnapshotJson = item.appliedSnapshotJson;
    }

    private void applyOriginal() {
        if (!saveController.isTempActive()) return;
        if (liveMatchesLastApplied()) {
            saveController.restoreInitialTrajectory();
        } else {
            saveController.clearTempTrajectory();
        }
        lastAppliedSnapshotJson = null;
    }

    private boolean liveMatchesLastApplied() {
        if (lastAppliedSnapshotJson == null) return false;
        SaveFile applied = SaveIO.parseSafe(lastAppliedSnapshotJson);
        if (applied == null || applied.rows == null) return false;
        List<InputRow> rows = inputData.getRows();
        if (rows.size() != applied.rows.size()) return false;
        for (int i = 0; i < rows.size(); i++) {
            if (!SaveIO.rowMatches(applied.rows.get(i), rows.get(i))) return false;
        }
        return true;
    }
}
