package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class FabricBlockPicker implements BlockPicker {

    private static final double REACH = 64.0;

    @Override
    public PickedBlock pickLookedAtBlock() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) return null;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 end = eye.add(look.scale(REACH));

        BlockHitResult hit = world.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;

        BlockState state = world.getBlockState(pos);

        List<AABB> boxes = new ArrayList<AABB>();
        for (net.minecraft.world.phys.AABB sub : state.getCollisionShape(world, pos).toAabbs()) boxes.add(toCoreBox(sub, pos));

        VoxelShape outline = state.getShape(world, pos);
        AABB hull = !outline.isEmpty() ? toCoreBox(outline.bounds(), pos)
                : !boxes.isEmpty() ? hullOf(boxes)
                : new AABB(new Vec3dCore(pos.getX(), pos.getY(), pos.getZ()),
                           new Vec3dCore(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
        return new PickedBlock(pos.getX(), pos.getY(), pos.getZ(), hull, boxes);
    }

    private static AABB toCoreBox(net.minecraft.world.phys.AABB b, BlockPos pos) {
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
