package de.legoshi.parkourcalc.core.anglesolver;

import java.util.Locale;

/** Number / chip formatting shared by the model (result lines) and the renderers (chips). */
public final class ConstraintText {

    public static String fixed7(double v) {
        return String.format(Locale.ROOT, "%.7f", v);
    }

    /** Leading-space sign flag so positive yaws are the same width as negatives, keeping the yaw column digit-aligned. */
    public static String fixed6(double v) {
        return String.format(Locale.ROOT, "% .6f", v);
    }

    public static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        String s = fixed7(v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s + "0";
        return s;
    }

    public static String chip(Constraint c) {
        if (c.isRange()) {
            String lb = c.isLoInclusive() ? "[" : "(";
            String rb = c.isHiInclusive() ? "]" : ")";
            return lb + num(c.getLo()) + ", " + num(c.getHi()) + rb;
        }
        return num(c.getValue());
    }
}
