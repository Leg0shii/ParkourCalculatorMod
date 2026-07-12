package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.concurrent.atomic.AtomicBoolean;

public interface NodeRuntime {
    NodeOutcome execute(GraphContext ctx, Candidate in, AtomicBoolean nodeToken, long deadlineNanos);
}
