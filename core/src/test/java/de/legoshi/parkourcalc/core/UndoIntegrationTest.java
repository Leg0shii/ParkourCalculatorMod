package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.undo.UndoController;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UndoIntegrationTest {

    private static final long STEP = UndoController.POLL_INTERVAL_NANOS + 50_000_000L;

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
        final AngleSolverState solver = new AngleSolverState();
        final SimulationRunner runner;
        final SaveController controller;
        final FileSystemSaveStore store;
        final UndoController<de.legoshi.parkourcalc.core.save.SaveFile> undo;
        final List<Integer> partialResims = new java.util.ArrayList<>();
        int fullResims;
        long now = 1L;

        Rig(Path dir) {
            runner = new SimulationRunner(new NullSimulator());
            runner.setStartVelocity(Vec3dCore.GROUND_REST_VELOCITY);
            controller = new SaveController(data, runner, (MinecraftAccess) null, () -> fullResims++);
            controller.setRetriggerFrom(partialResims::add);
            store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
            controller.setSaveStore(store);
            controller.setAngleSolver(solver);
            undo = new UndoController<>(
                    () -> SaveIO.undoSignature(data, runner.getStartPosition(), runner.getStartVelocity(),
                            runner.getStartYaw(), runner.getStartPitch(), solver),
                    () -> SaveIO.buildUndoSnapshot(data, runner.getStartPosition(), runner.getStartVelocity(),
                            runner.getStartYaw(), runner.getStartPitch(), solver),
                    SaveIO::undoJson,
                    controller::applySnapshotJson);
            controller.setUndoController(undo);
        }

        void settle() {
            now += STEP;
            undo.tick(now);
            now += STEP;
            undo.tick(now);
            undo.awaitPendingCapture();
        }
    }

    @Test
    public void undoRestoresRowsSolverStateAndStart() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-int"));
        rig.data.getRows().add(new InputRow());
        rig.settle();

        InputRow second = new InputRow();
        second.setKeyActive(InputRow.Key.W, true);
        rig.data.getRows().add(second);
        rig.settle();

        rig.solver.addConstraint(5);
        rig.settle();

        rig.runner.setStartPosition(new Vec3dCore(5.0, 64.0, -3.0));
        rig.settle();

        assertTrue(rig.undo.undo());
        assertEquals(Vec3dCore.ZERO.x, rig.runner.getStartPosition().x, 0.0);
        assertEquals(1, rig.solver.tickConstraintsOrNull(5) == null ? 0
                : rig.solver.tickConstraintsOrNull(5).getConstraints().size());

        assertTrue(rig.undo.undo());
        assertNull(rig.solver.tickConstraintsOrNull(5));
        assertEquals(2, rig.data.getRows().size());
        assertTrue(rig.data.getRows().get(1).isKeyActive(InputRow.Key.W));

        assertTrue(rig.undo.undo());
        assertEquals(1, rig.data.getRows().size());
        assertFalse(rig.undo.undo());

        assertTrue(rig.undo.redo());
        assertEquals(2, rig.data.getRows().size());
    }

    @Test
    public void historyPersistsAcrossSessionsThroughLoad() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-persist");

        Rig rig1 = new Rig(dir);
        rig1.data.getRows().add(new InputRow());
        rig1.settle();
        assertTrue(rig1.controller.save("run").ok);
        rig1.undo.awaitPendingCapture();
        assertTrue(Files.isRegularFile(dir.resolve(".history").resolve("run.undo")));

        rig1.data.getRows().add(new InputRow());
        rig1.settle();
        rig1.data.getRows().add(new InputRow());
        rig1.settle();

        Rig rig2 = new Rig(dir);
        assertTrue(rig2.controller.load("run").ok);
        assertEquals(1, rig2.data.getRows().size());

        assertTrue(rig2.undo.undo());
        assertEquals(3, rig2.data.getRows().size());
        assertTrue(rig2.undo.undo());
        assertEquals(2, rig2.data.getRows().size());
        assertTrue(rig2.undo.undo());
        assertEquals(1, rig2.data.getRows().size());
        assertFalse(rig2.undo.undo());
    }

    @Test
    public void deleteRemovesTheJournalFile() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-delete");
        Rig rig = new Rig(dir);
        rig.data.getRows().add(new InputRow());
        rig.settle();
        assertTrue(rig.controller.save("run").ok);
        rig.undo.awaitPendingCapture();

        Path journal = dir.resolve(".history").resolve("run.undo");
        assertTrue(Files.isRegularFile(journal));
        assertTrue(rig.controller.delete("run"));
        assertFalse(Files.exists(journal));
    }

    @Test
    public void undoResimulatesOnlyFromTheFirstChangedTick() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-inc"));
        for (int i = 0; i < 3; i++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            row.setYaw(16.3f + i);
            row.setPitch(-24.75f);
            rig.data.getRows().add(row);
        }
        rig.settle();

        rig.data.getRows().get(2).setKeyActive(InputRow.Key.SNEAK, true);
        rig.settle();

        rig.fullResims = 0;
        rig.partialResims.clear();
        assertTrue(rig.undo.undo());
        assertFalse(rig.data.getRows().get(2).isKeyActive(InputRow.Key.SNEAK));
        assertEquals(0, rig.fullResims);
        assertEquals(Collections.singletonList(2), rig.partialResims);

        rig.partialResims.clear();
        assertTrue(rig.undo.redo());
        assertTrue(rig.data.getRows().get(2).isKeyActive(InputRow.Key.SNEAK));
        assertEquals(0, rig.fullResims);
        assertEquals(Collections.singletonList(2), rig.partialResims);
    }

    @Test
    public void solverOnlyUndoSkipsResimulationEntirely() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-noresim"));
        rig.data.getRows().add(new InputRow());
        rig.settle();

        rig.solver.addConstraint(5);
        rig.settle();

        rig.fullResims = 0;
        rig.partialResims.clear();
        assertTrue(rig.undo.undo());
        assertNull(rig.solver.tickConstraintsOrNull(5));
        assertEquals(0, rig.fullResims);
        assertTrue(rig.partialResims.isEmpty());
    }

    @Test
    public void startChangeUndoFallsBackToFullResimulation() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-fullresim"));
        rig.data.getRows().add(new InputRow());
        rig.settle();

        rig.runner.setStartPosition(new Vec3dCore(3.0, 64.0, 1.0));
        rig.settle();

        rig.fullResims = 0;
        rig.partialResims.clear();
        assertTrue(rig.undo.undo());
        assertEquals(0.0, rig.runner.getStartPosition().x, 0.0);
        assertEquals(1, rig.fullResims);
        assertTrue(rig.partialResims.isEmpty());
    }

    @Test
    public void undoRestoresApplyDeviation() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-dev"));
        rig.data.getRows().add(new InputRow());
        rig.settle();

        rig.solver.setApplyDeviation("Sim left the solved path at T5", AngleSolverState.DeviationKind.WALL);
        rig.settle();

        assertTrue(rig.undo.undo());
        assertNull(rig.solver.getApplyDeviation());
        assertNull(rig.solver.getApplyDeviationKind());

        assertTrue(rig.undo.redo());
        assertEquals("Sim left the solved path at T5", rig.solver.getApplyDeviation());
        assertEquals(AngleSolverState.DeviationKind.WALL, rig.solver.getApplyDeviationKind());
    }

    @Test
    public void undoSnapshotIsDeterministicAndRoundTripStable() throws Exception {
        Rig rig = new Rig(Files.createTempDirectory("pkc-undo-det"));
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.W, true);
        row.setKeyActive(InputRow.Key.SPRINT, true);
        row.setYaw(12.5f);
        row.setYawLocked(true);
        rig.data.getRows().add(row);
        rig.solver.addConstraint(3);
        rig.solver.setLandingTick(7);
        rig.solver.setApplyDeviation("diverged", AngleSolverState.DeviationKind.SNEAK);
        rig.runner.setStartPosition(new Vec3dCore(1.25, 64.0, -9.5));
        rig.runner.setStartYaw(45.0f);

        String s1 = snapshot(rig);
        String s2 = snapshot(rig);
        assertEquals(s1, s2);

        rig.controller.applySnapshotJson(s1);
        String s3 = snapshot(rig);
        assertEquals(s1, s3);
    }

    private static String snapshot(Rig rig) {
        return SaveIO.undoSnapshotJson(rig.data, rig.runner.getStartPosition(), rig.runner.getStartVelocity(),
                rig.runner.getStartYaw(), rig.runner.getStartPitch(), rig.solver);
    }
}
