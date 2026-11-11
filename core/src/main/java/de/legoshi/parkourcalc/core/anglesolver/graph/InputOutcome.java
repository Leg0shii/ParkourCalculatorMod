package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;

public final class InputOutcome {

    public final TickInputs[] inputs;
    public final TickInputs[] before;
    public final int changed;
    public final int tried;
    public final double watchCost;
    public final double plainWatchCost;

    public InputOutcome(TickInputs[] inputs, TickInputs[] before, int changed, int tried, double watchCost,
                        double plainWatchCost) {
        this.inputs = inputs;
        this.before = before;
        this.changed = changed;
        this.tried = tried;
        this.watchCost = watchCost;
        this.plainWatchCost = plainWatchCost;
    }
}
