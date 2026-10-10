package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UnloadedChunkWatch {

    private final MinecraftAccess mc;
    private final Set<Long> pending = new LinkedHashSet<>();

    public UnloadedChunkWatch(MinecraftAccess mc) {
        this.mc = mc;
    }

    public void arm(List<TickState> path) {
        pending.clear();
        if (mc.isSinglePlayer()) return;
        for (TickState s : path) {
            int[] r = ChunkRange.around(s.position.x, s.position.z);
            for (int cx = r[0]; cx <= r[2]; cx++) {
                for (int cz = r[1]; cz <= r[3]; cz++) {
                    if (!mc.isChunkLoaded(cx, cz)) pending.add(key(cx, cz));
                }
            }
        }
    }

    public boolean isArmed() {
        return !pending.isEmpty();
    }

    public boolean pendingChunksLoaded() {
        if (pending.isEmpty()) return false;
        for (long k : pending) {
            if (!mc.isChunkLoaded((int) (k >> 32), (int) k)) return false;
        }
        pending.clear();
        return true;
    }

    public void clear() {
        pending.clear();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
