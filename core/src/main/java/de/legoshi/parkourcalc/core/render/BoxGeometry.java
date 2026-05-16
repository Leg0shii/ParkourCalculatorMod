package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.sim.AABB;

/**
 * Camera-relative box vertex emission shared between the Forge 1.8.9 and 1.12.2
 * BoxRenderer impls. The Fabric renderer uses a different vertex shape and is
 * not consolidated here.
 */
public final class BoxGeometry {

    private BoxGeometry() {}

    public interface VertexEmitter {
        void emit(double x, double y, double z, int argb);
    }

    /** Emit twelve edges (24 vertices) suitable for a GL_LINES draw. */
    public static void emitEdges(AABB b, double camX, double camY, double camZ, int argb, VertexEmitter out) {
        double x0 = b.min.x - camX, y0 = b.min.y - camY, z0 = b.min.z - camZ;
        double x1 = b.max.x - camX, y1 = b.max.y - camY, z1 = b.max.z - camZ;

        edge(x0, y0, z0, x1, y0, z0, argb, out);
        edge(x1, y0, z0, x1, y0, z1, argb, out);
        edge(x1, y0, z1, x0, y0, z1, argb, out);
        edge(x0, y0, z1, x0, y0, z0, argb, out);

        edge(x0, y1, z0, x1, y1, z0, argb, out);
        edge(x1, y1, z0, x1, y1, z1, argb, out);
        edge(x1, y1, z1, x0, y1, z1, argb, out);
        edge(x0, y1, z1, x0, y1, z0, argb, out);

        edge(x0, y0, z0, x0, y1, z0, argb, out);
        edge(x1, y0, z0, x1, y1, z0, argb, out);
        edge(x1, y0, z1, x1, y1, z1, argb, out);
        edge(x0, y0, z1, x0, y1, z1, argb, out);
    }

    /** Emit six quads as 36 vertices suitable for a GL_TRIANGLES draw. */
    public static void emitFaces(AABB b, double camX, double camY, double camZ, int argb, VertexEmitter out) {
        double x0 = b.min.x - camX, y0 = b.min.y - camY, z0 = b.min.z - camZ;
        double x1 = b.max.x - camX, y1 = b.max.y - camY, z1 = b.max.z - camZ;

        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb, out);
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, argb, out);
        quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, argb, out);
        quad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, argb, out);
        quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, argb, out);
        quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, argb, out);
    }

    private static void edge(double ax, double ay, double az,
                             double bx, double by, double bz, int argb, VertexEmitter out) {
        out.emit(ax, ay, az, argb);
        out.emit(bx, by, bz, argb);
    }

    private static void quad(double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz, int argb, VertexEmitter out) {
        out.emit(ax, ay, az, argb);
        out.emit(bx, by, bz, argb);
        out.emit(cx, cy, cz, argb);
        out.emit(ax, ay, az, argb);
        out.emit(cx, cy, cz, argb);
        out.emit(dx, dy, dz, argb);
    }
}
