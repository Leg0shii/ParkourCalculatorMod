package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.Locale;

/** Number / chip formatting shared by the model (result lines) and the renderers (chips). */
public final class ConstraintText {

    // Mirrors Settings.solverStatsPrecision (static wiring; the engine has no Settings ref).
    public static int statsPrecision = Settings.DEFAULT_SOLVER_STATS_PRECISION;

    public static int precision() {
        return Math.min(Math.max(statsPrecision, Settings.MIN_STAT_PRECISION), Settings.MAX_STAT_PRECISION);
    }

    public static String fixedStat(double v) {
        return String.format(Locale.ROOT, "%." + precision() + "f", v);
    }

    /** Leading-space sign flag so positive yaws are the same width as negatives, keeping the yaw column digit-aligned. */
    public static String fixedYaw(double v) {
        return String.format(Locale.ROOT, "% ." + precision() + "f", v);
    }

    public static String num(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        String s = fixedStat(v);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s + "0";
        return s;
    }

    public static String duration(long nanos) {
        if (nanos >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fs", nanos / 1.0e9);
        if (nanos >= 1_000_000L) return String.format(Locale.ROOT, "%.1fms", nanos / 1.0e6);
        if (nanos >= 1_000L) return String.format(Locale.ROOT, "%.1fµs", nanos / 1.0e3);
        return nanos + "ns";
    }

    public static String chip(Constraint c) {
        String other = c.getField() == Constraint.Field.DX ? "dZ" : "dX";
        if (c.isRange()) {
            String lb = c.isLoInclusive() ? "[" : "(";
            String rb = c.isHiInclusive() ? "]" : ")";
            String body = lb + num(c.getLo()) + ", " + num(c.getHi()) + rb;
            return c.isVsOther() ? other + "+" + body : body;
        }
        if (c.isVsOther()) {
            double v = c.getValue();
            if (v == 0.0) return other;
            return v > 0.0 ? other + "+" + num(v) : other + "-" + num(-v);
        }
        return num(c.getValue());
    }

    public static String fieldLabel(Constraint c) {
        String label = c.getField().label;
        if (!c.isRelative()) return label;
        return label + "-" + label + "[T" + (c.getRefTick() + 1) + "]";
    }
}
