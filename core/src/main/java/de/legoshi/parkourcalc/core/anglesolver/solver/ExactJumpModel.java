package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Byte-exact MC sprint-jump forward: a direct port of the real movement float chain (hard
 *  thresholds + exact MathHelper sine table + per-axis momentum cancellation).
 *  Reproduces the live SimulatorEntity to the ULP for collision-free airborne arcs, so CMA-ES
 *  (derivative-free) can optimize and report against it directly. The model
 *  is horizontal-aware but also tracks Y so the per-axis inertia on motionY matches MC; it never
 *  simulates collision, so it is only valid for constraint ticks before any wall graze or landing.
 *
 *  <p>Per the 1.8.9 path: onLivingUpdate zeroes any |motion| &lt; threshold (carry from last tick's
 *  friction), jump() fires the impulse + sprint boost, moveEntityWithHeading picks ground/air accel,
 *  moveFlying adds the rotated input, gravity hits motionY, then the friction multiply carries out.
 *  threshold is 0.005 in 1.8.x and 0.003 in 1.9+; players in 1.9+ use a combined-XZ rule instead of
 *  per-axis (select via {@link #perAxisInertia}). */
public final class ExactJumpModel implements ForwardModel {

    private final double inertiaThreshold;
    private final boolean perAxisInertia;

    public ExactJumpModel(double inertiaThreshold, boolean perAxisInertia) {
        this.inertiaThreshold = inertiaThreshold;
        this.perAxisInertia = perAxisInertia;
    }

    public double inertiaThreshold() {
        return inertiaThreshold;
    }

    public boolean perAxisInertia() {
        return perAxisInertia;
    }

    /** Inertia rule for a loader's MC version. 1.8.x: per-axis 0.005. 1.12.x: per-axis 0.003.
     *  1.9+ players (1.21.10 and the modern default here): combined-XZ |v|^2 &lt; 0.003^2. Covers the
     *  three loader versions; the per-axis-to-combined player switch lands between 1.12 and 1.21. */
    public static ExactJumpModel forMcVersion(String mcVersion) {
        if (mcVersion != null && mcVersion.startsWith("1.8")) return new ExactJumpModel(0.005, true);
        if (mcVersion != null && mcVersion.startsWith("1.12")) return new ExactJumpModel(0.003, true);
        return new ExactJumpModel(0.003, false);
    }

    @Override
    public ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg) {
        int n = yawAbsDeg.length;
        double[] posX = new double[n + 1];
        double[] posY = new double[n + 1];
        double[] posZ = new double[n + 1];
        double[] velX = new double[n + 1];
        double[] velY = new double[n + 1];
        double[] velZ = new double[n + 1];

        posX[0] = scenario.startPos.x;
        posY[0] = scenario.startPos.y;
        posZ[0] = scenario.startPos.z;
        velX[0] = scenario.initialVelocity.x;
        velY[0] = scenario.initialVelocity.y;
        velZ[0] = scenario.initialVelocity.z;

        double thr = inertiaThreshold;

        for (int t = 0; t < n; t++) {
            double vx = velX[t];
            double vy = velY[t];
            double vz = velZ[t];

            // (1) momentum cancellation, top of tick, on the post-friction carry from last tick.
            if (perAxisInertia) {
                if (Math.abs(vx) < thr) vx = 0.0;
                if (Math.abs(vy) < thr) vy = 0.0;
                if (Math.abs(vz) < thr) vz = 0.0;
            } else {
                if (vx * vx + vz * vz < thr * thr) { vx = 0.0; vz = 0.0; }
                if (Math.abs(vy) < thr) vy = 0.0;
            }

            float yawF = (float) yawAbsDeg[t];

            boolean isJumpTick = (scenario.jumpTick >= 0 && t == scenario.jumpTick);
            boolean onGround = (scenario.jumpTick >= 0 && t <= scenario.jumpTick);

            // (2) jump impulse + sprint boost. jump() uses (float)(Math.PI/180.0) for its rad cast.
            if (isJumpTick) {
                vy = (double) Constants.JUMP_VEL_F;
                float fj = yawF * (float) (Math.PI / 180.0);
                vx -= McSineTable.sinStep(fj) * 0.2F;
                vz += McSineTable.cosStep(fj) * 0.2F;
            }

            // (3) accel regime. Per-tick slip override turns a tick into a ground-contact tick.
            int amp = scenario.speedAmplifierAt(t);
            double slipOv = scenario.slipAt(t);
            boolean hasSurface = !Double.isNaN(slipOv);
            boolean contact = onGround || hasSurface;
            float slipF = hasSurface ? (float) slipOv : Constants.SLIP_F;

            float f4;
            float accelSpeed;
            if (contact) {
                f4 = slipF * 0.91F;
                float ground = 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = Constants.AIR_SPEED_F;
            }

            // (4) moveFlying(strafe, forward, accelSpeed), in float. moveFlying uses
            // rotationYaw*(float)PI/180F for its rad cast (distinct from jump()'s cast).
            float strafe;
            float forward = 1.0F * 0.98F;
            if (scenario.strafeAt(t) && !isJumpTick) {
                strafe = scenario.strafeSign * 1.0F * 0.98F;
            } else {
                strafe = 0.0F;
            }
            float fm = strafe * strafe + forward * forward;
            if (fm >= 1.0E-4F) {
                fm = (float) Math.sqrt((double) fm);
                if (fm < 1.0F) fm = 1.0F;
                fm = accelSpeed / fm;
                strafe *= fm;
                forward *= fm;
                float rad = yawF * (float) Math.PI / 180.0F;
                float sinD = McSineTable.sinStep(rad);
                float cosD = McSineTable.cosStep(rad);
                vx += (double) (strafe * cosD - forward * sinD);
                vz += (double) (forward * cosD + strafe * sinD);
            }

            // (5) move (collision-free): position uses pre-gravity velocity.
            posX[t + 1] = posX[t] + vx;
            posY[t + 1] = posY[t] + vy;
            posZ[t + 1] = posZ[t] + vz;

            // (6) gravity then friction multiply, carried into next tick.
            velX[t + 1] = vx * (double) f4;
            velZ[t + 1] = vz * (double) f4;
            velY[t + 1] = (vy - Constants.GRAVITY) * (double) Constants.Y_DRAG_F;
        }
        return new ForwardPath(posX, posY, posZ);
    }
}
