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

    private FastCheckVerdict(Kind kind, double[] yaws, double px, double pz, String note) {
        this.kind = kind;
        this.yaws = yaws;
        this.px = px;
        this.pz = pz;
        this.note = note;
    }

    public static FastCheckVerdict feasible(double[] yaws, double px, double pz, String note) {
        return new FastCheckVerdict(Kind.FEASIBLE, yaws, px, pz, note);
    }

    public static FastCheckVerdict infeasible(String note) {
        return new FastCheckVerdict(Kind.INFEASIBLE, null, Double.NaN, Double.NaN, note);
    }

    public static FastCheckVerdict unknown(String note) {
        return new FastCheckVerdict(Kind.UNKNOWN, null, Double.NaN, Double.NaN, note);
    }
}
