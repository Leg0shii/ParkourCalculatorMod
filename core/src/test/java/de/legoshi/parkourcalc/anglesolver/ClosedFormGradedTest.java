package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClosedFormGradedTest {

    @Test
    public void gradedAgreesWithRobust() {
        for (String name : new String[]{"j008-bfneo", "j022-1bmhbfly"}) {
            for (AngleSolverState.Axis axis : AngleSolverState.Axis.values()) {
                for (AngleSolverState.Goal goal : AngleSolverState.Goal.values()) {
                    checkDirection(name, axis, goal);
                }
            }
        }
    }

    private void checkDirection(String name, AngleSolverState.Axis axis, AngleSolverState.Goal goal) {
        ProblemFixture pf = ProblemFixture.load("solve", name);
        ProblemFixture.Run run = pf.solveDirected(60_000L, axis, goal);
        ExactJumpModel exact = pf.model;
        JumpSpec spec = run.engine.lastSpecDebug();
        if (spec == null) return;
        String tag = name + " " + axis + "/" + goal;
        AtomicBoolean cancel = new AtomicBoolean(false);

        double[] robust = ClosedFormSolve.optimizeRobust(exact, spec, 0.0, cancel);
        ClosedFormSolve.Result graded = ClosedFormSolve.optimizeRobustGraded(exact, spec, 0.0, cancel);

        if (robust == null) {
            assertTrue(tag + ": robust=null must not yield a feasible graded result",
                    graded == null || !graded.feasible);
        } else {
            assertNotNull(tag + ": graded must be present when robust certifies", graded);
            assertTrue(tag + ": graded must be feasible when robust certifies", graded.feasible);
            assertEquals(tag + ": certified graded violation must be 0", 0.0, graded.violation, 0.0);
            assertArrayEquals(tag + ": certified yaws must match robust", robust, graded.yaws, 0.0);
        }
    }
}
