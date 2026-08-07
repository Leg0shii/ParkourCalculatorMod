package de.legoshi.parkourcalc.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ByteSinkRenderer implements BoxRenderer {
    final ByteBuffer buf = ByteBuffer.allocateDirect(1 << 24).order(ByteOrder.nativeOrder());
    final Mode mode;
    final double ax;
    final double ay;
    final double az;
    long vertices;

    ByteSinkRenderer(Mode mode, double ax, double ay, double az) {
        this.mode = mode;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
    }

    void reset() {
        buf.clear();
        vertices = 0;
    }

    private void vertex(double x, double y, double z, int argb) {
        if (buf.remaining() < 16) buf.clear();
        buf.putFloat((float) (x - ax));
        buf.putFloat((float) (y - ay));
        buf.putFloat((float) (z - az));
        buf.putInt(argb);
        vertices++;
    }

    @Override
    public void drawBox(AABB b, int argb) {
        double x0 = b.min.x;
        double y0 = b.min.y;
        double z0 = b.min.z;
        double x1 = b.max.x;
        double y1 = b.max.y;
        double z1 = b.max.z;
        if (mode == Mode.LINES) {
            line(x0, y0, z0, x1, y0, z0, argb);
            line(x1, y0, z0, x1, y0, z1, argb);
            line(x1, y0, z1, x0, y0, z1, argb);
            line(x0, y0, z1, x0, y0, z0, argb);
            line(x0, y1, z0, x1, y1, z0, argb);
            line(x1, y1, z0, x1, y1, z1, argb);
            line(x1, y1, z1, x0, y1, z1, argb);
            line(x0, y1, z1, x0, y1, z0, argb);
            line(x0, y0, z0, x0, y1, z0, argb);
            line(x1, y0, z0, x1, y1, z0, argb);
            line(x1, y0, z1, x1, y1, z1, argb);
            line(x0, y0, z1, x0, y1, z1, argb);
        } else {
            tri(x0, y0, z0, x1, y0, z0, x1, y0, z1, argb);
            tri(x0, y0, z0, x1, y0, z1, x0, y0, z1, argb);
            tri(x0, y1, z0, x0, y1, z1, x1, y1, z1, argb);
            tri(x0, y1, z0, x1, y1, z1, x1, y1, z0, argb);
            tri(x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
            tri(x0, y0, z0, x1, y1, z0, x1, y0, z0, argb);
            tri(x0, y0, z1, x1, y0, z1, x1, y1, z1, argb);
            tri(x0, y0, z1, x1, y1, z1, x0, y1, z1, argb);
            tri(x0, y0, z0, x0, y0, z1, x0, y1, z1, argb);
            tri(x0, y0, z0, x0, y1, z1, x0, y1, z0, argb);
            tri(x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
            tri(x1, y0, z0, x1, y1, z1, x1, y0, z1, argb);
        }
    }

    private void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        vertex(x1, y1, z1, argb);
        vertex(x2, y2, z2, argb);
    }

    private void tri(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        vertex(x1, y1, z1, argb);
        vertex(x2, y2, z2, argb);
        vertex(x3, y3, z3, argb);
    }

    @Override
    public void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (mode != Mode.LINES) return;
        line(x1, y1, z1, x2, y2, z2, argb);
    }

    @Override
    public void drawTriangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (mode != Mode.FACES) return;
        tri(x1, y1, z1, x2, y2, z2, x3, y3, z3, argb);
    }
}
