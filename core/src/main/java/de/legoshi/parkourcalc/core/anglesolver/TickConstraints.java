package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.List;

/** All Angle Solver data attached to a single tick: its constraints plus its state override. */
public final class TickConstraints {

    private final List<Constraint> constraints = new ArrayList<>();
    private final StateOverride override = new StateOverride();
    private double landingY = Double.NaN;

    public List<Constraint> getConstraints() {
        return constraints;
    }

    public StateOverride getOverride() {
        return override;
    }

    public double getLandingY() {
        return landingY;
    }

    public boolean hasLandingY() {
        return !Double.isNaN(landingY);
    }

    public void setLandingY(double landingY) {
        this.landingY = landingY;
    }

    public TickConstraints copy() {
        TickConstraints c = new TickConstraints();
        for (Constraint constraint : constraints) {
            c.constraints.add(constraint.copy());
        }
        c.override.copyFrom(override);
        c.landingY = landingY;
        return c;
    }
}
