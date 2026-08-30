package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class Candidate {

    public final double[] yaws;
    public final double violation;
    public final double objective;
    public final boolean feasible;
    public final double dualGap;

    private Candidate(double[] yaws, double violation, double objective, boolean feasible, double dualGap) {
        this.yaws = yaws;
        this.violation = violation;
        this.objective = objective;
        this.feasible = feasible;
        this.dualGap = dualGap;
    }

    public static Candidate of(GraphContext ctx, double[] yaws) {
        double v = ctx.violationOf(yaws);
        double o = ctx.exactObjective(yaws);
        return new Candidate(yaws, v, o, v <= ctx.feasTol, Double.NaN);
    }

    public Candidate withDualGap(double gap) {
        return new Candidate(yaws, violation, objective, feasible, Math.max(0.0, gap));
    }
}
