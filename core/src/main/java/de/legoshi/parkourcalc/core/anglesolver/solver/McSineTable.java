package de.legoshi.parkourcalc.core.anglesolver.solver;

/** MC 1.21.10 MathHelper sine table reproduced bit-identical: same size, same float
 *  generation, same lookup mask. sinStep/cosStep equal MC's MathHelper.sin/cos exactly. */
public final class McSineTable {

    public static final int SIZE = 65536;
    public static final int MASK = SIZE - 1;
    public static final float INDEX_FROM_RAD = 10430.378F;
    public static final float COS_INDEX_OFFSET = 16384.0F;

    public static final float[] TABLE = new float[SIZE];
    static {
        for (int i = 0; i < SIZE; i++) {
            TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / SIZE);
        }
    }

    public static float sinStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD) & MASK];
    }

    public static float cosStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD + COS_INDEX_OFFSET) & MASK];
    }
}
