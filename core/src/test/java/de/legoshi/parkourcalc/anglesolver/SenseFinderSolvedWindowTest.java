package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SenseFinderSolvedWindowTest {

    @Test
    public void j703SolvedJumpWindowHoldsTheHumanLandingAtOnePointNineThree() throws Exception {
        SenseFinder.Turn turn = SenseFinderTest.loadJ703();
        long t0 = System.nanoTime();
        SenseFinder.Window w = SenseFinder.solvedWindow(turn, 1_500_000_000L, new AtomicBoolean(false), (s, f) -> { });
        double sec = (System.nanoTime() - t0) / 1e9;
        assertNotNull(w);
        assertTrue(w.solved);
        System.out.println(String.format(Locale.ROOT, "j703 solved jump window %.4f .. %.4f (width %.4f) in %.1f s",
                w.lo, w.hi, w.width(), sec));
        assertTrue("a player landed j703 with a 1.93 jump facing", w.contains(1.93));
        assertTrue(w.contains(turn.jumpFacing()));
        assertTrue(w.width() > 0.2);
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.minSens = 0.9f;
        cfg.coarseSteps = 2000;
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, cfg, w, new AtomicBoolean(false), (s, f) -> { });
        found.sort(SenseFinder.by(SenseFinder.Mode.SENSE));
        for (SenseFinder.Candidate c : found) assertTrue(w.contains(c.jumpFacing));
        SenseFinderTest.print("by sense (solved window)", found, 8);
    }
}
