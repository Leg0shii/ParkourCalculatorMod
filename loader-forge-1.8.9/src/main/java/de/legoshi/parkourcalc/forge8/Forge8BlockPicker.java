package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ports.BlockPicker;
import de.legoshi.parkourcalc.core.ports.PickedBlock;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Casts a ray from the camera and returns the looked-at block plus its real outline hitbox, so the
 *  Angle Solver can capture start / collision / land blocks (full cubes, slabs, heads) from the world. */
public final class Forge8BlockPicker implements BlockPicker {

    private static final double REACH = 64.0;

    @Override
    public PickedBlock pickLookedAtBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World world = mc.theWorld;
        if (view == null || world == null) return null;

        Vec3 eye = view.getPositionEyes(1.0F);
        Vec3 look = view.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * REACH, look.yCoord * REACH, look.zCoord * REACH);

        MovingObjectPosition hit = world.rayTraceBlocks(eye, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return null;

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        block.setBlockBoundsBasedOnState(world, pos); // reflect this block's actual shape before reading bounds
        AxisAlignedBB bb = block.getSelectedBoundingBox(world, pos);

        AABB box = bb == null
                ? new AABB(new Vec3dCore(pos.getX(), pos.getY(), pos.getZ()),
                           new Vec3dCore(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0))
                : new AABB(new Vec3dCore(bb.minX, bb.minY, bb.minZ), new Vec3dCore(bb.maxX, bb.maxY, bb.maxZ));
        return new PickedBlock(pos.getX(), pos.getY(), pos.getZ(), box);
    }
}
