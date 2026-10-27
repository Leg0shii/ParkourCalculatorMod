package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.StateOverride;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.TurnShape;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        if (pf.expect.flag(pf.expect.smoothInputs, false)) smoothInputs(pf, timeout);

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

    private static void smoothInputs(ProblemFixture pf, long timeout) {
        String name = pf.name + " (inputs)";
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        JumpSpec spec = rig.spec();
        assertNotNull(name + ": no spec", spec);
        double anchor = spec.asScenario().startYaw;
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(spec.constraints, spec.objective);

        SolveResult solved = rig.solve(timeout).result;
        assertNotNull(name + ": no result within " + timeout + " ms", solved);
        assertTrue(name + ": fast did not land: " + solved.getSolver(), solved.isSuccess());
        double solvedObjective = solved.getObjectiveValue();
        assertEquals(name + ": the mode defaults to Turn only", AngleSolverState.SmoothMode.TURN, rig.state.getSmoothMode());
        long plainBudget = rig.engine.smoothBudgetNanos();
        rig.engine.smooth();
        SolveResult plain = awaitSmooth(name, rig, timeout);
        assertTrue(name + ": Turn only lost the landing: " + plain.getSolver(), plain.isSuccess());
        assertNull(name + ": Turn only never changes keys", plain.getInputChanges());
        TurnShape plainShape = TurnShape.of(anchor, Paths.resultYaws(plain));

        rig.state.setSmoothMode(AngleSolverState.SmoothMode.INPUTS);
        assertEquals(name + ": the inputs mode gets three times the budget",
                AngleSolverEngine.SMOOTH_INPUTS_FACTOR * plainBudget, rig.engine.smoothBudgetNanos());
        assertTrue(name + ": smoothable in the inputs mode", rig.engine.canSmooth());
        rig.engine.smooth();
        long smoothTimeout = timeout + rig.engine.smoothBudgetNanos() / 1_000_000L;
        SolveResult smoothed = awaitSmooth(name, rig, smoothTimeout);
        assertTrue(name + ": the inputs mode lost the landing: " + smoothed.getSolver(), smoothed.isSuccess());
        assertTrue(name + ": the result says that it was smoothed", smoothed.isSmoothed());
        assertEquals(name + ": every constraint met", smoothed.getTotal(), smoothed.getMet());
        SolveRunRecord rec = rig.engine.lastRunRecord();
        assertNotNull(name + ": no run record", rec);
        assertEquals(name + ": only the inputs smooth stage ran", 1, rec.nodes.size());
        assertEquals(name + ": the stage that ran", "smooth", rec.nodes.get(0).id);
        assertNotNull(name + ": the details say whether inputs changed", Paths.detail(smoothed, "Inputs changed"));

        if (wall != null) {
            double offset = spec.objective.sense == Objective.Sense.MAX
                    ? solvedObjective - wall.rhs : wall.rhs - solvedObjective;
            double given = givenBack(spec, solvedObjective, smoothed.getObjectiveValue());
            assertTrue(name + ": gave back " + given + " of an offset of " + offset,
                    given <= rig.state.getSmoothGiveBack() * offset + 1e-9);
        }

        TurnShape shape = TurnShape.of(anchor, Paths.resultYaws(smoothed));
        System.out.printf("SMOOTHINPUTS %-22s plain %s inputs %s changed=%s%n", pf.name, plainShape, shape,
                Paths.detail(smoothed, "Inputs changed"));
        assertTrue(name + ": more reversals than Turn only: " + shape + " vs " + plainShape,
                shape.reversals <= plainShape.reversals);
        assertTrue(name + ": more acceleration flips than Turn only: " + shape + " vs " + plainShape,
                shape.reversals < plainShape.reversals || shape.accelFlips <= plainShape.accelFlips);

        TickInputs[] changes = smoothed.getInputChanges();
        int minChanged = pf.expect.or(pf.expect.minChanged, 1);
        assertNotNull(name + ": no key changed, expected at least " + minChanged, changes);
        int changed = 0;
        for (TickInputs c : changes) if (c != null) changed++;
        assertTrue(name + ": " + changed + " ticks changed, expected at least " + minChanged, changed >= minChanged);

        rig.engine.apply();
        List<InputRow> rows = rig.inputs.getRows();
        int startTick = smoothed.getStartTick() - 1;
        for (int k = 0; k < changes.length; k++) {
            TickInputs c = changes[k];
            if (c == null) continue;
            int tick = startTick + k;
            InputRow row = rows.get(tick);
            String at = name + ": T" + (tick + 1) + " ";
            assertEquals(at + "W", c.w, row.isKeyActive(InputRow.Key.W));
            assertEquals(at + "A", c.a, row.isKeyActive(InputRow.Key.A));
            assertEquals(at + "S", c.s, row.isKeyActive(InputRow.Key.S));
            assertEquals(at + "D", c.d, row.isKeyActive(InputRow.Key.D));
            assertEquals(at + "sprint", c.sprint, row.isKeyActive(InputRow.Key.SPRINT));
            TickConstraints tc = rig.state.tickConstraintsOrNull(tick);
            StateOverride ov = tc == null ? null : tc.getOverride();
            AngleSolverState.InputMode inputs = ov != null && ov.overridesInputs() ? ov.getInputs() : rig.state.getDefaultInputs();
            AngleSolverState.SprintMode sprint = ov != null && ov.overridesSprint() ? ov.getSprint() : rig.state.getDefaultSprint();
            assertEquals(at + "runs its own keys from now on", AngleSolverState.InputMode.KEEP, inputs);
            assertEquals(at + "derives its sprint from now on", AngleSolverState.SprintMode.DERIVE, sprint);
        }
        double[] yaws = Paths.resultYaws(smoothed);
        boolean freeStart = rig.engine.debugFreeStartYaw();
        double prev = freeStart ? yaws[0] : anchor;
        for (int k = 0; k < yaws.length; k++) {
            InputRow row = rows.get(startTick + k);
            Float yaw = row.getYaw();
            assertNotNull(name + ": no yaw written at T" + (startTick + k + 1), yaw);
            double abs = row.isYawLocked() ? yaw : prev + yaw;
            if (!(freeStart && k == 0)) {
                assertEquals(name + ": yaw at T" + (startTick + k + 1), 0.0, Angles.wrapDelta(abs - yaws[k]), 1e-3);
            }
            prev = abs;
        }
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
