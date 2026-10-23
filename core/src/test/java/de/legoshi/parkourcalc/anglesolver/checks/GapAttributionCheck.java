package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The reported dual-bound gap of a cold Optimize is honest: the credited yaws re-simulate byte-exact feasible
 *  to the reported objective, the printed gap is never negative, and when the answer sits short of a
 *  byte-exact-attainable {@code refObjective} the gap is at least that true shortfall (no false-tight
 *  certificate). {@code optimizeSeconds} is the Optimize budget (default 10). */
final class GapAttributionCheck implements ProblemCheck {

    private static final double BYTE_EXACT_FEAS_TOL = 0.0;
    private static final double REPORTED_OBJECTIVE_MATCH_TOL = 1.0e-9;
    private static final double GAP_NONNEGATIVE_TOL = 1.0e-9;
    private static final double GAP_ATTRIBUTION_DISPLAY_ROUNDING_TOL = 1.0e-4;

    @Override
    public void run(ProblemFixture pf) {
        String name = pf.name;
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        rig.state.setOptimizeSeconds(pf.expect.or(pf.expect.optimizeSeconds, 10));
        rig.state.clearResult();
        SolveResult result = rig.solve(pf.expect.timeoutMs(120_000L)).result;
        assertNotNull(name + ": engine returned no result", result);

        JumpSpec solvedSpec = rig.engine.lastSpecDebug();
        assertNotNull(name + ": engine did not retain the solved spec", solvedSpec);
        JumpPhysicsInputs committedStart = solvedSpec.asScenario();
        double[] creditedYaws = Paths.resultYaws(result);
        assertTrue(name + ": result carries no credited yaws", creditedYaws.length > 0);

        double[] gameFacings = committedStart.toGameFacings(creditedYaws);
        ForwardPath reSimPath = pf.model.forward(committedStart, gameFacings);
        double reSimViolation = JumpConstraintCompiler.compile(solvedSpec).maxViolation(gameFacings, reSimPath);
        double reSimObjective = solvedSpec.objective.evaluate(reSimPath);
        double reportedObjective = result.getObjectiveValue();

        assertTrue(name + ": credited yaws are not byte-exact feasible (viol=" + reSimViolation + ")",
                reSimViolation <= BYTE_EXACT_FEAS_TOL);
        assertEquals(name + ": credited yaws do not re-simulate to the reported objective",
                reportedObjective, reSimObjective, REPORTED_OBJECTIVE_MATCH_TOL);

        String gapText = Paths.detail(result, "Dual bound gap");
        double reportedGap = gapText == null ? Double.NaN : Double.parseDouble(gapText);
        System.out.printf("GAP %-36s obj=%.9f gap=%s%n", name, reportedObjective, gapText);
        if (Double.isNaN(reportedGap)) return;

        assertTrue(name + ": reported dual bound gap must never print negative (was " + reportedGap + ")",
                reportedGap >= -GAP_NONNEGATIVE_TOL);

        if (pf.expect.refObjective == null) return;
        double trueObjectiveGapToReference = Paths.shortfall(solvedSpec.objective.sense, reportedObjective, pf.expect.refObjective);
        if (trueObjectiveGapToReference > 0.0) {
            assertTrue(name + ": reported gap " + reportedGap + " reads tighter than the answer's true objective gap "
                            + trueObjectiveGapToReference + " to a byte-exact-attainable reference (false-tight certificate)",
                    reportedGap + GAP_ATTRIBUTION_DISPLAY_ROUNDING_TOL >= trueObjectiveGapToReference);
        }
    }
}
