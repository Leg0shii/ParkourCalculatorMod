package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RazorLegalReplayTest {

    @Test
    public void legalAttemptReplaysAtRecordedShortfall() {
        checkAttempt("razor-rung-legal-attempt", 9.683582974e-5);
    }

    @Test
    public void wrap720AttemptReplaysAtRecordedShortfall() {
        checkAttempt("razor-rung-legal-attempt-wrap720", 9.704755232e-5);
    }

    @Test
    public void turn360AttemptReplaysAtRecordedShortfall() {
        checkAttempt("razor-rung-legal-attempt-turn360", 2.121321382e-4);
    }

    private void checkAttempt(String capture, double recordedShortfall) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        assertNotNull(capture + ": failed to parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec proofSpec = engine.debugBuildSpec();
        assertNotNull(capture + ": no spec", proofSpec);
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(proofSpec);
        JumpSpec spec = patch.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        double[] gf = new double[n];
        List<InputRow> rows = inputs.getRows();
        float entity = sc.startYaw;
        for (int k = 0; k < n; k++) {
            InputRow row = rows.get(k);
            if (row.isYawLocked()) {
                entity = row.getYaw();
            } else {
                entity = entity + row.getYaw();
            }
            gf[k] = entity;
        }

        String[] whyNot = new String[1];
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(spec.constraints, spec.objective, whyNot);
        assertNotNull(capture + ": no goal wall: " + whyNot[0], goal);
        assertEquals("X@49lo", goal.name);
        List<JumpConstraint> hard = new ArrayList<JumpConstraint>(spec.constraints);
        hard.remove(goal);
        JumpSpec legalSpec = new JumpSpec(sc, hard, spec.objective);

        ForwardPath p = model.forward(sc, gf);
        double hardViol = JumpConstraintCompiler.compile(legalSpec).maxViolation(gf, p);
        double objX = p.getPos(spec.objective.tick, spec.objective.axis);
        double shortfall = goal.rhs - objX;
        System.out.printf(Locale.ROOT, "REPLAY %-36s hardViol=%.9e shortfall=%.12e recorded=%.12e drift=%+.3e%n",
                capture, hardViol, shortfall, recordedShortfall, shortfall - recordedShortfall);
        assertTrue(capture + ": hard walls must replay feasible, viol=" + hardViol, hardViol <= 0.0);
        assertTrue(capture + ": shortfall drifted from the recorded value: " + shortfall,
                Math.abs(shortfall - recordedShortfall) <= 1.0e-9);
    }
}
