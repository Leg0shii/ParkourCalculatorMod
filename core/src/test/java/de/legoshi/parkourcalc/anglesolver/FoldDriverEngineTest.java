package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class FoldDriverEngineTest {

    private static final String J003 = "j003";
    private static final String J154_INNER = "hpk_precise/j154-noturn-ja-inner";
    private static final String J1150_INNER = "hpk_precise/j1150-noturn-inner";

    private static SolveResult solveTier(String pool, boolean thorough, int optSec, long timeoutMs) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(pool));
        assertNotNull(pool + ": capture must parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (thorough) {
            state.setEffort(AngleSolverState.Effort.THOROUGH);
            state.setOptimizeSeconds(optSec);
        } else {
            state.setEffort(AngleSolverState.Effort.FAST);
        }
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull(pool + ": solve must publish a result inside " + timeoutMs + " ms", r);
        return r;
    }

    @Test
    public void j003ThoroughLandsInsideItsDeadline() {
        long t0 = System.currentTimeMillis();
        SolveResult r = solveTier(J003, true, 10, 30000);
        long wall = System.currentTimeMillis() - t0;
        assertTrue("j003 THOROUGH must solve", r.isSuccess());
        assertTrue("j003 THOROUGH objective " + r.getObjectiveValue(),
                r.getObjectiveValue() <= -31.2999);
        assertTrue("j003 THOROUGH wall " + wall + " ms must respect the 10 s deadline class", wall <= 20000);
    }

    @Test
    public void j154InnerFastBeatsTheInGameWitness() {
        SolveResult r = solveTier(J154_INNER, false, 0, 30000);
        assertTrue("j154 inner FAST must solve", r.isSuccess());
        assertTrue("j154 inner FAST objective " + r.getObjectiveValue(),
                r.getObjectiveValue() <= -1599.7001161289918);
    }

    @Test
    public void j1150InnerThoroughReachesTheWitnessValue() {
        SolveResult r = solveTier(J1150_INNER, true, 10, 30000);
        assertTrue("j1150 inner THOROUGH must solve", r.isSuccess());
        assertTrue("j1150 inner THOROUGH objective " + r.getObjectiveValue(),
                r.getObjectiveValue() >= -2805.2990460856336);
    }

    @Test
    public void j154InnerFastReSolvesBitIdentical() {
        SolveResult a = solveTier(J154_INNER, false, 0, 30000);
        SolveResult b = solveTier(J154_INNER, false, 0, 30000);
        assertTrue(a.isSuccess());
        assertTrue(b.isSuccess());
        assertEquals("FAST re-solve objective must be bit-identical",
                Double.doubleToLongBits(a.getObjectiveValue()), Double.doubleToLongBits(b.getObjectiveValue()));
        List<SolveResult.YawEntry> ya = a.getYaws();
        List<SolveResult.YawEntry> yb = b.getYaws();
        assertEquals(ya.size(), yb.size());
        for (int i = 0; i < ya.size(); i++) {
            assertEquals("yaw bits at row " + i,
                    Double.doubleToLongBits(ya.get(i).yaw), Double.doubleToLongBits(yb.get(i).yaw));
        }
    }
}
