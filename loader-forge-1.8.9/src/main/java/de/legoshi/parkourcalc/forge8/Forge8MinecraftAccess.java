package de.legoshi.parkourcalc.forge8;

import com.google.common.util.concurrent.ListenableFuture;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Vec3;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class Forge8MinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return Vec3dCore.ZERO;
        return new Vec3dCore(player.posX, player.posY, player.posZ);
    }

    @Override
    public Vec3dCore getEyePosition() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        Vec3 p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
    }

    @Override
    public Vec3dCore getLookDirection() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        Vec3 d = view.getLook(1.0F);
        return new Vec3dCore(d.xCoord, d.yCoord, d.zCoord);
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
        return mc.thePlayer != null && mc.theWorld != null;
    }

    @Override
    public boolean isSinglePlayer() {
        return Minecraft.getMinecraft().getIntegratedServer() != null;
    }

    @Override
    public <T> T runOnServerThread(Supplier<T> task) {
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) return task.get();
        if (server.isCallingFromMinecraftThread()) return task.get();
        Callable<T> callable = task::get;
        ListenableFuture<T> future = server.callFromMainThread(callable);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }
}
