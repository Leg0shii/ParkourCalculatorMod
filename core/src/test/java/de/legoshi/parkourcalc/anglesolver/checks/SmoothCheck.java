package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The Smooth button on a landed Fast solve: it is offered only for a landed solve of the current range, runs
 *  only the smooth stage from the incumbent, keeps the landing, says it smoothed and why it stopped, and is
 *  withdrawn once the range changes. */
final class SmoothCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        long timeout = pf.expect.timeoutMs(60_000L);
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        assertFalse(name + ": nothing to smooth before a solve", rig.engine.canSmooth());
        SolveResult solved = rig.solve(timeout).result;
        assertNotNull(name + ": no result within " + timeout + " ms", solved);
        assertTrue(name + ": fast did not land: " + solved.getSolver(), solved.isSuccess());
        assertTrue(name + ": a landed solve of the range can be smoothed", rig.engine.canSmooth());

        rig.engine.smooth();
        assertFalse(name + ": not while it runs", rig.engine.canSmooth());
        SolveResult smoothed = awaitSmooth(name, rig, timeout);
        assertTrue(name + ": smoothing lost the landing: " + smoothed.getSolver(), smoothed.isSuccess());
        assertTrue(name + ": the smoothed chain starts from the incumbent: " + smoothed.getSolver(),
                smoothed.getSolver() != null && smoothed.getSolver().startsWith("incumbent"));
        SolveRunRecord rec = rig.engine.lastRunRecord();
        assertNotNull(name + ": no run record", rec);
        assertEquals(name + ": only the smooth stage ran", 1, rec.nodes.size());
        assertEquals(name + ": the stage that ran", "smooth", rec.nodes.get(0).id);
        assertTrue(name + ": smoothable again afterwards", rig.engine.canSmooth());
        assertTrue(name + ": the result says that it was smoothed", smoothed.isSmoothed());
        assertNotNull(name + ": the result says why the smoothing stopped", smoothed.getSmoothStop());
        assertTrue(name + ": a Smooth button run has no reason to be cancelled: " + smoothed.getSmoothStop(),
                !"cancelled".equals(smoothed.getSmoothStop()));
        assertNotNull(name + ": the details carry the smoothing row", Paths.detail(smoothed, "Smoothing"));
        assertFalse(name + ": a plain solve is not reported as smoothed", solved.isSmoothed());

        if (pf.expect.flag(pf.expect.resmooth, false)) resmooth(pf, timeout);

        rig.state.setLandingTick(rig.state.getLandingTick() - 1);
        assertFalse(name + ": a changed range has no solved path", rig.engine.canSmooth());
    }

    private static void resmooth(ProblemFixture pf, long timeout) {
        String name = pf.name;
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        JumpSpec spec = rig.spec();
        assertNotNull(name + ": no spec", spec);
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(spec.constraints, spec.objective);
        assertNotNull(name + ": a goal wall on the objective axis", wall);

        SolveResult solved = rig.solve(timeout).result;
        assertNotNull(name + ": no result within " + timeout + " ms", solved);
        assertTrue(name + ": fast did not land: " + solved.getSolver(), solved.isSuccess());
        double solvedObjective = solved.getObjectiveValue();
        double offset = spec.objective.sense == Objective.Sense.MAX
                ? solvedObjective - wall.rhs : wall.rhs - solvedObjective;
        assertTrue(name + ": the solve has an offset to give back: " + offset, offset > 1e-3);

        rig.state.setSmoothGiveBack(0.5);
        rig.engine.smooth();
        SolveResult once = awaitSmooth(name, rig, timeout);
        assertTrue(name + ": smoothing lost the landing: " + once.getSolver(), once.isSuccess());
        assertTrue(name + ": half the offset was available: " + once.getObjectiveValue(),
                givenBack(spec, solvedObjective, once.getObjectiveValue()) <= 0.5 * offset + 1e-9);
        assertTrue(name + ": the smoothing used some of it: " + once.getObjectiveValue(),
                givenBack(spec, solvedObjective, once.getObjectiveValue()) > 1e-4);
        rig.engine.apply();
        Float[] onceYaws = yawsOf(rig);

        rig.engine.smooth();
        SolveResult twice = awaitSmooth(name, rig, timeout);
        assertTrue(name + ": smoothing again lost the landing: " + twice.getSolver(), twice.isSuccess());
        rig.engine.apply();
        assertEquals(name + ": smoothing twice equals smoothing once",
                once.getObjectiveValue(), twice.getObjectiveValue(), 0.0);
        assertArrayEquals(name + ": smoothing twice equals smoothing once", onceYaws, yawsOf(rig));

        rig.state.setSmoothGiveBack(0.0);
        rig.engine.smooth();
        SolveResult none = awaitSmooth(name, rig, timeout);
        assertTrue(name + ": smoothing at 0 % lost the landing: " + none.getSolver(), none.isSuccess());
        assertTrue(name + ": 0 % give-back keeps the solve's objective, not the smoothed one: "
                + none.getObjectiveValue(), givenBack(spec, solvedObjective, none.getObjectiveValue()) <= 1e-9);
    }

    private static SolveResult awaitSmooth(String name, ProblemFixture.Rig rig, long timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (rig.state.getResult() == null && System.currentTimeMillis() < deadline) {
            rig.engine.poll();
            sleep(10);
        }
        rig.engine.poll();
        SolveResult smoothed = rig.state.getResult();
        assertNotNull(name + ": no smoothed result within " + timeout + " ms", smoothed);
        return smoothed;
    }

    private static double givenBack(JumpSpec spec, double solved, double smoothed) {
        return spec.objective.sense == Objective.Sense.MAX ? solved - smoothed : smoothed - solved;
    }

    private static Float[] yawsOf(ProblemFixture.Rig rig) {
        List<InputRow> rows = rig.inputs.getRows();
        Float[] yaws = new Float[rig.state.getLandingTick() - rig.state.getStartTick()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = rows.get(rig.state.getStartTick() + k).getYaw();
        return yaws;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
