package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SolveClock {

    public static final int WORKERS = 8;

    public static final int POLL = 0;
    public static final int TICK = 1;
    public static final int KERNEL = 2;
    public static final int SUPPORT = 3;
    public static final int LP = 4;
    public static final int DUAL = 5;
    public static final int LEAF = 6;
    public static final int LATTICE = 7;
    public static final int VIOL = 8;
    public static final int POLISH = 9;
    public static final int KINDS = 10;

    private static final String[] KIND_NAMES = {"poll", "tick", "kernel", "support", "lp", "dual", "leaf", "lattice", "viol", "polish"};
    private static final long[] PICOS_PER_UNIT = weights();

    private static long[] weights() {
        long[] w = {30000L, 26000L, 290L, 6200L, 910L, 740L, 1700L, 78000L, 5200L, 2900L};
        String override = System.getProperty("pkc.clockWeights");
        if (override != null) {
            String[] parts = override.split(",");
            for (int i = 0; i < parts.length && i < w.length; i++) w[i] = Long.parseLong(parts[i].trim());
        }
        return w;
    }
    private static final int WALL_CHECK_MASK = 0xFFF;
    private static final int HURRY_SHIFT = 10;

    private static final ThreadLocal<SolveClock> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger LIVE = new AtomicInteger();

    private final AtomicBoolean outer;
    private final long startNanos;
    private final long wallLimitNanos;
    private final long seedMix;
    private final long[] units = new long[KINDS];
    private long nowNanos;
    private long workPicos;
    private long nextFire = Long.MAX_VALUE;
    private AtomicBoolean[] tokens = new AtomicBoolean[4];
    private long[] deadlines = new long[4];
    private int armed;
    private int advances;
    private boolean overran;
    private int rateShift;
    private int workers = WORKERS;

    private SolveClock(long startNanos, AtomicBoolean outer, long wallLimitNanos, long seedMix) {
        this.startNanos = startNanos;
        this.nowNanos = startNanos;
        this.outer = outer;
        this.wallLimitNanos = wallLimitNanos;
        this.seedMix = seedMix;
    }

    public static long nanoTime() {
        if (LIVE.get() == 0) return System.nanoTime();
        SolveClock c = CURRENT.get();
        if (c == null) return System.nanoTime();
        c.advance(POLL, 1L);
        return c.nowNanos;
    }

    public static void charge(int kind, long count) {
        if (LIVE.get() == 0) return;
        SolveClock c = CURRENT.get();
        if (c != null) c.advance(kind, count);
    }

    public static SolveClock current() {
        return LIVE.get() == 0 ? null : CURRENT.get();
    }

    public static long seedMix() {
        if (LIVE.get() == 0) return 0L;
        SolveClock c = CURRENT.get();
        return c == null ? 0L : c.seedMix;
    }

    private static long mix(long seed) {
        if (seed == 0L) return 0L;
        long h = seed * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public static SolveClock install(AtomicBoolean outer, long wallBudgetNanos, long seed) {
        long start = System.nanoTime();
        SolveClock c = new SolveClock(start, outer, wallBudgetNanos > 0 ? start + wallBudgetNanos : 0L, mix(seed));
        LIVE.incrementAndGet();
        CURRENT.set(c);
        return c;
    }

    public void uninstall() {
        CURRENT.remove();
        LIVE.decrementAndGet();
    }

    public SolveClock fork(AtomicBoolean token, long deadlineNanos) {
        SolveClock child = new SolveClock(nowNanos, outer, wallLimitNanos, seedMix);
        child.rateShift = rateShift;
        child.workers = workers;
        if (token != null) child.arm(token, deadlineNanos > 0 ? deadlineNanos : Long.MAX_VALUE);
        return child;
    }

    public SolveClock forkSerial(AtomicBoolean token, long deadlineNanos) {
        SolveClock child = fork(token, deadlineNanos);
        child.workers = 1;
        return child;
    }

    public int workers() {
        return workers;
    }

    public SolveClock enter() {
        SolveClock previous = CURRENT.get();
        CURRENT.set(this);
        return previous;
    }

    public static void restore(SolveClock previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    public void join(SolveClock[] children) {
        join(children, Long.MAX_VALUE, -1);
    }

    public void join(SolveClock[] children, long capNanos, int winner) {
        long longest = 0L;
        long sum = 0L;
        for (int i = 0; i < children.length; i++) {
            SolveClock child = children[i];
            if (child == null) continue;
            long own = child.nowNanos - child.startNanos;
            long elapsed = Math.min(capNanos, own);
            longest = Math.max(longest, elapsed);
            sum += elapsed;
            overran |= child.overran;
            rateShift = Math.max(rateShift, child.rateShift);
            if (own < capNanos || i == winner) {
                for (int k = 0; k < KINDS; k++) units[k] += child.units[k];
            }
        }
        advanceTo(nowNanos + Math.max(longest, (sum + workers - 1) / workers));
    }

    public void addUnits(SolveClock[] children) {
        for (SolveClock child : children) {
            if (child == null) continue;
            for (int k = 0; k < KINDS; k++) units[k] += child.units[k];
        }
    }

    public void advanceTo(long nanos) {
        if (nanos <= nowNanos) return;
        workPicos = (nanos - startNanos) * 1000L;
        nowNanos = nanos;
        if (nowNanos >= nextFire) fire();
    }

    public void arm(AtomicBoolean token, long deadlineNanos) {
        if (token == null || deadlineNanos <= 0) return;
        if (deadlineNanos <= nowNanos) {
            token.set(true);
            return;
        }
        if (armed == tokens.length) {
            tokens = java.util.Arrays.copyOf(tokens, armed * 2);
            deadlines = java.util.Arrays.copyOf(deadlines, armed * 2);
        }
        tokens[armed] = token;
        deadlines[armed] = deadlineNanos;
        armed++;
        if (deadlineNanos < nextFire) nextFire = deadlineNanos;
    }

    public void disarm(AtomicBoolean token) {
        int kept = 0;
        long next = Long.MAX_VALUE;
        for (int i = 0; i < armed; i++) {
            if (tokens[i] == token) continue;
            tokens[kept] = tokens[i];
            deadlines[kept] = deadlines[i];
            if (deadlines[kept] < next) next = deadlines[kept];
            kept++;
        }
        for (int i = kept; i < armed; i++) tokens[i] = null;
        armed = kept;
        nextFire = next;
    }

    public long elapsedNanos() {
        return nowNanos - startNanos;
    }

    public boolean overran() {
        return overran;
    }

    public long units(int kind) {
        return units[kind];
    }

    public long[] unitsSnapshot() {
        return units.clone();
    }

    public static String kindName(int kind) {
        return KIND_NAMES[kind];
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < KINDS; k++) {
            if (k > 0) sb.append(' ');
            sb.append(KIND_NAMES[k]).append('=').append(units[k]);
        }
        return sb.toString();
    }

    private void advance(int kind, long count) {
        units[kind] += count;
        workPicos += (count * PICOS_PER_UNIT[kind]) << rateShift;
        nowNanos = startNanos + workPicos / 1000L;
        if (nowNanos >= nextFire) fire();
        if ((++advances & WALL_CHECK_MASK) == 0) checkWall();
    }

    private void fire() {
        int kept = 0;
        long next = Long.MAX_VALUE;
        for (int i = 0; i < armed; i++) {
            if (deadlines[i] <= nowNanos) {
                tokens[i].set(true);
                continue;
            }
            tokens[kept] = tokens[i];
            deadlines[kept] = deadlines[i];
            if (deadlines[kept] < next) next = deadlines[kept];
            kept++;
        }
        for (int i = kept; i < armed; i++) tokens[i] = null;
        armed = kept;
        nextFire = next;
    }

    private void checkWall() {
        if (outer != null && outer.get()) {
            for (int i = 0; i < armed; i++) {
                tokens[i].set(true);
                tokens[i] = null;
            }
            armed = 0;
            nextFire = Long.MAX_VALUE;
            rateShift = HURRY_SHIFT;
            return;
        }
        if (!overran && wallLimitNanos > 0 && System.nanoTime() > wallLimitNanos) {
            overran = true;
            rateShift = HURRY_SHIFT;
        }
    }
}
