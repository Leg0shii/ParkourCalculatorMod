package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** MC 1.21.10 MathHelper sine table reproduced bit-identical: same size, same float
 *  generation, same lookup mask. sinStep/cosStep equal MC's MathHelper.sin/cos exactly.
 *  PWL variants linear-interpolate between adjacent buckets (C0, differentiable a.e.)
 *  for use as the smooth shadow's sine source. */
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

    private McSineTable() {}

    public static float sinStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD) & MASK];
    }

    public static float cosStep(float rad) {
        return TABLE[(int) (rad * INDEX_FROM_RAD + COS_INDEX_OFFSET) & MASK];
    }

    public static double sinPwl(double rad) {
        double idx = rad * INDEX_FROM_RAD;
        double floor = Math.floor(idx);
        int i0 = ((int) floor) & MASK;
        int i1 = (i0 + 1) & MASK;
        double frac = idx - floor;
        return (1.0 - frac) * TABLE[i0] + frac * TABLE[i1];
    }

    public static double cosPwl(double rad) {
        double idx = rad * INDEX_FROM_RAD + COS_INDEX_OFFSET;
        double floor = Math.floor(idx);
        int i0 = ((int) floor) & MASK;
        int i1 = (i0 + 1) & MASK;
        double frac = idx - floor;
        return (1.0 - frac) * TABLE[i0] + frac * TABLE[i1];
    }
}
