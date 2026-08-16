package de.legoshi.parkourcalc.forge12.sim.paired;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.stats.StatBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

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
        super(server, level, profile, new PlayerInteractionManager(level));
        this.respawnInvulnerabilityTicks = 0;
        this.interactionManager.gameType = GameType.SURVIVAL;
        GameType.SURVIVAL.configurePlayerCapabilities(this.capabilities);
        this.getAdvancements().dispose();
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
        if (fallRulingSink != null && onGroundIn && this.fallDistance > 0.0F) {
            fallRulingSink.onLandingRuled(this.fallDistance, state.getBlock(), blockpos);
        }
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }
        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().onFallenUpon(this.world, blockpos, this, this.fallDistance);
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
    public void playSound(SoundEvent sound, float volume, float pitch) {
    }

    @Override
    public EntityPlayer.SleepResult trySleep(BlockPos bedLocation) {
        return EntityPlayer.SleepResult.OTHER_PROBLEM;
    }

    @Override
    protected void outOfWorld() {
    }
}
