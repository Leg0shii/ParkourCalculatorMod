package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import de.legoshi.parkourcalc.core.ui.BoxSelectController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.FileMenu;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputOverlay;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.MainWindowOverlay;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.PerfOverlay;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.WorldPick;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import de.legoshi.parkourcalc.core.ui.StartDragController;
import de.legoshi.parkourcalc.core.ui.StartStateTable;
import de.legoshi.parkourcalc.core.ui.SettingsModal;
import de.legoshi.parkourcalc.core.ui.TickInfoPanel;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverTable;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverWindow;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Single-instance orchestrator wired by loaders via mixins / event handlers. */
public final class Application {

    private final MinecraftAccess mc;

    private final InputData inputData = new InputData();
    private final OverlayManager overlayManager = new OverlayManager();
    private final BoxController boxController = new BoxController();
    private final Settings settings = new Settings();
    private final SelectionManager selection;
    private final ConstraintSelection constraintSelection = new ConstraintSelection();
    private de.legoshi.parkourcalc.core.render.ConstraintBoxSource constraintSource = de.legoshi.parkourcalc.core.render.ConstraintBoxSource.NONE;
    private final SimulationRunner runner;
    private final BoxDragController dragController;
    private final StartDragController startDragController;
    private final BoxSelectController selectController;
    private final YawGizmoController yawGizmo;
    private final SaveController saveController;
    private final PlaybackController playback;

    private Path settingsPath;
    private boolean startInitialized;
    private String modVersion = "?";
    private InputOverlay inputOverlay;
    private FilePickerPort filePicker;
    private AngleSolverState angleSolverState;
    private final OsSystemBridge systemBridge = new OsSystemBridge();

    public Application(Simulator simulator, MinecraftAccess mc) {
        this.mc = mc;
        this.selection = new SelectionManager(mc);
        this.runner = new SimulationRunner(simulator);
        this.saveController = new SaveController(inputData, runner, mc, this::runSimulation);
        this.startDragController = new StartDragController(runner, boxController, selection,
                saveController::markDirty, this::runSimulation, SimulationRunner.DEFAULT_MOVE_TICK_TOLERANCE);
        // Start box is the "Start" anchor: draggable to reposition, and tap-selectable as path index 0.
        this.dragController = new BoxDragController(boxController, startDragController, this::commitStartTap);
        this.selectController = new BoxSelectController(this::pickWorld, this::commitWorldTap);
        this.yawGizmo = new YawGizmoController(
                boxController,
                this::handleStartYawChange,
                this::handleTickYawChange
        );
        this.playback = new PlaybackController(inputData, runner, settings);
        this.playback.setStartRangeResolver(this::resolvePlaybackStartRange);
    }

    private PlaybackController.StartRange resolvePlaybackStartRange() {
        if (selection.isEmpty()) return null;

        int rowCount = inputData.size();
        if (rowCount == 0) return null;

        Set<Integer> rows = selection.getSelectedRows();
        int first;
        int stopExclusive;
        if (rows.isEmpty()) {
            first = 0;
            stopExclusive = rowCount;
        } else {
            first = Collections.min(rows);
            int last = Collections.max(rows);
            if (first < 0 || first >= rowCount) return null;
            stopExclusive = (rows.size() == 1) ? rowCount : Math.min(last + 1, rowCount);
        }

        TickState pre = boxController.getState(first);
        Vec3dCore pos = pre != null ? pre.position : runner.getStartPosition();
        Vec3dCore vel = pre != null ? pre.velocity : runner.getStartVelocity();
        float yaw = pre != null ? pre.yaw : runner.getStartYaw();
        return new PlaybackController.StartRange(first, stopExclusive, pos, vel, yaw, runner.getCheckpoint(first));
    }

    public void setModVersion(String modVersion) {
        this.modVersion = modVersion;
    }

    public void setupUi() {
        inputOverlay = new InputOverlay(inputData, settings, selection, this::onUserChange,
                this::setStartToPlayer, playback, mc, boxController
        );

        angleSolverState = new AngleSolverState();
        saveController.setAngleSolver(angleSolverState);
        saveController.setDebugSource(boxController, settings);
        AngleSolverTable angleSolverTable = new AngleSolverTable(angleSolverState, settings, selection, constraintSelection, inputData::size);
        inputOverlay.setAngleSolver(angleSolverTable);
        StartStateTable startStateTable = new StartStateTable(runner, () -> onUserChange(-1));
        inputOverlay.setStartState(startStateTable);
        String mcVersion = saveController.getSaveStore() != null ? saveController.getSaveStore().getMcVersion() : null;
        ExactJumpModel forwardModel = ExactJumpModel.forMcVersion(mcVersion);
        AngleSolverEngine angleSolverEngine = new AngleSolverEngine(angleSolverState, boxController, inputData, this::onUserChange, forwardModel);
        saveController.setSolverEngine(angleSolverEngine);
        VelocityMapController velocityMapController = new VelocityMapController(
                angleSolverState, boxController, runner, saveController, inputData, forwardModel,
                this::onUserChange, Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
        AngleSolverWindow angleSolverWindow = new AngleSolverWindow(angleSolverState, settings, inputData::size, angleSolverEngine, velocityMapController.widget());

        // In-world constraint visualization (gh-145): plates appear while the solver view is open.
        constraintSource = new de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource(
                angleSolverState, boxController, () -> settings.viewAngleSolver, settings, selection, constraintSelection);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setConstraintSource(constraintSource);

        TickInfoPanel tickInfoPanel = new TickInfoPanel(boxController, inputData, selection, settings, runner);
        PerfOverlay perfOverlay = new PerfOverlay();
        FileMenu fileMenu = new FileMenu(saveController, filePicker, settings, this::saveSettings);
        SettingsModal settingsModal = new SettingsModal(settings, this::saveSettings);
        MainWindowOverlay mainWindow = new MainWindowOverlay(
                inputOverlay, inputData, fileMenu, settings, this::saveSettings,tickInfoPanel, perfOverlay,
                settingsModal, systemBridge, saveController::getSaveStore, modVersion, mc
        );
        overlayManager.register(mainWindow);
        overlayManager.register(angleSolverWindow);
    }

    public void setFilePicker(FilePickerPort filePicker) {
        this.filePicker = filePicker;
    }

    public void initSettingsStorage(Path path) {
        this.settingsPath = path;
        SettingsIO.load(path, settings);
        ConstraintText.statsPrecision = settings.solverStatsPrecision;
    }

    public void saveSettings() {
        SettingsIO.save(settingsPath, settings);
    }

    /** Loader calls this each frame with the display height; resolves the auto-scale sentinel once, then persists. */
    public void resolveAutoScaleIfNeeded(int displayHeightPx) {
        if (settings.scaleIndex != Settings.AUTO_SCALE_INDEX) return;
        if (displayHeightPx <= 0) return;
        settings.scaleIndex = Settings.resolveAutoScaleIndex(displayHeightPx);
        saveSettings();
    }

    public void runSimulation() {
        runSimulation(-1);
    }

    private void runSimulation(int dirtyTick) {
        if (!mc.isReady()) return;
        long t0 = Perf.now();
        List<TickState> path = mc.runOnServerThread(() -> dirtyTick < 0
                ? runner.simulate(inputData)
                : runner.simulateFrom(dirtyTick, inputData));
        if (DebugFlags.COMPARE_PARTIAL_SIM && dirtyTick >= 0) {
            Vec3dCore startPos = runner.getStartPosition();
            Vec3dCore startVel = runner.getStartVelocity();
            float startYaw = runner.getStartYaw();
            List<TickState> fresh = mc.runOnServerThread(() -> {
                runner.invalidate();
                runner.setStartPosition(startPos);
                runner.setStartVelocity(startVel);
                runner.setStartYaw(startYaw);
                return runner.simulate(inputData);
            });
            DebugFlags.compareAndLog(path, fresh, dirtyTick);
            path = fresh;
        }
        boxController.clearAll();
        for (TickState s : path) {
            boxController.add(s);
        }
        if (!startDragController.isDragActive()) {
            selection.retainBelow(boxController.size());
        }
        Perf.stop("runSimulation", t0);
    }

    /** Fired by the loader on disconnect / world join. */
    public void onWorldChange() {
        runner.invalidate();
        boxController.clearAll();
        inputData.clear();
        saveController.discardCurrent();
        startInitialized = false;
    }

    public void setStartToPlayer() {
        if (!mc.isReady()) return;
        runner.setStartPosition(mc.getPlayerPosition());
        runner.setStartYaw(mc.getPlayerYaw());
        onUserChange(-1);
    }

    private WorldPick pickWorld(Vec3dCore rayOrigin, Vec3dCore rayDirection) {
        return boxController.pickWorld(rayOrigin, rayDirection, constraintSource);
    }

    private void commitWorldTap(WorldPick pick) {
        if (pick == null) return;
        if (pick.kind == WorldPick.Kind.CONSTRAINT) {
            if (pick.index >= boxController.size() - 1) return;
            selection.handleClick(pick.index + 1);
            selection.requestScrollIntoView();
            constraintSelection.focus(pick.index, pick.constraintIndices);
            return;
        }
        if (pick.index <= 0) return;
        if (pick.index >= boxController.size() - 1) return;
        selection.handleClick(pick.index + 1);
        selection.requestScrollIntoView();
        constraintSelection.clear();
    }

    private void commitStartTap() {
        if (boxController.size() == 0) return;
        selection.handleClick(0);
        selection.requestScrollIntoView();
    }

    private void handleStartYawChange(float yaw) {
        runner.setStartYaw(yaw);
        onUserChange(-1);
    }

    private void handleTickYawChange(int rowIndex, float absoluteYaw) {
        if (rowIndex < 0 || rowIndex >= inputData.getRows().size()) return;
        InputRow row = inputData.getRows().get(rowIndex);
        if (row.isYawLocked()) {
            // Locked rows store the absolute facing directly.
            row.setYaw(absoluteYaw);
        } else {
            // InputRow.yaw is a delta added to states[rowIndex] (pre-row entity yaw) by Simulator.applyYaw.
            float prevTickYaw = boxController.getYaw(rowIndex);
            float delta = absoluteYaw - prevTickYaw;
            while (delta > 180.0f) delta -= 360.0f;
            while (delta < -180.0f) delta += 360.0f;
            row.setYaw(delta);
        }
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
        dragController.tick(
                mc.getEyePosition(),
                mc.getLookDirection(),
                mc.isMousePressedLeft(),
                mc.getCursorScreenX(),
                mc.getCursorScreenY(),
                isControlPanelOpen(),
                mc.isShiftDown()
        );
        selectController.tick(
                mc.getEyePosition(),
                mc.getLookDirection(),
                mc.isMousePressedLeft(),
                mc.getCursorScreenX(),
                mc.getCursorScreenY(),
                isControlPanelOpen()
        );
        yawGizmo.tick(
                mc.getEyePosition(),
                mc.getLookDirection(),
                mc.isMousePressedRight(),
                mc.getCursorScreenX(),
                mc.getCursorScreenY(),
                isControlPanelOpen()
        );
    }

    public void addLandingConstraintsForLookedAtBlock() {
        if (angleSolverState == null) return;
        if (!mc.isReady()) return;
        int[] block = mc.getLookedAtBlock();
        if (block == null || block.length < 3) return;
        int tick = selectedSolverTick();
        if (tick < 0) return;
        int bx = block[0], by = block[1], bz = block[2];
        angleSolverState.addLandingConstraintsForBlock(bx, by, bz, tick,
                isWalledSide(bx - 1, by, bz),
                isWalledSide(bx + 1, by, bz),
                isWalledSide(bx, by, bz - 1),
                isWalledSide(bx, by, bz + 1));
    }

    private boolean isWalledSide(int neighborX, int blockY, int neighborZ) {
        return mc.isBlockSolid(neighborX, blockY + 1, neighborZ)
                || mc.isBlockSolid(neighborX, blockY + 2, neighborZ);
    }

    private int selectedSolverTick() {
        Set<Integer> rows = selection.getSelectedRows();
        if (!rows.isEmpty()) {
            return rows.iterator().next();
        }
        return angleSolverState.getLandingTick();
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
        if (isPlaybackRunning()) return false;
        if (isControlPanelOpen()) return false;
        if (dragController.isDragging()) return true;
        if (!mc.isReady()) return false;
        return yawGizmo.isCursorOverAnyBox(mc.getEyePosition(), mc.getLookDirection())
                || boxController.isCursorOverConstraint(mc.getEyePosition(), mc.getLookDirection(), constraintSource);
    }

    public boolean shouldSuppressRightClick() {
        if (isPlaybackRunning()) return false;
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

    public boolean isEditingYaw() {
        return inputOverlay != null && inputOverlay.isEditingYaw();
    }

    public boolean isEditingPitch() {
        return inputOverlay != null && inputOverlay.isEditingPitch();
    }

    public void navigateYaw(boolean forward) {
        if (inputOverlay != null) inputOverlay.navigateYaw(forward);
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

    public void setSaveStore(FileSystemSaveStore saveStore) {
        saveController.setSaveStore(saveStore);
    }

    public FileSystemSaveStore getSaveStore() {
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
