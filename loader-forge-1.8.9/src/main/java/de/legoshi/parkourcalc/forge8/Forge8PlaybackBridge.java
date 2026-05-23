package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;

import java.util.UUID;

public final class Forge8PlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isSingleplayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getCurrentServerData() != null) return false;
        IntegratedServer s = mc.getIntegratedServer();
        if (s == null) return false;
        return !s.getPublic();
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        p.setPositionAndUpdate(pos.x, pos.y, pos.z);
        p.motionX = vel.x;
        p.motionY = vel.y;
        p.motionZ = vel.z;
        p.rotationYaw = yaw;
        p.rotationYawHead = yaw;
        p.renderYawOffset = yaw;
    }

    @Override
    public void teleportPositionOnly(Vec3dCore pos) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        p.setPositionAndUpdate(pos.x, pos.y, pos.z);
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        KeyBinding kb = bindFor(key);
        if (kb != null) KeyBinding.setKeyBindState(kb.getKeyCode(), pressed);
    }

    @Override
    public void setYaw(float absoluteYaw) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        p.rotationYaw = absoluteYaw;
        p.rotationYawHead = absoluteYaw;
        p.renderYawOffset = absoluteYaw;
        p.prevRotationYaw = absoluteYaw;
        p.prevRotationYawHead = absoluteYaw;
        p.prevRenderYawOffset = absoluteYaw;
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
        EntityPlayerSP client = mc.thePlayer;
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = server.getConfigurationManager().getPlayerByUUID(uuid);
            if (sp == null) return;
            sp.removePotionEffect(Potion.moveSpeed.id);
            sp.removePotionEffect(Potion.jump.id);
            if (speedAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(Potion.jump.id, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
            }
        });
    }

    @Override
    public void dumpPlayerState(int tickIndex) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        PotionEffect spd = p.getActivePotionEffect(Potion.moveSpeed);
        PotionEffect jmp = p.getActivePotionEffect(Potion.jump);
        double mvSp = p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue();
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.posX + "," + p.posY + "," + p.posZ
                + " mot=" + p.motionX + "," + p.motionY + "," + p.motionZ
                + " yaw=" + p.rotationYaw
                + " onG=" + p.onGround
                + " spr=" + p.isSprinting()
                + " sne=" + p.isSneaking()
                + " colH=" + p.isCollidedHorizontally
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
        }
        return null;
    }
}
