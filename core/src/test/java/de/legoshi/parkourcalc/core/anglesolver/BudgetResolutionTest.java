package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BudgetResolutionTest {

    @Test
    public void fastAndCustomDefaultsHaveNoDeadline() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.FAST);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
        LongRunSolver.LongRunConfig lr = AngleSolverEngine.longRunConfigFor(s);
        assertEquals(10, lr.window());
        assertEquals(3, lr.commit());
    }

    @Test
    public void timeBudgetBecomesANanosecondDeadlinePerTier() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        s.getSolveBudget().setTimeBudgetSeconds(30);
        assertEquals(30_000_000_000L, AngleSolverEngine.deadlineNanosFor(s));
        s.getSolveBudget().setTimeBudgetSeconds(0);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        s.getSolveBudget().setTimeBudgetSeconds(30);
        assertEquals("Optimize uses its own knob, not the Custom time budget",
                10_000_000_000L, AngleSolverEngine.deadlineNanosFor(s));
        s.setOptimizeSeconds(25);
        assertEquals(25_000_000_000L, AngleSolverEngine.deadlineNanosFor(s));
        s.setEffort(AngleSolverState.Effort.FAST);
        assertEquals(0L, AngleSolverEngine.deadlineNanosFor(s));
    }

    @Test
    public void optimizeResolvesToExhaustiveStages() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        assertTrue(AngleSolverEngine.ilsExhaustiveFor(s));
        assertFalse(AngleSolverEngine.stopOnFeasibleFor(s));
    }

    @Test
    public void stopOnFeasibleIsForcedPerTier() {
        AngleSolverState s = new AngleSolverState();
        s.setEffort(AngleSolverState.Effort.FAST);
        assertTrue("Fast always stops at the first feasible", AngleSolverEngine.stopOnFeasibleFor(s));
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        s.setStopOnFeasible(true);
        assertFalse("Optimize never stops early", AngleSolverEngine.stopOnFeasibleFor(s));
        s.setEffort(AngleSolverState.Effort.CUSTOM);
        assertTrue("Custom follows the toggle", AngleSolverEngine.stopOnFeasibleFor(s));
        s.setStopOnFeasible(false);
        assertFalse(AngleSolverEngine.stopOnFeasibleFor(s));
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
