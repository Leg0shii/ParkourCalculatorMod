package de.legoshi.parkourcalc.fabric.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
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

    public void beginSubtickCapture() {
        subtickBuf.clear();
        capturing = true;
    }

    public List<Vec3dCore> endSubtickCapture() {
        capturing = false;
        List<Vec3dCore> result = new ArrayList<Vec3dCore>(subtickBuf);
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

        Vec3dCore startCore = new Vec3dCore(before.x, before.y, before.z);
        double cx = after.x - before.x;
        double cy = after.y - before.y;
        double cz = after.z - before.z;
        boolean xBeforeZ = Math.abs(motion.x) >= Math.abs(motion.z);

        if (subtickBuf.isEmpty()) {
            subtickBuf.add(startCore);
        }
        subtickBuf.add(new Vec3dCore(before.x, before.y + cy, before.z));
        if (xBeforeZ) {
            subtickBuf.add(new Vec3dCore(before.x + cx, before.y + cy, before.z));
            subtickBuf.add(new Vec3dCore(before.x + cx, before.y + cy, before.z + cz));
        } else {
            subtickBuf.add(new Vec3dCore(before.x, before.y + cy, before.z + cz));
            subtickBuf.add(new Vec3dCore(before.x + cx, before.y + cy, before.z + cz));
        }
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
        this.setPosition(startPosition);
        this.setVelocity(startVelocity);
        this.setRotation(startYaw, 0);

        this.input.setData(new InputRow());
        this.ticksLeftToDoubleTapSprint = 0;
        this.tick();
        this.tick();

        this.setPosition(startPosition);
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
    public void tickMovement() {
        if (this.ticksLeftToDoubleTapSprint > 0) {
            this.ticksLeftToDoubleTapSprint--;
        }

        // Capture pre-input.tick() so bl2/bl3 hold the previous tick's state.
        boolean bl2 = this.input.playerInput.sneak();
        boolean bl3 = this.input.hasForwardMovement();

        // MC sets inSneakingPose earlier in tickMovement based on the persistent
        // isSneaking() flag; we mirror that with the per-tick input value.
        this.inSneakingPose = !this.isSwimming() && this.canChangeIntoPose(EntityPose.CROUCHING) && this.isSneaking();

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
            } else if (this.shouldStopSprinting()) {
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
                && this.canSprint(this.getAbilities().flying)
                && !this.isUsingItem()
                && (!this.isGliding() || this.isSubmergedInWater())
                && (!this.shouldSlowDown() || this.isSubmergedInWater());
    }

    private boolean shouldStopSprinting() {
        return !this.canSprint(this.getAbilities().flying)
                || !this.input.hasForwardMovement()
                || this.horizontalCollision && !this.collidedSoftly;
    }

    private boolean shouldStopSwimSprinting() {
        return !this.canSprint(true)
                || !this.isTouchingWater()
                || !this.input.hasForwardMovement() && !this.isOnGround() && !this.input.playerInput.sneak();
    }

    private boolean canSprint(boolean allowTouchingWater) {
        return !this.hasBlindnessEffect()
                && this.canSprint()
                && (!this.hasVehicle() || this.canVehicleSprint(this.getVehicle()))
                && (allowTouchingWater || !this.isPartlyTouchingWater());
    }

    private boolean canVehicleSprint(net.minecraft.entity.Entity vehicle) {
        return vehicle.canSprintAsVehicle() && vehicle.isLogicalSideForUpdatingMovement();
    }

    private boolean canSprint() {
        return this.hasVehicle() || this.getHungerManager().getFoodLevel() > 6.0F || this.getAbilities().allowFlying;
    }

    /** Lives on ClientPlayerEntity in MC, not PlayerEntity, so we redeclare it. */
    public boolean shouldSlowDown() {
        return this.isInSneakingPose() || this.isCrawling();
    }

    @Override
    protected boolean hasCollidedSoftly(Vec3d adjustedMovement) {
        float f = this.getYaw() * (float) (Math.PI / 180.0);
        double d = MathHelper.sin(f);
        double e = MathHelper.cos(f);
        double g = this.sidewaysSpeed * e - this.forwardSpeed * d;
        double h = this.forwardSpeed * e + this.sidewaysSpeed * d;
        double i = MathHelper.square(g) + MathHelper.square(h);
        double j = MathHelper.square(adjustedMovement.x) + MathHelper.square(adjustedMovement.z);
        if (i < 1.0E-5F || j < 1.0E-5F) {
            return false;
        }
        double k = g * adjustedMovement.x + h * adjustedMovement.z;
        double l = Math.acos(k / Math.sqrt(i * j));
        return l < 0.13962634F;
    }

    @Override
    public void tickMovementInput() {
        Vec2f movement = applyMovementSpeedFactors(this.input.getMovementInput());
        this.sidewaysSpeed = movement.x;
        this.forwardSpeed = movement.y;
        this.jumping = this.input.playerInput.jump();
    }

    private Vec2f applyMovementSpeedFactors(Vec2f raw) {
        if (raw.lengthSquared() == 0.0F) {
            return raw;
        }
        Vec2f result = raw.multiply(0.98F);
        if (this.isSneaking()) {
            float sneakSpeed = (float) this.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
            result = result.multiply(sneakSpeed);
        }
        return normalizeDirectionalMovement(result);
    }

    private static Vec2f normalizeDirectionalMovement(Vec2f vec) {
        float length = vec.length();
        if (length <= 0.0F) {
            return vec;
        }
        Vec2f normalized = vec.multiply(1.0F / length);
        float multiplier = getDirectionalMultiplier(normalized);
        float clampedLength = Math.min(length * multiplier, 1.0F);
        return normalized.multiply(clampedLength);
    }

    private static float getDirectionalMultiplier(Vec2f normalized) {
        float absX = Math.abs(normalized.x);
        float absY = Math.abs(normalized.y);
        float ratio = absY > absX ? absX / absY : absY / absX;
        return MathHelper.sqrt(1.0F + MathHelper.square(ratio));
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
        return c;
    }

    public void restoreCheckpoint(Checkpoint c) {
        this.setPosition(c.pos);
        this.setVelocity(c.velocity);
        this.setYaw(c.yaw);
        this.setOnGround(c.onGround);
        this.horizontalCollision = c.horizontalCollision;
        this.collidedSoftly = c.collidedSoftly;
        this.setSprinting(c.sprinting);
        this.ticksLeftToDoubleTapSprint = c.ticksLeftToDoubleTapSprint;
        this.input.playerInput = c.playerInput;
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
    }
}
