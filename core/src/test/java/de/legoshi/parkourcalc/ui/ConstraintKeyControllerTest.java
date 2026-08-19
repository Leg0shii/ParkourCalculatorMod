package de.legoshi.parkourcalc.ui;

import de.legoshi.parkourcalc.core.FakeMinecraftAccess;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.ConstraintKeyController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ConstraintKeyControllerTest {

    private static final double HALF = ConstraintDeriver.HALF;
    private static final double EPS = 1.0e-9;
    private static final int TICK = 3;

    private FakeMinecraftAccess mc;
    private AngleSolverState state;
    private ConstraintKeyController controller;

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(new Vec3dCore(x0, y0, z0), new Vec3dCore(x1, y1, z1));
    }

    @Before
    public void setUp() {
        mc = new FakeMinecraftAccess();
        state = new AngleSolverState();
        state.setLandingTick(TICK);
        controller = new ConstraintKeyController(
                mc, state, new SelectionManager(mc), new ConstraintSelection(), () -> { }, false, () -> TICK + 1);
    }

    private List<Constraint> constraints() {
        assertNotNull(state.tickConstraintsOrNull(TICK));
        return state.tickConstraintsOrNull(TICK).getConstraints();
    }

    private Constraint range(Constraint.Field field) {
        for (Constraint c : constraints()) {
            if (c.isRange() && c.getField() == field) return c;
        }
        throw new AssertionError("no range constraint for " + field);
    }

    @Test
    public void footprintOnTheLowerStepIsClippedByTheRiserInTheSameCell() {
        mc.worldBoxes.add(box(5.0, 64.0, 8.0, 6.0, 64.5, 9.0));
        mc.worldBoxes.add(box(5.5, 64.5, 8.0, 6.0, 65.0, 9.0));
        mc.lookedAtBlock = new int[] {5, 64, 8};
        mc.lookedAtFace = Face.POS_Y;
        mc.lookedAtHitVec = new Vec3dCore(5.25, 64.5, 8.5);

        controller.onKey(false, false);

        Constraint x = range(Constraint.Field.X);
        assertEquals(5 - HALF, x.getLo(), EPS);
        assertEquals("riser above the step surface pulls the high X in", 5.5 - HALF, x.getHi(), EPS);
        Constraint z = range(Constraint.Field.Z);
        assertEquals(8 - HALF, z.getLo(), EPS);
        assertEquals(9 + HALF, z.getHi(), EPS);
    }

    @Test
    public void footprintOnAShelfTopSpansTheMergedCoplanarBoards() {
        mc.worldBoxes.add(box(5.0, 64.75, 8.6875, 6.0, 65.0, 8.8125));
        mc.worldBoxes.add(box(5.0, 64.0, 8.8125, 6.0, 65.0, 9.0));
        mc.worldBoxes.add(box(5.0, 64.0, 8.6875, 6.0, 64.25, 8.8125));
        mc.lookedAtBlock = new int[] {5, 64, 8};
        mc.lookedAtFace = Face.POS_Y;
        mc.lookedAtHitVec = new Vec3dCore(5.5, 65.0, 8.75);

        controller.onKey(false, false);

        Constraint z = range(Constraint.Field.Z);
        assertEquals("front lip edge", 8.6875 - HALF, z.getLo(), EPS);
        assertEquals("flush back panel extends the surface", 9 + HALF, z.getHi(), EPS);
        Constraint x = range(Constraint.Field.X);
        assertEquals(5 - HALF, x.getLo(), EPS);
        assertEquals(6 + HALF, x.getHi(), EPS);
    }

    @Test
    public void wallOnAnInsetCollisionBlockUsesTheCollisionFace() {
        mc.worldBoxes.add(box(5.0625, 64.0, 5.0625, 5.9375, 64.9375, 5.9375));
        mc.lookedAtBlock = new int[] {5, 64, 5};
        mc.lookedAtFace = Face.POS_X;
        mc.lookedAtHitVec = new Vec3dCore(6.0, 64.97, 5.5);

        controller.onKey(false, false);

        Constraint wall = null;
        for (Constraint c : constraints()) {
            if (!c.isRange() && c.getField() == Constraint.Field.X) wall = c;
        }
        assertNotNull(wall);
        assertEquals(Constraint.Op.GE, wall.getOp());
        assertEquals("wall sits on the inset collision face, not the outline hit", 5.9375 + HALF, wall.getValue(), EPS);
    }

    @Test
    public void staleLandingTickPastTheLastRowClampsToTheLastRow() {
        state.setLandingTick(9);
        mc.worldBoxes.add(box(5.0, 64.0, 5.0, 6.0, 65.0, 6.0));
        mc.lookedAtBlock = new int[] {5, 64, 5};
        mc.lookedAtFace = Face.POS_X;
        mc.lookedAtHitVec = new Vec3dCore(6.0, 64.5, 5.5);

        controller.onKey(false, false);

        assertNull("no ghost entry past the route", state.tickConstraintsOrNull(9));
        assertNotNull("wall clamped onto the last row", state.tickConstraintsOrNull(TICK));
        assertEquals(1, state.tickConstraintsOrNull(TICK).getConstraints().size());
    }

    @Test
    public void footprintOnASlabUsesTheSlabNotTheWallProtrudingFromBelow() {
        mc.addBlock(5, 63, 8, box(5.25, 63.0, 8.25, 5.75, 64.5, 8.75));
        mc.addBlock(5, 64, 8, box(5.0, 64.0, 8.0, 6.0, 64.5, 9.0));
        mc.lookedAtBlock = new int[] {5, 64, 8};
        mc.lookedAtFace = Face.POS_Y;
        mc.lookedAtHitVec = new Vec3dCore(5.5, 64.5, 8.5);

        controller.onKey(false, false);

        Constraint x = range(Constraint.Field.X);
        assertEquals("slab footprint, not the wall's narrow cross-section", 5 - HALF, x.getLo(), EPS);
        assertEquals(6 + HALF, x.getHi(), EPS);
        Constraint z = range(Constraint.Field.Z);
        assertEquals(8 - HALF, z.getLo(), EPS);
        assertEquals(9 + HALF, z.getHi(), EPS);
    }
}
