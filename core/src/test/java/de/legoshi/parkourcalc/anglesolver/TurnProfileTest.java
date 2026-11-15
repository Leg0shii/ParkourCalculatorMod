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
        SaveFile file = Fixtures.hpkHuman(dir, res);
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
    public void theRecordedPathLandsAndATurnedOneMisses() {
        Loaded l = load("d10", J335);
        assertTrue(TurnProfile.lands(l.model, l.spec, l.recorded));
        double[] off = l.recorded.clone();
        off[12] += 5.0;
        assertFalse(TurnProfile.lands(l.model, l.spec, off));
    }

    @Test
    public void pixelSizeMatchesTheGameFormula() {
        assertEquals(0.15, TurnProfile.pixelDeg(0.5f), 1e-9);
        assertTrue(TurnProfile.pixelDeg(0.1f) < TurnProfile.pixelDeg(0.5f));
    }
}
