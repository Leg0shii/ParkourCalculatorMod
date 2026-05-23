package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * BoxRenderer port impl for Fabric 1.21.10. Two modes mirror the Forge loaders:
 *
 *   - LINES: emit 12 GL_LINES edges into FabricRenderLayers.THIN_LINES (a custom
 *     layer that uses 1px lines without view_offset_z, unlike MC's RenderLayer
 *     .getLines() which expands to window_scale-wide quads that cover the box).
 *   - FACES: VertexRendering.drawFilledBox into FabricRenderLayers.TRANSLUCENT_BOX
 *     (custom layer with BlendFunction.TRANSLUCENT + translucent=true so it
 *     renders after the wireframe pass).
 *
 * FabricWorldOverlayRenderer calls render() twice (faces then lines), and the
 * VertexConsumerProvider.Immediate batches both layers, drawing on consumers.draw()
 * in the correct opaque→translucent order.
 */
public final class FabricBoxRenderer implements BoxRenderer {

    private final MatrixStack matrices;
    private final VertexConsumerProvider consumers;
    private final Mode mode;

    public FabricBoxRenderer(MatrixStack matrices, VertexConsumerProvider consumers, Mode mode) {
        this.matrices = matrices;
        this.consumers = consumers;
        this.mode = mode;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode != Mode.LINES) return;
        VertexConsumer consumer = consumers.getBuffer(FabricRenderLayers.THIN_LINES);
        edge(consumer, matrices.peek().getPositionMatrix(),
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2, argb);
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            VertexConsumer consumer = consumers.getBuffer(FabricRenderLayers.THIN_LINES);
            emitEdges(consumer, matrices.peek().getPositionMatrix(), box, argb);
        } else {
            VertexConsumer consumer = consumers.getBuffer(FabricRenderLayers.TRANSLUCENT_BOX);
            emitFaces(consumer, matrices.peek().getPositionMatrix(), box, argb);
        }
    }

    private static void emitFaces(VertexConsumer c, Matrix4f m, AABB b, int argb) {
        float x0 = (float) b.min.x, y0 = (float) b.min.y, z0 = (float) b.min.z;
        float x1 = (float) b.max.x, y1 = (float) b.max.y, z1 = (float) b.max.z;

        // -Y
        tri(c, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, argb);
        tri(c, m, x0, y0, z0, x1, y0, z1, x0, y0, z1, argb);
        // +Y
        tri(c, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, argb);
        tri(c, m, x0, y1, z0, x1, y1, z1, x1, y1, z0, argb);
        // -Z
        tri(c, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
        tri(c, m, x0, y0, z0, x1, y1, z0, x1, y0, z0, argb);
        // +Z
        tri(c, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, argb);
        tri(c, m, x0, y0, z1, x1, y1, z1, x0, y1, z1, argb);
        // -X
        tri(c, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, argb);
        tri(c, m, x0, y0, z0, x0, y1, z1, x0, y1, z0, argb);
        // +X
        tri(c, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
        tri(c, m, x1, y0, z0, x1, y1, z1, x1, y0, z1, argb);
    }

    private static void tri(VertexConsumer c, Matrix4f m,
                            float ax, float ay, float az,
                            float bx, float by, float bz,
                            float cx, float cy, float cz, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float bb = (argb & 0xFF) / 255.0f;
        c.vertex(m, ax, ay, az).color(r, g, bb, a);
        c.vertex(m, bx, by, bz).color(r, g, bb, a);
        c.vertex(m, cx, cy, cz).color(r, g, bb, a);
    }

    private static void emitEdges(VertexConsumer c, Matrix4f m, AABB b, int argb) {
        float x0 = (float) b.min.x, y0 = (float) b.min.y, z0 = (float) b.min.z;
        float x1 = (float) b.max.x, y1 = (float) b.max.y, z1 = (float) b.max.z;

        edge(c, m, x0, y0, z0, x1, y0, z0, argb);
        edge(c, m, x1, y0, z0, x1, y0, z1, argb);
        edge(c, m, x1, y0, z1, x0, y0, z1, argb);
        edge(c, m, x0, y0, z1, x0, y0, z0, argb);

        edge(c, m, x0, y1, z0, x1, y1, z0, argb);
        edge(c, m, x1, y1, z0, x1, y1, z1, argb);
        edge(c, m, x1, y1, z1, x0, y1, z1, argb);
        edge(c, m, x0, y1, z1, x0, y1, z0, argb);

        edge(c, m, x0, y0, z0, x0, y1, z0, argb);
        edge(c, m, x1, y0, z0, x1, y1, z0, argb);
        edge(c, m, x1, y0, z1, x1, y1, z1, argb);
        edge(c, m, x0, y0, z1, x0, y1, z1, argb);
    }

    private static void edge(VertexConsumer c, Matrix4f m,
                             float ax, float ay, float az,
                             float bx, float by, float bz, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        c.vertex(m, ax, ay, az).color(r, g, b, a);
        c.vertex(m, bx, by, bz).color(r, g, b, a);
    }
}
