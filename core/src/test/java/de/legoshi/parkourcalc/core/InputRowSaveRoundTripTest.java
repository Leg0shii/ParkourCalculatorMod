package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InputRowSaveRoundTripTest {

    private static FileSystemSaveStore store(Path dir) {
        return new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
    }

    private static InputData saveAndReload(FileSystemSaveStore store, InputData in) {
        Result<String> saved = SaveIO.save(store, "run", in, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, null, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        InputData out = new InputData();
        SaveIO.applyRowsTo(loaded.value, out);
        return out;
    }

    @Test
    public void pitchAndMouseButtonsRoundTrip() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-pitch-mouse"));

        InputData in = new InputData();
        InputRow r0 = new InputRow();
        r0.setKeyActive(InputRow.Key.W, true);
        r0.setKeyActive(InputRow.Key.LEFT_CLICK, true);
        r0.setYaw(45.5f);
        r0.setPitch(-12.25f);
        r0.setPitchLocked(true);
        in.getRows().add(r0);

        InputRow r1 = new InputRow();
        r1.setKeyActive(InputRow.Key.RIGHT_CLICK, true);
        r1.setKeyActive(InputRow.Key.SPRINT, true);
        r1.setPitch(90f);
        in.getRows().add(r1);

        InputData out = saveAndReload(store, in);
        assertEquals(2, out.size());

        InputRow o0 = out.get(0);
        assertTrue(o0.isKeyActive(InputRow.Key.W));
        assertTrue(o0.isKeyActive(InputRow.Key.LEFT_CLICK));
        assertFalse(o0.isKeyActive(InputRow.Key.RIGHT_CLICK));
        assertEquals(Float.valueOf(45.5f), o0.getYaw());
        assertEquals(Float.valueOf(-12.25f), o0.getPitch());
        assertTrue("pitch lock must survive a round-trip", o0.isPitchLocked());

        InputRow o1 = out.get(1);
        assertTrue(o1.isKeyActive(InputRow.Key.RIGHT_CLICK));
        assertTrue(o1.isKeyActive(InputRow.Key.SPRINT));
        assertFalse(o1.isKeyActive(InputRow.Key.LEFT_CLICK));
        assertEquals(Float.valueOf(90f), o1.getPitch());
        assertFalse("an untouched pitch lock stays false", o1.isPitchLocked());
    }

    @Test
    public void absentPitchRoundTripsAsNull() throws Exception {
        FileSystemSaveStore store = store(Files.createTempDirectory("pkc-rt-nopitch"));

        InputData in = new InputData();
        InputRow r0 = new InputRow();
        r0.setYaw(10f);
        in.getRows().add(r0);

        InputRow o0 = saveAndReload(store, in).get(0);
        assertEquals(Float.valueOf(10f), o0.getYaw());
        assertNull("an unset pitch must stay null (inherit) across a round-trip", o0.getPitch());
    }

    @Test
    public void oldFormatRowWithoutPitchOrMouseLoadsWithDefaults() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0.0, 0.0, 0.0], \"vel\": [0.0, 0.0, 0.0], \"yaw\": 0.0 },\n" +
                "  \"rows\": [\n" +
                "    { \"keys\": [\"W\", \"SPRINT\"], \"yaw\": 12.0, \"yawLocked\": false, \"speedAmplifier\": 0, \"jumpBoostAmplifier\": 0 }\n" +
                "  ]\n" +
                "}";

        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        InputData out = new InputData();
        SaveIO.applyRowsTo(file, out);

        assertEquals(1, out.size());
        InputRow o0 = out.get(0);
        assertTrue(o0.isKeyActive(InputRow.Key.W));
        assertTrue(o0.isKeyActive(InputRow.Key.SPRINT));
        assertEquals(Float.valueOf(12.0f), o0.getYaw());
        assertFalse(o0.isKeyActive(InputRow.Key.LEFT_CLICK));
        assertFalse(o0.isKeyActive(InputRow.Key.RIGHT_CLICK));
        assertNull("missing pitch field must load as null (inherit)", o0.getPitch());
    }

    @Test
    public void unknownKeyNamesInSaveAreIgnored() {
        String json = "{\n" +
                "  \"version\": 1,\n" +
                "  \"start\": { \"pos\": [0.0, 0.0, 0.0], \"vel\": [0.0, 0.0, 0.0], \"yaw\": 0.0 },\n" +
                "  \"rows\": [ { \"keys\": [\"W\", \"MIDDLE_CLICK\"], \"yaw\": 0.0 } ]\n" +
                "}";
        SaveFile file = SaveIO.parseSafe(json);
        assertNotNull(file);
        InputData out = new InputData();
        SaveIO.applyRowsTo(file, out);
        assertEquals(1, out.size());
        assertTrue(out.get(0).isKeyActive(InputRow.Key.W));
    }
}
