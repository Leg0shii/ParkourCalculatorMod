package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.UnloadedChunkWatch;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnloadedChunkWatchTest {

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
    public void armsOnUnloadedChunkAndFiresOnceLoaded() {
        ChunkAccess mc = new ChunkAccess();
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5, 100.5, 60.0));
        assertTrue(watch.isArmed());
        assertFalse(watch.pendingChunksLoaded());
        mc.load(6, 6);
        assertFalse(watch.pendingChunksLoaded());
        mc.load(6, 3);
        assertTrue(watch.pendingChunksLoaded());
        assertFalse(watch.isArmed());
        assertFalse(watch.pendingChunksLoaded());
    }

    @Test
    public void marginCrossingChunkBorderCountsBothChunks() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(96.3, 100.5));
        assertTrue(watch.isArmed());
        mc.load(5, 6);
        assertTrue(watch.pendingChunksLoaded());
    }

    @Test
    public void loadedPathNeverArms() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        assertFalse(watch.isArmed());
    }

    @Test
    public void singlePlayerNeverArms() {
        ChunkAccess mc = new ChunkAccess();
        mc.singlePlayer = true;
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        assertFalse(watch.isArmed());
    }

    @Test
    public void clearDropsPendingChunks() {
        ChunkAccess mc = new ChunkAccess();
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        watch.clear();
        assertFalse(watch.isArmed());
        mc.load(6, 6);
        assertFalse(watch.pendingChunksLoaded());
    }
}
