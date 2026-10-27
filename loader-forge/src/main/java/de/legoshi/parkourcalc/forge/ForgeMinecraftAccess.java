package de.legoshi.parkourcalc.forge;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.forge.core.lwjgl2.Lwjgl2InputState;
import de.legoshi.parkourcalc.forge.sim.ForgeSimulator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
//? if <1.12
//import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
//? if >=1.12 {
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
//?} else {
/*import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
*///?}
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@SuppressWarnings("DuplicatedCode")
public final class ForgeMinecraftAccess implements MinecraftAccess {

    @Override
    public Vec3dCore getPlayerPosition() {
        EntityPlayerSP player = McClient.player();
        if (player == null) return Vec3dCore.ZERO;
        return new Vec3dCore(player.posX, player.posY, player.posZ);
    }

    @Override
    public float getPlayerYaw() {
        EntityPlayerSP player = McClient.player();
        if (player == null) return 0.0f;
        return player.rotationYaw;
    }

    @Override
    public float getPlayerPitch() {
        EntityPlayerSP player = McClient.player();
        if (player == null) return 0.0f;
        return player.rotationPitch;
    }

    @Override
    public boolean isPlayerWearingElytra() {
        //? if >=1.12 {
        EntityPlayerSP player = McClient.player();
        return player != null && de.legoshi.parkourcalc.forge.sim.ElytraGlide.wearing(player);
        //?} else {
        /*return false;
        *///?}
    }

    @Override
    public Vec3dCore getEyePosition() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        //? if >=1.12 {
        Vec3d p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.x, p.y, p.z);
        //?} else {
        /*Vec3 p = view.getPositionEyes(1.0F);
        return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
        *///?}
    }

    @Override
    public Vec3dCore getLookDirection() {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) return Vec3dCore.ZERO;
        //? if >=1.12 {
        Vec3d d = view.getLook(1.0F);
        return new Vec3dCore(d.x, d.y, d.z);
        //?} else {
        /*Vec3 d = view.getLook(1.0F);
        return new Vec3dCore(d.xCoord, d.yCoord, d.zCoord);
        *///?}
    }

    @Override
    public boolean isBlockSolid(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        //? if >=1.12 {
        return world.getBlockState(pos).getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
        //?} else {
        /*IBlockState state = world.getBlockState(pos);
        return state.getBlock().getCollisionBoundingBox(world, pos, state) != null;
        *///?}
    }

    @Override
    public boolean isClimbable(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        //? if >=1.12 {
        return block == Blocks.LADDER || block == Blocks.VINE;
        //?} else {
        /*return block == Blocks.ladder || block == Blocks.vine;
        *///?}
    }

    @Override
    public boolean isSlimeBlock(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return false;
        //? if >=1.12 {
        return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.SLIME_BLOCK;
        //?} else {
        /*return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.slime_block;
        *///?}
    }

    @Override
    public boolean isIce(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return false;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        //? if >=1.12 {
        return block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.FROSTED_ICE;
        //?} else {
        /*return block == Blocks.ice || block == Blocks.packed_ice;
        *///?}
    }

    @Override
    public boolean isCobweb(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return false;
        //? if >=1.12 {
        return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.WEB;
        //?} else {
        /*return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.web;
        *///?}
    }

    @Override
    public double getInsideBlockInset() {
        return 0.001;
    }

    @Override
    public double[] getPressurePlateFootprint(int x, int y, int z) {
        World world = McClient.world();
        if (world == null) return null;
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (!(block instanceof BlockBasePressurePlate)) return null;
        double inset = 0.125;
        return new double[] {x + inset, x + 1.0 - inset, z + inset, z + 1.0 - inset};
    }

    @Override
    public double getEyeHeight(boolean sneaking) {
        return sneaking ? 1.54 : 1.62;
    }

    @Override
    public double getBlockReach() {
        PlayerControllerMP controller = Minecraft.getMinecraft().playerController;
        return controller != null ? controller.getBlockReachDistance() : 4.5;
    }

    @Override
    public Vec3dCore getLookVector(float yawDeg, float pitchDeg) {
        float f = MathHelper.cos(-yawDeg * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yawDeg * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitchDeg * 0.017453292F);
        float f3 = MathHelper.sin(-pitchDeg * 0.017453292F);
        return new Vec3dCore(f1 * f2, f3, f * f2);
    }

    @Override
    public double clipBlockDistance(Vec3dCore origin, Vec3dCore direction, double maxDistance) {
        World world = McClient.world();
        if (world == null) return -1.0;
        //? if >=1.12 {
        Vec3d start = new Vec3d(origin.x, origin.y, origin.z);
        Vec3d end = new Vec3d(
        //?} else {
        /*Vec3 start = new Vec3(origin.x, origin.y, origin.z);
        Vec3 end = new Vec3(
        *///?}
                origin.x + direction.x * maxDistance,
                origin.y + direction.y * maxDistance,
                origin.z + direction.z * maxDistance);
        //? if >=1.12 {
        RayTraceResult hit = world.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.hitVec == null) return -1.0;
        //?} else {
        /*MovingObjectPosition hit = world.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec == null) return -1.0;
        *///?}
        return hit.hitVec.distanceTo(start);
    }

    @Override
    public List<AABB> getCollisionBoxes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<AABB> out = new ArrayList<>();
        World world = McClient.world();
        if (world == null) return out;
        //? if >=1.12 {
        AxisAlignedBB region = new AxisAlignedBB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        for (AxisAlignedBB bb : world.getCollisionBoxes(null, region)) {
        //?} else {
        /*AxisAlignedBB mask = new AxisAlignedBB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
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
        *///?}
            out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
        }
        return out;
    }

    @Override
    public List<AABB> getBlockCollisionBoxes(int x, int y, int z) {
        List<AABB> out = new ArrayList<>();
        World world = McClient.world();
        if (world == null) return out;
        BlockPos pos = new BlockPos(x, y, z);
        //? if >=1.12 {
        AxisAlignedBB mask = new AxisAlignedBB(x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0);
        List<AxisAlignedBB> boxes = new ArrayList<>();
        world.getBlockState(pos).addCollisionBoxToList(world, pos, mask, boxes, null, false);
        //?} else {
        /*IBlockState state = world.getBlockState(pos);
        AxisAlignedBB mask = new AxisAlignedBB(x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0);
        List<AxisAlignedBB> boxes = new ArrayList<>();
        state.getBlock().addCollisionBoxesToList(world, pos, state, mask, boxes, null);
        *///?}
        for (AxisAlignedBB bb : boxes) {
            out.add(new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ)));
        }
        return out;
    }

    @Override
    public boolean isMousePressedLeft() {
        return Lwjgl2InputState.isMousePressedLeft();
    }

    @Override
    public boolean isSelectTickPressed() {
        return ForgeParkourCalculator.isSelectTickDown();
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
    public float getMouseSensitivity() {
        return Minecraft.getMinecraft().gameSettings.mouseSensitivity;
    }

    @Override
    public void setMouseSensitivity(float sensitivity) {
        Minecraft.getMinecraft().gameSettings.mouseSensitivity = sensitivity;
        Minecraft.getMinecraft().gameSettings.saveOptions();
    }

    @Override
    public boolean isReady() {
        return McClient.player() != null && McClient.world() != null;
    }

    @Override
    public boolean isSinglePlayer() {
        return Minecraft.getMinecraft().getIntegratedServer() != null;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        WorldClient world = McClient.world();
        //? if >=1.12 {
        return world != null && !world.getChunk(chunkX, chunkZ).isEmpty();
        //?} else {
        /*return world != null && !world.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty();
        *///?}
    }

    @Override
    public String getRerunSimulationKeyName() {
        return ForgeParkourCalculator.rerunSimulationKeyName();
    }

    @Override
    public <T> T runOnServerThread(final Supplier<T> task) {
        // MinecraftServer.callFromMainThread waits up to one server tick (~50ms) before
        // running, which capped drag at 20fps. ChunkProviderServer has no synchronized or
        // thread-routing here, so we tick on the client thread against WorldServer directly.
        // Reads against a chunk the server is concurrently writing are racy but stable for
        // getBlockState in practice; if races ever surface we can dispatch then.
        if (!ForgeSimulator.needsServerThread()) {
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
