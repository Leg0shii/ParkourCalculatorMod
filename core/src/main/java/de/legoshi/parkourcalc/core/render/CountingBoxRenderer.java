package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;

public final class CountingBoxRenderer implements BoxRenderer {

    private final Mode mode;
    private long vertexCount;

    public CountingBoxRenderer(Mode mode) {
        this.mode = mode;
    }

    public long vertexCount() {
        return vertexCount;
    }

    @Override
    public void drawBox(AABB box, int argb) {
        vertexCount += (mode == Mode.LINES) ? PathVertexLayout.LINE_VERTS_PER_BOX : PathVertexLayout.FACE_VERTS_PER_BOX;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode == Mode.LINES) vertexCount += PathVertexLayout.LINE_VERTS_PER_SEGMENT;
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (mode == Mode.FACES) vertexCount += PathVertexLayout.FACE_VERTS_PER_TRIANGLE;
    }
}
