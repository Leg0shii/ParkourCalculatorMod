package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

/**
 * Headless check of the ranked-window output of ColdStratFinder on a straight run jump.
 * Inert unless PKC_COLD_STRAT is set; PKC_COLD_STRAT_STEM overrides the capture stem.
 */
public class ColdStratFinderSmokeScreen {

    @Test
    public void ranksRunHoldWindows() {
        Assume.assumeTrue("set PKC_COLD_STRAT=1", "1".equals(System.getenv("PKC_COLD_STRAT")));
        String stem = System.getenv("PKC_COLD_STRAT_STEM");
        if (stem == null || stem.isEmpty()) stem = "hpk_human/d2/j012_1bm_4.25b";
        SaveFile file = ColdTestHarness.loadSave(stem);

        ColdStratFinder.Request req = new ColdStratFinder.Request();
        req.beam.threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        req.beam.bucketBudget = 6;

        ColdStratFinder.Result r = ColdStratFinder.find(file, req,
                ColdBeamSolver.NO_PROGRESS, new AtomicBoolean(false));

        System.out.printf(Locale.ROOT, "%s: built=%d certified=%d feasible=%d strats=%d truncated=%b%n",
                stem, r.candidatesBuilt, r.certified, r.feasible, r.strats.size(), r.truncated);
        int shown = 0;
        for (ColdStratFinder.Strat s : r.strats) {
            System.out.printf(Locale.ROOT, "  [%.4f] %s  (feasible lines=%d, start=(%.4f,%.4f))%n",
                    s.difficulty, s.label(), s.feasibleCount,
                    s.representative.startX, s.representative.startZ);
            if (++shown >= 15) break;
        }
        List<ColdStratFinder.Strat> strats = r.strats;
        assertFalse(stem + ": no run/hold strat found", strats.isEmpty());
    }
}
