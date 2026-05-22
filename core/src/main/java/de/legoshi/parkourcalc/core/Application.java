package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import de.legoshi.parkourcalc.core.ui.FileBrowserOverlay;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import de.legoshi.parkourcalc.core.ui.SettingsOverlay;
import de.legoshi.parkourcalc.core.ui.TickInfoPanel;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;

import java.nio.file.Path;
import java.util.List;

/**
 * Single-instance orchestrator: wiring of InputData/OverlayManager/runner/box state and the
 * lifecycle hooks loaders call from mixins / event handlers. Save/load state lives on SaveController.
 */
public final class Application {

    private final MinecraftAccess mc;

    private final InputData inputData = new InputData();
    private final OverlayManager overlayManager = new OverlayManager(this::onPinStateChanged);
    private final BoxController boxController = new BoxController();
    private final Settings settings = new Settings();
    private final SelectionManager selection;
    private final SimulationRunner runner;
    private final BoxDragController dragController;
    private final YawGizmoController yawGizmo;
    private final SaveController saveController;
    private final PlaybackController playback;

    private Path settingsPath;
    private boolean startInitialized;

    public Application(Simulator simulator, MinecraftAccess mc) {
        this.mc = mc;
        this.selection = new SelectionManager(mc);
        this.runner = new SimulationRunner(simulator);
        this.dragController = new BoxDragController(boxController, this::handleStartPositionChange);
        this.yawGizmo = new YawGizmoController(
                boxController,
                this::handleStartYawChange,
                this::handleTickYawChange
        );
        this.saveController = new SaveController(inputData, runner, mc, this::runSimulation);
        this.playback = new PlaybackController(inputData, runner, settings);
    }

    public void registerInputOverlay() {
        InputOverlay inputOverlay = new InputOverlay(inputData, selection, this::onUserChange, this::setStartToPlayer, playback, mc);
        overlayManager.register("TAS Inputs", inputOverlay);
    }

    public void registerSettingsOverlay() {
        overlayManager.register("Settings", new SettingsOverlay(settings, this::saveSettings));
    }

    public void registerFileBrowserOverlay() {
        overlayManager.register("Files", new FileBrowserOverlay(saveController));
    }

    public void registerTickInfoOverlay() {
        overlayManager.register("Tick Info", new TickInfoPanel(boxController, selection));
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
        runSimulation(-1);
    }

    private void runSimulation(int dirtyTick) {
        if (!mc.isReady()) return;
        // SP path runs on the server thread (Fabric) or client thread against WorldServer (Forge);
        // either way, simulator ticks against the server world so chunks page from disk on demand.
        List<TickState> path = mc.runOnServerThread(() -> dirtyTick < 0
                ? runner.simulate(inputData)
                : runner.simulateFrom(dirtyTick, inputData));
        boxController.clearAll();
        for (TickState s : path) {
            boxController.add(s);
        }
    }

    /** Loader fires this on disconnect / world join: cached entity, recorded path, checkpoints,
     *  and InputData all reset so the next simulation starts fresh in the new world. */
    public void onWorldChange() {
        runner.invalidate();
        boxController.clearAll();
        inputData.resetToDefault();
        startInitialized = false;
    }

    public void setStartToPlayer() {
        if (!mc.isReady()) return;
        runner.setStartPosition(mc.getPlayerPosition());
        onUserChange(-1);
    }

    private void handleStartPositionChange(Vec3dCore pos) {
        runner.setStartPosition(pos);
        onUserChange(-1);
    }

    private void handleStartYawChange(float yaw) {
        runner.setStartYaw(yaw);
        onUserChange(-1);
    }

    private void handleTickYawChange(int rowIndex, float absoluteYaw) {
        if (rowIndex < 0 || rowIndex >= inputData.getRows().size()) return;
        // InputRow.yaw is a delta added to the prior tick's entity yaw by Simulator.applyYaw.
        // Box index for that row is rowIndex (states[rowIndex] = entity yaw BEFORE row[rowIndex] applies).
        float prevTickYaw = boxController.getYaw(rowIndex);
        float delta = absoluteYaw - prevTickYaw;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        inputData.getRows().get(rowIndex).setYaw(delta);
        onUserChange(rowIndex);
    }

    private void onUserChange(int dirtyTick) {
        saveController.markDirty();
        runSimulation(dirtyTick);
    }

    /** Loader calls this from its world-render hook to advance drag picking. */
    public void tickDrag() {
        if (!mc.isReady()) return;
        if (!startInitialized) {
            runner.setStartPosition(mc.getPlayerPosition());
            runSimulation();
            startInitialized = true;
        }
        dragController.tick(mc.getEyePosition(), mc.getLookDirection(), mc.isMousePressedLeft(), isControlPanelOpen());
        yawGizmo.tick(
                mc.getEyePosition(),
                mc.getLookDirection(),
                mc.isMousePressedRight(),
                mc.getCursorScreenX(),
                mc.getCursorScreenY(),
                isControlPanelOpen()
        );
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

    public boolean shouldSuppressRightClick() {
        if (isControlPanelOpen()) return false;
        if (yawGizmo.isEngaged()) return true;
        if (!mc.isReady()) return false;
        return yawGizmo.isCursorOverAnyBox(mc.getEyePosition(), mc.getLookDirection());
    }

    public YawGizmoController getYawGizmo() {
        return yawGizmo;
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

    public SelectionManager getSelection() {
        return selection;
    }

    public InputData getInputData() {
        return inputData;
    }

    public void setSaveStore(SaveStore saveStore) {
        saveController.setSaveStore(saveStore);
    }

    public SaveStore getSaveStore() {
        return saveController.getSaveStore();
    }

    public void setPlaybackBridge(PlaybackBridge bridge) {
        playback.setBridge(bridge);
    }

    public PlaybackController getPlayback() {
        return playback;
    }

    public boolean isPlaybackRunning() {
        return playback.isRunning();
    }

    public void tickPlayback() {
        playback.tick();
    }

    public void postTickPlayback() {
        playback.postTick();
    }

    public void renderPlayback() {
        playback.renderFrame();
    }
}
