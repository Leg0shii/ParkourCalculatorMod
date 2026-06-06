package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.MersenneTwister;

import java.util.Arrays;

/** One CMA-ES (commons-math3) run on the smooth model with a quadratic-penalty composition of the
 *  compiled constraints. Derivative-free and global, so it escapes the facing-clamp local optima the
 *  gradient solver rails into. The engine runs many of these from diverse starts in parallel and keeps
 *  the best feasible. Returns a {@link HarnessResult} (same shape as the gradient harness). */
public final class CmaesJumpHarness {

    // Search a WIDER range than one turn: the optimal facing sequence can straddle the +/-180 wrap
    // (e.g. -176 then +178, physically 6deg apart but a full turn apart in coordinates). Bounding to
    // one turn puts a wall through the basin and rails facings to the clamp; +/-2 turns lets the
    // periodic space stay continuous so the global basin is one smooth region. sin/cos are periodic,
    // so any value is a valid facing; results are wrapped to (-180,180] for display/apply.
    private static final double YAW_LOWER_DEG = -360.0;
    private static final double YAW_UPPER_DEG = 360.0;

    private final double muIneq;
    private final double muEq;
    private final double sigmaDeg;
    private final int maxEval;

    public CmaesJumpHarness() {
        this(1.0e6, 1.0e6, 45.0, 8000);
    }

    public CmaesJumpHarness(double muIneq, double muEq, double sigmaDeg, int maxEval) {
        this.muIneq = muIneq;
        this.muEq = muEq;
        this.sigmaDeg = sigmaDeg;
        this.maxEval = maxEval;
    }

    public HarnessResult solve(DifferentiableModel model, JumpSpec spec, double[] initialFAbsDeg) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        Spike0Scenario scenario = spec.asScenario();
        int n = initialFAbsDeg.length;
        double sign = spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0;
        Objective obj = spec.objective;

        MultivariateFunction penalized = F -> {
            PathResult pr = model.forward(scenario, F);
            double o = sign * pr.getPos(obj.tick, obj.axis);
            double pen = 0.0;
            for (JumpConstraint cc : c.ineq) {
                double s = JumpConstraintCompiler.slack(cc, F, pr);
                if (s > 0) pen += muIneq * s * s;
            }
            for (JumpConstraint cc : c.eq) {
                double e = JumpConstraintCompiler.evaluate(cc, F, pr);
                pen += muEq * e * e;
            }
            return o + pen;
        };

        double[] lower = new double[n];
        double[] upper = new double[n];
        double[] sigma = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = YAW_LOWER_DEG;
            upper[i] = YAW_UPPER_DEG;
            sigma[i] = sigmaDeg;
        }
        int lambda = 2 * (4 + (int) Math.floor(3.0 * Math.log(n)));
        double[] start = clamp(initialFAbsDeg.clone());

        long t0 = System.nanoTime();
        double[] fStar = start;
        int iters = 0;
        try {
            // stopFitness MUST be -inf, not 0: the objective is sign*pos (~ -2102 at large world coords),
            // so a 0 threshold would stop on the first generation. Deterministic RNG seeded off the start.
            CMAESOptimizer opt = new CMAESOptimizer(1000, Double.NEGATIVE_INFINITY, true, 0, 0,
                    new MersenneTwister(0x5DEECE66DL ^ Arrays.hashCode(start)), false,
                    new SimpleValueChecker(1.0e-12, 1.0e-12));
            PointValuePair pv = opt.optimize(
                    new MaxEval(maxEval),
                    new ObjectiveFunction(penalized),
                    GoalType.MINIMIZE,
                    new SimpleBounds(lower, upper),
                    new InitialGuess(start),
                    new CMAESOptimizer.PopulationSize(lambda),
                    new CMAESOptimizer.Sigma(sigma));
            fStar = pv.getPoint();
            iters = opt.getEvaluations();
        } catch (TooManyEvaluationsException ignored) {
            // Used the eval budget without converging; keep the start. Other restarts cover it.
        }
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;

        PathResult finalPath = model.forward(scenario, fStar);
        double objectiveValue = finalPath.getPos(obj.tick, obj.axis);
        double[] ineqSlack = new double[c.ineq.size()];
        for (int i = 0; i < c.ineq.size(); i++) {
            ineqSlack[i] = JumpConstraintCompiler.slack(c.ineq.get(i), fStar, finalPath);
        }
        double[] eqResidual = new double[c.eq.size()];
        for (int i = 0; i < c.eq.size(); i++) {
            eqResidual[i] = JumpConstraintCompiler.evaluate(c.eq.get(i), fStar, finalPath);
        }
        return new HarnessResult(fStar, wallMs, iters, true, objectiveValue, ineqSlack, eqResidual);
    }

    private static double[] clamp(double[] f) {
        for (int i = 0; i < f.length; i++) {
            if (f[i] < YAW_LOWER_DEG + 1.0e-6) f[i] = YAW_LOWER_DEG + 1.0e-6;
            if (f[i] > YAW_UPPER_DEG - 1.0e-6) f[i] = YAW_UPPER_DEG - 1.0e-6;
        }
        return f;
    }
}
