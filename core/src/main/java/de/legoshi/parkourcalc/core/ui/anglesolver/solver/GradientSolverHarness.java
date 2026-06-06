package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import java.util.List;

/** Gradient-based constrained optimizer for SMOOTH models (augmented Lagrangian + a projected-
 *  gradient inner solver with Armijo backtracking). The M2 shadow has exact gradients, so this is
 *  the right tool for it at any dimension. All objective + constraint gradients come from one
 *  central-difference position Jacobian per step. Returns a HarnessResult. */
public final class GradientSolverHarness {

    private static final double YAW_LOWER_DEG = -180.0;
    private static final double YAW_UPPER_DEG = 180.0;
    private static final double STEP_DEG = 1.0e-5;
    private static final double ARMIJO_C = 1.0e-4;
    private static final double TARGET_STEP_DEG = 10.0;
    private static final double FEAS_TOL = 1.0e-10;

    private final int maxOuter;
    private final int maxInner;
    private final double mu0;
    private final double muGrowth;
    private final double muMax;

    public GradientSolverHarness() {
        this(30, 120, 10.0, 5.0, 1.0e9);
    }

    public GradientSolverHarness(int maxOuter, int maxInner, double mu0, double muGrowth, double muMax) {
        this.maxOuter = maxOuter;
        this.maxInner = maxInner;
        this.mu0 = mu0;
        this.muGrowth = muGrowth;
        this.muMax = muMax;
    }

    public HarnessResult solve(DifferentiableModel model, JumpSpec spec, double[] initialFAbsDeg) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        Spike0Scenario sc = spec.asScenario();
        Objective obj = spec.objective;
        double sign = obj.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        List<JumpConstraint> ineq = c.ineq;
        List<JumpConstraint> eq = c.eq;
        int mI = ineq.size();
        int mE = eq.size();
        double[] ineqSign = new double[mI];
        for (int i = 0; i < mI; i++) ineqSign[i] = ineq.get(i).cmp == JumpConstraint.Cmp.GE ? 1.0 : -1.0;

        int n = initialFAbsDeg.length;
        double[] F = initialFAbsDeg.clone();
        for (int j = 0; j < n; j++) F[j] = clamp(F[j]);
        double[] lam = new double[mI];
        double[] nu = new double[mE];
        double mu = mu0;
        double prevViol = Double.POSITIVE_INFINITY;
        long t0 = System.nanoTime();
        int totalIters = 0;

        for (int outer = 0; outer < maxOuter; outer++) {
            totalIters += inner(model, sc, obj, sign, ineq, eq, ineqSign, F, lam, nu, mu);
            PathResult pr = model.forward(sc, F);
            double viol = 0.0;
            for (int i = 0; i < mI; i++) {
                double ci = ineqSign[i] * JumpConstraintCompiler.evaluate(ineq.get(i), F, pr);
                viol = Math.max(viol, Math.max(0.0, -ci));
            }
            for (int j = 0; j < mE; j++) {
                viol = Math.max(viol, Math.abs(JumpConstraintCompiler.evaluate(eq.get(j), F, pr)));
            }
            for (int i = 0; i < mI; i++) {
                double ci = ineqSign[i] * JumpConstraintCompiler.evaluate(ineq.get(i), F, pr);
                lam[i] = Math.max(0.0, lam[i] - mu * ci);
            }
            for (int j = 0; j < mE; j++) {
                nu[j] = nu[j] - mu * JumpConstraintCompiler.evaluate(eq.get(j), F, pr);
            }
            if (viol < FEAS_TOL) break;
            if (viol > 0.25 * prevViol) mu = Math.min(mu * muGrowth, muMax);
            prevViol = viol;
        }
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        PathResult finalPath = model.forward(sc, F);
        double objectiveValue = finalPath.getPos(obj.tick, obj.axis);
        double[] ineqSlack = new double[mI];
        for (int i = 0; i < mI; i++) ineqSlack[i] = JumpConstraintCompiler.slack(ineq.get(i), F, finalPath);
        double[] eqResidual = new double[mE];
        for (int j = 0; j < mE; j++) eqResidual[j] = JumpConstraintCompiler.evaluate(eq.get(j), F, finalPath);
        return new HarnessResult(F, wallMs, totalIters, true, objectiveValue, ineqSlack, eqResidual);
    }

    private int inner(DifferentiableModel model, Spike0Scenario sc, Objective obj, double sign,
                      List<JumpConstraint> ineq, List<JumpConstraint> eq, double[] ineqSign,
                      double[] F, double[] lam, double[] nu, double mu) {
        int n = F.length;
        int iters = 0;
        for (int it = 0; it < maxInner; it++) {
            iters++;
            PathResult pr = model.forward(sc, F);
            double[][][] jac = jacobian(model, sc, F);
            double[] g = gradLA(F, pr, jac, obj, sign, ineq, eq, ineqSign, lam, nu, mu);
            double f0 = valueLA(pr, F, obj, sign, ineq, eq, ineqSign, lam, nu, mu);

            double gmax = 0.0;
            for (int j = 0; j < n; j++) gmax = Math.max(gmax, Math.abs(g[j]));
            if (gmax < 1.0e-12) break;

            // first trial moves the steepest component by ~TARGET_STEP_DEG; backtrack from there.
            double alpha = TARGET_STEP_DEG / gmax;
            boolean accepted = false;
            for (int ls = 0; ls < 50; ls++) {
                double[] fn = new double[n];
                double gdotstep = 0.0;
                for (int j = 0; j < n; j++) {
                    fn[j] = clamp(F[j] - alpha * g[j]);
                    gdotstep += g[j] * (F[j] - fn[j]);
                }
                PathResult prNew = model.forward(sc, fn);
                double fNew = valueLA(prNew, fn, obj, sign, ineq, eq, ineqSign, lam, nu, mu);
                if (fNew <= f0 - ARMIJO_C * gdotstep) {
                    System.arraycopy(fn, 0, F, 0, n);
                    accepted = true;
                    break;
                }
                alpha *= 0.5;
            }
            if (!accepted) break;
        }
        return iters;
    }

    private static double valueLA(PathResult pr, double[] F, Objective obj, double sign,
                                  List<JumpConstraint> ineq, List<JumpConstraint> eq, double[] ineqSign,
                                  double[] lam, double[] nu, double mu) {
        double v = sign * pr.getPos(obj.tick, obj.axis);
        for (int j = 0; j < eq.size(); j++) {
            double hj = JumpConstraintCompiler.evaluate(eq.get(j), F, pr);
            v += -nu[j] * hj + 0.5 * mu * hj * hj;
        }
        for (int i = 0; i < ineq.size(); i++) {
            double ci = ineqSign[i] * JumpConstraintCompiler.evaluate(ineq.get(i), F, pr);
            if (ci <= lam[i] / mu) v += -lam[i] * ci + 0.5 * mu * ci * ci;
            else v += -lam[i] * lam[i] / (2.0 * mu);
        }
        return v;
    }

    private static double[] gradLA(double[] F, PathResult pr, double[][][] jac, Objective obj, double sign,
                                   List<JumpConstraint> ineq, List<JumpConstraint> eq, double[] ineqSign,
                                   double[] lam, double[] nu, double mu) {
        int n = F.length;
        double[] g = new double[n];
        double[] gObj = posGrad(obj.tick, obj.axis, jac);
        for (int j = 0; j < n; j++) g[j] = sign * gObj[j];
        for (int j = 0; j < eq.size(); j++) {
            double hj = JumpConstraintCompiler.evaluate(eq.get(j), F, pr);
            double coef = -nu[j] + mu * hj;
            double[] gh = evalGrad(eq.get(j), jac);
            for (int k = 0; k < n; k++) g[k] += coef * gh[k];
        }
        for (int i = 0; i < ineq.size(); i++) {
            double ci = ineqSign[i] * JumpConstraintCompiler.evaluate(ineq.get(i), F, pr);
            double psip = (ci <= lam[i] / mu) ? (-lam[i] + mu * ci) : 0.0;
            if (psip != 0.0) {
                double[] gc = evalGrad(ineq.get(i), jac);
                for (int k = 0; k < n; k++) g[k] += psip * ineqSign[i] * gc[k];
            }
        }
        return g;
    }

    private static double[] posGrad(int tick, Spike0Scenario.Axis axis, double[][][] jac) {
        if (axis == Spike0Scenario.Axis.X) return jac[0][tick];
        if (axis == Spike0Scenario.Axis.Z) return jac[1][tick];
        return new double[jac[0][tick].length];
    }

    private static double[] evalGrad(JumpConstraint c, double[][][] jac) {
        double[][] d = c.mode == JumpConstraint.Mode.X ? jac[0] : jac[1];
        int n = jac[0][0].length;
        double[] g = new double[n];
        double opSign = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
        if (c.mode == JumpConstraint.Mode.F) {
            g[c.t1] += 1.0;
            if (c.t2 != null) g[c.t2] += opSign;
            return g;
        }
        for (int j = 0; j < n; j++) {
            g[j] = d[c.t1][j] + (c.t2 != null ? opSign * d[c.t2][j] : 0.0);
        }
        return g;
    }

    /** jac[0] = dX[t][j], jac[1] = dZ[t][j] (central difference, 2n forwards). */
    private static double[][][] jacobian(DifferentiableModel model, Spike0Scenario sc, double[] F) {
        int n = F.length;
        double[][] dX = new double[n + 1][n];
        double[][] dZ = new double[n + 1][n];
        double[] cpy = F.clone();
        for (int j = 0; j < n; j++) {
            double saved = cpy[j];
            cpy[j] = saved + STEP_DEG;
            PathResult up = model.forward(sc, cpy);
            cpy[j] = saved - STEP_DEG;
            PathResult dn = model.forward(sc, cpy);
            cpy[j] = saved;
            for (int t = 0; t <= n; t++) {
                dX[t][j] = (up.posX[t] - dn.posX[t]) / (2.0 * STEP_DEG);
                dZ[t][j] = (up.posZ[t] - dn.posZ[t]) / (2.0 * STEP_DEG);
            }
        }
        return new double[][][]{dX, dZ};
    }

    private static double clamp(double v) {
        if (v < YAW_LOWER_DEG) return YAW_LOWER_DEG;
        if (v > YAW_UPPER_DEG) return YAW_UPPER_DEG;
        return v;
    }
}
