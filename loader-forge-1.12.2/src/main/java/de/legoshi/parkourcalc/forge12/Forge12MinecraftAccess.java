package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class Forge12MinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return Vec3dCore.ZERO;
        return new Vec3dCore(player.posX, player.posY, player.posZ);
    }

    @Override
    public Vec3dCore getEyePosition() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        Vec3d p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public Vec3dCore getLookDirection() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        Vec3d d = view.getLook(1.0F);
        return new Vec3dCore(d.x, d.y, d.z);
    }

    @Override
    public boolean isMousePressedLeft() {
        return Lwjgl2InputState.isMousePressedLeft();
    }

    @Override
    public boolean isMousePressedRight() {
        return Lwjgl2InputState.isMousePressedRight();
    }

    @Override
    public double getCursorScreenX() {
        return Lwjgl2InputState.getCursorScreenX();
    }

    @Override
    public double getCursorScreenY() {
        return Lwjgl2InputState.getCursorScreenY();
    }

    @Override
    public boolean isCtrlDown() {
        return Lwjgl2InputState.isCtrlDown();
    }

    @Override
    public boolean isShiftDown() {
        return Lwjgl2InputState.isShiftDown();
    }

    @Override
    public boolean isReady() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null && mc.world != null;
    }
}
