package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SmoothButtonTest {

    private static final String CAPTURE = "gh398-optimize-2jump";
    private static final long TIMEOUT_MS = 60_000L;

    private final AngleSolverState state = new AngleSolverState();
    private final AngleSolverEngine engine;

    public SmoothButtonTest() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull(CAPTURE + ": failed to parse", file);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
    }

    private SolveResult await() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (state.getResult() == null && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull("no result within " + TIMEOUT_MS + " ms", r);
        return r;
    }

    @Test
    public void smoothRunsOnlyTheSmoothStageOnTheSolvedPathAndStaysLanded() {
        assertFalse("nothing to smooth before a solve", engine.canSmooth());
        engine.solve();
        SolveResult solved = await();
        assertTrue("fast did not land: " + solved.getSolver(), solved.isSuccess());
        assertTrue("a landed solve of the range can be smoothed", engine.canSmooth());

        engine.smooth();
        assertFalse("not while it runs", engine.canSmooth());
        SolveResult smoothed = await();
        assertTrue("smoothing lost the landing: " + smoothed.getSolver(), smoothed.isSuccess());
        assertTrue("the smoothed chain starts from the incumbent: " + smoothed.getSolver(),
                smoothed.getSolver() != null && smoothed.getSolver().startsWith("incumbent"));
        SolveRunRecord rec = engine.lastRunRecord();
        assertNotNull(rec);
        assertEquals("only the smooth stage ran", 1, rec.nodes.size());
        assertEquals("smooth", rec.nodes.get(0).id);
        assertTrue("smoothable again afterwards", engine.canSmooth());

        state.setLandingTick(state.getLandingTick() - 1);
        assertFalse("a changed range has no solved path", engine.canSmooth());
    }
}
