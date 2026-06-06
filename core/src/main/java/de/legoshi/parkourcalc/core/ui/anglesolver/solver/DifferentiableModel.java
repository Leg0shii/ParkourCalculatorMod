package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

public interface DifferentiableModel {

    String name();

    PathResult forward(Spike0Scenario scenario, double[] yawAbsDeg);

    /** Gradient of position[axis][tick] wrt the n-vector yawAbsDeg (degrees). */
    double[] gradient(Spike0Scenario scenario, double[] yawAbsDeg, int tick, Spike0Scenario.Axis axis);

    /** True for M3 straight-through (forward exact, backward smooth/biased). */
    boolean gradientIsBiased();
}
