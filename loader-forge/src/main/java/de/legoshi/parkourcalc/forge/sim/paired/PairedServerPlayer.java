package de.legoshi.parkourcalc.forge.sim.paired;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
//? if >=1.12 {
import net.minecraft.block.state.IBlockState;
//?} else {
/*import net.minecraft.block.material.Material;
*///?}
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
//? if >=1.12 {
import net.minecraft.server.management.PlayerInteractionManager;
//?} else {
/*import net.minecraft.server.management.ItemInWorldManager;
*///?}
import net.minecraft.stats.StatBase;
//? if <1.12
//import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
//? if >=1.12 {
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
//?} else {
/*import net.minecraft.util.MathHelper;
*///?}
import net.minecraft.world.WorldServer;
//? if <1.12
//import net.minecraft.world.WorldSettings;

public final class PairedServerPlayer extends EntityPlayerMP {

    public interface DamageSink {
        void onDamageRuled(DamageSource source, float amount);
    }

    public interface FallRulingSink {
        void onLandingRuled(float fallDistance, Block sampledBlock, BlockPos sampledPos);
    }

    public interface DamageGate {
        boolean isDamageEnabled();
    }

    private DamageSink damageSink;
    private FallRulingSink fallRulingSink;
    private DamageGate damageGate;

    public PairedServerPlayer(MinecraftServer server, WorldServer level, GameProfile profile) {
        //? if >=1.12 {
        super(server, level, profile, new PlayerInteractionManager(level));
        this.respawnInvulnerabilityTicks = 0;
        this.interactionManager.gameType = GameType.SURVIVAL;
        GameType.SURVIVAL.configurePlayerCapabilities(this.capabilities);
        this.getAdvancements().dispose();
        //?} else {
        /*super(server, level, profile, new ItemInWorldManager(level));
        this.respawnInvulnerabilityTicks = 0;
        this.theItemInWorldManager.gameType = WorldSettings.GameType.SURVIVAL;
        WorldSettings.GameType.SURVIVAL.configurePlayerCapabilities(this.capabilities);
        *///?}
    }

    public void setDamageSink(DamageSink sink) {
        this.damageSink = sink;
    }

    public void setFallRulingSink(FallRulingSink sink) {
        this.fallRulingSink = sink;
    }

    public void setDamageGate(DamageGate gate) {
        this.damageGate = gate;
    }

    public float getLastDamage() {
        return this.lastDamage;
    }

    public void setLastDamage(float value) {
        this.lastDamage = value;
    }

    @Override
    public void handleFalling(double dy, boolean onGroundIn) {
        //? if >=1.12 {
        int i = MathHelper.floor(this.posX);
        int j = MathHelper.floor(this.posY - 0.20000000298023224D);
        int k = MathHelper.floor(this.posZ);
        BlockPos blockpos = new BlockPos(i, j, k);
        IBlockState state = this.world.getBlockState(blockpos);
        if (state.getBlock().isAir(state, this.world, blockpos)) {
            BlockPos below = blockpos.down();
            IBlockState belowState = this.world.getBlockState(below);
            Block block = belowState.getBlock();
            if (block instanceof BlockFence || block instanceof BlockWall || block instanceof BlockFenceGate) {
                blockpos = below;
                state = belowState;
            }
        }
        Block landedOn = state.getBlock();
        //?} else {
        /*int i = MathHelper.floor_double(this.posX);
        int j = MathHelper.floor_double(this.posY - 0.2F);
        int k = MathHelper.floor_double(this.posZ);
        BlockPos blockpos = new BlockPos(i, j, k);
        Block landedOn = this.worldObj.getBlockState(blockpos).getBlock();
        if (landedOn.getMaterial() == Material.air) {
            Block below = this.worldObj.getBlockState(blockpos.down()).getBlock();
            if (below instanceof BlockFence || below instanceof BlockWall || below instanceof BlockFenceGate) {
                blockpos = blockpos.down();
                landedOn = below;
            }
        }
        *///?}
        if (fallRulingSink != null && onGroundIn && this.fallDistance > 0.0F) {
            fallRulingSink.onLandingRuled(this.fallDistance, landedOn, blockpos);
        }
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                //? if >=1.12 {
                landedOn.onFallenUpon(this.world, blockpos, this, this.fallDistance);
                //?} else {
                /*landedOn.onFallenUpon(this.worldObj, blockpos, this, this.fallDistance);
                *///?}
            }
            this.fallDistance = 0.0F;
        } else if (dy < 0.0D) {
            this.fallDistance = (float) ((double) this.fallDistance - dy);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (damageGate != null && !damageGate.isDamageEnabled()) {
            return false;
        }
        boolean applied = super.attackEntityFrom(source, amount);
        if (applied && damageSink != null) {
            damageSink.onDamageRuled(source, amount);
        }
        return applied;
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(getMaxHealth());
    }

    @Override
    public void onDeath(DamageSource source) {
    }

    @Override
    public void addExhaustion(float amount) {
    }

    @Override
    public void addStat(StatBase stat, int amount) {
    }

    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    //? if >=1.12 {
    public void playSound(SoundEvent sound, float volume, float pitch) {
    //?} else {
    /*public void playSound(String name, float volume, float pitch) {
    *///?}
    }

    @Override
    //? if >=1.12 {
    public EntityPlayer.SleepResult trySleep(BlockPos bedLocation) {
        return EntityPlayer.SleepResult.OTHER_PROBLEM;
    //?} else {
    /*public EntityPlayer.EnumStatus trySleep(BlockPos bedLocation) {
        return EntityPlayer.EnumStatus.OTHER_PROBLEM;
    *///?}
    }

    @Override
    //? if >=1.12 {
    protected void outOfWorld() {
    //?} else {
    /*protected void kill() {
    *///?}
    }
}
