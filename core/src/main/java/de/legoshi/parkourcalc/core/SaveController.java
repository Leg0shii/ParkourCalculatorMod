package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveBrowseResult;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphPresetIO;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.undo.UndoController;
import de.legoshi.parkourcalc.core.undo.UndoJournal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SaveController {

    private static final String LAST_OPEN_FILE = ".lastopen";

    private final InputData inputData;
    private final SimulationRunner runner;
    private final MinecraftAccess mc;
    private final Runnable retriggerSimulation;

    private FileSystemSaveStore store;
    private FileSystemSaveStore graphStore;
    private AngleSolverState angleSolver;
    private AngleSolverEngine solverEngine;
    private BoxController boxController;
    private Settings settings;
    private UndoController undo;
    private String currentName;
    private boolean dirty;
    private String preTempSnapshotJson;
    private boolean tempActive;

    public SaveController(InputData inputData, SimulationRunner runner, MinecraftAccess mc, Runnable retriggerSimulation) {
        this.inputData = inputData;
        this.runner = runner;
        this.mc = mc;
        this.retriggerSimulation = retriggerSimulation;
    }

    void setSaveStore(FileSystemSaveStore store) {
        this.store = store;
    }

    void setGraphStore(FileSystemSaveStore graphStore) {
        this.graphStore = graphStore;
    }

    void setAngleSolver(AngleSolverState angleSolver) {
        this.angleSolver = angleSolver;
    }

    void setSolverEngine(AngleSolverEngine solverEngine) {
        this.solverEngine = solverEngine;
    }

    void setUndoController(UndoController undo) {
        this.undo = undo;
    }

    /** Source for the optional per-tick debug dump (Settings.saveDebugValues gates it). */
    void setDebugSource(BoxController boxController, Settings settings) {
        this.boxController = boxController;
        this.settings = settings;
    }

    FileSystemSaveStore getSaveStore() {
        return store;
    }

    void markDirty() {
        this.dirty = true;
    }

    public boolean isTempActive() {
        return tempActive;
    }

    public void beginTempTrajectory() {
        if (tempActive) return;
        List<TickState> states = boxController != null ? boxController.getStates() : null;
        preTempSnapshotJson = SaveIO.snapshotJson(store, inputData, runner.getStartPosition(),
                runner.getStartVelocity(), runner.getStartYaw(), runner.getStartPitch(), angleSolver, states);
        tempActive = true;
    }

    public void restoreInitialTrajectory() {
        if (!tempActive || preTempSnapshotJson == null) return;
        applySnapshotJson(preTempSnapshotJson);
        clearTempTrajectory();
    }

    public void applySnapshotJson(String json) {
        if (json == null) return;
        SaveFile f = SaveIO.parseSafe(json);
        if (f == null || f.start == null) return;
        if (solverEngine != null) solverEngine.onProblemReplaced();
        SaveIO.applyRowsTo(f, inputData);
        SaveIO.applyAngleSolverTo(f, angleSolver);
        resolveGraphPreset();
        runner.invalidate();
        runner.setStartPosition(SaveIO.posOf(f.start));
        runner.setStartVelocity(SaveIO.velOf(f.start));
        runner.setStartYaw(f.start.yaw);
        runner.setStartPitch(f.start.pitch != null ? f.start.pitch : PlaybackController.DEFAULT_PITCH);
        markDirty();
        retriggerSimulation.run();
    }

    public void clearTempTrajectory() {
        tempActive = false;
        preTempSnapshotJson = null;
    }

    public Result<String> saveCopyAs(String rawName) {
        if (store == null) return Result.failure("Save store not initialized.");
        List<TickState> states = boxController != null ? boxController.getStates() : null;
        boolean fullDebug = settings != null && settings.saveDebugValues;
        return SaveIO.save(store, rawName, inputData, runner.getStartPosition(), runner.getStartVelocity(),
                runner.getStartYaw(), runner.getStartPitch(), angleSolver, states, fullDebug);
    }

    public Result<String> save(String name) {
        if (store == null) return Result.failure("Save store not initialized.");
        List<TickState> states = boxController != null ? boxController.getStates() : null;
        boolean fullDebug = settings != null && settings.saveDebugValues;
        Result<String> result = SaveIO.save(store, name, inputData, runner.getStartPosition(), runner.getStartVelocity(), runner.getStartYaw(), runner.getStartPitch(), angleSolver, states, fullDebug);
        if (result.ok) {
            currentName = result.value;
            dirty = false;
            if (undo != null) undo.bindJournal(journalFor(currentName));
            writeLastOpen(currentName);
        }
        return result;
    }

    public Result<SaveFile> load(String name) {
        if (store == null) return Result.failure("Save store not initialized.");
        Result<SaveFile> result = SaveIO.load(store, name);
        if (!result.ok) return result;

        SaveFile.Start s = result.value.start;
        if (solverEngine != null) solverEngine.onProblemReplaced();
        SaveIO.applyRowsTo(result.value, inputData);
        SaveIO.applyAngleSolverTo(result.value, angleSolver);
        resolveGraphPreset();
        // Must precede the setStart* calls: invalidate clears pending*, which they then refill.
        runner.invalidate();
        runner.setStartPosition(SaveIO.posOf(s));
        runner.setStartVelocity(SaveIO.velOf(s));
        runner.setStartYaw(s.yaw);
        runner.setStartPitch(s.pitch != null ? s.pitch : PlaybackController.DEFAULT_PITCH);
        retriggerSimulation.run();
        currentName = name;
        dirty = false;
        clearTempTrajectory();
        if (undo != null) undo.onDocumentReplaced(journalFor(name));
        writeLastOpen(name);
        return result;
    }

    private UndoJournal journalFor(String name) {
        if (store == null || name == null) return null;
        return new UndoJournal(store.getSaveDir().resolve(".history").resolve(name + ".undo"));
    }

    public boolean tryReopenLastSave() {
        if (store == null || currentName != null) return false;
        String name = readLastOpen();
        if (name == null) return false;
        Result<SaveFile> peek = SaveIO.load(store, name);
        if (!peek.ok) return false;
        if (!worldMatches(peek.value.world, store.getWorldDescriptor())) return false;
        return load(name).ok;
    }

    private static boolean worldMatches(SaveFile.World saved, WorldDescriptor current) {
        if (saved == null || current == null) return false;
        if (current.serverAddress != null || saved.serverAddress != null) {
            return current.serverAddress != null && current.serverAddress.equals(saved.serverAddress);
        }
        return current.worldName != null && current.worldName.equals(saved.worldName);
    }

    private void writeLastOpen(String name) {
        if (store == null || name == null) return;
        try {
            Files.createDirectories(store.getSaveDir());
            Files.write(store.getSaveDir().resolve(LAST_OPEN_FILE), name.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private String readLastOpen() {
        if (store == null) return null;
        Path file = store.getSaveDir().resolve(LAST_OPEN_FILE);
        if (!Files.isRegularFile(file)) return null;
        try {
            String name = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? null : name;
        } catch (IOException e) {
            return null;
        }
    }

    private void clearLastOpenIf(String name) {
        if (store == null || name == null || !name.equals(readLastOpen())) return;
        try {
            Files.deleteIfExists(store.getSaveDir().resolve(LAST_OPEN_FILE));
        } catch (IOException ignored) {
        }
    }

    private void resolveGraphPreset() {
        if (angleSolver == null) return;
        String name = angleSolver.getGraphPresetName();
        if (name == null || graphStore == null || !graphStore.exists(name)) return;
        Result<SolverGraph> graph = GraphPresetIO.loadGraph(graphStore, name);
        if (graph.ok) angleSolver.setCustomGraph(graph.value);
    }

    public boolean delete(String name) {
        if (store == null) return false;
        boolean ok = store.moveToRecycleBin(name);
        if (ok) {
            UndoJournal journal = journalFor(name);
            if (journal != null) {
                if (undo != null) undo.unbindIf(journal);
                journal.delete();
            }
            clearLastOpenIf(name);
            if (name.equals(currentName)) currentName = null;
        }
        return ok;
    }

    public void newSession() {
        inputData.clear();
        if (solverEngine != null) solverEngine.onProblemReplaced();
        if (angleSolver != null) angleSolver.reset();
        discardCurrent();
        runner.invalidate();
        if (mc.isReady()) {
            runner.setStartPosition(mc.getPlayerPosition());
        }
        runner.setStartVelocity(Vec3dCore.GROUND_REST_VELOCITY);
        runner.setStartYaw(0.0F);
        runner.setStartPitch(PlaybackController.DEFAULT_PITCH);
        retriggerSimulation.run();
        if (undo != null) undo.onDocumentReplaced(null);
    }

    public void discardCurrent() {
        currentName = null;
        dirty = false;
        clearTempTrajectory();
    }

    public List<SaveInfo> list() {
        if (store == null) return Collections.emptyList();
        return store.list();
    }

    public SaveBrowseResult browse(String relDir) {
        if (store == null) return SaveBrowseResult.empty();
        return store.browse(relDir);
    }

    public String currentName() {
        return currentName;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean exists(String name) {
        if (store == null) return false;
        String sanitized = SaveIO.sanitize(name);
        return sanitized != null && store.exists(sanitized);
    }

    /** Parse, copy into save dir under a non-colliding name, then load. */
    public Result<String> importFromPath(Path source) {
        if (store == null) return Result.failure("Save store not initialized.");
        if (source == null) return Result.failure("No file selected.");

        String json;
        try {
            json = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Result.failure("Failed to read file: " + e.getMessage());
        }

        SaveFile parsed = SaveIO.parseSafe(json);
        if (parsed == null) return Result.failure("Not a valid JSON file.");
        if (parsed.start == null || parsed.start.pos == null || parsed.start.pos.length < 3) {
            return Result.failure("Save file is missing required fields.");
        }
        if (parsed.version != SaveFile.FORMAT_VERSION) {
            return Result.failure("Unsupported save format version: " + parsed.version);
        }

        String stem = source.getFileName().toString();
        String lower = stem.toLowerCase(Locale.US);
        if (lower.endsWith(".json")) stem = stem.substring(0, stem.length() - 5);
        String base = SaveIO.sanitize(stem);
        if (base == null) return Result.failure("Cannot derive save name from filename.");

        String unique = base;
        int suffix = 1;
        while (store.exists(unique)) {
            unique = base + "_" + suffix++;
        }

        try {
            store.write(unique, json);
        } catch (IOException e) {
            return Result.failure("Failed to write save: " + e.getMessage());
        }

        Result<SaveFile> loaded = load(unique);
        if (!loaded.ok) return Result.failure(loaded.error);
        return Result.success(unique);
    }
}
