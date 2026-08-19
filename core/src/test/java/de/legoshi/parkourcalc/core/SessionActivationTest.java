package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionActivationTest {

    private static final class Rig {
        final InputData data = new InputData();
        final SaveController controller;
        final FileSystemSaveStore store;

        Rig(Path dir) {
            SimulationRunner runner = new SimulationRunner(new FakeSimulator());
            controller = new SaveController(data, runner, new FakeMinecraftAccess(), () -> { });
            store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
            controller.setSaveStore(store);
        }
    }

    @Test
    public void markDirtyIsIgnoredWithoutASession() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-session1"));
        assertFalse(rig.controller.isSessionActive());
        rig.controller.markDirty();
        assertFalse(rig.controller.isDirty());
    }

    @Test
    public void newSessionActivatesAndEndSessionDeactivates() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-session2"));
        rig.controller.newSession();
        assertTrue(rig.controller.isSessionActive());
        rig.controller.markDirty();
        assertTrue(rig.controller.isDirty());
        rig.controller.endSession();
        assertFalse(rig.controller.isSessionActive());
        assertFalse(rig.controller.isDirty());
        rig.controller.markDirty();
        assertFalse(rig.controller.isDirty());
    }

    @Test
    public void loadAndSaveActivateTheSession() throws Exception {
        Path dir = Files.createTempDirectory("pkc-session3");
        Rig writer = new Rig(dir);
        assertFalse(writer.controller.isSessionActive());
        assertTrue(writer.controller.save("run").ok);
        assertTrue(writer.controller.isSessionActive());

        Rig reader = new Rig(dir);
        assertTrue(reader.controller.load("run").ok);
        assertTrue(reader.controller.isSessionActive());
    }
}
