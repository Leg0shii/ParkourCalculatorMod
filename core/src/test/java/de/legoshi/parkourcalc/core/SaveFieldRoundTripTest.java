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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SaveFieldRoundTripTest {

    private static AngleSolverState saveAndReload(AngleSolverState in) throws Exception {
        FileSystemSaveStore store = new FileSystemSaveStore(Files.createTempDirectory("pkc-field-rt"), "test", "1.8.9", () -> null);
        Result<String> saved = SaveIO.save(store, "run", new InputData(), Vec3dCore.ZERO, Vec3dCore.ZERO,
                0f, PlaybackController.DEFAULT_PITCH, in, null, false);
        assertTrue("save should succeed: " + saved.error, saved.ok);
        Result<SaveFile> loaded = SaveIO.load(store, "run");
        assertTrue("load should succeed: " + loaded.error, loaded.ok);
        AngleSolverState out = new AngleSolverState();
        SaveIO.applyAngleSolverTo(loaded.value, out);
        return out;
    }

    private static AngleSolverState load(String json) {
        SaveFile f = SaveIO.parseSafe(json);
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);
        return state;
    }

    private static SaveFile.Start start(double[] vel) {
        SaveFile.Start s = new SaveFile.Start();
        s.pos = new double[]{0.0, 0.0, 0.0};
        s.vel = vel;
        s.yaw = 0.0f;
        return s;
    }

    @Test
    public void missingVelocityDefaultsToGroundRest() {
        assertEquals(Vec3dCore.GROUND_REST_VELOCITY, SaveIO.velOf(start(null)));
    }

    @Test
    public void legacyExplicitZeroVelocityLoadsAsGroundRest() {
        assertEquals(Vec3dCore.GROUND_REST_VELOCITY, SaveIO.velOf(start(new double[]{0.0, 0.0, 0.0})));
    }

    @Test
    public void editedVelocityIsPreserved() {
        Vec3dCore v = new Vec3dCore(0.1, -0.2, 0.3);
        assertEquals(v, SaveIO.velOf(start(new double[]{0.1, -0.2, 0.3})));
    }

    @Test
    public void noticeSurvivesSaveRoundTrip() throws Exception {
        AngleSolverState in = new AngleSolverState();
        SolveResult r = new SolveResult(true, 3, 3, 1, 10);
        r.setNotice(AngleSolverEngine.DF_DIRECTION_NOTICE);
        r.setNoticeLabel(AngleSolverEngine.OVERCONSTRAINED_LABEL);
        in.setResult(r);

        AngleSolverState out = saveAndReload(in);
        assertNotNull(out.getResult());
        assertEquals(AngleSolverEngine.DF_DIRECTION_NOTICE, out.getResult().getNotice());
        assertEquals(AngleSolverEngine.OVERCONSTRAINED_LABEL, out.getResult().getNoticeLabel());
    }

    @Test
    public void legacyResultLoadsWithoutNotice() {
        AngleSolverState state = load("{\"version\":1,\"angleSolver\":{\"result\":{"
                + "\"success\":true,\"met\":1,\"total\":1,\"startTick\":1,\"landingTick\":5}}}");
        assertNotNull(state.getResult());
        assertNull(state.getResult().getNotice());
        assertNull(state.getResult().getNoticeLabel());
    }

    @Test
    public void unmetTicksSurviveSaveRoundTrip() throws Exception {
        AngleSolverState in = new AngleSolverState();
        SolveResult r = new SolveResult(false, 2, 4, 1, 10);
        r.addUnmetTick(7);
        r.addUnmetTick(3);
        in.setResult(r);

        AngleSolverState out = saveAndReload(in);
        assertNotNull(out.getResult());
        assertEquals("the world overlay's unmet ticks must survive a reload",
                new HashSet<Integer>(Arrays.asList(3, 7)),
                new HashSet<Integer>(out.getResult().getUnmetTicks()));
    }

    @Test
    public void legacyResultLoadsWithoutUnmetTicks() {
        AngleSolverState state = load("{\"version\":1,\"angleSolver\":{\"result\":{"
                + "\"success\":false,\"met\":1,\"total\":2,\"startTick\":1,\"landingTick\":5}}}");
        assertNotNull(state.getResult());
        assertTrue("a file written before the field existed must load with no plates",
                state.getResult().getUnmetTicks().isEmpty());
    }

    @Test
    public void absentGiveBackLoadsAsTheWholeOffset() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{\"smoothLambda\":0.01}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        state.setSmoothGiveBack(0.3);
        SaveIO.applyAngleSolverTo(f, state);
        assertEquals(1.0, state.getSmoothGiveBack(), 0.0);
    }

    @Test
    public void savedGiveBackLoadsAndClamps() {
        AngleSolverState state = load("{\"version\":1,\"angleSolver\":{\"smoothGiveBack\":0.25}}");
        assertEquals(0.25, state.getSmoothGiveBack(), 0.0);
        state.setSmoothGiveBack(7.0);
        assertEquals(1.0, state.getSmoothGiveBack(), 0.0);
        state.setSmoothGiveBack(-1.0);
        assertEquals(0.0, state.getSmoothGiveBack(), 0.0);
    }

    private static SaveFile fileWithConstraintAtTick(int tick, int rowCount, int landingTick) {
        SaveFile f = new SaveFile();
        f.rows = new ArrayList<SaveFile.Row>();
        for (int i = 0; i < rowCount; i++) f.rows.add(new SaveFile.Row());
        f.angleSolver = new SaveFile.AngleSolver();
        f.angleSolver.startTick = 0;
        f.angleSolver.landingTick = landingTick;
        f.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        SaveFile.Tick t = new SaveFile.Tick();
        t.tick = tick;
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "X";
        c.op = "LE";
        c.value = 718.7;
        t.constraints.add(c);
        f.angleSolver.ticks.add(t);
        return f;
    }

    @Test
    public void constraintPastTheLastRowClampsOntoTheLastRowOnLoad() {
        SaveFile f = fileWithConstraintAtTick(84, 84, 61);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state, 84);

        assertNull("ghost slot is gone", state.tickConstraintsOrNull(84));
        assertNotNull("constraint healed onto the last row", state.tickConstraintsOrNull(83));
        assertEquals(1, state.tickConstraintsOrNull(83).getConstraints().size());
        assertEquals(61, state.getLandingTick());
    }

    @Test
    public void staleStartAndLandingTicksClampOnLoad() {
        SaveFile f = fileWithConstraintAtTick(2, 4, 9);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state, 4);

        assertEquals(3, state.getLandingTick());
        assertNotNull(state.tickConstraintsOrNull(2));
    }

    @Test
    public void withoutRowCountTicksApplyUnclamped() {
        SaveFile f = fileWithConstraintAtTick(84, 84, 61);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);

        assertNotNull(state.tickConstraintsOrNull(84));
    }
}
