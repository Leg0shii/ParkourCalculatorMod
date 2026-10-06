package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.AttemptTracker;
import de.legoshi.parkourcalc.core.LandingForecast;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnProfileStore;
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
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
        final AngleSolverState state;
        final InputData inputs;
        final int solverStart;
        final int solverTicks;
        int tasFirst;
        TurnProfileController.Current cur;
        JumpPhysicsInputs sc;
        int k0;
        int span;
        long ns = 1_000_000_000L;
        int framesFrom = Integer.MAX_VALUE;
        int framesTo = Integer.MAX_VALUE;
        int[] maskOverride;

        Rig() {
            this(0);
        }

        Rig(int lead) {
            this(lead, false);
        }

        Rig(int lead, boolean timing) {
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
            this.state = state;
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f,
                    () -> 1000, () -> 1000, null, () -> null);
            OnejumpRigs.flag(inputs, solverStart - lead, state.getLandingTick() - 1);
            controller.refresh();
            assertNull(controller.lastError(), controller.lastError());
            tracker = new AttemptTracker(controller, () -> true, () -> false, () -> timing);
            sync();
        }

        void framesIn(int from, int to) {
            framesFrom = from;
            framesTo = to;
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
                landing(new TurnReference.Landing(cur.n, p.posX[cur.n], Double.NaN, Double.NaN, Double.NaN));
                span = cur.lastTick() - cur.startTick + 1;
            }
        }

        void landing(TurnReference.Landing l) {
            OnejumpRigs.landing(state, tasFirst, l);
            controller.refresh();
            cur = controller.current();
        }

        InputRow tasRow(int k) {
            return inputs.getRows().get(tasFirst + k);
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
            long now = ns;
            ns += 50_000_000L;
            float yaw = (float) yaws[yt];
            boolean ground = t < cur.n ? !Double.isNaN(sc.slipAt(t)) : t <= k0;
            tracker.tickStart(path.posX[pt] + (teleport ? 5.0 : 0.0), sc.startPos.y, path.posZ[pt], path.velX[pt],
                    path.velZ[pt], yaw, ground, now);
            if (t >= framesFrom && t <= framesTo) {
                tracker.frame(yaw, now + 10_000_000L);
                tracker.frame(yaw + 1f, now + 20_000_000L);
                tracker.frame(yaw + 2f, now + 30_000_000L);
                tracker.frame(yaw + 2f, now + 40_000_000L);
            }
            int mask = t < cur.n ? (fileKeys ? TurnReference.mask(inputs.getRows().get(tasFirst + t)) : cur.keys[t]) : 0;
            if (wrongKey) mask ^= TurnReference.KEY_W;
            if (maskOverride != null && t < maskOverride.length && maskOverride[t] >= 0) mask = maskOverride[t];
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
        rig.landing(new TurnReference.Landing(landTick, perfect.posX[rig.cur.n], Double.NaN, Double.NaN,
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
    public void aWrongKeyIsAnInputFailureButTheAttemptRunsOn() {
        Rig rig = new Rig();
        int at = rig.k0 + 2;
        rig.play(true, 0, 0.0, NONE, at);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.complete);
        assertTrue(a.inputFailure);
        assertTrue(a.verdict, a.landed);
        assertTrue(a.hasMargin());
        assertTrue(a.judged());
        assertEquals(rig.cur.startTick + at, a.failTick);
        assertEquals(rig.cur.n, a.recorded);
        assertEquals(rig.cur.keys[at], a.expectedKeys);
        assertEquals(rig.cur.keys[at] ^ TurnReference.KEY_W, a.failKeys);
        assertEquals(1, rig.controller.stats().inputFailures);
        assertEquals(1, rig.controller.stats().landings);
        assertNull(rig.tracker.live());
    }

    @Test
    public void onlyTheFirstWrongKeyIsKept() {
        Rig rig = new Rig();
        int first = rig.k0 + 1;
        rig.maskOverride = new int[rig.cur.n];
        java.util.Arrays.fill(rig.maskOverride, -1);
        rig.maskOverride[first] = rig.cur.keys[first] ^ TurnReference.KEY_W;
        rig.maskOverride[first + 2] = rig.cur.keys[first + 2] ^ TurnReference.KEY_W;
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.inputFailure);
        assertEquals(rig.cur.startTick + first, a.failTick);
        assertEquals(rig.cur.n, a.recorded);
        assertTrue(a.keysFailedAt(rig.cur.startTick + first));
        assertTrue(a.keysFailedAt(rig.cur.startTick + first + 2));
        assertFalse(a.keysFailedAt(rig.cur.startTick + first + 1));
        assertEquals(rig.cur.keys[first] ^ TurnReference.KEY_W, a.pressedKeysAt(rig.cur.startTick + first));
        for (int t = 0; t < rig.cur.n; t++) assertTrue("tick " + t, a.keysRecordedAt(rig.cur.startTick + t));
    }

    @Test
    public void stopCheckingKeysOnFailRecordsNothingAfterTheFirstWrongKey() {
        Rig rig = new Rig();
        rig.tracker.setStopKeysOnFail(() -> true);
        int first = rig.k0 + 1;
        rig.maskOverride = new int[rig.cur.n];
        java.util.Arrays.fill(rig.maskOverride, -1);
        rig.maskOverride[first] = rig.cur.keys[first] ^ TurnReference.KEY_W;
        rig.maskOverride[first + 2] = rig.cur.keys[first + 2] ^ TurnReference.KEY_W;
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertTrue(a.inputFailure);
        assertEquals(rig.cur.startTick + first, a.failTick);
        assertEquals(rig.cur.n, a.recorded);
        assertTrue(a.keysFailedAt(rig.cur.startTick + first));
        assertFalse(a.keysFailedAt(rig.cur.startTick + first + 2));
        assertTrue(a.keysRecordedAt(rig.cur.startTick + first));
        assertFalse(a.keysRecordedAt(rig.cur.startTick + first + 1));
        assertFalse(a.keysRecordedAt(rig.cur.startTick + first + 2));
    }

    @Test
    public void stopTheAttemptOnFailEndsItWithAKeysVerdict() {
        Rig rig = new Rig();
        rig.tracker.setStopTurnOnFail(() -> true);
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
        rig.tasRow(at).setOnejumpKeys(false);
        rig.controller.refresh();
        rig.sync();
        rig.play(true, 0, 0.0, NONE, at);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
    }

    @Test
    public void theCheckerExpectsTheTasKeys() {
        Rig rig = new Rig();
        int at = rig.k0 + 2;
        rig.play(true, 0, 0.0, NONE, NONE, true);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
        rig.play(true, 0, 0.0, NONE, at, true);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick + at, a.failTick);
        assertEquals(TurnReference.mask(rig.tasRow(at)), a.expectedKeys);
        assertEquals(TurnReference.mask(rig.tasRow(at)) ^ TurnReference.KEY_W, a.failKeys);
    }

    @Test
    public void theLandingRuleDecidesTheMargin() {
        Rig rig = new Rig();
        ForwardPath p = rig.pathFor(rig.cur.facing);
        int landTick = rig.cur.n;
        rig.landing(new TurnReference.Landing(landTick, p.posX[rig.cur.n] + 0.5, Double.NaN, Double.NaN,
                Double.NaN));
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertFalse(a.verdict, a.landed);
        assertEquals(0.5, a.margin, 1e-9);
        assertTrue(a.verdict, a.verdict.startsWith("-0.5"));
        assertTrue(a.verdict, a.verdict.endsWith(" X"));
        rig.landing(new TurnReference.Landing(landTick, Double.NaN, p.posX[rig.cur.n] + 0.5,
                p.posZ[rig.cur.n] - 1.0, p.posZ[rig.cur.n] + 1.0));
        rig.play(true, 0, 0.0, NONE, NONE);
        a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertEquals(-0.5, a.margin, 1e-9);
        assertEquals(-0.5, rig.controller.stats().closest, 1e-9);
    }

    @Test
    public void turnTimingMarksWhereTheMouseMovedInsideEachTick() {
        Rig rig = new Rig(0, true);
        rig.framesIn(0, Integer.MAX_VALUE);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertTrue(a.hasTiming());
        assertEquals(rig.cur.n, a.turnStart.length);
        assertEquals(rig.cur.n + 1, rig.span);
        for (int t = 0; t < a.recorded; t++) {
            assertEquals("tick " + t, 0.4f, a.turnStartAt(rig.cur.startTick + t), 1e-6f);
            assertEquals("tick " + t, 0.6f, a.turnEndAt(rig.cur.startTick + t), 1e-6f);
            float[] tr = a.traceAt(rig.cur.startTick + t);
            assertNotNull("tick " + t, tr);
            assertEquals(8, tr.length);
            assertEquals(0.2f, tr[2], 1e-6f);
            assertEquals(a.yaws[t] + 2.0, tr[7], 1e-3);
        }
        assertTrue(Float.isNaN(a.turnStartAt(rig.cur.startTick + a.recorded)));
        TurnAttempt stored = rig.controller.document().attempts().get(0);
        assertSame(a, stored);
        assertEquals(0.4f, stored.turnStartAt(rig.cur.startTick + rig.k0), 1e-6f);
        rig.play(true, 0, 0.0, NONE, NONE);
        assertNull(stored.trace);
        assertNotNull(rig.tracker.last().trace);
        assertEquals(0.4f, stored.turnStartAt(rig.cur.startTick + rig.k0), 1e-6f);
    }

    @Test
    public void runUpTicksBeforeTheJumpKeepTheirTimingAndStillTicksHaveNone() {
        Rig rig = new Rig(0, true);
        assertTrue(rig.k0 > 0);
        rig.framesIn(0, 0);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.hasTiming());
        assertEquals(0.4f, a.turnStartAt(rig.cur.startTick), 1e-6f);
        assertNotNull(a.traceAt(rig.cur.startTick));
        for (int t = 1; t < a.recorded; t++) {
            assertTrue("tick " + t, Float.isNaN(a.turnStartAt(rig.cur.startTick + t)));
            assertNull(a.traceAt(rig.cur.startTick + t));
        }
    }

    @Test
    public void timingOffLeavesTheAttemptUntimed() {
        Rig rig = new Rig(0, false);
        rig.framesIn(0, Integer.MAX_VALUE);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertFalse(a.hasTiming());
        assertNull(a.trace);
    }

    @Test
    public void aStillTickFailsOnASinglePixelPreturn() {
        Rig rig = new Rig();
        int at = rig.k0;
        rig.tasRow(at).setOnejumpFace(InputRow.ONEJUMP_FACE_STILL);
        rig.controller.refresh();
        rig.sync();
        assertTrue(rig.cur.still[at]);
        double px = rig.cur.pixelDeg;
        rig.play(true, at, px, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.turnFailure);
        assertFalse(a.inputFailure);
        assertTrue(a.complete);
        assertFalse(a.landed);
        assertFalse(a.judged());
        assertEquals(rig.cur.startTick + at, a.failTick);
        assertEquals(px, a.failTurn, 1e-4);
        assertTrue(a.verdict, a.verdict.startsWith("tick " + (at + 1) + ": turned +"));
        assertTrue(a.verdict, a.verdict.endsWith("(1 px), expected still"));
        assertEquals(at + 1, a.recorded);
        assertNull(rig.tracker.live());
        assertEquals(1, rig.controller.stats().turnFailures);
        assertEquals(0, rig.controller.stats().inputFailures);
        assertEquals(0, rig.controller.stats().landings);
        assertTrue(rig.controller.document().attempts().get(0).turnFailure);
    }

    @Test
    public void aStillTickPassesTheExactFacingAndATurnAfterIt() {
        Rig rig = new Rig();
        int at = rig.k0;
        rig.tasRow(at).setOnejumpFace(InputRow.ONEJUMP_FACE_STILL);
        rig.controller.refresh();
        rig.sync();
        rig.play(true, 0, 0.0, NONE, NONE);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
        rig.play(true, at + 1, 0.2, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertFalse(a.verdict, a.turnFailure);
        assertTrue(a.complete);
        assertEquals(0, rig.controller.stats().turnFailures);
        assertEquals(2, rig.controller.stats().attempts);
    }

    @Test
    public void aStillRunUpTickIsChecked() {
        Rig rig = new Rig();
        assertTrue(rig.k0 > 0);
        rig.tasRow(0).setOnejumpFace(InputRow.ONEJUMP_FACE_STILL);
        rig.controller.refresh();
        rig.sync();
        rig.play(true, 0, 0.5, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.turnFailure);
        assertEquals(rig.cur.startTick, a.failTick);
        assertEquals(1, a.recorded);
        assertNull(rig.tracker.live());
    }

    @Test
    public void theForecastFollowsAnAttemptAndNamesTheFailedTick() {
        Rig rig = new Rig();
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertTrue(a.hasForecast());
        assertEquals(-1, a.failedTick());
        assertEquals(rig.cur.startTick + rig.cur.n - 1, a.lastForecastTick());
        for (int t = 0; t < rig.cur.n; t++) {
            double held = a.forecast.held[t];
            assertFalse("tick " + t, Double.isNaN(held));
            assertTrue("tick " + t + " held " + held, Math.abs(held) < 1e-6);
            assertTrue("tick " + t, a.bestMarginAt(rig.cur.startTick + t) <= held);
        }
        assertEquals(rig.cur.n, a.forecast.held.length);
        TurnAttempt stored = rig.controller.document().attempts().get(0);
        assertTrue(stored.hasForecast());
        assertEquals(-1, stored.failedTick());

        double sign = rig.pathFor(rig.yawsFor(rig.k0 + 1, 3.0)).posX[rig.cur.n]
                < rig.pathFor(rig.yawsFor(rig.k0 + 1, -3.0)).posX[rig.cur.n] ? 3.0 : -3.0;
        rig.play(true, rig.k0 + 1, sign, NONE, NONE);
        a = rig.tracker.last();
        assertFalse(a.verdict, a.landed);
        assertTrue(a.failedTick() >= rig.cur.startTick + rig.k0 + 1);
        assertTrue(a.failedTick() < rig.cur.startTick + rig.cur.n);
        assertTrue(a.bestMarginAt(a.failedTick()) > 0.0);
        assertTrue(a.bestMarginAt(rig.cur.startTick + rig.k0) <= 0.0);
    }

    @Test
    public void aDisabledForecastIsNotComputed() {
        Rig rig = new Rig();
        rig.tracker.setForecastEnabled(() -> false);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.landed);
        assertFalse(a.hasForecast());
        assertEquals(-1, a.failedTick());
        assertNull(LandingForecast.failedSummary(rig.controller.document().attempts(), 100));
    }

    @Test
    public void anAbortedAttemptKeepsItsTimingSoFar() {
        Rig rig = new Rig(0, true);
        rig.framesIn(0, Integer.MAX_VALUE);
        int abortAt = rig.k0 + 3;
        rig.play(true, 0, 0.0, abortAt, NONE);
        TurnAttempt a = rig.tracker.last();
        assertFalse(a.complete);
        assertTrue(a.hasTiming());
        assertEquals(0.4f, a.turnStartAt(rig.cur.startTick + abortAt - 2), 1e-6f);
    }

    @Test
    public void aStoredReferenceFlagsAnUnflaggedTasOnce() throws Exception {
        Rig rig = new Rig();
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("pkc-onejump-adopt");
        de.legoshi.parkourcalc.core.save.FileSystemSaveStore fs =
                new de.legoshi.parkourcalc.core.save.FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        TurnProfileStore store = new TurnProfileStore(() -> fs);
        java.util.List<TurnAttempt> none = new java.util.ArrayList<TurnAttempt>();
        assertTrue(store.save("d10/j335", rig.controller.document().reference(), none));
        for (InputRow r : rig.inputs.getRows()) {
            r.setOnejumpKeys(false);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        TurnProfileController second = new TurnProfileController(rig.engine, rig.state, rig.inputs, () -> true,
                () -> 0.5f, () -> 1000, () -> 1000, store, () -> "d10/j335");
        second.refresh();
        TurnProfileController.Current cur = second.current();
        assertNotNull(second.lastError(), cur);
        assertEquals(rig.cur.n, cur.n);
        assertEquals(rig.tasFirst, second.document().reference().tasFirstTick());
        assertTrue(rig.tasRow(0).isOnejumpKeys());
        for (InputRow r : rig.inputs.getRows()) {
            r.setOnejumpKeys(false);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        second.refresh();
        assertNull(second.current());
        assertFalse(rig.tasRow(0).isOnejumpKeys());
    }

    @Test
    public void theLandingChanceComesFromTheMeasuredSpread() {
        Rig rig = new Rig();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec =
                rig.engine.snapshotPath(rig.tasFirst, rig.tasFirst + rig.cur.n).spec;
        double[][] none = new double[rig.cur.n][];
        for (int t = 0; t < none.length; t++) none[t] = new double[0];
        AttemptSampler.Stats exact = AttemptSampler.sample(rig.model, spec, rig.cur.facing, none, 20, AttemptSampler.SEED, null);
        assertEquals(20, exact.attempts);
        assertEquals(1.0, exact.rate(), 0.0);
        double[][] wide = new double[rig.cur.n][];
        double px = rig.cur.pixelDeg;
        for (int t = 0; t < wide.length; t++) {
            wide[t] = t >= rig.k0 + 1 && t <= rig.k0 + 3 ? new double[] {-3.0 * px, 0.0, 3.0 * px} : new double[0];
        }
        AttemptSampler.Stats spread = AttemptSampler.sample(rig.model, spec, rig.cur.facing, wide, 200, AttemptSampler.SEED, null);
        assertEquals(200, spread.attempts);
        assertTrue("rate " + spread.rate(), spread.rate() < 1.0);
        rig.play(true, 0, 0.0, NONE, NONE);
        rig.play(true, rig.k0 + 2, 40.0, NONE, NONE);
        rig.controller.rate();
        long until = System.currentTimeMillis() + 20_000L;
        while (rig.controller.isRating() && System.currentTimeMillis() < until) Thread.yield();
        TurnProfileController.Current cur = rig.controller.current();
        assertNotNull(cur.attempts);
        assertEquals(2, rig.controller.ratedSpread());
        assertTrue("rate " + cur.attempts.rate(), cur.attempts.rate() < 1.0);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.hasState());
        assertFalse(a.solved());
        assertEquals(a.forecast.failedTick, a.failedTick());
    }

    @Test
    public void anOptionalKeyMayBePressedOrNot() {
        Rig rig = new Rig();
        int at = -1;
        for (int t = rig.k0 + 1; t < rig.cur.n && at < 0; t++) {
            if (rig.cur.checkKeys[t] && (rig.cur.keys[t] & TurnReference.KEY_W) != 0) at = t;
        }
        assertTrue(at > 0);
        rig.maskOverride = new int[rig.cur.n];
        java.util.Arrays.fill(rig.maskOverride, -1);
        rig.maskOverride[at] = rig.cur.keys[at] ^ TurnReference.KEY_S;
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick + at, a.failTick);
        rig.tasRow(at).setOnejumpOptional(InputRow.Key.S, true);
        rig.controller.refresh();
        rig.sync();
        assertEquals(TurnReference.KEY_S, rig.cur.optionalKeys[at]);
        rig.play(true, 0, 0.0, NONE, NONE);
        a = rig.tracker.last();
        assertFalse(a.verdict, a.inputFailure);
        assertTrue(a.verdict, a.landed);
        rig.maskOverride[at] = rig.cur.keys[at] ^ TurnReference.KEY_S ^ TurnReference.KEY_W;
        rig.play(true, 0, 0.0, NONE, NONE);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().inputFailure);
    }

    @Test
    public void aJumpRowMustBeOnTheGround() {
        Rig rig = new Rig();
        int air = rig.k0 + 1;
        assertTrue(Double.isNaN(rig.sc.slipAt(air)));
        for (int t = 0; t <= rig.k0; t++) {
            rig.tasRow(t).setOnejumpKeys(false);
            rig.tasRow(t).setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        rig.tasRow(air).setKeyActive(InputRow.Key.JUMP, true);
        rig.controller.refresh();
        TurnProfileController.Current cur = rig.controller.current();
        assertNotNull(rig.controller.lastError(), cur);
        assertTrue((cur.keys[0] & TurnReference.KEY_JUMP) != 0);
        assertFalse(cur.jumpTicks[0]);
    }

    @Test
    public void theFirstKeyAfterTheResetOpensTheAttemptAtTheFirstReferenceTick() {
        Rig rig = new Rig();
        assertTrue(rig.k0 > 0);
        rig.reset();
        double[] yaws = rig.cur.facing.clone();
        ForwardPath path = rig.pathFor(yaws);
        rig.tick(0, yaws, path, false, false, false);
        assertFalse(rig.tracker.isArmed());
        TurnAttempt live = rig.tracker.live();
        assertNotNull(live);
        assertEquals(1, live.recorded);
        assertEquals(rig.cur.facing[0], live.yaws[0], 1e-4);
        for (int t = 1; t < rig.span; t++) rig.tick(t, yaws, path, false, false, false);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
    }

    @Test
    public void aSpaceHeldInTheAirDoesNotStartTheAttemptUntilTheGroundTick() {
        Rig rig = new Rig();
        rig.reset();
        double[] yaws = rig.cur.facing.clone();
        ForwardPath path = rig.pathFor(yaws);
        int air = TurnReference.KEY_JUMP | TurnReference.KEY_SPRINT;
        for (int i = 0; i < 3; i++) {
            rig.tracker.tickStart(path.posX[0], rig.sc.startPos.y + 0.1, path.posZ[0], 0.0, 0.0, (float) yaws[0], false, rig.ns);
            rig.ns += 50_000_000L;
            rig.keys(air);
            assertNull("air tick " + i, rig.tracker.live());
            assertTrue("air tick " + i, rig.tracker.isArmed());
            assertEquals("air tick " + i, -1, rig.tracker.pendingTicks());
        }
        rig.maskOverride = new int[] {air};
        rig.tick(0, yaws, path, false, false, false);
        rig.maskOverride = null;
        TurnAttempt live = rig.tracker.live();
        assertNotNull(live);
        assertEquals(1, live.recorded);
        assertEquals(rig.cur.facing[0], live.yaws[0], 1e-4);
    }

    @Test
    public void idleTicksAfterTheResetDoNotOpenTheAttempt() {
        Rig rig = new Rig();
        rig.reset();
        double[] yaws = rig.cur.facing.clone();
        ForwardPath path = rig.pathFor(yaws);
        rig.maskOverride = new int[] {0};
        for (int i = 0; i < 3; i++) rig.tick(0, yaws, path, false, false, false);
        assertTrue(rig.tracker.isArmed());
        assertNull(rig.tracker.live());
        assertNull(rig.tracker.last());
        rig.maskOverride = null;
        rig.play(false, 0, 0.0, NONE, NONE);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
        assertEquals(1, rig.controller.document().attempts().size());
    }

    @Test
    public void aHeldSprintKeyAloneDoesNotOpenTheAttempt() {
        Rig rig = new Rig();
        rig.reset();
        double[] yaws = rig.cur.facing.clone();
        ForwardPath path = rig.pathFor(yaws);
        rig.maskOverride = new int[] {TurnReference.KEY_SPRINT};
        for (int i = 0; i < 3; i++) rig.tick(0, yaws, path, false, false, false);
        assertTrue(rig.tracker.isArmed());
        assertNull(rig.tracker.live());
        rig.maskOverride = null;
        rig.play(false, 0, 0.0, NONE, NONE);
        assertTrue(rig.tracker.last().verdict, rig.tracker.last().landed);
    }

    @Test
    public void unflaggedRunUpRowsStillCountBeforeTheReference() {
        Rig rig = new Rig();
        int lead = rig.k0;
        assertTrue(lead > 0);
        for (int t = 0; t < lead; t++) {
            rig.tasRow(t).setOnejumpKeys(false);
            rig.tasRow(t).setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        rig.controller.refresh();
        rig.sync();
        assertEquals(lead, rig.tasFirst);
        assertEquals(lead, rig.cur.leadKeys.length);
        assertEquals(TurnReference.mask(rig.inputs.getRows().get(0)), rig.cur.leadKeys[0]);
        rig.reset();
        assertEquals(-1, rig.tracker.pendingTicks());
        double[] yaws = rig.cur.facing.clone();
        ForwardPath path = rig.pathFor(yaws);
        for (int t = 0; t < lead; t++) {
            rig.tracker.tickStart(path.posX[0], rig.sc.startPos.y, path.posZ[0], 0.0, 0.0, (float) yaws[0], true, rig.ns);
            rig.ns += 50_000_000L;
            rig.keys(TurnReference.mask(rig.inputs.getRows().get(t)));
            assertNull("lead tick " + t, rig.tracker.live());
            assertTrue("lead tick " + t, rig.tracker.isArmed());
            assertEquals("lead tick " + t, t, rig.tracker.pendingTicks());
        }
        for (int t = 0; t < rig.span; t++) rig.tick(t, yaws, path, false, false, false);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.landed);
        assertEquals(rig.cur.n, a.recorded);
        assertEquals(lead, a.tasFirstTick);
    }

    @Test
    public void uncheckedRowsPlayButAreNotJudged() {
        Rig rig = new Rig();
        int k0 = rig.k0;
        assertTrue(k0 > 0);
        for (int t = 0; t < k0; t++) rig.tasRow(t).setOnejumpKeys(false);
        rig.controller.refresh();
        rig.sync();
        assertEquals(0, rig.tasFirst);
        assertEquals(0, rig.cur.leadKeys.length);
        rig.maskOverride = new int[k0];
        java.util.Arrays.fill(rig.maskOverride, TurnReference.KEY_W | TurnReference.KEY_A);
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.landed);
        assertEquals(rig.cur.n, a.recorded);
        for (int t = 0; t < rig.cur.n; t++) assertEquals("tick " + t, rig.cur.facing[t], a.yaws[t], 1e-4);
        assertTrue(a.hasForecast());
        assertFalse(Double.isNaN(a.forecast.held[0]));
    }

    @Test
    public void aWrongFirstKeyIsAnInputFailureOnTheFirstTick() {
        Rig rig = new Rig();
        assertTrue(rig.k0 > 0);
        assertTrue(rig.cur.checkKeys[0]);
        rig.maskOverride = new int[] {rig.cur.keys[0] | TurnReference.KEY_A};
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick, a.failTick);
        assertEquals(rig.cur.keys[0], a.expectedKeys);
        assertEquals(rig.cur.keys[0] | TurnReference.KEY_A, a.failKeys);
        assertEquals(rig.cur.n, a.recorded);
        assertEquals(1, rig.controller.stats().inputFailures);
    }

    @Test
    public void aJumpPressedAtTheStartOfARunUpIsAnInputFailureOnTheFirstTick() {
        Rig rig = new Rig();
        assertTrue(rig.k0 > 0);
        assertTrue(rig.cur.checkKeys[0]);
        rig.maskOverride = new int[] {rig.cur.keys[rig.k0]};
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick, a.failTick);
        assertEquals(rig.cur.keys[0], a.expectedKeys);
        assertEquals(rig.cur.keys[rig.k0], a.failKeys);
        assertEquals(rig.cur.n, a.recorded);
        assertEquals(1, rig.controller.stats().inputFailures);
    }

    @Test
    public void aWrongKeyOnAnUncheckedRowDoesNotStopTheAttemptButALaterOneDoes() {
        Rig rig = new Rig();
        int k0 = rig.k0;
        assertTrue(k0 > 0);
        for (int t = 0; t < k0; t++) rig.tasRow(t).setOnejumpKeys(false);
        rig.controller.refresh();
        rig.sync();
        rig.maskOverride = new int[k0 + 3];
        java.util.Arrays.fill(rig.maskOverride, -1);
        rig.maskOverride[0] = TurnReference.KEY_W;
        rig.maskOverride[k0 + 2] = rig.cur.keys[k0 + 2] ^ TurnReference.KEY_W;
        rig.play(true, 0, 0.0, NONE, NONE);
        TurnAttempt a = rig.tracker.last();
        assertNotNull(a);
        assertTrue(a.verdict, a.inputFailure);
        assertEquals(rig.cur.startTick + k0 + 2, a.failTick);
        assertEquals(rig.cur.n, a.recorded);
    }

    @Test
    public void removingEveryFlagKeepsTheStoredReference() {
        Rig rig = new Rig();
        int n = rig.cur.n;
        for (InputRow r : rig.inputs.getRows()) {
            r.setOnejumpKeys(false);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_OFF);
        }
        rig.controller.refresh();
        assertNull(rig.controller.current());
        assertEquals(n, rig.controller.document().reference().size());
        assertTrue(rig.controller.lastError(), rig.controller.lastError().contains("kept"));
    }

    @Test
    public void replacingTheTasDropsTheUnsavedAttemptsAndTheSelection() {
        Rig rig = new Rig();
        rig.play(true, 0, 0.0, NONE, NONE);
        assertEquals(1, rig.controller.document().attempts().size());
        rig.controller.select(1);
        rig.controller.onTasReplaced();
        assertEquals(-1, rig.controller.selectedNumber());
        assertEquals(0, rig.controller.document().attempts().size());
        assertNotNull(rig.controller.current());
    }

    @Test
    public void theSnapshotKeepsTheGoalWallInLegalMode() {
        Rig rig = new Rig();
        int plain = rig.engine.snapshotPath(rig.tasFirst, rig.tasFirst + rig.cur.n).spec.constraints.size();
        rig.state.setLegalMode(true);
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        AngleSolverEngine.PathSnapshot legal = rig.engine.snapshotPath(rig.tasFirst, rig.tasFirst + rig.cur.n);
        assertNotNull(legal);
        assertEquals(plain, legal.spec.constraints.size());
    }
}
