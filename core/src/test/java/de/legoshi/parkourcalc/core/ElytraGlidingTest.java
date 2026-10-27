package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ElytraGlidingTest {

    private static final class GlidingFakeSimulator extends MediumWorldFakeSimulator {
        final List<Boolean> equipCalls = new ArrayList<>();
        int glideFrom = Integer.MAX_VALUE;
        int ticks;

        GlidingFakeSimulator() {
            super(new World());
        }

        @Override public boolean supportsElytra() { return true; }

        @Override protected void applyElytra(FakeEntity e, boolean equipped) { equipCalls.add(equipped); }

        @Override protected void resetEntity(FakeEntity e, StartResumeState resume) {
            super.resetEntity(e, resume);
            ticks = 0;
        }

        @Override protected void tickEntity(FakeEntity e) {
            super.tickEntity(e);
            ticks++;
        }

        @Override protected boolean isGliding(FakeEntity e) { return isElytraEquipped() && ticks >= glideFrom; }
    }

    private static InputData rows(int n) {
        InputData d = new InputData();
        for (int i = 0; i < n; i++) d.getRows().add(new InputRow());
        return d;
    }

    @Test
    public void runnerPassesTheElytraSettingAndGlidingTicksThrough() {
        GlidingFakeSimulator sim = new GlidingFakeSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        assertTrue(runner.supportsElytra());
        assertFalse(runner.isElytraEquipped());

        sim.glideFrom = 2;
        List<TickState> path = runner.simulate(rows(4));
        for (TickState s : path) assertFalse(s.gliding);

        runner.setElytraEquipped(true);
        assertTrue(runner.isElytraEquipped());
        path = runner.simulate(rows(4));
        assertFalse(path.get(0).gliding);
        assertFalse(path.get(1).gliding);
        assertTrue(path.get(2).gliding);
        assertTrue(path.get(3).gliding);
        assertTrue(path.get(4).gliding);
        assertEquals(Boolean.TRUE, sim.equipCalls.get(sim.equipCalls.size() - 1));
    }

    @Test
    public void glidingPicksItsOwnColourAndTheShortHitbox() {
        Settings settings = new Settings();
        TickState air = new TickState(Vec3dCore.ZERO, false, false, false, 0f, null, Vec3dCore.ZERO, false, Double.NaN,
                false, Float.NaN, Float.NaN, null, Double.NaN, 0, null, false);
        TickState glide = new TickState(Vec3dCore.ZERO, false, false, false, 0f, null, Vec3dCore.ZERO, false, Double.NaN,
                false, Float.NaN, Float.NaN, null, Double.NaN, 0, null, true);
        assertEquals(BoxStyle.toArgb(settings.tickAir), BoxStyle.tickFaceArgb(settings, air, false));
        assertEquals(BoxStyle.toArgb(settings.tickGliding), BoxStyle.tickFaceArgb(settings, glide, false));
        assertNotEquals(BoxStyle.tickFaceArgb(settings, air, false), BoxStyle.tickFaceArgb(settings, glide, false));
        assertEquals(BoxStyle.HITBOX_HEIGHT_GLIDING, BoxStyle.hitboxAabbAt(Vec3dCore.ZERO, false, true).max.y, 0.0);
        assertEquals(BoxStyle.HITBOX_HEIGHT_STANDING, BoxStyle.hitboxAabbAt(Vec3dCore.ZERO, false, false).max.y, 0.0);
    }

    @Test
    public void elytraSettingAndGlidingResumeSurviveTheSaveRoundTrip() throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(Files.createTempDirectory("pkc-elytra-rt"), "test", "26.3", () -> null);
        StartResumeState resume = new StartResumeState();
        resume.onGround = false;
        resume.gliding = true;
        Result<String> saved = SaveIO.save(store, "glide", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, true, resume, null, null, false);
        assertTrue(saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "glide");
        assertTrue(loaded.error, loaded.ok);
        assertTrue(SaveIO.elytraOf(loaded.value.start));
        StartResumeState back = SaveIO.resumeOf(loaded.value.start);
        assertTrue(back.gliding);
        assertTrue(back.sameAs(resume));

        Result<String> plain = SaveIO.save(store, "plain", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, null, null, false);
        assertTrue(plain.error, plain.ok);
        SaveFile plainFile = SaveIO.load(store, "plain").value;
        assertNull(plainFile.start.elytra);
        assertFalse(SaveIO.elytraOf(plainFile.start));
        assertFalse(SaveIO.saveJson(plainFile).contains("elytra"));
    }

    @Test
    public void oldFilesWithoutTheFieldLoadWithoutAnElytra() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"start\":{\"pos\":[0,0,0],\"vel\":[0,0,0],\"yaw\":0,"
                + "\"resume\":{\"onGround\":true}},\"rows\":[]}");
        assertFalse(SaveIO.elytraOf(f.start));
        assertFalse(SaveIO.resumeOf(f.start).gliding);
        assertTrue(SaveIO.resumeOf(f.start).onGround);
    }

    @Test
    public void elytraChangesTheUndoSignature() {
        InputData data = new InputData();
        String off = SaveIO.undoSignature(data, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, false, null, null);
        String on = SaveIO.undoSignature(data, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, true, null, null);
        assertNotEquals(off, on);
        assertEquals(off, SaveIO.undoSignature(data, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, null));
        assertTrue(SaveIO.elytraOf(SaveIO.buildUndoSnapshot(data, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, true, null, null).start));
    }

    @Test
    public void replayRefusesToStartWithoutTheElytraTheTasNeeds() {
        GlidingFakeSimulator sim = new GlidingFakeSimulator();
        SimulationRunner runner = new SimulationRunner(sim);
        InputData data = rows(3);
        PlaybackController pc = new PlaybackController(data, runner, new Settings());
        FakePlaybackBridge bridge = new FakePlaybackBridge();
        pc.setBridge(bridge);
        List<String> warnings = new ArrayList<>();
        pc.setWarningSink(warnings::add);

        runner.setElytraEquipped(true);
        bridge.elytraWorn = false;
        pc.start();
        assertFalse(pc.isRunning());
        assertEquals(1, warnings.size());
        assertEquals(PlaybackController.ELYTRA_MISSING, warnings.get(0));
        assertEquals(0, bridge.teleportCalls);

        bridge.elytraWorn = true;
        pc.start();
        assertTrue(pc.isRunning());
        assertEquals(1, warnings.size());
        assertEquals(Boolean.TRUE, bridge.preparedElytra);
        pc.stop();

        runner.setElytraEquipped(false);
        pc.start();
        assertTrue(pc.isRunning());
        assertEquals(2, warnings.size());
        assertEquals(PlaybackController.ELYTRA_UNEXPECTED, warnings.get(1));
        assertEquals(Boolean.FALSE, bridge.preparedElytra);
    }
}
