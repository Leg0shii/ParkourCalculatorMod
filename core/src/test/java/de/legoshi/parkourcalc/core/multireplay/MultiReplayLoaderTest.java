package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.FakeMinecraftAccess;
import de.legoshi.parkourcalc.core.FakeSimulator;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiReplayLoaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final FakeSimulator simulator = new FakeSimulator();
    private final FakeMinecraftAccess mc = new FakeMinecraftAccess();
    private final SimulationRunner mainRunner = new SimulationRunner(simulator);
    private final AtomicInteger retriggers = new AtomicInteger();
    private final MultiReplayLoader loader = new MultiReplayLoader(simulator, mainRunner, mc, retriggers::incrementAndGet);

    @Test
    public void loadsEveryJsonSortedAndRestoresMainStart() throws IOException {
        Path dir = tmp.newFolder("replays").toPath();
        write(dir.resolve("b_second.json"), save(5, 10, 64, 10));
        write(dir.resolve("a_first.json"), save(3, 20, 65, 20));
        write(dir.resolve("bad.json"), "{ not json");
        write(dir.resolve("notes.txt"), "ignored");

        mainRunner.setStartPosition(new Vec3dCore(1, 2, 3));
        mainRunner.setStartYaw(33f);
        mainRunner.setStartPitch(12f);

        MultiReplayLoader.Outcome out = loader.load(dir);

        assertEquals(2, out.tracks.size());
        assertEquals("a_first", out.tracks.get(0).name);
        assertEquals(4, out.tracks.get(0).size());
        assertEquals("b_second", out.tracks.get(1).name);
        assertEquals(6, out.tracks.get(1).size());
        assertTrue(out.tracks.get(0).argb != out.tracks.get(1).argb);
        assertEquals(1, out.errors.size());
        assertTrue(out.errors.get(0).startsWith("bad:"));

        assertEquals(new Vec3dCore(1, 2, 3), mainRunner.getStartPosition());
        assertEquals(33f, mainRunner.getStartYaw(), 0f);
        assertEquals(12f, mainRunner.getStartPitch(), 0f);
        assertEquals(1, retriggers.get());
    }

    @Test
    public void missingFolderReportsErrorWithoutTouchingMainSimulation() {
        MultiReplayLoader.Outcome out = loader.load(tmp.getRoot().toPath().resolve("nope"));
        assertTrue(out.tracks.isEmpty());
        assertEquals(1, out.errors.size());
        assertEquals(0, retriggers.get());
    }

    @Test
    public void emptyFolderReportsNoFiles() throws IOException {
        Path dir = tmp.newFolder("empty").toPath();
        MultiReplayLoader.Outcome out = loader.load(dir);
        assertTrue(out.tracks.isEmpty());
        assertEquals(1, out.errors.size());
        assertEquals(0, retriggers.get());
    }

    @Test
    public void notReadyWorldRefuses() throws IOException {
        Path dir = tmp.newFolder("replays").toPath();
        write(dir.resolve("a.json"), save(2, 0, 64, 0));
        mc.ready = false;
        MultiReplayLoader.Outcome out = loader.load(dir);
        assertTrue(out.tracks.isEmpty());
        assertEquals(1, out.errors.size());
        assertEquals(0, retriggers.get());
    }

    private static void write(Path file, String contents) throws IOException {
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String save(int rows, double x, double y, double z) {
        SaveFile f = new SaveFile();
        f.version = SaveFile.FORMAT_VERSION;
        f.start = new SaveFile.Start();
        f.start.pos = new double[] { x, y, z };
        f.start.vel = new double[] { 0, 0, 0 };
        for (int i = 0; i < rows; i++) {
            SaveFile.Row r = new SaveFile.Row();
            r.keys = Arrays.asList("W", "SPRINT");
            f.rows.add(r);
        }
        return SaveIO.saveJson(f);
    }
}
