package de.legoshi.parkourcalc.core.sim;

/**
 * Core's 3D position type. Named Vec3dCore (suffix indicates origin) to avoid the
 * import clash with net.minecraft.util.math.Vec3d in loader code while still reading
 * as "a Vec3d-shaped thing".
 */
public final class Vec3dCore {

    public static final Vec3dCore ZERO = new Vec3dCore(0.0, 0.0, 0.0);

    public static final Vec3dCore GROUND_REST_VELOCITY = new Vec3dCore(0.0, -(0.08 * 0.98F), 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3dCore(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3dCore add(double dx, double dy, double dz) {
        return new Vec3dCore(x + dx, y + dy, z + dz);
    }

    public Vec3dCore add(Vec3dCore o) {
        return new Vec3dCore(x + o.x, y + o.y, z + o.z);
    }

    public Vec3dCore sub(Vec3dCore o) {
        return new Vec3dCore(x - o.x, y - o.y, z - o.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vec3dCore)) return false;
        Vec3dCore other = (Vec3dCore) o;
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = 1L;
        bits = 31 * bits + Double.doubleToLongBits(x);
        bits = 31 * bits + Double.doubleToLongBits(y);
        bits = 31 * bits + Double.doubleToLongBits(z);
        return Long.hashCode(bits);
    }

    @Override
    public String toString() {
        return "Vec3dCore(" + x + ", " + y + ", " + z + ")";
    }
}
