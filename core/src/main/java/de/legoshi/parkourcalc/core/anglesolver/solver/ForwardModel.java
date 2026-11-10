package de.legoshi.parkourcalc.core.anglesolver.solver;

public interface ForwardModel {

    ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg);

    PhysicsVariant variant();

    void stepRange(JumpPhysicsInputs scenario, double[] yawAbsDeg, int from, ForwardPath into);
}
