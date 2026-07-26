package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class Candidate {

    public final double[] yaws;
    public final double violation;
    public final double objective;
    public final boolean feasible;

    private Candidate(double[] yaws, double violation, double objective, boolean feasible) {
        this.yaws = yaws;
        this.violation = violation;
        this.objective = objective;
        this.feasible = feasible;
    }

    public static Candidate of(GraphContext ctx, double[] yaws) {
        double v = ctx.violationOf(yaws);
        double o = ctx.exactObjective(yaws);
        return new Candidate(yaws, v, o, v <= ctx.feasTol);
    }
}
