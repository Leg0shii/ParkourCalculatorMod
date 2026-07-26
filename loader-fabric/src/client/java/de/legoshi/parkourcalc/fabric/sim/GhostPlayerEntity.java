package de.legoshi.parkourcalc.fabric.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public class GhostPlayerEntity extends AbstractClientPlayer {

    public final SimulatorInput input = new SimulatorInput();

    private int sprintTriggerTime = 0;
    private boolean crouching;

    public GhostPlayerEntity(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    public void setInput(InputRow row) {
        this.input.setData(row);
    }

    public void copyModelCustomisationFrom(Player source) {
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, source.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
    }

    public void applyCarry(de.legoshi.parkourcalc.core.sim.Checkpoint state) {
        if (!(state instanceof SimulatorEntity.Checkpoint c)) return;
        this.setOnGround(c.onGround);
        this.horizontalCollision = c.horizontalCollision;
        this.minorHorizontalCollision = c.collidedSoftly;
        this.setSprinting(c.sprinting);
        this.sprintTriggerTime = c.ticksLeftToDoubleTapSprint;
        this.input.keyPresses = c.playerInput;
        this.noJumpDelay = c.jumpingCooldown;
        this.stuckSpeedMultiplier = c.movementMultiplier;
    }

    @Override
    public void tick() {
        if (FabricParkourCalculator.shouldForceGroundOnGhostTick0(this)) {
            this.setOnGround(FabricParkourCalculator.firstTickOnGround());
            this.fallDistance = 0.0;
        }
        super.tick();
    }

    @Override
    public boolean isLocalPlayer() {
        return true;
    }

    @Override
    public @Nullable GameType gameMode() {
        return GameType.DEFAULT_MODE;
    }

    @Override
    public void causeFoodExhaustion(float amount) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    protected void onBelowWorld() {
    }

    @Override
    public boolean removeAllEffects() {
        Map<net.minecraft.core.Holder<MobEffect>, MobEffectInstance> active = this.getActiveEffectsMap();
        if (active.isEmpty()) return false;
        Map<net.minecraft.core.Holder<MobEffect>, MobEffectInstance> copy = new HashMap<>(active);
        active.clear();
        this.onEffectsRemoved(copy.values());
        return true;
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        effect.getEffect().value().addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        if (reapplyEffect) {
            MobEffect type = effect.getEffect().value();
            type.removeAttributeModifiers(this.getAttributes());
            type.addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
        }
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        for (MobEffectInstance e : effects) {
            e.getEffect().value().removeAttributeModifiers(this.getAttributes());
        }
    }

    @Override
    public void aiStep() {
        if (this.sprintTriggerTime > 0) {
            this.sprintTriggerTime--;
        }

        boolean wasShiftKeyDown = this.input.keyPresses.shift();
        boolean hasForwardImpulse = this.input.hasForwardImpulse();

        this.crouching = !this.getAbilities().flying
                && !this.isSwimming()
                && !this.isPassenger()
                && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
                && (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));

        this.input.tick();

        if (wasShiftKeyDown || this.isUsingItem() && !this.isPassenger() || this.input.keyPresses.backward()) {
            this.sprintTriggerTime = 0;
        }

        if (this.canStartSprinting()) {
            if (!hasForwardImpulse) {
                if (this.sprintTriggerTime > 0) {
                    this.setSprinting(true);
                } else {
                    this.sprintTriggerTime = 7;
                }
            }

            if (this.input.keyPresses.sprint()) {
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

        super.aiStep();
    }

    @Override
    public boolean isShiftKeyDown() {
        return this.input.keyPresses.shift();
    }

    @Override
    public boolean isCrouching() {
        return this.crouching;
    }

    private boolean canStartSprinting() {
        return !this.isSprinting()
                && this.input.hasForwardImpulse()
                && this.isSprintingPossible(this.getAbilities().flying)
                && !this.isUsingItem()
                && (!this.isFallFlying() || this.isUnderWater())
                && (!this.isMovingSlowly() || this.isUnderWater());
    }

    private boolean shouldStopRunSprinting() {
        return !this.isSprintingPossible(this.getAbilities().flying)
                || !this.input.hasForwardImpulse()
                || this.horizontalCollision && !this.minorHorizontalCollision;
    }

    private boolean shouldStopSwimSprinting() {
        return !this.isSprintingPossible(true)
                || !this.isInWater()
                || !this.input.hasForwardImpulse() && !this.onGround() && !this.input.keyPresses.shift();
    }

    private boolean isSprintingPossible(boolean allowedInShallowWater) {
        return !this.isMobilityRestricted()
                && this.hasEnoughFoodToSprint()
                && (!this.isPassenger() || this.vehicleCanSprint(this.getVehicle()))
                && (allowedInShallowWater || !this.isInShallowWater());
    }

    private boolean vehicleCanSprint(Entity vehicle) {
        return vehicle.canSprint() && vehicle.isLocalInstanceAuthoritative();
    }

    private boolean hasEnoughFoodToSprint() {
        return this.isPassenger() || this.getFoodData().getFoodLevel() > 6.0F || this.getAbilities().mayfly;
    }

    public boolean isMovingSlowly() {
        return this.isCrouching() || this.isVisuallyCrawling();
    }

    @Override
    protected boolean isHorizontalCollisionMinor(Vec3 adjustedMovement) {
        float yRotInRadians = this.getYRot() * (float) (Math.PI / 180.0);
        double yRotSin = Mth.sin(yRotInRadians);
        double yRotCos = Mth.cos(yRotInRadians);
        double globalXA = this.xxa * yRotCos - this.zza * yRotSin;
        double globalZA = this.zza * yRotCos + this.xxa * yRotSin;
        double aLengthSquared = Mth.square(globalXA) + Mth.square(globalZA);
        double movementLengthSquared = Mth.square(adjustedMovement.x) + Mth.square(adjustedMovement.z);
        if (aLengthSquared < 1.0E-5F || movementLengthSquared < 1.0E-5F) {
            return false;
        }
        double dotProduct = globalXA * adjustedMovement.x + globalZA * adjustedMovement.z;
        return Math.acos(dotProduct / Math.sqrt(aLengthSquared * movementLengthSquared)) < 0.13962634F;
    }

    @Override
    public void applyInput() {
        Vec2 movement = modifyInput(this.input.getMoveVector());
        this.xxa = movement.x;
        this.zza = movement.y;
        this.jumping = this.input.keyPresses.jump();
    }

    private Vec2 modifyInput(Vec2 input) {
        if (input.lengthSquared() == 0.0F) {
            return input;
        }
        Vec2 newInput = input.scale(0.98F);
        if (this.isMovingSlowly()) {
            float sneakingMovementFactor = (float) this.getAttributeValue(Attributes.SNEAKING_SPEED);
            newInput = newInput.scale(sneakingMovementFactor);
        }
        return modifyInputSpeedForSquareMovement(newInput);
    }

    private static Vec2 modifyInputSpeedForSquareMovement(Vec2 input) {
        float length = input.length();
        if (length <= 0.0F) {
            return input;
        }
        Vec2 direction = input.scale(1.0F / length);
        float distanceToUnitSquare = distanceToUnitSquare(direction);
        float modifiedLength = Math.min(length * distanceToUnitSquare, 1.0F);
        return direction.scale(modifiedLength);
    }

    private static float distanceToUnitSquare(Vec2 direction) {
        float directionX = Math.abs(direction.x);
        float directionY = Math.abs(direction.y);
        float tan = directionY > directionX ? directionX / directionY : directionY / directionX;
        return Mth.sqrt(1.0F + Mth.square(tan));
    }
}
