package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;

import java.util.List;

public final class UnloadedChunks {

    private UnloadedChunks() {
    }

    public static boolean touched(MinecraftAccess mc, List<TickState> path) {
        if (mc.isSinglePlayer()) return false;
        for (TickState s : path) {
            int[] r = ChunkRange.around(s.position.x, s.position.z);
            for (int cx = r[0]; cx <= r[2]; cx++) {
                for (int cz = r[1]; cz <= r[3]; cz++) {
                    if (!mc.isChunkLoaded(cx, cz)) return true;
                }
            }
        }
        return false;
    }
}
