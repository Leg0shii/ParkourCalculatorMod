package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class FabricMinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return Vec3dCore.ZERO;
        Vec3 p = player.position();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public float getPlayerYaw() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0.0f;
        return player.getYRot();
    }

    @Override
    public Vec3dCore getEyePosition() {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 p = camera.position();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public Vec3dCore getLookDirection() {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 d = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        return new Vec3dCore(d.x, d.y, d.z);
    }

    @Override
    public int[] getLookedAtBlock() {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (pos == null) return null;
        return new int[] {pos.getX(), pos.getY(), pos.getZ()};
    }

    @Override
    public boolean isBlockSolid(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    @Override
    public boolean isMousePressedLeft() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isMousePressedRight() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    @Override
    public double getCursorScreenX() {
        return cursorPos(true);
    }

    @Override
    public double getCursorScreenY() {
        return cursorPos(false);
    }

    private static double cursorPos(boolean wantX) {
        long window = Minecraft.getInstance().getWindow().handle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, x, y);
            return wantX ? x.get(0) : y.get(0);
        }
    }

    @Override
    public boolean isCtrlDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isSaveChordDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return isCtrlDown() && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isReady() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null;
    }

    @Override
    public boolean isSinglePlayer() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    @Override
    public <T> T runOnServerThread(Supplier<T> task) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return task.get();
        // Inline on the server thread too: avoids self-deadlock if anything re-enters.
        if (server.isSameThread()) return task.get();
        CompletableFuture<T> future = new CompletableFuture<T>();
        server.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.join();
    }
}
