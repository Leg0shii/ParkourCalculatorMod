package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.render.ArgbColor;
import de.legoshi.parkourcalc.core.sim.AABB;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public final class FabricBoxRenderer implements BoxRenderer {

    private final Matrix4f pose;
    private final VertexConsumer consumer;
    private final Mode mode;

    public FabricBoxRenderer(Matrix4f pose, VertexConsumer consumer, Mode mode) {
        this.pose = pose;
        this.consumer = consumer;
        this.mode = mode;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode != Mode.LINES) return;
        edge(consumer, pose,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2, argb
        );
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            emitEdges(consumer, pose, box, argb);
        } else {
            emitFaces(consumer, pose, box, argb);
        }
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (mode != Mode.FACES) return;
        quad(consumer, pose,
                (float) x1, (float) y1, (float) z1,
                (float) x2, (float) y2, (float) z2,
                (float) x3, (float) y3, (float) z3,
                (float) x3, (float) y3, (float) z3, argb
        );
    }

    private static void emitFaces(VertexConsumer c, Matrix4f m, AABB b, int argb) {
        float x0 = (float) b.min.x, y0 = (float) b.min.y, z0 = (float) b.min.z;
        float x1 = (float) b.max.x, y1 = (float) b.max.y, z1 = (float) b.max.z;

        quad(c, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb);
        quad(c, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, argb);
        quad(c, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, argb);
        quad(c, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb);
        quad(c, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb);
        quad(c, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, argb);
    }

    private static void quad(VertexConsumer c, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
        float r = ArgbColor.red(argb), g = ArgbColor.green(argb), b = ArgbColor.blue(argb), a = ArgbColor.alpha(argb);
        c.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        c.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        c.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        c.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
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

    private static void edge(VertexConsumer c, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, int argb) {
        float r = ArgbColor.red(argb), g = ArgbColor.green(argb), b = ArgbColor.blue(argb), a = ArgbColor.alpha(argb);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        c.addVertex(m, ax, ay, az).setColor(r, g, b, a).setNormal(nx, ny, nz);
        c.addVertex(m, bx, by, bz).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }
}
