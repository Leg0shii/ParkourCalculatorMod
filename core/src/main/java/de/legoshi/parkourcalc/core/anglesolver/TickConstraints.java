package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.List;

/** All Angle Solver data attached to a single tick: its constraints plus its state override. */
public final class TickConstraints {

    private final List<Constraint> constraints = new ArrayList<>();
    private final StateOverride override = new StateOverride();

    public List<Constraint> getConstraints() {
        return constraints;
    }

    public StateOverride getOverride() {
        return override;
    }
}
