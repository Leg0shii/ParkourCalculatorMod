package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.ports.BlockPatcher;
import de.legoshi.parkourcalc.core.sim.BlockPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FabricBlockPatcher implements BlockPatcher {

    private static final int SET_FLAGS = 2 | 16;
    private static final Set<BlockPos> OWN_BLOCK_ENTITIES = new HashSet<>();

    @Override
    public boolean isAvailable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCurrentServer() != null) return false;
        IntegratedServer server = mc.getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    @Override
    public boolean apply(List<BlockPatch> patches) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel clientLevel = mc.level;
        IntegratedServer server = mc.getSingleplayerServer();
        if (clientLevel == null || server == null || patches.isEmpty()) return true;
        List<Object[]> resolved = new ArrayList<>(patches.size());
        for (BlockPatch p : patches) {
            BlockState state = BlockOverridePlacer.state(p.block);
            if (state == null) continue;
            BlockState replaces = p.replaces == null ? null : BlockOverridePlacer.state(p.replaces);
            resolved.add(new Object[] {new BlockPos(p.x, p.y, p.z), state, replaces});
        }
        if (resolved.isEmpty()) return true;
        boolean complete = set(clientLevel, resolved);
        server.execute(() -> {
            ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
            if (serverLevel != null) set(serverLevel, resolved);
        });
        return complete;
    }

    private static boolean set(Level level, List<Object[]> resolved) {
        boolean complete = true;
        for (Object[] r : resolved) {
            BlockPos pos = (BlockPos) r[0];
            BlockState state = (BlockState) r[1];
            BlockState replaces = (BlockState) r[2];
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                complete = false;
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (current == state) continue;
            boolean own = OWN_BLOCK_ENTITIES.contains(pos) || (replaces != null && current == replaces);
            if (level.getBlockEntity(pos) != null && !own) continue;
            level.setBlock(pos, state, SET_FLAGS);
            if (level.getBlockEntity(pos) != null) {
                OWN_BLOCK_ENTITIES.add(pos.immutable());
            } else {
                OWN_BLOCK_ENTITIES.remove(pos);
            }
        }
        return complete;
    }
}
