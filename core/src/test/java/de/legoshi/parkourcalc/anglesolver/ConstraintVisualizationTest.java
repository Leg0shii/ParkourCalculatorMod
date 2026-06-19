package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintPalette;
import de.legoshi.parkourcalc.core.render.ConstraintPlate;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.render.ConstraintStyle;
import de.legoshi.parkourcalc.core.render.PathRenderPlan;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.ConstraintSelection;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.WorldPick;
import de.legoshi.parkourcalc.core.ui.anglesolver.AngleSolverConstraintSource;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConstraintVisualizationTest {

    private static final double EPS = 1.0e-9;
    private static final double H = ConstraintShapes.H;

    private static final double FRONT_W = 0.6;
    private static final double FRONT_H = 0.15;
    private static final double FRONT_L = 0.1;
    private static final double BACK_W = 0.6;
    private static final double BACK_H = 0.0;
    private static final double BACK_L = 4.0;
    private static final double CORE = FRONT_W * 0.5;

    private static final ConstraintStyle STYLE = new ConstraintStyle(true, FRONT_W, FRONT_H, FRONT_L, BACK_W, BACK_H, BACK_L);
    private static final ConstraintStyle NO_EXPAND = new ConstraintStyle(false, FRONT_W, FRONT_H, FRONT_L, BACK_W, BACK_H, BACK_L);

    private static final int[] I0 = {0};
    private static final ConstraintPalette PALETTE = new ConstraintPalette(0x1, 0x2, 0x3, 0x4, 0x5);

    private static final class VertCounter implements BoxRenderer {
        final Mode mode;
        long count;

        VertCounter(Mode mode) {
            this.mode = mode;
        }

        @Override
        public void drawBox(AABB box, int argb) {
            count += (mode == Mode.LINES) ? 24 : 36;
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
            if (mode == Mode.LINES) count += 2;
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            if (mode == Mode.FACES) count += 3;
        }
    }

    private static TickState tickAt(Vec3dCore p) {
        return new TickState(p, false, false, false, 0f, Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN);
    }

    private static BoxController boxesWith(Vec3dCore... feet) {
        BoxController boxes = new BoxController();
        for (Vec3dCore f : feet) boxes.add(tickAt(f));
        return boxes;
    }

    private static AngleSolverConstraintSource source(AngleSolverState state, BoxController boxes) {
        return new AngleSolverConstraintSource(state, boxes, () -> true, new Settings(), new SelectionManager(null), new ConstraintSelection());
    }

    @Test
    public void senseClassifiesInclusionVsExclusion() {
        assertEquals(ConstraintShapes.Sense.INCLUDE, ConstraintShapes.sense(Constraint.Op.IN));
        assertEquals(ConstraintShapes.Sense.INCLUDE, ConstraintShapes.sense(Constraint.Op.EQ));
        assertEquals(ConstraintShapes.Sense.EXCLUDE, ConstraintShapes.sense(Constraint.Op.GT));
        assertEquals(ConstraintShapes.Sense.EXCLUDE, ConstraintShapes.sense(Constraint.Op.GE));
        assertEquals(ConstraintShapes.Sense.EXCLUDE, ConstraintShapes.sense(Constraint.Op.LT));
        assertEquals(ConstraintShapes.Sense.EXCLUDE, ConstraintShapes.sense(Constraint.Op.LE));
    }

    @Test
    public void onlySpatialFieldsAreDrawable() {
        assertEquals(AngleSolverState.Axis.X, ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 1.0)));
        assertEquals(AngleSolverState.Axis.Z, ConstraintShapes.spatialAxis(Constraint.range(Constraint.Field.Z, 0, 1, true, true)));
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.F, Constraint.Op.GT, 0.1)));
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GT, 0.1)));
        assertNull(ConstraintShapes.spatialAxis(Constraint.scalar(Constraint.Field.DZ, Constraint.Op.LT, -0.1)));
        assertFalse(ConstraintShapes.isDrawable(Constraint.scalar(Constraint.Field.F, Constraint.Op.GT, 0.1)));
        assertTrue(ConstraintShapes.isDrawable(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 0.1)));
    }

    @Test
    public void satisfiedHandlesRangesAndComparisons() {
        Vec3dCore foot = new Vec3dCore(5.0, 64.0, -2.0);
        assertTrue(ConstraintShapes.satisfied(Constraint.range(Constraint.Field.X, 4.0, 6.0, true, true), foot));
        assertFalse(ConstraintShapes.satisfied(Constraint.range(Constraint.Field.X, 6.0, 7.0, true, true), foot));
        assertTrue(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 4.0), foot));
        assertFalse(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0), foot));
        assertTrue(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.X, Constraint.Op.GE, 5.0), foot));
        assertTrue(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.Z, Constraint.Op.LT, 0.0), foot));
        assertTrue(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.X, Constraint.Op.EQ, 5.0), foot));
        assertFalse(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.X, Constraint.Op.EQ, 5.5), foot));
        assertTrue(ConstraintShapes.satisfied(Constraint.scalar(Constraint.Field.F, Constraint.Op.GT, 9.0), foot));
    }

    @Test
    public void satisfiedHonorsExclusiveBounds() {
        Vec3dCore onUpperBound = new Vec3dCore(2.0, 64.0, 0.0);
        assertTrue(ConstraintShapes.satisfied(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true), onUpperBound));
        assertFalse(ConstraintShapes.satisfied(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, false), onUpperBound));
    }

    @Test
    public void padIsOneSolidFrontPlateDilatedByTheHitbox() {
        Vec3dCore foot = new Vec3dCore(5.5, 64.0, 8.5);
        Constraint xr = Constraint.range(Constraint.Field.X, 5.0 - H, 6.0 + H, true, true);
        Constraint zr = Constraint.range(Constraint.Field.Z, 8.0 - H, 9.0 + H, true, true);
        ConstraintPlate plate = ConstraintShapes.pad(xr, zr, foot, STYLE, 0, new int[]{0, 1});

        assertEquals(ConstraintShapes.Sense.INCLUDE, plate.sense);
        assertTrue(plate.satisfied);
        assertEquals(1, plate.front.size());
        assertTrue(plate.back.isEmpty());

        AABB body = plate.front.get(0);
        assertEquals((5.0 - H) - H, body.min.x, EPS);
        assertEquals((6.0 + H) + H, body.max.x, EPS);
        assertEquals((8.0 - H) - H, body.min.z, EPS);
        assertEquals((9.0 + H) + H, body.max.z, EPS);
        assertEquals(64.0, body.min.y, EPS);
        assertEquals(64.0 + FRONT_H, body.max.y, EPS);
    }

    @Test
    public void padWithoutExpandUsesRawFootCenterBounds() {
        Vec3dCore foot = new Vec3dCore(5.5, 64.0, 8.5);
        Constraint xr = Constraint.range(Constraint.Field.X, 5.0 - H, 6.0 + H, true, true);
        Constraint zr = Constraint.range(Constraint.Field.Z, 8.0 - H, 9.0 + H, true, true);
        AABB body = ConstraintShapes.pad(xr, zr, foot, NO_EXPAND, 0, new int[]{0, 1}).front.get(0);
        assertEquals(5.0 - H, body.min.x, EPS);
        assertEquals(6.0 + H, body.max.x, EPS);
    }

    @Test
    public void plateCarriesTickAndConstraintIndices() {
        Vec3dCore foot = new Vec3dCore(5.5, 64.0, 8.5);
        Constraint xr = Constraint.range(Constraint.Field.X, 5.0 - H, 6.0 + H, true, true);
        Constraint zr = Constraint.range(Constraint.Field.Z, 8.0 - H, 9.0 + H, true, true);
        ConstraintPlate plate = ConstraintShapes.pad(xr, zr, foot, STYLE, 7, new int[]{2, 3});
        assertEquals(7, plate.tick);
        assertArrayEquals(new int[]{2, 3}, plate.constraintIndices);
        assertFalse(plate.highlighted);
    }

    @Test
    public void validFootHasItsHitboxCoveredByThePad() {
        Constraint xr = Constraint.range(Constraint.Field.X, 5.0 - H, 6.0 + H, true, true);
        Constraint zr = Constraint.range(Constraint.Field.Z, 8.0 - H, 9.0 + H, true, true);
        Vec3dCore foot = new Vec3dCore(5.0 - H + 0.05, 64.0, 8.5);
        ConstraintPlate plate = ConstraintShapes.pad(xr, zr, foot, STYLE, 0, new int[]{0, 1});
        AABB body = plate.front.get(0);
        assertTrue(plate.satisfied);
        assertTrue("hitbox left edge inside plate", foot.x - H >= body.min.x - EPS);
        assertTrue("hitbox right edge inside plate", foot.x + H <= body.max.x + EPS);
    }

    @Test
    public void stripFrontBandWithFlatBackTrail() {
        Vec3dCore foot = new Vec3dCore(2.0, 64.0, 20.0);
        ConstraintPlate plate = ConstraintShapes.strip(Constraint.range(Constraint.Field.X, 1.0, 5.0, true, true), foot, STYLE, 0, I0);
        assertEquals(1, plate.front.size());
        assertEquals(1, plate.back.size());

        AABB front = plate.front.get(0);
        assertEquals(1.0 - H, front.min.x, EPS);
        assertEquals(5.0 + H, front.max.x, EPS);
        assertEquals(20.0 - CORE, front.min.z, EPS);
        assertEquals(20.0 + CORE, front.max.z, EPS);
        assertEquals(64.0 + FRONT_H, front.max.y, EPS);

        AABB back = plate.back.get(0);
        assertEquals(20.0 - BACK_L, back.min.z, EPS);
        assertEquals(20.0 + BACK_L, back.max.z, EPS);
        assertEquals(back.min.y, back.max.y, EPS);
    }

    @Test
    public void customFrontWidthSetsTheCrossExtent() {
        ConstraintStyle wide = new ConstraintStyle(true, 2.0, FRONT_H, FRONT_L, BACK_W, BACK_H, BACK_L);
        Vec3dCore foot = new Vec3dCore(2.0, 64.0, 20.0);
        AABB front = ConstraintShapes.strip(Constraint.range(Constraint.Field.X, 1.0, 5.0, true, true), foot, wide, 0, I0).front.get(0);
        assertEquals(20.0 - 1.0, front.min.z, EPS);
        assertEquals(20.0 + 1.0, front.max.z, EPS);
    }

    @Test
    public void eqIsAHitboxWideFrontBand() {
        Vec3dCore foot = new Vec3dCore(7.0, 64.0, 3.0);
        ConstraintPlate plate = ConstraintShapes.plane(Constraint.scalar(Constraint.Field.X, Constraint.Op.EQ, 7.0), foot, STYLE, 0, I0);
        assertEquals(ConstraintShapes.Sense.INCLUDE, plate.sense);
        assertEquals(1, plate.front.size());
        assertEquals(1, plate.back.size());
        AABB front = plate.front.get(0);
        assertEquals(7.0 - H, front.min.x, EPS);
        assertEquals(7.0 + H, front.max.x, EPS);
    }

    @Test
    public void eqWithoutExpandUsesFrontLengthThickness() {
        Vec3dCore foot = new Vec3dCore(7.0, 64.0, 3.0);
        AABB front = ConstraintShapes.plane(Constraint.scalar(Constraint.Field.X, Constraint.Op.EQ, 7.0), foot, NO_EXPAND, 0, I0).front.get(0);
        assertEquals(7.0 - FRONT_L * 0.5, front.min.x, EPS);
        assertEquals(7.0 + FRONT_L * 0.5, front.max.x, EPS);
    }

    @Test
    public void greaterThanExcludeFrontAnchoredAtBoundaryBackBehindIt() {
        Vec3dCore foot = new Vec3dCore(10.0, 64.0, 0.0);
        ConstraintPlate plate = ConstraintShapes.exclude(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0), foot, STYLE, 0, I0);

        assertEquals(ConstraintShapes.Sense.EXCLUDE, plate.sense);
        assertTrue(plate.satisfied);
        assertEquals(1, plate.front.size());
        assertEquals(1, plate.back.size());

        AABB front = plate.front.get(0);
        assertEquals("front near edge anchored at the hitbox-shifted bound", 5.0 - H, front.min.x, EPS);
        assertEquals((5.0 - H) + FRONT_L, front.max.x, EPS);

        AABB back = plate.back.get(0);
        assertEquals((5.0 - H) + FRONT_L, back.min.x, EPS);
        assertEquals((5.0 - H) + FRONT_L + BACK_L, back.max.x, EPS);
    }

    @Test
    public void lessThanExcludeExtendsTowardNegative() {
        Vec3dCore foot = new Vec3dCore(0.0, 64.0, 0.0);
        ConstraintPlate plate = ConstraintShapes.exclude(Constraint.scalar(Constraint.Field.X, Constraint.Op.LE, 5.0), foot, STYLE, 0, I0);
        AABB front = plate.front.get(0);
        assertEquals("front near edge anchored at the hitbox-shifted bound", 5.0 + H, front.max.x, EPS);
        assertEquals((5.0 + H) - FRONT_L, front.min.x, EPS);
        AABB back = plate.back.get(0);
        assertEquals((5.0 + H) - FRONT_L - BACK_L, back.min.x, EPS);
        assertTrue(plate.satisfied);
    }

    @Test
    public void excludeStatusFlipsWithFoot() {
        Vec3dCore violating = new Vec3dCore(2.0, 64.0, 0.0);
        ConstraintPlate plate = ConstraintShapes.exclude(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0), violating, STYLE, 0, I0);
        assertFalse(plate.satisfied);
    }

    // ---- selection + highlight rule -------------------------------------------

    @Test
    public void highlightRuleFocusVsTickSelection() {
        ConstraintSelection cs = new ConstraintSelection();
        SelectionManager sel = new SelectionManager(null);

        assertFalse(cs.highlights(1, 0, sel));

        sel.selectOnly(2); // path 2 == tick 1
        assertTrue("no focus + tick selected highlights every constraint on the tick", cs.highlights(1, 0, sel));
        assertTrue(cs.highlights(1, 5, sel));
        assertFalse(cs.highlights(2, 0, sel));

        cs.focusOne(1, 0);
        assertTrue("focused constraint highlights", cs.highlights(1, 0, sel));
        assertFalse("a sibling does not highlight when one is focused", cs.highlights(1, 5, sel));
    }

    @Test
    public void focusRemapFollowsAMovedTick() {
        ConstraintSelection cs = new ConstraintSelection();
        cs.focusOne(3, 1);
        cs.remapTick(t -> t == 3 ? 7 : t);
        SelectionManager sel = new SelectionManager(null);
        sel.selectOnly(8); // tick 7
        assertTrue(cs.highlights(7, 1, sel));
        assertFalse(cs.highlights(3, 1, sel));
    }

    @Test
    public void staleFocusOnDeselectedTickFallsBackToTickHighlight() {
        ConstraintSelection cs = new ConstraintSelection();
        SelectionManager sel = new SelectionManager(null);
        cs.focusOne(1, 0);
        sel.selectOnly(2); // tick 1 focused + selected
        assertTrue(cs.highlights(1, 0, sel));
        assertFalse(cs.highlights(1, 5, sel));

        sel.selectOnly(3); // select tick 2; tick 1's focus is now stale
        assertFalse("stale focus on a deselected tick stops highlighting", cs.highlights(1, 0, sel));
        assertTrue("the newly selected tick highlights all its constraints", cs.highlights(2, 0, sel));
        assertTrue(cs.highlights(2, 9, sel));
    }

    @Test
    public void sourceHighlightsConstraintsOfSelectedTick() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));

        SelectionManager sel = new SelectionManager(null);
        ConstraintSelection cs = new ConstraintSelection();
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> true, new Settings(), sel, cs);

        assertFalse(src.platesAt(1).get(0).highlighted);
        sel.selectOnly(2); // tick 1
        assertTrue(src.platesAt(1).get(0).highlighted);
    }

    @Test
    public void sourceHighlightsOnlyFocusedConstraint() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0)); // index 0
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.GT, 0.0)); // index 1

        SelectionManager sel = new SelectionManager(null);
        ConstraintSelection cs = new ConstraintSelection();
        cs.focusOne(1, 0);
        sel.selectOnly(2); // tick 1 selected, so the focus is effective
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> true, new Settings(), sel, cs);

        List<ConstraintPlate> plates = src.platesAt(1);
        assertEquals(2, plates.size());
        for (ConstraintPlate p : plates) {
            boolean isFirst = p.constraintIndices[0] == 0;
            assertEquals(isFirst, p.highlighted);
        }
    }

    // ---- source classification + identity -------------------------------------

    @Test
    public void sourceMergesCoTickXandZRangesIntoOnePadWithBothIndices() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(5.5, 64.0, 8.5));
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GT, 0.1)); // index 0, non-spatial
        state.addLandingConstraintsForBlock(5, 64, 8, 0, false, false, false, false); // X at index 1, Z at index 2

        List<ConstraintPlate> plates = source(state, boxes).platesAt(0);
        assertEquals(1, plates.size());
        ConstraintPlate pad = plates.get(0);
        assertEquals(0, pad.tick);
        assertArrayEquals("pad carries the X and Z list indices, skipping the non-spatial one", new int[]{1, 2}, pad.constraintIndices);
        AABB body = pad.front.get(0);
        assertEquals(5.0 - 2 * H, body.min.x, EPS);
        assertEquals(6.0 + 2 * H, body.max.x, EPS);
    }

    @Test
    public void sourceKeepsLoneRangeAsStripAndExcludeSeparate() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        state.tickConstraints(0).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 5.0, true, true));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.GT, 0.0));

        AngleSolverConstraintSource src = source(state, boxes);
        List<ConstraintPlate> lone = src.platesAt(0);
        assertEquals(1, lone.size());
        assertEquals(ConstraintShapes.Sense.INCLUDE, lone.get(0).sense);
        assertArrayEquals(new int[]{0}, lone.get(0).constraintIndices);

        List<ConstraintPlate> excl = src.platesAt(1);
        assertEquals(1, excl.size());
        assertEquals(ConstraintShapes.Sense.EXCLUDE, excl.get(0).sense);
        assertEquals(1, excl.get(0).back.size());
    }

    @Test
    public void sourceSkipsDisabledAndNonSpatialConstraints() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        Constraint disabled = Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true);
        disabled.setEnabled(false);
        state.tickConstraints(0).getConstraints().add(disabled);
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.DX, Constraint.Op.GT, 0.2));
        state.tickConstraints(0).getConstraints().add(Constraint.scalar(Constraint.Field.Z, Constraint.Op.GT, 0.0));

        List<ConstraintPlate> plates = source(state, boxes).platesAt(0);
        assertEquals(1, plates.size());
        assertEquals(ConstraintShapes.Sense.EXCLUDE, plates.get(0).sense);
        assertArrayEquals("uses the original list index of the only drawable constraint", new int[]{2}, plates.get(0).constraintIndices);
    }

    @Test
    public void sourceAnchorsAtItsTickPosition() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.25, 65.0, 3.5));
        state.tickConstraints(1).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true));

        AABB front = source(state, boxes).platesAt(1).get(0).front.get(0);
        double core = new Settings().constraintFrontWidth * 0.5;
        assertEquals(3.5 - core, front.min.z, EPS);
        assertEquals(65.0, front.min.y, EPS);
    }

    @Test
    public void sourceIsSilentWhenInactive() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        state.tickConstraints(0).getConstraints().add(Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true));

        final boolean[] active = {false};
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> active[0], new Settings(), new SelectionManager(null), new ConstraintSelection());
        assertTrue(src.platesAt(0).isEmpty());
        assertEquals(0L, src.revision());

        active[0] = true;
        assertEquals(1, src.platesAt(0).size());
        assertNotEquals(0L, src.revision());
    }

    @Test
    public void revisionChangesWhenConstraintChanges() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        Constraint c = Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0);
        state.tickConstraints(0).getConstraints().add(c);

        AngleSolverConstraintSource src = source(state, boxes);
        long before = src.revision();
        c.setValue(6.0);
        assertNotEquals(before, src.revision());
    }

    @Test
    public void revisionChangesWhenRangeInclusivityToggles() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5));
        Constraint c = Constraint.range(Constraint.Field.X, 1.0, 2.0, true, true);
        state.tickConstraints(0).getConstraints().add(c);

        AngleSolverConstraintSource src = source(state, boxes);
        long before = src.revision();
        c.setInclusive(false, true);
        assertNotEquals(before, src.revision());
    }

    @Test
    public void revisionTracksSelectionAndFocusOfConstrainedTicks() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));

        SelectionManager sel = new SelectionManager(null);
        ConstraintSelection cs = new ConstraintSelection();
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> true, new Settings(), sel, cs);

        long before = src.revision();
        sel.selectOnly(2); // selecting the constrained tick recolours -> revision must change
        assertNotEquals(before, src.revision());

        long afterSelect = src.revision();
        cs.focusOne(1, 0);
        assertNotEquals(afterSelect, src.revision());
    }

    @Test
    public void revisionIgnoresSelectionOfUnconstrainedTicks() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5), new Vec3dCore(2.5, 64.0, 0.5));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));

        SelectionManager sel = new SelectionManager(null);
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> true, new Settings(), sel, new ConstraintSelection());

        long before = src.revision();
        sel.selectOnly(3); // tick 2 has no constraints; box-selection patch should still apply
        assertEquals(before, src.revision());
    }

    // ---- pick (reuses the box ray-AABB machinery) -----------------------------

    @Test
    public void pickWorldPrefersBoxThenFallsBackToConstraint() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5), new Vec3dCore(2.5, 64.0, 0.5));
        state.addLandingConstraintsForBlock(5, 64, 8, 1, false, false, false, false);
        AngleSolverConstraintSource src = source(state, boxes);

        Vec3dCore down = new Vec3dCore(0.0, -1.0, 0.0);

        WorldPick boxPick = boxes.pickWorld(new Vec3dCore(1.5, 100.0, 0.5), down, src);
        assertEquals(WorldPick.Kind.BOX, boxPick.kind);
        assertEquals(1, boxPick.index);

        WorldPick conPick = boxes.pickWorld(new Vec3dCore(5.0, 100.0, 8.5), down, src);
        assertEquals(WorldPick.Kind.CONSTRAINT, conPick.kind);
        assertEquals(1, conPick.index);
        assertEquals(2, conPick.constraintIndices.length);

        assertNull(boxes.pickWorld(new Vec3dCore(40.0, 100.0, 40.0), down, src));

        assertTrue(boxes.isCursorOverConstraint(new Vec3dCore(5.0, 100.0, 8.5), down, src));
        assertFalse(boxes.isCursorOverConstraint(new Vec3dCore(40.0, 100.0, 40.0), down, src));
    }

    @Test
    public void backTrailIsAlsoClickable() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(0.5, 64.0, 0.5), new Vec3dCore(1.5, 64.0, 0.5));
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));
        AngleSolverConstraintSource src = source(state, boxes);
        Vec3dCore down = new Vec3dCore(0.0, -1.0, 0.0);

        // x=6 lands on the back trail (the front box is only ~[4.7, 4.8]); z=0.5 is the tick foot.
        WorldPick pick = boxes.pickWorld(new Vec3dCore(6.0, 100.0, 0.5), down, src);
        assertEquals(WorldPick.Kind.CONSTRAINT, pick.kind);
        assertEquals(1, pick.index);
        assertTrue(boxes.isCursorOverConstraint(new Vec3dCore(6.0, 100.0, 0.5), down, src));
    }

    // ---- emitted-vertex count invariant ---------------------------------------

    @Test
    public void countMethodsEqualIndependentlyCountedEmission() {
        AngleSolverState state = new AngleSolverState();
        BoxController boxes = boxesWith(new Vec3dCore(5.5, 64.0, 8.5), new Vec3dCore(10.0, 64.0, 0.0));
        state.addLandingConstraintsForBlock(5, 64, 8, 0, false, false, false, false);
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));

        AngleSolverConstraintSource src = source(state, boxes);

        VertCounter faces = new VertCounter(BoxRenderer.Mode.FACES);
        boxes.renderConstraints(faces, src, PALETTE, false, 0, 0, 0, Double.POSITIVE_INFINITY);
        VertCounter lines = new VertCounter(BoxRenderer.Mode.LINES);
        boxes.renderConstraints(lines, src, PALETTE, true, 0, 0, 0, Double.POSITIVE_INFINITY);

        // pad: 1 front box. exclude: 1 front + 1 back box. Front and back are both filled and outlined.
        int expectedFaces = 36 + 36 * 2;
        int expectedLines = 24 + 24 * 2;
        assertEquals(expectedFaces, faces.count);
        assertEquals(expectedLines, lines.count);

        assertEquals(faces.count, boxes.constraintFaceVertexCount(src, PALETTE));
        assertEquals(lines.count, boxes.constraintLineVertexCount(src, PALETTE));
    }

    @Test
    public void planConstraintCountsEqualWhatTheFullEmitterBakes() {
        BoxController boxes = boxesWith(new Vec3dCore(5.5, 64.0, 8.5), new Vec3dCore(10.0, 64.0, 0.0));
        AngleSolverState state = new AngleSolverState();
        state.addLandingConstraintsForBlock(5, 64, 8, 0, false, false, false, false);
        state.tickConstraints(1).getConstraints().add(Constraint.scalar(Constraint.Field.X, Constraint.Op.GT, 5.0));
        Settings settings = new Settings();
        AngleSolverConstraintSource src = new AngleSolverConstraintSource(state, boxes, () -> true, settings, new SelectionManager(null), new ConstraintSelection());

        SelectionManager selection = new SelectionManager(null);
        try {
            PathRenderPlan.setConstraintSource(src);

            settings.showConstraints = true;
            PathRenderPlan withC = PathRenderPlan.build(boxes, settings, selection);
            VertCounter facesWith = new VertCounter(BoxRenderer.Mode.FACES);
            withC.faceEmitter.accept(facesWith);
            VertCounter linesWith = new VertCounter(BoxRenderer.Mode.LINES);
            withC.lineEmitter.accept(linesWith);

            settings.showConstraints = false;
            PathRenderPlan without = PathRenderPlan.build(boxes, settings, selection);
            VertCounter facesWithout = new VertCounter(BoxRenderer.Mode.FACES);
            without.faceEmitter.accept(facesWithout);
            VertCounter linesWithout = new VertCounter(BoxRenderer.Mode.LINES);
            without.lineEmitter.accept(linesWithout);

            assertTrue(withC.constraintFaceVerts > 0);
            assertEquals(withC.constraintFaceVerts, facesWith.count - facesWithout.count);
            assertEquals(withC.constraintLineVerts, linesWith.count - linesWithout.count);
        } finally {
            PathRenderPlan.setConstraintSource(ConstraintBoxSource.NONE);
        }
    }
}
