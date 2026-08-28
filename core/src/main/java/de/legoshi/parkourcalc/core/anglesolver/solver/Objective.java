package de.legoshi.parkourcalc.core.anglesolver.solver;

/** Min/max of pos.axis[tick]. Axis reuses JumpPhysicsInputs.Axis so ForwardPath.getPos
 *  consumes it directly. Only X and Z are valid for jump objectives; Y is rejected. */
public final class Objective {

    public enum Sense { MAX, MIN }
    public enum Type { POSITION, MOTION }

    public final JumpPhysicsInputs.Axis axis;
    public final Sense sense;
    public final int tick;
    public final double smoothLambda;
    public final Double customYaw;
    public final Type type;

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick) {
        this(axis, sense, tick, 0.0);
    }

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick, double smoothLambda) {
        this(axis, sense, tick, smoothLambda, null, Type.POSITION);
    }

    public Objective(double customYaw, int tick, double smoothLambda) {
        this(customYaw, Type.POSITION, tick, smoothLambda);
    }

    public Objective(double customYaw, Type type, int tick, double smoothLambda) {
        this(JumpPhysicsInputs.Axis.X, Sense.MAX, tick, smoothLambda, customYaw, type);
    }

    public Objective(JumpPhysicsInputs.Axis axis, Sense sense, int tick, double smoothLambda, Double customYaw, Type type) {
        if (axis == JumpPhysicsInputs.Axis.Y) {
            throw new IllegalArgumentException("Y objective unsupported; use X or Z");
        }
        this.axis = axis;
        this.sense = sense;
        this.tick = tick;
        this.smoothLambda = smoothLambda;
        this.customYaw = customYaw;
        this.type = type != null ? type : Type.POSITION;
    }

    public boolean isCustomAngle() {
        return customYaw != null;
    }

    public boolean isMotion() {
        return type == Type.MOTION;
    }

    public double evaluate(ForwardPath path) {
        if (customYaw != null) {
            double rad = Math.toRadians(customYaw);
            double dx = -Math.sin(rad);
            double dz = Math.cos(rad);
            if (type == Type.MOTION) {
                double vx = tick > 0 ? path.posX[tick] - path.posX[tick - 1] : path.posX[tick];
                double vz = tick > 0 ? path.posZ[tick] - path.posZ[tick - 1] : path.posZ[tick];
                return dx * vx + dz * vz;
            } else {
                return dx * path.posX[tick] + dz * path.posZ[tick];
            }
        }
        return path.getPos(tick, axis);
    }

    public double smoothPenalty(double anchorYaw, double[] yawsAbs) {
        if (smoothLambda <= 0.0 || yawsAbs == null || yawsAbs.length < 2) return 0.0;
        double lambda = (isCustomAngle() && isMotion()) ? smoothLambda * 0.05 : smoothLambda;
        return lambda * Angles.turnCost(anchorYaw, yawsAbs);
    }

    public double scored(double raw, double anchorYaw, double[] yawsAbs) {
        if (smoothLambda <= 0.0) return raw;
        double pen = smoothPenalty(anchorYaw, yawsAbs);
        return sense == Sense.MAX ? raw - pen : raw + pen;
    }
}
