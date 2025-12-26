package de.legoshi;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Represents a renderable axis-aligned bounding box.
 */
public class BoxInfo {

    private static final float DEFAULT_COLOR = 0.7f;
    private static final float DEFAULT_SIZE = 0.1f;

    private final Box aabb;

    public BoxInfo(Vec3d position) {
        this(position, DEFAULT_SIZE);
    }

    public BoxInfo(Vec3d position, double size) {
        this.aabb = new Box(
                position.x, position.y, position.z,
                position.x + size, position.y + size, position.z + size
        );
    }

    public Box getAABB() {
        return aabb;
    }

    public Vec3d getMinPosition() {
        return new Vec3d(aabb.minX, aabb.minY, aabb.minZ);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        DebugRenderer.drawBox(
                matrices, consumers,
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ,
                DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR, 1.0f
        );
    }
}