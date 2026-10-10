package de.legoshi.parkourcalc.forge8;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

final class Forge8RayTrace {

    private static final int MAX_PASSES = 16;
    private static final double STEP_PAST_BLOCK = 1.0e-4;

    private Forge8RayTrace() {
    }

    static MovingObjectPosition trace(World world, Vec3 start, Vec3 end) {
        MovingObjectPosition hit = world.rayTraceBlocks(start, end);
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
            BlockPos pos = hit.getBlockPos();
            if (pos == null) return hit;
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (!(block instanceof BlockPane)) return hit;

            MovingObjectPosition paneHit = interceptCollisionBoxes(world, pos, state, block, start, end);
            if (paneHit != null) return paneHit;

            Vec3 past = exitOfBlock(pos, start, end);
            if (past == null) return null;
            BlockPos next = new BlockPos(past);
            if (next.equals(pos)) return null;
            IBlockState nextState = world.getBlockState(next);
            Block nextBlock = nextState.getBlock();
            hit = nextBlock.canCollideCheck(nextState, false) ? nextBlock.collisionRayTrace(world, next, start, end) : null;
            if (hit == null) hit = world.rayTraceBlocks(past, end);
        }
        return null;
    }

    private static MovingObjectPosition interceptCollisionBoxes(World world, BlockPos pos, IBlockState state, Block block,
                                                                Vec3 start, Vec3 end) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        AxisAlignedBB mask = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        block.addCollisionBoxesToList(world, pos, state, mask, boxes, null);
        MovingObjectPosition best = null;
        double bestDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            MovingObjectPosition mop = box.calculateIntercept(start, end);
            if (mop == null || mop.hitVec == null) continue;
            double dist = mop.hitVec.squareDistanceTo(start);
            if (dist < bestDist) {
                bestDist = dist;
                best = new MovingObjectPosition(mop.hitVec, mop.sideHit, pos);
            }
        }
        return best;
    }

    private static Vec3 exitOfBlock(BlockPos pos, Vec3 start, Vec3 end) {
        double dx = end.xCoord - start.xCoord;
        double dy = end.yCoord - start.yCoord;
        double dz = end.zCoord - start.zCoord;
        double tExit = Math.min(
                axisExit(start.xCoord, dx, pos.getX()),
                Math.min(axisExit(start.yCoord, dy, pos.getY()), axisExit(start.zCoord, dz, pos.getZ())));
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0) return null;
        double t = tExit + STEP_PAST_BLOCK / length;
        if (t >= 1.0) return null;
        return new Vec3(start.xCoord + dx * t, start.yCoord + dy * t, start.zCoord + dz * t);
    }

    private static double axisExit(double origin, double delta, int cell) {
        if (delta == 0.0) return Double.MAX_VALUE;
        double tMin = (cell - origin) / delta;
        double tMax = (cell + 1.0 - origin) / delta;
        return Math.max(tMin, tMax);
    }
}
