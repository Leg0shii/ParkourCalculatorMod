package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class ParamParse {

    private ParamParse() {
    }

    public static double[] doubles(String s, double[] def) {
        if (s == null) return def;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return def;
        String[] parts = trimmed.split("[,;\\s]+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return out;
    }

    public static int[] ints(String s, int[] def) {
        if (s == null) return def;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return def;
        String[] parts = trimmed.split("[,;\\s]+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return out;
    }
}
