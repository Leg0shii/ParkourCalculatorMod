package de.legoshi.parkourcalc.core.sim;

/**
 * Axis-aligned bounding box defined by two Vec3dCore corners. Pure value type;
 * matches the shape of net.minecraft.util.math.Box / similar in loader code while
 * keeping core MC-free.
 */
public final class AABB {

    public final Vec3dCore min;
    public final Vec3dCore max;

    public AABB(Vec3dCore min, Vec3dCore max) {
        this.min = min;
        this.max = max;
    }

    /** Cube centered on {@code center} in X/Z, with Y anchored at {@code center.y} (bottom face). */
    public static AABB ofCenteredXZ(Vec3dCore center, double size) {
        double half = size * 0.5;
        return new AABB(
                new Vec3dCore(center.x - half, center.y, center.z - half),
                new Vec3dCore(center.x + half, center.y + size, center.z + half)
        );
    }
}
