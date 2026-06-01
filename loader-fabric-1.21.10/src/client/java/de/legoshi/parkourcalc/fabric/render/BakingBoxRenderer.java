package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;

/** Bakes box geometry into a VertexSink as anchor-relative float offsets (vertex order matches FabricBoxRenderer). */
public final class BakingBoxRenderer implements BoxRenderer {

    public interface VertexSink {
        /** Called before a primitive's vertices so the sink can roll to a new buffer without splitting it. */
        void beginPrimitive(int vertexCount);

        void vertex(float x, float y, float z, int argb);
    }

    private final VertexSink sink;
    private final Mode mode;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;

    public BakingBoxRenderer(VertexSink sink, Mode mode, double anchorX, double anchorY, double anchorZ) {
        this.sink = sink;
        this.mode = mode;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            emitEdges(box, argb);
        } else {
            emitFaces(box, argb);
        }
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode != Mode.LINES) return;
        sink.beginPrimitive(2);
        vertexAbs(x1, y1, z1, argb);
        vertexAbs(x2, y2, z2, argb);
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3, int argb) {
        if (mode != Mode.FACES) return;
        sink.beginPrimitive(3);
        vertexAbs(x1, y1, z1, argb);
        vertexAbs(x2, y2, z2, argb);
        vertexAbs(x3, y3, z3, argb);
    }

    private void emitFaces(AABB b, int argb) {
        float x0 = (float) (b.min.x - anchorX), y0 = (float) (b.min.y - anchorY), z0 = (float) (b.min.z - anchorZ);
        float x1 = (float) (b.max.x - anchorX), y1 = (float) (b.max.y - anchorY), z1 = (float) (b.max.z - anchorZ);

        // -Y
        tri(x0, y0, z0, x1, y0, z0, x1, y0, z1, argb);
        tri(x0, y0, z0, x1, y0, z1, x0, y0, z1, argb);
        // +Y
        tri(x0, y1, z0, x0, y1, z1, x1, y1, z1, argb);
        tri(x0, y1, z0, x1, y1, z1, x1, y1, z0, argb);
        // -Z
        tri(x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
        tri(x0, y0, z0, x1, y1, z0, x1, y0, z0, argb);
        // +Z
        tri(x0, y0, z1, x1, y0, z1, x1, y1, z1, argb);
        tri(x0, y0, z1, x1, y1, z1, x0, y1, z1, argb);
        // -X
        tri(x0, y0, z0, x0, y0, z1, x0, y1, z1, argb);
        tri(x0, y0, z0, x0, y1, z1, x0, y1, z0, argb);
        // +X
        tri(x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
        tri(x1, y0, z0, x1, y1, z1, x1, y0, z1, argb);
    }

    private void emitEdges(AABB b, int argb) {
        float x0 = (float) (b.min.x - anchorX), y0 = (float) (b.min.y - anchorY), z0 = (float) (b.min.z - anchorZ);
        float x1 = (float) (b.max.x - anchorX), y1 = (float) (b.max.y - anchorY), z1 = (float) (b.max.z - anchorZ);

        edge(x0, y0, z0, x1, y0, z0, argb);
        edge(x1, y0, z0, x1, y0, z1, argb);
        edge(x1, y0, z1, x0, y0, z1, argb);
        edge(x0, y0, z1, x0, y0, z0, argb);

        edge(x0, y1, z0, x1, y1, z0, argb);
        edge(x1, y1, z0, x1, y1, z1, argb);
        edge(x1, y1, z1, x0, y1, z1, argb);
        edge(x0, y1, z1, x0, y1, z0, argb);

        edge(x0, y0, z0, x0, y1, z0, argb);
        edge(x1, y0, z0, x1, y1, z0, argb);
        edge(x1, y0, z1, x1, y1, z1, argb);
        edge(x0, y0, z1, x0, y1, z1, argb);
    }

    private void tri(float ax, float ay, float az,
                     float bx, float by, float bz,
                     float cx, float cy, float cz, int argb) {
        sink.beginPrimitive(3);
        sink.vertex(ax, ay, az, argb);
        sink.vertex(bx, by, bz, argb);
        sink.vertex(cx, cy, cz, argb);
    }

    private void edge(float ax, float ay, float az, float bx, float by, float bz, int argb) {
        sink.beginPrimitive(2);
        sink.vertex(ax, ay, az, argb);
        sink.vertex(bx, by, bz, argb);
    }

    private void vertexAbs(double x, double y, double z, int argb) {
        sink.vertex((float) (x - anchorX), (float) (y - anchorY), (float) (z - anchorZ), argb);
    }
}
