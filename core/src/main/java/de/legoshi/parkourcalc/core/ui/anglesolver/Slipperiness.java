package de.legoshi.parkourcalc.core.ui.anglesolver;

/** Slipperiness ladder the default-state combo cycles through (wraps on the last entry). */
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

    public Slipperiness next() {
        Slipperiness[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
