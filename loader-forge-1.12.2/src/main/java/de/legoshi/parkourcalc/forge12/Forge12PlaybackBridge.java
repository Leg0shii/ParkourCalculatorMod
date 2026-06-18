package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.MovementInput;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.UUID;

@SuppressWarnings("DuplicatedCode")
public final class Forge12PlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isGamePaused() {
        return Minecraft.getMinecraft().isGamePaused();
    }

    private static final String[] LAST_REPORTED_POS_X = { "lastReportedPosX", "field_175172_bI" };
    private static final String[] LAST_REPORTED_POS_Y = { "lastReportedPosY", "field_175166_bJ" };
    private static final String[] LAST_REPORTED_POS_Z = { "lastReportedPosZ", "field_175167_bK" };
    private static final String[] LAST_REPORTED_YAW = { "lastReportedYaw", "field_175164_bL" };
    private static final String[] LAST_REPORTED_PITCH = { "lastReportedPitch", "field_175165_bM" };
    private static final String[] POSITION_UPDATE_TICKS = { "positionUpdateTicks", "field_175168_bP" };

    private final InputRow currentRow = new InputRow();
    private MovementInput originalInput;

    InputRow getCurrentRow() {
        return currentRow;
    }

    void installPlaybackInput(EntityPlayerSP player) {
        if (originalInput != null) return;
        originalInput = player.movementInput;
        player.movementInput = new PlaybackMovementInput(this);
    }

    void restorePlaybackInput(EntityPlayerSP player) {
        if (originalInput == null) return;
        if (player.movementInput instanceof PlaybackMovementInput) {
            player.movementInput = originalInput;
        }
        originalInput = null;
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getCurrentServerData() != null) return false;
        IntegratedServer s = mc.getIntegratedServer();
        if (s == null) return false;
        return !s.getPublic();
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(uuid);
            if (sp == null) return;
            sp.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, sp.rotationPitch);
            sp.motionX = vel.x;
            sp.motionY = vel.y;
            sp.motionZ = vel.z;
            if (carry != null) {
                de.legoshi.parkourcalc.forge12.sim.SimulatorEntity.applyCheckpoint(sp, carry);
            } else {
                sp.setSprinting(false);
                sp.setSneaking(false);
                sp.onGround = true;
            }
            sp.velocityChanged = false;
        });
        client.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, client.rotationPitch);
        client.renderYawOffset = yaw;
        client.prevRenderYawOffset = yaw;
        client.rotationYawHead = yaw;
        client.prevRotationYawHead = yaw;
        client.motionX = vel.x;
        client.motionY = vel.y;
        client.motionZ = vel.z;
        if (carry != null) {
            de.legoshi.parkourcalc.forge12.sim.SimulatorEntity.applyCheckpoint(client, carry);
        } else {
            client.onGround = true;
        }
        client.fallDistance = 0.0F;
        // Suppress onUpdateWalkingPlayer's position packet until the server's scheduled
        // setPlayerLocation arms targetPos, otherwise the client races and trips moved-wrongly.
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, pos.x, LAST_REPORTED_POS_X);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, client.getEntityBoundingBox().minY, LAST_REPORTED_POS_Y);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, pos.z, LAST_REPORTED_POS_Z);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, yaw, LAST_REPORTED_YAW);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, client.rotationPitch, LAST_REPORTED_PITCH);
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerSP.class, client, 0, POSITION_UPDATE_TICKS);
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        currentRow.setKeyActive(key, pressed);
        KeyBinding kb = bindFor(key);
        if (kb == null) return;
        KeyBinding.setKeyBindState(kb.getKeyCode(), pressed);
        if (pressed && isClickKey(key)) {
            KeyBinding.onTick(kb.getKeyCode());
        }
    }

    private static boolean isClickKey(InputRow.Key key) {
        return key == InputRow.Key.LEFT_CLICK || key == InputRow.Key.RIGHT_CLICK;
    }

    @Override
    public void setYaw(float absoluteYaw) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationYaw = absoluteYaw;
        p.prevRotationYaw = absoluteYaw;
    }

    @Override
    public void setHeadYaw(float absoluteYaw) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationYawHead = absoluteYaw;
    }

    @Override
    public void setPitch(float absolutePitch) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationPitch = absolutePitch;
        p.prevRotationPitch = absolutePitch;
    }

    @Override
    public void releaseAllKeys() {
        for (InputRow.Key k : InputRow.Key.values()) {
            setKey(k, false);
        }
    }

    @Override
    public void closeUI() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) {
                mc.setIngameFocus();
            }
        }
    }

    private static final int EFFECT_DURATION_TICKS = 20000;

    @Override
    public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(uuid);
            if (sp == null) return;
            sp.removePotionEffect(MobEffects.SPEED);
            sp.removePotionEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
            }
        });
        applyClientEffects(client, speedAmplifier, jumpBoostAmplifier);
    }

    private static void applyClientEffects(EntityPlayerSP client, int speedAmplifier, int jumpBoostAmplifier) {
        client.removePotionEffect(MobEffects.SPEED);
        client.removePotionEffect(MobEffects.JUMP_BOOST);
        MobEffects.SPEED.removeAttributesModifiersFromEntity(client, client.getAttributeMap(), 0);
        if (speedAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            MobEffects.SPEED.applyAttributesModifiersToEntity(client, client.getAttributeMap(), speedAmplifier - 1);
        }
        if (jumpBoostAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
        }
    }

    @Override
    public void dumpPlayerState(int tickIndex) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        PotionEffect spd = p.getActivePotionEffect(MobEffects.SPEED);
        PotionEffect jmp = p.getActivePotionEffect(MobEffects.JUMP_BOOST);
        double mvSp = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.posX + "," + p.posY + "," + p.posZ
                + " mot=" + p.motionX + "," + p.motionY + "," + p.motionZ
                + " yaw=" + p.rotationYaw
                + " onG=" + p.onGround
                + " spr=" + p.isSprinting()
                + " sne=" + p.isSneaking()
                + " colH=" + p.collidedHorizontally
                + " mvF=" + p.moveForward
                + " mvS=" + p.moveStrafing
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp);
    }

    private static KeyBinding bindFor(InputRow.Key key) {
        GameSettings o = Minecraft.getMinecraft().gameSettings;
        switch (key) {
            case W: return o.keyBindForward;
            case S: return o.keyBindBack;
            case A: return o.keyBindLeft;
            case D: return o.keyBindRight;
            case JUMP: return o.keyBindJump;
            case SNEAK: return o.keyBindSneak;
            case SPRINT: return o.keyBindSprint;
            case LEFT_CLICK: return o.keyBindAttack;
            case RIGHT_CLICK: return o.keyBindUseItem;
        }
        return null;
    }
}
