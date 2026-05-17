package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import de.legoshi.parkourcalc.core.ui.SettingsOverlay;

import java.nio.file.Path;
import java.util.List;

/**
 * Single-instance orchestrator. Replaces the wiring previously hand-rolled in
 * each loader entry: holds the InputData / OverlayManager / runner / box state,
 * exposes the lifecycle hooks loaders call from their mixins / event handlers,
 * and drives drag-picking through the MinecraftAccess port so no MC types ever
 * cross the loader boundary into core.
 */
public final class Application {

    private final MinecraftAccess mc;

    private final InputData inputData = new InputData();
    private final OverlayManager overlayManager = new OverlayManager(this::onPinStateChanged);
    private final BoxController boxController = new BoxController();
    private final Settings settings = new Settings();
    private final SimulationRunner runner;
    private final BoxDragController dragController;

    private Path settingsPath;

    public Application(Simulator simulator, MinecraftAccess mc) {
        this.mc = mc;
        this.runner = new SimulationRunner(simulator);
        this.dragController = new BoxDragController(boxController, this::handleStartPositionChange);
    }

    public void registerInputOverlay() {
        InputOverlay inputOverlay = new InputOverlay(inputData, this::runSimulation, this::setStartToPlayer);
        overlayManager.register("TAS Inputs", inputOverlay);
    }

    public void registerSettingsOverlay() {
        overlayManager.register("Settings", new SettingsOverlay(settings, this::saveSettings));
    }

    public void initSettingsStorage(Path path) {
        this.settingsPath = path;
        SettingsIO.load(path, settings);
        overlayManager.setPinnedNames(settings.pinnedOverlays);
    }

    public void saveSettings() {
        SettingsIO.save(settingsPath, settings);
    }

    private void onPinStateChanged() {
        settings.pinnedOverlays = overlayManager.getPinnedNames();
        saveSettings();
    }

    public void runSimulation() {
        if (!mc.isReady()) return;
        List<Vec3dCore> path = runner.simulate(inputData);
        boxController.clearAll();
        for (Vec3dCore p : path) {
            boxController.add(p);
        }
    }

    public void setStartToPlayer() {
        if (!mc.isReady()) return;
        runner.setStartPosition(mc.getPlayerPosition());
        runSimulation();
    }

    private void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        runSimulation();
    }

    /** Loader calls this from its world-render hook to advance drag picking. */
    public void tickDrag() {
        if (!mc.isReady()) return;
        dragController.tick(mc.getEyePosition(), mc.getLookDirection(), mc.isMousePressedLeft(), isControlPanelOpen());
    }

    public boolean isControlPanelOpen() {
        return overlayManager.isControlPanelOpen();
    }

    public boolean isReady() {
        return mc.isReady();
    }

    public void setControlPanelOpen(boolean open) {
        overlayManager.setControlPanelOpen(open);
    }

    public boolean shouldSuppressLeftClick() {
        if (isControlPanelOpen()) return false;
        if (dragController.isDragging()) return true;
        if (!mc.isReady()) return false;
        return dragController.isCursorOverStartBox(mc.getEyePosition(), mc.getLookDirection());
    }

    public OverlayManager getOverlayManager() {
        return overlayManager;
    }

    public BoxController getBoxController() {
        return boxController;
    }

    public Settings getSettings() {
        return settings;
    }

    public InputData getInputData() {
        return inputData;
    }
}
