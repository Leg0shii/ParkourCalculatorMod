package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.mixin.ClientPlayerEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.UUID;

public final class FabricPlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isGamePaused() {
        return MinecraftClient.getInstance().isPaused();
    }

    private static final int EFFECT_DURATION_TICKS = 20000;

    private final InputRow currentRow = new InputRow();
    private Input originalInput;

    InputRow getCurrentRow() {
        return currentRow;
    }

    void installPlaybackInput(ClientPlayerEntity player) {
        if (originalInput != null) return;
        originalInput = player.input;
        player.input = new PlaybackInput(this);
    }

    void restorePlaybackInput(ClientPlayerEntity player) {
        if (originalInput == null) return;
        if (player.input instanceof PlaybackInput) {
            player.input = originalInput;
        }
        originalInput = null;
    }

    @Override
    public boolean isSingleplayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() != null) return false;
        IntegratedServer s = mc.getServer();
        if (s == null) return false;
        return !s.isRemote();
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, boolean onGround) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getServer();
        if (server == null) return;
        UUID uuid = client.getUuid();
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
            if (sp == null) return;
            // EntityPosition overload carries velocity in the teleport packet itself.
            // The 5-arg overload zeroes velocity and the followup setVelocity+velocityModified
            // path fires a SetEntityMotion packet that arrives ~1 tick late and stomps the
            // player mid-playback.
            sp.networkHandler.requestTeleport(
                    new EntityPosition(new Vec3d(pos.x, pos.y, pos.z), new Vec3d(vel.x, vel.y, vel.z), yaw, sp.getPitch()),
                    Collections.emptySet()
            );
        });
        client.updatePositionAndAngles(pos.x, pos.y, pos.z, yaw, client.getPitch());
        client.setVelocity(vel.x, vel.y, vel.z);
        client.setOnGround(onGround);
        client.fallDistance = 0.0;
        // Suppress the player tick's position packet until the server's requestTeleport
        // arms its teleport-pending state, otherwise the client races and trips moved-wrongly.
        ClientPlayerEntityAccessor acc = (ClientPlayerEntityAccessor) client;
        acc.pkc$setLastXClient(pos.x);
        acc.pkc$setLastYClient(pos.y);
        acc.pkc$setLastZClient(pos.z);
        acc.pkc$setLastYawClient(yaw);
        acc.pkc$setLastPitchClient(client.getPitch());
        acc.pkc$setTicksSinceLastPositionPacketSent(0);
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        currentRow.setKeyActive(key, pressed);
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

    @Override
    public void dumpPlayerState(int tickIndex) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        StatusEffectInstance spd = p.getStatusEffect(StatusEffects.SPEED);
        StatusEffectInstance jmp = p.getStatusEffect(StatusEffects.JUMP_BOOST);
        double mvSp = p.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.getX() + "," + p.getY() + "," + p.getZ()
                + " mot=" + p.getVelocity().x + "," + p.getVelocity().y + "," + p.getVelocity().z
                + " yaw=" + p.getYaw()
                + " onG=" + p.isOnGround()
                + " spr=" + p.isSprinting()
                + " sne=" + p.isSneaking()
                + " colH=" + p.horizontalCollision
                + " mvF=" + p.forwardSpeed
                + " mvS=" + p.sidewaysSpeed
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp);
    }

    private static KeyBinding bindFor(InputRow.Key key) {
        GameOptions o = MinecraftClient.getInstance().options;
        return switch (key) {
            case W -> o.forwardKey;
            case S -> o.backKey;
            case A -> o.leftKey;
            case D -> o.rightKey;
            case JUMP -> o.jumpKey;
            case SNEAK -> o.sneakKey;
            case SPRINT -> o.sprintKey;
        };
    }
}
