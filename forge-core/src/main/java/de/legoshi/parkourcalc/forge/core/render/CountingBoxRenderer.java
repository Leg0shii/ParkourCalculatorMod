package de.legoshi.parkourcalc.forge.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;

/** Tallies the vertices a pass would emit, so the Forge buffers can be pre-sized (they don't grow during building). */
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
        vertexCount += (mode == Mode.LINES) ? 24 : 36;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode == Mode.LINES) vertexCount += 2;
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (mode == Mode.FACES) vertexCount += 3;
    }
}
