package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.forge8.Forge8ParkourCalculator;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.SubtickPath;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 1.8.9 API surface uses net.minecraft.util.Vec3 and has no moveVertical field. */
@SuppressWarnings("DuplicatedCode")
public class SimulatorEntity extends EntityPlayer {

    public Vec3 startPosition;
    public Vec3 startVelocity;
    public float startYaw;

    private InputRow currentInput = new InputRow();
    private PlayerSprintMachine.State sprintState = PlayerSprintMachine.State.initial();
    private Medium tickMedium;
    private double tickGroundFriction = Double.NaN;
    private int tickSoulsandCells;

    private final ArrayList<Vec3dCore> subtickBuf = new ArrayList<>(8);
    private boolean capturing = false;

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

    @Override
    public void moveEntity(double x, double y, double z) {
        if (!capturing) {
            super.moveEntity(x, y, z);
            return;
        }
        double bx = this.posX, by = this.posY, bz = this.posZ;
        super.moveEntity(x, y, z);
        double cx = this.posX - bx;
        double cy = this.posY - by;
        double cz = this.posZ - bz;

        SubtickPath.appendMove(subtickBuf, bx, by, bz, cx, cy, cz, true);
    }

    public SimulatorEntity(World world, GameProfile profile, Vec3 startPosition, Vec3 startVelocity, float startYaw) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        resetPlayer();
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void resetPlayer() {
        resetPlayer(null);
    }

    public void resetPlayer(StartResumeState resume) {
        this.noClip = true;
        this.setHealth(this.getMaxHealth());
        this.motionX = 0.0;
        this.motionY = 0.0;
        this.motionZ = 0.0;
        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        this.rotationYaw = startYaw;
        this.rotationPitch = 0.0F;

        this.currentInput = new InputRow();
        this.sprintState = PlayerSprintMachine.State.initial();
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        this.motionX = startVelocity.xCoord;
        this.motionY = startVelocity.yCoord;
        this.motionZ = startVelocity.zCoord;
        this.tickMedium = null;
        this.tickGroundFriction = Double.NaN;
        this.tickSoulsandCells = 0;
        if (resume != null) {
            applyResume(resume);
        }
    }

    private void applyResume(StartResumeState resume) {
        this.onGround = resume.onGround;
        this.isCollidedHorizontally = resume.wallContact;
        this.setSprinting(resume.sprinting);
        this.jumpTicks = resume.jumpCooldown;
        this.isInWeb = resume.stuckMultiplier != null;
        this.jumpMovementFactor = !Float.isNaN(resume.airSprintFactor)
                ? resume.airSprintFactor
                : (resume.sprinting ? 0.026F : 0.02F);
        boolean prevSneak = resume.heldLastTick.contains(InputRow.Key.SNEAK);
        float prevForward = (resume.heldLastTick.contains(InputRow.Key.W) ? 1.0F : 0.0F)
                - (resume.heldLastTick.contains(InputRow.Key.S) ? 1.0F : 0.0F);
        if (prevSneak) {
            prevForward *= 0.3F;
        }
        this.sprintState = new PlayerSprintMachine.State(prevSneak, prevForward,
                resume.sprintWindow, resume.sprintTicksLeft, resume.sprinting);
    }

    @Override
    public void addExhaustion(float amount) {
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!Forge8ParkourCalculator.isDamageAllowed()) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public void setHealth(float health) {
        if (Forge8ParkourCalculator.isDamageAllowed()) {
            super.setHealth(this.getMaxHealth());
        } else {
            super.setHealth(health);
        }
    }

    @Override
    public void onLivingUpdate() {
        applyMovementInput();
        super.onLivingUpdate();
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        boolean web = this.isInWeb;
        boolean water = this.isInWater();
        boolean lava = this.isInLava();
        boolean ladder = this.isOnLadder();
        tickGroundFriction = this.onGround
                ? (double) this.worldObj.getBlockState(new BlockPos(
                        MathHelper.floor_double(this.posX),
                        MathHelper.floor_double(this.getEntityBoundingBox().minY) - 1,
                        MathHelper.floor_double(this.posZ))).getBlock().slipperiness
                : Double.NaN;
        super.moveEntityWithHeading(strafe, forward);
        tickSoulsandCells = countSoulsandCells();
        tickMedium = Medium.fromFlags(web, water, lava, ladder, tickSoulsandCells > 0);
    }

    private int countSoulsandCells() {
        AxisAlignedBB bb = this.getEntityBoundingBox();
        BlockPos min = new BlockPos(bb.minX + 0.001, bb.minY + 0.001, bb.minZ + 0.001);
        BlockPos max = new BlockPos(bb.maxX - 0.001, bb.maxY - 0.001, bb.maxZ - 0.001);
        if (!this.worldObj.isAreaLoaded(min, max)) return 0;
        int count = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (this.worldObj.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.soul_sand) {
                        count++;
                    }
                }
            }
        }
        return count;
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

    private void applyMovementInput() {
        PlayerSprintMachine.Inputs in = new PlayerSprintMachine.Inputs(
                currentInput.isKeyActive(InputRow.Key.W),
                currentInput.isKeyActive(InputRow.Key.S),
                currentInput.isKeyActive(InputRow.Key.A),
                currentInput.isKeyActive(InputRow.Key.D),
                currentInput.isKeyActive(InputRow.Key.JUMP),
                currentInput.isKeyActive(InputRow.Key.SNEAK),
                currentInput.isKeyActive(InputRow.Key.SPRINT),
                this.onGround,
                this.isUsingItem(),
                this.isRiding(),
                this.isCollidedHorizontally,
                this.isPotionActive(Potion.blindness),
                this.capabilities.allowFlying,
                (float) this.getFoodStats().getFoodLevel()
        );

        PlayerSprintMachine.State seed = sprintState.withIsSprinting(this.isSprinting());
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, seed);
        sprintState = out.next;

        this.setSprinting(out.next.isSprinting);
        this.setSneaking(in.sneak);
        this.moveForward = out.moveForward;
        this.moveStrafing = out.moveStrafe;
        this.isJumping = out.isJumping;
    }

    /** Prevent the simulator from pushing the real player or any other world entity. */
    @Override
    protected void collideWithNearbyEntities() {
    }

    /** No-op so the simulator doesn't spawn sprint particles in the real world. */
    @Override
    public void spawnRunningParticles() {
    }

    /** No-op so dragging a TAS through water doesn't spam splash/bubble particles
     *  on every re-simulation. */
    @Override
    protected void resetHeight() {
    }

    @Override
    public void playSound(String name, float volume, float pitch) {
    }

    /** Reimplements EntityLivingBase/Entity updateFallState minus the BLOCK_DUST
     *  landing particles, so re-simulating a fall on every drag doesn't spam them.
     *  Bounce (onFallenUpon) and fallDistance bookkeeping are preserved. */
    @Override
    protected void updateFallState(double y, boolean onGroundIn, net.minecraft.block.Block blockIn, net.minecraft.util.BlockPos pos) {
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                if (blockIn != null) {
                    blockIn.onFallenUpon(this.worldObj, pos, this, this.fallDistance);
                } else {
                    this.fall(this.fallDistance, 1.0F);
                }
                this.fallDistance = 0.0F;
            }
        } else if (y < 0.0) {
            this.fallDistance = (float) (this.fallDistance - y);
        }
    }

    /** No-op: vanilla calls setDead() when Y drops below 0, which freezes all subsequent
     *  ticks. Simulator paths legitimately fall into the void; resetPlayer snaps Y back. */
    @Override
    protected void kill() {
    }

    // EntityLivingBase gates clearActivePotions / onNew/Changed/FinishedPotionEffect
    // on !worldObj.isRemote, which would make every effect call a no-op in the
    // client world. Reimplement without the gate so attribute modifiers actually
    // attach and detach.

    @Override
    public void clearActivePotions() {
        Collection<PotionEffect> effects = this.getActivePotionEffects();
        if (effects.isEmpty()) return;
        List<PotionEffect> all = new ArrayList<PotionEffect>(effects);
        for (PotionEffect e : all) {
            this.removePotionEffect(e.getPotionID());
        }
    }

    @Override
    protected void onNewPotionEffect(PotionEffect effect) {
        Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    protected void onChangedPotionEffect(PotionEffect effect, boolean reapply) {
        if (reapply) {
            Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
            Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
        }
    }

    @Override
    protected void onFinishedPotionEffect(PotionEffect effect) {
        Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    /** Required: EntityLivingBase.moveEntityWithHeading is gated by isServerWorld. */
    @Override
    public boolean isServerWorld() {
        return true;
    }

    public Checkpoint saveCheckpoint() {
        Checkpoint c = new Checkpoint();
        c.posX = this.posX;
        c.posY = this.posY;
        c.posZ = this.posZ;
        c.motionX = this.motionX;
        c.motionY = this.motionY;
        c.motionZ = this.motionZ;
        c.rotationYaw = this.rotationYaw;
        c.onGround = this.onGround;
        c.isCollidedHorizontally = this.isCollidedHorizontally;
        c.sprinting = this.isSprinting();
        c.sneaking = this.isSneaking();
        c.sprintState = this.sprintState;
        c.jumpMovementFactor = this.jumpMovementFactor;
        c.landMovementFactor = this.getAIMoveSpeed();
        c.jumpTicks = this.jumpTicks;
        c.isInWeb = this.isInWeb;
        c.fallDistance = this.fallDistance;
        return c;
    }

    public void restoreCheckpoint(Checkpoint c) {
        // Start from the clean spawn baseline (same as a full run's resetToStart) so no uncaptured
        // entity state from the previous run leaks in; the overlay below restores the history it carries.
        resetPlayer();
        this.motionX = c.motionX;
        this.motionY = c.motionY;
        this.motionZ = c.motionZ;
        this.rotationYaw = c.rotationYaw;
        this.onGround = c.onGround;
        this.isCollidedHorizontally = c.isCollidedHorizontally;
        this.setSprinting(c.sprinting);
        this.setSneaking(c.sneaking);
        this.sprintState = c.sprintState;
        this.jumpMovementFactor = c.jumpMovementFactor;
        this.setAIMoveSpeed(c.landMovementFactor);
        this.jumpTicks = c.jumpTicks;
        this.isInWeb = c.isInWeb;
        this.fallDistance = c.fallDistance;
        this.setPosition(c.posX, c.posY, c.posZ);
    }

    public static void applyCheckpoint(EntityLivingBase p, de.legoshi.parkourcalc.core.sim.Checkpoint state) {
        if (!(state instanceof Checkpoint)) return;
        Checkpoint c = (Checkpoint) state;
        p.onGround = c.onGround;
        p.isCollidedHorizontally = c.isCollidedHorizontally;
        p.setSprinting(c.sprinting);
        p.setSneaking(c.sneaking);
        p.setAIMoveSpeed(c.landMovementFactor);
        p.jumpMovementFactor = c.jumpMovementFactor;
        p.jumpTicks = c.jumpTicks;
        p.fallDistance = c.fallDistance;
        if (c.isInWeb) {
            p.setInWeb();
        }
    }

    public static final class Checkpoint implements de.legoshi.parkourcalc.core.sim.Checkpoint {
        double posX, posY, posZ;
        double motionX, motionY, motionZ;
        float rotationYaw;
        boolean onGround;
        boolean isCollidedHorizontally;
        boolean sprinting, sneaking;
        PlayerSprintMachine.State sprintState;
        float jumpMovementFactor;
        float landMovementFactor;
        int jumpTicks;
        boolean isInWeb;
        float fallDistance;
    }
}
