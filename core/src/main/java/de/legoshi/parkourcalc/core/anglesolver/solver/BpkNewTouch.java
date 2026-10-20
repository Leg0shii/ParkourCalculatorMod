package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class BpkNewTouch {

    private static final float QUARTER_PI = 0.7853982F;
    private static final float BEST_YAW = (float) Math.acos(0.98);
    private static final float TURN = QUARTER_PI - BEST_YAW;
    private static final float SIN_POS = McSineTable.sinStep(TURN);
    private static final float COS_POS = McSineTable.cosStep(TURN);
    private static final float SIN_NEG = McSineTable.sinStep(-TURN);
    private static final float COS_NEG = McSineTable.cosStep(-TURN);

    private BpkNewTouch() {
    }

    public static boolean applies(float strafe, float forward) {
        return (double) Math.abs(strafe * forward) >= 1.0E-4;
    }

    public static float strafeAfter(float strafe, float forward) {
        return strafe * forward > 0.0F
                ? strafe * COS_POS - forward * SIN_POS
                : strafe * COS_NEG - forward * SIN_NEG;
    }

    public static float forwardAfter(float strafe, float forward) {
        return strafe * forward > 0.0F
                ? forward * COS_POS + strafe * SIN_POS
                : forward * COS_NEG + strafe * SIN_NEG;
    }
}
