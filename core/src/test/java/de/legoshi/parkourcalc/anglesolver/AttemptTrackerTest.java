package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AttemptTrackerTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";
    private static final int NONE = Integer.MIN_VALUE;

    private static final class Rig {
        final TurnProfileController controller;
        final AttemptTracker tracker;
        final ExactJumpModel model;
        final AngleSolverEngine engine;
        final InputData inputs;
        final int solverStart;
        final int solverTicks;
        int tasFirst;
        TurnProfileController.Current cur;
        JumpPhysicsInputs sc;
        int k0;
        int span;

        Rig() {
            this(0);
        }

        Rig(int lead) {
            SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
            model = ExactJumpModel.forMcVersion(file.mcVersion);
            inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            solverStart = state.getStartTick() + lead;
            state.setStartTick(solverStart);
            solverTicks = state.getLandingTick() - solverStart;
            engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f,
                    AttemptSampler.Scatter::new, () -> 1000, null, () -> null, n -> { });
            assertTrue(controller.lastError(), controller.importFromTas(solverStart - lead, state.getLandingTick() - 1));
            tracker = new AttemptTracker(controller, () -> true, () -> false);
            sync();
        }

        void sync() {
            cur = controller.current();
            assertNotNull("profile must compute", cur);
            assertTrue("the reference path must meet the TAS constraints", cur.pathLands());
            tasFirst = controller.document().reference().tasFirstTick();
            sc = engine.snapshotPath(tasFirst, tasFirst + cur.n).spec.asScenario();
            int jump = -1;
            for (int t = 0; t < cur.n && jump < 0; t++) if (cur.jumpTicks[t]) jump = t;
            k0 = jump;
            span = cur.lastTick() - cur.startTick + 1;
            if (cur.landing == null) {
                ForwardPath p = model.forward(sc, sc.toGameFacings(cur.facing.clone()));
                controller.setLanding(new TurnReference.Landing(cur.n, p.posX[cur.n], Double.NaN, Double.NaN, Double.NaN));
                cur = controller.current();
                span = cur.lastTick() - cur.startTick + 1;
            }
        }

        void reset() {
            tracker.mouseButton(AttemptTracker.RESET_BUTTON, true);
        }

        double[] yawsFor(double shiftFrom, double shiftDeg) {
            double[] y = cur.facing.clone();
            for (int t = 0; t < y.length; t++) if (t >= shiftFrom) y[t] += shiftDeg;
            return y;
        }

        ForwardPath pathFor(double[] yaws) {
            return model.forward(sc, sc.toGameFacings(yaws.clone()));
        }

        void keys(int mask) {
            tracker.tickEnd((mask & TurnReference.KEY_W) != 0, (mask & TurnReference.KEY_A) != 0,
                    (mask & TurnReference.KEY_S) != 0, (mask & TurnReference.KEY_D) != 0,
                    (mask & TurnReference.KEY_JUMP) != 0, (mask & TurnReference.KEY_SNEAK) != 0,
                    (mask & TurnReference.KEY_SPRINT) != 0);
        }

        void tick(int t, double[] yaws, ForwardPath path, boolean teleport, boolean wrongKey, boolean fileKeys) {
            int pt = Math.min(t, path.posX.length - 1);
            int yt = Math.min(t, cur.n - 1);
            tracker.tickStart(path.posX[pt] + (teleport ? 5.0 : 0.0), sc.startPos.y, path.posZ[pt], (float) yaws[yt], t <= k0);
            int mask = t < cur.n ? (fileKeys ? TurnReference.mask(inputs.getRows().get(tasFirst + t)) : cur.keys[t]) : 0;
            if (wrongKey) mask ^= TurnReference.KEY_W;
            keys(mask);
        }

        void play(boolean reset, double shiftFrom, double shiftDeg, int abortAt, int wrongKeyAt, boolean fileKeys) {
            if (reset) reset();
            double[] yaws = yawsFor(shiftFrom, shiftDeg);
            ForwardPath path = pathFor(yaws);
            for (int t = 0; t < span; t++) {
                tick(t, yaws, path, t == abortAt, t == wrongKeyAt, fileKeys);
                if (t == abortAt) return;
            }
        }

        void play(boolean reset, double shiftFrom, double shiftDeg, int abortAt, int wrongKeyAt) {
            play(reset, shiftFrom, shiftDeg, abortAt, wrongKeyAt, false);
        }
    }

    @Test
    public void thePathsOwnFacingsLand() {
        Rig rig = new Rig();
        assertEquals(rig.solverStart, rig.tasFirst);
        assertEquals(0, rig.cur.startTick);
        assertEquals(rig.solverTicks, rig.cur.n);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.complete);
        assertTrue(a.verdict, a.landed);
        assertTrue(a.hasMargin());
        assertTrue(a.margin <= 0.0);
        assertEquals(rig.cur.n, a.recorded);
        assertEquals(1, rig.controller.document().attempts().size());
        assertEquals(1, rig.controller.stats().attempts);
        assertEquals(1, rig.controller.stats().landings);
        assertNull(rig.tracker.live());
        assertFalse(rig.tracker.isArmed());
        for (int t = 0; t < a.recorded; t++) assertEquals(rig.cur.facing[t], a.yaws[t], 1e-4);
    }

    @Test
    public void aShiftedTurnFallsShortAndNamesTheWorstTick() {
        Rig rig = new Rig();
        int landTick = rig.cur.n;
        ForwardPath perfect = rig.pathFor(rig.cur.facing);
        rig.controller.setLanding(new TurnReference.Landing(landTick, perfect.posX[rig.cur.n], Double.NaN, Double.NaN,
                Double.NaN));
        rig.sync();
        double sign = rig.pathFor(rig.yawsFor(rig.k0 + 1, 3.0)).posX[rig.cur.n]
                < rig.pathFor(rig.yawsFor(rig.k0 + 1, -3.0)).posX[rig.cur.n] ? 3.0 : -3.0;
        rig.play(true, rig.k0 + 1, sign, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertFalse(a.verdict, a.landed);
        assertTrue(a.verdict, a.verdict.startsWith("-"));
        assertTrue(a.verdict, a.verdict.endsWith(" X"));
        assertTrue(a.margin > 0.0);
        assertTrue(a.worstTick >= rig.cur.startTick + rig.k0 + 1);
        assertEquals(0, rig.controller.stats().landings);
        assertTrue(rig.controller.stats().closest > 0.0);
    }

    @Test
    public void aTeleportAbortsWithoutCounting() {
        Rig rig = new Rig();
        int abortAt = rig.k0 + 3;
        rig.play(true, 0, 0.0, abortAt, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertFalse(a.verdict, a.complete);
        assertEquals(abortAt, a.recorded);
        assertEquals(0, rig.controller.stats().attempts);
        assertNull(rig.tracker.live());
        assertFalse(rig.tracker.isArmed());
    }

    @Test
    public void aWrongKeyIsAnInputFailure() {
        Rig rig = new Rig();
        int at = rig.k0 + 2;
        rig.play(true, 0, 0.0, NONE, at);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertTrue(a.inputFailure);
        assertFalse(a.landed);
        assertFalse(a.hasMargin());
        assertEquals(rig.cur.startTick + at, a.failTick);
        assertEquals(at + 1, a.recorded);
        assertEquals(rig.cur.keys[at], a.expectedKeys);
        assertEquals(rig.cur.keys[at] ^ TurnReference.KEY_W, a.failKeys);
        assertTrue(a.verdict, a.verdict.startsWith("tick " + (at + 1) + ": "));
        assertEquals(1, rig.controller.stats().inputFailures);
        assertEquals(0, rig.controller.stats().landings);
        assertNull(rig.tracker.live());
    }

    @Test
    public void keysWithoutAResetClickAreIgnored() {
        Rig rig = new Rig();
        rig.play(false, 0, 0.0, NONE, NONE);
        assertNull(rig.tracker.live());
        assertNull(rig.tracker.last());
        assertEquals(0, rig.controller.stats().attempts);
    }

    @Test
    public void aResetDuringAnAttemptAbortsItAndArmsTheNext() {
        Rig rig = new Rig();
        rig.reset();
        double[] yaws = rig.yawsFor(0, 0.0);
        ForwardPath path = rig.pathFor(yaws);
        for (int t = 0; t <= rig.k0 + 1; t++) rig.tick(t, yaws, path, false, false, false);
        assertNotNull(rig.tracker.live());
        rig.reset();
        assertNull(rig.tracker.live());
        assertTrue(rig.tracker.isArmed());
        assertFalse(rig.tracker.last().complete);
        assertEquals(0, rig.controller.stats().attempts);
        rig.play(false, 0, 0.0, NONE, NONE);
        assertTrue(rig.tracker.last().landed);
        assertEquals(1, rig.controller.stats().attempts);
    }

    @Test
    public void aReferenceStartingBeforeTheSolverRangeIsTracked() {
        Rig rig = new Rig(1);
        assertEquals(rig.solverStart - 1, rig.tasFirst);
        assertEquals(rig.solverTicks + 1, rig.cur.n);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.landed);
        assertEquals(rig.cur.n, a.recorded);
    }

    @Test
    public void anUncheckedKeysRowIgnoresAWrongKey() {
        Rig rig = new Rig();
        int at = rig.k0 + 2;
        TurnReference ref = rig.controller.document().reference();
        ref.setCheckKeys(ref.row(at), false);
        rig.controller.referenceChanged();
        rig.sync();
        rig.play(true, 0, 0.0, NONE, at);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
    }

    @Test
    public void handSetKeysAreWhatTheCheckerExpects() {
        Rig rig = new Rig();
        int at = rig.k0 + 2;
        TurnReference ref = rig.controller.document().reference();
        TurnReference.applyKeys(ref.row(at), TurnReference.KEY_S);
        rig.controller.referenceChanged();
        rig.sync();
        rig.play(true, 0, 0.0, NONE, NONE, true);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick + at, a.failTick);
        assertEquals(TurnReference.KEY_S, a.expectedKeys);
    }

    @Test
    public void theLandingRuleDecidesTheMargin() {
        Rig rig = new Rig();
        ForwardPath p = rig.pathFor(rig.cur.facing);
        int landTick = rig.cur.n;
        rig.controller.setLanding(new TurnReference.Landing(landTick, p.posX[rig.cur.n] + 0.5, Double.NaN, Double.NaN,
                Double.NaN));
        rig.sync();
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertFalse(a.verdict, a.landed);
        assertEquals(0.5, a.margin, 1e-9);
        assertTrue(a.verdict, a.verdict.startsWith("-0.5"));
        assertTrue(a.verdict, a.verdict.endsWith(" X"));
        rig.controller.setLanding(new TurnReference.Landing(landTick, Double.NaN, p.posX[rig.cur.n] + 0.5,
                p.posZ[rig.cur.n] - 1.0, p.posZ[rig.cur.n] + 1.0));
        rig.sync();
        rig.play(true, 0, 0.0, NONE, NONE);
        a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertEquals(-0.5, a.margin, 1e-9);
        assertEquals(-0.5, rig.controller.stats().closest, 1e-9);
    }
}
