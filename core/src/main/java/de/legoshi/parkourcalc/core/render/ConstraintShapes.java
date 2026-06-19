package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConstraintShapes {

    public enum Sense {
        INCLUDE,
        EXCLUDE
    }

    public static final double H = AngleSolverState.HITBOX_HALF_WIDTH;

    private static final double EQ_SATISFY_EPS = 1.0e-4;

    private ConstraintShapes() {
    }

    public static AngleSolverState.Axis spatialAxis(Constraint c) {
        switch (c.getField()) {
            case X:
                return AngleSolverState.Axis.X;
            case Z:
                return AngleSolverState.Axis.Z;
            default:
                return null;
        }
    }

    public static boolean isDrawable(Constraint c) {
        return spatialAxis(c) != null;
    }

    public static Sense sense(Constraint.Op op) {
        switch (op) {
            case GT:
            case GE:
            case LT:
            case LE:
                return Sense.EXCLUDE;
            default:
                return Sense.INCLUDE;
        }
    }

    public static boolean satisfied(Constraint c, Vec3dCore foot) {
        AngleSolverState.Axis axis = spatialAxis(c);
        if (axis == null) return true;
        double v = (axis == AngleSolverState.Axis.X) ? foot.x : foot.z;
        if (c.isRange()) {
            double lo = Math.min(c.getLo(), c.getHi());
            double hi = Math.max(c.getLo(), c.getHi());
            boolean lower = c.isLoInclusive() ? v >= lo : v > lo;
            boolean upper = c.isHiInclusive() ? v <= hi : v < hi;
            return lower && upper;
        }
        double val = c.getValue();
        switch (c.getOp()) {
            case GT:
                return v > val;
            case GE:
                return v >= val;
            case LT:
                return v < val;
            case LE:
                return v <= val;
            case EQ:
                return Math.abs(v - val) <= EQ_SATISFY_EPS;
            default:
                return true;
        }
    }

    public static ConstraintPlate pad(Constraint xRange, Constraint zRange, Vec3dCore foot, ConstraintStyle style, int tick, int[] indices) {
        double expand = style.expandByHitbox ? H : 0.0;
        double[] xs = sortedBounds(xRange);
        double[] zs = sortedBounds(zRange);
        double[] y = yBounds(style.frontHeight, foot);
        AABB body = new AABB(
                new Vec3dCore(xs[0] - expand, y[0], zs[0] - expand),
                new Vec3dCore(xs[1] + expand, y[1], zs[1] + expand));
        boolean sat = satisfied(xRange, foot) && satisfied(zRange, foot);
        return new ConstraintPlate(sense(xRange.getOp()), sat, one(body), none(), tick, indices);
    }

    public static ConstraintPlate strip(Constraint range, Vec3dCore foot, ConstraintStyle style, int tick, int[] indices) {
        AngleSolverState.Axis axis = spatialAxis(range);
        double expand = style.expandByHitbox ? H : 0.0;
        double[] s = sortedBounds(range);
        return band(axis, s[0] - expand, s[1] + expand, satisfied(range, foot), sense(range.getOp()), foot, style, tick, indices);
    }

    public static ConstraintPlate plane(Constraint eq, Vec3dCore foot, ConstraintStyle style, int tick, int[] indices) {
        AngleSolverState.Axis axis = spatialAxis(eq);
        double v = eq.getValue();
        double half = style.expandByHitbox ? H : style.frontLength * 0.5;
        return band(axis, v - half, v + half, satisfied(eq, foot), sense(eq.getOp()), foot, style, tick, indices);
    }

    public static ConstraintPlate exclude(Constraint open, Vec3dCore foot, ConstraintStyle style, int tick, int[] indices) {
        AngleSolverState.Axis axis = spatialAxis(open);
        double v = open.getValue();
        boolean allowedPositive = open.getOp() == Constraint.Op.GT || open.getOp() == Constraint.Op.GE;
        double expand = style.expandByHitbox ? H : 0.0;
        double cross = crossCoord(axis, foot);
        int dir = allowedPositive ? 1 : -1;
        double bound = v - dir * expand;

        double frontFar = bound + dir * style.frontLength;
        double fw = style.frontWidth * 0.5;
        double[] yF = yBounds(style.frontHeight, foot);
        AABB front = rect(axis, Math.min(bound, frontFar), Math.max(bound, frontFar), cross - fw, cross + fw, yF);

        double backFar = frontFar + dir * style.backLength;
        double bw = style.backWidth * 0.5;
        double[] yB = yBounds(style.backHeight, foot);
        AABB back = rect(axis, Math.min(frontFar, backFar), Math.max(frontFar, backFar), cross - bw, cross + bw, yB);

        return new ConstraintPlate(sense(open.getOp()), satisfied(open, foot), one(front), one(back), tick, indices);
    }

    private static ConstraintPlate band(AngleSolverState.Axis axis, double cLo, double cHi, boolean sat, Sense sense, Vec3dCore foot, ConstraintStyle style, int tick, int[] indices) {
        double cross = crossCoord(axis, foot);
        double fw = style.frontWidth * 0.5;
        AABB front = rect(axis, cLo, cHi, cross - fw, cross + fw, yBounds(style.frontHeight, foot));
        AABB back = rect(axis, cLo, cHi, cross - style.backLength, cross + style.backLength, yBounds(style.backHeight, foot));
        return new ConstraintPlate(sense, sat, one(front), one(back), tick, indices);
    }

    private static double[] yBounds(double height, Vec3dCore foot) {
        return new double[]{foot.y, foot.y + height};
    }

    private static AABB rect(AngleSolverState.Axis axis, double aLo, double aHi, double crossLo, double crossHi, double[] y) {
        if (axis == AngleSolverState.Axis.X) {
            return new AABB(new Vec3dCore(aLo, y[0], crossLo), new Vec3dCore(aHi, y[1], crossHi));
        }
        return new AABB(new Vec3dCore(crossLo, y[0], aLo), new Vec3dCore(crossHi, y[1], aHi));
    }

    private static double crossCoord(AngleSolverState.Axis axis, Vec3dCore foot) {
        return (axis == AngleSolverState.Axis.X) ? foot.z : foot.x;
    }

    private static double[] sortedBounds(Constraint range) {
        return new double[]{Math.min(range.getLo(), range.getHi()), Math.max(range.getLo(), range.getHi())};
    }

    private static List<AABB> one(AABB box) {
        List<AABB> list = new ArrayList<>(1);
        list.add(box);
        return list;
    }

    private static List<AABB> none() {
        return Collections.emptyList();
    }
}
