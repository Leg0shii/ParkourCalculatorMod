package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;

/** Solves the constant-modulus jump problem to global optimality via its Lagrangian dual.
 *
 *  <p>Primal: maximize {@code c·u} subject to walls {@code A_j·u ≤ b_j} and the fixed per-tick modulus
 *  {@code |u_t| = m_t}. Dualizing only the (linear) walls and keeping the modulus constraints gives the
 *  dual function
 *  <pre>   D(λ) = Σ_t m_t · ‖c_t − Σ_j λ_j A_{j,t}‖  +  Σ_j λ_j b_j ,   minimized over λ_j ≥ 0   </pre>
 *  (equality walls keep a free multiplier). The inner maximization {@code max_{|u_t|=m_t} g_t·u_t} is
 *  {@code m_t‖g_t‖}, solved EXACTLY rather than relaxed, so the constant-modulus nonconvexity is absorbed
 *  with NO duality gap: at the dual optimum the recovery {@code u*_t = m_t·g_t/‖g_t‖} (each input pointing
 *  along its friction-propagated costate {@code g_t = c_t − Σ_j λ_j A_{j,t}}) is automatically primal
 *  feasible and optimal by complementary slackness.
 *
 *  <p>{@code D} is convex in {@code λ} and low-dimensional. Two structural quirks drive the algorithm:
 *  (1) at {@code λ=0} (and wherever the costates align with a wall axis) the dual is locally FLAT in those
 *  wall directions (moving λ along them does not rotate the unit inputs, so the curvature {@code 1−ĝ·ĝ}
 *  vanishes); (2) elsewhere it is smooth and curved. So each iteration first tries a TRUNCATED-EIGEN Newton
 *  step (exact second order in the curved eigen-subspace of the free-set Hessian, zero in the flat
 *  subspace), and if that cannot reduce {@code D} (the curved part is already solved, only flat directions
 *  remain) it falls back to a projected-gradient step with a robust expand/backtrack line search along the
 *  feasible path, which always makes progress while any descent exists. The two together converge in a
 *  handful of iterations and a few microseconds; successive {@link #solve} calls (the margin ladder)
 *  warm-start from the previous λ.
 *
 *  <p>The norm is smoothed by a tiny {@code eps}. On the pinned fast path, recovered angles need only modest
 *  dual accuracy: a small inward margin is added downstream, feasibility is re-checked byte-exact, and a miss
 *  falls through to the SLP and recovery stages, so imprecision costs speed, never solutions. That safety net
 *  does NOT extend to the free-start role: there the {@link FreeP0} start-support smoothing biases the
 *  recovered shape and start, rigid dF chains have no downstream repair, and a near-miss is a lost solve.
 *  {@code FreeP0.smooth} is therefore per-solve; the free-start near-miss path retries sharper values
 *  (see FreeStartSolve), a window bounded below by conditioning (the {@code 1/smooth} support curvature).
 *  Returns the per-tick costate directions for {@link JumpLinearModel#recoverYawDeg}, or {@code null} if the
 *  dual is unbounded (primal infeasible). */
public final class CostateDualSolver {

    private static final double EPS2 = 1.0e-14;       // norm smoothing: ‖g‖ -> sqrt(‖g‖^2 + EPS2)
    private static final int MAX_ITER = 100;
    private static final double LAMBDA_CAP = 1.0e9;   // dual var this large => treat primal as infeasible
    private static final double GRAD_TOL = 1.0e-8;    // projected-gradient (constraint-slack) convergence, blocks
    private static final double U_TOL = 1.0e-9;       // convergence in recovered-input space (degenerate optima)
    // Early divergence bail: a converging dual drives its best projected-gradient residual steadily toward
    // GRAD_TOL; the degenerate high-dimensional landscape of a long multi-jump run instead plateaus it at a
    // large floor and grinds all MAX_ITER iterations before the caller's byte-exact check rejects it. Detect
    // that stall (best residual still HUGE and not improving for several iterations) and stop, so the
    // caller bails on the (unchanged) huge recovered violation immediately instead of after 100 iterations.
    // The floor is set well above the residual any solvable single jump ever lingers at (~1.5), so the fast
    // path, whose residual is always below it, can never trip this.
    private static final double DIVERGE_PGRES = 4.0;  // bail only while the best residual is this far from 0
    private static final double DIVERGE_REL = 0.05;   // an improvement must beat the best by this fraction
    private static final int DIVERGE_STALL = 12;      // ...for this many iterations running, else: diverged
    private static final double GAMMA = 1.0e-4;       // Armijo sufficient-decrease factor
    public static final double P0_SMOOTH_DEFAULT = 0.05;
    private static final double RHO0 = 1.0e-2;        // initial Levenberg damping (fraction of σ_max)
    private static final double RHO_MIN = 1.0e-10;
    private static final double RHO_MAX = 1.0e8;

    private final int n;
    private final int m;
    private final double[] cx;
    private final double[] cz;
    private final double[] mMag;
    private final int[] axis;       // [m] wall axis (0=X, 1=Z, 2=2D)
    private final double[][] coef;  // [m][n] wall coupling (1D legacy)
    private final double[][] coefX; // [m][n] wall coupling X
    private final double[][] coefZ; // [m][n] wall coupling Z
    private final double[] bBase;   // [m] margin-0 right-hand side
    private final boolean[] eq;     // [m]
    private final double[] p0coef;
    private final double[] p0coefX;
    private final double[] p0coefZ;
    private final FreeP0 freeP0;

    // Preallocated scratch reused across iterations and across solve() calls.
    private final double[] gx, gz;     // costates g_t at current lambda
    private final double[] ux, uz;     // recovered inputs u*_t = m_t * ĝ_t
    private final double[] uPrevX, uPrevZ; // recovered inputs from the previous iterate
    private final double[] gradient;   // dual gradient [m]
    private final double[] bPrime;     // margin-adjusted rhs [m]
    private final double[] lambda;     // dual vars [m]
    private final double[] cand;       // candidate lambda [m]
    private final double[] dir;        // search direction [m]
    private final double[] ngx, ngz;   // candidate costates
    private final int[] freeIdx;       // free-set indices
    private final double[] wOverNrm;   // per-tick m_t/‖g_t‖ for the Hessian build
    private final double[] gxHat, gzHat; // per-tick ĝ components for the Hessian build
    private final double[][] H;        // free-set Hessian
    private final double[][] Lwork;    // Cholesky factor of (H + damp·I)
    private final double[] step;       // damped-Newton step on the free set
    private double rhoRel;             // adaptive Levenberg damping (fraction of curvature), persisted across iters

    public CostateDualSolver(int n, double[] cx, double[] cz, double[] mMag, List<JumpLinearModel.Wall> walls) {
        this(n, cx, cz, mMag, walls, null);
    }

    public CostateDualSolver(int n, double[] cx, double[] cz, double[] mMag, List<JumpLinearModel.Wall> walls,
                             FreeP0 freeP0) {
        this.n = n;
        this.m = walls.size();
        this.cx = cx;
        this.cz = cz;
        this.mMag = mMag;
        this.freeP0 = freeP0;
        this.axis = new int[m];
        this.coef = new double[m][];
        this.coefX = new double[m][];
        this.coefZ = new double[m][];
        this.bBase = new double[m];
        this.eq = new boolean[m];
        this.p0coef = new double[m];
        this.p0coefX = new double[m];
        this.p0coefZ = new double[m];
        for (int j = 0; j < m; j++) {
            JumpLinearModel.Wall w = walls.get(j);
            axis[j] = w.axis;
            coef[j] = w.coef;
            coefX[j] = w.coefX;
            coefZ[j] = w.coefZ;
            bBase[j] = w.bPrime;
            eq[j] = w.eq;
            p0coef[j] = w.p0coef;
            p0coefX[j] = w.p0coefX;
            p0coefZ[j] = w.p0coefZ;
        }
        this.gx = new double[n];
        this.gz = new double[n];
        this.ux = new double[n];
        this.uz = new double[n];
        this.uPrevX = new double[n];
        this.uPrevZ = new double[n];
        this.gradient = new double[m];
        this.bPrime = new double[m];
        this.lambda = new double[m];
        this.cand = new double[m];
        this.dir = new double[m];
        this.ngx = new double[n];
        this.ngz = new double[n];
        this.freeIdx = new int[m];
        this.H = new double[m][m];
        this.Lwork = new double[m][m];
        this.step = new double[m];
        this.wOverNrm = new double[n];
        this.gxHat = new double[n];
        this.gzHat = new double[n];
    }

    /** Result: the per-tick costate directions {@code (gx,gz)} (recover the yaw from these), the dual
     *  multipliers (active-wall pattern, reusable as the next margin's warm start), and the dual value,
     *  which by weak duality upper-bounds {@code max c·u} even unconverged or recovery-degenerate. */
    public static final class Result {
        public final double[] gx;
        public final double[] gz;
        public final double[] lambda;
        public final double value;
        public final double dvx;
        public final double dvz;

        Result(double[] gx, double[] gz, double[] lambda, double value, double dvx, double dvz) {
            this.gx = gx;
            this.gz = gz;
            this.lambda = lambda;
            this.value = value;
            this.dvx = dvx;
            this.dvz = dvz;
        }
    }

    public static final class FreeP0 {
        public final double dvLoX;
        public final double dvHiX;
        public final double dvLoZ;
        public final double dvHiZ;
        public final double objDevX;
        public final double objDevZ;
        public final double smooth;

        public FreeP0(double dvLoX, double dvHiX, double dvLoZ, double dvHiZ, double objDevX, double objDevZ,
                      double smooth) {
            this.dvLoX = dvLoX;
            this.dvHiX = dvHiX;
            this.dvLoZ = dvLoZ;
            this.dvHiZ = dvHiZ;
            this.objDevX = objDevX;
            this.objDevZ = objDevZ;
            this.smooth = smooth;
        }
    }

    /** Diagnostics: solver iterations spent in the last {@link #solve}. */
    public int lastIters;
    /** Diagnostics: final projected-gradient residual of the last {@link #solve}. */
    public double lastPgres;
    public boolean lastStalled;

    /** Minimize the dual with walls tightened inward by {@code margin}, warm-started from {@code warm}
     *  (null = cold, λ=0). Returns fresh arrays so the caller may keep them; internal state is reusable. */
    public Result solve(double margin, double[] warm) {
        lastStalled = false;
        if (m == 0) {
            // No walls: the optimum is every input along the objective (costate = c). Closed form.
            double[] empty = new double[0];
            double v = costate(empty, gx, gz);
            lastIters = 0;
            return new Result(gx.clone(), gz.clone(), empty, v, recoveredDelta(empty, 0), recoveredDelta(empty, 1));
        }
        for (int j = 0; j < m; j++) bPrime[j] = bBase[j] - (eq[j] ? 0.0 : margin);
        if (warm != null) System.arraycopy(warm, 0, lambda, 0, m);
        else java.util.Arrays.fill(lambda, 0.0);

        double phi = costate(lambda, gx, gz);
        grad(gx, gz, gradient);
        System.arraycopy(ux, 0, uPrevX, 0, n);
        System.arraycopy(uz, 0, uPrevZ, 0, n);
        rhoRel = RHO0;

        double pgBest = Double.POSITIVE_INFINITY;
        int stall = 0;
        int it = 0;
        for (; it < MAX_ITER; it++) {
            double pgres = 0.0;
            for (int j = 0; j < m; j++) {
                double p = project(lambda[j] - gradient[j], eq[j]) - lambda[j];
                if (Math.abs(p) > pgres) pgres = Math.abs(p);
            }
            lastPgres = pgres;
            if (pgres <= GRAD_TOL) break;

            // Early divergence bail (rationale at the DIVERGE_* constants).
            if (pgres < pgBest * (1.0 - DIVERGE_REL)) { pgBest = pgres; stall = 0; }
            else { if (pgres < pgBest) pgBest = pgres; stall++; }
            if (pgBest > DIVERGE_PGRES && stall >= DIVERGE_STALL) { lastStalled = true; break; }

            // Converge in costate space: at a degenerate optimum the multipliers λ keep wandering in the
            // null space of Aᵀ (pg never reaches 0), but the recovered inputs u*, all that the angles
            // depend on, have stopped moving. That is the real optimum.
            double du = 0.0;
            for (int t = 0; t < n; t++) {
                du = Math.max(du, Math.abs(ux[t] - uPrevX[t]));
                du = Math.max(du, Math.abs(uz[t] - uPrevZ[t]));
            }
            if (it > 0 && du <= U_TOL) break;
            System.arraycopy(ux, 0, uPrevX, 0, n);
            System.arraycopy(uz, 0, uPrevZ, 0, n);

            int nf = 0;
            for (int j = 0; j < m; j++) {
                if (eq[j] || lambda[j] > 0.0 || gradient[j] < 0.0) freeIdx[nf++] = j;
            }

            double newPhi = newtonStep(phi, nf);
            if (newPhi < phi) {
                phi = newPhi;
            } else {
                double sp = gradientStep(phi);
                if (!(sp < phi)) break; // no descent along the projected gradient -> stationary
                phi = sp;
            }

            for (int j = 0; j < m; j++) {
                if (lambda[j] > LAMBDA_CAP || Double.isNaN(lambda[j])) { lastIters = it; return null; }
            }
        }
        lastIters = it;
        return new Result(gx.clone(), gz.clone(), lambda.clone(), phi, recoveredDelta(lambda, 0), recoveredDelta(lambda, 1));
    }

    private double recoveredDelta(double[] lam, int a) {
        return freeP0 == null ? 0.0 : deltaOf(hAxis(lam, a), a);
    }

    // ---- steps (each commits lambda/gx/gz/gradient on success and returns the new dual value) -----------

    /** Levenberg-damped projected-Newton step on the free set: solves {@code (H + ρ·maxDiag·I)·Δ = −∇D}
     *  by Cholesky, which is full Newton in well-curved directions and a bounded damped-gradient move in
     *  flat/near-flat ones. The damping {@code ρ} adapts (shrinks when a full step is accepted, grows when it
     *  must be retried), so it self-tunes between Newton and gradient descent. Commits and returns the new
     *  dual value on a decrease, else returns {@code phi} (caller falls back to the projected-gradient step,
     *  which also escapes a totally flat point). */
    private double newtonStep(double phi, int nf) {
        if (nf == 0) return phi;
        buildHessian(nf);
        double maxDiag = 0.0;
        for (int a = 0; a < nf; a++) maxDiag = Math.max(maxDiag, H[a][a]);
        if (maxDiag <= 0.0) return phi; // no positive curvature anywhere -> defer to the gradient step

        for (int retry = 0; retry < 8; retry++) {
            double damp = rhoRel * maxDiag;
            if (!choleskySolve(nf, damp)) { rhoRel = Math.min(rhoRel * 4.0, RHO_MAX); continue; }

            java.util.Arrays.fill(dir, 0.0);
            double dphi = 0.0;
            for (int a = 0; a < nf; a++) {
                int j = freeIdx[a];
                dir[j] = step[a];
                dphi += gradient[j] * step[a];
            }
            if (dphi < 0.0) {
                double t = 1.0;
                for (int ls = 0; ls < 22; ls++) {
                    for (int j = 0; j < m; j++) cand[j] = project(lambda[j] + t * dir[j], eq[j]);
                    double cphi = costate(cand, ngx, ngz);
                    if (cphi <= phi + GAMMA * t * dphi) {
                        // accepted: shrink damping if it took a full step (trust Newton more next time)
                        rhoRel = (t == 1.0) ? Math.max(rhoRel * 0.5, RHO_MIN) : Math.min(rhoRel * 2.0, RHO_MAX);
                        commit();
                        return cphi;
                    }
                    t *= 0.5;
                }
            }
            rhoRel = Math.min(rhoRel * 4.0, RHO_MAX); // step too aggressive: damp more and retry
        }
        return phi;
    }

    /** Projected-gradient step with an expand/backtrack line search along the feasible path
     *  {@code λ(t) = P(λ − t·α·∇D)}. Convex in t, so this always finds a decrease while one exists,
     *  including escaping the flat regions a Newton step cannot. Commits and returns the new dual value. */
    private double gradientStep(double phi) {
        double gInf = 0.0;
        for (int j = 0; j < m; j++) gInf = Math.max(gInf, Math.abs(gradient[j]));
        if (gInf == 0.0) return phi;
        double a = 1.0 / gInf; // scale so the first probe moves λ by O(1)

        double bestT = 0.0, bestPhi = phi;
        double t = 1.0;
        double pt = pathValue(a * t);
        if (pt < bestPhi) {
            bestT = t; bestPhi = pt;
            for (int e = 0; e < 40; e++) {     // expand while improving (reach far for flat-direction descent)
                double t2 = t * 2.0;
                double p2 = pathValue(a * t2);
                if (p2 < bestPhi) { t = t2; bestT = t2; bestPhi = p2; } else break;
            }
        } else {
            for (int b = 0; b < 40; b++) {     // backtrack until improving
                t *= 0.5;
                double pb = pathValue(a * t);
                if (pb < bestPhi) { bestT = t; bestPhi = pb; break; }
                if (t < 1.0e-18) break;
            }
        }
        if (bestT == 0.0) return phi;
        commitPath(a * bestT);
        return bestPhi;
    }

    /** D at the projected-gradient path point {@code P(λ − step·∇D)}, into {@code cand}/{@code ngx,ngz}. */
    private double pathValue(double s) {
        for (int j = 0; j < m; j++) cand[j] = project(lambda[j] - s * gradient[j], eq[j]);
        return costate(cand, ngx, ngz);
    }

    /** Adopt {@code cand}/{@code ngx,ngz} (a Newton candidate) as the new iterate and refresh the gradient. */
    private void commit() {
        System.arraycopy(cand, 0, lambda, 0, m);
        System.arraycopy(ngx, 0, gx, 0, n);
        System.arraycopy(ngz, 0, gz, 0, n);
        grad(gx, gz, gradient);
    }

    /** Adopt the projected-gradient path point at step {@code s} as the new iterate and refresh everything. */
    private void commitPath(double s) {
        for (int j = 0; j < m; j++) lambda[j] = project(lambda[j] - s * gradient[j], eq[j]);
        costate(lambda, gx, gz);
        grad(gx, gz, gradient);
    }

    // ---- math ---------------------------------------------------------------------------------------

    private static double project(double v, boolean isEq) {
        return isEq ? v : (v < 0.0 ? 0.0 : v);
    }

    private double hAxis(double[] lam, int a) {
        double h = a == 0 ? freeP0.objDevX : freeP0.objDevZ;
        for (int j = 0; j < m; j++) {
            if (axis[j] == a) h += lam[j] * p0coef[j];
            else if (axis[j] == 2) h += lam[j] * (a == 0 ? p0coefX[j] : p0coefZ[j]);
        }
        return h;
    }

    private double supportOf(double h, int a) {
        double d = deltaOf(h, a);
        return h * d - 0.5 * freeP0.smooth * d * d;
    }

    private double deltaOf(double h, int a) {
        double lo = a == 0 ? freeP0.dvLoX : freeP0.dvLoZ;
        double hi = a == 0 ? freeP0.dvHiX : freeP0.dvHiZ;
        double d = h / freeP0.smooth;
        return d < lo ? lo : (d > hi ? hi : d);
    }

    private double supportCurv(double h, int a) {
        double lo = a == 0 ? freeP0.dvLoX : freeP0.dvLoZ;
        double hi = a == 0 ? freeP0.dvHiX : freeP0.dvHiZ;
        double d = h / freeP0.smooth;
        return (d > lo && d < hi) ? 1.0 / freeP0.smooth : 0.0;
    }

    /** D(λ) = Σ_t m_t·sqrt(‖g_t‖^2+eps) + Σ_j λ_j b'_j, filling {@code outX,outZ} with the costates g_t. */
    private double costate(double[] lam, double[] outX, double[] outZ) {
        System.arraycopy(cx, 0, outX, 0, n);
        System.arraycopy(cz, 0, outZ, 0, n);
        for (int j = 0; j < m; j++) {
            double lj = lam[j];
            if (lj == 0.0) continue;
            if (axis[j] == 0) {
                double[] cj = coef[j];
                for (int t = 0; t < n; t++) outX[t] -= lj * cj[t];
            } else if (axis[j] == 1) {
                double[] cj = coef[j];
                for (int t = 0; t < n; t++) outZ[t] -= lj * cj[t];
            } else {
                double[] cxj = coefX[j];
                double[] czj = coefZ[j];
                if (cxj != null) for (int t = 0; t < n; t++) outX[t] -= lj * cxj[t];
                if (czj != null) for (int t = 0; t < n; t++) outZ[t] -= lj * czj[t];
            }
        }
        double d = 0.0;
        for (int t = 0; t < n; t++) d += mMag[t] * Math.sqrt(outX[t] * outX[t] + outZ[t] * outZ[t] + EPS2);
        for (int j = 0; j < m; j++) d += lam[j] * bPrime[j];
        if (freeP0 != null) d += supportOf(hAxis(lam, 0), 0) + supportOf(hAxis(lam, 1), 1);
        return d;
    }

    /** grad_j = b'_j − A_j·u*, with u*_t = m_t·g_t/‖g_t‖ (the recovered constraint slack, negated). */
    private void grad(double[] gX, double[] gZ, double[] out) {
        for (int t = 0; t < n; t++) {
            double nrm = Math.sqrt(gX[t] * gX[t] + gZ[t] * gZ[t] + EPS2);
            double w = mMag[t] / nrm;
            ux[t] = w * gX[t];
            uz[t] = w * gZ[t];
        }
        for (int j = 0; j < m; j++) {
            if (axis[j] < 2) {
                double[] cj = coef[j];
                double[] u = (axis[j] == 0) ? ux : uz;
                double dot = 0.0;
                for (int t = 0; t < n; t++) dot += cj[t] * u[t];
                out[j] = bPrime[j] - dot;
            } else {
                double[] cxj = coefX[j];
                double[] czj = coefZ[j];
                double dot = 0.0;
                if (cxj != null) for (int t = 0; t < n; t++) dot += cxj[t] * ux[t];
                if (czj != null) for (int t = 0; t < n; t++) dot += czj[t] * uz[t];
                out[j] = bPrime[j] - dot;
            }
        }
        if (freeP0 != null) {
            double dsx = deltaOf(hAxis(lambda, 0), 0);
            double dsz = deltaOf(hAxis(lambda, 1), 1);
            for (int j = 0; j < m; j++) {
                if (axis[j] == 0) out[j] += p0coef[j] * dsx;
                else if (axis[j] == 1) out[j] += p0coef[j] * dsz;
                else out[j] += p0coefX[j] * dsx + p0coefZ[j] * dsz;
            }
        }
    }

    /** Free-set Hessian H_{ab} = Σ_t (m_t/‖g_t‖)·coef_i·coef_j·([axis equal] − ĝ_i·ĝ_j), i=free[a], j=free[b]. */
    private void buildHessian(int nf) {
        double curvX = 0.0;
        double curvZ = 0.0;
        if (freeP0 != null) {
            curvX = supportCurv(hAxis(lambda, 0), 0);
            curvZ = supportCurv(hAxis(lambda, 1), 1);
        }
        for (int t = 0; t < n; t++) {
            double gxx = gx[t], gzz = gz[t];
            double nrm = Math.sqrt(gxx * gxx + gzz * gzz + EPS2);
            wOverNrm[t] = mMag[t] / nrm;
            gxHat[t] = gxx / nrm;
            gzHat[t] = gzz / nrm;
        }
        for (int a = 0; a < nf; a++) {
            int i = freeIdx[a];
            int ai = axis[i];
            for (int b = a; b < nf; b++) {
                int j = freeIdx[b];
                int aj = axis[j];
                if (ai < 2 && aj < 2) {
                    double[] ci = coef[i];
                    double[] cj = coef[j];
                    boolean sameAxis = (ai == aj);
                    double[] hatI = ai == 0 ? gxHat : gzHat;
                    double[] hatJ = aj == 0 ? gxHat : gzHat;
                    double sum = 0.0;
                    for (int t = 0; t < n; t++) {
                        double cc = ci[t] * cj[t];
                        if (cc == 0.0) continue;
                        sum += wOverNrm[t] * cc * ((sameAxis ? 1.0 : 0.0) - hatI[t] * hatJ[t]);
                    }
                    if (freeP0 != null && sameAxis) {
                        sum += p0coef[i] * p0coef[j] * (ai == 0 ? curvX : curvZ);
                    }
                    H[a][b] = sum;
                    H[b][a] = sum;
                } else {
                    double[] cxi = coefX[i];
                    double[] czi = coefZ[i];
                    double[] cxj = coefX[j];
                    double[] czj = coefZ[j];
                    double sum = 0.0;
                    for (int t = 0; t < n; t++) {
                        double cxit = (cxi != null) ? cxi[t] : 0.0;
                        double czit = (czi != null) ? czi[t] : 0.0;
                        double cxjt = (cxj != null) ? cxj[t] : 0.0;
                        double czjt = (czj != null) ? czj[t] : 0.0;
                        if (cxit == 0.0 && czit == 0.0) continue;
                        if (cxjt == 0.0 && czjt == 0.0) continue;

                        double dot = cxit * cxjt + czit * czjt;
                        double hatI = cxit * gxHat[t] + czit * gzHat[t];
                        double hatJ = cxjt * gxHat[t] + czjt * gzHat[t];
                        sum += wOverNrm[t] * (dot - hatI * hatJ);
                    }
                    if (freeP0 != null) {
                        sum += p0coefX[i] * p0coefX[j] * curvX + p0coefZ[i] * p0coefZ[j] * curvZ;
                    }
                    H[a][b] = sum;
                    H[b][a] = sum;
                }
            }
        }
    }

    /** Solve {@code (H + damp·I)·step = −grad_free} for the free set via Cholesky. {@code H} is PSD so the
     *  damped matrix is positive definite for {@code damp>0}; returns false only on numerical breakdown. */
    private boolean choleskySolve(int nf, double damp) {
        for (int a = 0; a < nf; a++) {
            for (int b = 0; b <= a; b++) {
                double s = H[a][b] + (a == b ? damp : 0.0);
                for (int k = 0; k < b; k++) s -= Lwork[a][k] * Lwork[b][k];
                if (a == b) {
                    if (s <= 0.0) return false;
                    Lwork[a][a] = Math.sqrt(s);
                } else {
                    Lwork[a][b] = s / Lwork[b][b];
                }
            }
            step[a] = -gradient[freeIdx[a]];
        }
        for (int a = 0; a < nf; a++) {
            double s = step[a];
            for (int k = 0; k < a; k++) s -= Lwork[a][k] * step[k];
            step[a] = s / Lwork[a][a];
        }
        for (int a = nf - 1; a >= 0; a--) {
            double s = step[a];
            for (int k = a + 1; k < nf; k++) s -= Lwork[k][a] * step[k];
            step[a] = s / Lwork[a][a];
        }
        return true;
    }
}
