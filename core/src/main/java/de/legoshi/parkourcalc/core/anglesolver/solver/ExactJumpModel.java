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
 *  <p>Tick order (both eras): zero any sub-threshold motion carry, jump() impulse + sprint boost,
 *  ground/air accel pick, rotated-input add, move, gravity, friction multiply. The {@code modern} flag
 *  switches each stage's arithmetic between the legacy 1.8.9 float chain and the 1.21.10 double chain
 *  (movementInputToVelocity); threshold is 0.005 in 1.8.x and 0.003 in 1.9+, per-axis vs combined-XZ
 *  via {@link #perAxisInertia}. */
public final class ExactJumpModel implements ForwardModel {

    /** MC's player combined-XZ momentum gate literal (LivingEntity.tickMovement), not 0.003*0.003. */
    private static final double COMBINED_INERTIA_SQ = 9.0E-6;

    private static final double LADDER_XZ_CAP = (double) 0.15F;
    private static final double FLUID_JUMP_BOOST = (double) 0.04F;
    private static final double WATER_DRAG = (double) 0.8F;
    private static final double WEB_Y_SCALE = (double) 0.05F;

    private static final float SQUARE_DIAG_INPUT = squareDiagInput();

    private static float squareDiagInput() {
        float dist = (float) Math.sqrt(2.0F);
        float n = 1.0F / dist;
        float s = n * 0.98F;
        float len = (float) Math.sqrt(s * s + s * s);
        float dirComp = s * (1.0F / len);
        float tan = 1.0F;
        float dtus = (float) Math.sqrt(1.0F + tan * tan);
        float modLen = Math.min(len * dtus, 1.0F);
        return dirComp * modLen;
    }

    private final double inertiaThreshold;
    private final boolean perAxisInertia;
    /** Modern (1.21.10) arithmetic: double-pipeline movementInputToVelocity, 0.21600002F ground factor,
     *  double sprint-jump boost, Math.max'd jump impulse. False = the legacy 1.8.9 float moveFlying chain. */
    private final boolean modern;
    /** 26.x Mth.sin/cos: double-indexed lookup into the regenerated table (see McSineTable.sinStep262). */
    private final boolean sine262;

    public ExactJumpModel(double inertiaThreshold, boolean perAxisInertia, boolean modern) {
        this(inertiaThreshold, perAxisInertia, modern, false);
    }

    public ExactJumpModel(double inertiaThreshold, boolean perAxisInertia, boolean modern, boolean sine262) {
        this.inertiaThreshold = inertiaThreshold;
        this.perAxisInertia = perAxisInertia;
        this.modern = modern;
        this.sine262 = sine262;
    }

    private float tableSin(float rad) {
        return sine262 ? McSineTable.sinStep262(rad) : McSineTable.sinStep(rad);
    }

    private float tableCos(float rad) {
        return sine262 ? McSineTable.cosStep262(rad) : McSineTable.cosStep(rad);
    }

    public double inertiaThreshold() {
        return inertiaThreshold;
    }

    public boolean perAxisInertia() {
        return perAxisInertia;
    }

    public boolean modern() {
        return modern;
    }

    /** Inertia rule for a loader's MC version. 1.8.x: per-axis 0.005. 1.12.x: per-axis 0.003.
     *  1.9+ players (1.21.10 and the modern default here): combined-XZ |v|^2 &lt; 9.0E-6. Covers the
     *  three loader versions; the per-axis-to-combined player switch lands between 1.12 and 1.21.
     *  Year-versioned MC (26.x onward) additionally runs the rewritten double-indexed sine lookup. */
    public static ExactJumpModel forMcVersion(String mcVersion) {
        if (mcVersion != null && mcVersion.startsWith("1.8")) return new ExactJumpModel(0.005, true, false);
        if (mcVersion != null && mcVersion.startsWith("1.12")) return new ExactJumpModel(0.003, true, false);
        boolean sine262 = mcVersion != null && !mcVersion.startsWith("1.");
        return new ExactJumpModel(0.003, false, true, sine262);
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
                if (vx * vx + vz * vz < COMBINED_INERTIA_SQ) { vx = 0.0; vz = 0.0; }
                if (Math.abs(vy) < thr) vy = 0.0;
            }

            float yawF = (float) yawAbsDeg[t];

            // (2) ground/air + jump, authored per tick (see class doc). jump() uses (float)(Math.PI/180.0)
            // for its rad cast (distinct from moveFlying's in step (4)).
            int amp = scenario.factorAmpAt(t);
            double slipOv = scenario.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            SurfaceKind kind = scenario.surfaceAt(t);
            boolean fluid = kind == SurfaceKind.WATER || kind == SurfaceKind.LAVA;
            float slipF = contact ? (float) slipOv : Constants.SLIP_F;
            boolean isJumpTick = !fluid && scenario.jumpAt(t) && contact;
            boolean sprint = scenario.sprintAt(t);
            boolean factorSprint = scenario.factorSprintAt(t);
            if (fluid && scenario.jumpAt(t)) {
                vy += FLUID_JUMP_BOOST;
            }
            if (isJumpTick) {
                if (modern) {
                    // jump(): Math.max'd impulse; the sprint boost stays double (float sin widened, * 0.2).
                    vy = Math.max((double) Constants.JUMP_VEL_F, vy);
                    if (sprint) {
                        float fj = yawF * (float) (Math.PI / 180.0);
                        vx += -tableSin(fj) * 0.2;
                        vz += tableCos(fj) * 0.2;
                    }
                } else {
                    vy = (double) Constants.JUMP_VEL_F;
                    if (sprint) {
                        float fj = yawF * (float) (Math.PI / 180.0);
                        vx -= tableSin(fj) * 0.2F;
                        vz += tableCos(fj) * 0.2F;
                    }
                }
            }

            // (3) accel regime; sprint is authored per tick (gh-120). Modern ground factor divides by the
            // raw slipperiness cube (getMovementSpeed(slipperiness)); legacy by the friction (slip*0.91) cube.
            float f4;
            float accelSpeed;
            if (fluid) {
                f4 = 0.91F;
                accelSpeed = 0.02F;
            } else if (contact) {
                f4 = slipF * 0.91F;
                float ground = modern ? 0.21600002F / (slipF * slipF * slipF) : 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(amp, sprint) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = factorSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }

            // (4) input acceleration. Inputs are authored per tick (gh-102): force-45 ticks carry the W+A
            // assumption (W-only on the grounded jump tick), every other tick runs the user's own keys.
            float forward = scenario.forwardAt(t);
            float strafe;
            if (scenario.strafeAt(t) && !isJumpTick) {
                if (sine262) {
                    forward = Math.signum(forward) * SQUARE_DIAG_INPUT;
                    strafe = scenario.strafeSign * SQUARE_DIAG_INPUT;
                } else {
                    strafe = scenario.strafeSign * 1.0F * 0.98F;
                }
            } else {
                strafe = scenario.strafeInputAt(t);
            }
            if (modern) {
                // Entity.movementInputToVelocity: double pipeline, double 1e-7 gate, Vec3d.normalize only
                // above unit length, multiply(speed) and the rotation all in double with widened float trig.
                double sw = (double) strafe;
                double fw = (double) forward;
                double lenSq = sw * sw + fw * fw;
                if (lenSq >= 1.0E-7) {
                    if (lenSq > 1.0) {
                        double len = Math.sqrt(lenSq);
                        sw /= len;
                        fw /= len;
                    }
                    sw *= (double) accelSpeed;
                    fw *= (double) accelSpeed;
                    float rad = yawF * (float) (Math.PI / 180.0);
                    float sinD = tableSin(rad);
                    float cosD = tableCos(rad);
                    vx += sw * (double) cosD - fw * (double) sinD;
                    vz += fw * (double) cosD + sw * (double) sinD;
                }
            } else {
                // Legacy moveFlying(strafe, forward, accelSpeed), in float. moveFlying uses
                // rotationYaw*(float)PI/180F for its rad cast (distinct from jump()'s cast).
                float fm = strafe * strafe + forward * forward;
                if (fm >= 1.0E-4F) {
                    fm = (float) Math.sqrt((double) fm);
                    if (fm < 1.0F) fm = 1.0F;
                    fm = accelSpeed / fm;
                    strafe *= fm;
                    forward *= fm;
                    float rad = yawF * (float) Math.PI / 180.0F;
                    float sinD = tableSin(rad);
                    float cosD = tableCos(rad);
                    vx += (double) (strafe * cosD - forward * sinD);
                    vz += (double) (forward * cosD + strafe * sinD);
                }
            }

            if (kind == SurfaceKind.LADDER) {
                if (vx < -LADDER_XZ_CAP) vx = -LADDER_XZ_CAP;
                else if (vx > LADDER_XZ_CAP) vx = LADDER_XZ_CAP;
                if (vz < -LADDER_XZ_CAP) vz = -LADDER_XZ_CAP;
                else if (vz > LADDER_XZ_CAP) vz = LADDER_XZ_CAP;
                double ladderFloor = modern ? (double) -0.15F : -0.15;
                if (vy < ladderFloor) vy = ladderFloor;
                if (scenario.sneakAt(t) && vy < 0.0) vy = 0.0;
            }

            // (5) move (collision-free): position uses pre-gravity velocity.
            if (kind == SurfaceKind.COBWEB) {
                posX[t + 1] = posX[t] + vx * 0.25;
                posY[t + 1] = posY[t] + vy * WEB_Y_SCALE;
                posZ[t + 1] = posZ[t] + vz * 0.25;
                vx = 0.0;
                vy = 0.0;
                vz = 0.0;
            } else {
                posX[t + 1] = posX[t] + vx;
                posY[t + 1] = posY[t] + vy;
                posZ[t + 1] = posZ[t] + vz;
            }

            if (kind == SurfaceKind.SOULSAND) {
                double soulFactor = modern ? (double) 0.4F : 0.4;
                vx *= soulFactor;
                vz *= soulFactor;
            }

            // (6) gravity then friction multiply, carried into next tick.
            if (fluid) {
                double fluidDrag = kind == SurfaceKind.WATER ? WATER_DRAG : 0.5;
                velX[t + 1] = vx * fluidDrag;
                velZ[t + 1] = vz * fluidDrag;
                velY[t + 1] = vy * fluidDrag - 0.02;
            } else {
                velX[t + 1] = vx * (double) f4;
                velZ[t + 1] = vz * (double) f4;
                velY[t + 1] = (vy - Constants.GRAVITY) * (double) Constants.Y_DRAG_F;
            }
        }
    }
}
