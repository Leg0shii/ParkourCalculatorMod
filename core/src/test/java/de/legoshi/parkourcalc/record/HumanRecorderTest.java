package de.legoshi.parkourcalc.record;

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
import java.util.ArrayList;
import java.util.List;

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

    private static List<JsonObject> rows(Path file) throws Exception {
        List<JsonObject> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) out.add(new JsonParser().parse(line).getAsJsonObject());
        }
        return out;
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
    public void streamsRowsInOrderAsJsonLines() throws Exception {
        HumanRecorder r = recorder();
        assertTrue(r.start());
        assertFalse(r.start());
        r.tickStart(1.5, 64.0, -2.25, 90f, 10f, true);
        r.mouse(0L, 12, -3, 90f, 10f);
        r.mouse(0L, 0, 0, 90f, 10f);
        r.key(77L, 32, true);
        r.button(0L, 1, true);
        r.tickEnd(true, false, false, false, true, false, true, true);
        r.tickStart(1.7, 64.42, -2.25, 91.5f, 10f, false);
        Path file = r.stop();
        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals(tmp.getRoot().toPath().resolve(HumanRecorder.DIRECTORY), file.getParent());
        assertTrue(file.getFileName().toString().endsWith(HumanRecorder.EXTENSION));
        List<JsonObject> rows = rows(file);
        assertEquals(8, rows.size());
        JsonObject header = rows.get(0);
        assertEquals("header", header.get("e").getAsString());
        assertEquals(HumanRecorder.FORMAT, header.get("format").getAsString());
        assertEquals("1.2.3", header.get("modVersion").getAsString());
        assertEquals("1.8.9", header.get("mcVersion").getAsString());
        assertEquals(0.5, header.get("sensitivity").getAsDouble(), 1e-9);
        JsonObject t0 = rows.get(1);
        assertEquals("tick", t0.get("e").getAsString());
        assertEquals(0, t0.get("t").getAsInt());
        assertEquals(1.5, t0.get("x").getAsDouble(), 0.0);
        assertEquals(90.0, t0.get("yaw").getAsDouble(), 0.0);
        assertTrue(t0.get("ground").getAsBoolean());
        JsonObject m = rows.get(2);
        assertEquals("mouse", m.get("e").getAsString());
        assertEquals(12.0, m.get("dx").getAsDouble(), 0.0);
        assertEquals(-3.0, m.get("dy").getAsDouble(), 0.0);
        assertFalse(m.has("eventNs"));
        JsonObject k = rows.get(3);
        assertEquals("key", k.get("e").getAsString());
        assertEquals(32, k.get("code").getAsInt());
        assertEquals(77L, k.get("eventNs").getAsLong());
        assertTrue(k.get("down").getAsBoolean());
        JsonObject b = rows.get(4);
        assertEquals("button", b.get("e").getAsString());
        assertEquals(1, b.get("button").getAsInt());
        assertTrue(b.get("down").getAsBoolean());
        JsonObject te = rows.get(5);
        assertEquals("tickEnd", te.get("e").getAsString());
        assertEquals(0, te.get("t").getAsInt());
        assertEquals("WJP", te.get("keys").getAsString());
        assertTrue(te.get("sprinting").getAsBoolean());
        assertEquals(1, rows.get(6).get("t").getAsInt());
        JsonObject end = rows.get(7);
        assertEquals("end", end.get("e").getAsString());
        assertEquals(2, end.get("ticks").getAsInt());
        assertEquals(1, end.get("mouse").getAsInt());
        assertEquals(2, end.get("keys").getAsInt());
        assertEquals(file, r.lastFile());
        assertFalse(r.isRecording());
    }

    @Test
    public void frameRowsOnlyWhenTheViewChanged() throws Exception {
        HumanRecorder r = recorder();
        r.frame(1f, 2f);
        assertTrue(r.start());
        r.frame(1f, 2f);
        r.frame(1f, 2f);
        r.frame(1.5f, 2f);
        r.frame(1.5f, 2.25f);
        Path file = r.stop();
        List<JsonObject> rows = rows(file);
        assertEquals(5, rows.size());
        assertEquals(2, rows.get(0).get("version").getAsInt());
        for (int i = 1; i <= 3; i++) assertEquals("frame", rows.get(i).get("e").getAsString());
        assertEquals(1.0, rows.get(1).get("yaw").getAsDouble(), 0.0);
        assertEquals(1.5, rows.get(2).get("yaw").getAsDouble(), 0.0);
        assertEquals(2.25, rows.get(3).get("pitch").getAsDouble(), 0.0);
        assertEquals(3, rows.get(4).get("frames").getAsInt());
        assertEquals(3, r.frameCount());
    }

    @Test
    public void secondRecordingStartsFresh() throws Exception {
        HumanRecorder r = recorder();
        r.start();
        r.tickStart(0, 0, 0, 0f, 0f, true);
        Path first = r.stop();
        r.start();
        assertEquals(0, r.tickCount());
        r.tickEnd(true, false, false, false, false, false, false, false);
        Path second = r.stop();
        assertNotNull(second);
        assertEquals(2, rows(second).size());
        assertNotNull(first);
    }
}
