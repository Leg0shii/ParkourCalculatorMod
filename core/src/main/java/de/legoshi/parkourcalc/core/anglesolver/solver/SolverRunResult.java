package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Output of a solver harness: the per-tick absolute facings plus diagnostics. */
public final class SolverRunResult {

    public final double[] yawAbsDeg;
    public final double objectiveValue;
    public final double[] ineqSlack;
    public final double[] eqResidual;

    public SolverRunResult(double[] yawAbsDeg, double objectiveValue, double[] ineqSlack, double[] eqResidual) {
        this.yawAbsDeg = yawAbsDeg;
        this.objectiveValue = objectiveValue;
        this.ineqSlack = ineqSlack;
        this.eqResidual = eqResidual;
    }
}
