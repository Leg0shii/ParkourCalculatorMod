package de.legoshi.parkourcalc.core.ui.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;

/** Editable, MC-free problem statement for the in-world per-tick-yaw constraint optimizer.
 *  Seeded from a selected TAS tick: startPos / startYaw / incomingVelocity are that tick's state.
 *  The jump is on jumpTick (read from the TAS); the optimizer searches the per-tick facing F[].
 *  Ticks in constraints/objective are counted from the start tick (0-based). */
public final class ConstraintScene {

    public Vec3dCore startPos = new Vec3dCore(0.0, 100.0, 0.0);
    public float startYaw = 0.0F;
    public Vec3dCore incomingVelocity = Vec3dCore.ZERO;
    public int numTicks = 12;
    public int jumpTick = 0;

    public boolean strafe = false;
    public int strafeSign = 1;
    public boolean[] strafePerTick = null;

    public int[] speedAmplifier = null;
    public double[] slipPerTick = null;
    public boolean[] yawLocked = null;

    public final List<JumpConstraint> constraints = new ArrayList<>();

    /** Objective; null defaults to max-Z at the final tick. */
    public Objective objective = null;

    public JumpSpec toJumpSpec() {
        Objective obj = objective != null
                ? objective
                : new Objective(Spike0Scenario.Axis.Z, Objective.Sense.MAX, numTicks);
        JumpSpec spec = new JumpSpec(startPos, startYaw, numTicks, new ArrayList<>(constraints), obj);
        spec.setInitialVelocity(incomingVelocity);
        spec.setJumpTick(jumpTick);
        spec.setStrafe(strafe, strafeSign);
        spec.setStrafePerTick(strafePerTick);
        spec.setSpeedAmplifier(speedAmplifier);
        spec.setSlipPerTick(slipPerTick);
        spec.setYawLocked(yawLocked);
        return spec;
    }
}
