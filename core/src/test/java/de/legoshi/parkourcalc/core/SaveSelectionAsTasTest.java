package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SaveSelectionAsTasTest {

    private static final class Rig {
        final InputData document = new InputData();
        final SaveController controller;
        final FileSystemSaveStore store;

        Rig(Path dir) {
            SimulationRunner runner = new SimulationRunner(new FakeSimulator());
            controller = new SaveController(document, runner, (MinecraftAccess) null, () -> { });
            store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
            controller.setSaveStore(store);
        }
    }

    @Test
    public void slicePreservesStartStateAndRowsWithoutTouchingDocument() throws Exception {
        Path dir = Files.createTempDirectory("pkc-slice");
        Rig rig = new Rig(dir);

        List<InputRow> rows = new ArrayList<>();
        InputRow r0 = new InputRow();
        r0.setKeyActive(InputRow.Key.W, true);
        r0.setKeyActive(InputRow.Key.SPRINT, true);
        r0.setYaw(12.5f);
        rows.add(r0);
        InputRow r1 = new InputRow();
        r1.setKeyActive(InputRow.Key.JUMP, true);
        rows.add(r1);

        Vec3dCore pos = new Vec3dCore(10.5, 64.0, -3.25);
        Vec3dCore vel = new Vec3dCore(0.12, -0.08, 0.34);

        Result<String> saved = rig.controller.saveSelectionAsNewTas("slice1", rows, Arrays.asList(0, 1), pos, vel, 45f, 10f, null);
        assertTrue(saved.ok);

        Result<SaveFile> loaded = SaveIO.load(rig.store, "slice1");
        assertTrue(loaded.ok);
        SaveFile file = loaded.value;

        assertArrayEquals(new double[] { 10.5, 64.0, -3.25 }, file.start.pos, 0.0);
        assertArrayEquals(new double[] { 0.12, -0.08, 0.34 }, file.start.vel, 0.0);
        assertEquals(45f, file.start.yaw, 0f);
        assertEquals(Float.valueOf(10f), file.start.pitch);

        assertEquals(2, file.rows.size());
        assertTrue(file.rows.get(0).keys.contains("W"));
        assertTrue(file.rows.get(0).keys.contains("SPRINT"));
        assertEquals(Float.valueOf(12.5f), file.rows.get(0).yaw);
        assertTrue(file.rows.get(1).keys.contains("JUMP"));

        assertEquals(0, rig.document.size());
        assertNull(rig.controller.currentName());
    }

    @Test
    public void sliceReanchorsSolverConstraintsAndWindow() throws Exception {
        Path dir = Files.createTempDirectory("pkc-slice-solver");
        Rig rig = new Rig(dir);
        AngleSolverState solver = new AngleSolverState();
        rig.controller.setAngleSolver(solver);
        solver.addConstraint(5);
        solver.addConstraint(7);
        solver.addConstraint(2);
        solver.setStartTick(5);
        solver.setLandingTick(7);

        List<InputRow> rows = new ArrayList<>();
        rows.add(new InputRow());
        rows.add(new InputRow());

        Result<String> saved = rig.controller.saveSelectionAsNewTas("slice-solver", rows, Arrays.asList(5, 6),
                new Vec3dCore(0, 64, 0), Vec3dCore.GROUND_REST_VELOCITY, 0f, 0f, null);
        assertTrue(saved.ok);

        Result<SaveFile> loaded = SaveIO.load(rig.store, "slice-solver");
        assertTrue(loaded.ok);
        SaveFile file = loaded.value;

        assertEquals(2, file.angleSolver.ticks.size());
        assertEquals(0, file.angleSolver.ticks.get(0).tick);
        assertEquals(2, file.angleSolver.ticks.get(1).tick);
        assertEquals(0, file.angleSolver.startTick);
        assertEquals(2, file.angleSolver.landingTick);
        assertEquals(3, solver.tickConstraintsOrNull(5).getConstraints().size()
                + solver.tickConstraintsOrNull(7).getConstraints().size()
                + solver.tickConstraintsOrNull(2).getConstraints().size());
    }

    @Test
    public void emptySelectionIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("pkc-slice-empty");
        Rig rig = new Rig(dir);
        Result<String> saved = rig.controller.saveSelectionAsNewTas("nope", new ArrayList<>(), new ArrayList<>(),
                Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null);
        assertTrue(!saved.ok);
    }
}
