package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState.PolishDepth;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState.SolveBudget;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CustomBudgetTest {

    @Test
    public void defaultsReproduceFast() {
        SolveBudget b = new SolveBudget();
        assertEquals(16, b.getRestarts());
        assertEquals(4500, b.getMaxEval());
        assertEquals(2, b.getPolishCount());
        assertEquals(PolishDepth.LIGHT, b.getPolishDepth());
        assertEquals(0, b.getTimeBudgetSeconds());
        assertEquals(10, b.getWindow());
        assertEquals(3, b.getCommit());
        assertTrue(b.getUseWindowSolver());
    }

    @Test
    public void settersClampToDocumentedRanges() {
        SolveBudget b = new SolveBudget();
        b.setRestarts(0);          assertEquals(AngleSolverState.MIN_RESTARTS, b.getRestarts());
        b.setRestarts(9999);       assertEquals(AngleSolverState.MAX_RESTARTS, b.getRestarts());
        b.setMaxEval(1);           assertEquals(AngleSolverState.MIN_MAX_EVAL, b.getMaxEval());
        b.setMaxEval(9_999_999);   assertEquals(AngleSolverState.MAX_MAX_EVAL, b.getMaxEval());
        b.setPolishCount(0);       assertEquals(AngleSolverState.MIN_POLISH_COUNT, b.getPolishCount());
        b.setPolishCount(9999);    assertEquals(AngleSolverState.MAX_POLISH_COUNT, b.getPolishCount());
        b.setTimeBudgetSeconds(-5);    assertEquals(0, b.getTimeBudgetSeconds());
        b.setTimeBudgetSeconds(99999); assertEquals(AngleSolverState.MAX_TIME_BUDGET, b.getTimeBudgetSeconds());
        b.setWindow(1);            assertEquals(AngleSolverState.MIN_WINDOW, b.getWindow());
        b.setWindow(99);           assertEquals(AngleSolverState.MAX_WINDOW, b.getWindow());
    }

    @Test
    public void commitStaysWithinOneToWindowMinusOne() {
        SolveBudget b = new SolveBudget();
        b.setWindow(14);
        b.setCommit(99);
        assertEquals("commit clamps to window - 1", 13, b.getCommit());
        b.setCommit(0);
        assertEquals(AngleSolverState.MIN_COMMIT, b.getCommit());
        b.setCommit(9);
        b.setWindow(6);
        assertEquals(5, b.getCommit());
    }

    @Test
    public void resetRestoresDefaults() {
        SolveBudget b = new SolveBudget();
        b.setRestarts(200);
        b.setPolishDepth(PolishDepth.EXHAUSTIVE);
        b.setTimeBudgetSeconds(120);
        b.setUseWindowSolver(false);
        b.resetToDefaults();
        assertEquals(16, b.getRestarts());
        assertEquals(PolishDepth.LIGHT, b.getPolishDepth());
        assertEquals(0, b.getTimeBudgetSeconds());
        assertTrue(b.getUseWindowSolver());
    }

    @Test
    public void stateResetClearsEffortAndBudget() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setRestarts(200);
        s.reset();
        assertEquals(AngleSolverState.Effort.FAST, s.getEffort());
        assertEquals(16, s.getSolveBudget().getRestarts());
    }

    @Test
    public void customBudgetRoundTripsThroughSaveLoad() throws Exception {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        SolveBudget b = s.getSolveBudget();
        b.setRestarts(64);
        b.setMaxEval(20000);
        b.setPolishCount(8);
        b.setPolishDepth(PolishDepth.EXHAUSTIVE);
        b.setTimeBudgetSeconds(30);
        b.setWindow(12);
        b.setCommit(4);
        b.setUseWindowSolver(false);

        AngleSolverState loaded = roundTrip(s);
        assertEquals(AngleSolverState.Effort.CUSTOM, loaded.getEffort());
        SolveBudget lb = loaded.getSolveBudget();
        assertEquals(64, lb.getRestarts());
        assertEquals(20000, lb.getMaxEval());
        assertEquals(8, lb.getPolishCount());
        assertEquals(PolishDepth.EXHAUSTIVE, lb.getPolishDepth());
        assertEquals(30, lb.getTimeBudgetSeconds());
        assertEquals(12, lb.getWindow());
        assertEquals(4, lb.getCommit());
        assertFalse(lb.getUseWindowSolver());
    }

    @Test
    public void zeroTimeBudgetSurvivesRoundTrip() throws Exception {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setRestarts(32);
        s.getSolveBudget().setTimeBudgetSeconds(0);
        AngleSolverState loaded = roundTrip(s);
        assertEquals(32, loaded.getSolveBudget().getRestarts());
        assertEquals(0, loaded.getSolveBudget().getTimeBudgetSeconds());
    }

    @Test
    public void oldSaveWithoutCustomBudgetLoadsFastDefaults() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0,0,0], \"vel\": [0,0,0], \"yaw\": 0.0 },\n" +
                "  \"rows\": [ { \"keys\": [\"W\"], \"yaw\": 0.0 } ],\n" +
                "  \"angleSolver\": { \"startTick\": 0, \"landingTick\": 1, \"axis\": \"X\", \"goal\": \"MAX\" }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setRestarts(200);
        SaveIO.applyAngleSolverTo(file, s);
        assertEquals(AngleSolverState.Effort.FAST, s.getEffort());
        assertEquals(16, s.getSolveBudget().getRestarts());
        assertEquals(10, s.getSolveBudget().getWindow());
        assertEquals(3, s.getSolveBudget().getCommit());
        assertTrue(s.getSolveBudget().getUseWindowSolver());
    }

    @Test
    public void customBudgetWithoutWindowSolverFieldDefaultsToOn() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0,0,0], \"vel\": [0,0,0], \"yaw\": 0.0 },\n" +
                "  \"rows\": [ { \"keys\": [\"W\"], \"yaw\": 0.0 } ],\n" +
                "  \"angleSolver\": { \"startTick\": 0, \"landingTick\": 1, \"axis\": \"X\", \"goal\": \"MAX\",\n" +
                "    \"effort\": \"CUSTOM\", \"customBudget\": { \"restarts\": 64, \"maxEval\": 4500,\n" +
                "      \"polishCount\": 2, \"timeBudgetSeconds\": 0, \"window\": 10, \"commit\": 3 } }\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        AngleSolverState s = new AngleSolverState();
        s.getSolveBudget().setUseWindowSolver(false);
        SaveIO.applyAngleSolverTo(file, s);
        assertEquals(64, s.getSolveBudget().getRestarts());
        assertTrue("absent useWindowSolver must not flip an existing custom save off",
                s.getSolveBudget().getUseWindowSolver());
    }

    private static AngleSolverState roundTrip(AngleSolverState s) throws Exception {
        Path dir = Files.createTempDirectory("pkc-budget-rt");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        InputData in = new InputData();
        in.getRows().add(new InputRow());
        Result<String> saved = SaveIO.save(store, "run", in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, s, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        return out;
    }
}
