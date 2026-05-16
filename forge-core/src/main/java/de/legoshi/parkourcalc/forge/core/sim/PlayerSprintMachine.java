package de.legoshi.parkourcalc.forge.core.sim;

/**
 * Pure-Java port of the EntityPlayerSP sprint state machine shared between
 * MC 1.8.9 (MCP stable-22) and 1.12.2 (MCP stable-39). Both Forge loaders
 * gather the primitive inputs from their version's accessors, call tick(),
 * and apply the outputs back onto the MC entity. The algorithm matches MC's
 * source line-for-line; only the names differ between versions, which is why
 * this lives in core as a primitive-in/primitive-out function.
 */
public final class PlayerSprintMachine {

    private PlayerSprintMachine() {}

    public static final class Inputs {
        public final boolean fwd, back, left, right, jump, sneak, sprintKey;
        public final boolean onGround;
        /** isUsingItem() (1.8.9) / isHandActive() (1.12.2), bare. */
        public final boolean usingItem;
        /** isRiding() (1.8.9) / isRiding() (1.12.2). */
        public final boolean riding;
        /** isCollidedHorizontally (1.8.9 field) / collidedHorizontally (1.12.2 field). */
        public final boolean horizontalCollision;
        public final boolean hasBlindness;
        /** capabilities.allowFlying. */
        public final boolean canFly;
        public final float foodLevel;

        public Inputs(boolean fwd, boolean back, boolean left, boolean right,
                      boolean jump, boolean sneak, boolean sprintKey,
                      boolean onGround, boolean usingItem, boolean riding,
                      boolean horizontalCollision, boolean hasBlindness,
                      boolean canFly, float foodLevel) {
            this.fwd = fwd;
            this.back = back;
            this.left = left;
            this.right = right;
            this.jump = jump;
            this.sneak = sneak;
            this.sprintKey = sprintKey;
            this.onGround = onGround;
            this.usingItem = usingItem;
            this.riding = riding;
            this.horizontalCollision = horizontalCollision;
            this.hasBlindness = hasBlindness;
            this.canFly = canFly;
            this.foodLevel = foodLevel;
        }
    }

    public static final class State {
        public final boolean prevSneak;
        public final float prevMoveForward;
        public final int sprintToggleTimer;
        public final boolean isSprinting;

        public State(boolean prevSneak, float prevMoveForward, int sprintToggleTimer, boolean isSprinting) {
            this.prevSneak = prevSneak;
            this.prevMoveForward = prevMoveForward;
            this.sprintToggleTimer = sprintToggleTimer;
            this.isSprinting = isSprinting;
        }

        public static State initial() {
            return new State(false, 0.0F, 0, false);
        }

        public State withIsSprinting(boolean newIsSprinting) {
            return new State(prevSneak, prevMoveForward, sprintToggleTimer, newIsSprinting);
        }
    }

    public static final class Outputs {
        public final float moveForward;
        public final float moveStrafe;
        public final boolean isJumping;
        public final State next;

        public Outputs(float moveForward, float moveStrafe, boolean isJumping, State next) {
            this.moveForward = moveForward;
            this.moveStrafe = moveStrafe;
            this.isJumping = isJumping;
            this.next = next;
        }
    }

    public static Outputs tick(Inputs in, State prev) {
        int sprintToggleTimer = prev.sprintToggleTimer > 0 ? prev.sprintToggleTimer - 1 : 0;

        boolean flag1 = prev.prevSneak;
        float f = 0.8F;
        boolean flag2 = prev.prevMoveForward >= f;

        float moveForward = axis(in.fwd, in.back);
        float moveStrafe = axis(in.left, in.right);
        if (in.sneak) {
            moveForward *= 0.3F;
            moveStrafe *= 0.3F;
        }

        if (in.usingItem && !in.riding) {
            moveStrafe *= 0.2F;
            moveForward *= 0.2F;
            sprintToggleTimer = 0;
        }

        boolean flag3 = in.foodLevel > 6.0F || in.canFly;
        boolean isSprinting = prev.isSprinting;

        if (in.onGround
                && !flag1
                && !flag2
                && moveForward >= f
                && !isSprinting
                && flag3
                && !in.usingItem
                && !in.hasBlindness) {
            if (sprintToggleTimer <= 0 && !in.sprintKey) {
                sprintToggleTimer = 7;
            } else {
                isSprinting = true;
            }
        }

        if (!isSprinting
                && moveForward >= f
                && flag3
                && !in.usingItem
                && !in.hasBlindness
                && in.sprintKey) {
            isSprinting = true;
        }

        if (isSprinting && (moveForward < f || in.horizontalCollision || !flag3)) {
            isSprinting = false;
        }

        State next = new State(in.sneak, moveForward, sprintToggleTimer, isSprinting);
        return new Outputs(moveForward, moveStrafe, in.jump, next);
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
