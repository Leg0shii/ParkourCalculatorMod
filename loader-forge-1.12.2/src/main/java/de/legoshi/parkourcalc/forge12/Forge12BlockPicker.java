package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class Forge12BlockPicker implements BlockPicker {

    private static final double REACH = 64.0;

    @Override
    public PickedBlock pickLookedAtBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World world = mc.world;
        if (view == null || world == null) return null;

        Vec3d eye = view.getPositionEyes(1.0F);
        Vec3d look = view.getLook(1.0F);
        Vec3d end = new Vec3d(eye.x + look.x * REACH, eye.y + look.y * REACH, eye.z + look.z * REACH);

        RayTraceResult hit = world.rayTraceBlocks(eye, end);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        List<AxisAlignedBB> mcBoxes = new ArrayList<AxisAlignedBB>();
        AxisAlignedBB mask = new AxisAlignedBB(
                pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0,
                pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
        block.addCollisionBoxToList(state, world, pos, mask, mcBoxes, null, false);
        List<AABB> boxes = new ArrayList<AABB>(mcBoxes.size());
        for (AxisAlignedBB sub : mcBoxes) boxes.add(toCoreBox(sub));

        AxisAlignedBB bb = block.getSelectedBoundingBox(state, world, pos);
        AABB hull = bb != null ? toCoreBox(bb)
                : !boxes.isEmpty() ? hullOf(boxes)
                : new AABB(new Vec3dCore(pos.getX(), pos.getY(), pos.getZ()),
                           new Vec3dCore(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
        return new PickedBlock(pos.getX(), pos.getY(), pos.getZ(), hull, boxes);
    }

    private static AABB toCoreBox(AxisAlignedBB bb) {
        return new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ));
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
