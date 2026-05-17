package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.save.LoadResult;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.SaveResult;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.FileBrowserOverlay;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.Collections;
import java.util.List;

public final class SaveController implements FileBrowserOverlay.Backend {

    private final InputData inputData;
    private final SimulationRunner runner;
    private final MinecraftAccess mc;
    private final Runnable retriggerSimulation;

    private SaveStore store;
    private String currentName;
    private boolean dirty;

    public SaveController(InputData inputData, SimulationRunner runner, MinecraftAccess mc, Runnable retriggerSimulation) {
        this.inputData = inputData;
        this.runner = runner;
        this.mc = mc;
        this.retriggerSimulation = retriggerSimulation;
    }

    public void setSaveStore(SaveStore store) {
        this.store = store;
    }

    public SaveStore getSaveStore() {
        return store;
    }

    public void markDirty() {
        this.dirty = true;
    }

    @Override
    public SaveResult save(String name) {
        if (store == null) return SaveResult.failure("Save store not initialized.");
        SaveResult result = SaveIO.save(store, name, inputData,
                runner.getStartPosition(), runner.getStartVelocity(), runner.getStartYaw());
        if (result.ok) {
            currentName = result.name;
            dirty = false;
        }
        return result;
    }

    @Override
    public LoadResult load(String name) {
        if (store == null) return LoadResult.failure("Save store not initialized.");
        LoadResult result = SaveIO.load(store, name);
        if (!result.ok) return result;

        SaveIO.AppliedStart start = SaveIO.applyTo(result.file, inputData);
        runner.setStartPosition(start.pos);
        runner.setStartVelocity(start.vel);
        runner.setStartYaw(start.yaw);
        retriggerSimulation.run();
        currentName = name;
        dirty = false;
        return result;
    }

    @Override
    public boolean delete(String name) {
        if (store == null) return false;
        boolean ok = store.moveToRecycleBin(name);
        if (ok && name.equals(currentName)) currentName = null;
        return ok;
    }

    @Override
    public void newSession() {
        inputData.resetToDefault();
        currentName = null;
        if (mc.isReady()) {
            runner.setStartPosition(mc.getPlayerPosition());
        }
        runner.setStartVelocity(Vec3dCore.ZERO);
        runner.setStartYaw(0.0F);
        retriggerSimulation.run();
        dirty = false;
    }

    @Override
    public List<SaveInfo> list() {
        if (store == null) return Collections.emptyList();
        return store.list();
    }

    @Override
    public String currentName() {
        return currentName;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public boolean exists(String name) {
        if (store == null) return false;
        String sanitized = SaveIO.sanitize(name);
        return sanitized != null && store.exists(sanitized);
    }
}
