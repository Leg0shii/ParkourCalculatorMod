package de.legoshi.parkourcalc.forge8.sim.paired;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.stats.StatBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

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
        super(server, level, profile, new ItemInWorldManager(level));
        this.respawnInvulnerabilityTicks = 0;
        this.theItemInWorldManager.gameType = WorldSettings.GameType.SURVIVAL;
        WorldSettings.GameType.SURVIVAL.configurePlayerCapabilities(this.capabilities);
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
        int i = MathHelper.floor_double(this.posX);
        int j = MathHelper.floor_double(this.posY - 0.2F);
        int k = MathHelper.floor_double(this.posZ);
        BlockPos blockpos = new BlockPos(i, j, k);
        Block block = this.worldObj.getBlockState(blockpos).getBlock();
        if (block.getMaterial() == Material.air) {
            Block below = this.worldObj.getBlockState(blockpos.down()).getBlock();
            if (below instanceof BlockFence || below instanceof BlockWall || below instanceof BlockFenceGate) {
                blockpos = blockpos.down();
                block = below;
            }
        }
        if (fallRulingSink != null && onGroundIn && this.fallDistance > 0.0F) {
            fallRulingSink.onLandingRuled(this.fallDistance, block, blockpos);
        }
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                block.onFallenUpon(this.worldObj, blockpos, this, this.fallDistance);
                this.fallDistance = 0.0F;
            }
        } else if (dy < 0.0) {
            this.fallDistance = (float) (this.fallDistance - dy);
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
    public void playSound(String name, float volume, float pitch) {
    }

    @Override
    public EntityPlayer.EnumStatus trySleep(BlockPos bedLocation) {
        return EntityPlayer.EnumStatus.OTHER_PROBLEM;
    }

    @Override
    protected void kill() {
    }
}
