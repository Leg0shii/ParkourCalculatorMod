package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.List;

/** Geometry + constraint-vocabulary helpers shared by every DERIVE candidate. The constraint at tick
 *  index {@code t} pins {@code path[t]} (index 0 = seed, index N = landing), matching
 *  {@code JumpConstraintCompiler}. Keep-out / footprint regions are the block AABB expanded by the
 *  player half-width (Minkowski sum), so a center-point constraint == a hitbox constraint. */
public final class DeriveSupport {

    public static final double HALF = 0.3;

    private DeriveSupport() {
    }

    /** [xlo, xhi, zlo, zhi]: the block's horizontal AABB grown by half on every side. */
    public static double[] expand(AABB box, double half) {
        return new double[]{box.min.x - half, box.max.x + half, box.min.z - half, box.max.z + half};
    }

    public static double[] expand(AABB box) {
        return expand(box, HALF);
    }

    public static double mid(double lo, double hi) {
        return (lo + hi) * 0.5;
    }

    // ---- single faces (half-spaces) -----------------------------------------

    public static JumpConstraint geX(int t, double v, String name) {
        return new JumpConstraint(JumpConstraint.Mode.X, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, v, name);
    }

    public static JumpConstraint leX(int t, double v, String name) {
        return new JumpConstraint(JumpConstraint.Mode.X, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, v, name);
    }

    public static JumpConstraint geZ(int t, double v, String name) {
        return new JumpConstraint(JumpConstraint.Mode.Z, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, v, name);
    }

    public static JumpConstraint leZ(int t, double v, String name) {
        return new JumpConstraint(JumpConstraint.Mode.Z, t, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, v, name);
    }

    // ---- footprints ---------------------------------------------------------

    /** Box-membership (XZ) constraints pinning {@code path[t]} inside the expanded footprint of {@code box}. */
    public static List<JumpConstraint> footprintAt(int t, AABB box, double half) {
        double[] e = expand(box, half);
        List<JumpConstraint> out = new ArrayList<>(4);
        out.add(geX(t, e[0], "fpXlo@" + t));
        out.add(leX(t, e[1], "fpXhi@" + t));
        out.add(geZ(t, e[2], "fpZlo@" + t));
        out.add(leZ(t, e[3], "fpZhi@" + t));
        return out;
    }

    /** Landing footprint at the final tick (index N). */
    public static List<JumpConstraint> landFootprint(int numTicks, AABB land, double half) {
        return footprintAt(numTicks, land, half);
    }

    // ---- objective convenience ---------------------------------------------

    public static Objective maxX(int t) {
        return new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, t);
    }

    public static Objective minX(int t) {
        return new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, t);
    }

    public static Objective maxZ(int t) {
        return new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, t);
    }

    public static Objective minZ(int t) {
        return new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, t);
    }

    /** The four single-axis endpoint objectives at the landing tick, in a sensible default order
     *  (perpendicular-to-travel first, since it does not fight the detour). */
    public static List<Objective> endpointObjectives(JumpPhysicsInputs sc, AABB land, int numTicks) {
        double sx = sc.startPos.x, sz = sc.startPos.z;
        double lx = mid(land.min.x, land.max.x), lz = mid(land.min.z, land.max.z);
        boolean travelX = Math.abs(lx - sx) >= Math.abs(lz - sz);
        List<Objective> objs = new ArrayList<>(4);
        if (travelX) {
            objs.add(maxZ(numTicks));
            objs.add(minZ(numTicks));
            objs.add(maxX(numTicks));
            objs.add(minX(numTicks));
        } else {
            objs.add(maxX(numTicks));
            objs.add(minX(numTicks));
            objs.add(maxZ(numTicks));
            objs.add(minZ(numTicks));
        }
        return objs;
    }
}
