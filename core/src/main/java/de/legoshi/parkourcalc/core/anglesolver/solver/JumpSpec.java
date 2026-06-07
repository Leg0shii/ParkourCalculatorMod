package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.Collections;
import java.util.List;

/** Immutable problem statement for a single jump. Wraps the mutable JumpPhysicsInputs the model
 *  forward reads, plus the constraint list and objective the solver harness evaluates. */
public final class JumpSpec {

    public final List<JumpConstraint> constraints;
    public final Objective objective;

    private final JumpPhysicsInputs scenario;

    public JumpSpec(JumpPhysicsInputs inputs, List<JumpConstraint> constraints, Objective objective) {
        this.scenario = inputs;
        this.constraints = Collections.unmodifiableList(constraints);
        this.objective = objective;
    }

    public JumpPhysicsInputs asScenario() {
        return scenario;
    }
}
