package de.legoshi.parkourcalc.fabric.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.SubtickPath;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorEntity extends Player {

    public final SimulatorInput input = new SimulatorInput();
    public Vec3 startPosition;
    public Vec3 startVelocity;
    public float startYaw;

    // No shadow prev* fields: SimulatorInput.playerInput holds the previous tick's
    // state until we call this.input.tick(), matching how MC's persistent Input
    // field works, so MC's `wasShiftKeyDown`/`hasForwardImpulse` capture pattern works as-is.
    private int sprintTriggerTime = 0;
    private boolean crouching;

    private final ArrayList<Vec3dCore> subtickBuf = new ArrayList<Vec3dCore>(8);
    private boolean capturing = false;

    private double lastCollisionAngleDegrees = Double.NaN;
    private boolean collisionAngleComputedThisTick = false;
    private Medium tickMedium;
    private double tickGroundFriction = Double.NaN;
    private int tickSoulsandCells;

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

    /** Axis order mirrors Direction.axisStepOrder (Y first, X/Z by |component|). */
    @Override
    public void move(MoverType type, Vec3 motion) {
        if (!capturing) {
            super.move(type, motion);
            return;
        }
        Vec3 before = this.position();
        super.move(type, motion);
        Vec3 after = this.position();

        double cx = after.x - before.x;
        double cy = after.y - before.y;
        double cz = after.z - before.z;
        SubtickPath.appendMove(subtickBuf, before.x, before.y, before.z, cx, cy, cz,
                Math.abs(motion.x) >= Math.abs(motion.z));
    }

    public SimulatorEntity(Level world, GameProfile profile, Vec3 startPosition, Vec3 startVelocity, float startYaw) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        resetPlayer();
    }

    public void resetPlayer() {
        resetPlayer(null);
    }

    public void resetPlayer(StartResumeState resume) {
        this.noPhysics = true;
        this.removeAllEffects();
        this.setHealth(this.getMaxHealth());
        this.setPos(startPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.setRot(startYaw, 0);

        this.input.setData(new InputRow());
        this.sprintTriggerTime = 0;
        this.tick();
        this.tick();

        this.setPos(startPosition);
        this.setDeltaMovement(startVelocity);
        this.tickMedium = null;
        this.tickGroundFriction = Double.NaN;
        this.tickSoulsandCells = 0;
        if (resume != null) {
            applyResume(resume);
        }
    }

    private void applyResume(StartResumeState resume) {
        this.setOnGround(resume.onGround);
        this.horizontalCollision = resume.wallContact;
        this.minorHorizontalCollision = resume.softWallContact;
        this.setSprinting(resume.sprinting);
        this.sprintTriggerTime = resume.sprintWindow;
        this.noJumpDelay = resume.jumpCooldown;
        this.stuckSpeedMultiplier = resume.stuckMultiplier != null
                ? new Vec3(resume.stuckMultiplier.x, resume.stuckMultiplier.y, resume.stuckMultiplier.z)
                : Vec3.ZERO;
        this.input.seedKeyPresses(new Input(
                resume.heldLastTick.contains(InputRow.Key.W),
                resume.heldLastTick.contains(InputRow.Key.S),
                resume.heldLastTick.contains(InputRow.Key.A),
                resume.heldLastTick.contains(InputRow.Key.D),
                resume.heldLastTick.contains(InputRow.Key.JUMP),
                resume.heldLastTick.contains(InputRow.Key.SNEAK),
                resume.heldLastTick.contains(InputRow.Key.SPRINT)));
    }

    @Override
    public void travel(Vec3 input) {
        boolean web = this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7;
        boolean fluid = this.shouldTravelInFluid(this.level().getFluidState(this.blockPosition()));
        boolean water = fluid && this.isInWater();
        boolean lava = fluid && !water && this.isInLava();
        boolean ladder = this.onClimbable();
        tickGroundFriction = this.onGround()
                ? (double) this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction()
                : Double.NaN;
        super.travel(input);
        tickSoulsandCells = this.getBlockSpeedFactor() != 1.0F ? 1 : 0;
        tickMedium = Medium.fromFlags(web, water, lava, ladder, tickSoulsandCells > 0);
    }

    public Medium capturedTickMedium() {
        return tickMedium;
    }

    public double capturedTickGroundFriction() {
        return tickGroundFriction;
    }

    public int capturedTickSoulsandCells() {
        return tickSoulsandCells;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void causeFoodExhaustion(float amount) {
    }

    /** Required so canSimulateMovement/isLocalInstanceAuthoritative return true
     *  on the client world; without it the entity's physics never advance. */
    @Override
    public boolean isLocalPlayer() {
        return true;
    }

    /** Lets the entity tick on ServerWorld: Entity.isLocalInstanceAuthoritative is final
     *  and returns !isControlledByPlayer() there, and PlayerEntity defaults that to true. */
    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    public @Nullable GameType gameMode() {
        return GameType.DEFAULT_MODE;
    }

    /** No-op so the simulator doesn't spawn particles in the real world. */
    @Override
    protected void spawnSprintParticle() {
    }

    /** No-op so dragging a TAS through water doesn't spam splash/bubble particles
     *  (and the splash sound) on every re-simulation. */
    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
    }

    /** No-op: vanilla calls discard() when Y < bottomY - 64, which sets removalReason and
     *  makes every subsequent tick() a no-op. Simulator paths legitimately fall past world
     *  bottom (TAS into the void) and must keep ticking; resetPlayer() snaps position back. */
    @Override
    protected void onBelowWorld() {
    }

    // LivingEntity gates removeAllEffects / onEffect* on !level.isClientSide(),
    // which would make every effect call a no-op in the client level. Reimplement
    // without the gate so attribute modifiers actually attach and detach.

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

    /** No-op so the simulator can't shove the real player or other world entities. */
    @Override
    protected void pushEntities() {
    }

    @Override
    public void tick() {
        Vec3 before = this.position();
        super.tick();
        if (!this.collisionAngleComputedThisTick) {
            Vec3 after = this.position();
            this.lastCollisionAngleDegrees = computeCollisionAngleDegrees(
                    after.x - before.x, after.z - before.z);
        }
    }

    /**
     * Mirrors LocalPlayer.aiStep sprint block (mojmaps 26.2).
     * Sprint-window length is hard-coded to vanilla's 7 (MC reads it from
     * client.options.sprintWindow(), which the simulator has no access to).
     */
    @Override
    public void aiStep() {
        this.lastCollisionAngleDegrees = Double.NaN;
        this.collisionAngleComputedThisTick = false;

        if (this.sprintTriggerTime > 0) {
            this.sprintTriggerTime--;
        }

        // Capture pre-input.tick() so wasShiftKeyDown/hasForwardImpulse hold the previous tick's state.
        boolean wasShiftKeyDown = this.input.keyPresses.shift();
        boolean hasForwardImpulse = this.input.hasForwardImpulse();

        // Mirrors LocalPlayer 1:1; the !canPlayerFitWithinBlocksAndEntitiesWhen(STANDING) term is vanilla's
        // forced crouch under a low ceiling, which keeps the slowdown after sneak is released.
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

    /** Read sneak directly from the per-tick input so any caller during this tick
     *  sees the current value without waiting on data-tracker sync. */
    @Override
    public boolean isShiftKeyDown() {
        return this.input.keyPresses.shift();
    }

    @Override
    public boolean isCrouching() {
        return this.crouching;
    }

    // Helpers copied 1-to-1 from LocalEntity (private there).

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

    /** Lives on LocalPlayer in MC, not Player, so we redeclare it. */
    public boolean isMovingSlowly() {
        return this.isCrouching() || this.isVisuallyCrawling();
    }

    @Override
    protected boolean isHorizontalCollisionMinor(Vec3 adjustedMovement) {
        double angleDeg = computeCollisionAngleDegrees(adjustedMovement.x, adjustedMovement.z);
        if (Double.isNaN(angleDeg)) {
            return false;
        }
        this.lastCollisionAngleDegrees = angleDeg;
        this.collisionAngleComputedThisTick = true;
        return Math.toRadians(angleDeg) < 0.13962634F;
    }

    private double computeCollisionAngleDegrees(double movementX, double movementZ) {
        float yRotInRadians = this.getYRot() * (float) (Math.PI / 180.0);
        double yRotSin = Mth.sin(yRotInRadians);
        double yRotCos = Mth.cos(yRotInRadians);
        double globalXA = this.xxa * yRotCos - this.zza * yRotSin;
        double globalZA = this.zza * yRotCos + this.xxa * yRotSin;
        double aLengthSquared = Mth.square(globalXA) + Mth.square(globalZA);
        double movementLengthSquared = Mth.square(movementX) + Mth.square(movementZ);
        if (aLengthSquared < 1.0E-5F || movementLengthSquared < 1.0E-5F) {
            return Double.NaN;
        }
        double dotProduct = globalXA * movementX + globalZA * movementZ;
        return Math.toDegrees(Math.acos(dotProduct / Math.sqrt(aLengthSquared * movementLengthSquared)));
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
        // Vanilla gates on isMovingSlowly() (pose from the previous tick's sneak), so the
        // slowdown lands one tick after the sneak input; isSneaking() would apply it a tick early.
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

    public Checkpoint saveCheckpoint() {
        Checkpoint c = new Checkpoint();
        c.pos = this.position();
        c.velocity = this.getDeltaMovement();
        c.yaw = this.getYRot();
        c.onGround = this.onGround();
        c.horizontalCollision = this.horizontalCollision;
        c.collidedSoftly = this.minorHorizontalCollision;
        c.sprinting = this.isSprinting();
        c.ticksLeftToDoubleTapSprint = this.sprintTriggerTime;
        c.playerInput = this.input.keyPresses;
        c.jumpingCooldown = this.noJumpDelay;
        c.movementMultiplier = this.stuckSpeedMultiplier;
        c.pose = this.getPose();
        c.crouching = this.crouching;
        c.fallDistance = this.fallDistance;
        return c;
    }

    public void restoreCheckpoint(Checkpoint c) {
        // Start from the clean spawn baseline (same as a full run's resetToStart) so no uncaptured
        // entity state from the previous run leaks in; the overlay below restores the history it carries.
        resetPlayer();
        this.crouching = c.crouching;
        this.setPose(c.pose);
        this.refreshDimensions();
        this.setPos(c.pos);
        this.setDeltaMovement(c.velocity);
        this.setYRot(c.yaw);
        this.setOnGround(c.onGround);
        this.horizontalCollision = c.horizontalCollision;
        this.minorHorizontalCollision = c.collidedSoftly;
        this.setSprinting(c.sprinting);
        this.sprintTriggerTime = c.ticksLeftToDoubleTapSprint;
        this.input.seedKeyPresses(c.playerInput);
        this.noJumpDelay = c.jumpingCooldown;
        this.stuckSpeedMultiplier = c.movementMultiplier;
        this.fallDistance = c.fallDistance;
    }

    public static void applyCheckpoint(net.minecraft.client.player.LocalPlayer p, de.legoshi.parkourcalc.core.sim.Checkpoint state) {
        Checkpoint c = de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint.clientPart(state);
        if (c == null) return;
        p.setOnGround(c.onGround);
        p.horizontalCollision = c.horizontalCollision;
        p.minorHorizontalCollision = c.collidedSoftly;
        p.setSprinting(c.sprinting);
        p.noJumpDelay = c.jumpingCooldown;
        p.stuckSpeedMultiplier = c.movementMultiplier;
        p.fallDistance = c.fallDistance;
    }

    public static final class Checkpoint implements de.legoshi.parkourcalc.core.sim.Checkpoint {
        Vec3 pos;
        Vec3 velocity;
        float yaw;
        boolean onGround;
        boolean horizontalCollision;
        boolean collidedSoftly;
        boolean sprinting;
        int ticksLeftToDoubleTapSprint;
        Input playerInput;
        int jumpingCooldown;
        Vec3 movementMultiplier;
        Pose pose;
        boolean crouching;
        double fallDistance;
    }
}
