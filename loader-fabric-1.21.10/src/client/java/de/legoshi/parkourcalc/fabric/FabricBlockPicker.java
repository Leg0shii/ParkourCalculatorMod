package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public final class FabricBlockPicker implements BlockPicker {

    private static final double REACH = 64.0;

    @Override
    public PickedBlock pickLookedAtBlock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return null;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d eye = camera.getPos();
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d end = eye.add(look.multiply(REACH));

        BlockHitResult hit = world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;

        BlockState state = world.getBlockState(pos);

        List<AABB> boxes = new ArrayList<AABB>();
        for (Box sub : state.getCollisionShape(world, pos).getBoundingBoxes()) boxes.add(toCoreBox(sub, pos));

        VoxelShape outline = state.getOutlineShape(world, pos);
        AABB hull = !outline.isEmpty() ? toCoreBox(outline.getBoundingBox(), pos)
                : !boxes.isEmpty() ? hullOf(boxes)
                : new AABB(new Vec3dCore(pos.getX(), pos.getY(), pos.getZ()),
                           new Vec3dCore(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
        return new PickedBlock(pos.getX(), pos.getY(), pos.getZ(), hull, boxes);
    }

    private static AABB toCoreBox(Box b, BlockPos pos) {
        return new AABB(
                new Vec3dCore(pos.getX() + b.minX, pos.getY() + b.minY, pos.getZ() + b.minZ),
                new Vec3dCore(pos.getX() + b.maxX, pos.getY() + b.maxY, pos.getZ() + b.maxZ));
    }

    private static AABB hullOf(List<AABB> boxes) {
        AABB h = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) {
            AABB b = boxes.get(i);
            h = new AABB(
                    new Vec3dCore(Math.min(h.min.x, b.min.x), Math.min(h.min.y, b.min.y), Math.min(h.min.z, b.min.z)),
                    new Vec3dCore(Math.max(h.max.x, b.max.x), Math.max(h.max.y, b.max.y), Math.max(h.max.z, b.max.z)));
        }
        return h;
    }
}
