package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closed-form jump solve: the principled fast path that replaces the global CMA-ES multistart.
 *
 *  <p>It exploits the proven structure -- horizontal motion is linear in the per-tick input vectors, the
 *  only nonconvexity is each input's fixed modulus -- by solving the convex Lagrangian dual
 *  ({@link CostateDualSolver}) to global optimality and recovering each tick's optimal yaw as the direction
 *  of its friction-propagated costate ({@link JumpLinearModel}). That is the entire continuous solve: a few
 *  microseconds, no search, no tuning.
 *
 *  <p>The continuous optimum hugs the active walls exactly; MC's 65536-bucket sine table then perturbs the
 *  realized path by ~1e-4 blocks, which could nudge a tight wall to the wrong side. So the walls are solved
 *  with a small inward margin and the result is re-checked on the byte-exact {@link ExactJumpModel}; the
 *  margin is grown geometrically until the quantized trajectory is strictly feasible. Each attempt is
 *  microseconds, so even several attempts stay far under a millisecond.
 *
 *  <p>Returns absolute wrapped facings strictly feasible on the exact model, or {@code null} when the
 *  closed form does not apply (facing/equality walls) or cannot certify feasibility -- the caller then
 *  falls back to the full multistart, so this only ever makes solving faster, never less reliable. */
public final class ClosedFormSolve {

    private ClosedFormSolve() {
    }

    public static boolean DEBUG = false;

    /** Inward wall margins (blocks) tried in order; the first that yields exact feasibility wins. The
     *  smallest feasible margin gives the best objective. 0 is tried first in case quantization happens to
     *  land safe; the rest cover the ~1e-4 sine-table perturbation with headroom. */
    private static final double[] MARGINS = {0.0, 1.0e-4, 3.0e-4, 6.0e-4, 1.2e-3, 2.5e-3, 5.0e-3, 1.0e-2};

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        List<JumpConstraint> constraints = spec.constraints;

        // The linear model represents only position (X/Z) walls. Facing walls are not position-linear.
        if (JumpLinearModel.hasFacingWall(constraints)) return null;

        long t0 = System.nanoTime();
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);

        // Compile the wall structure once (margin applied inside the dual solve); a violated constant
        // constraint is unfixable, so bail to the fallback immediately.
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        CostateDualSolver solver = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls);

        // Margin ladder: the continuous optimum hugs the active walls exactly, so grow a small inward
        // margin until the sine-table-quantized trajectory is byte-exact feasible. Each solve warm-starts
        // from the previous margin's multipliers, so the ladder costs barely more than a single solve.
        double bestViol = Double.POSITIVE_INFINITY;
        double[] warm = null;
        for (double margin : MARGINS) {
            if (cancel.get()) return null;
            CostateDualSolver.Result r = solver.solve(margin, warm);
            // Dual unbounded -> primal infeasible. Margins only tighten the inequality walls (equalities are
            // unmargined), so infeasibility is monotone along the ladder: every later rung is infeasible too.
            if (r == null) break;
            warm = r.lambda;

            double[] yaws = recover(lin, spec.objective, r);
            double viol = violOnExact(exact, sc, compiled, yaws);
            if (DEBUG) {
                double[] gf = sc.toGameFacings(yaws);
                double o = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
                System.out.printf("  CLOSED margin=%.2e iters=%d viol=%.2e obj=%.6f%n",
                        margin, solver.lastIters, viol, o);
            }
            if (viol < bestViol) bestViol = viol;
            if (viol <= feasTol) {
                if (DEBUG) System.out.printf("  CLOSED -> %.2fus (margin=%.1e)%n", (System.nanoTime() - t0) / 1e3, margin);
                return yaws;
            }
        }
        if (DEBUG) System.out.printf("  CLOSED FALLBACK %.2fus bestViol=%.2e%n",
                (System.nanoTime() - t0) / 1e3, bestViol);
        return null;
    }

    /** Per-tick costate direction -> absolute wrapped facing. A vanishing costate (objective and walls
     *  cancel, direction undetermined) defaults to pointing along the objective axis. */
    private static double[] recover(JumpLinearModel lin, Objective obj, CostateDualSolver.Result r) {
        int n = lin.n;
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            if (gx * gx + gz * gz < 1.0e-18) {
                boolean max = obj.sense == Objective.Sense.MAX;
                if (obj.axis == JumpPhysicsInputs.Axis.X) { gx = max ? 1.0 : -1.0; gz = 0.0; }
                else { gx = 0.0; gz = max ? 1.0 : -1.0; }
            }
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        return yaws;
    }

    private static double violOnExact(ExactJumpModel exact, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, double[] yawsAbsWrapped) {
        double[] gf = sc.toGameFacings(yawsAbsWrapped);
        ForwardPath path = exact.forward(sc, gf);
        return compiled.maxViolation(gf, path);
    }
}
