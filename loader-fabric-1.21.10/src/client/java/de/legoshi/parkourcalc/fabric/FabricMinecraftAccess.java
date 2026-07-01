package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class FabricMinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return Vec3dCore.ZERO;
        Vec3d p = player.getEntityPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public float getPlayerYaw() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return 0.0f;
        return player.getYaw();
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
    public int[] getLookedAtBlock() {
        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        if (!(hit instanceof BlockHitResult) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (pos == null) return null;
        return new int[] {pos.getX(), pos.getY(), pos.getZ()};
    }

    @Override
    public boolean isBlockSolid(int x, int y, int z) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    @Override
    public Face getLookedAtFace() {
        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        if (!(hit instanceof BlockHitResult) || hit.getType() != HitResult.Type.BLOCK) return null;
        return toFace(((BlockHitResult) hit).getSide());
    }

    @Override
    public Vec3dCore getLookedAtHitVec() {
        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        if (!(hit instanceof BlockHitResult) || hit.getType() != HitResult.Type.BLOCK) return null;
        Vec3d p = hit.getPos();
        if (p == null) return null;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return out;
        Box region = new Box(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        for (VoxelShape shape : world.getBlockCollisions(null, region)) {
            for (Box bb : shape.getBoundingBoxes()) {
                out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
            }
        }
        return out;
    }

    private static Face toFace(Direction side) {
        if (side == null) return null;
        switch (side) {
            case DOWN: return Face.NEG_Y;
            case UP: return Face.POS_Y;
            case NORTH: return Face.NEG_Z;
            case SOUTH: return Face.POS_Z;
            case WEST: return Face.NEG_X;
            case EAST: return Face.POS_X;
            default: return null;
        }
    }

    @Override
    public boolean isMousePressedLeft() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isMousePressedRight() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
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
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, x, y);
            return wantX ? x.get(0) : y.get(0);
        }
    }

    @Override
    public boolean isCtrlDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isSaveChordDown() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return isCtrlDown() && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
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

    @Override
    public boolean isSinglePlayer() {
        return MinecraftClient.getInstance().getServer() != null;
    }

    @Override
    public <T> T runOnServerThread(Supplier<T> task) {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) return task.get();
        // Inline on the server thread too: avoids self-deadlock if anything re-enters.
        if (server.isOnThread()) return task.get();
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
