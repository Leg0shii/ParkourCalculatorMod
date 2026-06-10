package de.legoshi.parkourcalc.core.anglesolver.solver;

/** MC sprint-jump physics constants, computed via MC's exact float->double promotion chain.
 *  Read by ExactJumpModel (byte-exact forward). */
public final class Constants {

    public static final float SLIP_F = 0.6F;
    /** getOffGroundSpeed: 0.025999999F sprinting, 0.02F not (PlayerEntity). */
    public static final float AIR_SPEED_F = 0.025999999F;
    public static final float AIR_SPEED_NO_SPRINT_F = 0.02F;

    public static final float Y_DRAG_F = 0.98F;

    public static final double GRAVITY = 0.08;

    public static final float JUMP_VEL_F = 0.42F;

    /** Movement-speed attribute for a given Speed-effect amplifier (TAS value: 0 = none, 1 = Speed I,
     *  2 = Speed II, ...). Speed is an ADD_MULTIPLIED_TOTAL modifier of 0.2 per level on top of the
     *  sprint x1.3 (also multiplied-total, absent when not sprinting), all in MC's double chain with
     *  one trailing float cast. amp 0 sprinting == the vanilla attribute value (byte-identical). */
    public static float attrValueF(int speedAmplifier, boolean sprinting) {
        double e = (double) 0.1F;
        if (sprinting) {
            e = e * (1.0 + (double) 0.3F);
        }
        if (speedAmplifier > 0) {
            e = e * (1.0 + (double) 0.2F * speedAmplifier);
        }
        return (float) e;
    }

}
