package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-104: Apply must realize the Force-45 solve assumption in the rows -- W + sprint on every
 * Force-45 tick and the strafe key per the solved mask (W-only on the grounded jump tick) -- even
 * when the user forgot the keys. Keep ticks pass through untouched: their keys are what the solve
 * ran. Before this, Apply wrote only A/D, so a missing W or sprint silently desynced the applied
 * TAS from the reported path.
 */
public class ApplyForce45InputsTest {

    private static final int TICKS = 4;

    @Test
    public void applyWritesTheForce45AssumptionAndLeavesKeepTicksAlone() {
        InputData inputs = new InputData();
        BoxController boxes = new BoxController();
        for (int t = 0; t < TICKS; t++) {
            inputs.getRows().add(new InputRow());
            boxes.add(new TickState(new Vec3dCore(0.5, 64.0, 0.5), t == 0, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        // The user forgot W/SPRINT everywhere. Tick 0 is the grounded jump; tick 2 holds the wrong
        // strafe key; tick 3 is a Keep tick with its own strafe-only inputs.
        inputs.get(0).setKeyActive(InputRow.Key.JUMP, true);
        inputs.get(2).setKeyActive(InputRow.Key.D, true);
        inputs.get(3).setKeyActive(InputRow.Key.A, true);

        AngleSolverState state = new AngleSolverState(); // default inputs: FORCE_45
        state.setStartTick(0);
        state.setLandingTick(TICKS);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.tickConstraints(0).getOverride().setSlipperiness(Slipperiness.DEFAULT); // ground under the jump
        state.tickConstraints(3).getOverride().setInputs(AngleSolverState.InputMode.KEEP);

        AtomicInteger resims = new AtomicInteger();
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> resims.incrementAndGet(),
                ExactJumpModel.forMcVersion("1.8.9"));

        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        assertNotNull("solve must finish", state.getResult());
        assertTrue("the unconstrained solve must succeed", state.getResult().isSuccess());

        engine.apply();
        assertEquals("apply retriggers the full resim", 1, resims.get());

        // Tick 0: Force 45, grounded jump -- W + sprint, no strafe (the boost stays aligned).
        assertTrue(inputs.get(0).isKeyActive(InputRow.Key.W));
        assertTrue(inputs.get(0).isKeyActive(InputRow.Key.SPRINT));
        assertTrue("jump key is the user's, untouched", inputs.get(0).isKeyActive(InputRow.Key.JUMP));
        assertFalse(inputs.get(0).isKeyActive(InputRow.Key.A));
        assertFalse(inputs.get(0).isKeyActive(InputRow.Key.D));

        // Ticks 1-2: Force 45 in the air -- W + sprint + the solved strafe (A), wrong D cleared.
        for (int t = 1; t <= 2; t++) {
            assertTrue("W on force-45 tick " + t, inputs.get(t).isKeyActive(InputRow.Key.W));
            assertTrue("sprint on force-45 tick " + t, inputs.get(t).isKeyActive(InputRow.Key.SPRINT));
            assertTrue("strafe A on force-45 tick " + t, inputs.get(t).isKeyActive(InputRow.Key.A));
            assertFalse("stale D cleared on tick " + t, inputs.get(t).isKeyActive(InputRow.Key.D));
        }

        // Tick 3: Keep -- the user's keys pass through untouched.
        assertFalse("keep tick: W not forced", inputs.get(3).isKeyActive(InputRow.Key.W));
        assertFalse("keep tick: sprint not forced", inputs.get(3).isKeyActive(InputRow.Key.SPRINT));
        assertTrue("keep tick: user's A kept", inputs.get(3).isKeyActive(InputRow.Key.A));
        assertFalse(inputs.get(3).isKeyActive(InputRow.Key.D));

        // The plan's yaws were written on every tick.
        for (int t = 0; t < TICKS; t++) {
            assertNotNull("yaw written on tick " + t, inputs.get(t).getYaw());
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
