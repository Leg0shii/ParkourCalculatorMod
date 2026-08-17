package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class DirectionFallbackReportTest {

    @Test
    public void fallbackReportNamesAForeignDirectionAndStaysFeasible() {
        ProblemFixture pf = ProblemFixture.load("solve", "j024-bfly-goal-direction");
        for (AngleSolverState.Axis axis : AngleSolverState.Axis.values()) {
            for (AngleSolverState.Goal goal : AngleSolverState.Goal.values()) {
                String dir = axis + "/" + goal;
                JumpSpec spec = pf.specFor(axis, goal);
                JumpPhysicsInputs sc = spec.asScenario();
                Objective[] fallback = new Objective[1];
                double[] gf = LongRunSolver.solve(pf.model, spec, 0.0, new AtomicBoolean(false),
                        LongRunSolver.LongRunConfig.defaults(), fallback);
                assertNotNull(dir + ": receding horizon must land the capture", gf);
                double[] replay = sc.toGameFacings(Angles.wrapAll(gf));
                double viol = JumpConstraintCompiler.compile(spec)
                        .maxViolation(replay, pf.model.forward(sc, replay));
                assertTrue(dir + ": reported solution not feasible (viol=" + viol + ")", viol <= 0.0);
                System.out.printf("LRS %-8s fallback=%s%n", dir,
                        fallback[0] == null ? "-" : fallback[0].axis + "/" + fallback[0].sense);
                if (fallback[0] != null) {
                    assertFalse(dir + ": fallback must name a different direction",
                            fallback[0].axis == spec.objective.axis && fallback[0].sense == spec.objective.sense);
                }
            }
        }
    }
}
