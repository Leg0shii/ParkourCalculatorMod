package de.legoshi.parkourcalc.core.sim;

import java.util.Collections;
import java.util.List;

public final class CollisionRayTrace {

    public interface World {
        List<AABB> collisionBoxes(int x, int y, int z);

        boolean pickWithoutCollision(int x, int y, int z);
    }

    private CollisionRayTrace() {
    }

    public static BlockHit trace(World world, Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        double length = Math.sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z);
        if (!(length > 0.0) || !(maxDistance > 0.0)) return null;
        Vec3dCore d = new Vec3dCore(direction.x / length, direction.y / length, direction.z / length);

        int cx = floor(origin.x);
        int cy = floor(origin.y);
        int cz = floor(origin.z);
        int stepX = step(d.x);
        int stepY = step(d.y);
        int stepZ = step(d.z);
        double nextX = boundaryDistance(origin.x, d.x, cx);
        double nextY = boundaryDistance(origin.y, d.y, cy);
        double nextZ = boundaryDistance(origin.z, d.z, cz);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.x);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.y);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(d.z);

        double travelled = 0.0;
        while (travelled <= maxDistance) {
            BlockHit hit = hitCell(world, cx, cy, cz, origin, d, maxDistance);
            if (hit != null) return hit;
            if (nextX < nextY && nextX < nextZ) {
                cx += stepX;
                travelled = nextX;
                nextX += deltaX;
            } else if (nextY < nextZ) {
                cy += stepY;
                travelled = nextY;
                nextY += deltaY;
            } else {
                cz += stepZ;
                travelled = nextZ;
                nextZ += deltaZ;
            }
        }
        return null;
    }

    private static BlockHit hitCell(World world, int x, int y, int z, Vec3dCore o, Vec3dCore d, double maxDistance) {
        List<AABB> boxes = world.collisionBoxes(x, y, z);
        if (boxes == null || boxes.isEmpty()) {
            if (!world.pickWithoutCollision(x, y, z)) return null;
            boxes = Collections.singletonList(new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0)));
        }
        double bestT = maxDistance;
        Face bestFace = null;
        for (AABB box : boxes) {
            double tNear = Double.NEGATIVE_INFINITY;
            double tFar = Double.POSITIVE_INFINITY;
            Face entering = null;
            boolean miss = false;
            for (int axis = 0; axis < 3 && !miss; axis++) {
                double oa = component(o, axis);
                double da = component(d, axis);
                double lo = component(box.min, axis);
                double hi = component(box.max, axis);
                if (da == 0.0) {
                    if (oa < lo || oa > hi) miss = true;
                    continue;
                }
                double t1 = (lo - oa) / da;
                double t2 = (hi - oa) / da;
                double tEnter = Math.min(t1, t2);
                double tExit = Math.max(t1, t2);
                if (tEnter > tNear) {
                    tNear = tEnter;
                    entering = da > 0.0 ? negativeFace(axis) : positiveFace(axis);
                }
                tFar = Math.min(tFar, tExit);
                if (tNear > tFar) miss = true;
            }
            if (miss || entering == null || tNear < 0.0 || tNear >= bestT) continue;
            bestT = tNear;
            bestFace = entering;
        }
        if (bestFace == null) return null;
        Vec3dCore hitVec = new Vec3dCore(o.x + d.x * bestT, o.y + d.y * bestT, o.z + d.z * bestT);
        return new BlockHit(x, y, z, bestFace, hitVec);
    }

    private static double component(Vec3dCore v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static Face negativeFace(int axis) {
        return axis == 0 ? Face.NEG_X : axis == 1 ? Face.NEG_Y : Face.NEG_Z;
    }

    private static Face positiveFace(int axis) {
        return axis == 0 ? Face.POS_X : axis == 1 ? Face.POS_Y : Face.POS_Z;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static int step(double d) {
        return d > 0.0 ? 1 : d < 0.0 ? -1 : 0;
    }

    private static double boundaryDistance(double origin, double d, int cell) {
        if (d > 0.0) return (cell + 1.0 - origin) / d;
        if (d < 0.0) return (origin - cell) / -d;
        return Double.POSITIVE_INFINITY;
    }
}
