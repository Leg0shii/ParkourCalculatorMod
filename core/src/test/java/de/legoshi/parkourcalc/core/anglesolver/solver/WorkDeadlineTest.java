package de.legoshi.parkourcalc.core.anglesolver.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkDeadlineTest {

    private static final long MS = 1_000_000L;

    private static long spinUntilOver(WorkDeadline deadline, long start) {
        double sink = 0.0;
        while (!deadline.over()) {
            for (int i = 0; i < 2000; i++) sink += StrictMath.sqrt(i + sink);
        }
        assertTrue(sink >= 0.0);
        return System.nanoTime() - start;
    }

    @Test
    public void aThreadTimeBudgetNeverEndsBeforeTheSameWallTime() {
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 20L * MS);
            long wall = spinUntilOver(deadline, start);
            assertTrue("ended after " + wall + " ns", wall >= 20L * MS);
            assertEquals(0L, deadline.remainingNanos());
        }
    }

    @Test
    public void aSleepingThreadDoesNotSpendItsThreadTimeBudget() throws InterruptedException {
        WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 40L * MS);
        Thread.sleep(80L);
        assertFalse(deadline.over());
        assertTrue(deadline.remainingNanos() > 0L);
    }

    @Test
    public void aCappedDeadlineEndsNoLaterThanItsParent() {
        WorkDeadline parent = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 15L * MS);
        WorkDeadline longer = parent.capIn(5_000L * MS);
        long start = System.nanoTime();
        WorkDeadline shorter = parent.capIn(5L * MS);
        long wall = spinUntilOver(shorter, start);
        assertTrue("ended after " + wall + " ns", wall >= 5L * MS);
        spinUntilOver(parent, start);
        assertTrue(longer.over());
        assertTrue(longer.remainingNanos() == 0L);
    }

    @Test
    public void aCapOnNoDeadlineIsAWallDeadline() throws InterruptedException {
        WorkDeadline none = WorkDeadline.none();
        assertTrue(none.isNone());
        assertFalse(none.over());
        assertEquals(Long.MAX_VALUE, none.remainingNanos());
        WorkDeadline capped = none.capIn(10L * MS);
        assertFalse(capped.isNone());
        Thread.sleep(30L);
        assertTrue(capped.over());
    }
}
