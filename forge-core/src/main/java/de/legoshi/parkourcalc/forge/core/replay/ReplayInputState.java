package de.legoshi.parkourcalc.forge.core.replay;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.MovementEffect;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;

public final class ReplayInputState {
    public static final class Frame {
        public final boolean sprinting;
        public final boolean airSprinting;
        public final boolean jumpFires;
        public final boolean sneaking;
        public final float forward;
        public final float strafe;
        public final double jumpVelocity;
        public final int speed;
        public final int slowness;

        private Frame(PlayerSprintMachine.Outputs input, boolean airSprint, boolean jump, InputRow row) {
            sprinting = input.next.isSprinting;
            airSprinting = airSprint;
            jumpFires = jump;
            sneaking = row.isKeyActive(InputRow.Key.SNEAK);
            forward = input.moveForward * 0.98F;
            strafe = input.moveStrafe * 0.98F;
            speed = MovementEffect.clientLevel(row.getSpeedAmplifier(), true);
            slowness = MovementEffect.clientLevel(row.getSlownessAmplifier(), true);
            jumpVelocity = (double) 0.42F + (double) ((float) MovementEffect.clientLevel(row.getJumpBoostAmplifier(), true) * 0.1F);
        }

        public JumpPhysicsInputs physics(Vec3dCore position, Vec3dCore velocity, boolean grounded, float slip) {
            JumpPhysicsInputs one = new JumpPhysicsInputs(1);
            one.startPos = position;
            one.initialVelocity = velocity;
            one.yawLockedPerTick = new boolean[]{true};
            one.jumpPerTick = new boolean[]{jumpFires};
            one.slipPerTick = new double[]{grounded ? slip : Double.NaN};
            one.sneakPerTick = new boolean[]{sneaking};
            one.sprintPerTick = new boolean[]{sprinting};
            one.incomingSprint = airSprinting;
            one.speedAmplifier = new int[]{speed};
            one.slownessAmplifier = new int[]{slowness};
            one.forwardInputPerTick = new float[]{forward};
            one.strafeInputPerTick = new float[]{strafe};
            return one;
        }
    }

    private PlayerSprintMachine.State sprint;
    private boolean airSprint;
    private int cooldown;

    public ReplayInputState(StartResumeState resume) {
        if (resume != null && resume.stuckMultiplier != null) throw new IllegalArgumentException("Web resume is not supported by this replay profile");
        sprint = PlayerSprintMachine.State.initial();
        if (resume == null) return;
        boolean sneak = resume.heldLastTick.contains(InputRow.Key.SNEAK);
        float forward = (resume.heldLastTick.contains(InputRow.Key.W) ? 1F : 0F) - (resume.heldLastTick.contains(InputRow.Key.S) ? 1F : 0F);
        if (sneak) forward *= 0.3F;
        sprint = new PlayerSprintMachine.State(sneak, forward, resume.sprintWindow, resume.sprintTicksLeft, resume.sprinting);
        cooldown = resume.jumpCooldown;
        airSprint = resume.sprinting;
        if (!Float.isNaN(resume.airSprintFactor)) {
            if (resume.airSprintFactor != Constants.AIR_SPEED_F && resume.airSprintFactor != Constants.AIR_SPEED_NO_SPRINT_F)
                throw new IllegalArgumentException("Unsupported resumed air movement factor");
            airSprint = resume.airSprintFactor == Constants.AIR_SPEED_F;
        }
    }

    public boolean isSprinting() { return sprint.isSprinting; }

    public Frame tick(InputRow row, boolean grounded, boolean wall) {
        validateRow(row);
        if (cooldown > 0) cooldown--;
        boolean jump = row.isKeyActive(InputRow.Key.JUMP);
        PlayerSprintMachine.Outputs input = PlayerSprintMachine.tick(new PlayerSprintMachine.Inputs(
                row.isKeyActive(InputRow.Key.W), row.isKeyActive(InputRow.Key.S), row.isKeyActive(InputRow.Key.A), row.isKeyActive(InputRow.Key.D),
                jump, row.isKeyActive(InputRow.Key.SNEAK), row.isKeyActive(InputRow.Key.SPRINT), grounded, false, false, wall,
                row.getEffectLevel(MovementEffect.BLINDNESS) > 0, false, 20), sprint);
        boolean fires = jump && grounded && cooldown == 0;
        if (fires) cooldown = 10;
        if (!jump) cooldown = 0;
        Frame result = new Frame(input, airSprint, fires, row);
        if (result.jumpFires && result.jumpVelocity < 0)
            throw new IllegalArgumentException("Wrapped negative jump impulses need an extended movement profile");
        sprint = input.next;
        airSprint = sprint.isSprinting;
        return result;
    }

    public static void validateRow(InputRow row) {
        if (row.isTeleportEnabled() || row.isKeyActive(InputRow.Key.LEFT_CLICK) || row.isKeyActive(InputRow.Key.RIGHT_CLICK))
            throw new IllegalArgumentException("Local replay requires static blocks and no teleports or clicks");
        for (MovementEffect effect : MovementEffect.all())
            if (row.getEffectLevel(effect) != 0 && effect != MovementEffect.SPEED && effect != MovementEffect.SLOWNESS
                    && effect != MovementEffect.JUMP_BOOST && effect != MovementEffect.BLINDNESS)
                throw new IllegalArgumentException("Unsupported replay movement effect: " + effect);
    }
}
