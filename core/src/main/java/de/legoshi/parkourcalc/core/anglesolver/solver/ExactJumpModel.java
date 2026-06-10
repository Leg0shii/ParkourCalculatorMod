package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Byte-exact MC sprint-jump forward: a direct port of the real movement float chain (hard
 *  thresholds + exact MathHelper sine table + per-axis momentum cancellation). Reproduces the live
 *  SimulatorEntity to the ULP in X/Z for collision-free motion, so CMA-ES (derivative-free) optimizes
 *  and reports against it directly.
 *
 *  <p>Ground/air is authored per tick by {@link JumpPhysicsInputs#slipPerTick} (a ground value = on a
 *  surface, NaN = airborne) and jumps by {@link JumpPhysicsInputs#jumpPerTick}: a JUMP tick fires only
 *  while grounded, so a window with any number of jumps and intermediate landings runs the same path as
 *  a single jump. It still never simulates collision (no wall clamp; SweptCollision checks that
 *  separately) and does not clamp Y onto a surface, so posY between jumps is not physical; X/Z (all the
 *  solver constrains) stay byte-exact.
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

        ForwardPath path = new ForwardPath(posX, posY, posZ, velX, velY, velZ);
        stepRange(scenario, yawAbsDeg, 0, path);
        return path;
    }

    /** Recompute {@code path} in place for ticks {@code [from, n)}, reusing the existing pos/vel at index
     *  {@code from} as the seed. The single-tick step depends only on the velocity carried into the tick, so
     *  a change to {@code yawAbsDeg[from]} (or any later facing) leaves indices {@code <= from} untouched and
     *  this recomputes exactly the affected tail. Lets a local search re-evaluate a one-facing perturbation
     *  in {@code O(n - from)} instead of {@code O(n)}; the full {@link #forward} is {@code stepRange(.,.,0,.)}.
     *  {@code path} must carry velocity arrays (built by {@link #forward}). Byte-identical to a full forward. */
    public void stepRange(JumpPhysicsInputs scenario, double[] yawAbsDeg, int from, ForwardPath path) {
        int n = yawAbsDeg.length;
        double[] posX = path.posX, posY = path.posY, posZ = path.posZ;
        double[] velX = path.velX, velY = path.velY, velZ = path.velZ;
        double thr = inertiaThreshold;

        for (int t = from; t < n; t++) {
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

            // (2) ground/air + jump, authored per tick. Slip annotation is the truth (ground value = on a
            // surface, NaN = airborne); a JUMP tick fires only while grounded (a held jump in the air does
            // nothing, like MC), so any number of jumps in the window works the same. jump() uses
            // (float)(Math.PI/180.0) for its rad cast.
            int amp = scenario.speedAmplifierAt(t);
            double slipOv = scenario.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            float slipF = contact ? (float) slipOv : Constants.SLIP_F;
            boolean isJumpTick = scenario.jumpAt(t) && contact;
            boolean sprint = scenario.sprintAt(t);
            if (isJumpTick) {
                vy = (double) Constants.JUMP_VEL_F;
                if (sprint) {
                    float fj = yawF * (float) (Math.PI / 180.0);
                    vx -= McSineTable.sinStep(fj) * 0.2F;
                    vz += McSineTable.cosStep(fj) * 0.2F;
                }
            }

            // (3) accel regime; sprint is authored per tick (gh-120).
            float f4;
            float accelSpeed;
            if (contact) {
                f4 = slipF * 0.91F;
                float ground = 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp, sprint) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = sprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }

            // (4) moveFlying(strafe, forward, accelSpeed), in float. moveFlying uses
            // rotationYaw*(float)PI/180F for its rad cast (distinct from jump()'s cast).
            // Inputs are authored per tick (gh-102): force-45 ticks carry the W+A assumption
            // (W-only on the grounded jump tick), every other tick runs the user's own keys.
            float forward = scenario.forwardAt(t);
            float strafe;
            if (scenario.strafeAt(t) && !isJumpTick) {
                strafe = scenario.strafeSign * 1.0F * 0.98F;
            } else {
                strafe = scenario.strafeInputAt(t);
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
    }
}
