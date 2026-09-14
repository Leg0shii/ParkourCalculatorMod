package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-484: an S tick drops sprint and the sprint key re-engages it while the player is still airborne,
 * which moves the lagged 1.8.9 air factor from 0.02 to 0.026 mid-jump. The capture is the applied solve,
 * so its debug trace is what SimulatorEntity produced for the solved yaws: replaying that trace through
 * the compiled spec pins that the sampled sprint flags and the air-factor lag agree with the sim
 * byte-exactly across the re-engage.
 */
public class MidAirSprintReengageTest {

    private static final String CAPTURE = "gh484-sprint-reengage";

    @Test
    public void solvedSegmentReplaysByteExactAcrossTheReengage() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(CAPTURE));
        assertNotNull(CAPTURE + ": failed to parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("no compiled spec", spec);
        JumpPhysicsInputs sc = spec.asScenario();
        int start = state.getStartTick();
        int n = sc.numTicks;

        int reengage = -1;
        for (int k = 1; k < n; k++) {
            boolean airborne = Double.isNaN(sc.slipPerTick[k]);
            if (sc.sprintPerTick[k] && !sc.sprintPerTick[k - 1] && airborne) reengage = k;
            assertFalse("the segment must be wall-free for this pin, hit at tick " + (start + k),
                    file.debug.get(start + k).wallCollision);
        }
        assertTrue("capture no longer carries a mid-air sprint re-engage", reengage > 0);
        assertTrue("the re-engage must follow a backward (S) tick",
                sc.forwardInputPerTick[reengage - 1] < 0.0F);

        double[] facings = new double[n];
        for (int k = 0; k < n; k++) facings[k] = file.debug.get(start + k + 1).yaw;
        ForwardPath p = model.forward(sc, facings);

        List<String> drift = new ArrayList<String>();
        for (int k = 0; k <= n; k++) {
            SaveFile.DebugTick rec = file.debug.get(start + k);
            double dx = p.posX[k] - rec.pos[0];
            double dz = p.posZ[k] - rec.pos[2];
            if (dx != 0.0 || dz != 0.0) {
                drift.add(String.format(Locale.ROOT, "t=%d dx=%.3e dz=%.3e", start + k, dx, dz));
            }
        }
        assertEquals("model drifts from the recorded sim: " + drift, 0, drift.size());
    }
}
