package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Smooth (continuous) twin of {@link ExactJumpModel}: the identical MC sprint-jump recurrence, but
 *  evaluated in double precision with real {@link Math#sin}/{@link Math#cos} instead of MC's 65536-bucket
 *  float sine table. Dropping the quantization turns the objective+constraint landscape from a step
 *  function with disconnected feasible islands into a single smooth basin, so a warm-started local search
 *  finds the wall-hugging solution in a handful of evaluations rather than the global multistart the
 *  byte-exact model forces.
 *
 *  <p>It is NOT byte-exact: each sin/cos differs from the table by up to ~1e-4, accumulating to ~1e-3 in
 *  landing position over a full arc. It is only used to locate the basin; feasibility is always re-checked
 *  and secured on the real {@link ExactJumpModel} afterwards. Mirror the exact model's per-version inertia
 *  rule so the same ticks zero out. */
public final class SmoothJumpModel implements ForwardModel {

    private final double inertiaThreshold;
    private final boolean perAxisInertia;

    public SmoothJumpModel(double inertiaThreshold, boolean perAxisInertia) {
        this.inertiaThreshold = inertiaThreshold;
        this.perAxisInertia = perAxisInertia;
    }

    /** Build the smooth twin matching an exact model's version configuration. */
    public static SmoothJumpModel like(ExactJumpModel exact) {
        return new SmoothJumpModel(exact.inertiaThreshold(), exact.perAxisInertia());
    }

    @Override
    public ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg) {
        int n = yawAbsDeg.length;
        double[] posX = new double[n + 1];
        double[] posY = new double[n + 1];
        double[] posZ = new double[n + 1];

        posX[0] = scenario.startPos.x;
        posY[0] = scenario.startPos.y;
        posZ[0] = scenario.startPos.z;
        double vx = scenario.initialVelocity.x;
        double vy = scenario.initialVelocity.y;
        double vz = scenario.initialVelocity.z;

        double thr = inertiaThreshold;
        double rad = Math.PI / 180.0;

        for (int t = 0; t < n; t++) {
            // (1) momentum cancellation on the post-friction carry from last tick.
            if (perAxisInertia) {
                if (Math.abs(vx) < thr) vx = 0.0;
                if (Math.abs(vy) < thr) vy = 0.0;
                if (Math.abs(vz) < thr) vz = 0.0;
            } else {
                if (vx * vx + vz * vz < thr * thr) { vx = 0.0; vz = 0.0; }
                if (Math.abs(vy) < thr) vy = 0.0;
            }

            double yaw = yawAbsDeg[t];
            boolean isJumpTick = (scenario.jumpTick >= 0 && t == scenario.jumpTick);
            boolean onGround = (scenario.jumpTick >= 0 && t <= scenario.jumpTick);

            // (2) jump impulse + sprint boost (real trig, doubles).
            if (isJumpTick) {
                vy = Constants.JUMP_VEL_F;
                double fj = yaw * rad;
                vx -= Math.sin(fj) * 0.2;
                vz += Math.cos(fj) * 0.2;
            }

            // (3) accel regime.
            int amp = scenario.speedAmplifierAt(t);
            double slipOv = scenario.slipAt(t);
            boolean hasSurface = !Double.isNaN(slipOv);
            boolean contact = onGround || hasSurface;
            double slip = hasSurface ? slipOv : Constants.SLIP_F;

            double f4;
            double accelSpeed;
            if (contact) {
                f4 = slip * 0.91;
                double ground = 0.16277136 / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp) * ground;
            } else {
                f4 = 0.91;
                accelSpeed = Constants.AIR_SPEED_F;
            }

            // (4) moveFlying(strafe, forward, accelSpeed).
            double forward = 1.0 * 0.98;
            double strafe = (scenario.strafeAt(t) && !isJumpTick) ? scenario.strafeSign * 1.0 * 0.98 : 0.0;
            double fm = strafe * strafe + forward * forward;
            if (fm >= 1.0e-4) {
                fm = Math.sqrt(fm);
                if (fm < 1.0) fm = 1.0;
                fm = accelSpeed / fm;
                strafe *= fm;
                forward *= fm;
                double a = yaw * rad;
                double sinD = Math.sin(a);
                double cosD = Math.cos(a);
                vx += strafe * cosD - forward * sinD;
                vz += forward * cosD + strafe * sinD;
            }

            // (5) move (collision-free): position uses pre-gravity velocity.
            posX[t + 1] = posX[t] + vx;
            posY[t + 1] = posY[t] + vy;
            posZ[t + 1] = posZ[t] + vz;

            // (6) gravity then friction multiply, carried into next tick.
            vx = vx * f4;
            vz = vz * f4;
            vy = (vy - Constants.GRAVITY) * Constants.Y_DRAG_F;
        }
        return new ForwardPath(posX, posY, posZ);
    }
}
