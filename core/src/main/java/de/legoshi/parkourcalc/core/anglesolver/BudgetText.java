package de.legoshi.parkourcalc.core.anglesolver;

import java.util.Locale;

public final class BudgetText {

    private BudgetText() {
    }

    public static int parseMs(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT).replace(",", ".");
        if (s.isEmpty()) return -1;
        boolean millis = false;
        if (s.endsWith("ms")) {
            millis = true;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("s")) {
            s = s.substring(0, s.length() - 1);
        }
        s = s.trim();
        if (s.isEmpty()) return -1;
        double v;
        try {
            v = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return -1;
        double ms = millis ? v : v * 1000.0;
        if (ms > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.round(ms);
    }

    public static String format(int ms) {
        if (ms < 0) ms = 0;
        if (ms < 1000) return ms + " ms";
        if (ms % 1000 == 0) return (ms / 1000) + " s";
        if (ms % 100 == 0) return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
        return String.format(Locale.ROOT, "%.3f s", ms / 1000.0).replaceAll("0+$", "");
    }
}
