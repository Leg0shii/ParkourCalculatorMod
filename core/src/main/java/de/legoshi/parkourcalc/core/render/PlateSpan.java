package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class PlateSpan {

    public interface RangeSink {
        void draw(int firstVertex, int vertexCount);
    }

    public final int firstVertex;
    public final int vertexCount;
    private final double topY;

    public PlateSpan(int firstVertex, int vertexCount, double topY) {
        this.firstVertex = firstVertex;
        this.vertexCount = vertexCount;
        this.topY = topY;
    }

    public boolean drawBeforePath(double camY) {
        return camY >= topY;
    }

    public static List<PlateSpan> drawSpans(List<PlateSpan> spans, int totalVertices) {
        if (totalVertices <= 0) return Collections.emptyList();
        int sum = 0;
        for (PlateSpan span : spans) sum += span.vertexCount;
        if (sum == totalVertices) return spans;
        return Collections.singletonList(new PlateSpan(0, totalVertices, Double.NEGATIVE_INFINITY));
    }

    public static void select(List<PlateSpan> spans, int totalVertices, double camY, boolean beforePath, RangeSink sink) {
        for (PlateSpan span : drawSpans(spans, totalVertices)) {
            if (span.drawBeforePath(camY) == beforePath) sink.draw(span.firstVertex, span.vertexCount);
        }
    }

    public Consumer<BoxRenderer> slice(Consumer<BoxRenderer> faceEmitter) {
        return target -> faceEmitter.accept(new SliceRenderer(target, firstVertex, firstVertex + vertexCount));
    }

    private static final class SliceRenderer implements BoxRenderer {
        private final BoxRenderer target;
        private final int from;
        private final int to;
        private int vertex;

        SliceRenderer(BoxRenderer target, int from, int to) {
            this.target = target;
            this.from = from;
            this.to = to;
        }

        @Override
        public void drawBox(AABB box, int argb) {
            if (vertex >= from && vertex < to) target.drawBox(box, argb);
            vertex += PathVertexLayout.FACE_VERTS_PER_BOX;
        }

        @Override
        public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        }

        @Override
        public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
            if (vertex >= from && vertex < to) target.drawTriangle(x1, y1, z1, x2, y2, z2, x3, y3, z3, argb);
            vertex += PathVertexLayout.FACE_VERTS_PER_TRIANGLE;
        }
    }
}
