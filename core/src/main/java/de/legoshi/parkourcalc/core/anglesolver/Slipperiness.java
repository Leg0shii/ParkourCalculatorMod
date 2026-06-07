package de.legoshi.parkourcalc.core.anglesolver;

/** Slipperiness options for the default-state combo. */
public enum Slipperiness {
    DEFAULT("Default", "0.60"),
    SLIME("Slime", "0.80"),
    ICE("Ice", "0.98"),
    PACKED_ICE("Packed ice", "0.98"),
    BLUE_ICE("Blue ice", "0.989"),
    AIR("Air", "1.00");

    public final String label;
    public final String valueLabel;

    Slipperiness(String label, String valueLabel) {
        this.label = label;
        this.valueLabel = valueLabel;
    }

    /** Combo labels: "label · valueLabel" per entry (middle-dot U+00B7 separator). */
    public static String[] comboItems() {
        Slipperiness[] all = values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) items[i] = all[i].label + " · " + all[i].valueLabel;
        return items;
    }
}
