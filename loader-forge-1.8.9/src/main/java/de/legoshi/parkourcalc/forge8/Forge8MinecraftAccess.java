package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

import java.util.function.Supplier;

@SuppressWarnings("DuplicatedCode")
public final class Forge8MinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return Vec3dCore.ZERO;
        return new Vec3dCore(player.posX, player.posY, player.posZ);
    }

    @Override
    public float getPlayerYaw() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return 0.0f;
        return player.rotationYaw;
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
    public boolean isSaveChordDown() {
        return Lwjgl2InputState.isSaveChordDown();
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
        // 1.8.9 MinecraftServer.callFromMainThread waits up to one server tick (~50ms) before
        // running, which capped drag at 20fps. ChunkProviderServer has no synchronized or
        // thread-routing here, so we tick on the client thread against WorldServer directly.
        // Reads against a chunk the server is concurrently writing are racy but stable for
        // getBlockState in practice; if races ever surface we can dispatch then.
        return task.get();
    }
}
