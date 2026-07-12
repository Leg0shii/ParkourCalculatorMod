package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;

import java.util.concurrent.atomic.AtomicLong;

public final class CountingForwardModel implements ForwardModel {

    private final ForwardModel inner;
    private final AtomicLong evals = new AtomicLong();

    public CountingForwardModel(ForwardModel inner) {
        this.inner = inner;
    }

    public long evals() {
        return evals.get();
    }

    @Override
    public ForwardPath forward(JumpPhysicsInputs scenario, double[] yawAbsDeg) {
        evals.incrementAndGet();
        return inner.forward(scenario, yawAbsDeg);
    }
}
