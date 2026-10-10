package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.UnloadedChunks;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnloadedChunksTest {

    private static final class ChunkAccess extends FakeMinecraftAccess {
        final Set<List<Integer>> loaded = new HashSet<>();
        boolean singlePlayer;

        void load(int cx, int cz) {
            loaded.add(Arrays.asList(cx, cz));
        }

        @Override public boolean isSinglePlayer() { return singlePlayer; }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { return loaded.contains(Arrays.asList(chunkX, chunkZ)); }
    }

    private static List<TickState> pathAt(double... xz) {
        List<TickState> path = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            path.add(new TickState(new Vec3dCore(xz[i], 64.0, xz[i + 1]), true, false, false, 0f, new ArrayList<Vec3dCore>(), Vec3dCore.ZERO, false, Double.NaN));
        }
        return path;
    }

    @Test
    public void missingChunkAnywhereOnThePathCounts() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        assertTrue(UnloadedChunks.touched(mc, pathAt(100.5, 100.5, 100.5, 60.0)));
        mc.load(6, 3);
        assertFalse(UnloadedChunks.touched(mc, pathAt(100.5, 100.5, 100.5, 60.0)));
    }

    @Test
    public void oneBlockMarginReachesTheNeighbourChunk() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        assertTrue(UnloadedChunks.touched(mc, pathAt(96.3, 100.5)));
        mc.load(5, 6);
        assertFalse(UnloadedChunks.touched(mc, pathAt(96.3, 100.5)));
    }

    @Test
    public void singlePlayerNeverCounts() {
        ChunkAccess mc = new ChunkAccess();
        mc.singlePlayer = true;
        assertFalse(UnloadedChunks.touched(mc, pathAt(100.5, 100.5)));
    }
}
