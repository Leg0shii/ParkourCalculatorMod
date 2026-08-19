package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Min/max of pos.axis[tick]. Axis reuses JumpPhysicsInputs.Axis so ForwardPath.getPos
 *  consumes it directly. Only X and Z are valid for jump objectives; Y is rejected. */
public final class Objective {

    public enum Sense { MAX, MIN }

    public final JumpPhysicsInputs.Axis axis;
    public final Sense sense;
    public final int tick;
    public final double smoothLambda;
    public final Double customYaw;

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick) {
        this(axis, sense, tick, 0.0, null);
    }

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick, double smoothLambda) {
        this(axis, sense, tick, smoothLambda, null);
    }

    public Objective(double customYaw, int tick, double smoothLambda) {
        this(JumpPhysicsInputs.Axis.X, Sense.MAX, tick, smoothLambda, customYaw);
    }

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick, double smoothLambda, Double customYaw) {
        if (axis == JumpPhysicsInputs.Axis.Y) {
            throw new IllegalArgumentException("Y objective unsupported; use X or Z");
        }
        this.axis = axis;
        this.sense = sense;
        this.tick = tick;
        this.smoothLambda = smoothLambda;
        this.customYaw = customYaw;
    }

    public boolean isCustomAngle() {
        return customYaw != null;
    }

    public double evaluate(ForwardPath path) {
        if (customYaw != null) {
            double rad = Math.toRadians(customYaw);
            return -Math.sin(rad) * path.posX[tick] + Math.cos(rad) * path.posZ[tick];
        }
        return path.getPos(tick, axis);
    }

    public double smoothPenalty(double anchorYaw, double[] yawsAbs) {
        if (smoothLambda <= 0.0 || yawsAbs == null || yawsAbs.length < 2) return 0.0;
        return smoothLambda * Angles.wiggleDeg(anchorYaw, yawsAbs);
    }

    public double scored(double raw, double anchorYaw, double[] yawsAbs) {
        if (smoothLambda <= 0.0) return raw;
        double pen = smoothPenalty(anchorYaw, yawsAbs);
        return sense == Sense.MAX ? raw - pen : raw + pen;
    }
}
