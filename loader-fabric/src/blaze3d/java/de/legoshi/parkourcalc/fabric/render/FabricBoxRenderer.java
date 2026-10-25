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
    private final double originX;
    private final double originY;
    private final double originZ;

    public FabricBoxRenderer(Matrix4f pose, VertexConsumer consumer, Mode mode) {
        this(pose, consumer, mode, 0.0, 0.0, 0.0);
    }

    public FabricBoxRenderer(Matrix4f pose, VertexConsumer consumer, Mode mode, double originX, double originY, double originZ) {
        this.pose = pose;
        this.consumer = consumer;
        this.mode = mode;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode != Mode.LINES) return;
        edge(consumer, pose,
                (float) (x1 - originX), (float) (y1 - originY), (float) (z1 - originZ),
                (float) (x2 - originX), (float) (y2 - originY), (float) (z2 - originZ), argb
        );
    }

    @Override
    public void drawBox(AABB box, int argb) {
        if (mode == Mode.LINES) {
            emitEdges(consumer, pose, box, argb, originX, originY, originZ);
        } else {
            emitFaces(consumer, pose, box, argb, originX, originY, originZ);
        }
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (mode != Mode.FACES) return;
        float ax = (float) (x1 - originX), ay = (float) (y1 - originY), az = (float) (z1 - originZ);
        float bx = (float) (x2 - originX), by = (float) (y2 - originY), bz = (float) (z2 - originZ);
        float cx = (float) (x3 - originX), cy = (float) (y3 - originY), cz = (float) (z3 - originZ);
        quad(consumer, pose, ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz, argb);
    }

    private static void emitFaces(VertexConsumer c, Matrix4f m, AABB b, int argb, double ox, double oy, double oz) {
        float x0 = (float) (b.min.x - ox), y0 = (float) (b.min.y - oy), z0 = (float) (b.min.z - oz);
        float x1 = (float) (b.max.x - ox), y1 = (float) (b.max.y - oy), z1 = (float) (b.max.z - oz);

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
        vertex(c, m, ax, ay, az, r, g, b, a);
        vertex(c, m, bx, by, bz, r, g, b, a);
        vertex(c, m, cx, cy, cz, r, g, b, a);
        vertex(c, m, dx, dy, dz, r, g, b, a);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        //? if >=1.21 {
        if (m == null) {
            c.addVertex(x, y, z).setColor(r, g, b, a);
        } else {
            c.addVertex(m, x, y, z).setColor(r, g, b, a);
        }
        //?} else {
        /*if (m == null) {
            c.vertex(x, y, z).color(r, g, b, a).endVertex();
        } else {
            c.vertex(m, x, y, z).color(r, g, b, a).endVertex();
        }
        *///?}
    }

    private static void emitEdges(VertexConsumer c, Matrix4f m, AABB b, int argb, double ox, double oy, double oz) {
        float x0 = (float) (b.min.x - ox), y0 = (float) (b.min.y - oy), z0 = (float) (b.min.z - oz);
        float x1 = (float) (b.max.x - ox), y1 = (float) (b.max.y - oy), z1 = (float) (b.max.z - oz);

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
        lineVertex(c, m, ax, ay, az, r, g, b, a, nx, ny, nz);
        lineVertex(c, m, bx, by, bz, r, g, b, a, nx, ny, nz);
    }

    private static void lineVertex(VertexConsumer c, Matrix4f m, float x, float y, float z,
                                   float r, float g, float b, float a, float nx, float ny, float nz) {
        //? if >=1.21 {
        if (m == null) {
            c.addVertex(x, y, z).setColor(r, g, b, a).setNormal(nx, ny, nz);
        } else {
            c.addVertex(m, x, y, z).setColor(r, g, b, a).setNormal(nx, ny, nz);
        }
        //?} else {
        /*if (m == null) {
            c.vertex(x, y, z).color(r, g, b, a).normal(nx, ny, nz).endVertex();
        } else {
            c.vertex(m, x, y, z).color(r, g, b, a).normal(nx, ny, nz).endVertex();
        }
        *///?}
    }
}
