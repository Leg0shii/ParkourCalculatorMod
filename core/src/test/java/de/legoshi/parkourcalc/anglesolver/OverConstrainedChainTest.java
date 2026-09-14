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

import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class OverConstrainedChainTest {

    private static final String CAPTURE = "gh454-p2s-overconstrained";
    private static final long POLL_TIMEOUT_MS = 60_000L;
    private static final long MAX_RUNTIME_MS = 20_000L;

    @Test
    public void fullyPinnedNoTurnChainReportsZeroFreeAngles() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull(CAPTURE + ": failed to parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.THOROUGH);
        state.clearResult();

        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        long startMs = System.currentTimeMillis();
        engine.solve();
        long deadline = startMs + POLL_TIMEOUT_MS;
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
        long elapsedMs = System.currentTimeMillis() - startMs;
        SolveResult result = state.getResult();

        System.out.printf(Locale.ROOT, "GH454 p2s: result=%s%n", result == null ? "null" : "present");
        if (result != null) {
            System.out.printf(Locale.ROOT, "GH454 p2s: success=%s met=%d/%d solver=%s elapsedMs=%d durationMs=%d%n",
                    result.isSuccess(), result.getMet(), result.getTotal(), result.getSolver(), elapsedMs,
                    result.getDurationMs());
            System.out.printf(Locale.ROOT, "GH454 p2s: notice=%s%n", result.getNotice());
            for (SolveResult.Detail d : result.getDetails()) {
                System.out.printf(Locale.ROOT, "GH454 p2s: detail %s = %s%n", d.label, d.value);
            }
            for (SolveResult.Outcome o : result.getOutcomes()) {
                if (o.met) continue;
                System.out.printf(Locale.ROOT, "GH454 p2s: unmet %s %s %s found=%s margin=%s%n",
                        o.field, o.tick, o.relation, o.found, o.margin);
            }
        }

        assertNotNull("no result within " + POLL_TIMEOUT_MS + " ms", result);
        assertFalse("the fully pinned chain is infeasible, the solve must not succeed", result.isSuccess());
        assertNotNull("failure carries no notice", result.getNotice());
        assertTrue("notice does not start with the over-constrained diagnostic: " + result.getNotice(),
                result.getNotice().contains("0 free angles"));
        assertNotNull("solver label missing", result.getSolver());
        assertTrue("solver label does not name the pinned chain: " + result.getSolver(),
                result.getSolver().contains("pinned chain"));
        assertTrue("over-constrained solve took " + elapsedMs + " ms, expected under " + MAX_RUNTIME_MS,
                elapsedMs < MAX_RUNTIME_MS);
    }
}
