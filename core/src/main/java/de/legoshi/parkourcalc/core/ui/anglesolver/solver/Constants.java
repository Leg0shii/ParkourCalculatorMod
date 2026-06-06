package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** MC 1.21.10 sprint-jump physics constants, computed via MC's exact float→double promotion
 *  chain. Static initializer replicates MathHelper.sqrt, applyMovementSpeedFactors,
 *  normalizeDirectionalMovement, and the attribute system in single-precision before
 *  promoting to double. Used by M2 (smooth shadow). Ported from the spike0 byte-exact model. */
public final class Constants {

    public static final float FORWARD_EFFECTIVE_F;
    public static final float ATTR_VALUE_F;
    public static final float SLIP_F = 0.6F;
    public static final float SLIP_CUBED_F = SLIP_F * SLIP_F * SLIP_F;
    public static final float SPEED_RATIO_F = 0.21600002F / SLIP_CUBED_F;
    public static final float GROUND_SPEED_F;
    public static final float AIR_SPEED_F = 0.025999999F;

    /** Slime block slipperiness (vs 0.6 for normal ground). A slime-contact tick is a ground tick with
     *  this slip: drag = slip*0.91, ground accel scales by 0.21600002/slip^3. */
    public static final float SLIME_SLIP_F = 0.8F;

    public static final double GROUND_ACCEL;
    public static final double AIR_ACCEL;

    // Per-component value of the diagonal (W+strafe) input after MC's double-precision normalize:
    // movementInput = (0.98, 0, 0.98), lengthSq = 1.92 > 1 so it normalizes; each of x/z becomes this.
    // The 45-strafe gain vs W-only (this*sqrt(2) = 1.0 vs 0.98 = +2.04%) is why strafing is faster.
    public static final double STRAFE_NORM_COMPONENT;

    public static final float GROUND_DRAG_F = SLIP_F * 0.91F;
    public static final float AIR_DRAG_F = 1.0F * 0.91F;
    public static final double GROUND_DRAG = (double) GROUND_DRAG_F;
    public static final double AIR_DRAG = (double) AIR_DRAG_F;

    public static final float Y_DRAG_F = 0.98F;
    public static final double Y_DRAG = (double) Y_DRAG_F;

    public static final double GRAVITY = 0.08;

    public static final float JUMP_VEL_F = 0.42F;
    public static final double JUMP_VEL = (double) JUMP_VEL_F;
    public static final double SPRINTJUMP_BOOST = 0.2;

    public static final float DEG_TO_RAD_F = (float) (Math.PI / 180.0);
    public static final double DEG_TO_RAD = Math.PI / 180.0;

    public static final double THRESHOLD = 0.003;
    public static final double PLAYER_HORIZ_THRESHOLD_SQ = 9.0e-6;

    static {
        float scaled = 1.0F * 0.98F;
        float lengthSq = scaled * scaled;
        float length = (float) Math.sqrt((double) lengthSq);
        float invLength = 1.0F / length;
        float normalizedY = scaled * invLength;
        float clampedLength = Math.min(length * 1.0F, 1.0F);
        FORWARD_EFFECTIVE_F = normalizedY * clampedLength;

        double baseAttr = (double) 0.1F;
        double sprintMod = (double) 0.3F;
        ATTR_VALUE_F = (float) (baseAttr * (1.0 + sprintMod));

        GROUND_SPEED_F = ATTR_VALUE_F * SPEED_RATIO_F;

        GROUND_ACCEL = (double) FORWARD_EFFECTIVE_F * (double) GROUND_SPEED_F;
        AIR_ACCEL = (double) FORWARD_EFFECTIVE_F * (double) AIR_SPEED_F;

        // MC Vec3d.normalize: component / sqrt(lengthSquared), in double. Input component = 1.0F*0.98F.
        double mi = (double) (1.0F * 0.98F);
        STRAFE_NORM_COMPONENT = mi / Math.sqrt(mi * mi + mi * mi);
    }

    /** Movement-speed attribute for a given Speed-effect amplifier (TAS value: 0 = none, 1 = Speed I,
     *  2 = Speed II, ...). Speed is an ADD_MULTIPLIED_TOTAL modifier of 0.2 per level on top of the
     *  sprint x1.3, all in MC's double chain with one trailing float cast. amp 0 == the vanilla
     *  ATTR_VALUE_F (byte-identical). */
    public static float attrValueF(int speedAmplifier) {
        double e = (double) 0.1F * (1.0 + (double) 0.3F);
        if (speedAmplifier > 0) {
            e = e * (1.0 + (double) 0.2F * speedAmplifier);
        }
        return (float) e;
    }

    public static float groundSpeedF(int speedAmplifier) {
        return attrValueF(speedAmplifier) * SPEED_RATIO_F;
    }

    /** Single-axis ground accel for a Speed amplifier. amp 0 == GROUND_ACCEL (byte-identical). */
    public static double groundAccel(int speedAmplifier) {
        return (double) FORWARD_EFFECTIVE_F * (double) groundSpeedF(speedAmplifier);
    }

    /** Ground movement speed for a given slipperiness (0.6 normal, 0.8 slime). slip 0.6 == groundSpeedF(amp). */
    public static float groundSpeedF(int speedAmplifier, float slip) {
        float ratio = 0.21600002F / (slip * slip * slip);
        return attrValueF(speedAmplifier) * ratio;
    }

    public static double groundAccel(int speedAmplifier, float slip) {
        return (double) FORWARD_EFFECTIVE_F * (double) groundSpeedF(speedAmplifier, slip);
    }

    public static double groundDrag(float slip) {
        return (double) (slip * 0.91F);
    }

    private Constants() {}
}
