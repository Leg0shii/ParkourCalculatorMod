package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.anglesolver.solver.SupportOverlap;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.List;

public final class ConstraintDeriver {

    public static final double HALF = (double) (0.6f / 2.0f);
    public static final double BODY_HEIGHT = 1.8;
    private static final double EPS = 1.0e-7;
    private static final double SPAN_EPS = 1.0e-3;

    private ConstraintDeriver() {
    }

    public static boolean isSide(Face face) {
        return face == Face.NEG_X || face == Face.POS_X || face == Face.NEG_Z || face == Face.POS_Z;
    }

    public static Constraint.Field axisOf(Face face) {
        return (face == Face.NEG_X || face == Face.POS_X) ? Constraint.Field.X : Constraint.Field.Z;
    }

    public static int signOf(Face face) {
        return (face == Face.POS_X || face == Face.POS_Z) ? 1 : -1;
    }

    public static boolean wallIsLowerBound(Face face, boolean enter) {
        boolean dontEnterLower = signOf(face) > 0;
        return enter ? !dontEnterLower : dontEnterLower;
    }

    public static Constraint deriveWall(Face face, List<AABB> boxes, Vec3dCore hitVec, boolean enter) {
        if (!isSide(face)) return null;
        Constraint.Field field = axisOf(face);
        int sign = signOf(face);
        double f = faceFromBoxes(field, sign, boxes, hitVec);
        double value = f + sign * HALF;
        Constraint.Op dontEnter = sign > 0 ? Constraint.Op.GE : Constraint.Op.LE;
        Constraint.Op op = enter ? opposite(dontEnter) : dontEnter;
        return Constraint.scalar(field, op, value);
    }

    private static double faceFromBoxes(Constraint.Field field, int sign, List<AABB> boxes, Vec3dCore hitVec) {
        double fallback = (field == Constraint.Field.X) ? hitVec.x : hitVec.z;
        if (boxes == null || boxes.isEmpty()) return fallback;
        double nearest = Double.POSITIVE_INFINITY;
        for (AABB b : boxes) {
            nearest = Math.min(nearest, tangentialGap(field, b, hitVec));
        }
        boolean found = false;
        double best = 0;
        for (AABB b : boxes) {
            if (tangentialGap(field, b, hitVec) > nearest + SPAN_EPS) continue;
            double faceCoord;
            if (field == Constraint.Field.X) {
                faceCoord = sign > 0 ? b.max.x : b.min.x;
            } else {
                faceCoord = sign > 0 ? b.max.z : b.min.z;
            }
            if (!found) {
                best = faceCoord;
                found = true;
            } else {
                best = sign > 0 ? Math.max(best, faceCoord) : Math.min(best, faceCoord);
            }
        }
        return found ? best : fallback;
    }

    private static double tangentialGap(Constraint.Field field, AABB b, Vec3dCore hitVec) {
        double gapY = axisGap(hitVec.y, b.min.y, b.max.y);
        double gapT = (field == Constraint.Field.X)
                ? axisGap(hitVec.z, b.min.z, b.max.z)
                : axisGap(hitVec.x, b.min.x, b.max.x);
        return Math.max(gapY, gapT);
    }

    private static double axisGap(double v, double lo, double hi) {
        if (v < lo) return lo - v;
        if (v > hi) return v - hi;
        return 0.0;
    }

    public static Constraint.Op opposite(Constraint.Op op) {
        switch (op) {
            case GE:
                return Constraint.Op.LE;
            case LE:
                return Constraint.Op.GE;
            case GT:
                return Constraint.Op.LT;
            case LT:
                return Constraint.Op.GT;
            default:
                return op;
        }
    }

    public static AABB mergeCoplanarSupport(AABB seed, List<AABB> boxes) {
        double top = seed.max.y;
        double xLo = seed.min.x, xHi = seed.max.x;
        double zLo = seed.min.z, zHi = seed.max.z;
        double yLo = seed.min.y;
        double bodyWidth = 2.0 * HALF;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (AABB b : boxes) {
                if (Math.abs(b.max.y - top) > EPS) continue;
                boolean sameX = Math.abs(b.min.x - xLo) <= EPS && Math.abs(b.max.x - xHi) <= EPS;
                boolean sameZ = Math.abs(b.min.z - zLo) <= EPS && Math.abs(b.max.z - zHi) <= EPS;
                if (sameX && (b.min.z < zLo - EPS || b.max.z > zHi + EPS)
                        && b.min.z - zHi < bodyWidth && zLo - b.max.z < bodyWidth) {
                    zLo = Math.min(zLo, b.min.z);
                    zHi = Math.max(zHi, b.max.z);
                    yLo = Math.min(yLo, b.min.y);
                    grew = true;
                } else if (sameZ && (b.min.x < xLo - EPS || b.max.x > xHi + EPS)
                        && b.min.x - xHi < bodyWidth && xLo - b.max.x < bodyWidth) {
                    xLo = Math.min(xLo, b.min.x);
                    xHi = Math.max(xHi, b.max.x);
                    yLo = Math.min(yLo, b.min.y);
                    grew = true;
                }
            }
        }
        return new AABB(new Vec3dCore(xLo, yLo, zLo), new Vec3dCore(xHi, top, zHi));
    }

    public static double[] deriveFootprint(AABB support, double clickX, double clickZ, List<AABB> obstacles,
                                           boolean modernCollision) {
        return clipByObstacles(
                SupportOverlap.minCenter(modernCollision, support.min.x, support.max.x),
                SupportOverlap.maxCenter(modernCollision, support.min.x, support.max.x),
                SupportOverlap.minCenter(modernCollision, support.min.z, support.max.z),
                SupportOverlap.maxCenter(modernCollision, support.min.z, support.max.z),
                support.max.y, clickX, clickZ, obstacles);
    }

    public static double[] deriveCell(int bx, int bz, double footY, double refX, double refZ, List<AABB> obstacles) {
        return clipByObstacles(bx, bx + 1.0, bz, bz + 1.0, footY, refX, refZ, obstacles);
    }

    private static double[] clipByObstacles(double xLo, double xHi, double zLo, double zHi,
                                            double footY, double refX, double refZ, List<AABB> obstacles) {
        double bodyHi = footY + BODY_HEIGHT;
        if (obstacles != null) {
            for (AABB o : obstacles) {
                if (o == null) continue;
                if (o.max.y <= footY + EPS) continue;
                if (o.min.y >= bodyHi - EPS) continue;
                boolean spansZ = o.min.z <= refZ + EPS && o.max.z >= refZ - EPS;
                if (spansZ) {
                    if (o.max.x <= refX + EPS) xLo = Math.max(xLo, o.max.x + HALF);
                    if (o.min.x >= refX - EPS) xHi = Math.min(xHi, o.min.x - HALF);
                }
                boolean spansX = o.min.x <= refX + EPS && o.max.x >= refX - EPS;
                if (spansX) {
                    if (o.max.z <= refZ + EPS) zLo = Math.max(zLo, o.max.z + HALF);
                    if (o.min.z >= refZ - EPS) zHi = Math.min(zHi, o.min.z - HALF);
                }
            }
        }
        return new double[] {xLo, xHi, zLo, zHi};
    }
}
