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
        final boolean[] edges;

        Loaded(ExactJumpModel model, JumpSpec spec, double[] recorded, boolean[] held, boolean[] edges) {
            this.model = model;
            this.spec = spec;
            this.recorded = recorded;
            this.held = held;
            this.edges = edges;
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
        boolean[] edges = new boolean[n];
        edges[10] = true;
        return new Loaded(model, spec, recorded, TurnProfile.heldTicks(spec, n), edges);
    }

    @Test
    public void zeroScatterLandsEveryAttempt() {
        Loaded l = load();
        AttemptSampler.Scatter sc = new AttemptSampler.Scatter();
        sc.flickRestPx = 0.0;
        sc.flickMovingPx = 0.0;
        sc.smoothPx = 0.0;
        sc.flickOnTickChance = 1.0;
        sc.flickThresholdDeg = 0.0;
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), sc, 200, new AtomicBoolean(false));
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
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), new AttemptSampler.Scatter(), 3000, new AtomicBoolean(false));
        assertTrue(s.failed.length <= AttemptSampler.RESERVOIR);
        assertTrue(s.landed.length <= AttemptSampler.RESERVOIR);
        assertEquals(Math.min(s.landings, AttemptSampler.RESERVOIR), s.landed.length);
        assertEquals(Math.min(s.attempts - s.landings, AttemptSampler.RESERVOIR), s.failed.length);
    }

    @Test
    public void thePinnedSolveAlmostNeverLandsWithHumanScatter() {
        Loaded l = load();
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), new AttemptSampler.Scatter(), 5000, new AtomicBoolean(false));
        assertEquals(5000, s.attempts);
        assertTrue("rate " + s.rate(), s.rate() < 0.01);
        double blameSum = 0.0;
        for (double b : s.blame) blameSum += b;
        assertEquals(1.0, blameSum, 1e-9);
        for (int t = 0; t < 10; t++) assertEquals(0.0, s.blame[t], 0.0);
    }

    @Test
    public void sameSeedGivesSameCounts() {
        Loaded l = load();
        AttemptSampler.Scatter sc = new AttemptSampler.Scatter();
        AttemptSampler.Stats a = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), sc, 2000, new AtomicBoolean(false));
        AttemptSampler.Stats b = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), sc, 2000, new AtomicBoolean(false));
        assertEquals(a.landings, b.landings);
        for (int t = 0; t < a.blame.length; t++) assertEquals(a.blame[t], b.blame[t], 0.0);
    }

    @Test
    public void cancelStopsEarly() {
        Loaded l = load();
        AtomicBoolean cancel = new AtomicBoolean(true);
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), new AttemptSampler.Scatter(), 5000, cancel);
        assertEquals(0, s.attempts);
    }

    @Test
    public void flickTicksAreTheLargeTurns() {
        Loaded l = load();
        AttemptSampler.Stats s = AttemptSampler.sample(l.model, l.spec, l.recorded, l.held, l.edges,
                TurnProfile.pixelDeg(0.5f), new AttemptSampler.Scatter(), 10, new AtomicBoolean(false));
        assertTrue(s.flickTick[10]);
        assertTrue(s.flickTick[11]);
        assertTrue(!s.flickTick[15]);
    }
}
