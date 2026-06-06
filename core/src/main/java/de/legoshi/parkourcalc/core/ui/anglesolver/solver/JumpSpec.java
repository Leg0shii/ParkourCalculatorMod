package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.Collections;
import java.util.List;

/** Immutable problem statement for a single jump. Holds an internal Spike0Scenario so the model
 *  forward can be reused. The constraint list and objective are evaluated by the solver harness. */
public final class JumpSpec {

    public final int numTicks;
    public final List<JumpConstraint> constraints;
    public final Objective objective;

    private final Spike0Scenario scenario;

    public JumpSpec(Vec3dCore startPos, float startYaw, int numTicks,
                    List<JumpConstraint> constraints, Objective objective) {
        this.numTicks = numTicks;
        this.constraints = Collections.unmodifiableList(constraints);
        this.objective = objective;
        this.scenario = new Spike0Scenario(numTicks);
        this.scenario.startPos = startPos;
        this.scenario.startYaw = startYaw;
    }

    public void setInitialVelocity(Vec3dCore v) {
        this.scenario.initialVelocity = v;
    }

    public void setJumpTick(int jumpTick) {
        this.scenario.jumpTick = jumpTick;
    }

    public void setStrafe(boolean strafe, int strafeSign) {
        this.scenario.strafe = strafe;
        this.scenario.strafeSign = strafeSign;
    }

    public void setStrafePerTick(boolean[] strafePerTick) {
        this.scenario.strafePerTick = strafePerTick;
    }

    public void setSpeedAmplifier(int[] speedAmplifier) {
        this.scenario.speedAmplifier = speedAmplifier;
    }

    public void setSlipPerTick(double[] slipPerTick) {
        this.scenario.slipPerTick = slipPerTick;
    }

    public Spike0Scenario asScenario() {
        return scenario;
    }
}
