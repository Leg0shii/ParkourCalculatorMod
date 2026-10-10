package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class Forge8BlockPicker implements BlockPicker {

    @Override
    public PickedBlock pickBlock(int x, int y, int z) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return null;
        BlockPos pos = new BlockPos(x, y, z);

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        block.setBlockBoundsBasedOnState(world, pos);

        List<AxisAlignedBB> mcBoxes = new ArrayList<AxisAlignedBB>();
        AxisAlignedBB mask = new AxisAlignedBB(
                pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0,
                pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
        block.addCollisionBoxesToList(world, pos, state, mask, mcBoxes, null);
        List<AABB> boxes = new ArrayList<AABB>(mcBoxes.size());
        for (AxisAlignedBB sub : mcBoxes) boxes.add(toCoreBox(sub));

        AxisAlignedBB bb = block.getSelectedBoundingBox(world, pos);
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
