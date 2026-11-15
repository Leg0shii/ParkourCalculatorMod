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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgeBlockPatcher implements BlockPatcher {

    //? if >=1.12 {
    private static final int SET_FLAGS = 2 | 16;
    //?} else {
    /*private static final int SET_FLAGS = 2;
    *///?}
    private static final Set<BlockPos> OWN_TILE_ENTITIES = new HashSet<BlockPos>();

    @Override
    public boolean isAvailable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (McClient.world() == null || mc.getCurrentServerData() != null) return false;
        IntegratedServer server = mc.getIntegratedServer();
        return server != null && !server.getPublic();
    }

    @Override
    public boolean apply(List<BlockPatch> patches) {
        Minecraft mc = Minecraft.getMinecraft();
        final WorldClient clientWorld = McClient.world();
        final IntegratedServer server = mc.getIntegratedServer();
        if (clientWorld == null || server == null || patches.isEmpty()) return true;
        final List<Object[]> resolved = new ArrayList<Object[]>(patches.size());
        for (BlockPatch p : patches) {
            IBlockState state = BlockOverridePlacer.state(p.block);
            if (state == null) continue;
            IBlockState replaces = p.replaces == null ? null : BlockOverridePlacer.state(p.replaces);
            resolved.add(new Object[] {new BlockPos(p.x, p.y, p.z), state, replaces});
        }
        if (resolved.isEmpty()) return true;
        boolean complete = set(clientWorld, resolved);
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
        return complete;
    }

    private static boolean set(World world, List<Object[]> resolved) {
        boolean complete = true;
        for (Object[] r : resolved) {
            BlockPos pos = (BlockPos) r[0];
            IBlockState state = (IBlockState) r[1];
            IBlockState replaces = (IBlockState) r[2];
            //? if >=1.12 {
            boolean loaded = !world.getChunk(pos.getX() >> 4, pos.getZ() >> 4).isEmpty();
            //?} else {
            /*boolean loaded = !world.getChunkFromChunkCoords(pos.getX() >> 4, pos.getZ() >> 4).isEmpty();
            *///?}
            if (!loaded) {
                complete = false;
                continue;
            }
            IBlockState current = world.getBlockState(pos);
            if (current == state) continue;
            boolean own = OWN_TILE_ENTITIES.contains(pos) || (replaces != null && current == replaces);
            if (world.getTileEntity(pos) != null && !own) continue;
            world.setBlockState(pos, state, SET_FLAGS);
            if (world.getTileEntity(pos) != null) {
                OWN_TILE_ENTITIES.add(pos);
            } else {
                OWN_TILE_ENTITIES.remove(pos);
            }
        }
        return complete;
    }
}
