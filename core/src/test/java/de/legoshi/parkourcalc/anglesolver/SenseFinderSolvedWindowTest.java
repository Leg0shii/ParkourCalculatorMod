package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SenseFinderSolvedWindowTest {

    @Test
    public void j703ListsBothJumpAnglesWithTheirTurnShapes() throws Exception {
        SenseFinder.Turn turn = SenseFinderTest.loadJ703();
        AtomicBoolean cancel = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        SenseFinder.Window w = SenseFinder.solvedWindow(turn, 750_000_000L, cancel, null);
        double windowSec = (System.nanoTime() - t0) / 1e9;
        assertNotNull(w);
        assertTrue(w.solved);
        assertTrue("a player landed j703 with a 1.93 jump facing", w.contains(1.93));
        assertTrue(w.contains(turn.jumpFacing()));
        long t1 = System.nanoTime();
        List<SenseFinder.Anchor> anchors = SenseFinder.anchors(turn, w, SenseFinder.SIGNIFICANT_ANGLE_DEG, 750_000_000L, 4, cancel, null);
        double anchorSec = (System.nanoTime() - t1) / 1e9;
        long t2 = System.nanoTime();
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.threads = 4;
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, cfg, w, anchors, cancel, null);
        double sweepSec = (System.nanoTime() - t2) / 1e9;
        System.out.println(String.format(Locale.ROOT,
                "j703 window %.4f..%.4f in %.1f s, %d anchors in %.1f s, %d rows in %.1f s",
                w.lo, w.hi, windowSec, anchors.size(), anchorSec, found.size(), sweepSec));
        SenseFinder.Candidate high = best(turn, found, 1.95);
        SenseFinder.Candidate low = best(turn, found, 1.78);
        assertNotNull("a row near jump angle 1.95", high);
        assertNotNull("a row near jump angle 1.78", low);
        assertEquals(0.00184, high.offset, 1.5e-4);
        assertEquals(0.00208, low.offset, 1.5e-4);
        assertTrue("1.95 turns harder on the jump's next tick", high.turnDeg(1) < low.turnDeg(1) - 2.0);
        boolean top = false;
        for (SenseFinder.Candidate c : found) top |= c.percent() >= 190.0;
        assertTrue("a row lands near 200%", top);
        long t3 = System.nanoTime();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (SenseFinder.Candidate c : found) futures.add(pool.submit(() -> SenseFinder.fillMargins(turn, c, 120_000_000L, cancel)));
        for (java.util.concurrent.Future<?> f : futures) f.get();
        pool.shutdown();
        for (SenseFinder.Candidate c : found) {
            assertTrue(c.marginsComplete());
            assertEquals(c.offset, SenseFinder.offsetOf(turn, c.yaws), 1.0e-12);
            for (int k = 0; k < c.pixels.length; k++) if (c.hasMargin(k)) assertTrue(c.margin(k) >= 0.0);
        }
        System.out.println(String.format(Locale.ROOT, "margins of %d rows in %.1f s", found.size(),
                (System.nanoTime() - t3) / 1e9));
        for (SenseFinder.Candidate c : found) assertFalse(Double.isNaN(SenseFinder.offsetOf(turn, c.yaws)));
        SenseFinderTest.print("1.95 and 1.78", turn, java.util.Arrays.asList(high, low), 2);
        SenseFinderTest.print("by weakest margin", turn, SenseFinder.ranked(found, SenseFinder.Mode.WEAKEST), 8);
    }

    private static SenseFinder.Candidate best(SenseFinder.Turn turn, List<SenseFinder.Candidate> found, double jump) {
        SenseFinder.Candidate best = null;
        for (SenseFinder.Candidate c : found) {
            if (Math.abs(c.jumpFacing(turn) - jump) > 0.02) continue;
            if (best == null || c.offset > best.offset) best = c;
        }
        return best;
    }
}
