package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnytimeRaceTest {

    private static final double SIGMA = 90.0;

    private static JumpSpec singleJumpSpec() {
        ProblemFixture pf = ProblemFixture.load("solve", "j005");
        return pf.solveDirected(30_000L, AngleSolverState.Axis.X, AngleSolverState.Goal.MAX).engine.lastSpecDebug();
    }

    private static ExactJumpModel modelFor(String name) {
        return ProblemFixture.load("solve", name).model;
    }

    private static double violation(ExactJumpModel model, JumpSpec spec, double[] yawsAbsWrapped) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbsWrapped));
        ForwardPath path = model.forward(sc, gf);
        return c.maxViolation(gf, path);
    }

    @Test
    public void largeBudgetSolvesASingleJump() {
        ExactJumpModel model = modelFor("j005");
        JumpSpec spec = singleJumpSpec();
        SolveCore.Budget big = new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
        double[] yaws = SolveCore.optimize(model, spec, big, SIGMA, 0.0, new AtomicBoolean(false));
        assertNotNull(yaws);
        assertTrue("expected a byte-exact feasible solve", violation(model, spec, yaws) <= 0.0);
    }

    @Test
    public void tinyBudgetDegradesGracefully() {
        ExactJumpModel model = modelFor("j005");
        JumpSpec spec = singleJumpSpec();
        SolveCore.Budget tiny = new SolveCore.Budget(1, 500, 1, BucketAscentPolish.FAST);
        double[] yaws = SolveCore.optimize(model, spec, tiny, SIGMA, 0.0, new AtomicBoolean(false));
        assertNotNull(yaws);
    }

    @Test
    public void anytimeRaceKeepsSearchingToTheDeadline() {
        ExactJumpModel model = modelFor("j005");
        JumpSpec spec = singleJumpSpec();
        SolveCore.Budget budget = new SolveCore.Budget(16, 4500, 2, BucketAscentPolish.FAST);

        long budgetMs = 400L;
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        long t0 = System.nanoTime();
        double[] yaws = SolveCore.optimize(model, spec, budget, SIGMA, 0.0, new AtomicBoolean(false), null, deadline);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        assertNotNull(yaws);
        assertTrue("anytime result must be feasible", violation(model, spec, yaws) <= 0.0);
        assertTrue("kept launching batches until ~deadline (was " + elapsedMs + " ms)", elapsedMs >= budgetMs / 2);
        assertTrue("returned within the deadline plus one in-flight batch (was " + elapsedMs + " ms)",
                elapsedMs <= budgetMs + 15_000L);
    }

    @Test
    public void longRunHonorsAWindowCommitOverride() {
        ExactJumpModel model = modelFor("j001");
        JumpSpec spec = ProblemFixture.load("solve", "j001").solve(60_000L).engine.lastSpecDebug();
        // 8x2 is inside the swept-good window x commit grid (docs 3.1), so the override must still solve j001.
        double[] yaws = LongRunSolver.solve(model, spec, 0.0, new AtomicBoolean(false),
                LongRunSolver.LongRunConfig.of(8, 2));
        assertNotNull("the window/commit override must still solve the long run", yaws);
        assertTrue("byte-exact feasible concatenated chain", violation(model, spec, yaws) <= 0.0);
    }
}
