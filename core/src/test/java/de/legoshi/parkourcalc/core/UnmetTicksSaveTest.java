package de.legoshi.parkourcalc.core;

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
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UnmetTicksSaveTest {

    @Test
    public void unmetTicksSurviveSaveRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("pkc-unmet");
        FileSystemSaveStore store = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        AngleSolverState in = new AngleSolverState();
        SolveResult r = new SolveResult(false, 2, 4, 1, 10);
        r.addUnmetTick(7);
        r.addUnmetTick(3);
        in.setResult(r);

        Result<String> saved = SaveIO.save(store, "run", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, in, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);

        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        assertNotNull(out.getResult());
        assertEquals("the world overlay's unmet ticks must survive a reload",
                new HashSet<Integer>(Arrays.asList(3, 7)),
                new HashSet<Integer>(out.getResult().getUnmetTicks()));
    }

    @Test
    public void legacyResultLoadsWithoutUnmetTicks() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{\"result\":{"
                + "\"success\":false,\"met\":1,\"total\":2,\"startTick\":1,\"landingTick\":5}}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);
        assertNotNull(state.getResult());
        assertTrue("a file written before the field existed must load with no plates",
                state.getResult().getUnmetTicks().isEmpty());
    }
}
