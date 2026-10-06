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
import static org.junit.Assert.assertTrue;

public class SenseFinderTest {

    private static final float MAX_SENS = 1.0f;

    private static final class Loaded {
        final SenseFinder.Turn turn;
        final JumpSpec spec;

        Loaded(SenseFinder.Turn turn, JumpSpec spec) {
            this.turn = turn;
            this.spec = spec;
        }
    }

    private static Loaded load(String capture) throws Exception {
        NoTurnCapture cap = NoTurnCapture.load(capture);
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
        return new Loaded(SenseFinder.turnOf(cap.model, spec, yaws, sc.startPos.x, sc.startPos.z), spec);
    }

    private static SenseFinder.Config config() {
        SenseFinder.Config cfg = new SenseFinder.Config();
        cfg.minSens = 0.5f;
        cfg.maxSens = MAX_SENS;
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
        Loaded l = load("hpk_human/d10/j703");
        SenseFinder.Turn turn = l.turn;
        assertEquals(12, turn.angles());
        assertEquals(27, turn.turnTicks[0]);
        assertEquals(-30.218939, turn.deltas[0], 1.0e-5);
        double reference = turn.referenceOffset();
        assertFalse("the TAS itself must land", Double.isNaN(reference));
        assertTrue(reference >= 0.0);
    }

    @Test
    public void j703CandidatesLandAndAreRanked() throws Exception {
        Loaded l = load("hpk_human/d10/j703");
        SenseFinder.Turn turn = l.turn;
        long t0 = System.nanoTime();
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, config(), new AtomicBoolean(false), (s, f) -> { });
        double sec = (System.nanoTime() - t0) / 1e9;
        assertFalse("some sense must make the quantized turn land", found.isEmpty());
        for (SenseFinder.Candidate c : found) {
            assertEquals(turn.angles(), c.pixels.length);
            assertEquals(turn.angles(), c.hits.length);
            double again = SenseFinder.offsetOf(turn, c.yaws);
            assertFalse("candidate must land when re-forwarded", Double.isNaN(again));
            assertEquals(c.offset, again, 1.0e-12);
            assertTrue(c.sens >= 0.5f && c.sens <= MAX_SENS);
            assertTrue(c.sensLo <= c.sens && c.sens <= c.sensHi);
            for (int h : c.hits) assertTrue(h >= 1);
            assertEquals(turn.deltas[0], c.pixels[0] * c.pixelDeg, c.pixelDeg);
        }
        List<SenseFinder.Candidate> byHits = new ArrayList<>(found);
        byHits.sort(SenseFinder.by(SenseFinder.Mode.HITS));
        List<SenseFinder.Candidate> byOffset = new ArrayList<>(found);
        byOffset.sort(SenseFinder.by(SenseFinder.Mode.FURTHEST));
        for (int i = 1; i < byHits.size(); i++) assertTrue(byHits.get(i - 1).hits[0] >= byHits.get(i).hits[0]);
        for (int i = 1; i < byOffset.size(); i++) assertTrue(byOffset.get(i - 1).offset >= byOffset.get(i).offset - 1.0e-6);
        System.out.println(String.format(Locale.ROOT, "j703 sense search: %d candidates in %.2f s, reference offset %.6f",
                found.size(), sec, turn.referenceOffset()));
        print("by hits", byHits, 12);
        print("by offset", byOffset, 12);
    }

    private static void print(String title, List<SenseFinder.Candidate> list, int limit) {
        System.out.println(title);
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            SenseFinder.Candidate c = list.get(i);
            StringBuilder px = new StringBuilder();
            StringBuilder hits = new StringBuilder();
            for (int k = 0; k < c.pixels.length; k++) {
                if (k > 0) {
                    px.append(' ');
                    hits.append(' ');
                }
                px.append(c.pixels[k]);
                hits.append(c.hits[k]);
            }
            System.out.println(String.format(Locale.ROOT, "  %10.6f%% [%.6f..%.6f] px/deg %.5f offset %+.6f hits %s pixels %s",
                    c.percent(), SenseFinder.percent(c.sensLo), SenseFinder.percent(c.sensHi), c.pixelDeg, c.offset, hits, px));
        }
    }
    @Test
    public void j703JumpAngleWindowIsBelowOnePixelFromHundredPercentUp() throws Exception {
        Loaded l = load("hpk_human/d10/j703");
        SenseFinder.Turn turn = l.turn;
        double[] ref = turn.yaws;
        double width = window(turn, ref, -1) + window(turn, ref, 1);
        System.out.println(String.format(Locale.ROOT, "j703 jump facing window %.4f deg", width));
        assertTrue(width < TurnProfile.pixelDeg(0.5f));
        List<SenseFinder.Candidate> found = SenseFinder.run(turn, config(), new AtomicBoolean(false), (s, f) -> { });
        for (SenseFinder.Candidate c : found) assertEquals(1, c.hits[0]);
    }

    @Test
    public void j703LowSenseGivesTheJumpAngleMoreThanOneHittablePixel() throws Exception {
        Loaded l = load("hpk_human/d10/j703");
        SenseFinder.Config cfg = config();
        cfg.minSens = 0.26f;
        cfg.maxSens = 0.30f;
        cfg.coarseSteps = 800;
        List<SenseFinder.Candidate> found = SenseFinder.run(l.turn, cfg, new AtomicBoolean(false), (s, f) -> { });
        int best = 0;
        for (SenseFinder.Candidate c : found) best = Math.max(best, c.hits[0]);
        assertTrue("expected a 2+ pixel jump angle below 60%, got " + best, best >= 2);
    }

    private static double window(SenseFinder.Turn turn, double[] ref, int sign) {
        for (double d = 0; d < 2.0; d += 0.0005) {
            double[] y = ref.clone();
            for (int t = turn.turnTicks[0]; t < y.length; t++) y[t] = ref[t] + sign * d;
            if (Double.isNaN(SenseFinder.offsetOf(turn, y))) return d;
        }
        return 2.0;
    }
}
