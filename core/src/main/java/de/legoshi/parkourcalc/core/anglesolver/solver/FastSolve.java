package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fast solve that replaces the cold global multistart on the byte-exact model for the common case.
 *
 *  <p>Stage 1 finds a near-feasible wall-hugging facing sequence on the differentiable {@link
 *  SmoothJumpModel} with the analytic-Jacobian {@link SmoothGradientSolver} (a few smoothness weights x a
 *  few seeds; the smooth landscape is single-basin enough that no global search is needed). Stage 2 hands
 *  that as a warm start to the proven {@link SolveCore} multistart on the real {@link ExactJumpModel} with
 *  a drastically reduced budget and a small CMA-ES step, so it only settles the quantized facing lattice
 *  onto strict feasibility (and {@link BucketAscentPolish} optimizes the objective) rather than searching
 *  globally. The expensive global part -- finding the basin -- is done cheaply on the smooth model.
 *
 *  <p>Returns absolute wrapped facings strictly feasible on the exact model, or {@code null} when the fast
 *  path cannot certify feasibility -- the caller then falls back to the full multistart, so this can only
 *  make solving faster, never less reliable. */
public final class FastSolve {

    private FastSolve() {
    }

    public static boolean DEBUG = false;

    /** Smoothness weights tried in stage 1; the feasible-basin weight is problem-dependent. */
    private static final double[] SMOOTH_WEIGHTS = {0.0003, 0.0008, 0.0015};

    /** The smooth walls are hugged this far inside so the transfer biases to the feasible side. */
    private static final double SMOOTH_MARGIN = 1.0e-3;

    /** Warm-started exact closer: the smooth warm start is essentially on the answer, so a handful of
     *  restarts and a modest eval budget reach strict byte-exact feasibility -- versus the cold default's
     *  16x4500. The smooth basin search is the global part; this just settles the quantized lattice. */
    // One restart: with a small sigma only the warm-started restart is useful (cold restarts would explore
    // a tiny region around a random point). Polish one feasible basin.
    private static final SolveCore.Budget CLOSER =
            new SolveCore.Budget(1, 5000, 2, BucketAscentPolish.FAST);

    /** Small CMA-ES step for the closer: the warm start is nearly feasible, so a wide sigma (the cold
     *  default is 90deg) would explore away from it and discard its precision. */
    private static final double CLOSER_SIGMA_DEG = 4.0;

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        SmoothJumpModel smooth = SmoothJumpModel.like(exact);
        JumpPhysicsInputs sc = spec.asScenario();
        List<JumpConstraint> ineq = JumpConstraintCompiler.compile(tighten(spec, SMOOTH_MARGIN)).ineq;
        double[][] seeds = seeds(sc, spec.objective);
        long t0 = System.nanoTime();

        // Stage 1: cheap analytic-gradient basin search on the smooth model -> best near-feasible warm start.
        double[] warm = null;
        double warmViol = Double.POSITIVE_INFINITY;
        for (double w : SMOOTH_WEIGHTS) {
            SmoothGradientSolver gs = new SmoothGradientSolver(smooth, sc, w);
            for (double[] seed : seeds) {
                if (cancel.get()) return null;
                double[] y = gs.solve(ineq, spec.objective, seed);
                double v = violOnExact(exact, spec, y);
                if (v < warmViol) { warmViol = v; warm = y; }
            }
        }
        if (warm == null) return null;
        long t1 = System.nanoTime();

        // Stage 2: settle the quantized lattice with the proven multistart, warm-started from the basin.
        double[] yaws = SolveCore.optimize(exact, spec, CLOSER, CLOSER_SIGMA_DEG, feasTol, cancel, warm);
        if (cancel.get() || yaws == null) return null;
        double viol = violOnExact(exact, spec, yaws);

        if (DEBUG) System.out.printf("  FAST smooth=%.1fms(warmViol=%.2e) closer=%.1fms viol=%.2e -> %s%n",
                (t1 - t0) / 1e6, warmViol, (System.nanoTime() - t1) / 1e6, viol,
                viol <= feasTol ? "ok" : "FALLBACK");

        return viol <= feasTol ? yaws : null;
    }

    /** Non-degenerate starting facing vectors. A start pointed exactly along the objective axis is a saddle
     *  (rotating yaw there is first-order orthogonal to that axis), so seeds are offset +/- off the axis and
     *  one tracks the segment's entry facing. Both arc directions are tried since the walls decide which way
     *  the path must bend. */
    private static double[][] seeds(JumpPhysicsInputs sc, Objective obj) {
        double axis = objAxisYaw(obj);
        int n = sc.numTicks;
        return new double[][]{
                fill(n, axis + 40.0),
                fill(n, axis - 40.0),
                fill(n, sc.startYaw),
        };
    }

    /** Copy the spec with every inequality wall moved inward by {@code margin} (GE up, LE down). */
    private static JumpSpec tighten(JumpSpec spec, double margin) {
        java.util.List<JumpConstraint> out = new java.util.ArrayList<>(spec.constraints.size());
        for (JumpConstraint c : spec.constraints) {
            double rhs = c.rhs;
            if (c.mode != JumpConstraint.Mode.F) {
                if (c.cmp == JumpConstraint.Cmp.GE) rhs = c.rhs + margin;
                else if (c.cmp == JumpConstraint.Cmp.LE) rhs = c.rhs - margin;
            }
            out.add(new JumpConstraint(c.mode, c.t1, c.t2, c.op, c.cmp, rhs, c.name));
        }
        return new JumpSpec(spec.asScenario(), out, spec.objective);
    }

    private static double[] fill(int n, double v) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    /** World yaw pointing along the objective direction. MC yaw: 0 = +Z, 90 = -X, 180 = -Z, -90 = +X. */
    private static double objAxisYaw(Objective obj) {
        boolean max = obj.sense == Objective.Sense.MAX;
        if (obj.axis == JumpPhysicsInputs.Axis.X) return max ? -90.0 : 90.0;
        return max ? 0.0 : 180.0;
    }

    private static double violOnExact(ExactJumpModel exact, JumpSpec spec, double[] yawsAbsWrapped) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbsWrapped));
        ForwardPath path = exact.forward(sc, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
    }
}
