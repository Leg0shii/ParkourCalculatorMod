package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class GraphPathObjectiveGateTest {

    private static final class Solved {
        double engineObj;
        double recomputeObj;
        double violation;
        boolean success;
    }

    private static Solved solveThroughGraph(String capture, AngleSolverState.Effort effort,
                                            int optimizeSeconds, long timeoutMs) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(effort);
        state.setOptimizeSeconds(optimizeSeconds);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> {
        }, model);
        engine.solve();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (state.getResult() == null && System.nanoTime() < deadline) {
            engine.poll();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull(capture + ": engine returned no result within " + timeoutMs + "ms", r);

        JumpSpec spec = engine.lastSpecDebug();
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws = new double[r.getYaws().size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = r.getYaws().get(k).yaw;
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        Solved s = new Solved();
        s.engineObj = r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
        s.recomputeObj = path.getPos(spec.objective.tick, spec.objective.axis);
        s.violation = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        s.success = r.isSuccess();
        System.out.printf("[GRAPH-GATE] %-24s eff=%-8s engineObj=%.9f recompute=%.9f (div=%.2e) viol=%.3e success=%b%n",
                capture, effort, s.engineObj, s.recomputeObj, Math.abs(s.engineObj - s.recomputeObj),
                s.violation, s.success);
        return s;
    }

    @Test
    public void j021GraphSolveReachesResidualOptimum() {
        Solved s = solveThroughGraph("j021-rinav1-01", AngleSolverState.Effort.THOROUGH, 12, 60_000L);
        assertTrue("j021 engine did not report success", s.success);
        assertTrue("j021 shipped objective below the deterministic-optimum gate (engineObj=" + s.engineObj
                + "); the optimize pipeline lost its improve stage", s.engineObj >= 1067.8637);
    }

    @Test
    public void loopmmGraphSolveLandsPerEngine() {
        Solved s = solveThroughGraph("loopmm-3jump-lands", AngleSolverState.Effort.THOROUGH, 60, 120_000L);
        assertTrue("loopmm engine did not report success", s.success);
        assertTrue("loopmm shipped objective misses the -279.3 block edge (engineObj=" + s.engineObj + ")",
                s.engineObj >= -279.3);
        assertTrue("loopmm sphere-snap result is yaw-lock-dependent: a naive yaw re-derivation gives recompute="
                + s.recomputeObj + " (div=" + Math.abs(s.engineObj - s.recomputeObj) + "); landing requires playback "
                + "to honor the yaw-lock, so VERIFY IN-GAME. Divergence must stay bounded",
                Math.abs(s.engineObj - s.recomputeObj) < 1.0e-3);
    }

    @Test
    public void graphSolveIsReproducibleAcrossRepeats() {
        Solved a = solveThroughGraph("j021-rinav1-01", AngleSolverState.Effort.THOROUGH, 12, 60_000L);
        Solved b = solveThroughGraph("j021-rinav1-01", AngleSolverState.Effort.THOROUGH, 12, 60_000L);
        double tol = 1.0e-2;
        assertTrue("shipped objective not reproducible across repeats within " + tol + " ("
                + a.engineObj + " vs " + b.engineObj + ")", Math.abs(a.engineObj - b.engineObj) < tol);
        assertTrue("recompute not reproducible across repeats within " + tol + " ("
                + a.recomputeObj + " vs " + b.recomputeObj + ")", Math.abs(a.recomputeObj - b.recomputeObj) < tol);
    }
}
