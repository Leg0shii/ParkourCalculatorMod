package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public final class WorkDeadline {

    public enum Clock { WALL, THREAD_CPU }

    private static final ThreadMXBean CPU = threadCpuBean();
    private static final boolean CPU_OK = CPU != null && CPU.isThreadCpuTimeSupported();
    private static final boolean WALL_FLOOR = !"false".equals(System.getProperty("pkc.deadline.wallFloor"));

    private final Clock clock;
    private final boolean none;
    private final long targetNanos;
    private final boolean floored;
    private final long wallFloorNanos;

    private WorkDeadline(Clock clock, boolean none, long targetNanos, boolean floored, long wallFloorNanos) {
        this.clock = clock;
        this.none = none;
        this.targetNanos = targetNanos;
        this.floored = floored;
        this.wallFloorNanos = wallFloorNanos;
    }

    public static WorkDeadline none() {
        return new WorkDeadline(Clock.WALL, true, 0L, false, 0L);
    }

    public static WorkDeadline wallAbsolute(long deadlineNanos) {
        return deadlineNanos == 0L ? none() : new WorkDeadline(Clock.WALL, false, deadlineNanos, false, 0L);
    }

    public static WorkDeadline in(Clock clock, long budgetNanos) {
        Clock c = clock == Clock.THREAD_CPU && !CPU_OK ? Clock.WALL : clock;
        boolean floor = c == Clock.THREAD_CPU && WALL_FLOOR;
        return new WorkDeadline(c, false, readNow(c) + budgetNanos, floor, floor ? System.nanoTime() + budgetNanos : 0L);
    }

    public boolean isNone() {
        return none;
    }

    public boolean over() {
        if (none) return false;
        if (floored && System.nanoTime() - wallFloorNanos < 0L) return false;
        return readNow(clock) >= targetNanos;
    }

    public long remainingNanos() {
        if (none) return Long.MAX_VALUE;
        long left = targetNanos - readNow(clock);
        if (floored) left = Math.max(left, wallFloorNanos - System.nanoTime());
        return Math.max(0L, left);
    }

    public WorkDeadline capIn(long budgetNanos) {
        long t = readNow(clock) + budgetNanos;
        if (none) return new WorkDeadline(clock, false, t, false, 0L);
        long target = Math.min(targetNanos, t);
        if (!floored) return new WorkDeadline(clock, false, target, false, 0L);
        long floor = System.nanoTime() + budgetNanos;
        if (wallFloorNanos - floor < 0L) floor = wallFloorNanos;
        return new WorkDeadline(clock, false, target, true, floor);
    }

    private static long readNow(Clock c) {
        return c == Clock.THREAD_CPU ? CPU.getCurrentThreadCpuTime() : SolveClock.nanoTime();
    }

    private static ThreadMXBean threadCpuBean() {
        try {
            ThreadMXBean b = ManagementFactory.getThreadMXBean();
            if (b.isThreadCpuTimeSupported() && !b.isThreadCpuTimeEnabled()) b.setThreadCpuTimeEnabled(true);
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
