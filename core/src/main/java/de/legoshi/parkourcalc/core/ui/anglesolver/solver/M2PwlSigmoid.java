package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Smooth MC sprint-jump forward (PWL-interpolated sine + sigmoid soft gates instead of MC's step
 *  lookup + hard thresholds, so the gradient is well-defined everywhere). Ground ticks are
 *  t &lt;= jumpTick (run-up + the jump tick), with the jump impulse + sprintjump boost on jumpTick
 *  itself; later ticks are airborne. 45-strafe applies on every tick whose strafe mask is set except
 *  the jump tick (which stays W-only so the 0.2 boost aligns with travel). Per-tick slipperiness
 *  overrides turn a tick into a ground-contact tick at that slip. Forward is approximate, not
 *  byte-exact; the live SimulatorEntity is the source of truth once facings are applied. */
public final class M2PwlSigmoid implements DifferentiableModel {

    private final double sharpness;

    public M2PwlSigmoid() { this(5000.0); }
    public M2PwlSigmoid(double sharpness) { this.sharpness = sharpness; }

    @Override public String name() { return "M2_pwl_sigmoid"; }
    @Override public boolean gradientIsBiased() { return false; }

    @Override
    public PathResult forward(Spike0Scenario scenario, double[] yawAbsDeg) {
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

        double startY = scenario.startPos.y;

        for (int t = 0; t < n; t++) {
            double vx = velX[t];
            double vy = velY[t];
            double vz = velZ[t];

            double horizLenSq = vx * vx + vz * vz;
            double horizGate = sigmoid(sharpness * (horizLenSq - Constants.PLAYER_HORIZ_THRESHOLD_SQ));
            vx *= horizGate;
            vz *= horizGate;
            double yGate = sigmoid(sharpness * (Math.abs(vy) - Constants.THRESHOLD));
            vy *= yGate;

            double yawRad = yawAbsDeg[t] * Constants.DEG_TO_RAD;
            double sinD = McSineTable.sinPwl(yawRad);
            double cosD = McSineTable.cosPwl(yawRad);

            boolean isJumpTick = (t == scenario.jumpTick);
            boolean onGround = (t <= scenario.jumpTick);
            if (isJumpTick) {
                double jumpGate = sigmoid(sharpness * (Constants.JUMP_VEL - vy));
                vy = jumpGate * Constants.JUMP_VEL + (1.0 - jumpGate) * vy;
                vx += -sinD * Constants.SPRINTJUMP_BOOST;
                vz += cosD * Constants.SPRINTJUMP_BOOST;
            }

            int amp = scenario.speedAmplifierAt(t);
            double slipOv = scenario.slipAt(t);
            boolean hasSurface = !Double.isNaN(slipOv);
            boolean contact = onGround || hasSurface;
            float slipF = hasSurface ? (float) slipOv : Constants.SLIP_F;

            double accel;
            double drag;
            double groundSpeed;
            if (contact) {
                accel = Constants.groundAccel(amp, slipF);
                drag = Constants.groundDrag(slipF);
                groundSpeed = (double) Constants.groundSpeedF(amp, slipF);
            } else {
                accel = Constants.AIR_ACCEL;
                drag = Constants.AIR_DRAG;
                groundSpeed = (double) Constants.AIR_SPEED_F;
            }

            double vxPost;
            double vzPost;
            if (scenario.strafeAt(t) && !isJumpTick) {
                // Smooth mirror of MC's two-input 45-strafe rotate: the diagonal input normalizes to
                // STRAFE_NORM_COMPONENT per axis, scales by the regime movement speed, then rotates by
                // the facing's sin/cos. The jump tick stays W-only so the sprintjump boost aligns.
                double speed = groundSpeed;
                double vFwd = Constants.STRAFE_NORM_COMPONENT * speed;
                double vStrafe = scenario.strafeSign * vFwd;
                vxPost = vx + (vStrafe * cosD - vFwd * sinD);
                vzPost = vz + (vFwd * cosD + vStrafe * sinD);
            } else {
                vxPost = vx + (-accel * sinD);
                vzPost = vz + (accel * cosD);
            }

            posX[t + 1] = posX[t] + vxPost;
            posZ[t + 1] = posZ[t] + vzPost;

            double posYAfterMove = posY[t] + vy;
            double landGate = sigmoid(sharpness * (startY - posYAfterMove));
            posY[t + 1] = landGate * startY + (1.0 - landGate) * posYAfterMove;

            double vyPostGrav = vy - Constants.GRAVITY;
            velX[t + 1] = vxPost * drag;
            velZ[t + 1] = vzPost * drag;
            velY[t + 1] = vyPostGrav * Constants.Y_DRAG;
        }
        return new PathResult(posX, posY, posZ, velX, velY, velZ);
    }

    @Override
    public double[] gradient(Spike0Scenario scenario, double[] yawAbsDeg, int tick, Spike0Scenario.Axis axis) {
        return FiniteDifference.gradient(this, scenario, yawAbsDeg, tick, axis);
    }

    static double sigmoid(double x) {
        if (x >= 0) {
            return 1.0 / (1.0 + Math.exp(-x));
        }
        double e = Math.exp(x);
        return e / (1.0 + e);
    }
}
