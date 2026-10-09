package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OnejumpFastModeTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";
    private static final double BLOCK_TOP = 70.0;

    private static final class Rig {
        final TurnProfileController controller;
        final AttemptTracker tracker;
        final AngleSolverState state;
        final InputData inputs;
        final int landTick;
        final double landX;
        final double landZ;
        final boolean simulated;
        long ns = 1_000_000_000L;

        Rig(double xSpare, double zSpare) {
            this(xSpare, zSpare, true, false);
        }

        Rig(double xSpare, double zSpare, boolean fromBlock, boolean simulated) {
            this.simulated = simulated;
            SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            int start = state.getStartTick();
            landTick = state.getLandingTick();
            AngleSolverEngine.PathSnapshot snap = engine.snapshotPath(start, landTick);
            assertNotNull(snap);
            JumpPhysicsInputs sc = snap.spec.asScenario();
            ForwardPath p = model.forward(sc, sc.toGameFacings(snap.yaws.clone()));
            int n = landTick - start;
            landX = p.posX[n];
            landZ = p.posZ[n];
            for (InputRow r : inputs.getRows()) {
                r.setOnejumpKeys(false);
                r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
            }
            OnejumpRigs.clearLandings(state, 0, inputs.getRows().size());
            OnejumpRigs.landing(state, 0, new TurnReference.Landing(landTick, landX - xSpare, Double.NaN,
                    Double.isNaN(zSpare) ? Double.NaN : landZ - zSpare, Double.NaN));
            if (fromBlock) state.tickConstraints(landTick).setLandingY(BLOCK_TOP);
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f, () -> 1000,
                    () -> 1000, null, () -> null, this::tickState);
            controller.refresh();
            tracker = new AttemptTracker(controller, () -> true, () -> false, () -> false);
        }

        TickState tickState(int t) {
            if (!simulated || t != landTick) return null;
            return new TickState(new Vec3dCore(landX, BLOCK_TOP, landZ), true, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), new Vec3dCore(0, 0, 0), false, 0.0);
        }

        TurnProfileController.Current cur() {
            TurnProfileController.Current c = controller.current();
            assertNotNull(c);
            return c;
        }

        void tick(double x, double y, double z, boolean ground) {
            long now = ns;
            ns += 50_000_000L;
            tracker.tickStart(x, y, z, 0.0, 0.0, 0f, ground, now);
            tracker.tickEnd(true, false, false, false, false, false, true);
        }

        void jump(double dx, double dz, boolean lands) {
            tick(landX + dx - 0.6, BLOCK_TOP, landZ + dz, true);
            tick(landX + dx - 0.4, BLOCK_TOP + 0.4, landZ + dz, false);
            tick(landX + dx - 0.2, BLOCK_TOP + 0.5, landZ + dz, false);
            if (lands) tick(landX + dx, BLOCK_TOP, landZ + dz, true);
            else tick(landX + dx, BLOCK_TOP - 0.3, landZ + dz, false);
            tick(landX + dx + 0.1, lands ? BLOCK_TOP : BLOCK_TOP - 0.8, landZ + dz, lands);
        }

        int attempts() {
            return controller.document().attempts().size();
        }
    }

    @Test
    public void aConstraintWithoutFlagsBuildsALandingOnlyReference() {
        Rig rig = new Rig(0.01, Double.NaN);
        TurnProfileController.Current cur = rig.cur();
        assertTrue(cur.isFast());
        assertEquals(0, cur.n);
        assertEquals(rig.landTick, cur.tasFirstTick);
        assertEquals(0, cur.landing.tick);
        assertEquals(BLOCK_TOP, cur.landing.y, 0.0);
        assertNull(rig.controller.lastError());
        assertTrue(rig.controller.document().reference().isLandingOnly());
    }

    @Test
    public void aTypedConstraintWithoutASimulatedLandingHasNoHeightAndJudgesNothing() {
        Rig rig = new Rig(0.01, Double.NaN, false, false);
        assertTrue(rig.cur().isFast());
        assertFalse(rig.cur().landing.hasY());
        rig.jump(0.0, 0.0, true);
        assertEquals(0, rig.attempts());
    }

    @Test
    public void aTypedConstraintTakesTheHeightFromAGroundedSimulatedLanding() {
        Rig rig = new Rig(0.01, Double.NaN, false, true);
        assertEquals(BLOCK_TOP, rig.cur().landing.y, 0.0);
        rig.jump(0.0, 0.0, true);
        assertEquals(1, rig.attempts());
        Rig miss = new Rig(-0.02, Double.NaN, false, true);
        assertFalse(miss.cur().landing.hasY());
    }

    @Test
    public void theBlockHeightSurvivesASaveRoundTrip() {
        Rig rig = new Rig(0.01, Double.NaN);
        SaveFile file = SaveIO.buildUndoSnapshot(rig.inputs, new Vec3dCore(0, 0, 0), new Vec3dCore(0, 0, 0), 0f, 0f,
                null, rig.state);
        SaveFile back = SaveIO.parseSafe(SaveIO.undoJson(file));
        AngleSolverState copy = new AngleSolverState();
        SaveIO.applyAngleSolverTo(back, copy);
        assertEquals(BLOCK_TOP, copy.tickConstraintsOrNull(rig.landTick).getLandingY(), 0.0);
        copy.clearFootprint(rig.landTick);
        assertFalse(copy.tickConstraintsOrNull(rig.landTick).hasLandingY());
        assertEquals(BLOCK_TOP, rig.state.tickConstraintsOrNull(rig.landTick).copy().getLandingY(), 0.0);
    }

    @Test
    public void aDescentOntoTheBoxIsJudgedWithoutAReset() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(0.0, 0.0, true);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertTrue(a.landed);
        assertEquals(-0.01, a.margin, 1e-9);
        assertEquals(-0.01, a.marginX, 1e-9);
        assertTrue(Double.isNaN(a.marginZ));
        assertEquals("+0.01", a.verdict.substring(0, 5));
        assertEquals(rig.landTick, a.tasFirstTick);
        assertEquals(1, rig.attempts());
        assertEquals(1, rig.controller.stats().landings);
        assertNull(rig.tracker.live());
        assertFalse(rig.tracker.isArmed());
        rig.tracker.mouseButton(AttemptTracker.RESET_BUTTON, true);
        assertFalse(rig.tracker.isArmed());
    }

    @Test
    public void aMissIsMeasuredOnTheLastTickAboveTheBlock() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(-0.05, 0.0, false);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertFalse(a.landed);
        assertEquals(0.24, a.margin, 1e-9);
        assertTrue(a.verdict, a.verdict.endsWith(" X"));
        assertEquals(1, rig.attempts());
        assertEquals(0, rig.controller.stats().landings);
    }

    @Test
    public void aLandingOnANeighbourBlockIsAMissMeasuredAbove() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(-0.05, 0.0, true);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertFalse(a.landed);
        assertEquals(0.24, a.margin, 1e-9);
    }

    @Test
    public void passesFurtherThanTheToleranceAreIgnored() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(-2.0, 0.0, false);
        assertEquals(0, rig.attempts());
        rig.jump(-0.3, 0.0, false);
        assertEquals(1, rig.attempts());
        assertEquals(0.49, rig.tracker.last().margin, 1e-9);
        rig.tick(rig.landX, BLOCK_TOP, rig.landZ, true);
        rig.tick(rig.landX, BLOCK_TOP, rig.landZ, true);
        assertEquals(1, rig.attempts());
    }

    @Test
    public void theOffsetAxisRejudgesEveryAttempt() {
        Rig rig = new Rig(0.01, 0.2);
        rig.jump(0.0, 0.0, true);
        TurnAttempt a = rig.tracker.last();
        assertEquals(-0.01, a.marginX, 1e-9);
        assertEquals(-0.2, a.marginZ, 1e-9);
        assertEquals(-0.01, a.margin, 1e-9);
        rig.controller.setAxis(TurnReference.AXIS_Z);
        assertEquals(TurnReference.AXIS_Z, rig.cur().axis);
        assertEquals(-0.2, a.margin, 1e-9);
        assertEquals(-0.2, rig.controller.stats().closest, 1e-9);
        rig.jump(0.0, 0.0, true);
        assertEquals(-0.2, rig.tracker.last().margin, 1e-9);
        rig.controller.refresh();
        assertEquals(TurnReference.AXIS_Z, rig.controller.document().reference().axis());
        rig.controller.setAxis(TurnReference.AXIS_X);
        assertEquals(-0.01, a.margin, 1e-9);
        assertEquals(-0.01, rig.tracker.last().margin, 1e-9);
    }

    @Test
    public void theCrossingNeedsADescentOntoTheLandingHeight() {
        assertTrue(AttemptTracker.crossed(70.5, 70.0, 70.0));
        assertTrue(AttemptTracker.crossed(70.5, 69.2, 70.0));
        assertFalse(AttemptTracker.crossed(70.0, 70.0, 70.0));
        assertFalse(AttemptTracker.crossed(69.0, 70.0, 70.0));
        assertFalse(AttemptTracker.crossed(70.5, 70.4, 70.0));
        assertEquals(0.6, AttemptTracker.FAST_NEAR, 0.0);
    }
}
