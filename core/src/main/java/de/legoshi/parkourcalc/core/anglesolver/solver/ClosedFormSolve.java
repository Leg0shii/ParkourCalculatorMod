package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closed-form jump solve: the microsecond fast path tried ahead of the CMA-ES multistart.
 *
 *  <p>It exploits the proven structure (horizontal motion is linear in the per-tick input vectors; the
 *  only nonconvexity is each input's fixed modulus) by solving the convex Lagrangian dual
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
 *  closed form does not apply (facing walls) or cannot certify feasibility; the caller then falls back
 *  ({@link SlpSolve}, then the full multistart), so this only ever makes solving faster, never less
 *  reliable. Optimizing into a same-axis wall degenerates the dual's recovery, which is why one
 *  direction can fail here while the opposite certifies (docs/research/angle-solver.md 2.1.1). */
public final class ClosedFormSolve {

    private ClosedFormSolve() {
    }

    public static boolean DEBUG = false;

    /** Inward wall margins (blocks) tried in order; the first that yields exact feasibility wins. The
     *  smallest feasible margin gives the best objective. 0 is tried first in case quantization happens to
     *  land safe; the rest cover the ~1e-4 sine-table perturbation with headroom. */
    private static final double[] MARGINS = {0.0, 1.0e-4, 3.0e-4, 6.0e-4, 1.2e-3, 2.5e-3, 5.0e-3, 1.0e-2};

    /** Robust (centered) margins, largest first: the first margin that certifies is the realized clearance
     *  on every active wall. For surrogate-objective solves (lead-in windows), where hugging walls commits
     *  fragile seam states. */
    private static final double[] MARGINS_ROBUST = {5.0e-2, 2.0e-2, 1.0e-2, 5.0e-3, 1.2e-3, 3.0e-4, 0.0};

    private static final int MAX_INERTIA_PASSES = 4;

    private static final int RUNG_STALL_LIMIT = 2;

    public static final class Result {
        public final double[] yaws;
        public final double violation;
        public final boolean feasible;

        Result(double[] yaws, double violation, boolean feasible) {
            this.yaws = yaws;
            this.violation = violation;
            this.feasible = feasible;
        }
    }

    public static double[] optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        Result r = optimizeReturning(exact, spec, feasTol, cancel, MARGINS, true);
        return r != null && r.feasible ? r.yaws : null;
    }

    /** Like {@link #optimize}, but prefers clearance over objective: the result keeps the largest
     *  certifiable uniform distance from every wall. */
    public static double[] optimizeRobust(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        Result r = optimizeReturning(exact, spec, feasTol, cancel, MARGINS_ROBUST, false);
        return r != null && r.feasible ? r.yaws : null;
    }

    public static Result optimizeRobustGraded(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return optimizeReturning(exact, spec, feasTol, cancel, MARGINS_ROBUST, false);
    }

    private static Result optimizeReturning(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                            double[] margins, boolean ascending) {
        JumpPhysicsInputs sc = spec.asScenario();

        // The linear model represents only position (X/Z) walls. Facing walls are not position-linear.
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return null;

        long t0 = System.nanoTime();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        int n = sc.numTicks;
        boolean[] zeroX = null;
        boolean[] zeroZ = null;
        Result best = null;
        for (int pass = 0; pass < MAX_INERTIA_PASSES; pass++) {
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "pass=%d pattern=%s n=%d m=%d %s",
                        pass, SolverTrace.patternLabel(zeroX, zeroZ), n, spec.constraints.size(),
                        ascending ? "ascending" : "robust");
            }
            Result r = runLadder(exact, spec, sc, compiled, feasTol, cancel, margins, ascending, zeroX, zeroZ, t0);
            if (r == null) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d ladder empty (trivial/unbounded/cancel)", pass);
                break;
            }
            if (r.feasible) return r;
            boolean improved = best == null || r.violation < best.violation;
            if (best == null || r.violation < best.violation) best = r;
            if (pass > 0 && !improved) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d no improvement (viol=%.3e), stop", pass, r.violation);
                break;
            }

            boolean[] nzx = new boolean[n];
            boolean[] nzz = new boolean[n];
            new JumpLinearModel(sc).zeroingPattern(r.yaws, exact.inertiaThreshold(), exact.perAxisInertia(), nzx, nzz);
            if (!patternEffective(sc, nzx, nzz)) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d pattern %s ineffective, stop", pass, SolverTrace.patternLabel(nzx, nzz));
                break;
            }
            if (patternEquals(zeroX, nzx) && patternEquals(zeroZ, nzz)) {
                if (SolverTrace.on()) SolverTrace.log("CF", "pass=%d pattern fixed point, stop", pass);
                break;
            }
            zeroX = nzx;
            zeroZ = nzz;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("CF", "fallback bestViol=%s us=%.1f",
                    best == null ? "none" : SolverTrace.fmt("%.3e", best.violation), (System.nanoTime() - t0) / 1e3);
        }
        return best;
    }

    private static boolean patternEffective(JumpPhysicsInputs sc, boolean[] zeroX, boolean[] zeroZ) {
        for (int t = 0; t < zeroX.length; t++) {
            if (zeroX[t] && (t > 0 || sc.initialVelocity.x != 0.0)) return true;
            if (zeroZ[t] && (t > 0 || sc.initialVelocity.z != 0.0)) return true;
        }
        return false;
    }

    private static Result runLadder(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double feasTol, AtomicBoolean cancel,
                                    double[] margins, boolean ascending, boolean[] zeroX, boolean[] zeroZ, long t0) {
        JumpLinearModel lin = new JumpLinearModel(sc, zeroX, zeroZ);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);

        // Compile the wall structure once (margin applied inside the dual solve); a violated constant
        // constraint is unfixable, so bail to the fallback immediately.
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return null;
        double vBound = exact.perAxisInertia() ? exact.inertiaThreshold()
                : exact.inertiaThreshold() / Math.sqrt(2.0);
        walls.addAll(lin.velocityWalls(vBound));

        CostateDualSolver solver = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls);

        // Each rung warm-starts from the previous margin's multipliers, so the ladder costs barely more
        // than a single solve.
        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestYaws = null;
        double[] warm = null;
        int rungStall = 0;
        for (double margin : margins) {
            if (cancel.get()) return null;
            CostateDualSolver.Result r = solver.solve(margin, warm);
            // Dual unbounded -> primal infeasible; infeasibility is monotone in the margin, so ascending
            // stops while the descending (robust) ladder keeps trying smaller rungs.
            if (r == null) {
                if (SolverTrace.on()) SolverTrace.log("CF", "rung margin=%.2e dual unbounded%s", margin, ascending ? ", ladder stop" : "");
                if (ascending) break;
                continue;
            }
            warm = r.lambda;

            double[] yaws = recover(lin, spec.objective, r);
            double viol = violOnExact(exact, sc, compiled, yaws);
            if (SolverTrace.on()) {
                SolverTrace.log("CF", "rung margin=%.2e iters=%d pg=%.3e dual=%.9f viol=%.3e%s",
                        margin, solver.lastIters, solver.lastPgres, r.value, viol,
                        viol <= feasTol ? " certified" : "");
            }
            if (DEBUG) {
                double[] gf = sc.toGameFacings(yaws);
                double o = exact.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
                System.out.printf("  CLOSED margin=%.2e iters=%d pg=%.3e viol=%.2e obj=%.6f%n",
                        margin, solver.lastIters, solver.lastPgres, viol, o);
            }
            if (viol < bestViol) {
                bestViol = viol;
                bestYaws = yaws;
                rungStall = 0;
            } else {
                rungStall++;
            }
            if (viol <= feasTol) {
                if (DEBUG) System.out.printf("  CLOSED -> %.2fus (margin=%.1e)%n", (System.nanoTime() - t0) / 1e3, margin);
                return new Result(yaws, viol, true);
            }
            if (ascending && rungStall >= RUNG_STALL_LIMIT) {
                if (SolverTrace.on()) SolverTrace.log("CF", "ladder stalled after margin=%.2e (bestViol=%.3e)", margin, bestViol);
                break;
            }
        }
        if (DEBUG) System.out.printf("  CLOSED FALLBACK %.2fus bestViol=%.2e%n",
                (System.nanoTime() - t0) / 1e3, bestViol);
        if (bestYaws == null) return null;
        return new Result(bestYaws, bestViol, false);
    }

    private static boolean patternEquals(boolean[] a, boolean[] b) {
        int n = b.length;
        for (int t = 0; t < n; t++) {
            boolean av = a != null && a[t];
            if (av != b[t]) return false;
        }
        return true;
    }

    /** Weak-duality bound on the spec's objective in world coordinates: no feasible path can land beyond
     *  it. Valid even where the dual's recovery degenerates, so it certifies a primally-found solution
     *  without a search. {@code NaN} when no bound applies (facing walls, violated constant, unbounded). */
    public static double dualBound(JumpSpec spec) {
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return Double.NaN;
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivialInfeasible = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivialInfeasible);
        if (trivialInfeasible[0]) return Double.NaN;
        CostateDualSolver.Result r = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        if (r == null) return Double.NaN;
        // r.value bounds max c·u with c MAX-normalized; fold the constant part back in (MIN is negated).
        int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        double constPos = lin.constPos(spec.objective.tick, axis);
        return spec.objective.sense == Objective.Sense.MAX ? constPos + r.value : constPos - r.value;
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
