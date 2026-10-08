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
        double tasY = BLOCK_TOP;
        boolean tasGround = true;
        long ns = 1_000_000_000L;

        Rig(double xSpare, double zSpare) {
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
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f, () -> 1000,
                    () -> 1000, null, () -> null, this::tickState);
            controller.refresh();
            tracker = new AttemptTracker(controller, () -> true, () -> false, () -> false);
        }

        TickState tickState(int t) {
            if (t != landTick) return null;
            return new TickState(new Vec3dCore(landX, tasY, landZ), tasGround, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), new Vec3dCore(0, 0, 0), false, 0.0);
        }

        TurnProfileController.Current cur() {
            TurnProfileController.Current c = controller.current();
            assertNotNull(c);
            return c;
        }

        void tick(double x, double y, double z) {
            long now = ns;
            ns += 50_000_000L;
            tracker.tickStart(x, y, z, 0.0, 0.0, 0f, y <= BLOCK_TOP, now);
            tracker.tickEnd(true, false, false, false, false, false, true);
        }

        void jump(double dx, double dz) {
            tick(landX + dx - 1.5, BLOCK_TOP, landZ + dz);
            tick(landX + dx - 1.0, BLOCK_TOP + 0.4, landZ + dz);
            tick(landX + dx - 0.5, BLOCK_TOP + 0.5, landZ + dz);
            tick(landX + dx, BLOCK_TOP, landZ + dz);
            tick(landX + dx + 0.1, BLOCK_TOP, landZ + dz);
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
        assertTrue(cur.tasGrounded);
        assertTrue(cur.tasLands());
        assertEquals(-0.01, cur.tasMiss, 1e-9);
        assertNull(rig.controller.lastError());
        assertTrue(rig.controller.document().reference().isLandingOnly());
    }

    @Test
    public void theSetupCheckSeesAnAirborneTasAndAMissedBox() {
        Rig rig = new Rig(-0.02, Double.NaN);
        assertFalse(rig.cur().tasLands());
        assertEquals(0.02, rig.cur().tasMiss, 1e-9);
        rig.tasGround = false;
        rig.controller.refresh();
        assertFalse(rig.cur().tasGrounded);
    }

    @Test
    public void aDescentOntoTheBoxIsJudgedWithoutAReset() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(0.0, 0.0);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertTrue(a.landed);
        assertEquals(-0.01, a.margin, 1e-9);
        assertEquals(-0.01, a.marginX, 1e-9);
        assertTrue(Double.isNaN(a.marginZ));
        assertEquals("+0.01", a.verdict.substring(0, 5));
        assertEquals(rig.landTick, a.tasFirstTick);
        assertEquals(1, rig.controller.document().attempts().size());
        assertEquals(1, rig.controller.stats().landings);
        assertNull(rig.tracker.live());
        assertFalse(rig.tracker.isArmed());
        rig.tracker.mouseButton(AttemptTracker.RESET_BUTTON, true);
        assertFalse(rig.tracker.isArmed());
    }

    @Test
    public void aShortJumpIsAMissAndAFarPassIsIgnored() {
        Rig rig = new Rig(0.01, Double.NaN);
        rig.jump(-0.05, 0.0);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertFalse(a.landed);
        assertEquals(0.04, a.margin, 1e-9);
        assertTrue(a.verdict, a.verdict.endsWith(" X"));
        assertEquals(1, rig.controller.document().attempts().size());
        rig.jump(-2.0, 0.0);
        assertEquals(1, rig.controller.document().attempts().size());
        rig.tick(rig.landX, BLOCK_TOP, rig.landZ);
        rig.tick(rig.landX, BLOCK_TOP, rig.landZ);
        assertEquals(1, rig.controller.document().attempts().size());
    }

    @Test
    public void theOffsetAxisRejudgesEveryAttempt() {
        Rig rig = new Rig(0.01, 0.2);
        rig.jump(0.0, 0.0);
        TurnAttempt a = rig.tracker.last();
        assertEquals(-0.01, a.marginX, 1e-9);
        assertEquals(-0.2, a.marginZ, 1e-9);
        assertEquals(-0.01, a.margin, 1e-9);
        rig.controller.setAxis(TurnReference.AXIS_Z);
        assertEquals(TurnReference.AXIS_Z, rig.cur().axis);
        assertEquals(-0.2, a.margin, 1e-9);
        assertEquals(-0.2, rig.controller.stats().closest, 1e-9);
        rig.jump(0.0, 0.0);
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
    }
}
