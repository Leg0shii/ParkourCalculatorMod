package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConstraintDeriverTest {

    private static final double HALF = ConstraintDeriver.HALF;
    private static final double EPS = 1.0e-9;

    private static AABB cube(int x, int y, int z) {
        return new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0));
    }

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(new Vec3dCore(x0, y0, z0), new Vec3dCore(x1, y1, z1));
    }

    @Test
    public void halfIsTheFloatPromotedPlayerHalfWidth() {
        assertEquals((double) (0.6f / 2.0f), HALF, 0.0);
        assertTrue("float-exact half must be wider than the naive 0.3 double", HALF > 0.3);
    }

    @Test
    public void wallPosXDontEnterIsLowerBoundBeyondTheFace() {
        List<AABB> boxes = Arrays.asList(cube(5, 64, 5));
        Constraint c = ConstraintDeriver.deriveWall(Face.POS_X, boxes, new Vec3dCore(6.0, 64.5, 5.5), false);
        assertEquals(Constraint.Field.X, c.getField());
        assertEquals(Constraint.Op.GE, c.getOp());
        assertEquals(6.0 + HALF, c.getValue(), EPS);
    }

    @Test
    public void wallPosXEnterFlipsToUpperBound() {
        List<AABB> boxes = Arrays.asList(cube(5, 64, 5));
        Constraint c = ConstraintDeriver.deriveWall(Face.POS_X, boxes, new Vec3dCore(6.0, 64.5, 5.5), true);
        assertEquals(Constraint.Op.LE, c.getOp());
        assertEquals(6.0 + HALF, c.getValue(), EPS);
    }

    @Test
    public void wallNegZDontEnterIsUpperBound() {
        List<AABB> boxes = Arrays.asList(cube(5, 64, 3));
        Constraint c = ConstraintDeriver.deriveWall(Face.NEG_Z, boxes, new Vec3dCore(5.5, 64.5, 3.0), false);
        assertEquals(Constraint.Field.Z, c.getField());
        assertEquals(Constraint.Op.LE, c.getOp());
        assertEquals(3.0 - HALF, c.getValue(), EPS);
    }

    @Test
    public void wallUsesCollisionFaceNotTheRayHitSurface() {
        AABB paneArm = box(5.0, 64.0, 5.4375, 5.5, 65.0, 5.5625);
        List<AABB> boxes = Arrays.asList(paneArm);
        Vec3dCore visualHit = new Vec3dCore(5.5625, 64.5, 5.5);
        Constraint c = ConstraintDeriver.deriveWall(Face.POS_X, boxes, visualHit, false);
        assertEquals(Constraint.Op.GE, c.getOp());
        assertEquals("wall snaps to the collision arm face at the cell centre, not the 0.5625 render surface",
                5.5 + HALF, c.getValue(), EPS);
    }

    @Test
    public void wallSnapsToTheCollisionFaceWhenTheHitLandsOffTheBoxes() {
        AABB honey = box(5.0625, 64.0, 5.0625, 5.9375, 64.9375, 5.9375);
        Vec3dCore outlineHit = new Vec3dCore(6.0, 64.97, 5.5);
        Constraint c = ConstraintDeriver.deriveWall(Face.POS_X, Arrays.asList(honey), outlineHit, false);
        assertEquals("hit above the inset collision still snaps to the collision face, not the outline",
                5.9375 + HALF, c.getValue(), EPS);
    }

    @Test
    public void wallPicksTheNearestBoxWhenNoneSpansTheHit() {
        AABB bottomPlate = box(5.0, 64.0, 5.0, 6.0, 64.2, 6.0);
        AABB insetTopPlate = box(5.0, 64.9, 5.0, 5.8, 65.0, 6.0);
        Vec3dCore hit = new Vec3dCore(6.0, 64.85, 5.5);
        Constraint c = ConstraintDeriver.deriveWall(Face.POS_X, Arrays.asList(bottomPlate, insetTopPlate), hit, false);
        assertEquals("the nearest box wins over a farther box with a more extreme face",
                5.8 + HALF, c.getValue(), EPS);
    }

    @Test
    public void wallKeepsTheOutermostFaceAmongBoxesSpanningTheHit() {
        AABB backPanel = box(5.8125, 64.0, 5.0, 6.0, 65.0, 6.0);
        AABB topLip = box(5.6875, 64.75, 5.0, 5.8125, 65.0, 6.0);
        Vec3dCore hit = new Vec3dCore(5.6875, 64.8, 5.5);
        Constraint c = ConstraintDeriver.deriveWall(Face.NEG_X, Arrays.asList(backPanel, topLip), hit, false);
        assertEquals("the lip in front of the panel sets the wall plane at the clicked height",
                5.6875 - HALF, c.getValue(), EPS);
    }

    @Test
    public void wallReturnsNullForTopFace() {
        assertNull(ConstraintDeriver.deriveWall(Face.POS_Y, Collections.<AABB>emptyList(), new Vec3dCore(5.5, 65.0, 5.5), false));
    }

    @Test
    public void footprintOnOpenBlockOverhangsBothWays() {
        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, Collections.<AABB>emptyList(), false);
        assertEquals(5 - HALF, r[0], EPS);
        assertEquals(6 + HALF, r[1], EPS);
        assertEquals(8 - HALF, r[2], EPS);
        assertEquals(9 + HALF, r[3], EPS);
    }

    @Test
    public void footprintInsetsWhenAFlushNeighborBlocksTheHighXSide() {
        List<AABB> obstacles = Arrays.asList(cube(6, 65, 8));
        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, obstacles, false);
        assertEquals("open low side keeps its overhang", 5 - HALF, r[0], EPS);
        assertEquals("flush neighbor pulls the high X in to edge minus half", 6 - HALF, r[1], EPS);
        assertEquals(8 - HALF, r[2], EPS);
        assertEquals(9 + HALF, r[3], EPS);
    }

    @Test
    public void footprintIgnoresNeighborBelowTheStandSurface() {
        List<AABB> obstacles = Arrays.asList(box(6.0, 63.0, 8.0, 7.0, 65.0, 9.0));
        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, obstacles, false);
        assertEquals("a neighbor whose top is at the stand surface does not block the body", 6 + HALF, r[1], EPS);
    }

    @Test
    public void paneAgainstHighXBlockCapsTheFootprintAtTheCellCentre() {
        AABB pane = box(5.5, 65.0, 8.4375, 6.0, 66.0, 8.5625);
        List<AABB> obstacles = Arrays.asList(pane);

        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.2, 8.5, obstacles, false);
        assertEquals("front face of a pane against a block sits at the cell centre", 5.5 - HALF, r[1], EPS);
        assertEquals(5 - HALF, r[0], EPS);
    }

    @Test
    public void paneAgainstLowXBlockRaisesTheLowSideToTheCellCentre() {
        AABB pane = box(5.0, 65.0, 8.4375, 5.5, 66.0, 8.5625);
        List<AABB> obstacles = Arrays.asList(pane);

        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.8, 8.5, obstacles, false);
        assertEquals("front face of a pane against a block sits at the cell centre", 5.5 + HALF, r[0], EPS);
        assertEquals(6 + HALF, r[1], EPS);
    }

    @Test
    public void diagonalNeighborDoesNotInsetAStraightApproach() {
        List<AABB> obstacles = Arrays.asList(cube(6, 65, 9));
        double[] r = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, obstacles, false);
        assertEquals(6 + HALF, r[1], EPS);
        assertEquals(9 + HALF, r[3], EPS);
    }

    @Test
    public void ownCellRiserAboveTheStandSurfaceClipsTheFootprint() {
        AABB slab = box(5.0, 64.0, 8.0, 6.0, 64.5, 9.0);
        AABB riser = box(5.5, 64.5, 8.0, 6.0, 65.0, 9.0);
        double[] r = ConstraintDeriver.deriveFootprint(slab, 5.25, 8.5, Arrays.asList(riser), false);
        assertEquals("the stair riser in the same cell blocks the body above the lower step", 5.5 - HALF, r[1], EPS);
        assertEquals(5 - HALF, r[0], EPS);
        assertEquals(8 - HALF, r[2], EPS);
        assertEquals(9 + HALF, r[3], EPS);
    }

    @Test
    public void coplanarCollinearBoxesMergeIntoOneSupport() {
        AABB topLip = box(5.0, 64.75, 8.6875, 6.0, 65.0, 8.8125);
        AABB backPanel = box(5.0, 64.0, 8.8125, 6.0, 65.0, 9.0);
        AABB bottomLip = box(5.0, 64.0, 8.6875, 6.0, 64.25, 8.8125);
        AABB merged = ConstraintDeriver.mergeCoplanarSupport(topLip, Arrays.asList(topLip, backPanel, bottomLip));
        assertEquals(8.6875, merged.min.z, 0.0);
        assertEquals("the flush back panel extends the standing surface", 9.0, merged.max.z, 0.0);
        assertEquals(5.0, merged.min.x, 0.0);
        assertEquals(6.0, merged.max.x, 0.0);
        assertEquals(65.0, merged.max.y, 0.0);
    }

    @Test
    public void ringWallsWithDifferentSpansStayUnmerged() {
        AABB north = box(5.0, 64.0, 8.0, 6.0, 65.0, 8.125);
        AABB east = box(5.875, 64.0, 8.0, 6.0, 65.0, 9.0);
        AABB merged = ConstraintDeriver.mergeCoplanarSupport(north, Arrays.asList(north, east));
        assertEquals("a rim wall keeps only the clicked strip", 8.125, merged.max.z, 0.0);
        assertEquals(5.0, merged.min.x, 0.0);
        assertEquals(6.0, merged.max.x, 0.0);
    }

    @Test
    public void modernFootprintPullsSupportEdgesInByTheCollideEpsilon() {
        double[] legacy = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, Collections.<AABB>emptyList(), false);
        double[] modern = ConstraintDeriver.deriveFootprint(cube(5, 64, 8), 5.5, 8.5, Collections.<AABB>emptyList(), true);
        assertEquals(1.0e-7, modern[0] - legacy[0], 1.0e-9);
        assertEquals(1.0e-7, legacy[1] - modern[1], 1.0e-9);
        assertEquals(1.0e-7, modern[2] - legacy[2], 1.0e-9);
        assertEquals(1.0e-7, legacy[3] - modern[3], 1.0e-9);
    }

    private static List<AABB> crossCornerWalls() {
        return Arrays.asList(
                box(6.0, 65.0, 7.0, 7.0, 66.0, 8.0),
                box(4.0, 65.0, 7.0, 5.0, 66.0, 8.0),
                box(6.0, 65.0, 9.0, 7.0, 66.0, 10.0),
                box(4.0, 65.0, 9.0, 5.0, 66.0, 10.0));
    }

    @Test
    public void intersectionCentreSplitsIntoTwoBarsAvoidingTheCornerWalls() {
        List<double[]> areas = ConstraintDeriver.deriveFootprintAreas(cube(5, 64, 8), 5.5, 8.5, crossCornerWalls(), false);
        assertEquals("a + intersection yields two bars", 2, areas.size());

        double[] hBar = areas.get(0);
        assertEquals("horizontal bar keeps the full X overhang", 5 - HALF, hBar[0], EPS);
        assertEquals(6 + HALF, hBar[1], EPS);
        assertEquals("horizontal bar Z is pulled off the corner walls", 8 + HALF, hBar[2], EPS);
        assertEquals(9 - HALF, hBar[3], EPS);

        double[] vBar = areas.get(1);
        assertEquals("vertical bar X is pulled off the corner walls", 5 + HALF, vBar[0], EPS);
        assertEquals(6 - HALF, vBar[1], EPS);
        assertEquals("vertical bar keeps the full Z overhang", 8 - HALF, vBar[2], EPS);
        assertEquals(9 + HALF, vBar[3], EPS);
    }

    @Test
    public void openBlockStaysASingleFootprintArea() {
        List<double[]> areas = ConstraintDeriver.deriveFootprintAreas(cube(5, 64, 8), 5.5, 8.5, Collections.<AABB>emptyList(), false);
        assertEquals(1, areas.size());
        double[] r = areas.get(0);
        assertEquals(5 - HALF, r[0], EPS);
        assertEquals(6 + HALF, r[1], EPS);
        assertEquals(8 - HALF, r[2], EPS);
        assertEquals(9 + HALF, r[3], EPS);
    }

    @Test
    public void diagonalCornerAtOrBelowTheStandSurfaceDoesNotSplit() {
        List<AABB> obstacles = Arrays.asList(box(6.0, 63.0, 9.0, 7.0, 65.0, 10.0));
        List<double[]> areas = ConstraintDeriver.deriveFootprintAreas(cube(5, 64, 8), 5.5, 8.5, obstacles, false);
        assertEquals("a corner whose top is at the stand surface does not clip the body", 1, areas.size());
    }

    @Test
    public void flushCardinalNeighbourStaysASingleArea() {
        List<AABB> obstacles = Arrays.asList(cube(6, 65, 8));
        List<double[]> areas = ConstraintDeriver.deriveFootprintAreas(cube(5, 64, 8), 5.5, 8.5, obstacles, false);
        assertEquals("a straight-on neighbour only insets one side, no split", 1, areas.size());
        assertEquals(6 - HALF, areas.get(0)[1], EPS);
    }

    @Test
    public void openCellKeepsExactBlockEdges() {
        double[] r = ConstraintDeriver.deriveCell(5, 8, 64.0, 5.5, 8.5, Collections.<AABB>emptyList());
        assertEquals(5.0, r[0], 0.0);
        assertEquals(6.0, r[1], 0.0);
        assertEquals(8.0, r[2], 0.0);
        assertEquals(9.0, r[3], 0.0);
    }

    @Test
    public void ladderPanelAndWallBehindClipTheCell() {
        AABB panel = box(5.0, 64.0, 8.0, 6.0, 65.0, 8.125);
        List<AABB> obstacles = Arrays.asList(panel, cube(5, 64, 7));
        double[] r = ConstraintDeriver.deriveCell(5, 8, 64.0, 5.5, 8.5, obstacles);
        assertEquals("panel face clips harder than the wall behind it", 8.125 + HALF, r[2], EPS);
        assertEquals(9.0, r[3], 0.0);
        assertEquals(5.0, r[0], 0.0);
        assertEquals(6.0, r[1], 0.0);
    }

    @Test
    public void adjacentWallClipsTheCellSide() {
        List<AABB> obstacles = Arrays.asList(cube(6, 65, 8));
        double[] r = ConstraintDeriver.deriveCell(5, 8, 65.0, 5.5, 8.5, obstacles);
        assertEquals(6 - HALF, r[1], EPS);
        assertEquals(5.0, r[0], 0.0);
    }

    @Test
    public void cellIgnoresObstacleBelowTheFeet() {
        List<AABB> obstacles = Arrays.asList(box(6.0, 63.0, 8.0, 7.0, 65.0, 9.0));
        double[] r = ConstraintDeriver.deriveCell(5, 8, 65.0, 5.5, 8.5, obstacles);
        assertEquals(6.0, r[1], 0.0);
    }
}
