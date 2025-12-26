package de.legoshi;

import com.mojang.authlib.GameProfile;
import de.legoshi.ui.InputRow;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A simulated player entity for movement prediction.
 * Stripped down version that only includes necessary movement logic.
 */
public class SimulatorEntity extends PlayerEntity {

    public final SimulatorInput input = new SimulatorInput();
    public Vec3d startPosition;
    public Vec3d startVelocity;

    private boolean inSneakingPose;

    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition, Vec3d startVelocity) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        resetPlayer();
    }

    public void resetPlayer() {
        this.noClip = true;
        this.clearStatusEffects();
        this.setPosition(startPosition);
        this.setVelocity(startVelocity);
        this.setRotation(0, 0);

        this.input.setData(new InputRow());
        this.tick();
        this.tick();

        this.setPosition(startPosition);
    }

    @Override
    public boolean isMainPlayer() {
        return true;
    }

    @Override
    public @Nullable GameMode getGameMode() {
        return GameMode.DEFAULT;
    }

    @Override
    protected void spawnSprintingParticles() {
        // No-op for simulation
    }

    @Override
    protected void tickCramming() {
        // No-op for simulation
    }

    @Override
    public void tickMovement() {
        boolean wasSneak = this.input.playerInput.sneak();
        boolean hasForward = this.input.hasForwardMovement();

        this.inSneakingPose = !this.isSwimming()
                && this.canChangeIntoPose(EntityPose.CROUCHING)
                && this.isSneaking();

        this.input.tick();

        // Handle sprint activation
        if (canStartSprinting() && this.input.playerInput.sprint()) {
            this.setSprinting(true);
        }

        // Handle sprint deactivation
        if (this.isSprinting() && shouldStopSprinting()) {
            this.setSprinting(false);
        }

        // Handle water sinking
        if (this.isTouchingWater() && this.input.playerInput.sneak() && this.shouldSwimInFluids()) {
            this.knockDownwards();
        }

        super.tickMovement();
    }

    private boolean canStartSprinting() {
        return !this.isSprinting()
                && this.input.hasForwardMovement()
                && canSprint()
                && !this.isUsingItem()
                && !hasStatusEffect(StatusEffects.BLINDNESS)
                && (!this.isGliding() || this.isSubmergedInWater())
                && (!this.shouldSlowDown() || this.isSubmergedInWater())
                && (!this.isTouchingWater() || this.isSubmergedInWater());
    }

    private boolean canSprint() {
        return this.hasVehicle()
                || (float) this.getHungerManager().getFoodLevel() > 6.0F
                || this.getAbilities().allowFlying;
    }

    private boolean shouldStopSprinting() {
        if (this.isSwimming()) {
            return shouldStopSwimSprinting();
        }

        return hasStatusEffect(StatusEffects.BLINDNESS)
                || this.hasVehicle()
                || !this.input.hasForwardMovement()
                || !canSprint()
                || (this.horizontalCollision && !this.collidedSoftly)
                || (this.isTouchingWater() && !this.isSubmergedInWater());
    }

    private boolean shouldStopSwimSprinting() {
        return hasStatusEffect(StatusEffects.BLINDNESS)
                || this.hasVehicle()
                || !this.isTouchingWater()
                || (!this.input.hasForwardMovement() && !this.isOnGround() && !this.input.playerInput.sneak())
                || !canSprint();
    }

    @Override
    public void tickMovementInput() {
        Vec2f movement = applyMovementSpeedFactors(this.input.getMovementInput());
        this.sidewaysSpeed = movement.x;
        this.forwardSpeed = movement.y;
        this.jumping = this.input.playerInput.jump();
    }

    @Override
    public boolean isSneaking() {
        return this.input.playerInput.sneak();
    }

    public boolean shouldSlowDown() {
        return this.isInSneakingPose() || this.isCrawling();
    }

    @Override
    public boolean isInSneakingPose() {
        return this.inSneakingPose;
    }

    private Vec2f applyMovementSpeedFactors(Vec2f input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        }

        Vec2f result = input.multiply(0.98F);

        if (this.isUsingItem() && !this.hasVehicle()) {
            result = result.multiply(0.2F);
        }

        if (this.shouldSlowDown()) {
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
}