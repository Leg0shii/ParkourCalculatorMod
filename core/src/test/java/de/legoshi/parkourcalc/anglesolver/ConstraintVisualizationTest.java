package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * gh-145: in-world constraint visualization geometry. Pins the constraint -> AABB mapping
 * (anchored at the tick's simulated position), the open-ended clamping extent / side, and the
 * BoxController emission + AngleSolverConstraintSource bridge.
 */
public class ConstraintVisualizationTest {

    private static final double EPS = 1.0e-9;

    /** Captures every AABB submitted, so tests can assert geometry without a real renderer. */
    private static final class CapturingRenderer implements BoxRenderer {
        final Mode mode;
        final List<AABB> boxes = new ArrayList<>();
        int lines;
        int triangles;

        CapturingRenderer(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void drawBox(AABB box, int argb) {
            boxes.add(box);
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            lines++;
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            triangles++;
        }
    }

    private static TickState tickAt(Vec3dCore p) {
        return new TickState(p, false, false, false, 0f, Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    // ---- ConstraintShapes: range bands ----------------------------------------

    @Test
    public void rangeXMapsToBandAtTickPosition() {
        Vec3dCore foot = new Vec3dCore(10.0, 64.0, 20.0);
        Constraint c = Constraint.range(Constraint.Field.X, 12.0, 15.0, true, true);
        AABB box = ConstraintShapes.boxFor(c, foot);

        // Constrained axis spans the range exactly.
        assertEquals(12.0, box.min.x, EPS);
        assertEquals(15.0, box.max.x, EPS);
        // Cross axis (Z) is a fixed plate centred on the tick.
        assertEquals(20.0 - ConstraintShapes.CROSS_HALF_WIDTH, box.min.z, EPS);
        assertEquals(20.0 + ConstraintShapes.CROSS_HALF_WIDTH, box.max.z, EPS);
        // Y is a thin slab on the tick foot.
        assertEquals(64.0, box.min.y, EPS);
        assertEquals(64.0 + ConstraintShapes.SLAB_HEIGHT, box.max.y, EPS);
    }

    @Test
    public void rangeZMapsToBandOnZAxis() {
        Vec3dCore foot = new Vec3dCore(10.0, 64.0, 20.0);
        Constraint c = Constraint.range(Constraint.Field.Z, -3.0, 2.0, true, true);
        AABB box = ConstraintShapes.boxFor(c, foot);

        assertEquals(-3.0, box.min.z, EPS);
        assertEquals(2.0, box.max.z, EPS);
        assertEquals(10.0 - ConstraintShapes.CROSS_HALF_WIDTH, box.min.x, EPS);
        assertEquals(10.0 + ConstraintShapes.CROSS_HALF_WIDTH, box.max.x, EPS);
    }

    @Test
    public void reversedRangeBoundsAreNormalised() {
        Constraint c = Constraint.range(Constraint.Field.X, 15.0, 12.0, true, true);
        AABB box = ConstraintShapes.boxFor(c, new Vec3dCore(0, 0, 0));
        assertEquals(12.0, box.min.x, EPS);
        assertEquals(15.0, box.max.x, EPS);
    }

    // ---- ConstraintShapes: open-ended clamping --------------------------------

    @Test
    public void greaterThanClampsToPositiveSide() {
        Vec3dCore foot = new Vec3dCore(0.0, 64.0, 0.0);
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0);
        AABB box = ConstraintShapes.boxFor(c, foot);
        // Band starts at the bound and extends +X by OPEN_EXTENT (open toward +X).
        assertEquals(5.0, box.min.x, EPS);
        assertEquals(5.0 + ConstraintShapes.OPEN_EXTENT, box.max.x, EPS);
    }

    @Test
    public void lessThanClampsToNegativeSide() {
        Vec3dCore foot = new Vec3dCore(0.0, 64.0, 0.0);
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 5.0);
        AABB box = ConstraintShapes.boxFor(c, foot);
        // Band ends at the bound and extends -X by OPEN_EXTENT (open toward -X).
        assertEquals(5.0 - ConstraintShapes.OPEN_EXTENT, box.min.x, EPS);
        assertEquals(5.0, box.max.x, EPS);
    }

    @Test
    public void greaterEqualOnZClampsToPositiveZ() {
        Constraint c = Constraint.scalar(Constraint.Field.Z, Constraint.Op.GE, -2.0);
        AABB box = ConstraintShapes.boxFor(c, new Vec3dCore(0, 0, 0));
        assertEquals(-2.0, box.min.z, EPS);
        assertEquals(-2.0 + ConstraintShapes.OPEN_EXTENT, box.max.z, EPS);
    }

    @Test
    public void equalityRendersThinSlabCentredOnValue() {
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.EQ, 7.0);
        AABB box = ConstraintShapes.boxFor(c, new Vec3dCore(0, 0, 0));
        assertEquals(7.0 - ConstraintShapes.EQ_HALF_WIDTH, box.min.x, EPS);
        assertEquals(7.0 + ConstraintShapes.EQ_HALF_WIDTH, box.max.x, EPS);
        assertTrue("equality slab is thin", box.max.x - box.min.x < 0.1);
    }

    // ---- ConstraintShapes: non-spatial fields are not drawn -------------------

    @Test
    public void facingAndVelocityFieldsAreNotDrawable() {
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.F, Constraint.Op.GT, 0.1)));
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GT, 0.1)));
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.DZ, Constraint.Op.LT, -0.1)));
        assertFalse(ConstraintShapes.isDrawable(Constraint.scalar(Constraint.Field.F, Constraint.Op.GT, 0.1)));
        assertNull("non-spatial -> no box", ConstraintShapes.boxFor(Constraint.scalar(Constraint.Field.DZ, Constraint.Op.LT, -0.1), new Vec3dCore(0, 0, 0)));
        assertEquals(AngleSolverState.Axis.X, ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 1.0)));
        assertEquals(AngleSolverState.Axis.Z, ConstraintShapes.spatialAxis(Constraint.range(Constraint.Field.Z, 0, 1, true, true)));
    }

    // ---- BoxController emission -----------------------------------------------

    @Test
    public void boxControllerEmitsOnePlatePerConstraintInBothPasses() {
        BoxController boxes = new BoxController();
        boxes.add(tickAt(new Vec3dCore(0.5, 64.0, 0.5)));    // tick 0
        boxes.add(tickAt(new Vec3dCore(1.5, 64.0, 0.5)));    // tick 1: two plates
        boxes.add(tickAt(new Vec3dCore(2.5, 64.0, 0.5)));    // tick 2

        final AABB a = new AABB(new Vec3dCore(1, 1, 1), new Vec3dCore(2, 2, 2));
        final AABB b = new AABB(new Vec3dCore(3, 3, 3), new Vec3dCore(4, 4, 4));
        ConstraintBoxSource source = new ConstraintBoxSource() {
            @Override
            public List<AABB> boxesAt(int tickIndex) {
                if (tickIndex == 1) {
                    List<AABB> l = new ArrayList<>();
                    l.add(a);
                    l.add(b);
                    return l;
                }
                return Collections.emptyList();
            }

            @Override
            public long revision() {
                return 1L;
            }
        };

        assertEquals(2, boxes.constraintBoxCount(source));

        CapturingRenderer faces = new CapturingRenderer(BoxRenderer.Mode.FACES);
        boxes.renderConstraints(faces, source, 0xFFFFFFFF, 0, 0, 0, Double.POSITIVE_INFINITY);
        assertEquals(2, faces.boxes.size());
        assertEquals(a, faces.boxes.get(0));
        assertEquals(b, faces.boxes.get(1));

        CapturingRenderer lines = new CapturingRenderer(BoxRenderer.Mode.LINES);
        boxes.renderConstraints(lines, source, 0xFF00FF00, 0, 0, 0, Double.POSITIVE_INFINITY);
        assertEquals(2, lines.boxes.size());
    }

    // ---- AngleSolverConstraintSource bridge -----------------------------------

    private static BoxController boxesWith(Vec3dCore... feet) {
        BoxController boxes = new BoxController();
        for (Vec3dCore f : feet) boxes.add(tickAt(f));
        return boxes;
    }

    @Test
    public void sourceAnchorsRangeConstraintAtItsTickPosition() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(
                new Vec3dCore(0.5, 64.0, 0.5),
                new Vec3dCore(1.25, 65.0, 3.5)); // tick 1 carries the constraint
        state.tickConstraints(1).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true));

        AngleSolverConstraintSource source = new AngleSolverConstraintSource(state, boxes, () -> true);

        assertTrue(source.boxesAt(0).isEmpty());
        List<AABB> at1 = source.boxesAt(1);
        assertEquals(1, at1.size());
        AABB box = at1.get(0);
        assertEquals(1.0, box.min.x, EPS);
        assertEquals(2.0, box.max.x, EPS);
        // Anchored at tick 1's position (z = 3.5, y = 65.0), not tick 0's.
        assertEquals(3.5 - ConstraintShapes.CROSS_HALF_WIDTH, box.min.z, EPS);
        assertEquals(65.0, box.min.y, EPS);
    }

    @Test
    public void sourceSkipsDisabledAndNonSpatialConstraints() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        Constraint disabled = Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true);
        disabled.setEnabled(false);
        state.tickConstraints(0).getConstraints().add(disabled);
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GT, 0.2)); // non-spatial
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.GT, 0.0)); // the only drawable one

        AngleSolverConstraintSource source = new AngleSolverConstraintSource(state, boxes, () -> true);
        assertEquals(1, source.boxesAt(0).size());
    }

    @Test
    public void sourceIsSilentWhenInactive() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        state.tickConstraints(0).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true));

        final boolean[] active = {false};
        AngleSolverConstraintSource source = new AngleSolverConstraintSource(state, boxes, () -> active[0]);
        assertTrue(source.boxesAt(0).isEmpty());
        assertEquals(0L, source.revision());

        active[0] = true;
        assertEquals(1, source.boxesAt(0).size());
        assertNotEquals(0L, source.revision());
    }

    @Test
    public void revisionChangesWhenConstraintChanges() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0);
        state.tickConstraints(0).getConstraints().add(c);

        AngleSolverConstraintSource source = new AngleSolverConstraintSource(state, boxes, () -> true);
        long before = source.revision();
        c.setValue(6.0);
        assertNotEquals("editing a constraint value must bump the revision", before, source.revision());
    }
}
