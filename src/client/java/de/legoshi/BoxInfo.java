package de.legoshi;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BoxInfo {
    public double minX, minY, minZ, maxX, maxY, maxZ;
    private float r, g, b, a;

    public BoxInfo(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.r = this.g = this.b = 0.7f;
        this.a = 1f;
    }

    public Box getAABB() {
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void setMin(Vec3d newMin) {
        double dx = maxX - minX;
        double dy = maxY - minY;
        double dz = maxZ - minZ;

        minX = newMin.x;
        minY = newMin.y;
        minZ = newMin.z;

        maxX = minX + dx;
        maxY = minY + dy;
        maxZ = minZ + dz;
    }

    public void render(MatrixStack ms, VertexConsumerProvider consumers) {
        DebugRenderer.drawBox(ms, consumers, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }
}
