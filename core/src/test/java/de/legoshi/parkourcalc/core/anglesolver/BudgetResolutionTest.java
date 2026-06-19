package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BudgetResolutionTest {

    @Test
    public void fastResolvesToTheShippedFastBudget() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.FAST);
        SolveCore.Budget b = AngleSolverEngine.budgetFor(s);
        assertEquals(16, b.restarts);
        assertEquals(4500, b.maxEval);
        assertEquals(2, b.polishCount);
        assertSame(BucketAscentPolish.FAST, b.polishCfg);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
    }

    @Test
    public void customDefaultsAreByteForByteFast() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        SolveCore.Budget b = AngleSolverEngine.budgetFor(s);
        assertEquals(16, b.restarts);
        assertEquals(4500, b.maxEval);
        assertEquals(2, b.polishCount);
        assertSame(BucketAscentPolish.FAST, b.polishCfg);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
        LongRunSolver.LongRunConfig lr = AngleSolverEngine.longRunConfigFor(s);
        assertEquals(10, lr.window());
        assertEquals(3, lr.commit());
    }

    @Test
    public void customBudgetFlowsIntoTheResolvedBudget() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setRestarts(80);
        s.getSolveBudget().setMaxEval(30000);
        s.getSolveBudget().setPolishCount(12);
        s.getSolveBudget().setPolishDepth(AngleSolverState.PolishDepth.EXHAUSTIVE);
        SolveCore.Budget b = AngleSolverEngine.budgetFor(s);
        assertEquals(80, b.restarts);
        assertEquals(30000, b.maxEval);
        assertEquals(12, b.polishCount);
        assertSame("Exhaustive maps to the THOROUGH schedule", BucketAscentPolish.THOROUGH, b.polishCfg);
    }

    @Test
    public void timeBudgetBecomesANanosecondDeadlineForCustomOnly() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setTimeBudgetSeconds(30);
        assertEquals(30_000_000_000L, AngleSolverEngine.deadlineNanosFor(s));
        s.getSolveBudget().setTimeBudgetSeconds(0);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        s.getSolveBudget().setTimeBudgetSeconds(30);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
    }

    @Test
    public void windowSolverIsOnByDefaultAndTogglesOnlyForCustom() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.FAST);
        assertTrue(AngleSolverEngine.useWindowSolverFor(s));
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        assertTrue(AngleSolverEngine.useWindowSolverFor(s));
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        assertTrue("Custom defaults to the window solver", AngleSolverEngine.useWindowSolverFor(s));
        s.getSolveBudget().setUseWindowSolver(false);
        assertFalse(AngleSolverEngine.useWindowSolverFor(s));
        s.setEffort(AngleSolverState.Effort.FAST);
        assertTrue("non-Custom ignores the toggle", AngleSolverEngine.useWindowSolverFor(s));
    }

    @Test
    public void customWindowAndCommitFlowIntoLongRunConfig() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setWindow(8);
        s.getSolveBudget().setCommit(2);
        LongRunSolver.LongRunConfig lr = AngleSolverEngine.longRunConfigFor(s);
        assertEquals(8, lr.window());
        assertEquals(2, lr.commit());
        s.setEffort(AngleSolverState.Effort.FAST);
        assertEquals(10, AngleSolverEngine.longRunConfigFor(s).window());
    }
}
