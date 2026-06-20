package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.FileMenu;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * gh-107: the auto-save tick saves a named + dirty TAS at most once per interval while the option
 * is on, and never touches an unnamed or clean session. (Lives in the core package for the
 * package-private SaveController wiring.)
 */
public class AutoSaveTest {

    private static final class NullSimulator implements Simulator {
        private Vec3dCore startPos = Vec3dCore.ZERO;
        private Vec3dCore startVel = Vec3dCore.ZERO;
        private float startYaw;

        @Override public void resetToStart() { }
        @Override public void applyInput(InputRow row) { }
        @Override public void tick() { }
        @Override public Vec3dCore getCurrentPosition() { return Vec3dCore.ZERO; }
        @Override public boolean isCurrentOnGround() { return false; }
        @Override public boolean isCurrentSneaking() { return false; }
        @Override public boolean isCurrentSprinting() { return false; }
        @Override public float getCurrentMoveForward() { return Float.NaN; }
        @Override public float getCurrentMoveStrafe() { return Float.NaN; }
        @Override public boolean isCurrentWallCollision() { return false; }
        @Override public Vec3dCore getCurrentVelocity() { return Vec3dCore.ZERO; }
        @Override public boolean isCurrentSoftCollision() { return false; }
        @Override public double getCurrentCollisionAngleDegrees() { return Double.NaN; }
        @Override public float getCurrentYaw() { return 0f; }
        @Override public List<Vec3dCore> getCurrentSubtickPath() { return Collections.emptyList(); }
        @Override public Vec3dCore getStartPosition() { return startPos; }
        @Override public void setStartPosition(Vec3dCore pos) { startPos = pos; }
        @Override public Vec3dCore getStartVelocity() { return startVel; }
        @Override public void setStartVelocity(Vec3dCore vel) { startVel = vel; }
        @Override public float getStartYaw() { return startYaw; }
        @Override public void setStartYaw(float yaw) { startYaw = yaw; }
        @Override public Checkpoint saveCheckpoint() { return null; }
        @Override public void restoreCheckpoint(Checkpoint checkpoint) { }
        @Override public void invalidate() { }
    }

    private static final class Rig {
        final InputData data = new InputData();
        final Settings settings = new Settings();
        final SaveController controller;
        final FileMenu menu;
        final FileSystemSaveStore store;
        final SimulationRunner runner;

        Rig(Path dir) {
            runner = new SimulationRunner(new NullSimulator());
            controller = new SaveController(data, runner, (MinecraftAccess) null, () -> { });
            store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
            controller.setSaveStore(store);
            data.getRows().add(new InputRow());
            menu = new FileMenu(controller, null, settings, () -> { });
            menu.setAutoSaveIntervalNanosForTests(1L); // first tick arms, the next is already past due
        }
    }

    @Test
    public void autoSaveWritesNamedDirtySessionsAtMostOncePerInterval() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-autosave"));
        rig.settings.autoSave = true;
        assertTrue(rig.controller.save("run").ok);

        rig.controller.markDirty();
        rig.menu.tickAutoSave(); // arms the clock
        assertTrue("arming tick must not save yet", rig.controller.isDirty());
        Thread.sleep(1);
        rig.menu.tickAutoSave(); // past due: saves
        assertFalse("auto-save cleared the dirty flag by saving", rig.controller.isDirty());
        assertEquals("run", rig.controller.currentName());

        long stamp = Files.getLastModifiedTime(rig.store.getSaveDir().resolve("run.json")).toMillis();
        Thread.sleep(5);
        rig.menu.tickAutoSave();
        assertEquals(stamp, Files.getLastModifiedTime(rig.store.getSaveDir().resolve("run.json")).toMillis());
    }

    @Test
    public void autoSaveIgnoresUnnamedSessionsAndCanBeDisabled() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-autosave2"));

        assertTrue("auto-save defaults to on", rig.settings.autoSave);
        rig.settings.autoSave = false;
        assertTrue(rig.controller.save("run").ok);
        rig.controller.markDirty();
        rig.menu.tickAutoSave();
        Thread.sleep(1);
        rig.menu.tickAutoSave();
        assertTrue("auto-save must stay off when disabled", rig.controller.isDirty());

        // On, but unnamed: never opens a popup or writes anything.
        Rig unnamed = new Rig(Files.createTempDirectory("pkc-autosave3"));
        unnamed.settings.autoSave = true;
        unnamed.controller.markDirty();
        unnamed.menu.tickAutoSave();
        Thread.sleep(1);
        unnamed.menu.tickAutoSave();
        assertTrue(unnamed.controller.isDirty());
        assertEquals("nothing written for an unnamed session", 0,
                unnamed.store.list().size());
    }

    @Test
    public void tempApplySuppressesAutoSaveAndRestoresInitialTrajectory() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-temp"));
        rig.controller.setAngleSolver(new AngleSolverState());
        rig.settings.autoSave = true;

        rig.runner.setStartVelocity(new Vec3dCore(0.10, 0.0, 0.20));
        rig.data.getRows().clear();
        rig.data.getRows().add(new InputRow());
        rig.data.getRows().add(new InputRow());
        assertTrue(rig.controller.save("run").ok);

        int origRows = rig.data.getRows().size();
        Vec3dCore origVel = rig.runner.getStartVelocity();
        Path saveFile = rig.store.getSaveDir().resolve("run.json");
        long savedStamp = Files.getLastModifiedTime(saveFile).toMillis();

        rig.controller.beginTempTrajectory();
        assertTrue("temp apply suppresses auto-save", rig.controller.isTempActive());

        rig.runner.setStartVelocity(new Vec3dCore(0.90, 0.0, -0.40));
        rig.data.getRows().clear();
        rig.data.getRows().add(new InputRow());
        rig.controller.markDirty();

        rig.menu.tickAutoSave();
        Thread.sleep(2);
        rig.menu.tickAutoSave();
        assertTrue("suppressed auto-save preserves the dirty flag", rig.controller.isDirty());
        assertEquals("suppressed auto-save writes nothing",
                savedStamp, Files.getLastModifiedTime(saveFile).toMillis());

        rig.controller.restoreInitialTrajectory();
        assertFalse("restore resumes auto-save", rig.controller.isTempActive());
        assertEquals("restore brings back the original rows", origRows, rig.data.getRows().size());
        assertEquals("restore brings back the original start vel x", origVel.x, rig.runner.getStartVelocity().x, 0.0);
        assertEquals("restore brings back the original start vel z", origVel.z, rig.runner.getStartVelocity().z, 0.0);

        rig.menu.tickAutoSave();
        Thread.sleep(2);
        rig.menu.tickAutoSave();
        assertFalse("auto-save resumes and persists after restore", rig.controller.isDirty());
    }

    @Test
    public void saveCopyAsWritesNewFileWithoutSwitchingOrClearingTemp() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-copyas"));
        rig.controller.setAngleSolver(new AngleSolverState());
        assertTrue(rig.controller.save("run").ok);

        rig.controller.beginTempTrajectory();
        rig.runner.setStartVelocity(new Vec3dCore(0.50, 0.0, 0.50));
        rig.controller.markDirty();
        boolean dirtyBefore = rig.controller.isDirty();

        assertTrue(rig.controller.saveCopyAs("copy").ok);

        assertTrue("save-copy wrote the new file", rig.store.exists("copy"));
        assertEquals("save-copy did not switch the active save", "run", rig.controller.currentName());
        assertEquals("save-copy left the dirty flag untouched", dirtyBefore, rig.controller.isDirty());
        assertTrue("save-copy kept the temp trajectory active", rig.controller.isTempActive());
    }
}
