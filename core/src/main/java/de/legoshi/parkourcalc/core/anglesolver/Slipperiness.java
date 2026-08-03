package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;

/** Slipperiness options for the default-state combo. */
public enum Slipperiness {
    DEFAULT("Default", "0.60", 0.6, SurfaceKind.NORMAL),
    SLIME("Slime", "0.80", 0.8, SurfaceKind.NORMAL),
    ICE("Ice", "0.98", 0.98, SurfaceKind.NORMAL),
    PACKED_ICE("Packed ice", "0.98", 0.98, SurfaceKind.NORMAL),
    BLUE_ICE("Blue ice", "0.989", 0.989, SurfaceKind.NORMAL),
    AIR("Air", "1.00", 1.0, SurfaceKind.NORMAL),
    LADDER("Ladder", "cap 0.15", 1.0, SurfaceKind.LADDER),
    SOULSAND("Soulsand", "0.60 x0.4", 0.6, SurfaceKind.SOULSAND),
    SOULSAND_ICE("Soulsand on ice", "0.98 x0.4", 0.98, SurfaceKind.SOULSAND),
    WATER("Water", "drag 0.80", 1.0, SurfaceKind.WATER),
    WATER_SHALLOW("Water (shallow)", "drag 0.80", 1.0, SurfaceKind.WATER_SHALLOW),
    LAVA("Lava", "drag 0.50", 1.0, SurfaceKind.LAVA),
    LAVA_SHALLOW("Lava (shallow)", "drag 0.50", 1.0, SurfaceKind.LAVA_SHALLOW),
    COBWEB("Cobweb", "0.60 x0.25", 0.6, SurfaceKind.COBWEB),
    COBWEB_AIR("Cobweb (air)", "1.00 x0.25", 1.0, SurfaceKind.COBWEB);

    public final String label;
    public final String valueLabel;
    public final double slip;
    public final SurfaceKind kind;

    Slipperiness(String label, String valueLabel, double slip, SurfaceKind kind) {
        this.label = label;
        this.valueLabel = valueLabel;
        this.slip = slip;
        this.kind = kind;
    }

    /** Combo labels: "label · valueLabel" per entry (middle-dot U+00B7 separator). */
    public static String[] comboItems() {
        Slipperiness[] all = values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) items[i] = all[i].label + " · " + all[i].valueLabel;
        return items;
    }
}
