package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.List;

/**
 * Faithful port of MC 1.8.9 {@code Entity.moveEntity} collision for a single tick's move against static
 * block AABBs. MC clamps the intended motion axis by axis in the order <b>Y, then X, then Z</b>, offsetting
 * the hitbox between each. That order is why collision is asymmetric: the X clamp tests against the player's
 * START Z (so a move that crosses a block in X must already be clear in Z the tick before), while the Z clamp
 * tests against the player's ALREADY-MOVED X (so X can clear in the same tick a Z crossing happens).
 *
 * <p>The angle model is collision-free, so its per-tick positions are the INTENDED move; this detects whether
 * the real entity would clamp (i.e. clip a block) between two such positions, which the endpoint check misses.
 */
public final class SweptCollision {

    public static final char NONE = 0;

    public static final class Hit {
        public final char axis;    // 'X', 'Y', 'Z', or NONE
        public final int blockIndex;

        public Hit(char axis, int blockIndex) {
            this.axis = axis;
            this.blockIndex = blockIndex;
        }

        public boolean any() {
            return axis != NONE;
        }
    }

    /** The first axis MC would clamp moving a {@code half}-wide, {@code height}-tall player from p0 to p1, or NONE. */
    public static Hit firstHit(double p0x, double p0y, double p0z, double p1x, double p1y, double p1z,
                               double half, double height, List<AABB> blocks) {
        double mx = p1x - p0x;
        double my = p1y - p0y;
        double mz = p1z - p0z;

        double minX = p0x - half, maxX = p0x + half;
        double minY = p0y, maxY = p0y + height;
        double minZ = p0z - half, maxZ = p0z + half;

        double y = my;
        int yObs = -1;
        for (int i = 0; i < blocks.size(); i++) {
            double ny = yOff(minX, minY, minZ, maxX, maxY, maxZ, blocks.get(i), y);
            if (ny != y) { y = ny; yObs = i; }
        }
        minY += y; maxY += y;

        double x = mx;
        int xObs = -1;
        for (int i = 0; i < blocks.size(); i++) {
            double nx = xOff(minX, minY, minZ, maxX, maxY, maxZ, blocks.get(i), x);
            if (nx != x) { x = nx; xObs = i; }
        }
        minX += x; maxX += x;

        double z = mz;
        int zObs = -1;
        for (int i = 0; i < blocks.size(); i++) {
            double nz = zOff(minX, minY, minZ, maxX, maxY, maxZ, blocks.get(i), z);
            if (nz != z) { z = nz; zObs = i; }
        }

        if (xObs >= 0) return new Hit('X', xObs);
        if (zObs >= 0) return new Hit('Z', zObs);
        if (yObs >= 0) return new Hit('Y', yObs);
        return new Hit(NONE, -1);
    }

    private static double xOff(double pMinX, double pMinY, double pMinZ, double pMaxX, double pMaxY, double pMaxZ, AABB o, double off) {
        if (pMaxY > o.min.y && pMinY < o.max.y && pMaxZ > o.min.z && pMinZ < o.max.z) {
            if (off > 0.0 && pMaxX <= o.min.x) {
                double e = o.min.x - pMaxX;
                if (e < off) off = e;
            } else if (off < 0.0 && pMinX >= o.max.x) {
                double e = o.max.x - pMinX;
                if (e > off) off = e;
            }
        }
        return off;
    }

    private static double yOff(double pMinX, double pMinY, double pMinZ, double pMaxX, double pMaxY, double pMaxZ, AABB o, double off) {
        if (pMaxX > o.min.x && pMinX < o.max.x && pMaxZ > o.min.z && pMinZ < o.max.z) {
            if (off > 0.0 && pMaxY <= o.min.y) {
                double e = o.min.y - pMaxY;
                if (e < off) off = e;
            } else if (off < 0.0 && pMinY >= o.max.y) {
                double e = o.max.y - pMinY;
                if (e > off) off = e;
            }
        }
        return off;
    }

    private static double zOff(double pMinX, double pMinY, double pMinZ, double pMaxX, double pMaxY, double pMaxZ, AABB o, double off) {
        if (pMaxX > o.min.x && pMinX < o.max.x && pMaxY > o.min.y && pMinY < o.max.y) {
            if (off > 0.0 && pMaxZ <= o.min.z) {
                double e = o.min.z - pMaxZ;
                if (e < off) off = e;
            } else if (off < 0.0 && pMinZ >= o.max.z) {
                double e = o.max.z - pMinZ;
                if (e > off) off = e;
            }
        }
        return off;
    }
}
