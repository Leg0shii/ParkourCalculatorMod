package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SenseFinderTest {

    static SenseFinder.Turn loadJ703() throws Exception {
        NoTurnCapture cap = NoTurnCapture.load("hpk_human/d10/j703");
        AngleSolverEngine.PathSnapshot snap = cap.snapshot();
        assertNotNull("the Angle Solver setup must give a path", snap);
        return SenseFinder.turnOf(cap.model, snap);
    }

    @Test
    public void pixelSizeRoundTripsThroughTheSensitivity() {
        for (float s : new float[]{0f, 0.25f, 0.5f, 0.75f, 0.99376810f, 1.0f}) {
            double p = TurnProfile.pixelDeg(s);
            assertEquals(s, SenseFinder.sensForPixel(p), 1.0e-6);
        }
        assertEquals(200.0, SenseFinder.percent(1.0f), 1.0e-9);
        assertEquals(0.6144, TurnProfile.pixelDeg(1.0f), 1.0e-9);
    }

    @Test
    public void j703TurnComesFromTheAngleSolverSetup() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        assertEquals(12, turn.angles());
        assertEquals(27, turn.jumpTick());
        assertEquals(-30.218939, turn.deltas[0], 1.0e-5);
        assertEquals(1.7811, turn.jumpFacing(), 1.0e-3);
        assertEquals(0.00208, turn.referenceOffset(), 1.0e-5);
    }

    @Test
    public void aSpanStartingOnTheJumpTickRoundsTheJumpFromTheFacingBefore() throws Exception {
        NoTurnCapture cap = NoTurnCapture.load("hpk_human/d10/j703");
        cap.state.setStartTick(27);
        AngleSolverEngine.PathSnapshot snap = cap.snapshot();
        assertNotNull(snap);
        assertFalse(snap.freeStartYaw);
        SenseFinder.Turn turn = SenseFinder.turnOf(cap.model, snap);
        assertEquals(0, turn.jumpTick());
        assertEquals(-30.218939, turn.deltas[0], 1.0e-5);
        double p = TurnProfile.pixelDeg(1.0f);
        int[] pixels = new int[turn.angles()];
        pixels[0] = -49;
        double[] yaws = SenseFinder.quantizedYaws(turn, pixels, p);
        assertEquals(turn.before(0) - 49 * p, yaws[0], 1.0e-9);
        assertEquals(1.894, yaws[0], 1.0e-3);
    }

    @Test
    public void j703CarriedWindowSitsOnTheLowerEdgeAndIsBelowOnePixel() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        SenseFinder.Window w = SenseFinder.carriedWindow(turn);
        assertNotNull(w);
        assertFalse(w.solved);
        assertEquals(turn.jumpFacing(), w.lo, 1.0e-3);
        assertEquals(0.0655, w.width(), 2.0e-3);
        assertTrue(w.width() < TurnProfile.pixelDeg(0.5f));
    }

    @Test
    public void j703RowsOverTheFullSenseRangeLandAndAreRanked() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        SenseFinder.Window w = SenseFinder.carriedWindow(turn);
        List<SenseFinder.Anchor> anchors = SenseFinder.anchors(turn, w, 0.025, 0L, 1, null, null);
        assertEquals(1, anchors.size());
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.coarseSteps = 2000;
        long t0 = System.nanoTime();
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, cfg, w, anchors, new AtomicBoolean(false), null);
        double sec = (System.nanoTime() - t0) / 1e9;
        assertFalse("some sense must make the rounded turn land", found.isEmpty());
        boolean low = false;
        for (SenseFinder.Candidate c : found) {
            assertEquals(turn.angles(), c.pixels.length);
            assertEquals(c.offset, SenseFinder.offsetOf(turn, c.yaws), 1.0e-12);
            assertTrue(c.sens >= 0f && c.sens <= 1f);
            assertTrue(c.sensLo <= c.sens && c.sens <= c.sensHi);
            assertTrue(c.below[0] >= 0.0 && c.above[0] >= 0.0);
            low |= c.sens < 0.5f;
        }
        assertTrue("the range reaches below 100%", low);
        for (SenseFinder.Mode mode : SenseFinder.Mode.values()) {
            List<SenseFinder.Candidate> ranked = SenseFinder.ranked(found, mode);
            for (int i = 1; i < ranked.size(); i++) {
                SenseFinder.Candidate a = ranked.get(i - 1);
                SenseFinder.Candidate b = ranked.get(i);
                if (mode == SenseFinder.Mode.SENSE) assertTrue(a.sens >= b.sens);
                if (mode == SenseFinder.Mode.WEAKEST) assertTrue(a.weakest() >= b.weakest() - 2.0e-5);
                if (mode == SenseFinder.Mode.FURTHEST) assertTrue(a.offset >= b.offset - 1.0e-6);
            }
        }
        System.out.println(String.format(Locale.ROOT, "j703 carried sweep: %d rows in %.2f s", found.size(), sec));
    }

    @Test
    public void laterTickMarginsAreMeasuredAroundTheRoundedPath() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        SenseFinder.Window w = SenseFinder.carriedWindow(turn);
        List<SenseFinder.Anchor> anchors = SenseFinder.anchors(turn, w, 0.025, 0L, 1, null, null);
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.coarseSteps = 200;
        cfg.minSens = 0.3f;
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, cfg, w, anchors, new AtomicBoolean(false), null);
        assertFalse(found.isEmpty());
        SenseFinder.Candidate c = found.get(0);
        SenseFinder.fillMargins(turn, c, 300_000_000L, new AtomicBoolean(false));
        assertTrue(c.marginsComplete());
        assertTrue(c.scoped[0]);
        assertFalse(c.scoped[c.pixels.length - 1]);
        for (int k = 1; k < c.pixels.length; k++) {
            if (!c.scoped[k]) continue;
            int tick = turn.turnTicks[k];
            double below = Math.min(SenseFinder.carriedEdge(turn, c.yaws, tick, -1), SenseFinder.MARGIN_CAP_DEG);
            double above = Math.min(SenseFinder.carriedEdge(turn, c.yaws, tick, 1), SenseFinder.MARGIN_CAP_DEG);
            assertTrue(c.below[k] >= Math.floor(below / c.pixelDeg + 1.0e-9) * c.pixelDeg - 1.0e-9);
            assertTrue(c.above[k] >= Math.floor(above / c.pixelDeg + 1.0e-9) * c.pixelDeg - 1.0e-9);
            assertEquals(0.0, Math.IEEEremainder(c.below[k], c.pixelDeg), 1.0e-9);
            assertTrue(c.below[k] <= SenseFinder.MARGIN_CAP_DEG && c.above[k] <= SenseFinder.MARGIN_CAP_DEG);
        }
    }

    static void print(String title, SenseFinder.Turn turn, List<SenseFinder.Candidate> list, int limit) {
        System.out.println(title);
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            SenseFinder.Candidate c = list.get(i);
            StringBuilder px = new StringBuilder();
            for (int k = 0; k < c.pixels.length; k++) {
                px.append(String.format(Locale.ROOT, " %d(%.2f|%s)", c.pixels[k], c.turnDeg(k),
                        c.hasMargin(k) ? String.format(Locale.ROOT, "%.3f", c.margin(k)) : "?"));
            }
            System.out.println(String.format(Locale.ROOT, "  %8.4f%% jump %.4f offset %+.6f weakest %.4f%s",
                    c.percent(), c.jumpFacing(turn), c.offset, c.weakest(), px));
        }
    }
}
