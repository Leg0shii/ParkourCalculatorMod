package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.ArrayList;
import java.util.List;

/** Exact, SDP-free convex decomposition of the obstacle-free horizontal space into axis-aligned cells.
 *
 *  <p><b>Why this exists.</b> {@link BlockSolver} derives per-tick keep-out half-spaces with a bespoke
 *  geometric homotopy planner (side selection, forced crossing tick, +1 dilation). The research synthesis
 *  in {@code docs/research/findings-block-constraints-and-collision-integration.md} (§2.1, §5) recommends
 *  replacing that with the standard motion-planning lens: decompose the obstacle-free space into convex
 *  cells, then route a corridor of adjacent cells (the homotopy class) and run the existing closed-form
 *  dual inside each fixed cell. This class is the first half of that pipeline — the decomposition and its
 *  adjacency graph. The corridor / Graph-of-Convex-Sets selection layer is a separate, later step.
 *
 *  <p><b>Why not IRIS / Safe Flight Corridors.</b> Those grow convex regions by inflating an ellipsoid until
 *  it touches an obstacle, which goes degenerate or empty at the razor-tight (~1e-6) clearances this solver
 *  must keep feasible (§4.4). Because every block is an axis-aligned box, the obstacle-free space is exactly
 *  a union of axis-aligned rectangles obtainable by a coordinate plane-sweep in {@code O(n log n)} — pure
 *  coordinate comparisons, no ellipsoid, no SDP, and nothing that collapses at 1e-6.
 *
 *  <p><b>Configuration space, not safety margin.</b> Obstacles are expanded by the player half-width
 *  {@code PLAYER_HALF = 0.3} so the player <i>centre</i> (a point) must avoid the expanded rectangles. This
 *  is exact C-space for a point robot — it matches {@link BlockSolver}'s own {@code o.max.x + HALF} edges and
 *  is <i>not</i> the conservative inflation §4.4 forbids; no extra padding is added.
 *
 *  <p><b>Scope.</b> Horizontal X/Z only — Y is decoupled (no input touches it) and is gated downstream
 *  exactly as {@link BlockSolver#yActive} does, by intersecting a cell's owning block Y-range with the
 *  player's per-tick Y window. A 2-D cell here is "an obstacle while the player's feet overlap the block in
 *  Y"; this class deliberately leaves that temporal gating to the corridor layer. */
public final class FreeSpaceDecomposition {

    /** Player hitbox half-width (blocks); obstacles expand by this to form C-space rectangles. */
    public static final double PLAYER_HALF = 0.3;

    /** Coordinates closer than this are treated as identical (drops zero-width slabs from duplicate edges). */
    private static final double COORD_EPS = 1.0e-12;

    private FreeSpaceDecomposition() {
    }

    /** An axis-aligned rectangular region of the X/Z plane (also used for cells and C-space obstacles). */
    public static final class Rect {
        public final double xLo, xHi, zLo, zHi;

        public Rect(double xLo, double xHi, double zLo, double zHi) {
            this.xLo = xLo;
            this.xHi = xHi;
            this.zLo = zLo;
            this.zHi = zHi;
        }

        public double area() {
            return Math.max(0.0, xHi - xLo) * Math.max(0.0, zHi - zLo);
        }

        public boolean containsPoint(double x, double z) {
            return x >= xLo && x <= xHi && z >= zLo && z <= zHi;
        }

        /** Strict-interior membership: used to classify a cell centre against a C-space obstacle. */
        public boolean strictlyContains(double x, double z) {
            return x > xLo && x < xHi && z > zLo && z < zHi;
        }
    }

    /** One convex free cell: an axis-aligned rectangle of obstacle-free C-space, plus which of its four
     *  sides is a real obstacle wall (versus the open outer region boundary). The wall sides are exactly the
     *  per-tick keep-out half-spaces to feed the inner solve; non-wall sides are the region frame and emit no
     *  constraint. */
    public static final class Cell {
        public final Rect rect;
        public final boolean wallXLo, wallXHi, wallZLo, wallZHi;

        Cell(Rect rect, boolean wallXLo, boolean wallXHi, boolean wallZLo, boolean wallZHi) {
            this.rect = rect;
            this.wallXLo = wallXLo;
            this.wallXHi = wallXHi;
            this.wallZLo = wallZLo;
            this.wallZHi = wallZHi;
        }

        /** The cell's obstacle-wall sides as per-tick {@link JumpConstraint}s in the solver's alphabet
         *  (Mode.X/Z, GE/LE). Open region-boundary sides emit nothing, so a cell never over-constrains with
         *  arbitrary frame walls. Confining a tick to this cell == applying all returned constraints. */
        public List<JumpConstraint> wallFaces(int tick) {
            List<JumpConstraint> out = new ArrayList<>(4);
            if (wallXLo) out.add(face(JumpConstraint.Mode.X, tick, JumpConstraint.Cmp.GE, rect.xLo, "cellXLo@" + tick));
            if (wallXHi) out.add(face(JumpConstraint.Mode.X, tick, JumpConstraint.Cmp.LE, rect.xHi, "cellXHi@" + tick));
            if (wallZLo) out.add(face(JumpConstraint.Mode.Z, tick, JumpConstraint.Cmp.GE, rect.zLo, "cellZLo@" + tick));
            if (wallZHi) out.add(face(JumpConstraint.Mode.Z, tick, JumpConstraint.Cmp.LE, rect.zHi, "cellZHi@" + tick));
            return out;
        }

        private static JumpConstraint face(JumpConstraint.Mode mode, int t, JumpConstraint.Cmp cmp, double v, String name) {
            return new JumpConstraint(mode, t, null, JumpConstraint.Op.PLUS, cmp, v, name);
        }
    }

    /** The decomposition: the free cells and the adjacency graph over them (cells sharing a boundary segment
     *  of positive length). Edges of the graph are the corridor moves the later GCS/routing layer searches. */
    public static final class Decomposition {
        public final Rect region;
        public final List<Cell> cells;
        public final List<Rect> cspaceObstacles; // expanded, region-clipped obstacle rectangles
        /** adjacency[i] = indices of cells sharing a positive-length border with cell i. */
        public final int[][] adjacency;

        Decomposition(Rect region, List<Cell> cells, List<Rect> cspaceObstacles, int[][] adjacency) {
            this.region = region;
            this.cells = cells;
            this.cspaceObstacles = cspaceObstacles;
            this.adjacency = adjacency;
        }

        /** Index of the free cell containing {@code (x,z)}, or -1 (boundary ties resolve to the first hit). */
        public int cellAt(double x, double z) {
            for (int i = 0; i < cells.size(); i++) if (cells.get(i).rect.containsPoint(x, z)) return i;
            return -1;
        }
    }

    /** Decompose, deriving the working region as the bounding box of every block plus the seed and landing
     *  points, padded by {@code pad} blocks so the reachable approach/exit space is represented. */
    public static Decomposition decompose(List<AABB> blocks, double seedX, double seedZ,
                                          double landX, double landZ, double pad) {
        double xLo = Math.min(seedX, landX), xHi = Math.max(seedX, landX);
        double zLo = Math.min(seedZ, landZ), zHi = Math.max(seedZ, landZ);
        for (AABB b : blocks) {
            xLo = Math.min(xLo, b.min.x - PLAYER_HALF);
            xHi = Math.max(xHi, b.max.x + PLAYER_HALF);
            zLo = Math.min(zLo, b.min.z - PLAYER_HALF);
            zHi = Math.max(zHi, b.max.z + PLAYER_HALF);
        }
        return decompose(blocks, new Rect(xLo - pad, xHi + pad, zLo - pad, zHi + pad));
    }

    /** Decompose the {@code region} of the X/Z plane minus the (C-space-expanded) blocks into free cells.
     *  Plane-sweep: cut the region along every obstacle edge, classify each grid tile by its centre, then
     *  strip-merge free tiles along X within each Z band into maximal-width cells. */
    public static Decomposition decompose(List<AABB> blocks, Rect region) {
        List<Rect> obstacles = new ArrayList<>();
        for (AABB b : blocks) {
            double oxLo = clamp(b.min.x - PLAYER_HALF, region.xLo, region.xHi);
            double oxHi = clamp(b.max.x + PLAYER_HALF, region.xLo, region.xHi);
            double ozLo = clamp(b.min.z - PLAYER_HALF, region.zLo, region.zHi);
            double ozHi = clamp(b.max.z + PLAYER_HALF, region.zLo, region.zHi);
            if (oxHi - oxLo > COORD_EPS && ozHi - ozLo > COORD_EPS) {
                obstacles.add(new Rect(oxLo, oxHi, ozLo, ozHi));
            }
        }

        double[] xCuts = cutCoords(region.xLo, region.xHi, obstacles, true);
        double[] zCuts = cutCoords(region.zLo, region.zHi, obstacles, false);

        List<Cell> cells = new ArrayList<>();
        // For each Z band, collect the free X tiles, then merge contiguous free tiles into one cell.
        for (int zj = 0; zj + 1 < zCuts.length; zj++) {
            double zL = zCuts[zj], zH = zCuts[zj + 1];
            if (zH - zL <= COORD_EPS) continue;
            double zMid = 0.5 * (zL + zH);

            int runStart = -1; // index of the first X tile in the current free run
            for (int xi = 0; xi + 1 < xCuts.length; xi++) {
                double xL = xCuts[xi], xH = xCuts[xi + 1];
                boolean degenerate = xH - xL <= COORD_EPS;
                boolean free = !degenerate && !blocked(0.5 * (xL + xH), zMid, obstacles);
                if (free) {
                    if (runStart < 0) runStart = xi;
                } else if (runStart >= 0) {
                    cells.add(makeCell(xCuts[runStart], xCuts[xi], zL, zH, region, obstacles));
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                cells.add(makeCell(xCuts[runStart], xCuts[xCuts.length - 1], zL, zH, region, obstacles));
            }
        }

        return new Decomposition(region, cells, obstacles, buildAdjacency(cells));
    }

    // ---- internals --------------------------------------------------------------------------------------

    /** Sorted, de-duplicated cut coordinates: the region bounds plus every obstacle edge on the chosen axis
     *  (X if {@code xAxis}, else Z), all within the region. These slice the region into grid tiles each of
     *  which lies wholly inside or wholly outside every obstacle, so a single centre test classifies it. */
    private static double[] cutCoords(double lo, double hi, List<Rect> obstacles, boolean xAxis) {
        List<Double> vals = new ArrayList<>();
        vals.add(lo);
        vals.add(hi);
        for (Rect o : obstacles) {
            double a = xAxis ? o.xLo : o.zLo;
            double b = xAxis ? o.xHi : o.zHi;
            if (a > lo + COORD_EPS && a < hi - COORD_EPS) vals.add(a);
            if (b > lo + COORD_EPS && b < hi - COORD_EPS) vals.add(b);
        }
        vals.sort(Double::compare);
        double[] out = new double[vals.size()];
        int k = 0;
        for (double v : vals) {
            if (k == 0 || v - out[k - 1] > COORD_EPS) out[k++] = v;
        }
        return java.util.Arrays.copyOf(out, k);
    }

    private static boolean blocked(double x, double z, List<Rect> obstacles) {
        for (Rect o : obstacles) if (o.strictlyContains(x, z)) return true;
        return false;
    }

    /** Build a cell and decide which sides are real obstacle walls: a side is a wall unless it lies on the
     *  outer region boundary (the only non-obstacle cut), in which case it is open and emits no constraint. */
    private static Cell makeCell(double xLo, double xHi, double zLo, double zHi, Rect region, List<Rect> obstacles) {
        Rect r = new Rect(xLo, xHi, zLo, zHi);
        boolean wXLo = !near(xLo, region.xLo) && touchesObstacleX(xLo, zLo, zHi, obstacles, true);
        boolean wXHi = !near(xHi, region.xHi) && touchesObstacleX(xHi, zLo, zHi, obstacles, false);
        boolean wZLo = !near(zLo, region.zLo) && touchesObstacleZ(zLo, xLo, xHi, obstacles, true);
        boolean wZHi = !near(zHi, region.zHi) && touchesObstacleZ(zHi, xLo, xHi, obstacles, false);
        return new Cell(r, wXLo, wXHi, wZLo, wZHi);
    }

    /** True if some obstacle's vertical edge sits at {@code x} and overlaps the cell's Z span on the side the
     *  cell faces (so confining the cell to {@code x} actually keeps the player off that obstacle). */
    private static boolean touchesObstacleX(double x, double zLo, double zHi, List<Rect> obstacles, boolean cellIsAbove) {
        for (Rect o : obstacles) {
            double edge = cellIsAbove ? o.xHi : o.xLo; // cell sits on the +X side of o.xHi, or -X side of o.xLo
            if (near(edge, x) && o.zHi > zLo + COORD_EPS && o.zLo < zHi - COORD_EPS) return true;
        }
        return false;
    }

    private static boolean touchesObstacleZ(double z, double xLo, double xHi, List<Rect> obstacles, boolean cellIsAbove) {
        for (Rect o : obstacles) {
            double edge = cellIsAbove ? o.zHi : o.zLo;
            if (near(edge, z) && o.xHi > xLo + COORD_EPS && o.xLo < xHi - COORD_EPS) return true;
        }
        return false;
    }

    /** Adjacency: two cells are neighbours iff they share a boundary segment of positive length (touch in X
     *  with overlapping Z, or touch in Z with overlapping X). This is the corridor graph. */
    private static int[][] buildAdjacency(List<Cell> cells) {
        int nc = cells.size();
        List<List<Integer>> adj = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) adj.add(new ArrayList<>());
        for (int i = 0; i < nc; i++) {
            Rect a = cells.get(i).rect;
            for (int j = i + 1; j < nc; j++) {
                Rect b = cells.get(j).rect;
                double zOv = Math.min(a.zHi, b.zHi) - Math.max(a.zLo, b.zLo);
                double xOv = Math.min(a.xHi, b.xHi) - Math.max(a.xLo, b.xLo);
                boolean touchX = (near(a.xHi, b.xLo) || near(a.xLo, b.xHi)) && zOv > COORD_EPS;
                boolean touchZ = (near(a.zHi, b.zLo) || near(a.zLo, b.zHi)) && xOv > COORD_EPS;
                if (touchX || touchZ) {
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }
        int[][] out = new int[nc][];
        for (int i = 0; i < nc; i++) {
            out[i] = new int[adj.get(i).size()];
            for (int k = 0; k < out[i].length; k++) out[i][k] = adj.get(i).get(k);
        }
        return out;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= COORD_EPS;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
