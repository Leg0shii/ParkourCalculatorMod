package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.FreeSpaceDecomposition;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeSpaceDecomposition.Cell;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeSpaceDecomposition.Decomposition;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeSpaceDecomposition.Rect;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Invariant tests for {@link FreeSpaceDecomposition}: every returned cell is genuinely obstacle-free, the
 *  cells partition the region (cover it, interiors disjoint), the C-space expansion matches the player
 *  half-width, razor-tight (1e-6) corridors survive (the IRIS/SFC failure case), and the emitted faces stay
 *  inside the solver's {@link JumpConstraint} alphabet. */
public class FreeSpaceDecompositionTest {

    private static final double HALF = FreeSpaceDecomposition.PLAYER_HALF;

    private static AABB cube(double x, double z) {
        // Unit block footprint at integer (x,z); Y range arbitrary (decoupled), kept 0..1.
        return new AABB(new Vec3dCore(x, 0, z), new Vec3dCore(x + 1, 1, z + 1));
    }

    private static AABB box(double xLo, double zLo, double xHi, double zHi) {
        return new AABB(new Vec3dCore(xLo, 0, zLo), new Vec3dCore(xHi, 1, zHi));
    }

    // ---- coverage / disjointness via Monte-Carlo: every point is in exactly one free cell, XOR blocked ----

    @Test
    public void emptyRegionIsASingleOpenCell() {
        Decomposition d = FreeSpaceDecomposition.decompose(new ArrayList<>(),
                new Rect(-5, 5, -5, 5));
        assertEquals(1, d.cells.size());
        Cell c = d.cells.get(0);
        assertEquals(0, c.wallFaces(0).size()); // no obstacles => no walls, only the open region frame
        assertEquals(100.0, c.rect.area(), 1e-9);
    }

    @Test
    public void cellsPartitionTheRegion_singleBlock() {
        List<AABB> blocks = new ArrayList<>();
        blocks.add(cube(0, 0));
        Rect region = new Rect(-4, 5, -4, 5);
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, region);
        assertCoversAndDisjoint(d, region, 200_000);

        // The C-space obstacle is the block grown by HALF on every side: [-0.3,1.3] x [-0.3,1.3].
        assertEquals(1, d.cspaceObstacles.size());
        Rect o = d.cspaceObstacles.get(0);
        assertEquals(-HALF, o.xLo, 1e-12);
        assertEquals(1 + HALF, o.xHi, 1e-12);
        assertEquals(-HALF, o.zLo, 1e-12);
        assertEquals(1 + HALF, o.zHi, 1e-12);
    }

    @Test
    public void cellsPartitionTheRegion_cluster() {
        List<AABB> blocks = new ArrayList<>();
        blocks.add(cube(0, 0));
        blocks.add(cube(2, 0));
        blocks.add(cube(0, 3));
        blocks.add(box(3.5, 3.5, 5.0, 4.0)); // a slab-shaped partial block
        Rect region = new Rect(-3, 8, -3, 8);
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, region);
        assertCoversAndDisjoint(d, region, 400_000);
    }

    @Test
    public void noFreeCellOverlapsAnObstacleInterior() {
        List<AABB> blocks = new ArrayList<>();
        blocks.add(cube(0, 0));
        blocks.add(cube(2, 1));
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, new Rect(-3, 6, -3, 6));
        for (Cell c : d.cells) {
            double cx = 0.5 * (c.rect.xLo + c.rect.xHi);
            double cz = 0.5 * (c.rect.zLo + c.rect.zHi);
            for (Rect o : d.cspaceObstacles) {
                assertFalse("cell centre inside C-space obstacle", o.strictlyContains(cx, cz));
            }
        }
    }

    // ---- the IRIS/SFC failure case: a razor-tight corridor must survive, no ellipsoid to collapse ----

    @Test
    public void tinyCorridorSurvives_andConnectsTheTwoRooms() {
        // Two-rooms-one-doorway layout: a horizontal wall band (z in [0,0.4]) spans the FULL region width
        // except a 1e-6 doorway, so top and bottom rooms connect ONLY through the gap. The wall segments
        // reach the region edges (their outer edges clamp to the frame), so there is no route around them.
        // left segment right edge x=0 -> C-space xHi=0.3; right segment left edge x=0.6+gap -> C-space
        // xLo=0.3+gap, leaving a free corridor x in [0.3, 0.3+gap].
        double gapX = 1.0e-6;
        Rect region = new Rect(-2, 3, -2, 3);
        List<AABB> blocks = new ArrayList<>();
        blocks.add(box(region.xLo, 0.0, 0.0, 0.4));          // left wall segment, reaches the frame
        blocks.add(box(0.6 + gapX, 0.0, region.xHi, 0.4));   // right wall segment, reaches the frame
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, region);

        // A free cell of width ~1e-6 exists inside the wall band, straddling x=0.3.
        int corridor = d.cellAt(0.3 + gapX / 2, 0.2);
        assertTrue("expected a free corridor cell at the 1e-6 gap", corridor >= 0);
        double w = d.cells.get(corridor).rect.xHi - d.cells.get(corridor).rect.xLo;
        assertTrue("corridor width should be ~1e-6, was " + w, w > 0 && w <= 2 * gapX + 1e-12);

        // The corridor's X sides are real obstacle walls (the two block edges), not the open frame.
        Cell c = d.cells.get(corridor);
        assertTrue(c.wallXLo);
        assertTrue(c.wallXHi);

        // Graph reachability: the bottom room reaches the top room, and ONLY through the doorway corridor.
        int below = d.cellAt(0.3 + gapX / 2, -1.5);
        int above = d.cellAt(0.3 + gapX / 2, 2.5);
        assertTrue(below >= 0 && above >= 0);
        assertTrue("rooms must connect through the corridor", connected(d, below, above));
        assertTrue("the only path between rooms must traverse the corridor",
                onEveryPath(d, below, above, corridor));
    }

    @Test
    public void sealedDoorwayIsNotPassable() {
        // Same two-rooms layout but world gap 0.5 < 2*HALF=0.6: the C-space obstacles overlap, the doorway
        // seals, and the rooms become disconnected. Confirms the method never invents a passage the player
        // cannot fit, even though the blocks leave a visible 0.5-wide hole.
        Rect region = new Rect(-2, 3, -2, 3);
        List<AABB> blocks = new ArrayList<>();
        blocks.add(box(region.xLo, 0.0, 0.0, 0.4));
        blocks.add(box(0.5, 0.0, region.xHi, 0.4)); // C-space xLo=0.2 < left xHi 0.3 -> overlap, seam sealed
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, region);

        assertEquals("the sealed seam must be inside an obstacle, not a free cell", -1, d.cellAt(0.25, 0.2));
        int below = d.cellAt(0.25, -1.5);
        int above = d.cellAt(0.25, 2.5);
        assertTrue(below >= 0 && above >= 0);
        assertFalse("a sealed sub-0.6 doorway must not be passable", connected(d, below, above));
    }

    // ---- alphabet compatibility: faces are valid JumpConstraints the inner solver already ingests ----

    @Test
    public void wallFacesAreValidPositionConstraints() {
        List<AABB> blocks = new ArrayList<>();
        blocks.add(cube(1, 1));
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, new Rect(-3, 5, -3, 5));
        int withWalls = 0;
        for (Cell c : d.cells) {
            List<JumpConstraint> faces = c.wallFaces(4);
            withWalls += faces.isEmpty() ? 0 : 1;
            for (JumpConstraint f : faces) {
                assertTrue("face must be a position (X/Z) constraint, never facing",
                        f.mode == JumpConstraint.Mode.X || f.mode == JumpConstraint.Mode.Z);
                assertTrue(f.cmp == JumpConstraint.Cmp.GE || f.cmp == JumpConstraint.Cmp.LE);
                assertEquals(4, f.t1);
                assertEquals(null, f.t2);
                assertTrue(Double.isFinite(f.rhs));
            }
        }
        assertTrue("the block should induce at least one walled cell", withWalls > 0);
    }

    @Test
    public void autoRegionContainsSeedLandingAndBlocks() {
        List<AABB> blocks = new ArrayList<>();
        blocks.add(cube(3, 0));
        Decomposition d = FreeSpaceDecomposition.decompose(blocks, 0.0, 0.0, 6.0, 0.0, 2.0);
        assertTrue("seed must lie in a free cell", d.cellAt(0.0, 0.0) >= 0);
        assertTrue("landing must lie in a free cell", d.cellAt(6.0, 0.0) >= 0);
    }

    // ---- non-asserting timing benchmark, mirroring SolveBenchmark's style ----

    @org.junit.Ignore("manual benchmark, not an assertion test")
    @Test
    public void benchmarkScaling() {
        int[] counts = {1, 4, 16, 64, 256};
        Random rng = new Random(42);
        for (int n : counts) {
            List<AABB> blocks = new ArrayList<>();
            int side = (int) Math.ceil(Math.sqrt(n));
            for (int i = 0; i < n; i++) {
                double x = (i % side) * 2 + rng.nextDouble() * 0.1;
                double z = (i / side) * 2 + rng.nextDouble() * 0.1;
                blocks.add(cube(x, z));
            }
            Rect region = new Rect(-2, side * 2 + 2, -2, side * 2 + 2);
            for (int w = 0; w < 3; w++) FreeSpaceDecomposition.decompose(blocks, region); // warmup
            long t0 = System.nanoTime();
            Decomposition d = FreeSpaceDecomposition.decompose(blocks, region);
            long us = (System.nanoTime() - t0) / 1000;
            System.out.printf("blocks=%-4d cells=%-5d  %d us%n", n, d.cells.size(), us);
        }
    }

    // ---- helpers ----

    /** Monte-Carlo partition check: a uniform sample point lands in exactly one free cell unless it is inside
     *  a C-space obstacle, in which case it lands in none. Catches gaps (uncovered free space) and overlaps. */
    private static void assertCoversAndDisjoint(Decomposition d, Rect region, int samples) {
        Random rng = new Random(7);
        for (int s = 0; s < samples; s++) {
            double x = region.xLo + rng.nextDouble() * (region.xHi - region.xLo);
            double z = region.zLo + rng.nextDouble() * (region.zHi - region.zLo);
            boolean blocked = false;
            for (Rect o : d.cspaceObstacles) {
                if (o.strictlyContains(x, z)) { blocked = true; break; }
            }
            int hits = 0;
            for (Cell c : d.cells) if (c.rect.strictlyContains(x, z)) hits++;
            if (blocked) {
                assertEquals("point inside an obstacle must be in no free cell", 0, hits);
            } else {
                // Strict-interior points of free space must be covered exactly once. Points exactly on a cut
                // line (measure zero) are ignored to avoid boundary-tie flakiness.
                if (onAnyCutBoundary(d, x, z)) continue;
                assertEquals("free point must be covered by exactly one cell", 1, hits);
            }
        }
    }

    private static boolean onAnyCutBoundary(Decomposition d, double x, double z) {
        for (Cell c : d.cells) {
            if (Math.abs(x - c.rect.xLo) < 1e-9 || Math.abs(x - c.rect.xHi) < 1e-9
                    || Math.abs(z - c.rect.zLo) < 1e-9 || Math.abs(z - c.rect.zHi) < 1e-9) return true;
        }
        return false;
    }

    private static boolean connected(Decomposition d, int from, int to) {
        return connectedExcluding(d, from, to, -1);
    }

    /** True if {@code via} lies on every path from {@code from} to {@code to} (i.e. it is a cut vertex for
     *  that pair): removing it disconnects them. */
    private static boolean onEveryPath(Decomposition d, int from, int to, int via) {
        return !connectedExcluding(d, from, to, via);
    }

    private static boolean connectedExcluding(Decomposition d, int from, int to, int blocked) {
        if (from == blocked || to == blocked) return false;
        boolean[] seen = new boolean[d.cells.size()];
        Deque<Integer> q = new ArrayDeque<>();
        q.add(from);
        seen[from] = true;
        while (!q.isEmpty()) {
            int u = q.poll();
            if (u == to) return true;
            for (int v : d.adjacency[u]) if (v != blocked && !seen[v]) { seen[v] = true; q.add(v); }
        }
        return false;
    }
}
