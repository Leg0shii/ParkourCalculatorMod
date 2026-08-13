package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("DuplicatedCode")
public final class Forge8MinecraftAccess implements MinecraftAccess {

    private static final double PICK_REACH = 64.0;

    private static MovingObjectPosition clipLookRay() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World world = mc.theWorld;
        if (view == null || world == null) return null;
        Vec3 eye = view.getPositionEyes(1.0F);
        Vec3 look = view.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * PICK_REACH, look.yCoord * PICK_REACH, look.zCoord * PICK_REACH);
        MovingObjectPosition hit = world.rayTraceBlocks(eye, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return hit;
    }

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
    public int[] getLookedAtBlock() {
        MovingObjectPosition hit = clipLookRay();
        if (hit == null) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;
        return new int[] {pos.getX(), pos.getY(), pos.getZ()};
    }

    @Override
    public boolean isBlockSolid(int x, int y, int z) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(world, pos, state) != null;
    }

    @Override
    public boolean isClimbable(int x, int y, int z) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == Blocks.ladder || block == Blocks.vine;
    }

    @Override
    public boolean isSlimeBlock(int x, int y, int z) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return false;
        return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.slime_block;
    }

    @Override
    public boolean isIce(int x, int y, int z) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == Blocks.ice || block == Blocks.packed_ice;
    }

    @Override
    public Face getLookedAtFace() {
        MovingObjectPosition hit = clipLookRay();
        if (hit == null) return null;
        return toFace(hit.sideHit);
    }

    @Override
    public Vec3dCore getLookedAtHitVec() {
        MovingObjectPosition hit = clipLookRay();
        if (hit == null || hit.hitVec == null) return null;
        return new Vec3dCore(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
    }

    @Override
    public double getEyeHeight(boolean sneaking) {
        return sneaking ? 1.54 : 1.62;
    }

    @Override
    public double clipBlockDistance(Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return -1.0;
        Vec3 start = new Vec3(origin.x, origin.y, origin.z);
        Vec3 end = new Vec3(
                origin.x + direction.x * maxDistance,
                origin.y + direction.y * maxDistance,
                origin.z + direction.z * maxDistance);
        MovingObjectPosition hit = world.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec == null) return -1.0;
        return hit.hitVec.distanceTo(start);
    }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return out;
        AxisAlignedBB mask = new AxisAlignedBB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    state.getBlock().addCollisionBoxesToList(world, pos, state, mask, boxes, null);
                }
            }
        }
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
