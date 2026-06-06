package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Output of a solver harness: the per-tick absolute facings plus diagnostics. (Extracted from the
 *  spike0 JumpSolverHarness.Result so the gradient harness needs no commons-math3 dependency.) */
public final class HarnessResult {

    public final double[] yawAbsDeg;
    public final long wallMs;
    public final int iters;
    public final boolean success;
    public final double objectiveValue;
    public final double[] ineqSlack;
    public final double[] eqResidual;

    public HarnessResult(double[] yawAbsDeg, long wallMs, int iters, boolean success,
                         double objectiveValue, double[] ineqSlack, double[] eqResidual) {
        this.yawAbsDeg = yawAbsDeg;
        this.wallMs = wallMs;
        this.iters = iters;
        this.success = success;
        this.objectiveValue = objectiveValue;
        this.ineqSlack = ineqSlack;
        this.eqResidual = eqResidual;
    }
}
