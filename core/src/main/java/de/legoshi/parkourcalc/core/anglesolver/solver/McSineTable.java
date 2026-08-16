package de.legoshi.parkourcalc.core.anglesolver.solver;

/** MC MathHelper/Mth sine table reproduced bit-identical, in both historical forms. sinStep/cosStep
 *  equal the float chain used before the 26.x rewrite (float scale 10430.378F, int cast). sinStep262/cosStep262
 *  equal the 26.x rewrite: double scale 65536/(2*PI), long cast + mask, and a table regenerated from
 *  {@code Math.sin(i / scale)} instead of {@code Math.sin(i * 2 * PI / 65536)}; both the index and some
 *  table values differ from the old chain at bucket boundaries. */
public final class McSineTable {

    public static final int SIZE = 65536;
    public static final int MASK = SIZE - 1;
    public static final float INDEX_FROM_RAD = 10430.378F;
    public static final float COS_INDEX_OFFSET = 16384.0F;
    public static final double INDEX_FROM_RAD_262 = 10430.378350470453;
    public static final double COS_INDEX_OFFSET_262 = 16384.0;

    public static final float[] TABLE = new float[SIZE];
    static {
        for (int i = 0; i < SIZE; i++) {
            TABLE[i] = (float) Math.sin(i * Math.PI * 2.0 / SIZE);
        }
    }

    public static final float[] TABLE_262 = new float[SIZE];
    static {
        for (int i = 0; i < SIZE; i++) {
            TABLE_262[i] = (float) Math.sin(i / INDEX_FROM_RAD_262);
        }
    }

    public static float sinStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD) & MASK];
    }

    public static float cosStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD + COS_INDEX_OFFSET) & MASK];
    }

    public static float sinStep262(float rad) {
        return TABLE_262[(int) ((long) ((double) rad * INDEX_FROM_RAD_262) & 65535L)];
    }

    public static float cosStep262(float rad) {
        return TABLE_262[(int) ((long) ((double) rad * INDEX_FROM_RAD_262 + COS_INDEX_OFFSET_262) & 65535L)];
    }
}
