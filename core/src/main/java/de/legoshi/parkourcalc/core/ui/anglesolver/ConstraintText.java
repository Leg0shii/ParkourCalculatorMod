package de.legoshi.parkourcalc.core.ui.anglesolver;

import java.util.Locale;

/** Number / chip formatting shared by the model (result lines) and the renderers (chips). */
final class ConstraintText {

    private ConstraintText() {}

    static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.7f", v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s + "0";
        return s;
    }

    static String chip(Constraint c) {
        if (c.isRange()) {
            String lb = c.isLoInclusive() ? "[" : "(";
            String rb = c.isHiInclusive() ? "]" : ")";
            return lb + num(c.getLo()) + ", " + num(c.getHi()) + rb;
        }
        return num(c.getValue());
    }
}
