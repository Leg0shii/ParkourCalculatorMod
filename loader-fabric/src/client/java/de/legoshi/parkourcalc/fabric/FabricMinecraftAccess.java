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
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
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
    public double[] getPressurePlateFootprint(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return null;
        if (!(world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BasePressurePlateBlock)) return null;
        double inset = 0.0625;
        return new double[] {x + inset, x + 1.0 - inset, z + inset, z + 1.0 - inset};
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
    public double getBlockReach() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.blockInteractionRange() : 4.5;
    }

    @Override
    public Vec3dCore getLookVector(float yawDeg, float pitchDeg) {
        float f = pitchDeg * ((float) Math.PI / 180F);
        float f1 = -yawDeg * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3dCore(f3 * f4, -f5, f2 * f4);
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
        return (mouseButtonMask() & (1 << (InputConstants.MOUSE_BUTTON_LEFT - 1))) != 0;
    }

    @Override
    public boolean isMousePressedRight() {
        return (mouseButtonMask() & (1 << (InputConstants.MOUSE_BUTTON_RIGHT - 1))) != 0;
    }

    @Override
    public double getCursorScreenX() {
        return cursorPos(true);
    }

    @Override
    public double getCursorScreenY() {
        return cursorPos(false);
    }

    private static int mouseButtonMask() {
        return SDLMouse.SDL_GetMouseState(null, null);
    }

    private static double cursorPos(boolean wantX) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            SDLMouse.SDL_GetMouseState(x, y);
            return wantX ? x.get(0) : y.get(0);
        }
    }

    @Override
    public boolean isCtrlDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    @Override
    public boolean isSaveChordDown() {
        return isCtrlDown() && InputConstants.isKeyDown(InputConstants.KEY_S);
    }

    @Override
    public boolean isAltDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
    }

    private static final int UNRESOLVED_SCANCODE = 0;
    private static int undoKey = UNRESOLVED_SCANCODE;
    private static int redoKey = UNRESOLVED_SCANCODE;

    private static int keyTyping(char letter, int fallback) {
        for (int scancode = InputConstants.KEY_A; scancode <= InputConstants.KEY_Z; scancode++) {
            int keycode = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) 0, false);
            if (keycode == letter) return scancode;
        }
        return fallback;
    }

    private static void resolveEditKeys() {
        if (undoKey != UNRESOLVED_SCANCODE) return;
        undoKey = keyTyping('z', InputConstants.KEY_Z);
        redoKey = keyTyping('y', InputConstants.KEY_Y);
    }

    @Override
    public boolean isUndoChordDown() {
        resolveEditKeys();
        return isCtrlDown() && !isShiftDown() && InputConstants.isKeyDown(undoKey);
    }

    @Override
    public boolean isRedoChordDown() {
        resolveEditKeys();
        if (!isCtrlDown()) return false;
        if (InputConstants.isKeyDown(redoKey)) return true;
        return isShiftDown() && InputConstants.isKeyDown(undoKey);
    }

    @Override
    public boolean isCopyChordDown() {
        return isCtrlDown() && InputConstants.isKeyDown(InputConstants.KEY_C);
    }

    @Override
    public boolean isPasteChordDown() {
        return isCtrlDown() && InputConstants.isKeyDown(InputConstants.KEY_V);
    }

    @Override
    public boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
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
