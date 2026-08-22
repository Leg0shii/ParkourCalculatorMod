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

import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class OptimizeVsFastTest {

    private static final String CAPTURE = "gh398-optimize-2jump";

    private static final class Run {
        SolveResult result;
        SolveRunRecord record;
    }

    private static Run solve(AngleSolverState.Effort effort, int optimizeSeconds, long timeoutMs) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull(CAPTURE + ": failed to parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(effort);
        state.setOptimizeSeconds(optimizeSeconds);
        state.clearResult();

        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
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
        Run run = new Run();
        run.result = state.getResult();
        run.record = engine.lastRunRecord();
        return run;
    }

    @Test
    public void optimizeIsNeverWorseThanFastAndPublishesItsProgress() {
        Run fast = solve(AngleSolverState.Effort.FAST, 10, 60_000L);
        Run optimize = solve(AngleSolverState.Effort.THOROUGH, 10, 60_000L);

        assertNotNull("fast: no result", fast.result);
        assertNotNull("optimize: no result", optimize.result);
        assertTrue("fast: no solution", fast.result.isSuccess());
        assertTrue("optimize: no solution", optimize.result.isSuccess());
        System.out.printf(Locale.ROOT, "GH398 fast obj=%.7f  optimize obj=%.7f  samples=%d%n",
                fast.result.getObjectiveValue(), optimize.result.getObjectiveValue(),
                optimize.record == null ? -1 : optimize.record.trajectory.size());

        assertTrue("optimize objective " + optimize.result.getObjectiveValue()
                        + " is worse than fast's " + fast.result.getObjectiveValue(),
                optimize.result.getObjectiveValue() <= fast.result.getObjectiveValue());

        assertNotNull("optimize: no run record", optimize.record);
        assertTrue("optimize published " + optimize.record.trajectory.size()
                        + " incumbents; Cancel would keep the first candidate",
                optimize.record.trajectory.size() >= 2);
    }
}
