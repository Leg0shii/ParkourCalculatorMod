package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
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
        boolean found = false;
        double best = 0;
        for (AABB b : boxes) {
            if (!spansTangential(field, b, hitVec)) continue;
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

    private static boolean spansTangential(Constraint.Field field, AABB b, Vec3dCore hitVec) {
        boolean spansY = b.min.y <= hitVec.y + SPAN_EPS && b.max.y >= hitVec.y - SPAN_EPS;
        if (field == Constraint.Field.X) {
            return spansY && b.min.z <= hitVec.z + SPAN_EPS && b.max.z >= hitVec.z - SPAN_EPS;
        }
        return spansY && b.min.x <= hitVec.x + SPAN_EPS && b.max.x >= hitVec.x - SPAN_EPS;
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

    public static double[] deriveFootprint(AABB support, double clickX, double clickZ, List<AABB> obstacles) {
        return clipByObstacles(
                support.min.x - HALF, support.max.x + HALF, support.min.z - HALF, support.max.z + HALF,
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
