package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SolveClockTest {

    private static final int WALL_CHECK_ADVANCES = 4096;

    @Test
    public void withoutAClockTimeIsTheWallClock() {
        assertNull(SolveClock.current());
        long before = System.nanoTime();
        long read = SolveClock.nanoTime();
        long after = System.nanoTime();
        assertTrue(before <= read && read <= after);
        assertEquals(0L, SolveClock.seedMix());
        SolveClock.charge(SolveClock.TICK, 1000L);
        assertNull(SolveClock.current());
    }

    @Test
    public void timeMovesOnlyWithCountedWork() throws Exception {
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            assertSame(clock, SolveClock.current());
            Thread.sleep(20L);
            assertEquals(0L, clock.elapsedNanos());
            SolveClock.charge(SolveClock.TICK, 1000L);
            long afterTicks = clock.elapsedNanos();
            assertTrue(afterTicks > 0L);
            assertEquals(1000L, clock.units(SolveClock.TICK));
            long first = SolveClock.nanoTime();
            long second = SolveClock.nanoTime();
            assertTrue("a clock read is counted work too", second > first);
            assertEquals(2L, clock.units(SolveClock.POLL));
        } finally {
            clock.uninstall();
        }
        assertNull(SolveClock.current());
    }

    @Test
    public void theSameWorkGivesTheSameTime() {
        long[] elapsed = new long[2];
        for (int run = 0; run < 2; run++) {
            SolveClock clock = SolveClock.install(null, 0L, 0L);
            try {
                for (int i = 0; i < 500; i++) {
                    SolveClock.charge(SolveClock.KERNEL, 977L + i);
                    SolveClock.charge(SolveClock.LP, 31L * i);
                    SolveClock.nanoTime();
                }
                elapsed[run] = clock.elapsedNanos();
            } finally {
                clock.uninstall();
            }
        }
        assertTrue(elapsed[0] > 0L);
        assertEquals(elapsed[0], elapsed[1]);
    }

    @Test
    public void anArmedTokenFiresWhenTheWorkReachesItsDeadline() {
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            AtomicBoolean token = new AtomicBoolean();
            AtomicBoolean disarmed = new AtomicBoolean();
            AtomicBoolean late = new AtomicBoolean();
            long now = SolveClock.nanoTime();
            clock.arm(token, now + 1_000_000L);
            clock.arm(disarmed, now + 1_000_000L);
            clock.arm(late, now + 1_000_000_000L);
            clock.disarm(disarmed);
            SolveClock.charge(SolveClock.TICK, 10L);
            assertFalse(token.get());
            while (clock.elapsedNanos() < 2_000_000L) SolveClock.charge(SolveClock.TICK, 10L);
            assertTrue(token.get());
            assertFalse(disarmed.get());
            assertFalse(late.get());

            AtomicBoolean past = new AtomicBoolean();
            clock.arm(past, now);
            assertTrue("a deadline that has passed fires at once", past.get());
        } finally {
            clock.uninstall();
        }
    }

    @Test
    public void aJoinTakesTheLongestTaskOrTheSharedLoad() {
        SolveClock parent = SolveClock.install(null, 0L, 0L);
        try {
            SolveClock quick = parent.fork(null, 0L);
            SolveClock slow = parent.fork(null, 0L);
            work(quick, 1000L);
            work(slow, 4000L);
            assertEquals("a fork does not move its parent", 0L, parent.elapsedNanos());
            parent.join(new SolveClock[]{quick, slow});
            assertEquals(slow.elapsedNanos(), parent.elapsedNanos());
            assertEquals(5000L, parent.units(SolveClock.TICK));

            long before = parent.elapsedNanos();
            int tasks = 2 * SolveClock.WORKERS;
            SolveClock[] forks = new SolveClock[tasks];
            for (int i = 0; i < tasks; i++) {
                forks[i] = parent.fork(null, 0L);
                work(forks[i], 1000L);
            }
            parent.join(forks);
            assertEquals("more equal tasks than workers take two rounds",
                    2L * forks[0].elapsedNanos(), parent.elapsedNanos() - before);
        } finally {
            parent.uninstall();
        }
    }

    @Test
    public void anArmedTokenTellsItsDeadline() {
        SolveClock clock = SolveClock.install(null, 0L, 0L);
        try {
            AtomicBoolean armed = new AtomicBoolean(false);
            AtomicBoolean loose = new AtomicBoolean(false);
            long deadline = SolveClock.nanoTime() + 1_000_000L;
            clock.arm(armed, deadline);
            clock.arm(armed, deadline + 5L);
            assertEquals("the earliest deadline of the token", deadline, clock.deadlineOf(armed));
            assertEquals("a token nobody armed has none", Long.MAX_VALUE, clock.deadlineOf(loose));
            assertEquals(Long.MAX_VALUE, clock.deadlineOf(null));
            while (!armed.get()) SolveClock.charge(SolveClock.TICK, 1L);
            assertTrue("a token that fired is due", clock.deadlineOf(armed) <= SolveClock.nanoTime());
            loose.set(true);
            assertTrue("a token set from outside is due", clock.deadlineOf(loose) <= SolveClock.nanoTime());
        } finally {
            clock.uninstall();
        }
    }

    @Test
    public void aSerialForkCountsEveryTaskInside() {
        SolveClock parent = SolveClock.install(null, 0L, 0L);
        try {
            SolveClock lane = parent.forkSerial(null, 0L);
            assertEquals(1, lane.workers());
            assertEquals(SolveClock.WORKERS, parent.workers());
            SolveClock first = lane.fork(null, 0L);
            SolveClock second = lane.fork(null, 0L);
            assertEquals("a fork of a serial fork is serial", 1, first.workers());
            work(first, 1000L);
            work(second, 4000L);
            lane.join(new SolveClock[]{first, second});
            assertEquals("one worker runs the tasks one after the other",
                    first.elapsedNanos() + second.elapsedNanos(), lane.elapsedNanos());
            parent.join(new SolveClock[]{lane});
            assertEquals(lane.elapsedNanos(), parent.elapsedNanos());
            assertEquals(5000L, parent.units(SolveClock.TICK));
        } finally {
            parent.uninstall();
        }
    }

    @Test
    public void aForkStopsAtItsOwnDeadline() {
        SolveClock parent = SolveClock.install(null, 0L, 0L);
        try {
            AtomicBoolean token = new AtomicBoolean();
            SolveClock fork = parent.fork(token, SolveClock.nanoTime() + 1_000_000L);
            SolveClock previous = fork.enter();
            try {
                while (!token.get()) SolveClock.charge(SolveClock.TICK, 1L);
            } finally {
                SolveClock.restore(previous);
            }
            assertSame(parent, SolveClock.current());
            assertTrue(fork.elapsedNanos() >= 1_000_000L);
            assertTrue(fork.elapsedNanos() < 1_100_000L);
        } finally {
            parent.uninstall();
        }
    }

    @Test
    public void aCancelFiresEveryTokenAndHurriesTheRest() {
        AtomicBoolean cancel = new AtomicBoolean();
        SolveClock clock = SolveClock.install(cancel, 0L, 0L);
        try {
            AtomicBoolean token = new AtomicBoolean();
            clock.arm(token, SolveClock.nanoTime() + 3_600_000_000_000L);
            for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
            assertFalse(token.get());
            long calm = clock.elapsedNanos();
            cancel.set(true);
            for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
            assertTrue(token.get());
            assertFalse(clock.overran());
            assertTrue("after a cancel the same work costs far more time",
                    clock.elapsedNanos() - calm > 100L * calm);
        } finally {
            clock.uninstall();
        }
    }

    @Test
    public void aForkWithoutADeadlineStillHearsACancel() {
        AtomicBoolean cancel = new AtomicBoolean();
        SolveClock parent = SolveClock.install(cancel, 0L, 0L);
        try {
            AtomicBoolean token = new AtomicBoolean();
            SolveClock fork = parent.fork(token, 0L);
            SolveClock previous = fork.enter();
            try {
                for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
                assertFalse(token.get());
                cancel.set(true);
                for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
                assertTrue(token.get());
            } finally {
                SolveClock.restore(previous);
            }
        } finally {
            parent.uninstall();
        }
    }

    @Test
    public void theWallLimitMarksTheSolveAsNotRepeatable() throws Exception {
        SolveClock clock = SolveClock.install(null, 1_000_000L, 0L);
        try {
            for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
            Thread.sleep(5L);
            for (int i = 0; i < 2 * WALL_CHECK_ADVANCES; i++) SolveClock.charge(SolveClock.KERNEL, 1L);
            assertTrue(clock.overran());
        } finally {
            clock.uninstall();
        }
    }

    @Test
    public void theSeedReachesEveryFork() {
        SolveClock plain = SolveClock.install(null, 0L, 0L);
        try {
            assertEquals(0L, SolveClock.seedMix());
        } finally {
            plain.uninstall();
        }
        long seven = seedMixOf(7L);
        assertNotEquals(0L, seven);
        assertEquals(seven, seedMixOf(7L));
        assertNotEquals(seven, seedMixOf(8L));

        SolveClock seeded = SolveClock.install(null, 0L, 7L);
        try {
            SolveClock fork = seeded.fork(null, 0L);
            SolveClock previous = fork.enter();
            try {
                assertEquals(seven, SolveClock.seedMix());
            } finally {
                SolveClock.restore(previous);
            }
        } finally {
            seeded.uninstall();
        }
    }

    private static long seedMixOf(long seed) {
        SolveClock clock = SolveClock.install(null, 0L, seed);
        try {
            return SolveClock.seedMix();
        } finally {
            clock.uninstall();
        }
    }

    private static void work(SolveClock clock, long ticks) {
        SolveClock previous = clock.enter();
        try {
            SolveClock.charge(SolveClock.TICK, ticks);
        } finally {
            SolveClock.restore(previous);
        }
    }
}
