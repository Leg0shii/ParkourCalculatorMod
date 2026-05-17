package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.save.LoadResult;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.SaveResult;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import de.legoshi.parkourcalc.core.ui.FileBrowserOverlay;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import de.legoshi.parkourcalc.core.ui.SettingsOverlay;

import java.nio.file.Path;
import java.util.Collections;
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
    private SaveStore saveStore;
    private boolean startInitialized;
    private String currentSaveName;
    private boolean dirty;

    public Application(Simulator simulator, MinecraftAccess mc) {
        this.mc = mc;
        this.runner = new SimulationRunner(simulator);
        this.dragController = new BoxDragController(boxController, this::handleStartPositionChange);
    }

    public void registerInputOverlay() {
        InputOverlay inputOverlay = new InputOverlay(inputData, this::onUserChange, this::setStartToPlayer);
        overlayManager.register("TAS Inputs", inputOverlay);
    }

    public void registerSettingsOverlay() {
        overlayManager.register("Settings", new SettingsOverlay(settings, this::saveSettings));
    }

    public void registerFileBrowserOverlay() {
        overlayManager.register("Files", new FileBrowserOverlay(new FileBrowserOverlay.Backend() {
            @Override public SaveResult save(String name) { return Application.this.save(name); }
            @Override public LoadResult load(String name) { return Application.this.load(name); }
            @Override public boolean delete(String name) { return Application.this.delete(name); }
            @Override public void newSession() { Application.this.newSession(); }
            @Override public List<SaveInfo> list() { return Application.this.listSaves(); }
            @Override public String currentName() { return Application.this.getCurrentSaveName(); }
            @Override public boolean isDirty() { return Application.this.isDirty(); }
        }));
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
        onUserChange();
    }

    private void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        onUserChange();
    }

    private void onUserChange() {
        dirty = true;
        runSimulation();
    }

    /** Loader calls this from its world-render hook to advance drag picking. */
    public void tickDrag() {
        if (!mc.isReady()) return;
        if (!startInitialized) {
            setStartToPlayer();
            startInitialized = true;
        }
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

    public void setSaveStore(SaveStore saveStore) {
        this.saveStore = saveStore;
    }

    public SaveStore getSaveStore() {
        return saveStore;
    }

    public SaveResult save(String name) {
        if (saveStore == null) return SaveResult.failure("Save store not initialized.");
        SaveResult result = SaveIO.save(saveStore, name, inputData, runner.getStartPosition());
        if (result.ok) {
            currentSaveName = result.name;
            dirty = false;
        }
        return result;
    }

    public LoadResult load(String name) {
        if (saveStore == null) return LoadResult.failure("Save store not initialized.");
        LoadResult result = SaveIO.load(saveStore, name);
        if (!result.ok) return result;

        Vec3dCore start = SaveIO.applyTo(result.file, inputData);
        runner.setStartPosition(start);
        runSimulation();
        currentSaveName = name;
        dirty = false;
        return result;
    }

    public boolean delete(String name) {
        if (saveStore == null) return false;
        boolean ok = saveStore.moveToRecycleBin(name);
        if (ok && name.equals(currentSaveName)) currentSaveName = null;
        return ok;
    }

    public void newSession() {
        inputData.resetToDefault();
        currentSaveName = null;
        if (mc.isReady()) {
            runner.setStartPosition(mc.getPlayerPosition());
        }
        runSimulation();
        dirty = false;
    }

    public String getCurrentSaveName() {
        return currentSaveName;
    }

    public boolean isDirty() {
        return dirty;
    }

    public List<SaveInfo> listSaves() {
        if (saveStore == null) return Collections.emptyList();
        return saveStore.list();
    }
}
