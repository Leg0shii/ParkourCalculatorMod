package de.legoshi;

import com.mojang.authlib.GameProfile;
import de.legoshi.ui.InputTick;
import net.minecraft.entity.Entity;
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

public class SimulatorEntity extends PlayerEntity {

    public SimulatorInput input = new SimulatorInput(new InputTick());

    public Vec3d startPosition;
    public Vec3d startVelocity;

    private int ticksLeftToDoubleTapSprint;
    private boolean inSneakingPose;

    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition, Vec3d startVelocity) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;

        resetPlayer();
    }

    public void resetPlayer() {
        this.clearStatusEffects();
        this.setPosition(startPosition);
        this.setVelocity(startVelocity);
        this.setRotation(0, 0);

        this.input.setTick(new InputTick());
        this.tick();
        this.tick();

        this.setPosition(startPosition);
    }

    public boolean isMainPlayer() {
        return true;
    }

    @Override
    public @Nullable GameMode getGameMode() {
        return GameMode.DEFAULT;
    }

    @Override
    protected void spawnSprintingParticles() {
    }

    private void collideWithEntity(Entity entity) {
        // entity.onPlayerCollision(this);
    }

    public void tickMovement() {
        if (this.ticksLeftToDoubleTapSprint > 0) {
            --this.ticksLeftToDoubleTapSprint;
        }

        boolean bl = this.input.playerInput.jump();
        boolean bl2 = this.input.playerInput.sneak();
        boolean bl3 = this.input.hasForwardMovement();
        this.inSneakingPose = !this.isSwimming() && this.canChangeIntoPose(EntityPose.CROUCHING) && this.isSneaking();
        this.input.tick();

        if (bl2 || this.isUsingItem() && !this.hasVehicle() || this.input.playerInput.backward()) {
            this.ticksLeftToDoubleTapSprint = 0;
        }

        if (this.canStartSprinting()) {
            if (!bl3) {
                if (this.ticksLeftToDoubleTapSprint > 0) {
                    // this.setSprinting(true);
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

        if (this.isTouchingWater() && this.input.playerInput.sneak() && this.shouldSwimInFluids()) {
            this.knockDownwards();
        }

        super.tickMovement();
    }

    private boolean shouldStopSprinting() {
        return this.isBlind() || this.hasVehicle() || !this.input.hasForwardMovement() || !this.canSprint() || this.horizontalCollision && !this.collidedSoftly || this.isTouchingWater() && !this.isSubmergedInWater();
    }

    private boolean shouldStopSwimSprinting() {
        return this.isBlind() || this.hasVehicle() || !this.isTouchingWater() || !this.input.hasForwardMovement() && !this.isOnGround() && !this.input.playerInput.sneak() || !this.canSprint();
    }

    public void tickMovementInput() {
        Vec2f vec2f = this.applyMovementSpeedFactors(this.input.getMovementInput());
        this.sidewaysSpeed = vec2f.x;
        this.forwardSpeed = vec2f.y;
        this.jumping = this.input.playerInput.jump();
    }

    public boolean isSneaking() {
        return this.input.playerInput.sneak();
    }

    private boolean canStartSprinting() {
        return !this.isSprinting() && this.input.hasForwardMovement() && this.canSprint() && !this.isUsingItem() && !this.isBlind() && (!this.isGliding() || this.isSubmergedInWater()) && (!this.shouldSlowDown() || this.isSubmergedInWater()) && (!this.isTouchingWater() || this.isSubmergedInWater());
    }

    private boolean canSprint() {
        return this.hasVehicle() || (float)this.getHungerManager().getFoodLevel() > 6.0F || this.getAbilities().allowFlying;
    }

    private boolean isBlind() {
        return this.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    private Vec2f applyMovementSpeedFactors(Vec2f input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        } else {
            Vec2f vec2f = input.multiply(0.98F);
            if (this.isUsingItem() && !this.hasVehicle()) {
                vec2f = vec2f.multiply(0.2F);
            }

            if (this.shouldSlowDown()) {
                float f = (float)this.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
                vec2f = vec2f.multiply(f);
            }

            return applyDirectionalMovementSpeedFactors(vec2f);
        }
    }

    public boolean shouldSlowDown() {
        return this.isInSneakingPose() || this.isCrawling();
    }

    public boolean isInSneakingPose() {
        return this.inSneakingPose;
    }

    private static Vec2f applyDirectionalMovementSpeedFactors(Vec2f vec) {
        float f = vec.length();
        if (f <= 0.0F) {
            return vec;
        } else {
            Vec2f vec2f = vec.multiply(1.0F / f);
            float g = getDirectionalMovementSpeedMultiplier(vec2f);
            float h = Math.min(f * g, 1.0F);
            return vec2f.multiply(h);
        }
    }

    private static float getDirectionalMovementSpeedMultiplier(Vec2f vec) {
        float f = Math.abs(vec.x);
        float g = Math.abs(vec.y);
        float h = g > f ? f / g : g / f;
        return MathHelper.sqrt(1.0F + MathHelper.square(h));
    }
}
