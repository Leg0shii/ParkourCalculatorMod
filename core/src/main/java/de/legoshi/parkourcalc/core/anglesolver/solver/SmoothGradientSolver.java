package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Analytic-Jacobian feasibility solver on the {@link SmoothJumpModel}. The smooth horizontal motion is
 *  linear in each tick's rotated input vector, so the position Jacobian d(pos_k)/d(yaw_t) factors through
 *  the per-tick friction products and is built in closed form -- no finite differences. A
 *  Levenberg-Marquardt restoration step drives the violated (and nearly-violated) wall residuals to their
 *  feasible side; LM threads the narrow corridors that steepest descent on a quadratic penalty stalls in.
 *  Deterministic basin-hopping restarts cover the nonconvexity. Y is decoupled (no input touches it), so
 *  only X/Z enter the Jacobian.
 *
 *  <p>This only needs a feasible (margin) point; the caller secures byte-exact feasibility and the
 *  objective with {@link BucketAscentPolish}. The momentum-cancellation clamp is ignored in the Jacobian
 *  (horizontal sprint-jump speeds never reach the ~0.005 threshold); the actual path is read from
 *  {@link SmoothJumpModel#forward} so violations stay consistent with that model. */
public final class SmoothGradientSolver {

    private static final double RAD = Math.PI / 180.0;
    private static final int MAX_ITER = 45;
    private static final int RESTARTS = 4;
    /** Default smoothness regularization weight (per degree of tick-to-tick yaw change). */
    public static final double DEFAULT_SMOOTH_W = 0.0006;

    private final SmoothJumpModel model;
    private final JumpPhysicsInputs sc;
    private final int n;
    private final double[] f4;     // per-tick friction multiply
    private final double[] fPre;   // prefix product fPre[j] = prod_{i<j} f4[i]
    private final double[] pConst; // per-tick forward+jump magnitude (rotated by yaw)
    private final double[] qConst; // per-tick strafe magnitude (rotated by yaw)
    private final double smoothWBase; // smoothness regularization weight (phase 1)
    private double smoothW;            // active weight (0 in phase 2)

    public SmoothGradientSolver(SmoothJumpModel model, JumpPhysicsInputs sc) {
        this(model, sc, DEFAULT_SMOOTH_W);
    }

    public SmoothGradientSolver(SmoothJumpModel model, JumpPhysicsInputs sc, double smoothW) {
        this.model = model;
        this.sc = sc;
        this.smoothWBase = smoothW;
        this.n = sc.numTicks;
        this.f4 = new double[n];
        this.fPre = new double[n + 1];
        this.pConst = new double[n];
        this.qConst = new double[n];
        precompute();
    }

    private void precompute() {
        for (int t = 0; t < n; t++) {
            boolean isJump = (sc.jumpTick >= 0 && t == sc.jumpTick);
            boolean onGround = (sc.jumpTick >= 0 && t <= sc.jumpTick);
            double slipOv = sc.slipAt(t);
            boolean hasSurface = !Double.isNaN(slipOv);
            boolean contact = onGround || hasSurface;
            double slip = hasSurface ? slipOv : Constants.SLIP_F;
            double accelSpeed;
            if (contact) {
                f4[t] = slip * 0.91;
                accelSpeed = Constants.attrValueF(sc.speedAmplifierAt(t)) * (0.16277136 / (f4[t] * f4[t] * f4[t]));
            } else {
                f4[t] = 0.91;
                accelSpeed = Constants.AIR_SPEED_F;
            }
            double forward0 = 0.98;
            double strafe0 = (sc.strafeAt(t) && !isJump) ? sc.strafeSign * 0.98 : 0.0;
            double fm = strafe0 * strafe0 + forward0 * forward0;
            double fF = 0.0, sF = 0.0;
            if (fm >= 1.0e-4) {
                double raw = Math.sqrt(fm);
                if (raw < 1.0) raw = 1.0;
                double scale = accelSpeed / raw;
                fF = forward0 * scale;
                sF = strafe0 * scale;
            }
            pConst[t] = fF + (isJump ? 0.2 : 0.0);
            qConst[t] = sF;
        }
        fPre[0] = 1.0;
        for (int j = 0; j < n; j++) fPre[j + 1] = fPre[j] * f4[j];
    }

    /** Drive the (margin-tightened) walls to their feasible side from {@code startAbs}; returns wrapped
     *  absolute facings. Nonconvex, so LM restoration is wrapped in deterministic basin-hopping. */
    public double[] solve(List<JumpConstraint> ineqWithMargin, Objective objective, double[] startAbs) {
        Random rng = new Random(0x9E3779B9L ^ n);

        double[] cur = startAbs.clone();
        restore(cur, ineqWithMargin);
        double bestViol = maxViolation(cur, ineqWithMargin);
        double[] best = cur.clone();

        for (int restart = 0; restart < RESTARTS && bestViol > 0.0; restart++) {
            double[] trial = best.clone();
            double mag = 15.0 + 90.0 * rng.nextDouble();
            for (int t = 0; t < n; t++) trial[t] += (rng.nextDouble() * 2.0 - 1.0) * mag;
            restore(trial, ineqWithMargin);
            double v = maxViolation(trial, ineqWithMargin);
            if (v < bestViol) {
                bestViol = v;
                best = trial.clone();
            }
        }
        return Angles.wrapAll(best);
    }

    /** In-place Levenberg-Marquardt feasibility restoration: repeatedly take an LM step on the active
     *  (violated / nearly-violated) wall residuals until feasible or no step reduces the squared violation. */
    private void restore(double[] theta, List<JumpConstraint> ineq) {
        double lambda = 1.0e-2;
        // Phase 1 (smoothing on) steers into the smooth-ramp basin; phase 2 (smoothing off) is pure
        // Gauss-Newton feasibility restoration that converges to zero violation from within that basin.
        int phase1 = MAX_ITER / 2;
        smoothW = smoothWBase;
        double phi = combinedObjective(theta, ineq);
        for (int it = 0; it < MAX_ITER; it++) {
            if (maxViolation(theta, ineq) <= 0.0) return;
            double newW = (it < phase1) ? smoothWBase : 0.0;
            if (newW != smoothW) {
                smoothW = newW;
                phi = combinedObjective(theta, ineq); // objective changed; rebase
            }

            ForwardPath p = forward(theta);
            // Restore only the violated walls toward their boundary. Including already-satisfied walls as
            // equality residuals would pull them onto the wall and cause chatter; satisfied walls are left
            // free and only re-enter if a step pushes them across.
            List<JumpConstraint> active = new ArrayList<>();
            for (JumpConstraint c : ineq) {
                if (signedGap(c, p) > 0.0) active.add(c);
            }
            if (active.isEmpty()) return;

            // Rows: one per active wall, then n-1 smoothness rows penalizing tick-to-tick yaw change. The
            // problem has more yaw DOF than active walls, so without smoothing LM wanders the nullspace into
            // jagged, highly-nonlinear sequences where the linearization is poor; real solutions are smooth
            // yaw ramps, and this regularization steers the solve into that well-conditioned basin.
            int m = active.size();
            int rows = m + (n - 1);
            double[][] J = new double[rows][n];
            double[] r = new double[rows];
            double[] dAddX = new double[n];
            double[] dAddZ = new double[n];
            for (int t = 0; t < n; t++) {
                double a = theta[t] * RAD;
                double cos = Math.cos(a), sin = Math.sin(a);
                dAddX[t] = (-qConst[t] * sin - pConst[t] * cos) * RAD;
                dAddZ[t] = (qConst[t] * cos - pConst[t] * sin) * RAD;
            }
            for (int i = 0; i < m; i++) {
                JumpConstraint c = active.get(i);
                r[i] = signedGap(c, p); // want <= 0; drive toward 0
                // d(signedGap)/d(theta_t): GE gap = rhs - value -> -d(value); LE gap = value - rhs -> +d(value).
                double cmpSign = (c.cmp == JumpConstraint.Cmp.GE) ? -1.0 : 1.0;
                fillValueJacobian(c, dAddX, dAddZ, cmpSign, J[i]);
            }
            for (int t = 1; t < n; t++) {
                int row = m + (t - 1);
                double diff = theta[t] - theta[t - 1];
                r[row] = smoothW * Angles.wrapDelta(diff);
                J[row][t] = smoothW;
                J[row][t - 1] = -smoothW;
            }

            // Solve (J^T J + lambda I) d = -J^T r, with LM lambda adaptation.
            RealMatrix jm = new Array2DRowRealMatrix(J, false);
            RealMatrix jtj = jm.transpose().multiply(jm);
            RealVector jtr = jm.transpose().operate(new ArrayRealVector(r, false));

            boolean stepped = false;
            for (int tries = 0; tries < 6; tries++) {
                RealMatrix lhs = jtj.copy();
                for (int d = 0; d < n; d++) lhs.addToEntry(d, d, lambda * (1.0 + jtj.getEntry(d, d)));
                DecompositionSolver solver = new LUDecomposition(lhs).getSolver();
                if (!solver.isNonSingular()) { lambda *= 4.0; continue; }
                RealVector step = solver.solve(jtr).mapMultiply(-1.0);

                double[] cand = theta.clone();
                for (int t = 0; t < n; t++) cand[t] += step.getEntry(t);
                double candPhi = combinedObjective(cand, ineq);
                if (candPhi < phi) {
                    System.arraycopy(cand, 0, theta, 0, n);
                    phi = candPhi;
                    lambda = Math.max(lambda * 0.5, 1.0e-9);
                    stepped = true;
                    break;
                }
                lambda *= 4.0;
            }
            if (!stepped) return;
        }
    }

    /** d(value)/d(theta_t) * cmpSign into row, where value = pos_axis[t1] (+/- pos_axis[t2]). */
    private void fillValueJacobian(JumpConstraint c, double[] dAddX, double[] dAddZ, double cmpSign, double[] row) {
        boolean x = (c.mode == JumpConstraint.Mode.X);
        double[] dAdd = x ? dAddX : dAddZ;
        accumValueJacobian(dAdd, c.t1, cmpSign, row);
        if (c.t2 != null) {
            double opSign = (c.op == JumpConstraint.Op.PLUS) ? 1.0 : -1.0;
            accumValueJacobian(dAdd, c.t2, cmpSign * opSign, row);
        }
    }

    /** Add w * d(pos_axis[k])/d(theta_t) to row[t] for all t < k. d(pos_k)/d(theta_t) = dAdd_t * S(t,k),
     *  S(t,k) = prod_{i=t..k-2} f4 = fPre[k-1]/fPre[t]. */
    private void accumValueJacobian(double[] dAdd, int k, double w, double[] row) {
        if (k <= 0) return;
        double fk = fPre[k - 1];
        for (int t = 0; t < k && t < n; t++) {
            row[t] += w * dAdd[t] * (fk / fPre[t]);
        }
    }

    private ForwardPath forward(double[] theta) {
        return model.forward(sc, sc.toGameFacings(Angles.wrapAll(theta)));
    }

    /** Signed gap g(theta): g <= 0 means feasible. GE: rhs - value. LE: value - rhs. */
    private double signedGap(JumpConstraint c, ForwardPath p) {
        double v = value(c, p);
        if (c.cmp == JumpConstraint.Cmp.GE) return c.rhs - v;
        if (c.cmp == JumpConstraint.Cmp.LE) return v - c.rhs;
        return Math.abs(v - c.rhs);
    }

    /** Least-squares objective LM minimizes: squared violation of violated walls + squared smoothness. */
    private double combinedObjective(double[] theta, List<JumpConstraint> ineq) {
        ForwardPath p = forward(theta);
        double s = 0.0;
        for (JumpConstraint c : ineq) {
            double g = signedGap(c, p);
            if (g > 0) s += g * g;
        }
        for (int t = 1; t < n; t++) {
            double d = smoothW * Angles.wrapDelta(theta[t] - theta[t - 1]);
            s += d * d;
        }
        return s;
    }

    private double maxViolation(double[] theta, List<JumpConstraint> ineq) {
        ForwardPath p = forward(theta);
        double m = 0.0;
        for (JumpConstraint c : ineq) m = Math.max(m, Math.max(0.0, signedGap(c, p)));
        return m;
    }

    private static double value(JumpConstraint c, ForwardPath p) {
        double[] pos = (c.mode == JumpConstraint.Mode.X) ? p.posX : p.posZ;
        double v = pos[c.t1];
        if (c.t2 != null) v += (c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0) * pos[c.t2];
        return v;
    }
}
