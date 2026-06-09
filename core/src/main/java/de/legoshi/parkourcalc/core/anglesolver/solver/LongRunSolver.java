package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** From-scratch solver for long multi-jump spans -- the post-failure fallback for runs the closed-form dual
 *  cannot converge on at scale (e.g. the 354-tick "desert hard" runs, 30 jumps, 81 walls).
 *
 *  <p>It uses ONLY the legitimately-available inputs: the resume start state, the input-specified physics
 *  structure (per-tick ground/air from slip overrides, jump keys, strafe), and the constraints. It NEVER
 *  reads a recorded trajectory or recorded facings -- so it solves a run the same whether or not a prior
 *  (possibly broken) trajectory exists. Three stages, all on the byte-exact {@link ExactJumpModel} in
 *  game-facing space (the thing optimized is the thing run -- no affine surrogate to drift over a long
 *  horizon, no facing round-trip):
 *  <ol>
 *    <li><b>Waypoint construction.</b> The constraints pin each jump's landing to a footprint box; their
 *        centers are waypoints. A heading controller faces each tick toward its segment's waypoint (corrected
 *        by the per-tick strafe phase {@link JumpLinearModel#baseArg}), forwarding tick-by-tick. This is a
 *        constructive initial guess derived purely from the constraints -- a few blocks from feasible.</li>
 *    <li><b>Robust Gauss-Newton.</b> Active set = constraints within a buffer of their wall; a damped
 *        min-norm step with a byte-exact finite-difference Jacobian (via the incremental forward
 *        {@link ExactJumpModel#stepRange}) and a backtracking line search on the true max-violation drives
 *        the bulk of the violation out. The buffer widens on a stall to recruit more constraints.</li>
 *    <li><b>Coordinate polish.</b> The residual sub-bucket violation (MC's 65536-step sine table fragments
 *        the feasible set into discrete islands) is cleaned to strict feasibility by block-1 / block-2 moves
 *        restricted to the ticks influencing still-violated constraints.</li>
 *  </ol>
 *
 *  <p>Returns game facings strictly feasible on the byte-exact model, or {@code null}. This restores
 *  feasibility ("solve at all"); a follow-up objective ascent is a separate, strictly-improving step. */
public final class LongRunSolver {

    private static final double RAD = Math.PI / 180.0;
    /** Line-search step fractions (fraction of the GN step), coarse-to-fine. */
    private static final double[] STEPS_WIDE = {1.0, 0.5, 0.25, 0.12, 0.06, 0.03, 0.012, 0.005, 0.002};

    public static boolean DEBUG = false;

    private LongRunSolver() {
    }

    /** Solve the span from scratch. {@code feasTol} is the acceptance threshold on max-violation (0 = strict). */
    public static double[] solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        List<JumpConstraint> all = new ArrayList<>(compiled.ineq);
        all.addAll(compiled.eq);

        int[] bounds = boundaries(sc);
        double[] gf = waypointInit(exact, sc, spec, bounds);
        if (cancel != null && cancel.get()) return null;
        if (DEBUG) System.err.printf("LRS init viol=%.4f%n", compiled.maxViolation(gf, exact.forward(sc, gf)));

        // Stage 2 -- WIDE Gauss-Newton: from the several-block waypoint guess, a wide active set that widens
        // further on a stall drives the bulk of the violation out (down to ~the sine-bucket floor).
        gaussNewton(exact, sc, all, gf, cancel, 0.10, 0.60, 5.0e-4, 0.04, 4.0, STEPS_WIDE, 120);
        if (cancel != null && cancel.get()) return null;
        if (DEBUG) System.err.printf("LRS gn-wide viol=%.4f%n", compiled.maxViolation(gf, exact.forward(sc, gf)));

        // Stage 3 -- refine: a narrow-active-set Gauss-Newton + block-1/block-2 coordinate polish that drives
        // the continuous part down and crosses MC's discrete sine buckets. (FeasibilityRestorer.refine is the
        // same routine that polishes a near-feasible run; here its warm start is our own constructed guess.)
        gf = FeasibilityRestorer.refine(exact, spec, gf, feasTol, cancel);
        if (gf == null || (cancel != null && cancel.get())) return null;
        if (DEBUG) System.err.printf("LRS refine viol=%.6f%n", maxViol(all, gf, exact.forward(sc, gf)));

        // Stage 4 -- feasibility pump: cross the final sine bucket on any single binding constraint the polish
        // left short, scanning its highest-leverage facings over a fine grid for the best max-violation move.
        double v = feasibilityPump(exact, sc, all, new JumpLinearModel(sc), gf, cancel);
        if (DEBUG) System.err.printf("LRS pump viol=%.6f%n", v);
        return v <= feasTol ? gf : null;
    }

    // ---- stage 1: constructive waypoint init -------------------------------------------------------------

    /** Heading controller toward each segment's footprint center; returns the per-tick game facings. */
    private static double[] waypointInit(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, int[] b) {
        int n = sc.numTicks;
        JumpLinearModel lin = new JumpLinearModel(sc);
        double[][] wp = new double[b.length - 1][];
        for (int s = 0; s < b.length - 1; s++) wp[s] = footprintCenter(spec, b[s + 1]);
        double[] gf = new double[n];
        ForwardPath path = exact.forward(sc, gf);
        for (int t = 0; t < n; t++) {
            double[] w = wp[segOf(b, t)];
            double tx = w[2] > 0 ? w[0] : path.posX[t] - 5; // no footprint (final stretch): drift toward -X
            double tz = w[2] > 0 ? w[1] : path.posZ[t] + 5;
            double heading = Math.atan2(tz - path.posZ[t], tx - path.posX[t]); // desired (X,Z) movement heading
            gf[t] = Math.toDegrees(heading - lin.baseArg(t));                  // facing so baseArg + yaw == heading
            exact.stepRange(sc, gf, t, path);
        }
        return gf;
    }

    /** Center {@code [xc, zc, has]} of the footprint box implied by the single-tick X/Z constraints at or
     *  adjacent to {@code c} (half-open footprints collapse to the present bound). */
    private static double[] footprintCenter(JumpSpec spec, int c) {
        double xlo = Double.NEGATIVE_INFINITY, xhi = Double.POSITIVE_INFINITY;
        double zlo = Double.NEGATIVE_INFINITY, zhi = Double.POSITIVE_INFINITY;
        boolean has = false;
        for (JumpConstraint jc : spec.constraints) {
            if (jc.t2 != null || jc.t1 < c - 1 || jc.t1 > c + 1) continue;
            if (jc.mode == JumpConstraint.Mode.X) {
                if (jc.cmp == JumpConstraint.Cmp.GE) { xlo = Math.max(xlo, jc.rhs); has = true; }
                if (jc.cmp == JumpConstraint.Cmp.LE) { xhi = Math.min(xhi, jc.rhs); has = true; }
            } else if (jc.mode == JumpConstraint.Mode.Z) {
                if (jc.cmp == JumpConstraint.Cmp.GE) { zlo = Math.max(zlo, jc.rhs); has = true; }
                if (jc.cmp == JumpConstraint.Cmp.LE) { zhi = Math.min(zhi, jc.rhs); has = true; }
            }
        }
        return new double[]{mid(xlo, xhi), mid(zlo, zhi), has ? 1 : 0};
    }

    private static double mid(double lo, double hi) {
        if (Double.isInfinite(lo)) return Double.isInfinite(hi) ? 0 : hi;
        return Double.isInfinite(hi) ? lo : (lo + hi) / 2;
    }

    /** Segment launch boundaries: the grounded ticks that begin an airborne arc, plus both endpoints, with
     *  sub-2-tick pieces merged. Ground/air is the input-specified per-tick slip annotation (NaN = air). */
    private static int[] boundaries(JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        List<Integer> bl = new ArrayList<>();
        bl.add(0);
        for (int t = 1; t < n; t++) {
            boolean g = !Double.isNaN(sc.slipAt(t)), gp = !Double.isNaN(sc.slipAt(t - 1));
            if (g && !gp) bl.add(t);
        }
        if (bl.get(bl.size() - 1) != n) bl.add(n);
        List<Integer> m = new ArrayList<>();
        m.add(bl.get(0));
        for (int i = 1; i < bl.size(); i++) {
            if (bl.get(i) - m.get(m.size() - 1) < 2 && i < bl.size() - 1) continue;
            m.add(bl.get(i));
        }
        int[] o = new int[m.size()];
        for (int i = 0; i < o.length; i++) o[i] = m.get(i);
        return o;
    }

    private static int segOf(int[] b, int t) {
        for (int s = 0; s < b.length - 1; s++) if (t >= b[s] && t < b[s + 1]) return s;
        return b.length - 2;
    }

    // ---- stage 2: robust Gauss-Newton ------------------------------------------------------------------

    private static void gaussNewton(ExactJumpModel exact, JumpPhysicsInputs sc, List<JumpConstraint> all,
                                    double[] gf, AtomicBoolean cancel, double buffer0, double bufferMax,
                                    double target, double hCap, double hMult, double[] steps, int maxIters) {
        int n = gf.length;
        ForwardPath p = exact.forward(sc, gf);
        ForwardPath scratch = exact.forward(sc, gf);
        double v = maxViol(all, gf, p);
        double buffer = buffer0;
        for (int iter = 0; iter < maxIters && v > 0.0; iter++) {
            if (cancel != null && cancel.get()) return;
            double h = Math.max(0.004, Math.min(hCap, v * hMult));
            List<JumpConstraint> act = new ArrayList<>();
            for (JumpConstraint c : all) if (margin(c, gf, p) < buffer) act.add(c);
            if (act.isEmpty()) break;
            int A = act.size();
            int C = 0;
            for (JumpConstraint c : act) C = Math.max(C, Math.max(c.t1, c.t2 == null ? 0 : c.t2));
            C = Math.min(C, n);

            double[] m0 = new double[A];
            for (int i = 0; i < A; i++) m0[i] = margin(act.get(i), gf, p);
            // Finite-difference Jacobian via the incremental forward: perturbing facing s only changes the
            // path for ticks > s, so recompute just that tail.
            double[][] J = new double[A][C];
            copyPath(p, scratch);
            for (int s = 0; s < C; s++) {
                double sv = gf[s];
                gf[s] = sv + h;
                exact.stepRange(sc, gf, s, scratch);
                for (int i = 0; i < A; i++) J[i][s] = (margin(act.get(i), gf, scratch) - m0[i]) / h;
                gf[s] = sv;
                exact.stepRange(sc, gf, s, scratch);
            }
            double[] r = new double[A];
            for (int i = 0; i < A; i++) r[i] = target - m0[i];

            // Min-norm damped step: dg = J^T (J J^T + lambda I)^{-1} r.
            double[][] G = new double[A][A];
            for (int i = 0; i < A; i++)
                for (int k = i; k < A; k++) {
                    double sm = 0;
                    for (int j = 0; j < C; j++) sm += J[i][j] * J[k][j];
                    G[i][k] = sm;
                    G[k][i] = sm;
                }
            double tr = 0;
            for (int i = 0; i < A; i++) tr += G[i][i];
            double lam = 1e-3 * (tr / Math.max(1, A)) + 1e-12;
            for (int i = 0; i < A; i++) G[i][i] += lam;
            double[] al = solveSym(G, r);
            if (al == null) break;
            double[] dg = new double[C];
            for (int j = 0; j < C; j++) {
                double sm = 0;
                for (int i = 0; i < A; i++) sm += J[i][j] * al[i];
                dg[j] = sm;
            }

            double bestStep = 0, bestV = v;
            double[] trial = gf.clone();
            for (double stp : steps) {
                for (int j = 0; j < C; j++) trial[j] = gf[j] + stp * dg[j];
                double vv = maxViol(all, trial, exact.forward(sc, trial));
                if (vv < bestV - 1e-12) { bestV = vv; bestStep = stp; }
            }
            if (bestStep == 0) {
                // Stalled. If the active set can still widen (bufferMax > buffer), recruit more constraints
                // and retry; otherwise stop (the next stage takes over).
                if (buffer >= bufferMax) break;
                buffer = Math.min(bufferMax, buffer * 1.4);
                continue;
            }
            for (int j = 0; j < C; j++) gf[j] += bestStep * dg[j];
            p = exact.forward(sc, gf);
            v = maxViol(all, gf, p);
        }
    }

    // ---- stage 5: feasibility pump ---------------------------------------------------------------------

    /** Greedily reduce the TRUE max-violation by scanning the highest-leverage facings of the currently-worst
     *  constraint over a fine grid (incremental forward), applying the single best move each step. Crosses the
     *  discrete sine buckets the continuous Gauss-Newton cannot. Returns the final max-violation. */
    private static double feasibilityPump(ExactJumpModel exact, JumpPhysicsInputs sc, List<JumpConstraint> all,
                                          JumpLinearModel lin, double[] gf, AtomicBoolean cancel) {
        ForwardPath p = exact.forward(sc, gf);
        ForwardPath scratch = exact.forward(sc, gf);
        double v = maxViol(all, gf, p);
        // Bounded: a handful of targeted moves cross the last bucket when one exists; if they cannot (a
        // genuine minimax plateau), give up quickly so the caller's fallback gets the time, rather than grind.
        for (int step = 0; step < 30 && v > 0.0; step++) {
            if (cancel != null && cancel.get()) return v;
            JumpConstraint worst = null;
            double ws = 0;
            for (JumpConstraint c : all) {
                double sl = -margin(c, gf, p);
                if (sl > ws) { ws = sl; worst = c; }
            }
            if (worst == null) break;
            int tau = Math.max(worst.t1, worst.t2 == null ? 0 : worst.t2);
            int axis = worst.mode == JumpConstraint.Mode.X ? 0 : 1;
            double opSign = worst.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
            // Rank the influencing facings by |d(worst margin)/d(facing)| (analytic, from the affine model).
            double[] lev = new double[tau];
            for (int s = 0; s < tau; s++) {
                double ang = lin.baseArg(s) + gf[s] * RAD, m = lin.mMag(s);
                double dAdd = (axis == 0 ? -(m * Math.sin(ang)) : (m * Math.cos(ang))) * RAD;
                double dval = lin.coef(s, worst.t1) * dAdd;
                if (worst.t2 != null) dval += opSign * lin.coef(s, worst.t2) * dAdd;
                lev[s] = Math.abs(dval);
            }
            boolean improved = false;
            int K = Math.min(10, tau);
            for (int k = 0; k < K; k++) {
                int s = argMax(lev);
                if (lev[s] <= 0) break;
                lev[s] = -1; // consume this tick
                double orig = gf[s], bestVal = orig, bestV = v;
                copyPath(p, scratch);
                for (double d = -0.6; d <= 0.6 + 1e-12; d += 0.0015) {
                    gf[s] = orig + d;
                    exact.stepRange(sc, gf, s, scratch);
                    double vv = maxViol(all, gf, scratch);
                    if (vv < bestV - 1e-12) { bestV = vv; bestVal = gf[s]; }
                }
                gf[s] = bestVal;
                if (bestV < v - 1e-12) {
                    p = exact.forward(sc, gf);
                    v = bestV;
                    improved = true;
                    break;
                }
            }
            if (!improved) break;
        }
        return v;
    }

    private static int argMax(double[] a) {
        int bi = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[bi]) bi = i;
        return bi;
    }

    // ---- math -------------------------------------------------------------------------------------------

    /** Feasibility margin: >= 0 means satisfied (larger is safer); for eq, the goal is 0 so margin = -|res|. */
    private static double margin(JumpConstraint c, double[] gf, ForwardPath p) {
        double e = JumpConstraintCompiler.evaluate(c, gf, p);
        switch (c.cmp) {
            case GE: return e;
            case LE: return -e;
            default: return -Math.abs(e);
        }
    }

    private static double maxViol(List<JumpConstraint> all, double[] gf, ForwardPath p) {
        double v = 0.0;
        for (JumpConstraint c : all) {
            double s = -margin(c, gf, p);
            if (s > v) v = s;
        }
        return v;
    }

    private static void copyPath(ForwardPath src, ForwardPath dst) {
        System.arraycopy(src.posX, 0, dst.posX, 0, src.posX.length);
        System.arraycopy(src.posZ, 0, dst.posZ, 0, src.posZ.length);
        System.arraycopy(src.velX, 0, dst.velX, 0, src.velX.length);
        System.arraycopy(src.velY, 0, dst.velY, 0, src.velY.length);
        System.arraycopy(src.velZ, 0, dst.velZ, 0, src.velZ.length);
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
