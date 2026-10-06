package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SenseFinder;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
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
        JumpSpec spec = cap.buildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int start = cap.state.getStartTick();
        List<InputRow> rows = cap.inputs.getRows();
        double[] yaws = new double[sc.numTicks];
        double cur = sc.startYaw;
        for (int k = 0; k < yaws.length; k++) {
            InputRow row = rows.get(start + k);
            Float d = row.getYaw();
            cur = row.isYawLocked() && d != null ? d : cur + (d == null ? 0.0 : d);
            yaws[k] = cur;
        }
        return SenseFinder.turnOf(cap.model, spec, yaws, sc.startPos.x, sc.startPos.z);
    }

    private static SenseFinder.Config config() {
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.minSens = 0.5f;
        cfg.maxSens = 1.0f;
        cfg.coarseSteps = 4000;
        return cfg;
    }

    @Test
    public void pixelSizeRoundTripsThroughTheSensitivity() {
        for (float s : new float[]{0.25f, 0.5f, 0.75f, 0.99376810f, 1.0f}) {
            double p = TurnProfile.pixelDeg(s);
            assertEquals(s, SenseFinder.sensForPixel(p), 1.0e-6);
        }
        assertEquals(200.0, SenseFinder.percent(1.0f), 1.0e-9);
        assertEquals(0.6144, TurnProfile.pixelDeg(1.0f), 1.0e-9);
    }

    @Test
    public void j703TurnIsTheJumpFlickAndTheAirTicks() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        assertEquals(12, turn.angles());
        assertEquals(27, turn.jumpTick());
        assertEquals(-30.218939, turn.deltas[0], 1.0e-5);
        assertEquals(1.7811, turn.jumpFacing(), 1.0e-3);
        double reference = turn.referenceOffset();
        assertFalse("the TAS itself must land", Double.isNaN(reference));
        assertTrue(reference >= 0.0);
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
        assertTrue(w.contains(turn.jumpFacing()));
    }

    @Test
    public void j703CandidatesLandAndAreRanked() throws Exception {
        SenseFinder.Turn turn = loadJ703();
        SenseFinder.Window w = SenseFinder.carriedWindow(turn);
        long t0 = System.nanoTime();
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, config(), w, new AtomicBoolean(false), (s, f) -> { });
        double sec = (System.nanoTime() - t0) / 1e9;
        assertFalse("some sense must make the rounded turn land", found.isEmpty());
        for (SenseFinder.Candidate c : found) {
            assertEquals(turn.angles(), c.pixels.length);
            double again = SenseFinder.offsetOf(turn, c.yaws);
            assertFalse("candidate must land when re-forwarded", Double.isNaN(again));
            assertEquals(c.offset, again, 1.0e-12);
            assertTrue(c.sens >= 0.5f && c.sens <= 1.0f);
            assertTrue(c.sensLo <= c.sens && c.sens <= c.sensHi);
            assertTrue(c.below >= 0.0 && c.above >= 0.0);
            assertTrue(c.below + c.above >= w.width() - 1.0e-9);
            assertEquals(turn.deltas[0], c.pixels[0] * c.pixelDeg, c.pixelDeg);
        }
        List<SenseFinder.Candidate> bySense = new ArrayList<>(found);
        bySense.sort(SenseFinder.by(SenseFinder.Mode.SENSE));
        List<SenseFinder.Candidate> byMargin = new ArrayList<>(found);
        byMargin.sort(SenseFinder.by(SenseFinder.Mode.MARGIN));
        List<SenseFinder.Candidate> byOffset = new ArrayList<>(found);
        byOffset.sort(SenseFinder.by(SenseFinder.Mode.FURTHEST));
        for (int i = 1; i < bySense.size(); i++) assertTrue(bySense.get(i - 1).sens >= bySense.get(i).sens);
        for (int i = 1; i < byMargin.size(); i++) assertTrue(byMargin.get(i - 1).margin() >= byMargin.get(i).margin() - 2.0e-5);
        for (int i = 1; i < byOffset.size(); i++) assertTrue(byOffset.get(i - 1).offset >= byOffset.get(i).offset - 1.0e-6);
        System.out.println(String.format(Locale.ROOT, "j703 sense search: %d candidates in %.2f s, reference offset %.6f",
                found.size(), sec, turn.referenceOffset()));
        print("by sense", bySense, 6);
        print("by margin", byMargin, 6);
    }

    static void print(String title, List<SenseFinder.Candidate> list, int limit) {
        System.out.println(title);
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            SenseFinder.Candidate c = list.get(i);
            StringBuilder px = new StringBuilder();
            for (int k = 0; k < c.pixels.length; k++) {
                if (k > 0) px.append(' ');
                px.append(c.pixels[k]);
            }
            System.out.println(String.format(Locale.ROOT,
                    "  %10.6f%% [%.6f..%.6f] px/deg %.5f jump %.4f (-%.4f/+%.4f) offset %+.6f pixels %s",
                    c.percent(), SenseFinder.percent(c.sensLo), SenseFinder.percent(c.sensHi), c.pixelDeg, c.jumpFacing,
                    c.below, c.above, c.offset, px));
        }
    }
}
