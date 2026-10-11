package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OnejumpCheckTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Rig {
        final TurnProfileController controller;
        final AngleSolverState state;
        final InputData inputs;
        final BoxController boxes;
        final int first;
        final int landingTick;

        Rig() {
            SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            boxes = Fixtures.buildBoxes(file);
            AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
            first = state.getStartTick();
            landingTick = state.getLandingTick();
            OnejumpRigs.flag(inputs, first, landingTick - 1);
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f, () -> 1000,
                    () -> 1000, null, () -> null, boxes::getState,
                    () -> SaveIO.undoSignature(inputs, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, state));
            controller.tick();
            assertNull(controller.lastError(), controller.lastError());
            TurnProfileController.Current cur = controller.current();
            assertNotNull(cur);
            if (cur.landing == null) {
                Vec3dCore land = boxes.getPosition(landingTick);
                OnejumpRigs.landing(state, first, new TurnReference.Landing(landingTick - first, land.x - 0.01,
                        Double.NaN, Double.NaN, Double.NaN));
                controller.tick();
            }
        }

        String failing() {
            StringBuilder sb = new StringBuilder();
            for (TurnProfileController.SetupCheck.Item i : controller.lastCheck().items) {
                if (!i.ok) sb.append(i.label).append(": ").append(i.detail).append("; ");
            }
            return sb.toString();
        }
    }

    @Test
    public void aCompleteTasPassesEveryCheckAndTurnsTheLiveOffsetOn() {
        Rig rig = new Rig();
        assertFalse(rig.controller.isChecked());
        TurnProfileController.SetupCheck check = rig.controller.check();
        assertTrue(rig.failing(), check.ok);
        assertEquals(7, check.items.size());
        assertTrue(rig.controller.isChecked());
        rig.controller.tick();
        assertTrue(rig.controller.isChecked());
    }

    @Test
    public void theCheckFollowsTheRowsAsTypedNotTheSolverInputOverrides() {
        Rig rig = new Rig();
        rig.state.setDefaultInputs(AngleSolverState.InputMode.FORCE_45);
        rig.state.setDefaultSprint(AngleSolverState.SprintMode.ALWAYS);
        rig.controller.tick();
        rig.controller.refresh();
        boolean ok = rig.controller.check().ok;
        assertTrue(rig.failing(), ok);
        assertTrue(rig.controller.isChecked());
    }

    @Test
    public void anEditAfterTheCheckTurnsTheLiveOffsetOffUntilTheNextCheck() {
        Rig rig = new Rig();
        boolean ok = rig.controller.check().ok;
        assertTrue(rig.failing(), ok);
        InputRow row = rig.inputs.getRows().get(0);
        row.setKeyActive(InputRow.Key.SNEAK, !row.isKeyActive(InputRow.Key.SNEAK));
        rig.controller.tick();
        assertFalse(rig.controller.isChecked());
        assertTrue(rig.controller.lastCheck().ok);
        row.setKeyActive(InputRow.Key.SNEAK, !row.isKeyActive(InputRow.Key.SNEAK));
        rig.controller.tick();
        assertTrue(rig.controller.check().ok);
        assertTrue(rig.controller.isChecked());
    }

    @Test
    public void aYawEditOrANewTasDropsTheCheck() {
        Rig rig = new Rig();
        assertTrue(rig.controller.check().ok);
        assertTrue(rig.controller.lastCheckCurrent());
        InputRow row = rig.inputs.getRows().get(rig.first + 2);
        Float yaw = row.getYaw();
        row.setYaw(yaw == null ? 1.0f : yaw + 0.5f);
        rig.controller.tick();
        assertFalse(rig.controller.isChecked());
        assertFalse(rig.controller.lastCheckCurrent());
        rig.controller.onTasReplaced();
        assertNull(rig.controller.lastCheck());
        rig.controller.tick();
        assertFalse(rig.controller.isChecked());
    }

    @Test
    public void theCheckAppliesTheSurfaceStateOverTheFlaggedTicksFirst() {
        Rig rig = new Rig();
        int[] span = new int[2];
        rig.controller.setSurfaceApplier((from, to) -> {
            span[0] = from;
            span[1] = to;
        });
        boolean ok = rig.controller.check().ok;
        assertTrue(rig.failing(), ok);
        assertEquals(rig.first, span[0]);
        assertEquals(rig.landingTick, span[1]);
    }

    @Test
    public void theLbRowChoosesTheLandingEvenWhenTheFlagsEndEarlier() {
        Rig rig = new Rig();
        for (int t = rig.first + 4; t < rig.landingTick; t++) {
            InputRow r = rig.inputs.getRows().get(t);
            r.setOnejumpKeys(false);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        rig.inputs.getRows().get(rig.landingTick).setOnejumpLand(true);
        rig.controller.refresh();
        TurnProfileController.Current cur = rig.controller.current();
        assertNotNull(rig.controller.lastError(), cur);
        assertEquals(rig.landingTick - rig.first, cur.n);
        assertNotNull(cur.landing);
        assertEquals(cur.n, cur.landing.tick);
        assertEquals(rig.landingTick, rig.controller.landRow());
        boolean ok = rig.controller.check().ok;
        assertTrue(rig.failing(), ok);
    }

    @Test
    public void anLbRowWithoutAConstraintFailsTheLandingCheck() {
        Rig rig = new Rig();
        rig.inputs.getRows().get(rig.landingTick - 1).setOnejumpLand(true);
        rig.controller.refresh();
        TurnProfileController.SetupCheck check = rig.controller.check();
        assertFalse(check.ok);
        assertFalse(check.items.get(2).ok);
        assertTrue(check.items.get(2).detail, check.items.get(2).detail.contains("marked LB"));
    }

    @Test
    public void theLbFlagRoundTripsThroughTheSaveFile() {
        Rig rig = new Rig();
        rig.inputs.getRows().get(rig.landingTick).setOnejumpLand(true);
        SaveFile file = SaveIO.buildUndoSnapshot(rig.inputs, Vec3dCore.ZERO, Vec3dCore.ZERO, 0f, 0f, null, rig.state);
        SaveFile back = SaveIO.parseSafe(SaveIO.undoJson(file));
        InputData copy = new InputData();
        SaveIO.applyRowsTo(back, copy);
        assertTrue(copy.getRows().get(rig.landingTick).isOnejumpLand());
        assertFalse(copy.getRows().get(rig.landingTick - 1).isOnejumpLand());
        assertTrue(rig.inputs.getRows().get(rig.landingTick).copy().isOnejumpLand());
    }

    @Test
    public void aMissingLandingBoxFailsTheCheck() {
        Rig rig = new Rig();
        OnejumpRigs.clearLandings(rig.state, rig.first, rig.landingTick + 1);
        rig.controller.tick();
        TurnProfileController.SetupCheck check = rig.controller.check();
        assertFalse(check.ok);
        assertFalse(rig.controller.isChecked());
        assertFalse(check.items.get(2).ok);
        assertTrue(check.items.get(2).detail, check.items.get(2).detail.contains("press B"));
    }

    @Test
    public void aLandingBoxTheSimulationMissesFailsTheCheck() {
        Rig rig = new Rig();
        Vec3dCore land = rig.boxes.getPosition(rig.landingTick);
        OnejumpRigs.landing(rig.state, rig.first, new TurnReference.Landing(rig.landingTick - rig.first,
                land.x + 0.5, Double.NaN, Double.NaN, Double.NaN));
        rig.controller.tick();
        TurnProfileController.SetupCheck check = rig.controller.check();
        assertFalse(check.ok);
        assertFalse(check.items.get(4).ok);
        assertTrue(check.items.get(4).detail, check.items.get(4).detail.startsWith("tick " + (rig.landingTick + 1)));
    }

    @Test
    public void unflaggedTicksFailTheFirstCheck() {
        Rig rig = new Rig();
        for (InputRow r : rig.inputs.getRows()) {
            r.setOnejumpKeys(false);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        rig.controller.tick();
        TurnProfileController.SetupCheck check = rig.controller.check();
        assertFalse(check.ok);
        assertFalse(check.items.get(0).ok);
    }

    @Test
    public void aConstraintAddedLaterIsSeenOnTheNextTick() {
        Rig rig = new Rig();
        OnejumpRigs.clearLandings(rig.state, rig.first, rig.landingTick + 1);
        rig.controller.tick();
        assertNull(rig.controller.current().landing);
        Vec3dCore land = rig.boxes.getPosition(rig.landingTick);
        OnejumpRigs.landing(rig.state, rig.first, new TurnReference.Landing(rig.landingTick - rig.first,
                land.x - 0.01, Double.NaN, Double.NaN, Double.NaN));
        rig.controller.tick();
        assertNotNull(rig.controller.current().landing);
    }
}
