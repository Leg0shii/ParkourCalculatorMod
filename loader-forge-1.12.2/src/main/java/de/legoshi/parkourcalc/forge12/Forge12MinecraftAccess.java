package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import de.legoshi.parkourcalc.forge12.sim.Forge12Simulator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@SuppressWarnings("DuplicatedCode")
public final class Forge12MinecraftAccess implements MinecraftAccess {

    private static final double PICK_REACH = 64.0;

    private static RayTraceResult clipLookRay() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World world = mc.world;
        if (view == null || world == null) return null;
        Vec3d eye = view.getPositionEyes(1.0F);
        Vec3d look = view.getLook(1.0F);
        Vec3d end = new Vec3d(eye.x + look.x * PICK_REACH, eye.y + look.y * PICK_REACH, eye.z + look.z * PICK_REACH);
        RayTraceResult hit = world.rayTraceBlocks(eye, end);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return null;
        return hit;
    }

    @Override
    public Vec3dCore getPlayerPosition() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return Vec3dCore.ZERO;
        return new Vec3dCore(player.posX, player.posY, player.posZ);
    }

    @Override
    public float getPlayerYaw() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return 0.0f;
        return player.rotationYaw;
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
    public int[] getLookedAtBlock() {
        RayTraceResult hit = clipLookRay();
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;
        return new int[] {pos.getX(), pos.getY(), pos.getZ()};
    }

    @Override
    public boolean isBlockSolid(int x, int y, int z) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        return world.getBlockState(pos).getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
    }

    @Override
    public boolean isClimbable(int x, int y, int z) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == Blocks.LADDER || block == Blocks.VINE;
    }

    @Override
    public boolean isSlimeBlock(int x, int y, int z) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return false;
        return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.SLIME_BLOCK;
    }

    @Override
    public boolean isIce(int x, int y, int z) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.FROSTED_ICE;
    }

    @Override
    public Face getLookedAtFace() {
        RayTraceResult hit = clipLookRay();
        if (hit == null) return null;
        return toFace(hit.sideHit);
    }

    @Override
    public Vec3dCore getLookedAtHitVec() {
        RayTraceResult hit = clipLookRay();
        if (hit == null || hit.hitVec == null) return null;
        return new Vec3dCore(hit.hitVec.x, hit.hitVec.y, hit.hitVec.z);
    }

    @Override
    public double getEyeHeight(boolean sneaking) {
        return sneaking ? 1.54 : 1.62;
    }

    @Override
    public double clipBlockDistance(Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return -1.0;
        Vec3d start = new Vec3d(origin.x, origin.y, origin.z);
        Vec3d end = new Vec3d(
                origin.x + direction.x * maxDistance,
                origin.y + direction.y * maxDistance,
                origin.z + direction.z * maxDistance);
        RayTraceResult hit = world.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.hitVec == null) return -1.0;
        return hit.hitVec.distanceTo(start);
    }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        World world = Minecraft.getMinecraft().world;
        if (world == null) return out;
        AxisAlignedBB region = new AxisAlignedBB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        for (AxisAlignedBB bb : world.getCollisionBoxes(null, region)) {
            out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
        }
        return out;
    }

    @Override
    public List<AABB> getBlockCollisionBoxes(int x, int y, int z) {
        List<AABB> out = new ArrayList<>();
        World world = Minecraft.getMinecraft().world;
        if (world == null) return out;
        BlockPos pos = new BlockPos(x, y, z);
        AxisAlignedBB mask = new AxisAlignedBB(x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0);
        List<AxisAlignedBB> boxes = new ArrayList<>();
        world.getBlockState(pos).addCollisionBoxToList(world, pos, mask, boxes, null, false);
        for (AxisAlignedBB bb : boxes) {
            out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
        }
        return out;
    }

    private static Face toFace(EnumFacing side) {
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
    public boolean isAltDown() {
        return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU);
    }

    @Override
    public boolean isSaveChordDown() {
        return Lwjgl2InputState.isSaveChordDown();
    }

    @Override
    public boolean isUndoChordDown() {
        return Lwjgl2InputState.isUndoChordDown();
    }

    @Override
    public boolean isRedoChordDown() {
        return Lwjgl2InputState.isRedoChordDown();
    }

    @Override
    public boolean isCopyChordDown() {
        return Lwjgl2InputState.isCopyChordDown();
    }

    @Override
    public boolean isPasteChordDown() {
        return Lwjgl2InputState.isPasteChordDown();
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

    @Override
    public boolean isSinglePlayer() {
        return Minecraft.getMinecraft().getIntegratedServer() != null;
    }

    @Override
    public <T> T runOnServerThread(final Supplier<T> task) {
        // 1.12.2 MinecraftServer.callFromMainThread waits up to one server tick (~50ms) before
        // running, which capped drag at 20fps. ChunkProviderServer has no synchronized or
        // thread-routing here, so we tick on the client thread against WorldServer directly.
        // Reads against a chunk the server is concurrently writing are racy but stable for
        // getBlockState in practice; if races ever surface we can dispatch then.
        if (!Forge12Simulator.needsServerThread()) {
            return task.get();
        }
        IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
        if (server == null) {
            return task.get();
        }
        try {
            return server.callFromMainThread(new Callable<T>() {
                @Override
                public T call() {
                    return task.get();
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        }
    }
}
