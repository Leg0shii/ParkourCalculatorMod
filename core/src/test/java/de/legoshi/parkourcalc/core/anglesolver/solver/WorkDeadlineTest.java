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

    private static long spinFor(long nanos) {
        long start = System.nanoTime();
        double sink = 0.0;
        while (System.nanoTime() - start < nanos) {
            for (int i = 0; i < 2000; i++) sink += StrictMath.sqrt(i + sink);
        }
        assertTrue(sink >= 0.0);
        return System.nanoTime() - start;
    }

    @Test
    public void aMeteredBudgetWaitsForItsWork() {
        SolveClock meter = SolveClock.meter();
        try {
            WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 10L * MS);
            WorkDeadline inner = deadline.capIn(4L * MS);
            spinFor(25L * MS);
            assertFalse("no work was counted yet", deadline.over());
            assertFalse(inner.over());
            assertTrue(deadline.remainingNanos() > 0L);
            SolveClock.charge(SolveClock.TICK, 4L * MS / 26L);
            assertTrue("the inner budget has its work", inner.over());
            assertFalse(deadline.over());
            SolveClock.charge(SolveClock.TICK, 6L * MS / 26L);
            assertTrue("the work is done and the time has passed", deadline.over());
            assertEquals(0L, deadline.remainingNanos());
        } finally {
            meter.uninstall();
        }
    }

    @Test
    public void aMeteredBudgetWithItsWorkEndsOnTime() {
        SolveClock meter = SolveClock.meter();
        try {
            long start = System.nanoTime();
            WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 10L * MS);
            SolveClock.charge(SolveClock.TICK, 10L * MS / 26L);
            assertFalse("the work alone does not end it", deadline.over());
            long wall = spinUntilOver(deadline, start);
            assertTrue("ended after " + wall + " ns", wall >= 10L * MS && wall < 60L * MS);
        } finally {
            meter.uninstall();
        }
    }

    @Test
    public void aMeteredBudgetWithoutWorkEndsAtItsCap() {
        SolveClock meter = SolveClock.meter();
        try {
            long start = System.nanoTime();
            WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, 5L * MS);
            long wall = spinUntilOver(deadline, start);
            assertTrue("ended after " + wall + " ns", wall >= 35L * MS);
        } finally {
            meter.uninstall();
        }
    }

    @Test
    public void aMeterCountsWorkAndLeavesTheClockAlone() {
        assertEquals(-1L, SolveClock.meteredNanos());
        SolveClock meter = SolveClock.meter();
        try {
            assertEquals(null, SolveClock.current());
            assertEquals(null, SolveClock.meter());
            assertEquals(0L, SolveClock.meteredNanos());
            long before = System.nanoTime();
            long read = SolveClock.nanoTime();
            assertTrue(read >= before && read <= System.nanoTime());
            SolveClock.charge(SolveClock.TICK, 1000L);
            assertEquals(26_000L, SolveClock.meteredNanos());
            assertEquals(1000L, meter.units(SolveClock.TICK));
            assertEquals(0L, meter.units(SolveClock.POLL));
        } finally {
            meter.uninstall();
        }
        assertEquals(-1L, SolveClock.meteredNanos());
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
