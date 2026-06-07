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
}
