package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.DeepCheck;
import de.legoshi.parkourcalc.core.TurnAttempt;
import de.legoshi.parkourcalc.core.TurnProfileController;
import de.legoshi.parkourcalc.core.TurnProfileStore;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@Category(SlowSolverTests.class)
public class DeepCheckTest {

    private static final String J335 = "j335_1bmhh_Single_Fencegat_Butterfly_Neo";

    private static final class Rig {
        final ExactJumpModel model;
        final TurnProfileController controller;
        final TurnProfileController.Current cur;
        final JumpPhysicsInputs sc;
        final int n;

        Rig() {
            SaveFile file = HpkStartBenchmark.loadCapture("d10", J335);
            model = ExactJumpModel.forMcVersion(file.mcVersion);
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            controller = new TurnProfileController(engine, state, inputs, () -> true, () -> 0f, () -> 1000, () -> 1000,
                    (TurnProfileStore) null, () -> null);
            OnejumpRigs.flag(inputs, state.getStartTick(), state.getLandingTick() - 1);
            controller.refresh();
            cur = controller.current();
            assertNotNull(controller.lastError(), cur);
            assertNotNull("the capture needs a landing box", cur.landing);
            n = cur.n;
            int tasFirst = controller.document().reference().tasFirstTick();
            sc = engine.snapshotPath(tasFirst, tasFirst + n).spec.asScenario();
            assertEquals(n, sc.numTicks);
        }

        double[] onGrid(double[] yaws) {
            double[] out = new double[yaws.length];
            double start = yaws[0];
            for (int t = 0; t < yaws.length; t++) {
                out[t] = start + Math.round((yaws[t] - start) / cur.pixelDeg) * cur.pixelDeg;
            }
            return out;
        }

        TurnAttempt attempt(double[] raw) {
            double[] yaws = onGrid(raw);
            ForwardPath p = model.forward(sc, sc.toGameFacings(yaws.clone()));
            double[] x = new double[n];
            double[] z = new double[n];
            double[] vx = new double[n];
            double[] vz = new double[n];
            boolean[] ground = new boolean[n];
            for (int t = 0; t < n; t++) {
                x[t] = p.posX[t];
                z[t] = p.posZ[t];
                vx[t] = p.velX[t];
                vz[t] = p.velZ[t];
                ground[t] = !Double.isNaN(sc.slipAt(t));
            }
            double margin = cur.landing.margin(p.posX[n], p.posZ[n]);
            TurnAttempt.Forecast fc = new TurnAttempt.Forecast(null, null, null, -1, x, z, vx, vz, ground);
            return new TurnAttempt(1, 0, yaws, n, true, margin <= 0.0, false, "", margin, -1, -1, 0, 0, 0, null, null,
                    null, false, Double.NaN, fc);
        }
    }

    @Test
    public void thePerfectPathIsNeverLost() {
        Rig rig = new Rig();
        TurnAttempt a = rig.attempt(rig.cur.facing.clone());
        assumeTrue("the pixel-exact reference must land for this test", a.landed);
        long t0 = System.nanoTime();
        double[] off = DeepCheck.solve(rig.model, rig.cur, a, DeepCheck.BUDGET_NANOS, null);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertNotNull(off);
        a.solvedOffset = off;
        assertTrue(a.solvedAnywhere());
        assertEquals("took " + ms + " ms", -1, a.solvedFailedTick());
        assertTrue("tick 0 offset " + off[0], off[0] >= -a.margin - 1e-9);
        assertTrue("took " + ms + " ms", ms < 10_000L);
    }

    @Test
    public void aRuinedTurnIsLostAtOrAfterTheRuinAndNeverBefore() {
        Rig rig = new Rig();
        int jump = -1;
        for (int t = 0; t < rig.n && jump < 0; t++) if (rig.cur.jumpTicks[t]) jump = t;
        int ruin = jump + 2;
        double[] yaws = rig.cur.facing.clone();
        for (int t = ruin; t < rig.n; t++) yaws[t] += 90.0;
        assumeTrue("the pixel-exact reference must land for this test", rig.attempt(rig.cur.facing.clone()).landed);
        TurnAttempt a = rig.attempt(yaws);
        assertTrue(!a.landed);
        double[] off = DeepCheck.solve(rig.model, rig.cur, a, DeepCheck.BUDGET_NANOS, null);
        assertNotNull(off);
        a.solvedOffset = off;
        int failed = a.solvedFailedTick();
        assertTrue("failed " + failed, failed > ruin);
        assertTrue("failed " + failed, failed < rig.n);
        assertTrue("ruin offset " + off[ruin], off[ruin] >= 0.0);
        assertTrue("last offset " + off[rig.n - 1], off[rig.n - 1] < 0.0);
        assertEquals(failed, a.failedTick());
    }

    @Test
    public void anAttemptWithoutStateIsNotSolved() {
        Rig rig = new Rig();
        TurnAttempt a = new TurnAttempt(1, 0, rig.cur.facing.clone(), rig.n, true, true, false, "", -0.1, -1, -1, 0, 0, 0, null, null, null, false, Double.NaN, null);
        assertNull(DeepCheck.solve(rig.model, rig.cur, a, DeepCheck.BUDGET_NANOS, null));
        assertNull(a.forecast);
    }
}
