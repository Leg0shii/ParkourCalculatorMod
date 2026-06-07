package de.legoshi.parkourcalc.core.anglesolver;

import java.util.Locale;

/** Number / chip formatting shared by the model (result lines) and the renderers (chips). */
public final class ConstraintText {

    private ConstraintText() {}

    /** Fixed 7-decimal format (Locale.ROOT). */
    public static String fixed7(double v) {
        return String.format(Locale.ROOT, "%.7f", v);
    }

    /** Fixed 6-decimal format with the leading-space sign flag (Locale.ROOT). */
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
