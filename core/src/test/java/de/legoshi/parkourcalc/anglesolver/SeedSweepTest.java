package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SeedSweepTest {

    private static final String FREE_START = "gh486-cross2-freestart";
    private static final String PINNED = "gh398-optimize-2jump";
    private static final double IN_GAME_FAST_OBJECTIVE = -1909.5801025324097;

    private static final class Harness {
        final AngleSolverState state = new AngleSolverState();
        final AngleSolverEngine engine;

        Harness(String capture) {
            SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
            assertNotNull(capture + ": failed to parse", file);
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            SaveIO.applyAngleSolverTo(file, state);
            state.clearResult();
            engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        }

        SolveResult multiStart(long timeoutMs) {
            state.setGraphPresetName(BuiltinGraphs.MULTI_START_PRESET);
            return solve(AngleSolverState.Effort.CUSTOM, BuiltinGraphs.fastMultiStart(), timeoutMs);
        }

        SolveResult solve(AngleSolverState.Effort effort, SolverGraph graph, long timeoutMs) {
            engine.solve(effort, false, graph);
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
            SolveResult r = state.getResult();
            assertNotNull("no result within " + timeoutMs + " ms", r);
            assertTrue("no solution: " + r.getSolver(), r.isSuccess());
            return r;
        }

        SolveRunRecord.NodeRun node(String id) {
            SolveRunRecord rec = engine.lastRunRecord();
            assertNotNull("no run record", rec);
            for (SolveRunRecord.NodeRun n : rec.nodes) {
                if (id.equals(n.id)) return n;
            }
            return null;
        }
    }

    @Test
    public void multiStartIsNeverWorseThanFastAndReachesTheInGameBasin() {
        Harness fast = new Harness(FREE_START);
        SolveResult f = fast.solve(AngleSolverState.Effort.FAST, null, 60_000L);
        Harness multi = new Harness(FREE_START);
        SolveResult m = multi.multiStart(120_000L);
        System.out.printf(Locale.ROOT, "GH486 fast obj=%.9f (%d ms)  multi obj=%.9f (%d ms) solver=%s%n",
                f.getObjectiveValue(), f.getDurationMs(), m.getObjectiveValue(), m.getDurationMs(), m.getSolver());
        assertTrue("multi-start " + m.getObjectiveValue() + " is worse than fast " + f.getObjectiveValue(),
                m.getObjectiveValue() <= f.getObjectiveValue());
        assertTrue("multi-start " + m.getObjectiveValue() + " did not reach the in-game fast basin "
                        + IN_GAME_FAST_OBJECTIVE, m.getObjectiveValue() <= IN_GAME_FAST_OBJECTIVE + 1.0e-6);
        SolveRunRecord.NodeRun seeds = multi.node("seeds");
        assertNotNull("seed sweep did not run", seeds);
        assertEquals("seed sweep did not adopt a candidate", "FOUND", seeds.taken);
    }

    @Test
    public void optimizeKeepsTheAppliedFastResultAsIncumbent() {
        Harness h = new Harness(FREE_START);
        SolveResult f = h.solve(AngleSolverState.Effort.FAST, null, 60_000L);
        h.state.setOptimizeSeconds(8);
        SolveResult o = h.solve(AngleSolverState.Effort.THOROUGH, null, 60_000L);
        System.out.printf(Locale.ROOT, "GH486 fast obj=%.9f  optimize obj=%.9f solver=%s%n",
                f.getObjectiveValue(), o.getObjectiveValue(), o.getSolver());
        assertTrue("optimize solver chain does not start from the incumbent: " + o.getSolver(),
                o.getSolver() != null && o.getSolver().startsWith("incumbent"));
        assertTrue("optimize " + o.getObjectiveValue() + " is worse than the fast incumbent " + f.getObjectiveValue(),
                o.getObjectiveValue() <= f.getObjectiveValue() + 1.0e-9);
    }

    @Test
    public void pinnedStartPassesThroughTheSweep() {
        Harness h = new Harness(PINNED);
        SolveResult r = h.multiStart(60_000L);
        SolveRunRecord.NodeRun seeds = h.node("seeds");
        assertNotNull("seed sweep node missing from the run", seeds);
        assertEquals("pinned start must pass through the sweep", "NONE", seeds.taken);
        assertTrue("pinned sweep should be instant, took " + seeds.elapsedNanos / 1_000_000L + " ms",
                seeds.elapsedNanos < 200_000_000L);
        assertTrue(r.getSolver() == null || !r.getSolver().contains("seed sweep"));
    }
}
