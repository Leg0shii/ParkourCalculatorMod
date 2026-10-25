package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.StrafeSideRealization;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** gh-338: a Force 45 capture with a flick over 45 deg solves, the strafe side pass puts at least
 *  {@code minFlipped} ticks on the other side, the realized facings carry less anchored yaw jerk than the
 *  solved ones, the realized path is feasible byte-exact with the shipped objective, and Apply writes the
 *  chosen A/D per tick with the shifted yaws. Sidecar: {@code minFlipped}. */
final class StrafeSideCheck implements ProblemCheck {

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setStrafeSide(AngleSolverState.StrafeSide.BOTH);
        ProblemFixture.Run run = rig.solve(SolveCheck.timeout(pf));
        SolveResult r = run.result;
        assertNotNull(name + ": no result after solve", r);
        assertTrue(name + ": solver met " + r.getMet() + "/" + r.getTotal() + " constraints", r.isSuccess());
        int[] sides = r.getStrafeSides();
        assertNotNull(name + ": the solve adopted no strafe side realization", sides);
        JumpSpec spec = run.engine.lastSpecDebug();
        JumpPhysicsInputs sc = spec.asScenario();
        int base = sc.strafeSign;
        int flipped = 0;
        for (int t = 0; t < sides.length; t++) {
            if (sides[t] == base) continue;
            flipped++;
            assertTrue(name + ": tick " + t + " flipped without being a strafe tick", sc.strafeAt(t));
        }
        int minFlipped = pf.expect.or(pf.expect.minFlipped, 1);
        assertTrue(name + ": " + flipped + " ticks on the other side, expected at least " + minFlipped,
                flipped >= minFlipped);

        double[] solved = Paths.resultYaws(r);
        double[] realized = StrafeSideRealization.realizedYaws(solved, sides, base);
        boolean freeStart = run.engine.debugFreeStartYaw();
        double before = StrafeSideRealization.wiggle(solved, sc.startYaw, freeStart);
        double after = StrafeSideRealization.wiggle(realized, sc.startYaw, freeStart);
        System.out.printf("STRAFESIDE %-22s flipped=%d/%d jerk %.1f -> %.1f deg obj=%.9f%n",
                name, flipped, sides.length, before, after, r.getObjectiveValue());
        assertTrue(name + ": realized jerk " + after + " not below the solved " + before, after < before);

        JumpPhysicsInputs withSides = sc.copy();
        withSides.strafeSignPerTick = sides;
        double[] gf = withSides.toGameFacings(realized);
        ForwardPath path = pf.model.forward(withSides, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        assertTrue(name + ": realized path violates by " + viol, viol <= 0.0);
        assertEquals(name + ": shipped objective is not the realized path's", spec.objective.evaluate(path),
                r.getObjectiveValue(), 0.0);

        boolean[] force45 = run.engine.lastForce45MaskDebug();
        assertNotNull(name + ": engine kept no Force 45 mask", force45);
        rig.engine.apply();
        List<InputRow> rows = rig.inputs.getRows();
        int startTick = r.getStartTick() - 1;
        double prev = freeStart ? realized[0] : sc.startYaw;
        for (int k = 0; k < sides.length; k++) {
            InputRow row = rows.get(startTick + k);
            if (sc.strafeAt(k)) {
                assertEquals(name + ": A at T" + (startTick + k + 1), sides[k] > 0, row.isKeyActive(InputRow.Key.A));
                assertEquals(name + ": D at T" + (startTick + k + 1), sides[k] < 0, row.isKeyActive(InputRow.Key.D));
            } else if (force45[k]) {
                assertFalse(name + ": strafe key on a W-only tick T" + (startTick + k + 1),
                        row.isKeyActive(InputRow.Key.A) || row.isKeyActive(InputRow.Key.D));
            }
            Float yaw = row.getYaw();
            assertNotNull(name + ": no yaw written at T" + (startTick + k + 1), yaw);
            double abs = row.isYawLocked() ? yaw : prev + yaw;
            if (!(freeStart && k == 0)) {
                assertEquals(name + ": yaw at T" + (startTick + k + 1), 0.0,
                        Angles.wrapDelta(abs - realized[k]), 1e-3);
            }
            prev = abs;
        }
        oneSide(pf, AngleSolverState.StrafeSide.A);
        oneSide(pf, AngleSolverState.StrafeSide.D);
    }

    private static void oneSide(ProblemFixture pf, AngleSolverState.StrafeSide side) {
        String name = pf.name + " on " + side.label;
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setStrafeSide(side);
        ProblemFixture.Run run = rig.solve(SolveCheck.timeout(pf));
        SolveResult r = run.result;
        assertNotNull(name + ": no result after solve", r);
        assertTrue(name + ": solver met " + r.getMet() + "/" + r.getTotal() + " constraints", r.isSuccess());
        assertEquals(name + ": no strafe side pass on one side", null, r.getStrafeSides());
        JumpPhysicsInputs sc = run.engine.lastSpecDebug().asScenario();
        assertEquals(name + ": scenario side", side == AngleSolverState.StrafeSide.D ? -1 : 1, sc.strafeSign);
        rig.engine.apply();
        List<InputRow> rows = rig.inputs.getRows();
        int startTick = r.getStartTick() - 1;
        for (int k = 0; k < sc.numTicks; k++) {
            if (!sc.strafeAt(k)) continue;
            InputRow row = rows.get(startTick + k);
            assertEquals(name + ": A at T" + (startTick + k + 1), side == AngleSolverState.StrafeSide.A,
                    row.isKeyActive(InputRow.Key.A));
            assertEquals(name + ": D at T" + (startTick + k + 1), side == AngleSolverState.StrafeSide.D,
                    row.isKeyActive(InputRow.Key.D));
        }
    }
}
