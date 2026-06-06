package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Mothball2-style scalar constraint on the solver decision vector F[]. (The spike0 name was
 *  {@code Constraint}; renamed here to avoid clashing with the UI's anglesolver.Constraint.)
 *  Modes:
 *    X / Z   evaluate against pos.x / pos.z at tick t1 (and optionally t2).
 *    F       evaluate against F[t1] (and optionally F[t2]); both in absolute yaw degrees.
 *    FC      facing-chain; t2 required; expanded by the compiler into a sequence of
 *            consecutive single-tick F constraints.
 *  lhs is { v(t1)  op  v(t2) } when t2 != null, else just v(t1).
 *  The constraint reads as: lhs  cmp  rhs.
 */
public final class JumpConstraint {

    public enum Mode { X, Z, F, FC }
    public enum Op   { PLUS, MINUS }
    public enum Cmp  { GE, EQ, LE }

    public final Mode mode;
    public final int t1;
    public final Integer t2;
    public final Op op;
    public final Cmp cmp;
    public final double rhs;
    public final String name;
    public final boolean active;

    public JumpConstraint(Mode mode, int t1, Integer t2, Op op, Cmp cmp,
                          double rhs, String name, boolean active) {
        this.mode = mode;
        this.t1 = t1;
        this.t2 = t2;
        this.op = op;
        this.cmp = cmp;
        this.rhs = rhs;
        this.name = name;
        this.active = active;
    }

    public static JumpConstraint xGe(int t, double rhs, String name) {
        return new JumpConstraint(Mode.X, t, null, Op.PLUS, Cmp.GE, rhs, name, true);
    }

    public static JumpConstraint xLe(int t, double rhs, String name) {
        return new JumpConstraint(Mode.X, t, null, Op.PLUS, Cmp.LE, rhs, name, true);
    }

    public static JumpConstraint zGe(int t, double rhs, String name) {
        return new JumpConstraint(Mode.Z, t, null, Op.PLUS, Cmp.GE, rhs, name, true);
    }

    public static JumpConstraint zLe(int t, double rhs, String name) {
        return new JumpConstraint(Mode.Z, t, null, Op.PLUS, Cmp.LE, rhs, name, true);
    }

    public static JumpConstraint fEq(int t, double rhsDeg, String name) {
        return new JumpConstraint(Mode.F, t, null, Op.PLUS, Cmp.EQ, rhsDeg, name, true);
    }
}
