package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiles a JumpSpec's constraint list into evaluable inequality and equality lists.
 *  FC (facing-chain) constraints are expanded into consecutive single-tick F pairs.
 *
 *  Constraints are normalized so:
 *    ineq: every entry, when satisfied, has evaluate() >= 0 (slack = max(0, -evaluate)).
 *    eq:   every entry, when satisfied, has evaluate() == 0 (slack = |evaluate|). */
public final class JumpConstraintCompiler {

    private JumpConstraintCompiler() {}

    public static final class Compiled {
        public final List<JumpConstraint> ineq;
        public final List<JumpConstraint> eq;

        public Compiled(List<JumpConstraint> ineq, List<JumpConstraint> eq) {
            this.ineq = Collections.unmodifiableList(ineq);
            this.eq = Collections.unmodifiableList(eq);
        }
    }

    public static Compiled compile(JumpSpec spec) {
        List<JumpConstraint> ineq = new ArrayList<>();
        List<JumpConstraint> eq = new ArrayList<>();
        int n = spec.numTicks;
        for (JumpConstraint c : spec.constraints) {
            if (!c.active) continue;
            if (c.mode == JumpConstraint.Mode.FC) {
                expandFC(c, ineq, eq);
                continue;
            }
            validateTick(c.t1, n, c.name);
            if (c.t2 != null) validateTick(c.t2, n, c.name);
            if (c.cmp == JumpConstraint.Cmp.EQ) {
                eq.add(c);
            } else {
                ineq.add(c);
            }
        }
        return new Compiled(ineq, eq);
    }

    /** Signed lhs - rhs, in the units of the constraint's mode (m for X/Z, degrees for F). F residuals
     *  are wrapped to (-180,180]: facings are periodic, so a -182deg result satisfies a +178deg target,
     *  and a wider-than-one-turn search space does not fabricate a violation. */
    public static double evaluate(JumpConstraint c, double[] F, PathResult path) {
        switch (c.mode) {
            case X:
                return path.posX[c.t1] + opSign(c.op) * (c.t2 != null ? path.posX[c.t2] : 0.0) - c.rhs;
            case Z:
                return path.posZ[c.t1] + opSign(c.op) * (c.t2 != null ? path.posZ[c.t2] : 0.0) - c.rhs;
            case F:
                return wrapDeg(F[c.t1] + opSign(c.op) * (c.t2 != null ? F[c.t2] : 0.0) - c.rhs);
            default:
                throw new IllegalStateException("FC not expanded: " + c.name);
        }
    }

    private static double wrapDeg(double d) {
        d = d % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    /** Nonnegative slack: 0 means the constraint is satisfied; positive means violated by that amount. */
    public static double slack(JumpConstraint c, double[] F, PathResult path) {
        double e = evaluate(c, F, path);
        switch (c.cmp) {
            case GE: return e < 0 ? -e : 0.0;
            case LE: return e > 0 ? e : 0.0;
            case EQ: return Math.abs(e);
        }
        throw new IllegalStateException("unknown cmp: " + c.cmp);
    }

    private static double opSign(JumpConstraint.Op op) {
        return op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
    }

    private static void validateTick(int t, int numTicks, String name) {
        if (t < 0 || t > numTicks) {
            throw new IllegalArgumentException("constraint " + name + ": tick " + t
                    + " out of range [0, " + numTicks + "]");
        }
    }

    private static void expandFC(JumpConstraint c, List<JumpConstraint> ineq, List<JumpConstraint> eq) {
        if (c.t2 == null) {
            throw new IllegalArgumentException("FC constraint " + c.name + " requires t2");
        }
        int from = c.t1;
        int to = c.t2;
        if (from == to) {
            throw new IllegalArgumentException("FC constraint " + c.name + ": t1 == t2");
        }
        int step = from > to ? -1 : 1;
        int cur = from;
        while (cur != to) {
            int next = cur + step;
            JumpConstraint pair = new JumpConstraint(
                    JumpConstraint.Mode.F, cur, next, c.op, c.cmp, c.rhs,
                    c.name + "[" + cur + "," + next + "]", true);
            if (c.cmp == JumpConstraint.Cmp.EQ) eq.add(pair); else ineq.add(pair);
            cur = next;
        }
    }
}
