package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.render.ReachProbe;
import de.legoshi.parkourcalc.core.render.TailPatchGate;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TailPatchGateTest {

    @After
    public void resetSources() {
        PathRenderPlan.setConstraintSource(null);
        PathRenderPlan.setLiveSource(null);
        PathRenderPlan.setReachProbe(null);
    }

    private static BoxController boxes(int n) {
        BoxController bc = new BoxController();
        for (int i = 0; i < n; i++) {
            bc.add(new TickState(new Vec3dCore(i * 0.3, 64.0, 0.0), true, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN));
        }
        bc.setPitches(new float[n]);
        return bc;
    }

    private static PathRenderPlan planWithConstraints(BoxController bc, Settings settings) {
        AngleSolverState state = new AngleSolverState();
        state.tickConstraints(1).getConstraints().add(
                Constraint.range(Constraint.Field.X, 0.0, 1.0, true, true));
        PathRenderPlan.setConstraintSource(new AngleSolverConstraintSource(
                state, bc, () -> true, settings, new SelectionManager(null), new ConstraintSelection()));
        return PathRenderPlan.build(bc, settings, new SelectionManager(null));
    }

    @Test
    public void countStableConstraintsAllowPatch() {
        Settings settings = new Settings();
        BoxController bc = boxes(4);
        PathRenderPlan plan = planWithConstraints(bc, settings);
        assertTrue(plan.constraintFaceVerts > 0);
        assertTrue(TailPatchGate.canPatch(true, true, true, 0, false, plan,
                plan.constraintFaceVerts, plan.constraintLineVerts));
    }

    @Test
    public void changedConstraintCountsForceRebuild() {
        Settings settings = new Settings();
        BoxController bc = boxes(4);
        PathRenderPlan plan = planWithConstraints(bc, settings);
        assertFalse(TailPatchGate.canPatch(true, true, true, 0, false, plan,
                plan.constraintFaceVerts + 36, plan.constraintLineVerts));
        assertFalse(TailPatchGate.canPatch(true, true, true, 0, false, plan,
                plan.constraintFaceVerts, plan.constraintLineVerts + 24));
    }

    @Test
    public void reachLinesForceRebuild() {
        Settings settings = new Settings();
        settings.showHitDistanceLines = true;
        settings.hitDistanceSelectedOnly = false;
        BoxController bc = boxes(4);
        PathRenderPlan.setReachProbe(ReachProbe.NONE);
        PathRenderPlan plan = PathRenderPlan.build(bc, settings, new SelectionManager(null));
        assertTrue(plan.reachLineVerts > 0);
        assertEquals(0, plan.constraintLineVerts);
        assertFalse(TailPatchGate.canPatch(true, true, true, 0, false, plan,
                plan.constraintFaceVerts, plan.constraintLineVerts));
    }

    @Test
    public void overlayAndStalenessConditionsForceRebuild() {
        Settings settings = new Settings();
        BoxController bc = boxes(4);
        PathRenderPlan plan = PathRenderPlan.build(bc, settings, new SelectionManager(null));
        assertFalse(TailPatchGate.canPatch(false, true, true, 0, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, false, true, 0, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, false, 0, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, true, 4, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, true, 0, true, plan, 0, 0));
        assertTrue(TailPatchGate.canPatch(true, true, true, 0, false, plan, 0, 0));
    }

    @Test
    public void matchingHitboxLayoutAllowsPatch() {
        Settings settings = new Settings();
        settings.showHitbox = true;
        BoxController bc = boxes(4);
        PathRenderPlan plan = PathRenderPlan.build(bc, settings, new SelectionManager(null));
        assertEquals(4, plan.patch.hitboxEdges());
        assertTrue(TailPatchGate.canPatch(true, true, true, 4, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, true, 0, false, plan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, true, 4, true, plan, 0, 0));

        settings.showFullHitbox = true;
        PathRenderPlan fullPlan = PathRenderPlan.build(bc, settings, new SelectionManager(null));
        assertEquals(12, fullPlan.patch.hitboxEdges());
        assertTrue(TailPatchGate.canPatch(true, true, true, 12, false, fullPlan, 0, 0));
        assertFalse(TailPatchGate.canPatch(true, true, true, 4, false, fullPlan, 0, 0));
    }
}
