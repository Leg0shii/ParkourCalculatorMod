package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Angle reduction shared by the solver and the engine. Two distinct idioms are kept on purpose:
 *  {@link #wrap(double)} (modulo) and {@link #wrapDelta(double)} (while-loop). They can differ for
 *  multi-turn inputs, and this math is byte-exact-sensitive, so do not fold one into the other. */
public final class Angles {

    /** Reduce to (-180,180] via the modulo idiom (single subtraction, valid for the search box's <=2-turn range). */
    public static double wrap(double d) {
        d = d % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    /** Per-element {@link #wrap(double)} into a fresh array (never mutates the input). */
    public static double[] wrapAll(double[] f) {
        double[] w = new double[f.length];
        for (int i = 0; i < f.length; i++) w[i] = wrap(f[i]);
        return w;
    }

    public static double wrapDelta(double delta) {
        while (delta > 180.0) delta -= 360.0;
        while (delta < -180.0) delta += 360.0;
        return delta;
    }

    public static double travelDeg(double[] f) {
        double travel = 0.0;
        for (int i = 1; i < f.length; i++) {
            double d = f[i] - f[i - 1];
            d -= 360.0 * Math.round(d / 360.0);
            travel += Math.abs(d);
        }
        return travel;
    }

    public static final double REVERSAL_FLOOR_DEG = 0.01;

    public static int reversals(double[] f, double floorDeg) {
        int count = 0;
        int lastSign = 0;
        for (int i = 1; i < f.length; i++) {
            double d = f[i] - f[i - 1];
            d -= 360.0 * Math.round(d / 360.0);
            if (Math.abs(d) <= floorDeg) continue;
            int sign = d > 0.0 ? 1 : -1;
            if (lastSign != 0 && sign != lastSign) count++;
            lastSign = sign;
        }
        return count;
    }

    public static double wiggleDeg(double[] f) {
        if (f.length < 3) return 0.0;
        double jerk = 0.0;
        double prev = f[1] - f[0];
        prev -= 360.0 * Math.round(prev / 360.0);
        for (int i = 2; i < f.length; i++) {
            double d = f[i] - f[i - 1];
            d -= 360.0 * Math.round(d / 360.0);
            jerk += Math.abs(d - prev);
            prev = d;
        }
        return jerk;
    }

    public static double wiggleDeg(double anchorYaw, double[] f) {
        if (f.length < 2) return 0.0;
        double jerk = 0.0;
        double prev = f[0] - anchorYaw;
        prev -= 360.0 * Math.round(prev / 360.0);
        for (int i = 1; i < f.length; i++) {
            double d = f[i] - f[i - 1];
            d -= 360.0 * Math.round(d / 360.0);
            jerk += Math.abs(d - prev);
            prev = d;
        }
        return jerk;
    }
}
