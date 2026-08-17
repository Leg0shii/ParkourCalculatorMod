package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DirectionNoticeSaveTest {

    private static final String NOTICE = AngleSolverEngine.directionFallbackNotice("min Z", "max X");

    @Test
    public void noticeSurvivesSaveRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("pkc-notice");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        AngleSolverState in = new AngleSolverState();
        SolveResult r = new SolveResult(true, 3, 3, 1, 10);
        r.setNotice(NOTICE);
        in.setResult(r);

        Result<String> saved = SaveIO.save(store, "run", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, in, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);

        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        assertNotNull(out.getResult());
        assertEquals(NOTICE, out.getResult().getNotice());
    }

    @Test
    public void legacyResultLoadsWithoutNotice() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{\"result\":{"
                + "\"success\":true,\"met\":1,\"total\":1,\"startTick\":1,\"landingTick\":5}}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);
        assertNotNull(state.getResult());
        assertNull(state.getResult().getNotice());
    }
}
