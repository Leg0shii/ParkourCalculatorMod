package de.legoshi.parkourcalc.core.anglesolver;

public final class FlowVector {

    public static final FlowVector NONE = new FlowVector(0.0, 0.0);

    public final double x;
    public final double z;

    public FlowVector(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public static FlowVector of(FlowDirection direction) {
        if (direction == null || direction == FlowDirection.NONE) return NONE;
        return new FlowVector(direction.dx, direction.dz);
    }

    public boolean pushes() {
        return x != 0.0 || z != 0.0;
    }

    public FlowDirection direction() {
        return FlowDirection.nearest(x, z);
    }

    public boolean isUnitOf(FlowDirection direction) {
        return x == direction.dx && z == direction.dz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowVector)) return false;
        FlowVector v = (FlowVector) o;
        return Double.compare(x, v.x) == 0 && Double.compare(z, v.z) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(x) + Double.hashCode(z);
    }

    @Override
    public String toString() {
        return "FlowVector(" + x + ", " + z + ")";
    }
}
