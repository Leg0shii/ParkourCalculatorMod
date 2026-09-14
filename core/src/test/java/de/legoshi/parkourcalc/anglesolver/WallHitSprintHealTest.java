package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A wall hit in the recorded run breaks sprint, and the post-hit sprint=false samples would compile a
 * spec the remaining jumps cannot satisfy, even though the solve's whole purpose is a path that avoids
 * that wall. The compiled spec heals sprint across an in-window wall-hit loss while the sampled inputs
 * still sustain it; losses from a genuine stop, a soft (grazing) hit, or a pre-window hit are kept.
 */
public class WallHitSprintHealTest {

    private static final float F = 1.0F * 0.98F;
    private static final float SNEAK_F = 0.29400003F;

    private static JumpPhysicsInputs compile(BoxController boxes, int startTick, int numTicks) {
        InputData inputs = new InputData();
        for (int t = 0; t < boxes.size() - 1; t++) inputs.getRows().add(new InputRow());
        AngleSolverState state = new AngleSolverState();
        state.setDefaultInputs(AngleSolverState.InputMode.KEEP);
        state.setDefaultSprint(AngleSolverState.SprintMode.DERIVE);
        state.setStartTick(startTick);
        state.setLandingTick(startTick + numTicks);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { },
                ExactJumpModel.forMcVersion("1.8.9"));
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return spec.asScenario();
    }

    private static TickState sampled(boolean sprinting, float moveForward, boolean wallHit, boolean soft) {
        return new TickState(new Vec3dCore(0.5, 64.0, 0.5), false, false, wallHit, 0f,
                Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, soft, Double.NaN,
                sprinting, moveForward, 0.0F);
    }

    @Test
    public void wallHitSprintLossIsHealedWhileInputsSustain() {
        BoxController boxes = new BoxController();
        boxes.add(sampled(true, F, false, false));   // seed
        boxes.add(sampled(true, F, false, false));   // after tick 0
        boxes.add(sampled(true, F, true, false));    // after tick 1: ran sprinting, hit the wall
        boxes.add(sampled(false, F, false, false));  // after tick 2: sprint broke
        boxes.add(sampled(false, F, false, false));  // after tick 3
        JumpPhysicsInputs sc = compile(boxes, 0, 4);
        assertTrue(sc.sprintAt(1));
        assertTrue("post-hit loss is healed", sc.sprintAt(2));
        assertTrue("heal carries forward", sc.sprintAt(3));
    }

    @Test
    public void healEndsOnAGenuineStop() {
        BoxController boxes = new BoxController();
        boxes.add(sampled(true, F, false, false));
        boxes.add(sampled(true, F, true, false));        // tick 0 ran sprinting, hit the wall
        boxes.add(sampled(false, F, false, false));      // tick 1: healed
        boxes.add(sampled(false, SNEAK_F, false, false)); // tick 2: sneak-scaled forward, a real stop
        boxes.add(sampled(false, F, false, false));      // tick 3: stays stopped
        JumpPhysicsInputs sc = compile(boxes, 0, 4);
        assertTrue(sc.sprintAt(1));
        assertFalse("sneak ends the heal", sc.sprintAt(2));
        assertFalse("the heal does not restart", sc.sprintAt(3));
    }

    @Test
    public void softHitLossIsNotHealed() {
        BoxController boxes = new BoxController();
        boxes.add(sampled(true, F, false, false));
        boxes.add(sampled(true, F, true, true));    // grazing hit: does not break sprint in MC
        boxes.add(sampled(false, F, false, false)); // so this loss came from something else
        boxes.add(sampled(false, F, false, false));
        JumpPhysicsInputs sc = compile(boxes, 0, 3);
        assertTrue(sc.sprintAt(0));
        assertFalse(sc.sprintAt(1));
        assertFalse(sc.sprintAt(2));
    }

    @Test
    public void preWindowHitIsNotHealed() {
        BoxController boxes = new BoxController();
        boxes.add(sampled(true, F, false, false));
        boxes.add(sampled(true, F, true, false));   // hit during tick 0, before the window
        boxes.add(sampled(false, F, false, false));
        boxes.add(sampled(false, F, false, false));
        JumpPhysicsInputs sc = compile(boxes, 1, 2); // window starts at tick 1; its yaws cannot avoid the hit
        assertFalse(sc.sprintAt(0));
        assertFalse(sc.sprintAt(1));
    }

    @Test
    public void lossWithoutAHitIsKept() {
        BoxController boxes = new BoxController();
        boxes.add(sampled(true, F, false, false));
        boxes.add(sampled(true, F, false, false));
        boxes.add(sampled(false, F, false, false));
        boxes.add(sampled(false, F, false, false));
        JumpPhysicsInputs sc = compile(boxes, 0, 3);
        assertTrue(sc.sprintAt(0));
        assertFalse(sc.sprintAt(1));
        assertFalse(sc.sprintAt(2));
    }
}
