package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
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

@Category(VerySlowSolverTests.class)
public class OptimizeJ154Test {

    private static final String CAPTURE = "gh454-j154-optimize";
    private static final int OPTIMIZE_SECONDS = 120;
    private static final long POLL_TIMEOUT_MS = 200_000L;
    private static final int KNOWN_BEST_MET = 43;

    @Test
    public void optimizeSolvesJ154HeadButterflyNeo() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull(CAPTURE + ": failed to parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.THOROUGH);
        state.setOptimizeSeconds(OPTIMIZE_SECONDS);
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
        SolveRunRecord record = engine.lastRunRecord();

        assertNotNull("no result within " + POLL_TIMEOUT_MS + " ms", result);
        System.out.printf(Locale.ROOT, "GH454 j154: success=%s met=%d/%d obj=%.9f solver=%s elapsedMs=%d durationMs=%d%n",
                result.isSuccess(), result.getMet(), result.getTotal(), result.getObjectiveValue(),
                result.getSolver(), elapsedMs, result.getDurationMs());
        System.out.printf(Locale.ROOT, "GH454 j154: notice=%s%n", result.getNotice());
        for (SolveResult.Detail d : result.getDetails()) {
            System.out.printf(Locale.ROOT, "GH454 j154: detail %s = %s%n", d.label, d.value);
        }
        for (SolveResult.Outcome o : result.getOutcomes()) {
            if (o.met) continue;
            System.out.printf(Locale.ROOT, "GH454 j154: unmet %s %s %s found=%s margin=%s%n",
                    o.field, o.tick, o.relation, o.found, o.margin);
        }
        if (record != null) {
            System.out.printf(Locale.ROOT, "GH454 j154: record status=%s chain=%s obj=%s viol=%s samples=%d%n",
                    record.outcome == null ? null : record.outcome.status,
                    record.outcome == null ? null : record.outcome.chain,
                    record.outcome == null ? null : record.outcome.objective,
                    record.outcome == null ? null : record.outcome.violation,
                    record.trajectory.size());
            for (SolveRunRecord.NodeRun n : record.nodes) {
                System.out.printf(Locale.ROOT, "GH454 j154: node %s (%s) visits=%d ms=%d taken=%s evals=%d%n",
                        n.id, n.label, n.visits, n.elapsedNanos / 1_000_000L, n.taken, n.evals);
            }
            for (SolveRunRecord.Sample s : record.trajectory) {
                System.out.printf(Locale.ROOT, "GH454 j154: sample t=%.2fs obj=%.9f viol=%.3e feasible=%s stage=%s node=%s%n",
                        s.elapsedNanos / 1e9, s.obj, s.viol, s.feasible, s.stage, s.node);
            }
        }

        assertTrue("met " + result.getMet() + "/" + result.getTotal() + " is below the known best of "
                        + KNOWN_BEST_MET, result.getMet() >= KNOWN_BEST_MET);
    }
}
