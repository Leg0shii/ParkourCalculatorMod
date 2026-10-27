package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.anglesolver.solver.VelocityPacketCodec;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.fabric.input.FabricInput;
import de.legoshi.parkourcalc.fabric.sim.paired.PairedServerSim;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
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
    public float getPlayerPitch() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return 0.0f;
        return player.getXRot();
    }

    @Override
    public Vec3dCore getEyePosition() {
        //? if >=26.1 {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 p = camera.position();
        //?} else {
        /*Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 p = camera.getPosition();
        *///?}
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    public Vec3dCore getLookDirection() {
        //? if >=26.1 {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 d = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        //?} else {
        /*Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 d = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        *///?}
        return new Vec3dCore(d.x, d.y, d.z);
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
    public boolean isCobweb(int x, int y, int z) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) return false;
        return world.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB);
    }

    @Override
    public VelocityPacketCodec velocityPacketCodec() {
        return v -> {
            Vec3 q = PairedServerSim.quantizeVelocity(new Vec3(v.x, v.y, v.z));
            return new Vec3dCore(q.x, q.y, q.z);
        };
    }

    @Override
    public double getInsideBlockInset() {
        //? if >=1.21.2 {
        return 1.0E-5F;
        //?} else {
        /*return 1.0E-7;
        *///?}
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
    public double getEyeHeight(boolean sneaking) {
        return sneaking ? 1.27 : 1.62;
    }

    @Override
    public double getBlockReach() {
        //? if >=1.20.5 {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.blockInteractionRange() : 4.5;
        //?} else {
        /*net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        return gameMode != null ? gameMode.getPickRange() : 4.5;
        *///?}
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

    @Override
    public boolean isMousePressedLeft() {
        return FabricInput.isMouseDown(InputConstants.MOUSE_BUTTON_LEFT);
    }

    @Override
    public boolean isSelectTickPressed() {
        return FabricParkourCalculator.isSelectTickDown();
    }

    @Override
    public boolean isMousePressedRight() {
        return FabricInput.isMouseDown(InputConstants.MOUSE_BUTTON_RIGHT);
    }

    @Override
    public double getCursorScreenX() {
        return FabricInput.cursorX();
    }

    @Override
    public double getCursorScreenY() {
        return FabricInput.cursorY();
    }

    @Override
    public boolean isCtrlDown() {
        return FabricInput.isCtrlDown();
    }

    @Override
    public boolean isSaveChordDown() {
        return isCtrlDown() && FabricInput.isKeyDown(InputConstants.KEY_S);
    }

    @Override
    public boolean isAltDown() {
        return FabricInput.isAltDown();
    }

    private static int undoKey = FabricInput.UNRESOLVED_KEY;
    private static int redoKey = FabricInput.UNRESOLVED_KEY;

    private static void resolveEditKeys() {
        if (undoKey != FabricInput.UNRESOLVED_KEY) return;
        undoKey = FabricInput.keyTyping('z', InputConstants.KEY_Z);
        redoKey = FabricInput.keyTyping('y', InputConstants.KEY_Y);
    }

    public static boolean isEditChordPress(int key) {
        resolveEditKeys();
        if (key != undoKey && key != redoKey) return false;
        return FabricInput.isCtrlDown();
    }

    @Override
    public boolean isUndoChordDown() {
        resolveEditKeys();
        return isCtrlDown() && !isShiftDown() && FabricInput.isKeyDown(undoKey);
    }

    @Override
    public boolean isRedoChordDown() {
        resolveEditKeys();
        if (!isCtrlDown()) return false;
        if (FabricInput.isKeyDown(redoKey)) return true;
        return isShiftDown() && FabricInput.isKeyDown(undoKey);
    }

    @Override
    public boolean isCopyChordDown() {
        return isCtrlDown() && FabricInput.isKeyDown(InputConstants.KEY_C);
    }

    @Override
    public boolean isPasteChordDown() {
        return isCtrlDown() && FabricInput.isKeyDown(InputConstants.KEY_V);
    }

    @Override
    public boolean isShiftDown() {
        return FabricInput.isShiftDown();
    }

    @Override
    public float getMouseSensitivity() {
        return Minecraft.getInstance().options.sensitivity().get().floatValue();
    }

    @Override
    public void setMouseSensitivity(float sensitivity) {
        Minecraft client = Minecraft.getInstance();
        client.options.sensitivity().set((double) sensitivity);
        client.options.save();
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
    public boolean isUltrawarm() {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimensionType().ultraWarm();
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.hasChunk(chunkX, chunkZ);
    }

    @Override
    public String getRerunSimulationKeyName() {
        return FabricParkourCalculator.rerunSimulationKeyName();
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
