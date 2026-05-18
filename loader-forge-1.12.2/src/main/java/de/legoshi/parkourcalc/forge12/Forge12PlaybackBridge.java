package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.server.integrated.IntegratedServer;

public final class Forge12PlaybackBridge implements PlaybackBridge {

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
        EntityPlayerSP p = Minecraft.getMinecraft().player;
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
    public void setKey(InputRow.Key key, boolean pressed) {
        KeyBinding kb = bindFor(key);
        if (kb != null) KeyBinding.setKeyBindState(kb.getKeyCode(), pressed);
    }

    @Override
    public void addYaw(float deltaYaw) {
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationYaw += deltaYaw;
        p.rotationYawHead = p.rotationYaw;
        p.renderYawOffset = p.rotationYaw;
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
