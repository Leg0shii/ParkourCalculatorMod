package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.io.OsSystemBridge;
import de.legoshi.parkourcalc.core.perf.Perf;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxDragController;
import de.legoshi.parkourcalc.core.ui.BoxSelectController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.FileMenu;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.HudMessagesPanel;
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
import de.legoshi.parkourcalc.core.ui.ServerEventLogPanel;
import de.legoshi.parkourcalc.core.ui.TickInfoPanel;
import de.legoshi.parkourcalc.core.ui.YawGizmoController;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ui.ConstraintKeyController;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverTable;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverWindow;
import de.legoshi.parkourcalc.core.ui.anglesolver.GraphEditorWindow;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunLog;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.undo.UndoController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Single-instance orchestrator wired by loaders via mixins / event handlers. */
public final class Application {

    private final MinecraftAccess mc;
    private final Simulator simulator;

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
    private BlockPicker blockPicker;
    private AngleSolverState angleSolverState;
    private AngleSolverEngine solverEngine;
    private ConstraintKeyController constraintKeyController;
    private UndoController<de.legoshi.parkourcalc.core.save.SaveFile> undoController;
    private RunTicksController runTicks;
    private final HudMessages hudMessages = new HudMessages();
    private final OsSystemBridge systemBridge = new OsSystemBridge();

    public Application(Simulator simulator, MinecraftAccess mc) {
        this.mc = mc;
        this.simulator = simulator;
        this.selection = new SelectionManager(mc);
        this.runner = new SimulationRunner(simulator);
        this.saveController = new SaveController(inputData, runner, mc, this::runSimulation);
        this.saveController.setRetriggerFrom(this::runSimulation);
        this.startDragController = new StartDragController(runner, boxController, selection,
                saveController::markDirty, this::runSimulation, SimulationRunner.DEFAULT_DRAG_TOLERANCE);
        // Start box is the "Start" anchor: draggable to reposition, and tap-selectable as path index 0.
        this.dragController = new BoxDragController(boxController, startDragController, this::commitStartTap);
        this.selectController = new BoxSelectController(this::pickWorld, this::commitWorldTap);
        this.yawGizmo = new YawGizmoController(
                boxController,
                this::handleStartYawChange,
                this::handleTickYawChange,
                this::handleStartPitchChange,
                this::handleTickPitchChange
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

    private void promptSaveSelectionAsTas(FileMenu fileMenu) {
        Set<Integer> rows = selection.getSelectedRows();
        if (rows.isEmpty()) return;
        int first = Collections.min(rows);
        List<InputRow> copied = new ArrayList<>(rows.size());
        List<Integer> sourceRows = new ArrayList<>(rows.size());
        for (int idx : rows) {
            if (idx >= 0 && idx < inputData.size()) {
                copied.add(inputData.get(idx).copy());
                sourceRows.add(idx);
            }
        }
        if (copied.isEmpty()) return;

        TickState pre = boxController.getState(first);
        Vec3dCore pos = pre != null ? pre.position : runner.getStartPosition();
        Vec3dCore vel = pre != null ? pre.velocity : runner.getStartVelocity();
        float yaw = pre != null ? pre.yaw : runner.getStartYaw();
        float pitch = pre != null ? boxController.getPitch(first) : runner.getStartPitch();
        StartResumeState resume = pre != null ? runner.describeResumeAt(first) : runner.getStartResumeState();
        fileMenu.promptSaveSelectionAsTas(copied, sourceRows, pos, vel, yaw, pitch, resume);
    }

    public void setModVersion(String modVersion) {
        this.modVersion = modVersion;
    }

    public void setupUi() {
        inputOverlay = new InputOverlay(inputData, settings, selection, this::onUserChange,
                this::setStartToPlayer, playback, mc, boxController
        );

        angleSolverState = new AngleSolverState();
        FileSystemSaveStore saveStore = saveController.getSaveStore();
        String mcVersion = saveStore != null ? saveStore.getMcVersion() : null;
        ExactJumpModel forwardModel = ExactJumpModel.forMcVersion(mcVersion);
        constraintKeyController = new ConstraintKeyController(
                mc, angleSolverState, selection, constraintSelection, saveController::markDirty,
                forwardModel.modern(), inputData::size);
        saveController.setAngleSolver(angleSolverState);
        saveController.setDebugSource(boxController, settings);
        AngleSolverTable angleSolverTable = new AngleSolverTable(angleSolverState, settings, selection, constraintSelection, inputData::size);
        inputOverlay.setAngleSolver(angleSolverTable);
        StartStateTable startStateTable = new StartStateTable(runner, () -> onUserChange(-1));
        inputOverlay.setStartState(startStateTable);
        FileSystemSaveStore graphStore = saveStore == null ? null : new FileSystemSaveStore(
                saveStore.getSaveDir().resolve("graphs"), saveStore.getModVersion(), saveStore.getMcVersion(),
                null, GraphPresetIO.infoParser());
        saveController.setGraphStore(graphStore);
        AngleSolverEngine angleSolverEngine = new AngleSolverEngine(angleSolverState, boxController, inputData, this::onUserChange, forwardModel);
        angleSolverEngine.setOnStartMoved(runner::setStartPosition);
        this.solverEngine = angleSolverEngine;
        if (saveStore != null) {
            angleSolverEngine.setRunLog(new SolveRunLog(saveStore.getSaveDir().resolve("runs"),
                    saveStore.getModVersion(), saveStore.getMcVersion()));
        }
        saveController.setSolverEngine(angleSolverEngine);
        undoController = new UndoController<>(
                () -> SaveIO.undoSignature(inputData, runner.getStartPosition(), runner.getStartVelocity(),
                        runner.getStartYaw(), runner.getStartPitch(), runner.getStartResumeState(), angleSolverState),
                () -> SaveIO.buildUndoSnapshot(inputData, runner.getStartPosition(), runner.getStartVelocity(),
                        runner.getStartYaw(), runner.getStartPitch(), runner.getStartResumeState(), angleSolverState),
                SaveIO::undoJson,
                saveController::applySnapshotJson);
        saveController.setUndoController(undoController);
        VelocityMapController velocityMapController = new VelocityMapController(
                angleSolverState, boxController, runner, saveController, inputData, forwardModel,
                this::onUserChange, Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
        GraphEditorWindow graphEditorWindow = new GraphEditorWindow(angleSolverEngine);
        AngleSolverWindow angleSolverWindow = new AngleSolverWindow(angleSolverState, settings, inputData::size, angleSolverEngine, velocityMapController.widget(), graphStore, graphEditorWindow);
        angleSolverWindow.setApplySurfaceState(this::applyPathSurfaceState);
        runTicks = new RunTicksController(angleSolverState, angleSolverEngine, inputData, constraintSelection,
                hudMessages, this::runSimulation, saveController::markDirty, this::pushHudMessage);
        angleSolverWindow.setRunTicksControls(runTicks);

        // In-world constraint visualization (gh-145): plates appear while the solver view is open.
        constraintSource = new de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource(
                angleSolverState, boxController, () -> settings.viewAngleSolver, settings, selection, constraintSelection);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setConstraintSource(constraintSource);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setDeviationTickSource(angleSolverState::getApplyDeviationTick);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setSolverStartTickSource(angleSolverState::getStartTick);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setSolverGoalTickSource(angleSolverState::getLandingTick);
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setLiveSource(
                new de.legoshi.parkourcalc.core.ui.anglesolver.LiveBestPathSource(
                        angleSolverEngine, boxController, () -> settings.viewAngleSolver));
        de.legoshi.parkourcalc.core.render.PathRenderPlan.setReachProbe(new de.legoshi.parkourcalc.core.render.ReachProbe() {
            @Override
            public double eyeHeight(boolean sneaking) {
                return mc.getEyeHeight(sneaking);
            }

            @Override
            public double hitDistance(double originX, double originY, double originZ,
                                      double dirX, double dirY, double dirZ, double maxDistance) {
                return mc.clipBlockDistance(new Vec3dCore(originX, originY, originZ),
                        new Vec3dCore(dirX, dirY, dirZ), maxDistance);
            }
        });

        TickInfoPanel tickInfoPanel = new TickInfoPanel(boxController, inputData, selection, settings, runner, this::pushHudMessage);
        ServerEventLogPanel serverEventLogPanel = new ServerEventLogPanel(runner, selection);
        PerfOverlay perfOverlay = new PerfOverlay();
        FileMenu fileMenu = new FileMenu(saveController, filePicker, settings, this::saveSettings);
        inputOverlay.setSaveSelectionAsTasHandler(() -> promptSaveSelectionAsTas(fileMenu));
        SettingsModal settingsModal = new SettingsModal(settings, this::saveSettings);
        settingsModal.setPairedSimulationHook(simulator.supportsPairedSimulation(), this::applyPairedSimulationChange);
        HudMessagesPanel hudMessagesPanel = new HudMessagesPanel(hudMessages, settings);
        MainWindowOverlay mainWindow = new MainWindowOverlay(
                inputOverlay, inputData, fileMenu, settings, this::saveSettings,tickInfoPanel, perfOverlay,
                settingsModal, systemBridge, saveController::getSaveStore, modVersion, mc, this::undo, this::redo,
                hudMessagesPanel
        );
        mainWindow.setServerEventLogPanel(serverEventLogPanel);
        overlayManager.register(mainWindow);
        overlayManager.register(angleSolverWindow);
        overlayManager.register(graphEditorWindow);
    }

    public void setFilePicker(FilePickerPort filePicker) {
        this.filePicker = filePicker;
    }

    public void setBlockPicker(BlockPicker blockPicker) {
        this.blockPicker = blockPicker;
    }

    public AngleSolverState getAngleSolverState() {
        return angleSolverState;
    }

    public void initSettingsStorage(Path path) {
        this.settingsPath = path;
        SettingsIO.load(path, settings);
        ConstraintText.statsPrecision = settings.solverStatsPrecision;
        if (!simulator.supportsPairedSimulation()) {
            settings.pairedSimulation = false;
        }
        simulator.setPairedSimulation(settings.pairedSimulation);
        simulator.setPairedDamage(settings.pairedDamage);
    }

    public void applyPairedSimulationChange() {
        Vec3dCore startPos = runner.getStartPosition();
        Vec3dCore startVel = runner.getStartVelocity();
        float startYaw = runner.getStartYaw();
        StartResumeState startResume = runner.getStartResumeState();
        mc.runOnServerThread(() -> {
            simulator.setPairedSimulation(settings.pairedSimulation);
            simulator.setPairedDamage(settings.pairedDamage);
            runner.invalidate();
            return null;
        });
        runner.setStartPosition(startPos);
        runner.setStartVelocity(startVel);
        runner.setStartYaw(startYaw);
        runner.setStartResumeState(startResume);
        boxController.clearAll();
        runSimulation();
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
        if (!saveController.isSessionActive()) return;
        long t0 = Perf.now();
        boolean incremental = dirtyTick > 0 && runner.canResumeFrom(dirtyTick)
                && boxController.size() > dirtyTick && !DebugFlags.COMPARE_PARTIAL_SIM;
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
        if (incremental) {
            boxController.replaceFrom(dirtyTick + 1, path.subList(dirtyTick + 1, path.size()));
        } else {
            boxController.clearAll();
            for (TickState s : path) {
                boxController.add(s);
            }
        }
        boxController.setPitches(foldPitches(path.size()));
        if (!startDragController.isDragActive()) {
            selection.retainBelow(boxController.size());
        }
        Perf.stop("runSimulation", t0);
    }

    private float[] foldPitches(int count) {
        float[] pitches = new float[count];
        if (count == 0) return pitches;
        pitches[0] = runner.getStartPitch();
        List<InputRow> rows = inputData.getRows();
        for (int i = 1; i < count; i++) {
            pitches[i] = i - 1 < rows.size()
                    ? PlaybackController.applyPitch(pitches[i - 1], rows.get(i - 1))
                    : pitches[i - 1];
        }
        return pitches;
    }

    /** Fired by the loader on disconnect / world join. */
    public void onWorldChange() {
        runner.invalidate();
        boxController.clearAll();
        inputData.clear();
        saveController.endSession();
        if (undoController != null) undoController.onDocumentReplaced(null);
        if (runTicks != null) runTicks.reset();
        if (angleSolverState != null) angleSolverState.clearResult();
        hudMessages.clearStatus();
        startInitialized = false;
    }

    public void undo() {
        if (undoController == null) return;
        boolean done = undoController.undo();
        pushHudMessage(done ? "Undo" : "Nothing to undo",
                done ? HudMessages.COLOR_DEFAULT : HudMessageStyle.COLOR_WARN);
    }

    public void redo() {
        if (undoController == null) return;
        boolean done = undoController.redo();
        pushHudMessage(done ? "Redo" : "Nothing to redo",
                done ? HudMessages.COLOR_DEFAULT : HudMessageStyle.COLOR_WARN);
    }

    public void pushHudMessage(String text) {
        pushHudMessage(text, HudMessages.COLOR_DEFAULT);
    }

    public void pushHudMessage(String text, int colorArgb) {
        hudMessages.push(text, colorArgb, System.nanoTime());
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
        selection.handleClick(boxController.size() >= 2 ? 1 : 0);
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

    private void handleStartPitchChange(float pitch) {
        runner.setStartPitch(pitch);
        onUserChange(-1);
    }

    private void handleTickPitchChange(int rowIndex, float absolutePitch) {
        if (rowIndex < 0 || rowIndex >= inputData.getRows().size()) return;
        InputRow row = inputData.getRows().get(rowIndex);
        if (row.isPitchLocked()) {
            row.setPitch(absolutePitch);
        } else {
            row.setPitch(absolutePitch - boxController.getPitch(rowIndex));
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
            startInitialized = true;
            saveController.tryReopenLastSave();
        }
        if (undoController != null && (runTicks == null || !runTicks.isRunning())) {
            undoController.tick(System.nanoTime());
        }
        pollSolver();
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
                mc.isCtrlDown(),
                mc.getCursorScreenX(),
                mc.getCursorScreenY(),
                isControlPanelOpen()
        );
    }

    public void onConstraintKey(boolean enter, boolean remove) {
        if (constraintKeyController != null) constraintKeyController.onKey(enter, remove);
    }

    public void removeSelectedConstraints() {
        if (constraintKeyController != null) constraintKeyController.removeSelected();
    }

    public void captureAngleSolverBlock(BlockSelection.Kind kind) {
        if (blockPicker == null || angleSolverState == null || kind == null) return;
        PickedBlock hit = blockPicker.pickLookedAtBlock();
        if (hit == null) return;
        angleSolverState.toggleBlock(new BlockSelection(kind, hit.x, hit.y, hit.z, hit.box, hit.boxes));
    }

    public void clearAngleSolverBlocks() {
        if (angleSolverState != null) angleSolverState.clearBlocks();
    }

    private void pollSolver() {
        if (solverEngine == null) return;
        if (runTicks != null) {
            runTicks.poll();
            if (runTicks.isRunning()) return;
        }
        boolean wasSolving = solverEngine.isSolving();
        solverEngine.poll();
        if (solverEngine.isSolving()) {
            SolveResult live = solverEngine.liveBestResult();
            String text = String.format(Locale.ROOT, "Solving %.1fs", solverEngine.elapsedSeconds());
            if (live != null) text += " · " + live.getMet() + "/" + live.getTotal();
            hudMessages.setStatus(text, HudMessages.COLOR_DEFAULT);
            return;
        }
        hudMessages.clearStatus();
        if (!wasSolving) return;
        SolveResult done = angleSolverState.getResult();
        if (done == null) return;
        if (done.isSuccess() && !done.getYaws().isEmpty()) {
            solverEngine.apply();
            if (angleSolverState.getApplyDeviation() != null) {
                pushHudMessage("Solved · sim diverged at T" + (angleSolverState.getApplyDeviationTick() + 1),
                        HudMessageStyle.COLOR_WARN);
            } else {
                pushHudMessage("Solved · applied", HudMessageStyle.COLOR_OK);
            }
        } else {
            pushHudMessage("No solution · " + done.getMet() + "/" + done.getTotal() + " constraints met",
                    HudMessageStyle.COLOR_DANGER);
        }
    }

    public void solveAngleSolver() {
        if (solverEngine == null) return;
        if (runTicks != null && runTicks.isRunning()) {
            runTicks.cancel();
            return;
        }
        if (solverEngine.isSolving()) {
            solverEngine.cancel();
            pushHudMessage("Solve cancelled", HudMessageStyle.COLOR_WARN);
            return;
        }
        if (!mc.isReady()) return;
        if (runTicks != null) runTicks.start();
        else solverEngine.solve();
    }

    public void setSolverStartTickFromSelection() {
        if (angleSolverState == null) return;
        int tick = firstSelectedRow();
        if (tick < 0) {
            pushHudMessage("No tick selected", HudMessageStyle.COLOR_WARN);
            return;
        }
        angleSolverState.setStartTick(tick);
        saveController.markDirty();
        pushHudMessage("Solver start · T" + (tick + 1));
    }

    public void setSolverLandingTickFromSelection() {
        if (angleSolverState == null) return;
        int tick = firstSelectedRow();
        if (tick < 0) {
            pushHudMessage("No tick selected", HudMessageStyle.COLOR_WARN);
            return;
        }
        angleSolverState.setLandingTick(tick);
        saveController.markDirty();
        pushHudMessage("Solver goal · T" + (tick + 1));
    }

    private int firstSelectedRow() {
        Set<Integer> rows = selection.getSelectedRows();
        return rows.isEmpty() ? -1 : rows.iterator().next();
    }

    public void applyPathSurfaceState() {
        if (angleSolverState == null) return;
        int start = Math.max(0, angleSolverState.getStartTick());
        int end = Math.min(Math.min(inputData.size(), boxController.size() - 1), angleSolverState.getLandingTick());
        if (end <= start) {
            pushHudMessage("Solver range invalid", HudMessageStyle.COLOR_WARN);
            return;
        }
        boolean changed = false;
        for (int t = start; t < end; t++) {
            TickState s = boxController.getState(t + 1);
            if (s == null || !s.hasSurfaceSample()) continue;
            Slipperiness slip = Double.isNaN(s.groundFriction)
                    ? Slipperiness.AIR : Slipperiness.fromFriction(s.groundFriction);
            Medium medium = s.medium;
            boolean slipDefault = slip == angleSolverState.getDefaultSlipperiness();
            boolean mediumNone = medium == Medium.NONE;
            TickConstraints tc = angleSolverState.tickConstraintsOrNull(t);
            if (tc == null && slipDefault && mediumNone) continue;
            StateOverride ov = angleSolverState.tickConstraints(t).getOverride();
            if (slipDefault) ov.clearSlipperiness();
            else ov.setSlipperiness(slip);
            if (mediumNone) {
                ov.clearMedium();
            } else {
                ov.setMedium(medium);
                if (medium == Medium.SOULSAND) ov.setSoulsandCells(Math.max(1, s.soulsandCells));
            }
            changed = true;
        }
        if (changed) saveController.markDirty();
        pushHudMessage("Surface state applied · T" + (start + 1) + "-T" + end);
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

    public de.legoshi.parkourcalc.core.sim.Checkpoint getCheckpoint(int index) {
        return runner.getCheckpoint(index);
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

    public void extendPathAndSolveToBlock(double targetX, double targetY, double targetZ) {
        List<InputRow> rows = this.inputData.getRows();
        int originalSize = rows.size();

        boolean wasAbove = false;
        double startY = runner.getStartPosition().y;
        if (originalSize > 0) {
            TickState lastState = this.boxController.getState(originalSize - 1);
            if (lastState != null) {
                startY = lastState.position.y;
            }
        }
        if (startY > targetY) {
            wasAbove = true;
        }

        // 1. Probe-Airtime-Ticks mit W + Sprint anhängen
        int maxSimTicks = 100;
        for (int i = 0; i < maxSimTicks; i++) {
            InputRow newRow = new InputRow();
            newRow.setKeyActive(InputRow.Key.W, true);
            newRow.setKeyActive(InputRow.Key.SPRINT, true);
            rows.add(newRow);
        }

        this.runSimulation();

        // 2. Exakten Lande-Tick finden
        int crossingIndex = -1;
        for (int i = originalSize; i < rows.size(); i++) {
            TickState state = this.boxController.getState(i);
            if (state == null) continue;

            if (state.position.y > targetY) {
                wasAbove = true;
            }

            if (wasAbove && state.position.y <= targetY && state.velocity.y <= 0) {
                crossingIndex = i;
                break;
            }
        }

        if (crossingIndex == -1) {
            while (rows.size() > originalSize) {
                rows.remove(rows.size() - 1);
            }
            this.runSimulation();
            pushHudMessage("Target height not reached", HudMessageStyle.COLOR_WARN);
            return;
        }

        // 3. Überflüssige Ticks nach der Landung abschneiden
        while (rows.size() > crossingIndex + 1) {
            rows.remove(rows.size() - 1);
        }

        int jumpTick = crossingIndex;
        this.selection.clear();
        this.selection.handleClick(jumpTick);
        this.onConstraintKey(false, false);

        InputRow landingRow = rows.get(jumpTick);
        if (landingRow != null) {
            landingRow.setKeyActive(InputRow.Key.JUMP, true);
        }

        TickConstraints tc = this.angleSolverState.tickConstraints(jumpTick);
        if (tc != null && tc.getOverride() != null) {
            tc.getOverride().setSlipperiness(Slipperiness.DEFAULT);
        }

        this.runSimulation();
        this.angleSolverState.setLandingTick(jumpTick);
        saveController.markDirty();
        this.solveAngleSolver();
        pushHudMessage("Path extended to T" + (jumpTick + 1) + " · solving...", HudMessages.COLOR_DEFAULT);
    }
}

