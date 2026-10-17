package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SmoothButtonTest {

    private static final String CAPTURE = "gh398-optimize-2jump";
    private static final String WALLED_CAPTURE = "j008b-2jump";
    private static final long TIMEOUT_MS = 60_000L;

    private final AngleSolverState state = new AngleSolverState();
    private final InputData inputs = new InputData();
    private final AngleSolverEngine engine;

    public SmoothButtonTest() {
        engine = load(CAPTURE);
    }

    private AngleSolverEngine load(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        assertNotNull(capture + ": failed to parse", file);
        SaveIO.applyRowsTo(file, inputs);
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        return new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { },
                ExactJumpModel.forMcVersion(file.mcVersion));
    }

    private SolveResult await(AngleSolverEngine engine) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (state.getResult() == null && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull("no result within " + TIMEOUT_MS + " ms", r);
        return r;
    }

    @Test
    public void smoothRunsOnlyTheSmoothStageOnTheSolvedPathAndStaysLanded() {
        assertFalse("nothing to smooth before a solve", engine.canSmooth());
        engine.solve();
        SolveResult solved = await(engine);
        assertTrue("fast did not land: " + solved.getSolver(), solved.isSuccess());
        assertTrue("a landed solve of the range can be smoothed", engine.canSmooth());

        engine.smooth();
        assertFalse("not while it runs", engine.canSmooth());
        SolveResult smoothed = await(engine);
        assertTrue("smoothing lost the landing: " + smoothed.getSolver(), smoothed.isSuccess());
        assertTrue("the smoothed chain starts from the incumbent: " + smoothed.getSolver(),
                smoothed.getSolver() != null && smoothed.getSolver().startsWith("incumbent"));
        SolveRunRecord rec = engine.lastRunRecord();
        assertNotNull(rec);
        assertEquals("only the smooth stage ran", 1, rec.nodes.size());
        assertEquals("smooth", rec.nodes.get(0).id);
        assertTrue("smoothable again afterwards", engine.canSmooth());

        state.setLandingTick(state.getLandingTick() - 1);
        assertFalse("a changed range has no solved path", engine.canSmooth());
    }

    @Test
    public void smoothingAgainStartsFromTheUnsmoothedSolve() {
        AngleSolverEngine walled = load(WALLED_CAPTURE);
        JumpSpec spec = walled.debugBuildSpec();
        assertNotNull(spec);
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(spec.constraints, spec.objective);
        assertNotNull(WALLED_CAPTURE + " has a goal wall on the objective axis", wall);

        walled.solve();
        SolveResult solved = await(walled);
        assertTrue("fast did not land: " + solved.getSolver(), solved.isSuccess());
        double solvedObjective = solved.getObjectiveValue();
        double offset = spec.objective.sense == Objective.Sense.MAX ? solvedObjective - wall.rhs : wall.rhs - solvedObjective;
        assertTrue("the solve has an offset to give back: " + offset, offset > 1e-3);

        state.setSmoothGiveBack(0.5);
        walled.smooth();
        SolveResult once = await(walled);
        assertTrue("smoothing lost the landing: " + once.getSolver(), once.isSuccess());
        assertTrue("half the offset was available: " + once.getObjectiveValue(),
                givenBack(spec, solvedObjective, once.getObjectiveValue()) <= 0.5 * offset + 1e-9);
        assertTrue("the smoothing used some of it: " + once.getObjectiveValue(),
                givenBack(spec, solvedObjective, once.getObjectiveValue()) > 1e-4);
        walled.apply();
        Float[] onceYaws = yawsOf();

        walled.smooth();
        SolveResult twice = await(walled);
        assertTrue("smoothing again lost the landing: " + twice.getSolver(), twice.isSuccess());
        walled.apply();
        assertEquals("smoothing twice equals smoothing once", once.getObjectiveValue(), twice.getObjectiveValue(), 0.0);
        assertArrayEquals("smoothing twice equals smoothing once", onceYaws, yawsOf());

        state.setSmoothGiveBack(0.0);
        walled.smooth();
        SolveResult none = await(walled);
        assertTrue("smoothing at 0 % lost the landing: " + none.getSolver(), none.isSuccess());
        assertTrue("0 % give-back keeps the solve's objective, not the smoothed one: " + none.getObjectiveValue(),
                givenBack(spec, solvedObjective, none.getObjectiveValue()) <= 1e-9);
    }

    private static double givenBack(JumpSpec spec, double solved, double smoothed) {
        return spec.objective.sense == Objective.Sense.MAX ? solved - smoothed : smoothed - solved;
    }

    private Float[] yawsOf() {
        List<InputRow> rows = inputs.getRows();
        Float[] yaws = new Float[state.getLandingTick() - state.getStartTick()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = rows.get(state.getStartTick() + k).getYaw();
        return yaws;
    }
}
