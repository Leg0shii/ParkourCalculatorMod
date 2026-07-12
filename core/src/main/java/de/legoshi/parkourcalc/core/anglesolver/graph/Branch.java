package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class Branch {

    public enum Feas {
        FEASIBLE,
        PRESERVES,
        UNKNOWN
    }

    public final Guarantee label;
    public final Feas feas;

    private Branch(Guarantee label, Feas feas) {
        this.label = label;
        this.feas = feas;
    }

    public static Branch feasible(Guarantee label) {
        return new Branch(label, Feas.FEASIBLE);
    }

    public static Branch preserves(Guarantee label) {
        return new Branch(label, Feas.PRESERVES);
    }

    public static Branch unknown(Guarantee label) {
        return new Branch(label, Feas.UNKNOWN);
    }
}
