package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;

public enum Medium {
    NONE("None", "", SurfaceKind.NORMAL),
    WATER("Water", "drag 0.80", SurfaceKind.WATER),
    LAVA("Lava", "drag 0.50", SurfaceKind.LAVA),
    LADDER("Ladder", "cap 0.15", SurfaceKind.LADDER),
    SOULSAND("Soulsand", "x0.40", SurfaceKind.SOULSAND),
    COBWEB("Cobweb", "x0.25", SurfaceKind.COBWEB);

    public final String label;
    public final String valueLabel;
    public final SurfaceKind kind;

    Medium(String label, String valueLabel, SurfaceKind kind) {
        this.label = label;
        this.valueLabel = valueLabel;
        this.kind = kind;
    }

    public static Medium fromFlags(boolean web, boolean water, boolean lava, boolean ladder, boolean soulsand) {
        if (web) return COBWEB;
        if (water) return WATER;
        if (lava) return LAVA;
        if (ladder) return LADDER;
        if (soulsand) return SOULSAND;
        return NONE;
    }

    public static String[] comboItems() {
        Medium[] all = values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            items[i] = all[i].valueLabel.isEmpty() ? all[i].label : all[i].label + " · " + all[i].valueLabel;
        }
        return items;
    }
}
