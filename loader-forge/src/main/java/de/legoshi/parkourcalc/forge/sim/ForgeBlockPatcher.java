package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import de.legoshi.parkourcalc.forge.McClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.server.integrated.IntegratedServer;
//? if >=1.12 {
import net.minecraft.util.math.BlockPos;
//?} else {
/*import net.minecraft.util.BlockPos;
*///?}
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

public final class ForgeBlockPatcher implements BlockPatcher {

    //? if >=1.12 {
    private static final int SET_FLAGS = 2 | 16;
    //?} else {
    /*private static final int SET_FLAGS = 2;
    *///?}

    @Override
    public boolean isAvailable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (McClient.world() == null || mc.getCurrentServerData() != null) return false;
        IntegratedServer server = mc.getIntegratedServer();
        return server != null && !server.getPublic();
    }

    @Override
    public void apply(List<BlockPatch> patches) {
        Minecraft mc = Minecraft.getMinecraft();
        final WorldClient clientWorld = McClient.world();
        final IntegratedServer server = mc.getIntegratedServer();
        if (clientWorld == null || server == null || patches.isEmpty()) return;
        final List<Object[]> resolved = new ArrayList<Object[]>(patches.size());
        for (BlockPatch p : patches) {
            IBlockState state = BlockOverridePlacer.state(p.block);
            if (state == null) continue;
            resolved.add(new Object[] {new BlockPos(p.x, p.y, p.z), state});
        }
        if (resolved.isEmpty()) return;
        set(clientWorld, resolved);
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                //? if >=1.12 {
                WorldServer serverWorld = server.getWorld(clientWorld.provider.getDimension());
                //?} else {
                /*WorldServer serverWorld = server.worldServerForDimension(clientWorld.provider.getDimensionId());
                *///?}
                if (serverWorld != null) set(serverWorld, resolved);
            }
        });
    }

    private static void set(World world, List<Object[]> resolved) {
        for (Object[] r : resolved) {
            BlockPos pos = (BlockPos) r[0];
            IBlockState state = (IBlockState) r[1];
            //? if >=1.12 {
            if (world.getChunk(pos.getX() >> 4, pos.getZ() >> 4).isEmpty()) continue;
            //?} else {
            /*if (world.getChunkFromChunkCoords(pos.getX() >> 4, pos.getZ() >> 4).isEmpty()) continue;
            *///?}
            if (world.getBlockState(pos) == state) continue;
            if (world.getTileEntity(pos) != null) continue;
            world.setBlockState(pos, state, SET_FLAGS);
        }
    }
}
