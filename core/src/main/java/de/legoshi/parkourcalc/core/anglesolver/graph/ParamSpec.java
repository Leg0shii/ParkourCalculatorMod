package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class ParamSpec {

    public enum Kind {
        INT,
        DOUBLE,
        BOOL,
        ENUM,
        STRING
    }

    public final String key;
    public final String label;
    public final Kind kind;
    public final double min;
    public final double max;
    public final double def;
    public final boolean defBool;
    public final String defString;
    public final String[] choices;

    private ParamSpec(String key, String label, Kind kind, double min, double max, double def,
                      boolean defBool, String defString, String[] choices) {
        this.key = key;
        this.label = label;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.def = def;
        this.defBool = defBool;
        this.defString = defString;
        this.choices = choices;
    }

    public static ParamSpec integer(String key, String label, int min, int max, int def) {
        return new ParamSpec(key, label, Kind.INT, min, max, def, false, null, null);
    }

    public static ParamSpec decimal(String key, String label, double min, double max, double def) {
        return new ParamSpec(key, label, Kind.DOUBLE, min, max, def, false, null, null);
    }

    public static ParamSpec bool(String key, String label, boolean def) {
        return new ParamSpec(key, label, Kind.BOOL, 0, 0, 0, def, null, null);
    }

    public static ParamSpec choice(String key, String label, String[] choices, String def) {
        return new ParamSpec(key, label, Kind.ENUM, 0, 0, 0, false, def, choices.clone());
    }

    public static ParamSpec text(String key, String label, String def) {
        return new ParamSpec(key, label, Kind.STRING, 0, 0, 0, false, def, null);
    }

    public double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    public boolean validChoice(String v) {
        if (choices == null) return true;
        for (String c : choices) {
            if (c.equals(v)) return true;
        }
        return false;
    }
}
