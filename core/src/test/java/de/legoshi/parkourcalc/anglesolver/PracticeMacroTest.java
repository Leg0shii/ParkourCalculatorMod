package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.FakePlaybackBridge;
import de.legoshi.parkourcalc.core.PracticeMacro;
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
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PracticeMacroTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Rig {
        final TurnProfileController controller;
        final AttemptTracker tracker;
        final Settings settings = new Settings();
        final FakePlaybackBridge bridge = new FakePlaybackBridge();
        final PracticeMacro macro;
        final ExactJumpModel model;
        final TurnProfileController.Current cur;
        final JumpPhysicsInputs sc;
        final ForwardPath path;
        long ns = 1_000_000_000L;
        int fed;

        Rig(int mode) {
            SaveFile file = Fixtures.hpkHuman("d10", J335);
            model = ExactJumpModel.forMcVersion(file.mcVersion);
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f,
                    () -> 1000, () -> 1000, null, () -> null);
            OnejumpRigs.flag(inputs, state.getStartTick(), state.getLandingTick() - 1);
            controller.refresh();
            assertNull(controller.lastError(), controller.lastError());
            cur = controller.current();
            assertNotNull(cur);
            tracker = new AttemptTracker(controller, () -> true, () -> false, () -> false);
            settings.onejumpMacroMode = mode;
            settings.onejumpMacroDelayMs = 0;
            macro = new PracticeMacro(controller, tracker, settings);
            macro.setBridge(bridge);
            tracker.setResetListener(macro::onReset);
            tracker.setMacroMode(macro::activeMode);
            int tasFirst = controller.document().reference().tasFirstTick();
            sc = engine.snapshotPath(tasFirst, tasFirst + cur.n).spec.asScenario();
            path = model.forward(sc, sc.toGameFacings(cur.facing.clone()));
        }

        void reset() {
            tracker.mouseButton(AttemptTracker.RESET_BUTTON, true);
        }

        void playerTick() {
            int t = fed++;
            int pt = Math.min(t, path.posX.length - 1);
            int yt = Math.min(t, cur.n - 1);
            long now = ns;
            ns += 50_000_000L;
            boolean ground = t < cur.n ? !Double.isNaN(sc.slipAt(t)) : false;
            tracker.tickStart(path.posX[pt], sc.startPos.y, path.posZ[pt], path.velX[pt], path.velZ[pt],
                    (float) cur.facing[yt], ground, now);
            int mask = t < cur.n ? cur.keys[t] : 0;
            tracker.tickEnd((mask & TurnReference.KEY_W) != 0, (mask & TurnReference.KEY_A) != 0,
                    (mask & TurnReference.KEY_S) != 0, (mask & TurnReference.KEY_D) != 0,
                    (mask & TurnReference.KEY_JUMP) != 0, (mask & TurnReference.KEY_SNEAK) != 0,
                    (mask & TurnReference.KEY_SPRINT) != 0);
        }
    }

    @Test
    public void offModeNeverArmsOrTouchesTheBridge() {
        Rig rig = new Rig(PracticeMacro.OFF);
        rig.reset();
        for (int i = 0; i < 5; i++) rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertNull(rig.macro.label());
        assertEquals(0, rig.bridge.beginReplicaCalls);
        assertEquals(0, rig.bridge.applyEffectsCalls);
        assertTrue(rig.bridge.keys.isEmpty());
    }

    @Test
    public void aTickWithoutAResetStaysIdle() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        for (int i = 0; i < 5; i++) rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(0, rig.bridge.beginReplicaCalls);
    }

    @Test
    public void theResetDelayHoldsTheReplicaBack() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.settings.onejumpMacroDelayMs = 60_000;
        rig.reset();
        for (int i = 0; i < 5; i++) rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(0, rig.bridge.beginReplicaCalls);
        rig.settings.onejumpMacroDelayMs = 0;
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
    }

    @Test
    public void inputsModeReplaysTheLeadInThenTheReferenceKeysOnTheReplica() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        int lead = rig.cur.leadKeys.length;
        for (int t = 0; t < lead + rig.cur.n; t++) {
            rig.macro.tick();
            assertEquals("tick " + t, PracticeMacro.INPUTS, rig.macro.activeMode());
            assertEquals(PracticeMacro.LABEL_INPUTS, rig.macro.label());
            assertTrue(rig.bridge.replicaActive);
            assertFalse(rig.bridge.replicaFreezeMouse);
            int expected = t < lead ? rig.cur.leadKeys[t] : rig.cur.keys[t - lead];
            assertEquals("tick " + t, expected, rig.bridge.keyMask());
            int k = Math.max(0, t - lead);
            assertEquals("tick " + t, rig.cur.speedAmp[k], rig.bridge.speedAmplifier);
            assertEquals("tick " + t, rig.cur.jumpAmp[k], rig.bridge.jumpBoostAmplifier);
        }
        assertEquals(1, rig.bridge.beginReplicaCalls);
        assertEquals(0, rig.bridge.endReplicaCalls);
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertNull(rig.macro.label());
        assertTrue(rig.bridge.keys.isEmpty());
        assertEquals(1, rig.bridge.endReplicaCalls);
        assertFalse(rig.bridge.replicaActive);
    }

    @Test
    public void inputsModeWaitsForTheLiveAttemptToFinishBeforeEndingTheReplica() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        int lead = rig.cur.leadKeys.length;
        for (int t = 0; t < lead + rig.cur.n; t++) rig.macro.tick();
        rig.playerTick();
        assertNotNull(rig.tracker.live());
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
        assertEquals(1, rig.bridge.releaseAllCalls);
        assertTrue(rig.bridge.replicaActive);
        while (rig.tracker.live() != null) rig.playerTick();
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertEquals(PracticeMacro.INPUTS, a.macro);
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
    }

    @Test
    public void inputsModeGivesUpOnAnAttemptThatOutlivesTheTailTimeout() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        int lead = rig.cur.leadKeys.length;
        for (int t = 0; t < lead + rig.cur.n; t++) rig.macro.tick();
        rig.playerTick();
        int ticks = 0;
        while (rig.macro.activeMode() == PracticeMacro.INPUTS) {
            if (ticks % 10 == 9) rig.playerTick();
            rig.macro.tick();
            ticks++;
            assertTrue("tail " + ticks, ticks <= 200);
        }
        assertEquals(102, ticks);
        assertNotNull(rig.tracker.live());
        assertEquals(1, rig.bridge.endReplicaCalls);
    }

    @Test
    public void turnModeFreezesTheMouseAndSetsTheReferenceFacingPerRecordedTick() {
        Rig rig = new Rig(PracticeMacro.TURN);
        rig.bridge.playerKeyMask = TurnReference.KEY_W | TurnReference.KEY_SPRINT;
        rig.reset();
        rig.macro.tick();
        assertEquals(PracticeMacro.TURN, rig.macro.activeMode());
        assertEquals(PracticeMacro.LABEL_TURN, rig.macro.label());
        assertTrue(rig.bridge.replicaFreezeMouse);
        assertEquals(rig.bridge.playerKeyMask, rig.bridge.keyMask());
        assertEquals((float) rig.cur.facing[0], rig.bridge.yaw, 0f);
        assertEquals((float) rig.cur.facing[0], rig.bridge.headYaw, 0f);
        for (int t = 0; t < rig.cur.n; t++) {
            rig.playerTick();
            TurnAttempt live = rig.tracker.live();
            rig.macro.tick();
            if (live == null) break;
            int k = Math.min(live.recorded, rig.cur.n - 1);
            assertEquals("tick " + t, (float) rig.cur.facing[k], rig.bridge.yaw, 0f);
            assertEquals("tick " + t, rig.cur.speedAmp[k], rig.bridge.speedAmplifier);
        }
        while (rig.tracker.live() != null) rig.playerTick();
        assertEquals(PracticeMacro.TURN, rig.tracker.last().macro);
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
    }

    @Test
    public void turnModeGivesUpWhenTheAttemptNeverStarts() {
        Rig rig = new Rig(PracticeMacro.TURN);
        rig.reset();
        for (int t = 0; t <= 200; t++) {
            rig.macro.tick();
            assertEquals("tick " + t, PracticeMacro.TURN, rig.macro.activeMode());
        }
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
    }

    @Test
    public void aRefusedReplicaLeavesTheMacroOffUntilTheNextReset() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.bridge.replicaAllowed = false;
        rig.reset();
        rig.macro.tick();
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.beginReplicaCalls);
        rig.bridge.replicaAllowed = true;
        rig.macro.tick();
        assertEquals(0, rig.bridge.endReplicaCalls);
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        rig.reset();
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
        assertEquals(2, rig.bridge.beginReplicaCalls);
    }

    @Test
    public void switchingTheModeOffStopsTheReplicaAndClearsTheEffects() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        rig.macro.tick();
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
        int effectsBefore = rig.bridge.applyEffectsCalls;
        boolean hadEffects = rig.bridge.speedAmplifier > 0 || rig.bridge.jumpBoostAmplifier > 0
                || rig.bridge.slownessAmplifier > 0;
        rig.settings.onejumpMacroMode = PracticeMacro.OFF;
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
        assertTrue(rig.bridge.keys.isEmpty());
        assertEquals(hadEffects ? effectsBefore + 1 : effectsBefore, rig.bridge.applyEffectsCalls);
        assertEquals(0, rig.bridge.speedAmplifier);
        assertEquals(0, rig.bridge.jumpBoostAmplifier);
    }

    @Test
    public void aLostReplicaStopsTheMacro() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
        rig.bridge.replicaActive = false;
        rig.macro.tick();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
    }

    @Test
    public void aSecondResetRestartsTheReplay() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        rig.reset();
        rig.macro.tick();
        rig.macro.tick();
        rig.reset();
        assertEquals(PracticeMacro.OFF, rig.macro.activeMode());
        assertEquals(1, rig.bridge.endReplicaCalls);
        rig.macro.tick();
        assertEquals(PracticeMacro.INPUTS, rig.macro.activeMode());
        assertEquals(2, rig.bridge.beginReplicaCalls);
        int first = rig.cur.leadKeys.length > 0 ? rig.cur.leadKeys[0] : rig.cur.keys[0];
        assertEquals(first, rig.bridge.keyMask());
    }

    @Test
    public void stopWithoutABridgeIsSafe() {
        Rig rig = new Rig(PracticeMacro.INPUTS);
        PracticeMacro bare = new PracticeMacro(rig.controller, rig.tracker, rig.settings);
        bare.onReset();
        bare.tick();
        bare.stop();
        assertEquals(PracticeMacro.OFF, bare.activeMode());
        assertNull(bare.label());
    }
}
