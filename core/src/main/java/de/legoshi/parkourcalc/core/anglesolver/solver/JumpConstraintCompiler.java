package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compiles a JumpSpec's constraint list into evaluable inequality and equality lists.
 *
 *  Constraints are normalized so:
 *    ineq: every entry, when satisfied, has evaluate() >= 0 (slack = max(0, -evaluate)).
 *    eq:   every entry, when satisfied, has evaluate() == 0 (slack = |evaluate|). */
public final class JumpConstraintCompiler {

    public static final class Compiled {
        public final List<JumpConstraint> ineq;
        public final List<JumpConstraint> eq;

        public Compiled(List<JumpConstraint> ineq, List<JumpConstraint> eq) {
            this.ineq = Collections.unmodifiableList(ineq);
            this.eq = Collections.unmodifiableList(eq);
        }

        /** Max over ineq slack and |eq residual|; <= 0 (FEAS_TOL) means strictly feasible. Iterates ineq before eq. */
        public double maxViolation(double[] gameFacings, ForwardPath path) {
            double v = 0.0;
            for (JumpConstraint c : ineq) v = Math.max(v, slack(c, gameFacings, path));
            for (JumpConstraint c : eq) v = Math.max(v, Math.abs(evaluate(c, gameFacings, path)));
            return v;
        }

        /** Quadratic penalty: muIneq*slack^2 (slack>0 only) summed over ineq, plus muEq*residual^2 over eq. */
        public double penalty(double[] gameFacings, ForwardPath path, double muIneq, double muEq) {
            double pen = 0.0;
            for (JumpConstraint c : ineq) {
                double s = slack(c, gameFacings, path);
                if (s > 0) pen += muIneq * s * s;
            }
            for (JumpConstraint c : eq) {
                double e = evaluate(c, gameFacings, path);
                pen += muEq * e * e;
            }
            return pen;
        }
    }

    public static Compiled compile(JumpSpec spec) {
        List<JumpConstraint> ineq = new ArrayList<>();
        List<JumpConstraint> eq = new ArrayList<>();
        int n = spec.asScenario().numTicks;
        for (JumpConstraint c : spec.constraints) {
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
    public static double evaluate(JumpConstraint c, double[] F, ForwardPath path) {
        switch (c.mode) {
            case X:
                return path.posX[c.t1] + opSign(c.op) * (c.t2 != null ? path.posX[c.t2] : 0.0) - c.rhs;
            case Z:
                return path.posZ[c.t1] + opSign(c.op) * (c.t2 != null ? path.posZ[c.t2] : 0.0) - c.rhs;
            case F:
                return Angles.wrap(F[c.t1] + opSign(c.op) * (c.t2 != null ? F[c.t2] : 0.0) - c.rhs);
            default:
                throw new IllegalStateException("unknown mode: " + c.mode);
        }
    }

    /** Nonnegative slack: 0 means the constraint is satisfied; positive means violated by that amount. */
    public static double slack(JumpConstraint c, double[] F, ForwardPath path) {
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
            throw new IllegalArgumentException("constraint " + name + ": tick " + t + " out of range [0, " + numTicks + "]");
        }
    }

}
