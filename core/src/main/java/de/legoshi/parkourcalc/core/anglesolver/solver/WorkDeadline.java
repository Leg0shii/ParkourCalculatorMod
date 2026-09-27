package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public final class WorkDeadline {

    public enum Clock { WALL, THREAD_CPU }

    private static final ThreadMXBean CPU = threadCpuBean();
    private static final boolean CPU_OK = CPU != null && CPU.isThreadCpuTimeSupported();

    private final Clock clock;
    private final boolean none;
    private final long targetNanos;

    private WorkDeadline(Clock clock, boolean none, long targetNanos) {
        this.clock = clock;
        this.none = none;
        this.targetNanos = targetNanos;
    }

    public static WorkDeadline none() {
        return new WorkDeadline(Clock.WALL, true, 0L);
    }

    public static WorkDeadline wallAbsolute(long deadlineNanos) {
        return deadlineNanos == 0L ? none() : new WorkDeadline(Clock.WALL, false, deadlineNanos);
    }

    public static WorkDeadline in(Clock clock, long budgetNanos) {
        Clock c = clock == Clock.THREAD_CPU && !CPU_OK ? Clock.WALL : clock;
        return new WorkDeadline(c, false, readNow(c) + budgetNanos);
    }

    public boolean isNone() {
        return none;
    }

    public boolean over() {
        return !none && readNow(clock) >= targetNanos;
    }

    public long remainingNanos() {
        return none ? Long.MAX_VALUE : Math.max(0L, targetNanos - readNow(clock));
    }

    public WorkDeadline capIn(long budgetNanos) {
        long t = readNow(clock) + budgetNanos;
        long target = none ? t : Math.min(targetNanos, t);
        return new WorkDeadline(clock, false, target);
    }

    private static long readNow(Clock c) {
        return c == Clock.THREAD_CPU ? CPU.getCurrentThreadCpuTime() : System.nanoTime();
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
