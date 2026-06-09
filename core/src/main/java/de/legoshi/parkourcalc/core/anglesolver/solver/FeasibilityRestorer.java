package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Feasibility refiner: drives the maximum constraint violation of a NEAR-feasible facing array to zero on
 *  the byte-exact model. {@link LongRunSolver} calls it as the endgame of its from-scratch solve, handing it
 *  the facings its constructive waypoint guess + wide Gauss-Newton has already brought close (a fraction of a
 *  block from feasible) -- never a recorded trajectory.
 *
 *  <p>It works directly in <em>game-facing space</em> on the byte-exact {@link ExactJumpModel}: each tick's
 *  facing independently drives that tick's move, so the thing optimized is exactly the thing run -- there is
 *  no affine surrogate to drift over a long horizon and no facing round-trip. From the warm start it drives
 *  the maximum constraint violation down in two stages:
 *  <ol>
 *    <li><b>Inexact Gauss-Newton.</b> Active set = constraints within a buffer of their wall. Residuals are
 *        the byte-exact margins; the Jacobian is the affine model's analytic position-vs-facing derivative
 *        (no forwards). A damped min-norm step (smallest facing change) with a byte-exact line search
 *        collapses the bulk of the violation in a handful of steps.</li>
 *    <li><b>Surgical coordinate polish.</b> The residual sub-bucket violation (MC's 65536-step sine table
 *        fragments the feasible set into discrete islands) is cleaned up by block-1 / block-2 coordinate
 *        moves restricted to the ticks influencing still-violated constraints, using the incremental forward
 *        ({@link ExactJumpModel#stepRange}) so a late perturbation costs O(n - t).</li>
 *  </ol>
 *
 *  <p>Returns the refined game facings (the caller checks feasibility and may run a final pass). This only
 *  restores feasibility ("solve at all"); a follow-up objective polish ({@link BucketAscentPolish}) is a
 *  separate, strictly-improving step. */
public final class FeasibilityRestorer {

    private static final double TARGET = 2.0e-4;   // push active margins this far onto the feasible side
    private static final double BUFFER = 0.05;     // include constraints within this of their wall as active
    private static final int GN_ITERS = 80;
    private static final int POLISH_PASSES = 60;
    private static final int REACH = 24;           // polish window: ticks before a violated constraint's tick

    private FeasibilityRestorer() {
    }

    /** Restore feasibility from {@code warmGameFacings}. Returns a strictly-feasible game-facing array (a
     *  fresh copy) or {@code null}. {@code feasTol} is the acceptance threshold on max-violation (0 = strict). */
    public static double[] refine(ExactJumpModel exact, JumpSpec spec, double[] warmGameFacings,
                                   double feasTol, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (warmGameFacings == null || warmGameFacings.length != n) return null;
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        double[] gf = warmGameFacings.clone();
        ForwardPath p = exact.forward(sc, gf);
        double v = compiled.maxViolation(gf, p);
        if (v <= feasTol) return gf;

        List<JumpConstraint> all = new ArrayList<>(compiled.ineq);
        all.addAll(compiled.eq);

        gaussNewton(exact, sc, all, gf, cancel);
        if (cancel != null && cancel.get()) return null;
        p = exact.forward(sc, gf);
        v = compiled.maxViolation(gf, p);
        if (v > feasTol) {
            focusedPolish(exact, sc, compiled, all, gf, p, cancel);
            if (cancel != null && cancel.get()) return null;
            p = exact.forward(sc, gf);
            v = compiled.maxViolation(gf, p);
        }
        return gf; // caller checks feasibility (may still be a bucket short; a follow-up pump finishes)
    }

    /** Inexact Gauss-Newton on the active set: finite-difference byte-exact Jacobian (via the incremental
     *  forward), byte-exact residual + line search. Finite differences track the true (clamped, quantized)
     *  position-vs-facing map -- the analytic affine Jacobian stalls well short of feasibility on long runs
     *  because it omits the momentum clamp. */
    private static void gaussNewton(ExactJumpModel exact, JumpPhysicsInputs sc, List<JumpConstraint> all,
                                    double[] gf, AtomicBoolean cancel) {
        ForwardPath p = exact.forward(sc, gf);
        ForwardPath scratch = exact.forward(sc, gf); // reused for the per-column perturbation
        double v = compiled(all, gf, p);
        for (int iter = 0; iter < GN_ITERS && v > 0.0; iter++) {
            if (cancel != null && cancel.get()) return;
            double h = Math.max(0.004, Math.min(0.03, v * 6.0)); // shrink the FD step as we near feasibility
            List<JumpConstraint> act = new ArrayList<>();
            for (JumpConstraint c : all) if (margin(c, gf, p) < BUFFER) act.add(c);
            if (act.isEmpty()) break;
            int A = act.size();
            int C = 0;
            for (JumpConstraint c : act) C = Math.max(C, Math.max(c.t1, c.t2 == null ? 0 : c.t2));
            C = Math.min(C, gf.length);

            double[] m0 = new double[A];
            for (int i = 0; i < A; i++) m0[i] = margin(act.get(i), gf, p);
            // Finite-difference Jacobian J[i][s] = d(margin_i)/d(facing_s), reusing the incremental forward:
            // perturbing facing s only changes the path for ticks > s, so recompute just that tail.
            double[][] J = new double[A][C];
            System.arraycopy(p.posX, 0, scratch.posX, 0, p.posX.length);
            System.arraycopy(p.posZ, 0, scratch.posZ, 0, p.posZ.length);
            System.arraycopy(p.velX, 0, scratch.velX, 0, p.velX.length);
            System.arraycopy(p.velY, 0, scratch.velY, 0, p.velY.length);
            System.arraycopy(p.velZ, 0, scratch.velZ, 0, p.velZ.length);
            for (int s = 0; s < C; s++) {
                double save = gf[s];
                gf[s] = save + h;
                exact.stepRange(sc, gf, s, scratch);
                for (int i = 0; i < A; i++) J[i][s] = (margin(act.get(i), gf, scratch) - m0[i]) / h;
                gf[s] = save;
                exact.stepRange(sc, gf, s, scratch); // restore the tail for the next column's baseline
            }
            double[] r = new double[A];
            for (int i = 0; i < A; i++) r[i] = TARGET - m0[i];

            // Min-norm damped Gauss-Newton: dg = J^T (J J^T + lambda I)^{-1} r.
            double[][] G = new double[A][A];
            for (int i = 0; i < A; i++)
                for (int k = i; k < A; k++) {
                    double s = 0;
                    for (int j = 0; j < C; j++) s += J[i][j] * J[k][j];
                    G[i][k] = s;
                    G[k][i] = s;
                }
            double tr = 0;
            for (int i = 0; i < A; i++) tr += G[i][i];
            double lambda = 1e-6 * (tr / Math.max(1, A)) + 1e-12;
            for (int i = 0; i < A; i++) G[i][i] += lambda;
            double[] alpha = solveSym(G, r);
            if (alpha == null) break;
            double[] dg = new double[C];
            for (int j = 0; j < C; j++) {
                double s = 0;
                for (int i = 0; i < A; i++) s += J[i][j] * alpha[i];
                dg[j] = s;
            }

            double bestStep = 0, bestV = v;
            double[] trial = gf.clone();
            for (double st : new double[]{1.0, 0.6, 0.3, 0.12, 0.04, 0.012}) {
                for (int j = 0; j < C; j++) trial[j] = gf[j] + st * dg[j];
                double vv = compiled(all, trial, exact.forward(sc, trial));
                if (vv < bestV - 1e-12) { bestV = vv; bestStep = st; }
            }
            if (bestStep == 0) break; // GN direction no longer descends the byte-exact violation
            for (int j = 0; j < C; j++) gf[j] += bestStep * dg[j];
            p = exact.forward(sc, gf);
            v = compiled(all, gf, p);
        }
    }

    /** Surgical incremental coordinate polish over the ticks influencing still-violated constraints. */
    private static void focusedPolish(ExactJumpModel exact, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, List<JumpConstraint> all,
                                      double[] gf, ForwardPath p, AtomicBoolean cancel) {
        double[][] sched = {{0.08, 0.002}, {0.02, 0.0004}, {0.006, 0.0001}};
        for (int pass = 0; pass < POLISH_PASSES; pass++) {
            if (cancel != null && cancel.get()) return;
            double v = compiled.maxViolation(gf, p);
            if (v <= 0.0) return;
            boolean[] scan = new boolean[gf.length];
            for (JumpConstraint c : all) {
                if (margin(c, gf, p) >= 0.0) continue;
                int tau = Math.max(c.t1, c.t2 == null ? 0 : c.t2);
                for (int t = Math.max(0, tau - REACH); t < tau; t++) scan[t] = true;
            }
            boolean moved = false;
            double pen = compiled.penalty(gf, p, 1.0, 1.0);
            for (double[] ws : sched) {
                for (int t = 0; t < gf.length; t++) {
                    if (!scan[t]) continue;
                    double orig = gf[t], bestG = orig, bestPen = pen;
                    for (double d = -ws[0]; d <= ws[0] + 1e-12; d += ws[1]) {
                        gf[t] = orig + d;
                        exact.stepRange(sc, gf, t, p);
                        double pp = compiled.penalty(gf, p, 1.0, 1.0);
                        if (pp < bestPen) { bestPen = pp; bestG = gf[t]; }
                    }
                    gf[t] = bestG;
                    exact.stepRange(sc, gf, t, p);
                    if (bestPen < pen - 1e-18) { pen = bestPen; moved = true; }
                }
            }
            if (!moved) {
                // Block-2 island hop on adjacent scanned pairs (tiny window) when block-1 stalls.
                for (int t = 0; t < gf.length - 1 && !moved; t++) {
                    if (!scan[t]) continue;
                    double oi = gf[t], oj = gf[t + 1], bi = oi, bj = oj, bo = pen;
                    for (double di = -0.05; di <= 0.05 + 1e-12; di += 0.0025) {
                        gf[t] = oi + di;
                        for (double dj = -0.05; dj <= 0.05 + 1e-12; dj += 0.0025) {
                            gf[t + 1] = oj + dj;
                            exact.stepRange(sc, gf, t, p);
                            double pp = compiled.penalty(gf, p, 1.0, 1.0);
                            if (pp < bo) { bo = pp; bi = gf[t]; bj = gf[t + 1]; }
                        }
                    }
                    gf[t] = bi; gf[t + 1] = bj;
                    exact.stepRange(sc, gf, t, p);
                    if (bo < pen - 1e-18) { pen = bo; moved = true; }
                }
            }
            p = exact.forward(sc, gf);
            if (!moved) return;
        }
    }

    /** Feasibility margin: >= 0 means satisfied (larger is safer); for eq, the goal is 0 so margin = -|res|. */
    private static double margin(JumpConstraint c, double[] gf, ForwardPath p) {
        double e = JumpConstraintCompiler.evaluate(c, gf, p);
        switch (c.cmp) {
            case GE: return e;
            case LE: return -e;
            default: return -Math.abs(e);
        }
    }

    private static double compiled(List<JumpConstraint> all, double[] gf, ForwardPath p) {
        double v = 0.0;
        for (JumpConstraint c : all) {
            double s = -margin(c, gf, p);
            if (s > v) v = s;
        }
        return v;
    }

    /** Solve a small symmetric system G x = b by Gaussian elimination with partial pivoting; null on breakdown. */
    private static double[] solveSym(double[][] G, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(G[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(M[r][col]) > Math.abs(M[piv][col])) piv = r;
            if (Math.abs(M[piv][col]) < 1e-14) return null;
            double[] tmp = M[col]; M[col] = M[piv]; M[piv] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = M[r][col] / M[col][col];
                for (int k = col; k <= n; k++) M[r][k] -= f * M[col][k];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = M[i][n] / M[i][i];
        return x;
    }
}
