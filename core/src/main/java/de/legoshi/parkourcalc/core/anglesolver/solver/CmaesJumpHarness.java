package de.legoshi.parkourcalc.core.anglesolver.solver;

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
import java.util.concurrent.atomic.AtomicBoolean;

/** One CMA-ES (commons-math3) run on the byte-exact model with a quadratic-penalty composition of the
 *  compiled constraints. Derivative-free and global, so it escapes the facing-clamp local optima a
 *  gradient method would rail into. The engine runs many of these from diverse starts in parallel and
 *  keeps the best feasible. Returns a {@link SolverRunResult}. */
public final class CmaesJumpHarness {

    // Search a WIDER range than one turn: the optimal facing sequence can straddle the +/-180 wrap
    // (e.g. -176 then +178, physically 6deg apart but a full turn apart in coordinates). Bounding to
    // one turn puts a wall through the basin and rails facings to the clamp; +/-2 turns lets the
    // periodic space stay continuous so the global basin is one smooth region. sin/cos are periodic,
    // so any value is a valid facing; results are wrapped to (-180,180] for display/apply.
    private static final double YAW_LOWER_DEG = -360.0;
    private static final double YAW_UPPER_DEG = 360.0;

    public static final class Config {
        public double muIneq = 1.0e7;
        public double muEq = 1.0e7;
        public int lambda;
        public double polishStepDeg = 45.0;
        public int polishIters = 500;
        public double polishFloorDeg = 1.0e-7;
    }

    private final Config cfg;
    private final double sigmaDeg;
    private final int maxEval;
    private final boolean feasibilityOnly;

    public CmaesJumpHarness(double muIneq, double muEq, double sigmaDeg, int maxEval) {
        this(muIneq, muEq, sigmaDeg, maxEval, false);
    }

    /** {@code feasibilityOnly} zeroes the objective term so the search optimizes pure constraint
     *  satisfaction, independent of the Solve-For direction. Used as a fallback when the objective-weighted
     *  pass finds no feasible basin: the penalized fitness blends the objective with the constraint penalty,
     *  so for some objectives CMA-ES settles a hair infeasible (and the strict-feasible polish then bails)
     *  while a different objective on the SAME problem lands feasible. Whether a solution EXISTS must not
     *  depend on what we optimize, so this mode recovers it. */
    public CmaesJumpHarness(double muIneq, double muEq, double sigmaDeg, int maxEval, boolean feasibilityOnly) {
        this(configFor(muIneq, muEq), sigmaDeg, maxEval, feasibilityOnly);
    }

    public CmaesJumpHarness(Config cfg, double sigmaDeg, int maxEval, boolean feasibilityOnly) {
        this.cfg = cfg;
        this.sigmaDeg = sigmaDeg;
        this.maxEval = maxEval;
        this.feasibilityOnly = feasibilityOnly;
    }

    private static Config configFor(double muIneq, double muEq) {
        Config cfg = new Config();
        cfg.muIneq = muIneq;
        cfg.muEq = muEq;
        return cfg;
    }

    public SolverRunResult solve(ForwardModel model, JumpSpec spec, double[] initialFAbsDeg, AtomicBoolean cancel) {
        return solve(model, spec, initialFAbsDeg, cancel, null);
    }

    public SolverRunResult solve(ForwardModel model, JumpSpec spec, double[] initialFAbsDeg, AtomicBoolean cancel, AtomicBoolean earlyStop) {
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        JumpPhysicsInputs scenario = spec.asScenario();
        StartBox box = scenario.startBox;
        boolean free = box != null && box.startFree();
        int n = initialFAbsDeg.length;
        double sign = feasibilityOnly ? 0.0 : (spec.objective.sense == Objective.Sense.MAX ? -1.0 : 1.0);
        Objective obj = spec.objective;
        int objAxis = obj.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;

        // Score the facings exactly as the game runs them: wrap to (-180,180] (the search box spans more
        // than one turn), then reconstruct the float32 accumulation Apply+sim produce. A raw or unwrapped
        // facing snaps to a different sine-table bucket than the one that actually lands, so without this the
        // objective, the penalty, and the applied path are three slightly different trajectories.
        MultivariateFunction penalized = F -> {
            if (stopped(cancel, earlyStop)) throw new SolveCancelledException();
            double[] gf = scenario.toGameFacings(Angles.wrapAll(F));
            ForwardPath pr = model.forward(scenario, gf);
            double smooth = feasibilityOnly ? 0.0 : obj.smoothPenalty(F);
            if (free) {
                double[] d = FreeStartSolve.bestTranslate(spec, gf, pr, box);
                double o = sign * (pr.getPos(obj.tick, obj.axis) + (objAxis == 0 ? d[0] : d[1]));
                return o + smooth + c.translatedPenalty(gf, pr, d[0], d[1], cfg.muIneq, cfg.muEq);
            }
            double o = sign * pr.getPos(obj.tick, obj.axis);
            double pen = c.penalty(gf, pr, cfg.muIneq, cfg.muEq);
            return o + smooth + pen;
        };

        double[] lower = new double[n];
        double[] upper = new double[n];
        double[] sigma = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = YAW_LOWER_DEG;
            upper[i] = YAW_UPPER_DEG;
            sigma[i] = sigmaDeg;
        }
        int lambda = cfg.lambda > 0 ? cfg.lambda : 2 * (4 + (int) Math.floor(3.0 * Math.log(n)));
        double[] start = clamp(initialFAbsDeg.clone());

        double[] fStar = start;
        try {
            // stopFitness MUST be -inf, not 0: the objective is sign*pos (~ -2102 at large world coords),
            // so a 0 threshold would stop on the first generation. Deterministic RNG seeded off the start.
            CMAESOptimizer opt = new CMAESOptimizer(
                    1000, Double.NEGATIVE_INFINITY, true, 0, 0,
                    new MersenneTwister(0x5DEECE66DL ^ Arrays.hashCode(start)), false,
                    new SimpleValueChecker(1.0e-12, 1.0e-12)
            );
            PointValuePair pv = opt.optimize(
                    new MaxEval(maxEval),
                    new ObjectiveFunction(penalized),
                    GoalType.MINIMIZE,
                    new SimpleBounds(lower, upper),
                    new InitialGuess(start),
                    new CMAESOptimizer.PopulationSize(lambda),
                    new CMAESOptimizer.Sigma(sigma)
            );
            fStar = pv.getPoint();
        } catch (TooManyEvaluationsException ignored) {
            // Used the eval budget without converging; keep the start. Other restarts cover it.
        }

        if (!free) fStar = polish(model, scenario, c, obj, sign, Angles.wrapAll(fStar), cancel, earlyStop);

        // Score the game's float-accumulated facings; return the absolute wrapped facings (yawAbsDeg) so
        // Apply can convert them to the deltas the game accumulates back into exactly this trajectory.
        double[] fStarW = Angles.wrapAll(fStar);
        double[] gf = scenario.toGameFacings(fStarW);
        ForwardPath finalPath = model.forward(scenario, gf);
        double dxFinal = 0.0;
        double dzFinal = 0.0;
        if (free) {
            double[] d = FreeStartSolve.bestTranslate(spec, gf, finalPath, box);
            dxFinal = d[0];
            dzFinal = d[1];
        }
        double objectiveValue = finalPath.getPos(obj.tick, obj.axis) + (objAxis == 0 ? dxFinal : dzFinal);
        double[] ineqSlack = new double[c.ineq.size()];
        for (int i = 0; i < c.ineq.size(); i++) {
            ineqSlack[i] = free ? JumpConstraintCompiler.translatedSlack(c.ineq.get(i), gf, finalPath, dxFinal, dzFinal)
                    : JumpConstraintCompiler.slack(c.ineq.get(i), gf, finalPath);
        }
        double[] eqResidual = new double[c.eq.size()];
        for (int i = 0; i < c.eq.size(); i++) {
            eqResidual[i] = free ? JumpConstraintCompiler.translatedEvaluate(c.eq.get(i), gf, finalPath, dxFinal, dzFinal)
                    : JumpConstraintCompiler.evaluate(c.eq.get(i), gf, finalPath);
        }
        return new SolverRunResult(fStarW, objectiveValue, ineqSlack, eqResidual);
    }

    private static boolean stopped(AtomicBoolean cancel, AtomicBoolean earlyStop) {
        return (cancel != null && cancel.get()) || (earlyStop != null && earlyStop.get());
    }

    private static double[] clamp(double[] f) {
        for (int i = 0; i < f.length; i++) {
            if (f[i] < YAW_LOWER_DEG + 1.0e-6) f[i] = YAW_LOWER_DEG + 1.0e-6;
            if (f[i] > YAW_UPPER_DEG - 1.0e-6) f[i] = YAW_UPPER_DEG - 1.0e-6;
        }
        return f;
    }


    /** Compass search from a strictly-feasible facing vector: greedily climb the objective while keeping
     *  every wall strictly satisfied (no clip), shrinking the step to a fine resolution. The global pass
     *  optimizes a penalty blend and stops a hair short inside the feasible region; this finishes the job.
     *  It never accepts a candidate that crosses a wall, so it can only improve the result, never invalidate it. */
    private double[] polish(ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, double[] startAbs, AtomicBoolean cancel, AtomicBoolean earlyStop) {
        double[] cur = startAbs.clone();
        double[] sv = scoreViol(model, scenario, c, obj, sign, cur);
        if (sv[1] > 0.0) return cur; // start not strictly feasible: leave it (another restart may be)
        double curScore = sv[0];
        int n = cur.length;
        double step = cfg.polishStepDeg;
        for (int it = 0; it < cfg.polishIters && step > cfg.polishFloorDeg; it++) {
            if (stopped(cancel, earlyStop)) throw new SolveCancelledException();
            boolean improved = false;
            for (int i = 0; i < n; i++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    double[] cand = cur.clone();
                    cand[i] += dir * step;
                    double[] cv = scoreViol(model, scenario, c, obj, sign, cand);
                    if (cv[1] <= 0.0 && cv[0] < curScore) {
                        cur = cand;
                        curScore = cv[0];
                        improved = true;
                    }
                }
            }
            if (!improved) step *= 0.5;
        }
        return cur;
    }

    /** {sign*objective + eq-penalty, max inequality slack} for an absolute facing vector, via the same
     *  wrap + game-facing + byte-exact forward the search uses. Second value &lt;= 0 means no wall is crossed. */
    private double[] scoreViol(ForwardModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled c, Objective obj, double sign, double[] abs) {
        double[] gf = scenario.toGameFacings(Angles.wrapAll(abs));
        ForwardPath pr = model.forward(scenario, gf);
        // score folds ONLY the eq squared-penalty (muIneq=0 zeroes the ineq term); ineq is reported separately.
        double smooth = feasibilityOnly ? 0.0 : obj.smoothPenalty(abs);
        double score = sign * pr.getPos(obj.tick, obj.axis) + smooth + c.penalty(gf, pr, 0.0, cfg.muEq);
        double ineqViol = 0.0;
        for (JumpConstraint cc : c.ineq) ineqViol = Math.max(ineqViol, JumpConstraintCompiler.slack(cc, gf, pr));
        return new double[]{score, ineqViol};
    }
}
