package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Mothball2-style scalar constraint on the solver decision vector F[]. (Named JumpConstraint to
 *  avoid clashing with the UI's anglesolver.Constraint.)
 *  Modes:
 *    X / Z   evaluate against pos.x / pos.z at tick t1 (and optionally t2).
 *    F       evaluate against F[t1] (and optionally F[t2]); both in absolute yaw degrees.
 *  lhs is { v(t1)  op  v(t2) } when t2 != null, else just v(t1).
 *  The constraint reads as: lhs  cmp  rhs.
 */
public final class JumpConstraint {

    public enum Mode {
        X,
        Z,
        F
    }

    public enum Op {
        PLUS,
        MINUS
    }

    public enum Cmp {
        GE,
        EQ,
        LE
    }

    public final Mode mode;
    public final int t1;
    public final Integer t2;
    public final Op op;
    public final Cmp cmp;
    public final double rhs;
    public final String name;

    public JumpConstraint(Mode mode, int t1, Integer t2, Op op, Cmp cmp, double rhs, String name) {
        this.mode = mode;
        this.t1 = t1;
        this.t2 = t2;
        this.op = op;
        this.cmp = cmp;
        this.rhs = rhs;
        this.name = name;
    }
}
