package de.legoshi.parkourcalc.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.record.HumanRecorder;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HumanRecorderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private HumanRecorder recorder() {
        FileSystemSaveStore store = new FileSystemSaveStore(tmp.getRoot().toPath(), "1.2.3", "1.8.9", null);
        return new HumanRecorder(() -> store, () -> 0.5f);
    }

    @Test
    public void ignoresEventsWhileNotRecording() {
        HumanRecorder r = recorder();
        r.tickStart(1, 2, 3, 4f, 5f, true);
        r.mouse(0L, 3, -1, 4f, 5f);
        r.key(0L, 32, true);
        assertEquals(0, r.tickCount());
        assertEquals(0, r.mouseCount());
        assertEquals(0, r.keyCount());
        assertNull(r.stop());
    }

    @Test
    public void writesTicksMouseAndKeysAsJson() throws Exception {
        HumanRecorder r = recorder();
        assertTrue(r.start());
        assertFalse(r.start());
        r.tickStart(1.5, 64.0, -2.25, 90f, 10f, true);
        r.mouse(0L, 12, -3, 90f, 10f);
        r.mouse(0L, 0, 0, 90f, 10f);
        r.key(0L, 32, true);
        r.tickEnd(true, false, false, false, true, false, true, true);
        r.tickStart(1.7, 64.42, -2.25, 91.5f, 10f, false);
        Path file = r.stop();
        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals(tmp.getRoot().toPath().resolve(HumanRecorder.DIRECTORY), file.getParent());
        JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(HumanRecorder.FORMAT, root.get("format").getAsString());
        assertEquals("1.2.3", root.get("modVersion").getAsString());
        assertEquals("1.8.9", root.get("mcVersion").getAsString());
        assertEquals(0.5, root.get("sensitivity").getAsDouble(), 1e-9);
        JsonArray ticks = root.getAsJsonArray("ticks");
        assertEquals(2, ticks.size());
        JsonObject t0 = ticks.get(0).getAsJsonObject();
        assertEquals(0, t0.get("t").getAsInt());
        assertEquals(1.5, t0.get("x").getAsDouble(), 0.0);
        assertEquals(90.0, t0.get("yaw").getAsDouble(), 0.0);
        assertTrue(t0.get("ground").getAsBoolean());
        assertEquals("WJP", t0.get("keys").getAsString());
        assertTrue(t0.get("sprinting").getAsBoolean());
        assertTrue(t0.get("endUs").getAsLong() >= t0.get("us").getAsLong());
        JsonObject t1 = ticks.get(1).getAsJsonObject();
        assertEquals("", t1.get("keys").getAsString());
        assertEquals(-1, t1.get("endUs").getAsLong());
        JsonArray mouse = root.getAsJsonArray("mouse");
        assertEquals(1, mouse.size());
        assertEquals(12.0, mouse.get(0).getAsJsonObject().get("dx").getAsDouble(), 0.0);
        assertEquals(-3.0, mouse.get(0).getAsJsonObject().get("dy").getAsDouble(), 0.0);
        JsonArray keys = root.getAsJsonArray("keys");
        assertEquals(1, keys.size());
        assertEquals(32, keys.get(0).getAsJsonObject().get("code").getAsInt());
        assertTrue(keys.get(0).getAsJsonObject().get("down").getAsBoolean());
        assertEquals(file, r.lastFile());
        assertFalse(r.isRecording());
    }

    @Test
    public void secondRecordingStartsEmpty() {
        HumanRecorder r = recorder();
        r.start();
        r.tickStart(0, 0, 0, 0f, 0f, true);
        r.stop();
        r.start();
        assertEquals(0, r.tickCount());
        r.stop();
    }
}
