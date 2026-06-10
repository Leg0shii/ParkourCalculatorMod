package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression for gh-116: with no per-tick slipperiness override the compiled spec must use the panel's
 * DEFAULT slipperiness, never the recorded trajectory's per-tick on-ground state. The old fallback read
 * {@code boxes.getState(t).onGround} for un-annotated ticks, so the solve silently followed the previous
 * sim run (and lagged one re-run behind after a terrain edit). The recorded trajectory may only seed the
 * launch state; ground/air is authored purely by default + overrides.
 */
public class SlipperinessFallbackTest {

    private static final int TICKS = 4;

    /** Compiles the spec for a {@code TICKS}-tick segment whose recorded trajectory is uniformly
     *  grounded/airborne -- the spec must come out identical either way. */
    private static JumpPhysicsInputs compile(AngleSolverState state, boolean recordedOnGround) {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < TICKS; t++) {
            InputRow row = new InputRow();
            row.setKeyActive(InputRow.Key.W, true);
            inputs.getRows().add(row);
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), recordedOnGround, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull("segment should compile", spec);
        return spec.asScenario();
    }

    private static double slip(Slipperiness s) {
        return Double.parseDouble(s.valueLabel);
    }

    @Test
    public void airDefaultIgnoresGroundedTrajectory() {
        // The gh-116 repro: every recorded tick grounded, panel default Air, no overrides.
        AngleSolverState state = new AngleSolverState();
        assertEquals(Slipperiness.AIR, state.getDefaultSlipperiness());
        JumpPhysicsInputs sc = compile(state, true);
        for (int t = 0; t < sc.numTicks; t++) {
            assertTrue("tick " + t + " must be airborne (default Air), not the recorded ground",
                    Double.isNaN(sc.slipAt(t)));
        }
    }

    @Test
    public void groundDefaultIgnoresAirborneTrajectory() {
        // The converse direction: a ground default applies even where the recorded run was airborne.
        AngleSolverState state = new AngleSolverState();
        state.setDefaultSlipperiness(Slipperiness.DEFAULT);
        JumpPhysicsInputs sc = compile(state, false);
        for (int t = 0; t < sc.numTicks; t++) {
            assertEquals("tick " + t + " must run on the default surface",
                    slip(Slipperiness.DEFAULT), sc.slipAt(t), 0.0);
        }
    }

    @Test
    public void perTickOverrideBeatsDefault() {
        AngleSolverState state = new AngleSolverState(); // default Air
        state.tickConstraints(1).getOverride().setSlipperiness(Slipperiness.ICE);
        state.tickConstraints(2).getOverride().setSlipperiness(Slipperiness.DEFAULT);
        JumpPhysicsInputs sc = compile(state, true);
        assertTrue("un-annotated tick inherits the Air default", Double.isNaN(sc.slipAt(0)));
        assertEquals("ICE override grounds the tick at its friction", slip(Slipperiness.ICE), sc.slipAt(1), 0.0);
        assertEquals("DEFAULT override grounds the tick at 0.6", slip(Slipperiness.DEFAULT), sc.slipAt(2), 0.0);
        assertTrue("un-annotated tick inherits the Air default", Double.isNaN(sc.slipAt(3)));
    }
}
