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

    public static final float VANILLA_IMPULSE = 0.8F;
    public static final float BPK_IMPULSE = 1.0E-5F;
    public static final float BPK_PURE_FORWARD = 1.0F;

    public static final class Rules {
        public static final Rules VANILLA = new Rules(false, false, false, false, false, false, false, Float.NaN);

        public final boolean active;
        public final boolean normaliseInput;
        public final boolean nonSprintingStrafe;
        public final boolean sprintBackward;
        public final boolean ignoreWallCollision;
        public final boolean sprintDelayOnGround;
        public final boolean bedrockSneak;
        public final float sneakHeight;

        public Rules(boolean active, boolean normaliseInput, boolean nonSprintingStrafe, boolean sprintBackward,
                     boolean ignoreWallCollision, boolean sprintDelayOnGround, boolean bedrockSneak, float sneakHeight) {
            this.active = active;
            this.normaliseInput = normaliseInput;
            this.nonSprintingStrafe = nonSprintingStrafe;
            this.sprintBackward = sprintBackward;
            this.ignoreWallCollision = ignoreWallCollision;
            this.sprintDelayOnGround = sprintDelayOnGround;
            this.bedrockSneak = bedrockSneak;
            this.sneakHeight = sneakHeight;
        }

        public float impulse() {
            return active ? BPK_IMPULSE : VANILLA_IMPULSE;
        }

        public float startThreshold() {
            if (!active) return VANILLA_IMPULSE;
            return nonSprintingStrafe ? BPK_PURE_FORWARD : BPK_IMPULSE;
        }
    }

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
        public final float height;
        public final boolean flying;
        public final boolean sneakForced;

        public Inputs(boolean fwd, boolean back, boolean left, boolean right,
                      boolean jump, boolean sneak, boolean sprintKey,
                      boolean onGround, boolean usingItem, boolean riding,
                      boolean horizontalCollision, boolean hasBlindness,
                      boolean canFly, float foodLevel
        ) {
            this(fwd, back, left, right, jump, sneak, sprintKey, onGround, usingItem, riding,
                    horizontalCollision, hasBlindness, canFly, foodLevel, 1.8F, false, false);
        }

        public Inputs(boolean fwd, boolean back, boolean left, boolean right,
                      boolean jump, boolean sneak, boolean sprintKey,
                      boolean onGround, boolean usingItem, boolean riding,
                      boolean horizontalCollision, boolean hasBlindness,
                      boolean canFly, float foodLevel,
                      float height, boolean flying, boolean sneakForced
        ) {
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
            this.height = height;
            this.flying = flying;
            this.sneakForced = sneakForced;
        }
    }

    public static final class State {
        public final boolean prevSneak;
        public final float prevMoveForward;
        public final int sprintToggleTimer;
        public final int sprintingTicksLeft;
        public final boolean isSprinting;

        public State(boolean prevSneak, float prevMoveForward, int sprintToggleTimer, int sprintingTicksLeft, boolean isSprinting) {
            this.prevSneak = prevSneak;
            this.prevMoveForward = prevMoveForward;
            this.sprintToggleTimer = sprintToggleTimer;
            this.sprintingTicksLeft = sprintingTicksLeft;
            this.isSprinting = isSprinting;
        }

        public static State initial() {
            return new State(false, 0.0F, 0, 0, false);
        }

        public State withIsSprinting(boolean newIsSprinting) {
            return new State(prevSneak, prevMoveForward, sprintToggleTimer, sprintingTicksLeft, newIsSprinting);
        }
    }

    public static final class Outputs {
        public final float moveForward;
        public final float moveStrafe;
        public final boolean isJumping;
        public final boolean sneaking;
        public final boolean dropSprintBoost;
        public final State next;

        public Outputs(float moveForward, float moveStrafe, boolean isJumping, State next) {
            this(moveForward, moveStrafe, isJumping, false, false, next);
        }

        public Outputs(float moveForward, float moveStrafe, boolean isJumping, boolean sneaking,
                       boolean dropSprintBoost, State next) {
            this.moveForward = moveForward;
            this.moveStrafe = moveStrafe;
            this.isJumping = isJumping;
            this.sneaking = sneaking;
            this.dropSprintBoost = dropSprintBoost;
            this.next = next;
        }
    }

    public static Outputs tick(Inputs in, State prev) {
        return tick(in, prev, Rules.VANILLA);
    }

    public static Outputs tick(Inputs in, State prev, Rules rules) {
        Rules r = rules == null ? Rules.VANILLA : rules;
        int sprintToggleTimer = prev.sprintToggleTimer > 0 ? prev.sprintToggleTimer - 1 : 0;
        int sprintingTicksLeft = prev.sprintingTicksLeft;
        boolean isSprinting = prev.isSprinting;

        if (sprintingTicksLeft > 0) {
            sprintingTicksLeft--;
            if (sprintingTicksLeft == 0) {
                isSprinting = false;
            }
        }

        boolean flag1 = prev.prevSneak;
        float impulse = r.impulse();
        float start = r.startThreshold();
        boolean flag2 = prev.prevMoveForward >= impulse;

        float moveForward = axis(in.fwd, in.back);
        float moveStrafe = axis(in.left, in.right);
        if (r.active && r.normaliseInput) {
            float distance = (float) Math.sqrt((double) (moveForward * moveForward + moveStrafe * moveStrafe));
            if (distance >= 1.0E-4F) {
                moveForward /= distance;
                moveStrafe /= distance;
            }
        }
        if (in.sneak) {
            moveForward = (float) ((double) moveForward * 0.3D);
            moveStrafe = (float) ((double) moveStrafe * 0.3D);
        }

        boolean sneakPose = r.active && in.height == r.sneakHeight;
        boolean sneaking = in.sneak || (r.active && r.bedrockSneak && in.sneakForced);
        if (r.active && r.bedrockSneak && !in.sneak && sneakPose && !in.flying) {
            moveStrafe *= 0.3F;
            moveForward *= 0.3F;
        }

        if (in.usingItem && !in.riding) {
            moveStrafe *= 0.2F;
            moveForward *= 0.2F;
            sprintToggleTimer = 0;
        }

        boolean flag3 = in.foodLevel > 6.0F || in.canFly;
        boolean prevSprinting = isSprinting;

        if (in.onGround
                && !flag1
                && !flag2
                && moveForward >= start
                && !isSprinting
                && flag3
                && !in.usingItem
                && !in.hasBlindness
        ) {
            if (sprintToggleTimer <= 0 && !in.sprintKey) {
                sprintToggleTimer = 7;
            } else {
                isSprinting = true;
                sprintingTicksLeft = 600;
            }
        }

        if (!isSprinting
                && moveForward >= start
                && flag3
                && !in.usingItem
                && !in.hasBlindness
                && in.sprintKey
                && !sneakPose
        ) {
            isSprinting = true;
            sprintingTicksLeft = 600;
        }

        float stopForward = r.active && r.sprintBackward && !sneaking && moveForward != 0.0F
                ? BPK_PURE_FORWARD
                : moveForward;
        boolean wall = r.active
                ? (!r.ignoreWallCollision && in.horizontalCollision) || sneakPose
                : in.horizontalCollision;
        if (isSprinting && (stopForward < impulse || wall || !flag3)) {
            isSprinting = false;
            sprintingTicksLeft = 0;
        }

        boolean dropSprintBoost = r.active && r.sprintDelayOnGround && !prevSprinting && isSprinting;

        State next = new State(in.sneak, moveForward, sprintToggleTimer, sprintingTicksLeft, isSprinting);
        return new Outputs(moveForward, moveStrafe, in.jump, sneaking, dropSprintBoost, next);
    }

    public static float heldForward(boolean fwd, boolean back, boolean left, boolean right, boolean sneak, Rules rules) {
        Rules r = rules == null ? Rules.VANILLA : rules;
        float moveForward = axis(fwd, back);
        float moveStrafe = axis(left, right);
        if (r.active && r.normaliseInput) {
            float distance = (float) Math.sqrt((double) (moveForward * moveForward + moveStrafe * moveStrafe));
            if (distance >= 1.0E-4F) {
                moveForward /= distance;
            }
        }
        if (sneak) {
            moveForward = (float) ((double) moveForward * 0.3D);
        }
        return moveForward;
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
