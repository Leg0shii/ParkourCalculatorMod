package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Min/max of pos.axis[tick]. Axis reuses JumpPhysicsInputs.Axis so ForwardPath.getPos
 *  consumes it directly. Only X and Z are valid for jump objectives; Y is rejected. */
public final class Objective {

    public enum Sense { MAX, MIN }

    public final JumpPhysicsInputs.Axis axis;
    public final Sense sense;
    public final int tick;

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick) {
        if (axis == JumpPhysicsInputs.Axis.Y) {
            throw new IllegalArgumentException("Y objective unsupported; use X or Z");
        }
        this.axis = axis;
        this.sense = sense;
        this.tick = tick;
    }
}
