package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class FabricMinecraftAccess implements MinecraftAccess {

    private static final double PICK_REACH = 64.0;

    private static BlockHitResult clipLookRay() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) return null;
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 end = eye.add(look.scale(PICK_REACH));
        BlockHitResult hit = world.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        return hit;
    }

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
        BlockHitResult hit = clipLookRay();
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
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
    public boolean isClimbable(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return false;
        return world.getBlockState(new BlockPos(x, y, z)).is(BlockTags.CLIMBABLE);
    }

    @Override
    public boolean isSlimeBlock(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return false;
        return world.getBlockState(new BlockPos(x, y, z)).is(Blocks.SLIME_BLOCK);
    }

    @Override
    public boolean isIce(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return false;
        BlockState state = world.getBlockState(new BlockPos(x, y, z));
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
    }

    @Override
    public Face getLookedAtFace() {
        BlockHitResult hit = clipLookRay();
        if (hit == null) return null;
        return toFace(hit.getDirection());
    }

    @Override
    public Vec3dCore getLookedAtHitVec() {
        BlockHitResult hit = clipLookRay();
        if (hit == null) return null;
        Vec3 p = hit.getLocation();
        if (p == null) return null;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public double getEyeHeight(boolean sneaking) {
        return sneaking ? 1.27 : 1.62;
    }

    @Override
    public double clipBlockDistance(Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) return -1.0;
        Vec3 start = new Vec3(origin.x, origin.y, origin.z);
        Vec3 end = start.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        BlockHitResult hit = world.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return -1.0;
        return hit.getLocation().distanceTo(start);
    }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return out;
        net.minecraft.world.phys.AABB region = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        for (VoxelShape shape : world.getBlockCollisions(null, region)) {
            for (net.minecraft.world.phys.AABB bb : shape.toAabbs()) {
                out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
            }
        }
        return out;
    }

    @Override
    public List<AABB> getBlockCollisionBoxes(int x, int y, int z) {
        List<AABB> out = new ArrayList<>();
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return out;
        BlockPos pos = new BlockPos(x, y, z);
        for (net.minecraft.world.phys.AABB bb : world.getBlockState(pos).getCollisionShape(world, pos).toAabbs()) {
            out.add(new AABB(
                    new Vec3dCore(x + bb.minX, y + bb.minY, z + bb.minZ),
                    new Vec3dCore(x + bb.maxX, y + bb.maxY, z + bb.maxZ)));
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
    public boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static int undoKey = GLFW.GLFW_KEY_UNKNOWN;
    private static int redoKey = GLFW.GLFW_KEY_UNKNOWN;

    private static int keyTyping(char letter, int fallback) {
        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && name.length() == 1 && Character.toLowerCase(name.charAt(0)) == letter) return key;
        }
        return fallback;
    }

    private static void resolveEditKeys() {
        if (undoKey != GLFW.GLFW_KEY_UNKNOWN) return;
        undoKey = keyTyping('z', GLFW.GLFW_KEY_Z);
        redoKey = keyTyping('y', GLFW.GLFW_KEY_Y);
    }

    @Override
    public boolean isUndoChordDown() {
        resolveEditKeys();
        long window = Minecraft.getInstance().getWindow().handle();
        return isCtrlDown() && !isShiftDown() && GLFW.glfwGetKey(window, undoKey) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isRedoChordDown() {
        resolveEditKeys();
        long window = Minecraft.getInstance().getWindow().handle();
        if (!isCtrlDown()) return false;
        if (GLFW.glfwGetKey(window, redoKey) == GLFW.GLFW_PRESS) return true;
        return isShiftDown() && GLFW.glfwGetKey(window, undoKey) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isCopyChordDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return isCtrlDown() && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean isPasteChordDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return isCtrlDown() && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
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
