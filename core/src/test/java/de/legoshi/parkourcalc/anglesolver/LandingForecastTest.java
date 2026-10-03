package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.LandingForecast;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LandingForecastTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Rig {
        final ExactJumpModel model;
        final AngleSolverState state;
        final TurnProfileController controller;
        final int tasFirst;
        final AngleSolverEngine engine;
        JumpSpec spec;
        final JumpPhysicsInputs sc;
        final double[] game;
        final ForwardPath full;
        final int n;
        TurnProfileController.Current cur;

        Rig() {
            SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
            model = ExactJumpModel.forMcVersion(file.mcVersion);
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            this.state = state;
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0.5f,
                    () -> 1000, () -> 1000, null, () -> null);
            OnejumpRigs.flag(inputs, state.getStartTick(), state.getLandingTick() - 1);
            controller.refresh();
            cur = controller.current();
            assertNotNull(controller.lastError(), cur);
            n = cur.n;
            tasFirst = controller.document().reference().tasFirstTick();
            spec = engine.snapshotPath(tasFirst, tasFirst + n).spec;
            sc = spec.asScenario();
            assertEquals(n, sc.numTicks);
            game = sc.toGameFacings(cur.facing.clone());
            full = model.forward(sc, game);
            assertNotNull(full.velX);
        }

        void landing(TurnReference.Landing l) {
            OnejumpRigs.landing(state, tasFirst, l);
            controller.refresh();
            cur = controller.current();
            spec = engine.snapshotPath(tasFirst, tasFirst + n).spec;
        }

        boolean ground(int t) {
            return !Double.isNaN(sc.slipAt(t));
        }

        LandingForecast forecastWithSpare(double spare) {
            landing(new TurnReference.Landing(n, full.posX[n] - spare, Double.NaN, Double.NaN, Double.NaN));
            LandingForecast f = LandingForecast.of(model, cur);
            assertNotNull(f);
            return f;
        }
    }

    @Test
    public void aSliceReproducesTheFullPathByteExactly() {
        Rig rig = new Rig();
        for (int t0 = 1; t0 < rig.n; t0++) {
            JumpPhysicsInputs slice = LandingForecast.slice(rig.sc, t0, rig.full.posX[t0], rig.full.posY[t0],
                    rig.full.posZ[t0], rig.full.velX[t0], rig.full.velY[t0], rig.full.velZ[t0], (float) rig.game[t0]);
            assertEquals(rig.n - t0, slice.numTicks);
            ForwardPath tail = rig.model.forward(slice, Arrays.copyOfRange(rig.game, t0, rig.n));
            for (int k = 0; k <= rig.n - t0; k++) {
                assertEquals("x at " + t0 + "+" + k, rig.full.posX[t0 + k], tail.posX[k], 0.0);
                assertEquals("z at " + t0 + "+" + k, rig.full.posZ[t0 + k], tail.posZ[k], 0.0);
                assertEquals("vx at " + t0 + "+" + k, rig.full.velX[t0 + k], tail.velX[k], 0.0);
                assertEquals("vz at " + t0 + "+" + k, rig.full.velZ[t0 + k], tail.velZ[k], 0.0);
            }
        }
    }

    @Test
    public void theVerticalStateDoesNotTouchTheHorizontalPath() {
        Rig rig = new Rig();
        for (int t0 = 1; t0 < rig.n; t0++) {
            JumpPhysicsInputs slice = LandingForecast.slice(rig.sc, t0, rig.full.posX[t0], rig.sc.startPos.y,
                    rig.full.posZ[t0], rig.full.velX[t0], 0.0, rig.full.velZ[t0], (float) rig.game[t0]);
            ForwardPath tail = rig.model.forward(slice, Arrays.copyOfRange(rig.game, t0, rig.n));
            assertEquals(rig.full.posX[rig.n], tail.posX[rig.n - t0], 0.0);
            assertEquals(rig.full.posZ[rig.n], tail.posZ[rig.n - t0], 0.0);
        }
    }

    @Test
    public void holdingThePlanFromTheTruePathPredictsTheRealLanding() {
        Rig rig = new Rig();
        LandingForecast f = rig.forecastWithSpare(0.5);
        assertEquals(rig.n, f.landingIndex());
        assertFalse(f.covers(rig.n));
        for (int t = 0; t < rig.n; t++) {
            assertTrue(f.covers(t));
            LandingForecast.Result r = f.at(t, rig.full.posX[t], rig.full.posZ[t], rig.full.velX[t], rig.full.velZ[t],
                    (float) rig.game[t], rig.ground(t));
            assertNotNull("tick " + t, r);
            assertFalse("tick " + t, r.offStructure);
            assertEquals("tick " + t, -0.5, r.held, 0.0);
            assertTrue("tick " + t, r.best <= r.held);
            assertTrue("tick " + t, r.landable());
            assertTrue("tick " + t, r.hasWindow());
            assertTrue("tick " + t, r.offsetLoDeg <= 0.0 && r.offsetHiDeg >= 0.0);
        }
        assertNull(f.at(rig.n, 0, 0, 0, 0, 0f, true));
    }

    @Test
    public void aStateFarOffThePathIsNotLandableAndAWrongStructureIsFlagged() {
        Rig rig = new Rig();
        LandingForecast f = rig.forecastWithSpare(0.0);
        int t = rig.n - 1;
        LandingForecast.Result off = f.at(t, rig.full.posX[t] - 2.0, rig.full.posZ[t], rig.full.velX[t], rig.full.velZ[t],
                (float) rig.game[t], rig.ground(t));
        assertTrue(off.held > 1.0);
        assertFalse(off.landable());
        LandingForecast.Result wrong = f.at(t, rig.full.posX[t], rig.full.posZ[t], rig.full.velX[t], rig.full.velZ[t],
                (float) rig.game[t], !rig.ground(t));
        assertTrue(wrong.offStructure);
    }

    @Test
    public void theSweepCountersAFacingError() {
        Rig rig = new Rig();
        LandingForecast f = rig.forecastWithSpare(0.01);
        int jump = -1;
        for (int t = 0; t < rig.n; t++) if (rig.cur.jumpTicks[t]) { jump = t; break; }
        assertTrue(jump >= 0 && jump + 1 < rig.n);
        int t = jump + 1;
        for (int sign : new int[] {1, -1}) {
            float yaw = (float) (rig.game[t] + sign * rig.cur.pixelDeg);
            LandingForecast.Result r = f.at(t, rig.full.posX[t], rig.full.posZ[t], rig.full.velX[t], rig.full.velZ[t],
                    yaw, rig.ground(t));
            assertTrue("held " + r.held, r.held > -0.01);
            assertTrue("best " + r.best + " held " + r.held, r.best < r.held);
            assertTrue("offset " + r.bestOffsetDeg, r.bestOffsetDeg * sign < 0.0);
        }
    }

    @Test
    public void theOffsetWindowRespectsEveryConstraintOfTheJump() {
        Rig rig = new Rig();
        LandingForecast f = rig.forecastWithSpare(0.0);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(LandingForecast.positional(rig.spec));
        assertTrue(comp.maxViolation(rig.game, rig.full) <= 0.0);
        assertTrue(LandingForecast.positional(rig.spec).constraints.size() < rig.spec.constraints.size());
        int checked = 0;
        for (int t = 0; t + 1 < rig.n; t++) {
            LandingForecast.Result r = f.at(t, rig.full.posX[t], rig.full.posZ[t], rig.full.velX[t], rig.full.velZ[t],
                    (float) rig.game[t], rig.ground(t));
            assertTrue("tick " + t, r.landable());
            for (double off : new double[] {r.offsetLoDeg, r.offsetHiDeg}) {
                double[] abs = rig.cur.facing.clone();
                for (int k = t + 1; k < rig.n; k++) abs[k] += off;
                double[] game = rig.sc.toGameFacings(abs);
                ForwardPath p = rig.model.forward(rig.sc, game);
                assertTrue("tick " + t + " offset " + off, comp.maxViolation(game, p) <= 0.0);
                assertTrue("tick " + t + " offset " + off, rig.cur.landing.margin(p.posX[rig.n], p.posZ[rig.n]) <= 0.0);
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    public void theForecastNeedsATasScenarioAndALandingBox() {
        Rig rig = new Rig();
        OnejumpRigs.clearLandings(rig.state, rig.tasFirst, rig.tasFirst + rig.n);
        rig.controller.refresh();
        assertNull(rig.controller.current().landing);
        assertNull(LandingForecast.of(rig.model, rig.controller.current()));
        assertNull(LandingForecast.of(null, rig.cur));
    }

    @Test
    public void failedTicksSummariseTheLatestFailedAttempts() {
        List<TurnAttempt> list = new ArrayList<TurnAttempt>();
        int[] failed = {8, 8, 8, 7, 9, -1};
        for (int i = 0; i < failed.length; i++) {
            list.add(new TurnAttempt(i + 1, 0, new double[] {0}, 1, true, false, false, "", 0.1, -1, -1, 0, 0, 0, null, null,
                    null, false, Double.NaN, new TurnAttempt.Forecast(null, null, null, null, null, failed[i])));
        }
        list.add(new TurnAttempt(9, 0, new double[] {0}, 1, true, true, false, "", -0.1, -1, -1, 0, 0, 0, null, null, null,
                false, Double.NaN, new TurnAttempt.Forecast(null, null, null, null, null, 3)));
        String s = LandingForecast.failedSummary(list, 100);
        assertEquals("tick 9 (60%), tick 8 (20%), tick 10 (20%)  of 5 failed", s);
        assertNull(LandingForecast.failedSummary(list.subList(5, 7), 100));
    }
}
