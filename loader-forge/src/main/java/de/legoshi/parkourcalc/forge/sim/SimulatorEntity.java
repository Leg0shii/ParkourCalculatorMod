package de.legoshi.parkourcalc.forge.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.SubtickPath;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
//? if >=1.12
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
//? if >=1.12
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
//? if >=1.12
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
//? if <1.12 {
/*import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
*///?}
import net.minecraft.util.DamageSource;
//? if >=1.12 {
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
//?} else {
/*import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
*///?}
import net.minecraft.world.World;

import java.util.ArrayList;
//? if <1.12
//import java.util.Collection;
import java.util.List;
//? if >=1.12
import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public class SimulatorEntity extends EntityPlayer {

    //? if >=1.12 {
    public Vec3d startPosition;
    public Vec3d startVelocity;
    //?} else {
    /*public Vec3 startPosition;
    public Vec3 startVelocity;
    *///?}
    public float startYaw;

    private InputRow currentInput = new InputRow();
    private PlayerSprintMachine.State sprintState = PlayerSprintMachine.State.initial();
    private Medium tickMedium;
    private double tickGroundFriction = Double.NaN;
    private int tickSoulsandCells;
    private boolean tickGliding;
    private boolean elytraEquipped;
    private boolean prevJump;
    //? if >=1.12
    private final ElytraGlide.Lag glide = new ElytraGlide.Lag();

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

    @Override
    //? if >=1.12 {
    public void move(MoverType type, double x, double y, double z) {
        if (!capturing) {
            super.move(type, x, y, z);
    //?} else {
    /*public void moveEntity(double x, double y, double z) {
        if (!capturing) {
            super.moveEntity(x, y, z);
    *///?}
            return;
        }
        double bx = this.posX, by = this.posY, bz = this.posZ;
        //? if >=1.12 {
        super.move(type, x, y, z);
        //?} else {
        /*super.moveEntity(x, y, z);
        *///?}
        double cx = this.posX - bx;
        double cy = this.posY - by;
        double cz = this.posZ - bz;

        SubtickPath.appendMove(subtickBuf, bx, by, bz, cx, cy, cz, true);
    }

    //? if >=1.12 {
    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition, Vec3d startVelocity, float startYaw) {
    //?} else {
    /*public SimulatorEntity(World world, GameProfile profile, Vec3 startPosition, Vec3 startVelocity, float startYaw) {
    *///?}
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        this.setSilent(true);
        resetPlayer();
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void setElytraEquipped(boolean equipped) {
        this.elytraEquipped = equipped;
        //? if >=1.12
        ElytraGlide.equip(this, equipped);
    }

    public boolean isElytraEquipped() {
        return elytraEquipped;
    }

    public boolean capturedTickGliding() {
        return tickGliding;
    }

    //? if >=1.12 {
    public ElytraGlide.Lag glide() {
        return glide;
    }

    static void setEntityFlag(net.minecraft.entity.Entity entity, int flag, boolean set) {
        byte bits = entity.getDataManager().get(FLAGS);
        entity.getDataManager().set(FLAGS, (byte) (set ? bits | 1 << flag : bits & ~(1 << flag)));
    }
    //?}

    public void resetPlayer() {
        resetPlayer(null);
    }

    public void resetPlayer(StartResumeState resume) {
        this.noClip = true;
        this.extinguish();
        this.setHealth(this.getMaxHealth());
        this.setSneaking(false);
        this.setSize(0.6F, 1.8F);
        this.motionX = 0.0;
        this.motionY = 0.0;
        this.motionZ = 0.0;
        //? if >=1.12 {
        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
        //?} else {
        /*this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        *///?}
        this.rotationYaw = startYaw;
        this.rotationPitch = 0.0F;
        this.prevJump = false;
        //? if >=1.12 {
        this.glide.reset(this);
        ElytraGlide.equip(this, elytraEquipped);
        //?}

        this.currentInput = new InputRow();
        this.sprintState = PlayerSprintMachine.State.initial();
        this.onUpdate();
        this.onUpdate();

        //? if >=1.12 {
        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
        this.motionX = startVelocity.x;
        this.motionY = startVelocity.y;
        this.motionZ = startVelocity.z;
        //?} else {
        /*this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        this.motionX = startVelocity.xCoord;
        this.motionY = startVelocity.yCoord;
        this.motionZ = startVelocity.zCoord;
        *///?}
        this.tickMedium = null;
        this.tickGroundFriction = Double.NaN;
        this.tickSoulsandCells = 0;
        this.tickGliding = false;
        if (resume != null) {
            applyResume(resume);
        }
    }

    private void applyResume(StartResumeState resume) {
        this.onGround = resume.onGround;
        //? if >=1.12 {
        if (resume.gliding) {
            ElytraGlide.setFallFlying(this, true);
            this.setSize(0.6F, 0.6F);
        }
        //?}
        //? if >=1.12 {
        this.collidedHorizontally = resume.wallContact;
        //?} else {
        /*this.isCollidedHorizontally = resume.wallContact;
        *///?}
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
        return false;
    }

    @Override
    public void onLivingUpdate() {
        boolean wasJumping = prevJump;
        applyMovementInput();
        boolean jump = currentInput.isKeyActive(InputRow.Key.JUMP);
        prevJump = jump;
        //? if >=1.12 {
        if (ElytraGlide.requestsDeploy(this, jump, wasJumping)) {
            glide.request(this);
        }
        //?}
        super.onLivingUpdate();
    }

    @Override
    public void onUpdate() {
        //? if >=1.12
        glide.beginTick(this);
        super.onUpdate();
        //? if >=1.12 {
        if (glide.endTick(this)) {
            this.updateSize();
        }
        //?}
    }

    @Override
    //? if >=1.12 {
    public void travel(float strafe, float vertical, float forward) {
    //?} else {
    /*public void moveEntityWithHeading(float strafe, float forward) {
    *///?}
        boolean web = this.isInWeb;
        boolean water = this.isInWater();
        boolean lava = this.isInLava();
        boolean ladder = this.isOnLadder();
        //? if >=1.12 {
        if (this.onGround) {
            BlockPos below = new BlockPos(this.posX, this.getEntityBoundingBox().minY - 1.0D, this.posZ);
            IBlockState underState = this.world.getBlockState(below);
            tickGroundFriction = (double) underState.getBlock().getSlipperiness(underState, this.world, below, this);
        } else {
            tickGroundFriction = Double.NaN;
        }
        tickGliding = this.isElytraFlying() && !water && !lava;
        super.travel(strafe, vertical, forward);
        //?} else {
        /*tickGroundFriction = this.onGround
                ? (double) this.worldObj.getBlockState(new BlockPos(
                        MathHelper.floor_double(this.posX),
                        MathHelper.floor_double(this.getEntityBoundingBox().minY) - 1,
                        MathHelper.floor_double(this.posZ))).getBlock().slipperiness
                : Double.NaN;
        super.moveEntityWithHeading(strafe, forward);
        *///?}
        tickSoulsandCells = countSoulsandCells();
        tickMedium = Medium.fromFlags(web, water, lava, ladder, tickSoulsandCells > 0);
    }

    private int countSoulsandCells() {
        AxisAlignedBB bb = this.getEntityBoundingBox();
        BlockPos min = new BlockPos(bb.minX + 0.001, bb.minY + 0.001, bb.minZ + 0.001);
        BlockPos max = new BlockPos(bb.maxX - 0.001, bb.maxY - 0.001, bb.maxZ - 0.001);
        //? if >=1.12 {
        if (!this.world.isAreaLoaded(min, max)) return 0;
        //?} else {
        /*if (!this.worldObj.isAreaLoaded(min, max)) return 0;
        *///?}
        int count = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    //? if >=1.12 {
                    if (this.world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.SOUL_SAND) {
                    //?} else {
                    /*if (this.worldObj.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.soul_sand) {
                    *///?}
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
                //? if >=1.12 {
                this.isHandActive(),
                this.isRiding(),
                this.collidedHorizontally,
                this.isPotionActive(MobEffects.BLINDNESS),
                //?} else {
                /*this.isUsingItem(),
                this.isRiding(),
                this.isCollidedHorizontally,
                this.isPotionActive(Potion.blindness),
                *///?}
                this.capabilities.allowFlying,
                (float) this.getFoodStats().getFoodLevel()
        );

        PlayerSprintMachine.State seed = sprintState.withIsSprinting(this.isSprinting());
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, seed);
        sprintState = out.next;

        this.setSprinting(out.next.isSprinting);
        this.setSneaking(in.sneak);
        this.moveForward = out.moveForward;
        //? if >=1.12 {
        this.moveVertical = 0.0F;
        //?}
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
    //? if >=1.12 {
    protected void doWaterSplashEffect() {
    //?} else {
    /*protected void resetHeight() {
    *///?}
    }

    @Override
    //? if >=1.12 {
    public void playSound(net.minecraft.util.SoundEvent soundIn, float volume, float pitch) {
    //?} else {
    /*public void playSound(String name, float volume, float pitch) {
    *///?}
    }

    /** Reimplements EntityLivingBase/Entity updateFallState minus the BLOCK_DUST
     *  landing particles, so re-simulating a fall on every drag doesn't spam them.
     *  Bounce (onFallenUpon) and fallDistance bookkeeping are preserved. */
    @Override
    //? if >=1.12 {
    protected void updateFallState(double y, boolean onGroundIn, net.minecraft.block.state.IBlockState state, net.minecraft.util.math.BlockPos pos) {
    //?} else {
    /*protected void updateFallState(double y, boolean onGroundIn, net.minecraft.block.Block blockIn, net.minecraft.util.BlockPos pos) {
    *///?}
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                //? if >=1.12 {
                state.getBlock().onFallenUpon(this.world, pos, this, this.fallDistance);
                //?} else {
                /*if (blockIn != null) {
                    blockIn.onFallenUpon(this.worldObj, pos, this, this.fallDistance);
                } else {
                    this.fall(this.fallDistance, 1.0F);
                }
                *///?}
            }
            this.fallDistance = 0.0F;
        } else if (y < 0.0) {
            this.fallDistance = (float) ((double) this.fallDistance - y);
        }
    }

    /** No-op: vanilla calls setDead() when Y drops below the world, which freezes all subsequent
     *  ticks. Simulator paths legitimately fall into the void; resetPlayer snaps Y back. */
    @Override
    //? if >=1.12 {
    protected void outOfWorld() {
    //?} else {
    /*protected void kill() {
    *///?}
    }

    // EntityLivingBase gates clearActivePotions / onNew/Changed/FinishedPotionEffect
    // on the world not being remote, which would make every effect call a no-op in
    // the client world. Reimplement without the gate so attribute modifiers actually
    // attach and detach.

    @Override
    public void clearActivePotions() {
        //? if >=1.12 {
        Map<Potion, PotionEffect> map = this.getActivePotionMap();
        if (map.isEmpty()) return;
        List<PotionEffect> all = new ArrayList<PotionEffect>(map.values());
        map.clear();
        for (PotionEffect e : all) {
            e.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), e.getAmplifier());
        //?} else {
        /*Collection<PotionEffect> effects = this.getActivePotionEffects();
        if (effects.isEmpty()) return;
        List<PotionEffect> all = new ArrayList<PotionEffect>(effects);
        for (PotionEffect e : all) {
            this.removePotionEffect(e.getPotionID());
        *///?}
        }
    }

    @Override
    protected void onNewPotionEffect(PotionEffect effect) {
        //? if >=1.12 {
        effect.getPotion().applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
        //?} else {
        /*Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
        *///?}
    }

    @Override
    protected void onChangedPotionEffect(PotionEffect effect, boolean reapply) {
        if (reapply) {
            //? if >=1.12 {
            effect.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
            effect.getPotion().applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
            //?} else {
            /*Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
            Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
            *///?}
        }
    }

    @Override
    protected void onFinishedPotionEffect(PotionEffect effect) {
        //? if >=1.12 {
        effect.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
        //?} else {
        /*Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
        *///?}
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    //? if >=1.12 {
    @Override
    public boolean isCreative() {
        return false;
    }
    //?}

    /** Required: EntityLivingBase travel is gated by isServerWorld. */
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
        //? if >=1.12 {
        c.collidedHorizontally = this.collidedHorizontally;
        //?} else {
        /*c.collidedHorizontally = this.isCollidedHorizontally;
        *///?}
        c.sprinting = this.isSprinting();
        c.sneaking = this.isSneaking();
        c.sprintState = this.sprintState;
        c.jumpMovementFactor = this.jumpMovementFactor;
        c.landMovementFactor = this.getAIMoveSpeed();
        c.jumpTicks = this.jumpTicks;
        c.isInWeb = this.isInWeb;
        c.width = this.width;
        c.height = this.height;
        c.fallDistance = this.fallDistance;
        c.prevJump = this.prevJump;
        //? if >=1.12 {
        c.fallFlying = this.isElytraFlying();
        c.glidePendingStop = this.glide.pendingStop();
        c.glidePendingStart = this.glide.pendingStart();
        //?}
        return c;
    }

    public void restoreCheckpoint(Checkpoint c) {
        // Start from the clean spawn baseline (same as a full run's resetToStart) so no uncaptured
        // entity state from the previous run leaks in; the overlay below restores the history it carries.
        resetPlayer();
        this.prevJump = c.prevJump;
        //? if >=1.12
        this.glide.restore(this, c.fallFlying, c.glidePendingStop, c.glidePendingStart);
        this.motionX = c.motionX;
        this.motionY = c.motionY;
        this.motionZ = c.motionZ;
        this.rotationYaw = c.rotationYaw;
        this.onGround = c.onGround;
        //? if >=1.12 {
        this.collidedHorizontally = c.collidedHorizontally;
        //?} else {
        /*this.isCollidedHorizontally = c.collidedHorizontally;
        *///?}
        this.setSprinting(c.sprinting);
        this.setSneaking(c.sneaking);
        this.sprintState = c.sprintState;
        this.jumpMovementFactor = c.jumpMovementFactor;
        this.setAIMoveSpeed(c.landMovementFactor);
        this.jumpTicks = c.jumpTicks;
        this.isInWeb = c.isInWeb;
        this.width = c.width;
        this.height = c.height;
        this.fallDistance = c.fallDistance;
        this.setPosition(c.posX, c.posY, c.posZ);
    }

    public void teleportRest(double x, double y, double z, double vx, double vy, double vz) {
        this.setPosition(x, y, z);
        this.motionX = vx;
        this.motionY = vy;
        this.motionZ = vz;
        this.onGround = true;
        //? if >=1.12 {
        this.collidedHorizontally = false;
        //?} else {
        /*this.isCollidedHorizontally = false;
        *///?}
        this.setSprinting(false);
        this.setSneaking(false);
        this.sprintState = PlayerSprintMachine.State.initial();
        this.jumpTicks = 0;
        this.isInWeb = false;
        this.fallDistance = 0;
        this.prevJump = false;
        //? if >=1.12
        this.glide.reset(this);
    }

    public static void applyCheckpoint(EntityLivingBase p, de.legoshi.parkourcalc.core.sim.Checkpoint state) {
        Checkpoint c = de.legoshi.parkourcalc.forge.sim.paired.PairedCheckpoint.clientPart(state);
        if (c == null) return;
        p.onGround = c.onGround;
        //? if >=1.12 {
        p.collidedHorizontally = c.collidedHorizontally;
        //?} else {
        /*p.isCollidedHorizontally = c.collidedHorizontally;
        *///?}
        p.setSprinting(c.sprinting);
        p.setSneaking(c.sneaking);
        p.setAIMoveSpeed(c.landMovementFactor);
        p.jumpMovementFactor = c.jumpMovementFactor;
        p.jumpTicks = c.jumpTicks;
        p.fallDistance = c.fallDistance;
        if (c.isInWeb) {
            p.setInWeb();
        }
        //? if >=1.12
        ElytraGlide.setFallFlying(p, c.fallFlying);
    }

    public static final class Checkpoint implements de.legoshi.parkourcalc.core.sim.Checkpoint {
        double posX, posY, posZ;
        double motionX, motionY, motionZ;
        float rotationYaw;
        boolean onGround;
        boolean collidedHorizontally;
        boolean sprinting, sneaking;
        PlayerSprintMachine.State sprintState;
        float jumpMovementFactor;
        float landMovementFactor;
        int jumpTicks;
        boolean isInWeb;
        float width;
        float height;
        float fallDistance;
        boolean prevJump;
        public boolean fallFlying;
        boolean glidePendingStop;
        boolean glidePendingStart;
    }
}
