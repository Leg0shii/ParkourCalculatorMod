package de.legoshi.parkourcalc.core.anglesolver.solver;

public interface ForwardModel {

    ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg);
}
