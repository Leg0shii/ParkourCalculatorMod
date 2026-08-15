package de.legoshi.parkourcalc.fabric.sim.paired;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stat;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

public final class PairedServerPlayer extends ServerPlayer {

    public interface DamageSink {
        void onDamageRuled(DamageSource source, float amount);
    }

    public interface FallRulingSink {
        void onLandingRuled(double fallDistance, BlockState sampledState, BlockPos sampledPos);
    }

    private DamageSink damageSink;
    private FallRulingSink fallRulingSink;
    private BooleanSupplier damageGate = () -> true;

    public PairedServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
        gameMode.setGameModeForPlayer(GameType.DEFAULT_MODE, null);
        getAdvancements().clearTriggers();
    }

    public void setDamageSink(DamageSink sink) {
        this.damageSink = sink;
    }

    public void setFallRulingSink(FallRulingSink sink) {
        this.fallRulingSink = sink;
    }

    public void setDamageGate(BooleanSupplier gate) {
        this.damageGate = gate;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (fallRulingSink != null && onGround && this.fallDistance > 0.0) {
            fallRulingSink.onLandingRuled(this.fallDistance, onState, pos);
        }
        super.checkFallDamage(ya, onGround, onState, pos);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!damageGate.getAsBoolean()) {
            return false;
        }
        boolean applied = super.hurtServer(level, source, amount);
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
    public void die(DamageSource source) {
    }

    @Override
    public void causeFoodExhaustion(float amount) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
    }

    @Override
    protected void onBelowWorld() {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }

    @Override
    public void resetStat(Stat<?> stat) {
    }

    @Override
    public void startSleeping(BlockPos bedPosition) {
    }

    @Override
    public OptionalInt openMenu(MenuProvider provider) {
        return OptionalInt.empty();
    }
}
