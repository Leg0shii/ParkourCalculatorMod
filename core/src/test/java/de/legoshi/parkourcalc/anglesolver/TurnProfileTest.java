package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TurnProfileTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;
        final double[] recorded;

        Loaded(ExactJumpModel model, JumpSpec spec, double[] recorded) {
            this.model = model;
            this.spec = spec;
            this.recorded = recorded;
        }
    }

    private static Loaded load(String dir, String res) {
        SaveFile file = HpkStartBenchmark.loadCapture(dir, res);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("capture must build a spec", spec);
        int n = spec.asScenario().numTicks;
        int start = state.getStartTick();
        double[] recorded = new double[n];
        for (int t = 0; t < n; t++) recorded[t] = Angles.wrap(file.debug.get(start + t + 1).yaw);
        return new Loaded(model, spec, recorded);
    }

    @Test
    public void recordedSolveIsPinnedOnTheTurnAndHeldOnTheRunUp() {
        Loaded l = load("d10", J335);
        TurnProfile p = TurnProfile.compute(l.model, l.spec, l.recorded, new AtomicBoolean(false));
        assertTrue(p.lands);
        assertTrue(p.complete);
        assertEquals(21, p.n);
        for (int t = 1; t <= 9; t++) assertTrue("run-up tick " + t + " is held", p.held[t]);
        assertFalse(p.held[10]);
        for (int t = 10; t <= 15; t++) {
            assertTrue("tick " + t + " width " + p.width(t), p.width(t) < TurnProfile.SIG_ANGLE_DEG);
            assertEquals(TurnProfile.TickClass.PINNED, p.classify(t, TurnProfile.pixelDeg(0.5f)));
        }
        assertTrue("tick 20 may under-turn by degrees: " + p.below[20], p.below[20] > 15.0);
        assertTrue("tick 20 may not over-turn: " + p.above[20], p.above[20] < TurnProfile.SIG_ANGLE_DEG);
        assertEquals(TurnProfile.TickClass.FREE, p.classify(20, TurnProfile.pixelDeg(0.5f)));
    }

    @Test
    public void carriedWindowsNeverWidenTowardTheStart() {
        Loaded l = load("d10", J335);
        TurnProfile p = TurnProfile.compute(l.model, l.spec, l.recorded, new AtomicBoolean(false));
        for (int t = 10; t + 1 < p.n; t++) {
            assertTrue(p.below[t] <= p.below[t + 1] + 1e-9);
            assertTrue(p.above[t] <= p.above[t + 1] + 1e-9);
        }
    }

    @Test
    public void aPathThatMissesHasNoBand() {
        Loaded l = load("d10", J335);
        double[] off = l.recorded.clone();
        off[12] += 5.0;
        TurnProfile p = TurnProfile.compute(l.model, l.spec, off, new AtomicBoolean(false));
        assertFalse(p.lands);
        for (int t = 0; t < p.n; t++) assertEquals(0.0, p.width(t), 0.0);
    }

    @Test
    public void pixelSizeMatchesTheGameFormula() {
        assertEquals(0.15, TurnProfile.pixelDeg(0.5f), 1e-9);
        assertTrue(TurnProfile.pixelDeg(0.1f) < TurnProfile.pixelDeg(0.5f));
    }

    @Test
    public void heldTicksComeFromFacingTies() {
        Loaded l = load("d10", J335);
        boolean[] held = TurnProfile.heldTicks(l.spec, l.spec.asScenario().numTicks);
        int count = 0;
        for (boolean h : held) if (h) count++;
        assertEquals(9, count);
    }
}
