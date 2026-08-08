package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class StratFinderSweepTest {

    @Test
    public void sweepSolvesCanaryStreamsItemsAndCancels() throws Exception {
        SaveFile witness = new Gson().fromJson(
                Fixtures.rawPool("hpk_human/d2/j012_1bm_4.25b"), SaveFile.class);
        ExactJumpModel model = ExactJumpModel.forMcVersion(witness.mcVersion);
        FileSystemSaveStore store = new FileSystemSaveStore(
                Files.createTempDirectory("pkc-stratfinder"), "test", witness.mcVersion, null);

        StratFinder finder = new StratFinder(witness, model, store, 3000L, Double.NaN, true);
        finder.start();

        long deadline = System.currentTimeMillis() + 120_000L;
        while (finder.isRunning() && finder.done() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        finder.cancel();
        while (finder.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse("worker did not stop after cancel", finder.isRunning());
        assertTrue("cancel flag lost", finder.isCancelled());
        assertTrue("enumeration produced no variants", finder.total() > 1);

        List<StratFinder.Item> ranked = finder.ranked();
        assertFalse(ranked.isEmpty());
        StratFinder.Item first = ranked.get(0);
        assertTrue("original is not the top row", first.original);
        assertEquals(0, first.edits);
        assertTrue("canary failed its own re-solve", first.feasible);
        assertFalse("canary flag disagrees", finder.canaryFailed());

        for (StratFinder.Item item : ranked) {
            if (!item.feasible) continue;
            assertNotNull("feasible item without snapshot: " + item.label, item.appliedSnapshotJson);
            SaveFile applied = SaveIO.parseSafe(item.appliedSnapshotJson);
            assertNotNull("snapshot unparseable: " + item.label, applied);
            assertEquals("row count drifted: " + item.label, witness.rows.size(), applied.rows.size());
            assertNotNull("result missing: " + item.label, applied.angleSolver.result);
            boolean shaped = "nt".equals(item.label) || "ja".equals(item.label)
                    || "nt45".equals(item.label) || item.label.startsWith("nt[")
                    || item.label.endsWith("/nt") || item.label.endsWith("/ja")
                    || item.label.contains("/nt[");
            if (!shaped) {
                assertEquals("constraint tick count drifted: " + item.label,
                        witness.angleSolver.ticks.size(), applied.angleSolver.ticks.size());
            }
        }
    }
}
