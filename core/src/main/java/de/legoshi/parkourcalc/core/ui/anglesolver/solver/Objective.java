package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

/** Min/max of pos.axis[tick]. Axis reuses Spike0Scenario.Axis so PathResult.getPos
 *  consumes it directly. Only X and Z are valid for jump objectives; Y is rejected. */
public final class Objective {

    public enum Sense { MAX, MIN }

    public final Spike0Scenario.Axis axis;
    public final Sense sense;
    public final int tick;

    public Objective(Spike0Scenario.Axis axis, Sense sense, int tick) {
        if (axis == Spike0Scenario.Axis.Y) {
            throw new IllegalArgumentException("Y objective unsupported; use X or Z");
        }
        this.axis = axis;
        this.sense = sense;
        this.tick = tick;
    }
}
