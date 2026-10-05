package de.legoshi.parkourcalc.core.anglesolver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BudgetTextTest {

    @Test
    public void parsesSecondsAndMilliseconds() {
        assertEquals(0, BudgetText.parseMs("0"));
        assertEquals(0, BudgetText.parseMs("0 s"));
        assertEquals(250, BudgetText.parseMs("250ms"));
        assertEquals(250, BudgetText.parseMs(" 250 MS "));
        assertEquals(500, BudgetText.parseMs("0.5"));
        assertEquals(500, BudgetText.parseMs("0.5s"));
        assertEquals(500, BudgetText.parseMs("0,5 s"));
        assertEquals(45_000, BudgetText.parseMs("45"));
        assertEquals(45_000, BudgetText.parseMs("45 s"));
    }

    @Test
    public void rejectsGarbage() {
        assertEquals(-1, BudgetText.parseMs(null));
        assertEquals(-1, BudgetText.parseMs(""));
        assertEquals(-1, BudgetText.parseMs("s"));
        assertEquals(-1, BudgetText.parseMs("ms"));
        assertEquals(-1, BudgetText.parseMs("-3"));
        assertEquals(-1, BudgetText.parseMs("fast"));
    }

    @Test
    public void formatsTheShortestExactUnit() {
        assertEquals("0 ms", BudgetText.format(0));
        assertEquals("250 ms", BudgetText.format(250));
        assertEquals("1 s", BudgetText.format(1000));
        assertEquals("2.5 s", BudgetText.format(2500));
        assertEquals("1.234 s", BudgetText.format(1234));
        assertEquals("45 s", BudgetText.format(45_000));
    }

    @Test
    public void budgetSetsTheEffortTierUnlessCustom() {
        AngleSolverState s = new AngleSolverState();
        assertEquals(AngleSolverState.Effort.FAST, s.getEffort());
        s.setBudgetMs(500);
        assertEquals(AngleSolverState.Effort.FAST, s.getEffort());
        s.setBudgetMs(1000);
        assertEquals(AngleSolverState.Effort.THOROUGH, s.getEffort());
        s.setCustomBudget(true);
        s.setBudgetMs(0);
        assertEquals(AngleSolverState.Effort.CUSTOM, s.getEffort());
        s.setCustomBudget(false);
        assertEquals(AngleSolverState.Effort.FAST, s.getEffort());
        s.setEffort(AngleSolverState.Effort.THOROUGH);
        assertEquals(AngleSolverState.OPTIMIZE_FALLBACK_MS, s.getBudgetMs());
        s.setEffort(AngleSolverState.Effort.FAST);
        assertEquals(0, s.getBudgetMs());
    }
}
