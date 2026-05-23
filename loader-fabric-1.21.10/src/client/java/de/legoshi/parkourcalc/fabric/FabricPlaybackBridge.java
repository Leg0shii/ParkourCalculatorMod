package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class FabricPlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isSingleplayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() != null) return false;
        IntegratedServer s = mc.getServer();
        if (s == null) return false;
        return !s.isRemote();
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        p.setPosition(pos.x, pos.y, pos.z);
        p.setVelocity(vel.x, vel.y, vel.z);
        p.setYaw(yaw);
        p.setHeadYaw(yaw);
        p.setBodyYaw(yaw);
    }

    @Override
    public void teleportPositionOnly(Vec3dCore pos) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        p.setPosition(pos.x, pos.y, pos.z);
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        KeyBinding kb = bindFor(key);
        if (kb != null) kb.setPressed(pressed);
    }

    @Override
    public void setYaw(float absoluteYaw) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        p.setYaw(absoluteYaw);
        p.setHeadYaw(absoluteYaw);
        p.setBodyYaw(absoluteYaw);
        p.lastYaw = absoluteYaw;
        p.lastHeadYaw = absoluteYaw;
        p.lastBodyYaw = absoluteYaw;
    }

    @Override
    public void releaseAllKeys() {
        for (InputRow.Key k : InputRow.Key.values()) {
            setKey(k, false);
        }
    }

    @Override
    public void closeUI() {
        FabricParkourCalculator.closeOverlay();
    }

    private static final int EFFECT_DURATION_TICKS = 20000;

    @Override
    public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getServer();
        if (server == null) return;
        UUID uuid = client.getUuid();
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
            if (sp == null) return;
            sp.removeStatusEffect(StatusEffects.SPEED);
            sp.removeStatusEffect(StatusEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false, true));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false, true));
            }
        });
    }

    private static KeyBinding bindFor(InputRow.Key key) {
        GameOptions o = MinecraftClient.getInstance().options;
        switch (key) {
            case W: return o.forwardKey;
            case S: return o.backKey;
            case A: return o.leftKey;
            case D: return o.rightKey;
            case JUMP: return o.jumpKey;
            case SNEAK: return o.sneakKey;
            case SPRINT: return o.sprintKey;
        }
        return null;
    }
}
