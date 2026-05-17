package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class FabricMinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return Vec3dCore.ZERO;
        Vec3d p = player.getEntityPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public Vec3dCore getEyePosition() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d p = camera.getPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public Vec3dCore getLookDirection() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d d = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        return new Vec3dCore(d.x, d.y, d.z);
    }

    @Override
    public boolean isMousePressedLeft() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isCtrlDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isShiftDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.world != null;
    }
}
