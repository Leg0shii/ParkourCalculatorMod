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
 *  omitted: it only fires when a path's along-axis speed grazes MC's ~0.005 threshold, a borderline,
 *  float-sensitive event that even a clamp-aware linear model cannot place reliably. The continuous optimum
 *  is therefore solved clamp-free and the {@link ExactJumpModel} re-check downstream (with a small inward
 *  margin) is the source of truth, so any unmodeled clamp costs at most a sliver of objective, never
 *  feasibility. */
public final class JumpLinearModel {

    private static final double RAD = Math.PI / 180.0;
    private static final double DEG = 180.0 / Math.PI;

    public final int n;
    private final JumpPhysicsInputs sc;

    private final double[] pConst;  // per-tick forward+jump input magnitude (the e^{iθ} is along +imag)
    private final double[] qConst;  // per-tick strafe input magnitude (along +real)
    private final double[] mMag;    // |input_t| = hypot(pConst, qConst)  (constant modulus)
    private final double[] baseArg; // atan2(pConst, qConst): phase of the base input vector (q + i p)
    private final double[] fPre;    // fPre[k] = prod_{i<k} f4[i]
    private final double[] sPre;    // sPre[k] = sum_{t<k} fPre[t]

    /** One wall compiled to {@code a·(Σ_s coef[s]·u_s) ≤ bPrime}, normalized so feasible == satisfied.
     *  {@code a} is the unit axis (X or Z); {@code coef[s]} is the friction coupling C(s,τ) with the
     *  GE/LE sign folded in. An equality keeps a free (sign-unconstrained) multiplier. */
    public static final class Wall {
        public final int axis;       // 0 = X, 1 = Z
        public final double[] coef;  // length n; A_{j,s} = coef[s] * unit(axis)
        public final double bPrime;
        public final boolean eq;
        public final String name;

        Wall(int axis, double[] coef, double bPrime, boolean eq, String name) {
            this.axis = axis;
            this.coef = coef;
            this.bPrime = bPrime;
            this.eq = eq;
            this.name = name;
        }
    }

    public JumpLinearModel(JumpPhysicsInputs scenario) {
        this.sc = scenario;
        this.n = scenario.numTicks;
        this.pConst = new double[n];
        this.qConst = new double[n];
        this.mMag = new double[n];
        this.baseArg = new double[n];
        this.fPre = new double[n + 1];
        this.sPre = new double[n + 1];
        precompute();
    }

    private void precompute() {
        double[] f4 = new double[n];
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
                accelSpeed = Constants.attrValueF(sc.speedAmplifierAt(t), sprint) * (0.16277136 / (f4[t] * f4[t] * f4[t]));
            } else {
                f4[t] = 0.91;
                accelSpeed = sprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
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
            pConst[t] = fF + (isJump && sprint ? 0.2 : 0.0);
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

    /** Phase of the base input vector {@code (q + i p)} at tick t: the input added by a move at absolute yaw
     *  {@code y} (radians) is {@code mMag·e^{i(baseArg + y)}} = {@code (addX, addZ)}. So
     *  {@code d(addX)/dy = -addZ} and {@code d(addZ)/dy = addX}, which gives the analytic Jacobian of any
     *  X/Z position constraint wrt the per-tick facings (no forward needed). */
    public double baseArg(int t) {
        return baseArg[t];
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

    /** Constant part of {@code pos_k} on the given axis: start position plus decayed initial velocity. */
    public double constPos(int k, int axis) {
        double p0 = axis == 0 ? sc.startPos.x : sc.startPos.z;
        double v0 = axis == 0 ? sc.initialVelocity.x : sc.initialVelocity.z;
        return p0 + v0 * sPre[k];
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
            double c = coef(t, objTick);
            cx[t] = c * dx;
            cz[t] = c * dz;
        }
    }

    /** Compile a position wall into the linear {@code a·Σ coef·u ≤ bPrime} form, tightened inward by
     *  {@code margin} (so the sine-table quantization keeps the exact model on the feasible side). Returns
     *  {@code null} for a constraint with no decision dependence (tick 0, or t1==t2): such a constraint is a
     *  constant, reported via {@code trivialInfeasible} when the constant itself violates it. F-mode (facing)
     *  walls are not linear in the inputs and are rejected (caller falls back). */
    public Wall compileWall(JumpConstraint c, double margin, boolean[] trivialInfeasible) {
        if (c.mode == JumpConstraint.Mode.F) return null; // facing constraints are not position-linear
        int axis = (c.mode == JumpConstraint.Mode.X) ? 0 : 1;
        int t1 = c.t1;
        Integer t2 = c.t2;
        double opSign = (c.op == JumpConstraint.Op.PLUS) ? 1.0 : -1.0;

        double[] coef = new double[n];
        for (int s = 0; s < n; s++) {
            double k = coef(s, t1);
            if (t2 != null) k += opSign * coef(s, t2);
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
        return new Wall(axis, coef, bPrime, eq, c.name);
    }

    /** Compile all walls of a spec; sets {@code trivialInfeasible[0]} if a constant constraint is violated. */
    public List<Wall> compileWalls(List<JumpConstraint> constraints, double margin, boolean[] trivialInfeasible) {
        List<Wall> walls = new ArrayList<>();
        for (JumpConstraint c : constraints) {
            Wall w = compileWall(c, margin, trivialInfeasible);
            if (w != null) walls.add(w);
        }
        return walls;
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
