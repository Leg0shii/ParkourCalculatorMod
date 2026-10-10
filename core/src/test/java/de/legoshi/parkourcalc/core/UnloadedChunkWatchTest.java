package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.UnloadedChunkWatch;
import de.legoshi.parkourcalc.core.sim.UnloadedChunkWatch.Rerun;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnloadedChunkWatchTest {

    private static final class ChunkAccess extends FakeMinecraftAccess {
        final Set<List<Integer>> loaded = new HashSet<>();
        boolean singlePlayer;
        Object world = new Object();

        void load(int cx, int cz) {
            loaded.add(Arrays.asList(cx, cz));
        }

        @Override public boolean isSinglePlayer() { return singlePlayer; }
        @Override public boolean isChunkLoaded(int chunkX, int chunkZ) { return loaded.contains(Arrays.asList(chunkX, chunkZ)); }
        @Override public Object getWorldToken() { return world; }
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
        assertEquals(Rerun.NONE, watch.poll());
        mc.load(6, 6);
        assertEquals(Rerun.NONE, watch.poll());
        mc.load(6, 3);
        assertEquals(Rerun.CHUNKS_LOADED, watch.poll());
        assertFalse(watch.isArmed());
        assertEquals(Rerun.NONE, watch.poll());
    }

    @Test
    public void marginCrossingChunkBorderCountsBothChunks() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(96.3, 100.5));
        assertTrue(watch.isArmed());
        mc.load(5, 6);
        assertEquals(Rerun.CHUNKS_LOADED, watch.poll());
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
        assertEquals(Rerun.NONE, watch.poll());
    }

    @Test
    public void worldSwitchRerunsOnceEvenWithChunksPresent() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        assertEquals(Rerun.NONE, watch.poll());
        mc.world = new Object();
        assertEquals(Rerun.WORLD_CHANGED, watch.poll());
        assertEquals(Rerun.NONE, watch.poll());
        watch.arm(pathAt(100.5, 100.5));
        assertEquals(Rerun.NONE, watch.poll());
    }

    @Test
    public void worldSwitchWaitsUntilReady() {
        ChunkAccess mc = new ChunkAccess();
        mc.load(6, 6);
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        mc.world = new Object();
        mc.ready = false;
        assertEquals(Rerun.NONE, watch.poll());
        mc.ready = true;
        assertEquals(Rerun.WORLD_CHANGED, watch.poll());
    }

    @Test
    public void worldSwitchBeatsPendingChunksAndFiresInSinglePlayerToo() {
        ChunkAccess mc = new ChunkAccess();
        UnloadedChunkWatch watch = new UnloadedChunkWatch(mc);
        watch.arm(pathAt(100.5, 100.5));
        mc.world = new Object();
        assertEquals(Rerun.WORLD_CHANGED, watch.poll());
        assertFalse(watch.isArmed());
        mc.singlePlayer = true;
        watch.arm(pathAt(100.5, 100.5));
        mc.world = new Object();
        assertEquals(Rerun.WORLD_CHANGED, watch.poll());
    }
}
