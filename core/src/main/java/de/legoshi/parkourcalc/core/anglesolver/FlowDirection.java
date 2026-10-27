package de.legoshi.parkourcalc.core.anglesolver;

public enum FlowDirection {
    NONE("None", 0, 0),
    POS_X("+X", 1, 0),
    NEG_X("-X", -1, 0),
    POS_Z("+Z", 0, 1),
    NEG_Z("-Z", 0, -1),
    POS_X_POS_Z("+X +Z", 1, 1),
    POS_X_NEG_Z("+X -Z", 1, -1),
    NEG_X_POS_Z("-X +Z", -1, 1),
    NEG_X_NEG_Z("-X -Z", -1, -1);

    public final String label;
    public final double dx;
    public final double dz;

    FlowDirection(String label, int sx, int sz) {
        this.label = label;
        double sum = (double) sx * (double) sx + (double) sz * (double) sz;
        if (sum == 0.0) {
            this.dx = 0.0;
            this.dz = 0.0;
        } else {
            double len = Math.sqrt(sum);
            this.dx = (double) sx / len;
            this.dz = (double) sz / len;
        }
    }

    public boolean pushes() {
        return this != NONE;
    }

    public static String[] comboItems() {
        FlowDirection[] all = values();
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) items[i] = all[i].label;
        return items;
    }
}
