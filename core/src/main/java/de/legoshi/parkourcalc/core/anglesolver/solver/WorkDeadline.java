package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public final class WorkDeadline {

    public enum Clock { WALL, THREAD_CPU }

    private static final ThreadMXBean CPU = threadCpuBean();
    private static final boolean CPU_OK = CPU != null && CPU.isThreadCpuTimeSupported();
    private static final boolean WALL_FLOOR = !"false".equals(System.getProperty("pkc.deadline.wallFloor"));
    private static final long WORK_FLOOR_PERCENT = Long.getLong("pkc.deadline.workFloorPercent", 75L);
    private static final long WORK_CAP_FACTOR = Long.getLong("pkc.deadline.workCapFactor", 8L);
    private static final long METER_MAX_NANOS = Long.MAX_VALUE / 1024L;

    private final Clock clock;
    private final boolean none;
    private final long targetNanos;
    private final boolean floored;
    private final long wallFloorNanos;
    private final boolean metered;
    private final long workFloorNanos;
    private final long workCapNanos;

    private WorkDeadline(Clock clock, boolean none, long targetNanos, boolean floored, long wallFloorNanos) {
        this(clock, none, targetNanos, floored, wallFloorNanos, false, 0L, 0L);
    }

    private WorkDeadline(Clock clock, boolean none, long targetNanos, boolean floored, long wallFloorNanos,
                         boolean metered, long workFloorNanos, long workCapNanos) {
        this.clock = clock;
        this.none = none;
        this.targetNanos = targetNanos;
        this.floored = floored;
        this.wallFloorNanos = wallFloorNanos;
        this.metered = metered;
        this.workFloorNanos = workFloorNanos;
        this.workCapNanos = workCapNanos;
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
        long now = readNow(c);
        long wallFloor = floor ? System.nanoTime() + budgetNanos : 0L;
        long work = meterFor(c, budgetNanos);
        if (work < 0L) return new WorkDeadline(c, false, now + budgetNanos, floor, wallFloor);
        return new WorkDeadline(c, false, now + budgetNanos, floor, wallFloor, true, work + workShare(budgetNanos),
                now + budgetNanos * WORK_CAP_FACTOR);
    }

    private static long meterFor(Clock c, long budgetNanos) {
        if (c != Clock.THREAD_CPU || WORK_FLOOR_PERCENT <= 0L || WORK_CAP_FACTOR <= 1L) return -1L;
        if (budgetNanos <= 0L || budgetNanos > METER_MAX_NANOS) return -1L;
        return SolveClock.meteredNanos();
    }

    private static long workShare(long budgetNanos) {
        return budgetNanos / 100L * WORK_FLOOR_PERCENT;
    }

    public boolean isNone() {
        return none;
    }

    public boolean over() {
        if (none) return false;
        if (floored && System.nanoTime() - wallFloorNanos < 0L) return false;
        long now = readNow(clock);
        if (now < targetNanos) return false;
        if (!metered || now >= workCapNanos) return true;
        long work = SolveClock.meteredNanos();
        return work < 0L || work >= workFloorNanos;
    }

    public long remainingNanos() {
        if (none) return Long.MAX_VALUE;
        long now = readNow(clock);
        long left = targetNanos - now;
        if (floored) left = Math.max(left, wallFloorNanos - System.nanoTime());
        if (metered) {
            long work = SolveClock.meteredNanos();
            if (work >= 0L && work < workFloorNanos) left = Math.max(left, Math.min(workFloorNanos - work, workCapNanos - now));
        }
        return Math.max(0L, left);
    }

    public WorkDeadline capIn(long budgetNanos) {
        long now = readNow(clock);
        long t = now + budgetNanos;
        if (none) return new WorkDeadline(clock, false, t, false, 0L);
        long target = Math.min(targetNanos, t);
        long floor = 0L;
        if (floored) {
            floor = System.nanoTime() + budgetNanos;
            if (wallFloorNanos - floor < 0L) floor = wallFloorNanos;
        }
        long work = metered ? meterFor(clock, budgetNanos) : -1L;
        if (work < 0L) return new WorkDeadline(clock, false, target, floored, floor);
        return new WorkDeadline(clock, false, target, floored, floor, true,
                Math.min(workFloorNanos, work + workShare(budgetNanos)),
                Math.min(workCapNanos, now + budgetNanos * WORK_CAP_FACTOR));
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
