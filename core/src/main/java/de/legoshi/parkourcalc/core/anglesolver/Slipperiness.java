package de.legoshi.parkourcalc.core.anglesolver;

/** Slipperiness options for the default-state combo. */
public enum Slipperiness {
    DEFAULT("Default", "0.60", 0.6),
    SLIME("Slime", "0.80", 0.8),
    ICE("Ice", "0.98", 0.98),
    PACKED_ICE("Packed ice", "0.98", 0.98),
    BLUE_ICE("Blue ice", "0.989", 0.989),
    AIR("Air", "1.00", 1.0);

    public final String label;
    public final String valueLabel;
    public final double slip;

    Slipperiness(String label, String valueLabel, double slip) {
        this.label = label;
        this.valueLabel = valueLabel;
        this.slip = slip;
    }

    public static Slipperiness fromFriction(double friction) {
        if (friction >= 0.9885) return BLUE_ICE;
        if (friction >= 0.97) return ICE;
        if (friction >= 0.79) return SLIME;
        return DEFAULT;
    }

    /** Combo labels: "label · valueLabel" per entry (middle-dot U+00B7 separator). */
    public static String[] comboItems() {
        Slipperiness[] all = values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) items[i] = all[i].label + " · " + all[i].valueLabel;
        return items;
    }
}
