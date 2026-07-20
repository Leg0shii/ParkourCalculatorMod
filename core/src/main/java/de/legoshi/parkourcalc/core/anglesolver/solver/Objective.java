package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Min/max of pos.axis[tick]. Axis reuses JumpPhysicsInputs.Axis so ForwardPath.getPos
 *  consumes it directly. Only X and Z are valid for jump objectives; Y is rejected. */
public final class Objective {

    public enum Sense { MAX, MIN }

    public final JumpPhysicsInputs.Axis axis;
    public final Sense sense;
    public final int tick;
    public final double smoothLambda;

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick) {
        this(axis, sense, tick, 0.0);
    }

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick, double smoothLambda) {
        if (axis == JumpPhysicsInputs.Axis.Y) {
            throw new IllegalArgumentException("Y objective unsupported; use X or Z");
        }
        this.axis = axis;
        this.sense = sense;
        this.tick = tick;
        this.smoothLambda = smoothLambda;
    }

    public double smoothPenalty(double[] yawsAbs) {
        if (smoothLambda <= 0.0 || yawsAbs == null || yawsAbs.length < 2) return 0.0;
        return smoothLambda * Angles.travelDeg(yawsAbs);
    }

    public double scored(double raw, double[] yawsAbs) {
        if (smoothLambda <= 0.0) return raw;
        double pen = smoothPenalty(yawsAbs);
        return sense == Sense.MAX ? raw - pen : raw + pen;
    }
}
