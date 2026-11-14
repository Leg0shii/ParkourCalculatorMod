package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class FastCheckVerdict {

    public enum Kind {
        FEASIBLE,
        INFEASIBLE,
        UNKNOWN
    }

    public final Kind kind;
    public final double[] yaws;
    public final double px;
    public final double pz;
    public final String note;
    public final double reached;

    private FastCheckVerdict(Kind kind, double[] yaws, double px, double pz, String note, double reached) {
        this.kind = kind;
        this.yaws = yaws;
        this.px = px;
        this.pz = pz;
        this.note = note;
        this.reached = reached;
    }

    public static FastCheckVerdict feasible(double[] yaws, double px, double pz, String note) {
        return new FastCheckVerdict(Kind.FEASIBLE, yaws, px, pz, note, 0.0);
    }

    public static FastCheckVerdict infeasible(String note) {
        return new FastCheckVerdict(Kind.INFEASIBLE, null, Double.NaN, Double.NaN, note, Double.POSITIVE_INFINITY);
    }

    public static FastCheckVerdict unknown(String note) {
        return unknown(note, Double.POSITIVE_INFINITY);
    }

    public static FastCheckVerdict unknown(String note, double reached) {
        return new FastCheckVerdict(Kind.UNKNOWN, null, Double.NaN, Double.NaN, note,
                reached > 0.0 ? reached : Double.POSITIVE_INFINITY);
    }
}
