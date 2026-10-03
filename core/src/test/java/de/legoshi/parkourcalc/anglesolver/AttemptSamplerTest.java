package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AttemptSamplerTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;
        final double[] recorded;
        final boolean[] held;

        Loaded(ExactJumpModel model, JumpSpec spec, double[] recorded, boolean[] held) {
            this.model = model;
            this.spec = spec;
            this.recorded = recorded;
            this.held = held;
        }
    }

    private static Loaded load() {
        SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        int n = spec.asScenario().numTicks;
        double[] recorded = new double[n];
        for (int t = 0; t < n; t++) recorded[t] = Angles.wrap(file.debug.get(t + 1).yaw);
        return new Loaded(model, spec, recorded, TurnProfile.heldTicks(spec, n));
    }

    private static double[][] spread(int n, double px, int from) {
        double[][] e = new double[n][];
        for (int t = 0; t < n; t++) e[t] = t < from ? new double[0] : new double[] {-px, -px / 2, 0.0, px / 2, px};
        return e;
    }

    @Test
    public void noSpreadLandsEveryAttempt() {
        Loaded l = load();
        int n = l.recorded.length;
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(n, 0.0, 0), 200,
                new AtomicBoolean(false));
        assertEquals(200, s.attempts);
        assertEquals(200, s.landings);
        assertEquals(200, s.landed.length);
        assertEquals(0, s.failed.length);
        for (int t = 0; t < s.landedLo.length; t++) {
            assertEquals(0.0, s.landedLo[t], 1e-9);
            assertEquals(0.0, s.landedHi[t], 1e-9);
        }
    }

    @Test
    public void reservoirsStayBounded() {
        Loaded l = load();
        int n = l.recorded.length;
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(n, 3.0, 10), 3000,
                new AtomicBoolean(false));
        assertTrue(s.failed.length <= AttemptSampler.RESERVOIR);
        assertTrue(s.landed.length <= AttemptSampler.RESERVOIR);
        assertEquals(Math.min(s.landings, AttemptSampler.RESERVOIR), s.landed.length);
        assertEquals(Math.min(s.attempts - s.landings, AttemptSampler.RESERVOIR), s.failed.length);
    }

    @Test
    public void aPixelSpreadOnTheTurnLandsOnlySomeTries() {
        Loaded l = load();
        int n = l.recorded.length;
        double px = TurnProfile.pixelDeg(0.5f);
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(n, 6.0 * px, 10), 2000,
                new AtomicBoolean(false));
        assertEquals(2000, s.attempts);
        assertTrue("rate " + s.rate(), s.rate() < 1.0);
        assertTrue("rate " + s.rate(), s.rate() > 0.0);
        for (int t = 0; t < 10; t++) {
            assertEquals(0.0, s.landedLo[t], 1e-9);
            assertEquals(0.0, s.landedHi[t], 1e-9);
        }
    }

    @Test
    public void theSameSpreadGivesTheSameCounts() {
        Loaded l = load();
        int n = l.recorded.length;
        double px = TurnProfile.pixelDeg(0.5f);
        AttemptSampler.Stats a = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(n, 6.0 * px, 10), 1000,
                new AtomicBoolean(false));
        AttemptSampler.Stats b = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(n, 6.0 * px, 10), 1000,
                new AtomicBoolean(false));
        assertEquals(a.landings, b.landings);
        for (int t = 0; t < n; t++) assertEquals(a.landedLo[t], b.landedLo[t], 0.0);
    }

    @Test
    public void cancelStopsEarly() {
        Loaded l = load();
        AtomicBoolean cancel = new AtomicBoolean(true);
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, spread(l.recorded.length, 1.0, 0),
                5000, cancel);
        assertEquals(0, s.attempts);
    }
}
