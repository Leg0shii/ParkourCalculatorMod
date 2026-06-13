package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class VelocityAngleSolver {

    private static final double COARSE_STEP_DEG = 0.2;

    private static final double REFINE_BRACKET_DEG = COARSE_STEP_DEG;

    private static final double REFINE_TOL_DEG = 1.0e-4;

    private static final double INV_GOLDEN = (Math.sqrt(5.0) - 1.0) / 2.0;

    public static final class Result {
        public final double angleDeg;
        public final double angle360Deg;
        public final Vec3dCore initialVelocity;
        public final ForwardPath path;
        public final double maxViolation;

        Result(double angleDeg, Vec3dCore initialVelocity, ForwardPath path, double maxViolation) {
            this.angleDeg = angleDeg;
            this.angle360Deg = angleDeg < 0 ? angleDeg + 360.0 : angleDeg;
            this.initialVelocity = initialVelocity;
            this.path = path;
            this.maxViolation = maxViolation;
        }
    }

    private final ForwardModel model;
    private final JumpPhysicsInputs scenario;
    private final double[] gameFacings;
    private final JumpConstraintCompiler.Compiled constraints;
    private final Objective objective;
    private final double speed;
    private final double vy0;

    public VelocityAngleSolver(ForwardModel model, JumpSpec spec, double[] gameFacings, double speed) {
        this.model = model;
        this.scenario = spec.asScenario();
        this.gameFacings = gameFacings.clone();
        this.constraints = JumpConstraintCompiler.compile(spec);
        this.objective = spec.objective;
        this.speed = Math.max(0.0, speed);
        this.vy0 = scenario.initialVelocity.y;
    }

    public static Vec3dCore velocityFor(double thetaDeg, double speed, double vy) {
        float rad = (float) thetaDeg * (float) (Math.PI / 180.0);
        double vx = -McSineTable.sinStep(rad) * speed;
        double vz = McSineTable.cosStep(rad) * speed;
        return new Vec3dCore(vx, vy, vz);
    }

    public Result solve(double feasTol, AtomicBoolean cancel) {
        double tol = Math.max(0.0, feasTol);
        double bestTheta = 0.0;
        double bestScore = Double.POSITIVE_INFINITY;
        int steps = (int) Math.round(360.0 / COARSE_STEP_DEG);
        for (int i = 0; i < steps; i++) {
            if (cancel != null && cancel.get()) return null;
            double theta = i * COARSE_STEP_DEG;
            double score = score(theta, tol);
            if (score < bestScore) {
                bestScore = score;
                bestTheta = theta;
            }
        }

        double refined = refine(bestTheta - REFINE_BRACKET_DEG, bestTheta + REFINE_BRACKET_DEG, tol, cancel);
        if (cancel != null && cancel.get()) return null;

        double wrapped = Angles.wrap(refined);
        ForwardPath path = evaluate(wrapped);
        double viol = constraints.maxViolation(gameFacings, path);
        return new Result(wrapped, velocityFor(wrapped, speed, vy0), path, viol);
    }

    private double refine(double lo, double hi, double feasTol, AtomicBoolean cancel) {
        double a = lo;
        double b = hi;
        double c = b - INV_GOLDEN * (b - a);
        double d = a + INV_GOLDEN * (b - a);
        double fc = score(c, feasTol);
        double fd = score(d, feasTol);
        for (int it = 0; it < 60 && (b - a) > REFINE_TOL_DEG; it++) {
            if (cancel != null && cancel.get()) break;
            if (fc < fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - INV_GOLDEN * (b - a);
                fc = score(c, feasTol);
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + INV_GOLDEN * (b - a);
                fd = score(d, feasTol);
            }
        }
        return 0.5 * (a + b);
    }

    private double score(double theta, double feasTol) {
        ForwardPath path = evaluate(theta);
        double viol = Math.max(0.0, constraints.maxViolation(gameFacings, path) - feasTol);
        double objTerm = objective.sense == Objective.Sense.MAX
                ? -path.getPos(objective.tick, objective.axis)
                : path.getPos(objective.tick, objective.axis);
        return viol + 1.0e-6 * objTerm;
    }

    private ForwardPath evaluate(double theta) {
        JumpPhysicsInputs sc = withInitialVelocity(velocityFor(theta, speed, vy0));
        return model.forward(sc, gameFacings);
    }

    private JumpPhysicsInputs withInitialVelocity(Vec3dCore v) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(scenario.numTicks);
        sc.startPos = scenario.startPos;
        sc.startYaw = scenario.startYaw;
        sc.initialVelocity = v;
        sc.jumpTick = scenario.jumpTick;
        sc.jumpPerTick = scenario.jumpPerTick;
        sc.strafeSign = scenario.strafeSign;
        sc.strafePerTick = scenario.strafePerTick;
        sc.speedAmplifier = scenario.speedAmplifier;
        sc.slipPerTick = scenario.slipPerTick;
        sc.yawLockedPerTick = scenario.yawLockedPerTick;
        sc.sprintPerTick = scenario.sprintPerTick;
        sc.forwardInputPerTick = scenario.forwardInputPerTick;
        sc.strafeInputPerTick = scenario.strafeInputPerTick;
        return sc;
    }
}
