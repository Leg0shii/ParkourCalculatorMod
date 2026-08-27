package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;

/** The exact linear structure of the horizontal sprint-jump, extracted once per {@link JumpSpec}.
 *
 *  <p>Horizontal motion is linear in each tick's input vector. The game adds, at tick {@code t}, a
 *  velocity contribution that is a vector of FIXED magnitude {@code m_t} (full sprint input: air-accel,
 *  ground accel, plus the 0.2 sprint-jump boost on the jump tick) rotated to any direction the yaw picks.
 *  Writing that input as the complex number {@code u_t = (q_t + i p_t)·e^{i θ_t}} (so {@code addX + i addZ})
 *  and unrolling the friction recurrence {@code v_{t+1} = (v_t + u_t)·f4_t} gives, for every tick {@code k},
 *  a closed-form affine map
 *  <pre>   pos_k = p0_k + Σ_{s&lt;k} C(s,k) · u_s ,   C(s,k) = (S[k]-S[s]) / fPre[s]   </pre>
 *  where {@code fPre} is the prefix product of the per-tick friction {@code f4} and {@code S} its prefix
 *  sum. {@code p0_k} folds in the start position and the decaying initial velocity. Y is fully decoupled
 *  (no input touches it) and handled by the forward models, so only X/Z live here.
 *
 *  <p>So the objective {@code d·pos_tick} and every axis wall {@code a·pos_k ≤ b} are LINEAR in the input
 *  vectors {@code u_s}; the only nonconvexity is the per-tick fixed modulus {@code |u_t| = m_t}. This class
 *  builds the objective gradient vectors {@code c_t} and compiles the walls into that linear form, and
 *  recovers the absolute yaw a desired input direction corresponds to. The momentum-cancellation clamp is
 *  piecewise-affine (for a fixed per-axis zeroing pattern the map stays affine, with each input's friction
 *  propagation cut at the first zeroing tick after it), so the pattern-aware constructor folds a given
 *  pattern into the couplings; the pattern-free constructor keeps the clamp-free model. */
public final class JumpLinearModel {

    private static final double RAD = Math.PI / 180.0;
    private static final double DEG = 180.0 / Math.PI;

    public final int n;
    private final JumpPhysicsInputs sc;

    private final double[] pConst;  // per-tick forward+jump input magnitude (the e^{iθ} is along +imag)
    private final double[] qConst;  // per-tick strafe input magnitude (along +real)
    private final double[] fwd;
    private final double[] boost;
    private final double[] mMag;    // |input_t| = hypot(pConst, qConst)  (constant modulus)
    private final double[] baseArg; // atan2(pConst, qConst): phase of the base input vector (q + i p)
    private final double[] f4;      // per-tick friction multiplier
    private final double[] fPre;    // fPre[k] = prod_{i<k} f4[i]
    private final double[] sPre;    // sPre[k] = sum_{t<k} fPre[t]

    private final boolean[][] zero;
    private final int[][] zNext;
    private final int[] zFirst;

    /** One wall compiled to {@code a·(Σ_s coef[s]·u_s) ≤ bPrime}, normalized so feasible == satisfied.
     *  {@code a} is the unit axis (X or Z); {@code coef[s]} is the friction coupling C(s,τ) with the
     *  GE/LE sign folded in. An equality keeps a free (sign-unconstrained) multiplier. */
    public static final class Wall {
        public final int axis;       // 0 = X, 1 = Z, 2 = 2D (both coefX and coefZ)
        public final double[] coefX;
        public final double[] coefZ;
        public final double[] coef;  // convenience: non-null axis coef for 1D, or coefX for 2D
        public final double bPrime;
        public final boolean eq;
        public final String name;
        public final double p0coefX;
        public final double p0coefZ;
        public final double p0coef;

        public Wall(int axis, double[] coef, double bPrime, boolean eq, String name, double p0coef) {
            this.axis = axis;
            this.coefX = (axis == 0) ? coef : null;
            this.coefZ = (axis == 1) ? coef : null;
            this.coef = coef;
            this.bPrime = bPrime;
            this.eq = eq;
            this.name = name;
            this.p0coefX = (axis == 0) ? p0coef : 0.0;
            this.p0coefZ = (axis == 1) ? p0coef : 0.0;
            this.p0coef = p0coef;
        }

        public Wall(double[] coefX, double[] coefZ, double bPrime, boolean eq, String name, double p0coefX, double p0coefZ) {
            this.axis = 2;
            this.coefX = coefX;
            this.coefZ = coefZ;
            this.coef = coefX != null ? coefX : coefZ;
            this.bPrime = bPrime;
            this.eq = eq;
            this.name = name;
            this.p0coefX = p0coefX;
            this.p0coefZ = p0coefZ;
            this.p0coef = (p0coefX != 0.0) ? p0coefX : p0coefZ;
        }
    }

    public JumpLinearModel(JumpPhysicsInputs scenario) {
        this(scenario, null, null);
    }

    public JumpLinearModel(JumpPhysicsInputs scenario, boolean[] zeroX, boolean[] zeroZ) {
        this.sc = scenario;
        this.n = scenario.numTicks;
        this.pConst = new double[n];
        this.qConst = new double[n];
        this.fwd = new double[n];
        this.boost = new double[n];
        this.mMag = new double[n];
        this.baseArg = new double[n];
        this.f4 = new double[n];
        this.fPre = new double[n + 1];
        this.sPre = new double[n + 1];
        precompute();
        this.zero = new boolean[2][];
        this.zero[0] = zeroX;
        this.zero[1] = zeroZ;
        this.zNext = new int[2][];
        this.zFirst = new int[]{n + 1, n + 1};
        for (int a = 0; a < 2; a++) {
            int[] nx = new int[n];
            int next = n + 1;
            for (int t = n - 1; t >= 0; t--) {
                nx[t] = next;
                if (zero[a] != null && zero[a][t]) next = t;
            }
            zNext[a] = nx;
            zFirst[a] = next;
        }
    }

    private void precompute() {
        for (int t = 0; t < n; t++) {
            // Ground/air + jump authored per tick, matching ExactJumpModel: a tick is grounded iff its slip
            // is annotated (NaN = airborne), and a JUMP fires only while grounded.
            double slipOv = sc.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            boolean isJump = sc.jumpAt(t) && contact;
            boolean sprint = sc.sprintAt(t);
            double slip = contact ? slipOv : Constants.SLIP_F;
            double accelSpeed;
            if (contact) {
                f4[t] = slip * 0.91;
                accelSpeed = Constants.attrValueF(sc.factorAmpAt(t), sprint) * (0.16277136 / (f4[t] * f4[t] * f4[t]));
            } else {
                f4[t] = 0.91;
                accelSpeed = sc.airFactorSprintAt(t) ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }
            // Same per-tick input authoring as ExactJumpModel step (4) (gh-102).
            double forward0 = sc.forwardAt(t);
            double strafe0 = (sc.strafeAt(t) && !isJump) ? sc.strafeSign * 0.98 : sc.strafeInputAt(t);
            double fm = strafe0 * strafe0 + forward0 * forward0;
            double fF = 0.0, sF = 0.0;
            if (fm >= 1.0e-4) {
                double raw = Math.sqrt(fm);
                if (raw < 1.0) raw = 1.0;
                double scale = accelSpeed / raw;
                fF = forward0 * scale;
                sF = strafe0 * scale;
            }
            fwd[t] = fF;
            boost[t] = (isJump && sprint) ? 0.2 : 0.0;
            pConst[t] = fF + boost[t];
            qConst[t] = sF;
            mMag[t] = Math.hypot(pConst[t], qConst[t]);
            baseArg[t] = Math.atan2(pConst[t], qConst[t]);
        }
        fPre[0] = 1.0;
        sPre[0] = 0.0;
        for (int t = 0; t < n; t++) {
            sPre[t + 1] = sPre[t] + fPre[t];
            fPre[t + 1] = fPre[t] * f4[t];
        }
    }

    public double mMag(int t) {
        return mMag[t];
    }

    public double forwardMag(int t) {
        return fwd[t];
    }

    public double strafeMag(int t) {
        return qConst[t];
    }

    public double boostAt(int t) {
        return boost[t];
    }

    /** Phase of the base input vector {@code (q + i p)} at tick t: the input added by a move at absolute yaw
     *  {@code y} (radians) is {@code mMag·e^{i(baseArg + y)}} = {@code (addX, addZ)}. So
     *  {@code d(addX)/dy = -addZ} and {@code d(addZ)/dy = addX}, which gives the analytic Jacobian of any
     *  X/Z position constraint wrt the per-tick facings (no forward needed). */
    public double baseArg(int t) {
        return baseArg[t];
    }

    public double friction(int t) {
        return f4[t];
    }

    /** The per-tick input magnitudes (constant moduli), shared read-only with the dual solver. */
    public double[] mMagAll() {
        return mMag;
    }

    /** Friction coupling C(s,k): the coefficient of input {@code u_s} in {@code pos_k} (0 for s &gt;= k). */
    public double coef(int s, int k) {
        if (s >= k) return 0.0;
        return (sPre[k] - sPre[s]) / fPre[s];
    }

    public double coefAxis(int axis, int s, int k) {
        if (s >= k) return 0.0;
        int stop = zNext[axis][s];
        int end = k < stop ? k : stop;
        return (sPre[end] - sPre[s]) / fPre[s];
    }

    /** Constant part of {@code pos_k} on the given axis: start position plus decayed initial velocity. */
    public double constPos(int k, int axis) {
        StartBox box = sc.startBox;
        double p0 = box != null ? (axis == 0 ? box.px : box.pz) : (axis == 0 ? sc.startPos.x : sc.startPos.z);
        double v0 = box != null ? (axis == 0 ? box.vx : box.vz) : (axis == 0 ? sc.initialVelocity.x : sc.initialVelocity.z);
        int end = k < zFirst[axis] ? k : zFirst[axis];
        return p0 + v0 * sPre[end];
    }

    /** Per-tick objective gradient {@code c_t}: the 2D vector whose dot with {@code u_t} is the tick's
     *  contribution to {@code objDir·pos_objTick}. objDir is the direction to MAXIMIZE (MIN is negated). */
    public void objectiveVectors(Objective obj, double[] cx, double[] cz) {
        int objTick = obj.tick;
        double dx, dz;
        boolean max = obj.sense == Objective.Sense.MAX;
        if (obj.axis == JumpPhysicsInputs.Axis.X) { dx = max ? 1.0 : -1.0; dz = 0.0; }
        else { dx = 0.0; dz = max ? 1.0 : -1.0; }
        for (int t = 0; t < n; t++) {
            cx[t] = coefAxis(0, t, objTick) * dx;
            cz[t] = coefAxis(1, t, objTick) * dz;
        }
    }

    /** Compile a position wall into the linear {@code a·Σ coef·u ≤ bPrime} form, tightened inward by
     *  {@code margin} (so the sine-table quantization keeps the exact model on the feasible side). Returns
     *  {@code null} for a constraint with no decision dependence (tick 0, or t1==t2): such a constraint is a
     *  constant, reported via {@code trivialInfeasible} when the constant itself violates it. F-mode (facing)
     *  and DXZ (cross-axis magnitude) walls are not linear in the inputs and are rejected (caller falls back). */
    public static boolean hasCrossAxis(List<JumpConstraint> constraints) {
        for (JumpConstraint c : constraints) {
            if (c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) return true;
        }
        return false;
    }

    public void addCrossAxisWalls(List<Wall> out, JumpConstraint c, int dominantSign, double margin) {
        if (c.t2 == null) return;
        boolean wantZDominant = (c.mode == JumpConstraint.Mode.DZX && c.cmp == JumpConstraint.Cmp.GE)
                || (c.mode == JumpConstraint.Mode.DXZ && c.cmp == JumpConstraint.Cmp.LE);
        double s = dominantSign >= 0 ? 1.0 : -1.0;
        if (wantZDominant) {
            out.add(compileCrossAxisWall(c.t1, c.t2, -1.0, s, c.rhs, margin, c.name + ".1"));
            out.add(compileCrossAxisWall(c.t1, c.t2, 1.0, s, c.rhs, margin, c.name + ".2"));
        } else {
            out.add(compileCrossAxisWall(c.t1, c.t2, s, -1.0, c.rhs, margin, c.name + ".1"));
            out.add(compileCrossAxisWall(c.t1, c.t2, s, 1.0, c.rhs, margin, c.name + ".2"));
        }
    }

    public Wall compileCrossAxisWall(int t1, Integer t2, double signX, double signZ, double rhs,
                                     double margin, String name) {
        if (t2 == null) return null;
        double constX = constPos(t1, 0) - constPos(t2, 0);
        double constZ = constPos(t1, 1) - constPos(t2, 1);
        double constVal = signX * constX + signZ * constZ;

        double[] cx = new double[n];
        double[] cz = new double[n];
        for (int s = 0; s < n; s++) {
            double kx = coefAxis(0, s, t1) - coefAxis(0, s, t2);
            double kz = coefAxis(1, s, t1) - coefAxis(1, s, t2);
            cx[s] = -signX * kx;
            cz[s] = -signZ * kz;
        }
        double bPrime = constVal - rhs - margin;
        return new Wall(cx, cz, bPrime, false, name, 0.0, 0.0);
    }

    public Wall compileWall(JumpConstraint c, double margin, boolean[] trivialInfeasible) {
        if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) return null;
        int axis = (c.mode == JumpConstraint.Mode.X) ? 0 : 1;
        int t1 = c.t1;
        Integer t2 = c.t2;
        double opSign = (c.op == JumpConstraint.Op.PLUS) ? 1.0 : -1.0;

        double[] coef = new double[n];
        for (int s = 0; s < n; s++) {
            double k = coefAxis(axis, s, t1);
            if (t2 != null) k += opSign * coefAxis(axis, s, t2);
            coef[s] = k;
        }
        double constVal = constPos(t1, axis);
        if (t2 != null) constVal += opSign * constPos(t2, axis);

        // value = constVal + Σ coef·(a·u).  Normalize each cmp to  Σ coef'·(a·u) ≤ bPrime.
        boolean eq = (c.cmp == JumpConstraint.Cmp.EQ);
        double bPrime;
        if (c.cmp == JumpConstraint.Cmp.GE) {
            for (int s = 0; s < n; s++) coef[s] = -coef[s];
            bPrime = constVal - c.rhs;            //  value >= rhs  ->  -Σcoef·au <= constVal - rhs
        } else {
            bPrime = c.rhs - constVal;            //  value <= rhs  ->   Σcoef·au <= rhs - constVal
        }
        if (!eq) bPrime -= margin;                // hug the wall this far inside

        int tc = (t2 == null) ? 1 : (opSign > 0.0 ? 2 : 0);
        double p0coef = (c.cmp == JumpConstraint.Cmp.GE) ? tc : -tc;

        boolean trivial = true;
        for (int s = 0; s < n; s++) if (coef[s] != 0.0) { trivial = false; break; }
        if (trivial) {
            // No decision dependence (tick 0, or t1==t2): the constraint is the constant 0 <= bPrime
            // (or, for EQ, |bPrime| == 0). Compare against the pre-margin bound; flag if the constant
            // itself violates it (nothing the solver does can fix a constant). The check is EXACT,
            // matching the byte-exact FEAS_TOL=0 gate downstream (constPos(0) is the seed position to
            // the bit, and the same subtraction the compiler's evaluate() performs): any grace here only
            // delays the inevitable "no solution" past a full ladder + fallback burn.
            double rawBound = eq ? bPrime : bPrime + margin;
            boolean ok = eq ? rawBound == 0.0 : rawBound >= 0.0;
            if (!ok && trivialInfeasible != null) trivialInfeasible[0] = true;
            return null;
        }
        return new Wall(axis, coef, bPrime, eq, c.name, p0coef);
    }

    /** Compile all walls of a spec; sets {@code trivialInfeasible[0]} if a constant constraint is violated. */
    public List<Wall> compileWalls(List<JumpConstraint> constraints, double margin, boolean[] trivialInfeasible) {
        return compileWalls(constraints, margin, trivialInfeasible, 1);
    }

    public List<Wall> compileWalls(List<JumpConstraint> constraints, double margin, boolean[] trivialInfeasible, int dominantSign) {
        List<Wall> walls = new ArrayList<>();
        for (JumpConstraint c : constraints) {
            if (c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) {
                addCrossAxisWalls(walls, c, dominantSign, margin);
                continue;
            }
            Wall w = compileWall(c, margin, trivialInfeasible);
            if (w != null) walls.add(w);
        }
        return walls;
    }

    public List<Wall> velocityWalls(double bound) {
        List<Wall> walls = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            if (zero[a] == null) continue;
            for (int t = 0; t < n; t++) {
                if (!zero[a][t]) continue;
                double[] coefHi = new double[n];
                boolean any = false;
                for (int s = 0; s < t; s++) {
                    if (zNext[a][s] < t) continue;
                    coefHi[s] = fPre[t] / fPre[s];
                    any = true;
                }
                if (!any) continue;
                double v0 = a == 0 ? sc.initialVelocity.x : sc.initialVelocity.z;
                double constVal = zFirst[a] < t ? 0.0 : v0 * fPre[t];
                double[] coefLo = new double[n];
                for (int s = 0; s < t; s++) coefLo[s] = -coefHi[s];
                String ax = a == 0 ? "X" : "Z";
                walls.add(new Wall(a, coefHi, bound - constVal, false, "inertia" + ax + "@" + t + "+", 0.0));
                walls.add(new Wall(a, coefLo, bound + constVal, false, "inertia" + ax + "@" + t + "-", 0.0));
            }
        }
        return walls;
    }

    /** The complement of a {@link #velocityWalls} pair: one half-space keeping {@code v_axis(tick)} outside
     *  the inertia band, on the {@code positive} side ({@code v >= bound}) or the negative one
     *  ({@code v <= -bound}). Null when no input reaches the tick. */
    public Wall keepAliveWall(int axis, int tick, double bound, boolean positive) {
        if (tick <= 0 || tick >= n) return null;
        double[] coef = new double[n];
        boolean any = false;
        for (int s = 0; s < tick; s++) {
            if (zNext[axis][s] < tick) continue;
            coef[s] = positive ? -(fPre[tick] / fPre[s]) : (fPre[tick] / fPre[s]);
            any = true;
        }
        if (!any) return null;
        double v0 = axis == 0 ? sc.initialVelocity.x : sc.initialVelocity.z;
        double constVal = zFirst[axis] < tick ? 0.0 : v0 * fPre[tick];
        double bPrime = positive ? constVal - bound : -bound - constVal;
        String ax = axis == 0 ? "X" : "Z";
        return new Wall(axis, coef, bPrime, false, "keep" + ax + "@" + tick + (positive ? "+" : "-"), 0.0);
    }

    public void zeroingPattern(double[] yawsAbsWrapped, double threshold, boolean perAxis,
                               boolean[] outZeroX, boolean[] outZeroZ) {
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        double thrSq = threshold * threshold;
        for (int t = 0; t < n; t++) {
            boolean zx = false, zz = false;
            if (perAxis) {
                if (Math.abs(vx) < threshold) { vx = 0.0; zx = true; }
                if (Math.abs(vz) < threshold) { vz = 0.0; zz = true; }
            } else {
                if (vx * vx + vz * vz < thrSq) { vx = 0.0; vz = 0.0; zx = true; zz = true; }
            }
            outZeroX[t] = zx;
            outZeroZ[t] = zz;
            double phi = baseArg[t] + yawsAbsWrapped[t] * RAD;
            vx += mMag[t] * Math.cos(phi);
            vz += mMag[t] * Math.sin(phi);
            vx *= f4[t];
            vz *= f4[t];
        }
    }

    /** True if any wall is an F-mode (facing) constraint, which this linear model cannot represent. */
    public static boolean hasFacingWall(List<JumpConstraint> constraints) {
        for (JumpConstraint c : constraints) if (c.mode == JumpConstraint.Mode.F) return true;
        return false;
    }

    /** Absolute yaw (deg) whose input vector points along {@code (gx,gz)}: from
     *  {@code addX + i addZ = (q + i p)·e^{iθ}} we get {@code θ = arg(g) - arg(q + i p)}. Direction only;
     *  the magnitude is fixed by the physics. A vanishing costate (undetermined direction) is left to the
     *  caller's default. */
    public double recoverYawDeg(int t, double gx, double gz) {
        return Angles.wrap((Math.atan2(gz, gx) - baseArg[t]) * DEG);
    }
}
