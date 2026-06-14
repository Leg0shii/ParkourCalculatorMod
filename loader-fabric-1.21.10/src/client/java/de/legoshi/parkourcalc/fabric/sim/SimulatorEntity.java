package de.legoshi.parkourcalc.fabric.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.sim.SubtickPath;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorEntity extends PlayerEntity {

    public final SimulatorInput input = new SimulatorInput();
    public Vec3d startPosition;
    public Vec3d startVelocity;
    public float startYaw;

    // No shadow prev* fields: SimulatorInput.playerInput holds the previous tick's
    // state until we call this.input.tick(), matching how MC's persistent Input
    // field works, so MC's `bl2`/`bl3` capture pattern works as-is.
    private int ticksLeftToDoubleTapSprint = 0;
    private boolean inSneakingPose;

    private final ArrayList<Vec3dCore> subtickBuf = new ArrayList<Vec3dCore>(8);
    private boolean capturing = false;

    private double lastCollisionAngleDegrees = Double.NaN;
    private boolean collisionAngleComputedThisTick = false;

    public double getLastCollisionAngleDegrees() {
        return lastCollisionAngleDegrees;
    }

    public void beginSubtickCapture() {
        subtickBuf.clear();
        capturing = true;
    }

    public List<Vec3dCore> endSubtickCapture() {
        capturing = false;
        List<Vec3dCore> result = new ArrayList<>(subtickBuf);
        subtickBuf.clear();
        return result;
    }

    /** Axis order mirrors Direction.method_73163 (Y first, X/Z by |component|). */
    @Override
    public void move(MovementType type, Vec3d motion) {
        if (!capturing) {
            super.move(type, motion);
            return;
        }
        Vec3d before = this.getEntityPos();
        super.move(type, motion);
        Vec3d after = this.getEntityPos();

        double cx = after.x - before.x;
        double cy = after.y - before.y;
        double cz = after.z - before.z;
        SubtickPath.appendMove(subtickBuf, before.x, before.y, before.z, cx, cy, cz,
                Math.abs(motion.x) >= Math.abs(motion.z));
    }

    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition, Vec3d startVelocity, float startYaw) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        resetPlayer();
    }

    public void resetPlayer() {
        this.noClip = true;
        this.clearStatusEffects();
        this.setHealth(this.getMaxHealth());
        this.setPosition(startPosition);
        this.setVelocity(Vec3d.ZERO);
        this.setRotation(startYaw, 0);

        this.input.setData(new InputRow());
        this.ticksLeftToDoubleTapSprint = 0;
        this.tick();
        this.tick();

        this.setPosition(startPosition);
        this.setVelocity(startVelocity);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void addExhaustion(float amount) {
    }

    /** Required so canMoveVoluntarily/isLogicalSideForUpdatingMovement return true
     *  on the client world; without it the entity's physics never advance. */
    @Override
    public boolean isMainPlayer() {
        return true;
    }

    /** Lets the entity tick on ServerWorld: Entity.isLogicalSideForUpdatingMovement is final
     *  and returns !isControlledByPlayer() there, and PlayerEntity defaults that to true. */
    @Override
    public boolean isControlledByPlayer() {
        return false;
    }

    @Override
    public @Nullable GameMode getGameMode() {
        return GameMode.DEFAULT;
    }

    /** No-op so the simulator doesn't spawn particles in the real world. */
    @Override
    protected void spawnSprintingParticles() {
    }

    /** No-op so dragging a TAS through water doesn't spam splash/bubble particles
     *  (and the splash sound) on every re-simulation. */
    @Override
    protected void onSwimmingStart() {
    }

    @Override
    public void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
    }

    /** No-op: vanilla calls discard() when Y < bottomY - 64, which sets removalReason and
     *  makes every subsequent tick() a no-op. Simulator paths legitimately fall past world
     *  bottom (TAS into the void) and must keep ticking; resetPlayer() snaps position back. */
    @Override
    protected void tickInVoid() {
    }

    // LivingEntity gates clearStatusEffects / onStatusEffect* on !world.isClient(),
    // which would make every effect call a no-op in the client world. Reimplement
    // without the gate so attribute modifiers actually attach and detach.

    @Override
    public boolean clearStatusEffects() {
        Map<net.minecraft.registry.entry.RegistryEntry<StatusEffect>, StatusEffectInstance> active = this.getActiveStatusEffects();
        if (active.isEmpty()) return false;
        Map<net.minecraft.registry.entry.RegistryEntry<StatusEffect>, StatusEffectInstance> copy = new HashMap<>(active);
        active.clear();
        this.onStatusEffectsRemoved(copy.values());
        return true;
    }

    @Override
    protected void onStatusEffectApplied(StatusEffectInstance effect, @Nullable Entity source) {
        effect.getEffectType().value().onApplied(this.getAttributes(), effect.getAmplifier());
    }

    @Override
    protected void onStatusEffectUpgraded(StatusEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        if (reapplyEffect) {
            StatusEffect type = effect.getEffectType().value();
            type.onRemoved(this.getAttributes());
            type.onApplied(this.getAttributes(), effect.getAmplifier());
        }
    }

    @Override
    protected void onStatusEffectsRemoved(Collection<StatusEffectInstance> effects) {
        for (StatusEffectInstance e : effects) {
            e.getEffectType().value().onRemoved(this.getAttributes());
        }
    }

    /** No-op so the simulator can't shove the real player or other world entities. */
    @Override
    protected void tickCramming() {
    }

    /**
     * Mirrors ClientPlayerEntity.tickMovement sprint block (yarn 1.21.10+build.2-v2).
     * Sprint-window length is hard-coded to vanilla's 7 (MC reads it from
     * client.options.getSprintWindow(), which the simulator has no access to).
     */
    @Override
    public void tick() {
        Vec3d before = this.getEntityPos();
        super.tick();
        if (!this.collisionAngleComputedThisTick) {
            Vec3d after = this.getEntityPos();
            this.lastCollisionAngleDegrees = computeCollisionAngleDegrees(
                    after.x - before.x, after.z - before.z);
        }
    }

    @Override
    public void tickMovement() {
        this.lastCollisionAngleDegrees = Double.NaN;
        this.collisionAngleComputedThisTick = false;

        if (this.ticksLeftToDoubleTapSprint > 0) {
            this.ticksLeftToDoubleTapSprint--;
        }

        // Capture pre-input.tick() so bl2/bl3 hold the previous tick's state.
        boolean bl2 = this.input.playerInput.sneak();
        boolean bl3 = this.input.hasForwardMovement();

        // Mirrors ClientPlayerEntity 1:1; the !canChangeIntoPose(STANDING) term is vanilla's
        // forced crouch under a low ceiling, which keeps the slowdown after sneak is released.
        this.inSneakingPose = !this.getAbilities().flying
                && !this.isSwimming()
                && !this.hasVehicle()
                && this.canChangeIntoPose(EntityPose.CROUCHING)
                && (this.isSneaking() || !this.isSleeping() && !this.canChangeIntoPose(EntityPose.STANDING));

        this.input.tick();

        if (bl2 || this.isUsingItem() && !this.hasVehicle() || this.input.playerInput.backward()) {
            this.ticksLeftToDoubleTapSprint = 0;
        }

        if (this.canStartSprinting()) {
            if (!bl3) {
                if (this.ticksLeftToDoubleTapSprint > 0) {
                    this.setSprinting(true);
                } else {
                    this.ticksLeftToDoubleTapSprint = 7;
                }
            }

            if (this.input.playerInput.sprint()) {
                this.setSprinting(true);
            }
        }

        if (this.isSprinting()) {
            if (this.isSwimming()) {
                if (this.shouldStopSwimSprinting()) {
                    this.setSprinting(false);
                }
            } else if (this.shouldStopRunSprinting()) {
                this.setSprinting(false);
            }
        }

        super.tickMovement();
    }

    /** Read sneak directly from the per-tick input so any caller during this tick
     *  sees the current value without waiting on data-tracker sync. */
    @Override
    public boolean isSneaking() {
        return this.input.playerInput.sneak();
    }

    @Override
    public boolean isInSneakingPose() {
        return this.inSneakingPose;
    }

    // Helpers copied 1-to-1 from ClientPlayerEntity (private there).

    private boolean canStartSprinting() {
        return !this.isSprinting()
                && this.input.hasForwardMovement()
                && this.isSprintingPossible(this.getAbilities().flying)
                && !this.isUsingItem()
                && (!this.isGliding() || this.isSubmergedInWater())
                && (!this.isMovingSlowly() || this.isSubmergedInWater());
    }

    private boolean shouldStopRunSprinting() {
        return !this.isSprintingPossible(this.getAbilities().flying)
                || !this.input.hasForwardMovement()
                || this.horizontalCollision && !this.collidedSoftly;
    }

    private boolean shouldStopSwimSprinting() {
        return !this.isSprintingPossible(true)
                || !this.isTouchingWater()
                || !this.input.hasForwardMovement() && !this.isOnGround() && !this.input.playerInput.sneak();
    }

    private boolean isSprintingPossible(boolean allowedInShallowWater) {
        return !this.hasBlindnessEffect()
                && this.hasEnoughFoodToSprint()
                && (!this.hasVehicle() || this.vehicleCanSprint(this.getVehicle()))
                && (allowedInShallowWater || !this.isPartlyTouchingWater());
    }

    private boolean vehicleCanSprint(Entity vehicle) {
        return vehicle.canSprintAsVehicle() && vehicle.isLogicalSideForUpdatingMovement();
    }

    private boolean hasEnoughFoodToSprint() {
        return this.hasVehicle() || this.getHungerManager().getFoodLevel() > 6.0F || this.getAbilities().allowFlying;
    }

    /** Lives on LocalPlayer in MC, not Player, so we redeclare it. */
    public boolean isMovingSlowly() {
        return this.isInSneakingPose() || this.isCrawling();
    }

    @Override
    protected boolean hasCollidedSoftly(Vec3d adjustedMovement) {
        double angleDeg = computeCollisionAngleDegrees(adjustedMovement.x, adjustedMovement.z);
        if (Double.isNaN(angleDeg)) {
            return false;
        }
        this.lastCollisionAngleDegrees = angleDeg;
        this.collisionAngleComputedThisTick = true;
        return Math.toRadians(angleDeg) < 0.13962634F;
    }

    private double computeCollisionAngleDegrees(double movementX, double movementZ) {
        float yRotInRadians = this.getYaw() * (float) (Math.PI / 180.0);
        double yRotSin = MathHelper.sin(yRotInRadians);
        double yRotCos = MathHelper.cos(yRotInRadians);
        double globalXA = this.sidewaysSpeed * yRotCos - this.forwardSpeed * yRotSin;
        double globalZA = this.forwardSpeed * yRotCos + this.sidewaysSpeed * yRotSin;
        double aLengthSquared = MathHelper.square(globalXA) + MathHelper.square(globalZA);
        double movementLengthSquared = MathHelper.square(movementX) + MathHelper.square(movementZ);
        if (aLengthSquared < 1.0E-5F || movementLengthSquared < 1.0E-5F) {
            return Double.NaN;
        }
        double dotProduct = globalXA * movementX + globalZA * movementZ;
        return Math.toDegrees(Math.acos(dotProduct / Math.sqrt(aLengthSquared * movementLengthSquared)));
    }

    @Override
    public void tickMovementInput() {
        Vec2f movement = modifyInput(this.input.getMovementInput());
        this.sidewaysSpeed = movement.x;
        this.forwardSpeed = movement.y;
        this.jumping = this.input.playerInput.jump();
    }

    private Vec2f modifyInput(Vec2f input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        }
        Vec2f newInput = input.multiply(0.98F);
        // Vanilla gates on shouldSlowDown() (pose from the previous tick's sneak), so the
        // slowdown lands one tick after the sneak input; isSneaking() would apply it a tick early.
        if (this.isMovingSlowly()) {
            float sneakingMovementFactor = (float) this.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
            newInput = newInput.multiply(sneakingMovementFactor);
        }
        return modifyInputSpeedForSquareMovement(newInput);
    }

    private static Vec2f modifyInputSpeedForSquareMovement(Vec2f input) {
        float length = input.length();
        if (length <= 0.0F) {
            return input;
        }
        Vec2f direction = input.multiply(1.0F / length);
        float distanceToUnitSquare = distanceToUnitSquare(direction);
        float modifiedLength = Math.min(length * distanceToUnitSquare, 1.0F);
        return direction.multiply(modifiedLength);
    }

    private static float distanceToUnitSquare(Vec2f direction) {
        float directionX = Math.abs(direction.x);
        float directionY = Math.abs(direction.y);
        float tan = directionY > directionX ? directionX / directionY : directionY / directionX;
        return MathHelper.sqrt(1.0F + MathHelper.square(tan));
    }

    public Checkpoint saveCheckpoint() {
        Checkpoint c = new Checkpoint();
        c.pos = this.getEntityPos();
        c.velocity = this.getVelocity();
        c.yaw = this.getYaw();
        c.onGround = this.isOnGround();
        c.horizontalCollision = this.horizontalCollision;
        c.collidedSoftly = this.collidedSoftly;
        c.sprinting = this.isSprinting();
        c.ticksLeftToDoubleTapSprint = this.ticksLeftToDoubleTapSprint;
        c.playerInput = this.input.playerInput;
        c.jumpingCooldown = this.jumpingCooldown;
        return c;
    }

    public void restoreCheckpoint(Checkpoint c) {
        // Start from the clean spawn baseline (same as a full run's resetToStart) so no uncaptured
        // entity state from the previous run leaks in; the overlay below restores the history it carries.
        resetPlayer();
        this.setPosition(c.pos);
        this.setVelocity(c.velocity);
        this.setYaw(c.yaw);
        this.setOnGround(c.onGround);
        this.horizontalCollision = c.horizontalCollision;
        this.collidedSoftly = c.collidedSoftly;
        this.setSprinting(c.sprinting);
        this.ticksLeftToDoubleTapSprint = c.ticksLeftToDoubleTapSprint;
        this.input.playerInput = c.playerInput;
        this.jumpingCooldown = c.jumpingCooldown;
    }

    public static void applyCheckpoint(net.minecraft.client.network.ClientPlayerEntity p, de.legoshi.parkourcalc.core.sim.Checkpoint state) {
        if (!(state instanceof Checkpoint)) return;
        Checkpoint c = (Checkpoint) state;
        p.setOnGround(c.onGround);
        p.horizontalCollision = c.horizontalCollision;
        p.collidedSoftly = c.collidedSoftly;
        p.setSprinting(c.sprinting);
        p.jumpingCooldown = c.jumpingCooldown;
    }

    public static final class Checkpoint implements de.legoshi.parkourcalc.core.sim.Checkpoint {
        Vec3d pos;
        Vec3d velocity;
        float yaw;
        boolean onGround;
        boolean horizontalCollision;
        boolean collidedSoftly;
        boolean sprinting;
        int ticksLeftToDoubleTapSprint;
        PlayerInput playerInput;
        int jumpingCooldown;
    }
}
