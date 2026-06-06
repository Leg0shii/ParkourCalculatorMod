package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Central-difference gradient for models without analytic Jacobians. Step is in F (degrees);
 *  1e-5 picked for double-precision smoothness vs cancellation balance. */
public final class FiniteDifference {

    private static final double STEP_DEG = 1.0e-5;

    private FiniteDifference() {}

    public static double[] gradient(DifferentiableModel model, Spike0Scenario scenario,
                                    double[] yawAbsDeg, int tick, Spike0Scenario.Axis axis) {
        int n = yawAbsDeg.length;
        double[] g = new double[n];
        double[] copy = yawAbsDeg.clone();
        for (int j = 0; j < n; j++) {
            double saved = copy[j];
            copy[j] = saved + STEP_DEG;
            double up = model.forward(scenario, copy).getPos(tick, axis);
            copy[j] = saved - STEP_DEG;
            double dn = model.forward(scenario, copy).getPos(tick, axis);
            copy[j] = saved;
            g[j] = (up - dn) / (2.0 * STEP_DEG);
        }
        return g;
    }
}
