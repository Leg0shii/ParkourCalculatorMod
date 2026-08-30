package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class GapAttributionTest {

    private static final long PER_JUMP_TIMEOUT_MS = 120_000L;
    private static final int OPTIMIZE_SECONDS = 10;

    private static final double BYTE_EXACT_FEAS_TOL = 0.0;
    private static final double REPORTED_OBJECTIVE_MATCH_TOL = 1.0e-9;
    private static final double GAP_NONNEGATIVE_TOL = 1.0e-9;
    private static final double GAP_ATTRIBUTION_DISPLAY_ROUNDING_TOL = 1.0e-4;

    @Test
    public void j1150NixNeoGapIsHonestlyAttributed() {
        assertReportedGapIsHonest("d11", "j1150-2x2bm_Nix_Neo", true, -2805.2980581942234);
    }

    @Test
    public void j155GapIsNeverNegativeOrFalseTight() {
        assertReportedGapIsHonest("d11", "j155_4jmm_3bcmm_4.9375b", false, 4984.76318619175);
    }

    private void assertReportedGapIsHonest(String dir, String resource, boolean maximize, double referenceObjective) {
        SaveFile file = HpkStartBenchmark.loadCapture(dir, resource);
        Solved solved = solveColdKeepingEngine(file);
        SolveResult result = solved.result;
        assertNotNull(resource + ": engine returned no result", result);

        JumpSpec solvedSpec = solved.engine.lastSpecDebug();
        assertNotNull(resource + ": engine did not retain the solved spec", solvedSpec);
        JumpPhysicsInputs committedStart = solvedSpec.asScenario();
        double[] creditedYaws = creditedYawsOf(result);
        assertTrue(resource + ": result carries no credited yaws", creditedYaws.length > 0);

        double[] gameFacings = committedStart.toGameFacings(creditedYaws);
        ForwardPath reSimPath = solved.model.forward(committedStart, gameFacings);
        double reSimViolation = JumpConstraintCompiler.compile(solvedSpec).maxViolation(gameFacings, reSimPath);
        double reSimObjective = solvedSpec.objective.evaluate(reSimPath);
        double reportedObjective = result.getObjectiveValue();

        assertTrue(resource + ": credited yaws are not byte-exact feasible (viol=" + reSimViolation + ")",
                reSimViolation <= BYTE_EXACT_FEAS_TOL);
        assertEquals(resource + ": credited yaws do not re-simulate to the reported objective",
                reportedObjective, reSimObjective, REPORTED_OBJECTIVE_MATCH_TOL);

        double reportedGap = reportedDualBoundGap(result);
        if (Double.isNaN(reportedGap)) return;

        assertTrue(resource + ": reported dual bound gap must never print negative (was " + reportedGap + ")",
                reportedGap >= -GAP_NONNEGATIVE_TOL);

        double trueObjectiveGapToReference = maximize ? referenceObjective - reportedObjective
                : reportedObjective - referenceObjective;
        if (trueObjectiveGapToReference > 0.0) {
            assertTrue(resource + ": reported gap " + reportedGap + " reads tighter than the answer's true"
                            + " objective gap " + trueObjectiveGapToReference + " to a byte-exact-attainable"
                            + " reference (false-tight certificate)",
                    reportedGap + GAP_ATTRIBUTION_DISPLAY_ROUNDING_TOL >= trueObjectiveGapToReference);
        }
    }

    private static double reportedDualBoundGap(SolveResult result) {
        for (SolveResult.Detail detail : result.getDetails()) {
            if ("Dual bound gap".equals(detail.label)) {
                return Double.parseDouble(detail.value);
            }
        }
        return Double.NaN;
    }

    private static double[] creditedYawsOf(SolveResult result) {
        double[] yaws = new double[result.getYaws().size()];
        for (int i = 0; i < yaws.length; i++) yaws[i] = result.getYaws().get(i).yaw;
        return yaws;
    }

    private static final class Solved {
        final SolveResult result;
        final AngleSolverEngine engine;
        final ExactJumpModel model;

        Solved(SolveResult result, AngleSolverEngine engine, ExactJumpModel model) {
            this.result = result;
            this.engine = engine;
            this.model = model;
        }
    }

    private static Solved solveColdKeepingEngine(SaveFile file) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.THOROUGH);
        state.setOptimizeSeconds(OPTIMIZE_SECONDS);
        state.clearResult();
        BoxController boxes = Fixtures.buildBoxes(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);

        engine.solve();
        long deadline = System.currentTimeMillis() + PER_JUMP_TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        engine.poll();
        return new Solved(state.getResult(), engine, model);
    }
}
