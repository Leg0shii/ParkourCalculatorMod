package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class MomentumSearch {

    public interface Sink {
        void kept(int[] combos, int variant, double value);
    }

    public enum Outcome {
        COMPLETE, CAPPED, GAVE_UP, STOPPED
    }

    public static final class Cut {
        double base;
        double[] x;
        double[] y;
        final AtomicLong rejects = new AtomicLong();
    }

    public static final class Settings {
        public double keep = 0.0005;
        public int threads = 1;
        public int cuts = 48;
        public int nodeCuts = 6;
        public int warmup = 3000;
        public int carriedWarmup = 500;
        public int judges = 1024;
        public long probeNanos = 4_000_000_000L;
        public long maxSuffixes = 1_500_000L;
        public long maxFinals = 1_000_000L;
        public long keptCap = 5_000L;
        public int minSegment = 8;
        public int poolCap = 3000;
        public double memoryShare = 1.0 / 6.0;
    }

    private static final int DIRS = 64;
    private static final double SEC = 1.0 / StrictMath.cos(Math.PI / DIRS);
    private static final double[] DX = new double[DIRS];
    private static final double[] DY = new double[DIRS];
    private static final double EPS = 1e-9;
    private static final long[] PROBE_SURVIVORS = {200, 1000, 3000, 10000};
    private static final long PROBE_SUFFIXES = 300_000L;
    private static final int KEYS = FreeAirExact.Tables.KEYS;
    private static final int FINALS_TICKS = 14;

    static {
        for (int i = 0; i < DIRS; i++) {
            DX[i] = StrictMath.cos(2 * Math.PI * i / DIRS);
            DY[i] = StrictMath.sin(2 * Math.PI * i / DIRS);
        }
    }

    private static double len(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    private static final class Suffix {
        int start;
        int end;
        double[] vx;
        double[] vy;
        double[] vs;
        byte[] combos;
    }

    private static final class Packed {
        final int from;
        final int to;
        final int words;
        long[] codes;
        int size;

        Packed(int from, int to) {
            this.from = from;
            this.to = to;
            this.words = Math.max(1, (to - from + 15) / 16);
            this.codes = new long[16 * words];
        }

        void add(int[] combos) {
            if ((size + 1) * words > codes.length) codes = Arrays.copyOf(codes, codes.length * 2);
            int at = size * words;
            for (int w = 0; w < words; w++) {
                long code = 0;
                int lo = from + w * 16;
                int hi = Math.min(to, lo + 16);
                for (int t = hi - 1; t >= lo; t--) code = (code << 4) | combos[t];
                codes[at + w] = code;
            }
            size++;
        }

        void decode(int i, int[] combos) {
            int at = i * words;
            for (int w = 0; w < words; w++) {
                long code = codes[at + w];
                int lo = from + w * 16;
                int hi = Math.min(to, lo + 16);
                for (int t = lo; t < hi; t++) {
                    combos[t] = (int) (code & 15);
                    code >>>= 4;
                }
            }
        }

        Packed permuted(Integer[] order) {
            Packed out = new Packed(from, to);
            out.codes = new long[Math.max(1, size) * words];
            for (int i = 0; i < size; i++) System.arraycopy(codes, order[i] * words, out.codes, i * words, words);
            out.size = size;
            return out;
        }
    }

    private static final class Finals {
        Packed keys;
        double[][] air;
        boolean[] fresh;
    }

    public static int walkedTicks(FreeAirExact.Tables tables, int[] jumpTicks) {
        int n = tables.ticks();
        if (n == 0) return -1;
        Set<Integer> seen = new HashSet<>();
        for (int t = 0; t < n; t++) {
            int unit = tables.unitOf(t);
            if (unit < 0) return -1;
            if ((t == 0 || unit != tables.unitOf(t - 1)) && !seen.add(unit)) return -1;
        }
        int free = n;
        while (free > 0 && (free == 1 || tables.unitOf(free - 1) != tables.unitOf(free - 2))) free--;
        int last = free;
        if (n - free > FINALS_TICKS) {
            last = n - FINALS_TICKS;
            for (int jump : jumpTicks) {
                if (jump > free && n - jump <= FINALS_TICKS) {
                    last = jump;
                    break;
                }
            }
        }
        return Math.max(1, last);
    }

    public final AtomicLong nodes = new AtomicLong();
    public final AtomicLong screens = new AtomicLong();
    public final AtomicLong emitted = new AtomicLong();

    private final FreeAirExact.Tables tables;
    private final MomentumRules rules;
    private final MomentumRules known;
    private final Settings settings;
    private final BooleanSupplier stop;
    private final Sink sink;
    private final int n;
    private final int last;
    private final boolean[] joins;
    private final int states;
    private final double keep;
    private final int variant;
    private final int threads;
    private final int[] bounds;
    private final int m;
    private final boolean[][] reach;
    private final List<Cut> pool = new ArrayList<>();
    private final ConcurrentHashMap<Integer, Finals> finals = new ConcurrentHashMap<>();
    private final Set<String> sent;
    private final AtomicLong finalsCount = new AtomicLong();
    private final List<int[]> survivors = new ArrayList<>();
    private Cut[] cuts;
    private int cutCount;
    private double[] need;
    private double[][][][] forward;
    private double[][] maxAir;
    private volatile boolean abort;
    private volatile boolean budgetHit;
    private volatile boolean capped;
    private volatile boolean stopped;
    private volatile boolean tooLarge;
    private volatile long deadline = Long.MAX_VALUE;
    private long taskBudget = Long.MAX_VALUE;
    private long suffixBudget = Long.MAX_VALUE;
    private long suffixLimit;
    private long finalsLimit;

    public MomentumSearch(FreeAirExact.Tables tables, MomentumRules rules, MomentumRules known, int[] jumpTicks, int variant,
                   Settings settings, List<Cut> carried, Set<String> sent, BooleanSupplier stop, Sink sink) {
        this.tables = tables;
        this.rules = rules;
        this.known = known;
        this.settings = settings;
        this.stop = stop;
        this.sink = sink;
        this.sent = sent;
        this.n = rules.n;
        this.last = walkedTicks(tables, jumpTicks);
        this.joins = new boolean[n + 1];
        for (int t = 1; t < n; t++) joins[t] = tables.unitOf(t) == tables.unitOf(t - 1);
        this.states = rules.states;
        this.keep = settings.keep;
        this.variant = variant;
        this.threads = Math.max(1, settings.threads);
        List<Integer> cutsAt = new ArrayList<>();
        cutsAt.add(0);
        for (int jump : jumpTicks) {
            if (jump >= settings.minSegment && jump < last - 1 && jump - cutsAt.get(cutsAt.size() - 1) >= settings.minSegment) {
                cutsAt.add(jump);
            }
        }
        cutsAt.add(last);
        this.bounds = new int[cutsAt.size()];
        for (int i = 0; i < bounds.length; i++) bounds[i] = cutsAt.get(i);
        this.m = bounds.length - 1;
        this.reach = new boolean[n + 1][states];
        reach[0][rules.startState()] = true;
        int[] key = new int[1];
        for (int t = 0; t < n; t++) {
            for (int st = 0; st < states; st++) {
                if (!reach[t][st]) continue;
                for (int combo : rules.alphabet) {
                    int next = rules.next(t, st, combo, variant, key);
                    if (next >= 0) reach[t + 1][next] = true;
                }
            }
        }
        if (carried != null) pool.addAll(carried);
    }

    public List<Cut> pool() {
        List<Cut> out = new ArrayList<>(pool);
        if (out.size() > settings.poolCap) {
            out.sort((a, b) -> Long.compare(b.rejects.get(), a.rejects.get()));
            out = new ArrayList<>(out.subList(0, settings.poolCap));
        }
        return out;
    }

    private Cut cutOf(double[] lambda) {
        Cut cut = new Cut();
        cut.x = new double[n * KEYS];
        cut.y = new double[n * KEYS];
        cut.base = tables.rejectTerms(lambda, cut.x, cut.y);
        return cut;
    }

    private double cutValue(Cut cut, int[] combos) {
        double sx = 0.0;
        double sy = 0.0;
        double total = 0.0;
        int phase = 0;
        for (int t = 0; t < n; t++) {
            int combo = combos[t];
            boolean run = NoTurnKeys.isRun(combo);
            int nextPhase = phase == 0 ? (run ? 1 : 0) : Math.min(phase + 1, 3);
            boolean sprint = run && nextPhase > variant;
            boolean before = t == 0 ? sprint : NoTurnKeys.isRun(combos[t - 1]) && phase > variant;
            int at = t * KEYS + FreeAirExact.Tables.key(combo, sprint, before);
            sx += cut.x[at];
            sy += cut.y[at];
            if (!joins[t + 1]) {
                total += len(sx, sy);
                sx = 0.0;
                sy = 0.0;
            }
            phase = nextPhase;
        }
        return -(cut.base + total);
    }

    private int stateAt(int[] combos, int tick) {
        int state = rules.startState();
        int[] key = new int[1];
        for (int t = 0; t < tick && state >= 0; t++) state = rules.next(t, state, combos[t], variant, key);
        return state;
    }

    private int[] randomSchedule(Random random) {
        int[] combos = new int[n];
        int state = rules.startState();
        int[] key = new int[1];
        for (int t = 0; t < n; t++) {
            int next = -1;
            int combo = 0;
            for (int tries = 0; tries < 60 && next < 0; tries++) {
                if (t > 0 && random.nextDouble() < 0.85) combo = combos[t - 1];
                else combo = rules.alphabet[random.nextInt(rules.alphabet.length)];
                next = rules.next(t, state, combo, variant, key);
            }
            if (next < 0) {
                if (t == 0) return null;
                combo = combos[t - 1];
                next = rules.next(t, state, combo, variant, key);
                if (next < 0) return null;
            }
            combos[t] = combo;
            state = next;
        }
        return combos;
    }

    private List<int[]> randomSchedules(Random random, int count) {
        List<int[]> out = new ArrayList<>();
        for (int tries = 0; out.size() < count && tries < count * 20; tries++) {
            int[] combos = randomSchedule(random);
            if (combos != null) out.add(combos);
        }
        return out;
    }

    private void learn(List<int[]> samples, FreeAirExact.Screen screen) {
        boolean[] sprint = new boolean[n];
        for (int[] combos : samples) {
            boolean dead = false;
            for (int c = pool.size() - 1; c >= 0 && !dead; c--) {
                if (cutValue(pool.get(c), combos) > keep) {
                    dead = true;
                    pool.get(c).rejects.incrementAndGet();
                }
            }
            if (dead) continue;
            MomentumRules.sprintOf(combos, variant, sprint);
            FreeAirExact.Result r = screen.solve(combos, sprint, keep);
            if (r.dual > keep && r.lambda != null) pool.add(cutOf(r.lambda));
        }
    }

    private void pickCuts(List<int[]> samples, int count) {
        List<Cut> ranked = new ArrayList<>(pool);
        ranked.sort((a, b) -> Long.compare(b.rejects.get(), a.rejects.get()));
        final List<Cut> candidates = ranked.size() > 768 ? new ArrayList<>(ranked.subList(0, 768)) : ranked;
        Set<Cut> listed = new HashSet<>(candidates);
        for (int i = Math.max(0, pool.size() - 256); i < pool.size(); i++) {
            if (listed.add(pool.get(i))) candidates.add(pool.get(i));
        }
        final int candidateCount = candidates.size();
        final int sampleCount = samples.size();
        final boolean[][] kills = new boolean[candidateCount][sampleCount];
        parallel(candidateCount, c -> {
            for (int s = 0; s < sampleCount; s++) kills[c][s] = cutValue(candidates.get(c), samples.get(s)) > keep;
        });
        boolean[] dead = new boolean[sampleCount];
        boolean[] used = new boolean[candidateCount];
        List<Cut> chosen = new ArrayList<>();
        while (chosen.size() < count) {
            int best = -1;
            int bestKills = 0;
            for (int c = 0; c < candidateCount; c++) {
                if (used[c]) continue;
                int k = 0;
                for (int s = 0; s < sampleCount; s++) if (!dead[s] && kills[c][s]) k++;
                if (k > bestKills) {
                    bestKills = k;
                    best = c;
                }
            }
            if (best < 0) {
                boolean any = false;
                for (boolean b : dead) any |= b;
                if (!any) break;
                Arrays.fill(dead, false);
                continue;
            }
            used[best] = true;
            chosen.add(candidates.get(best));
            for (int s = 0; s < sampleCount; s++) if (kills[best][s]) dead[s] = true;
        }
        for (int c = 0; c < candidateCount && chosen.size() < count; c++) {
            if (!used[c]) chosen.add(candidates.get(c));
        }
        if (chosen.isEmpty()) chosen.add(cutOf(new double[tables.rows()]));
        cuts = chosen.toArray(new Cut[0]);
        cutCount = cuts.length;
        long room = (long) (Runtime.getRuntime().maxMemory() * settings.memoryShare);
        suffixLimit = Math.max(1000L, Math.min(settings.maxSuffixes, room / (24L * cutCount + 112L + last)));
        finalsLimit = Math.max(1000L, Math.min(settings.maxFinals, room / (8L * cutCount + 16L)));
        need = new double[cutCount];
        for (int c = 0; c < cutCount; c++) need[c] = -cuts[c].base - keep;
    }

    private interface IndexTask {
        void run(int index);
    }

    private void parallel(int count, IndexTask task) {
        final AtomicInteger next = new AtomicInteger();
        runWorkers(Math.min(threads, Math.max(1, count)), worker -> {
            int i;
            while ((i = next.getAndIncrement()) < count) task.run(i);
        });
    }

    private void runWorkers(int count, IndexTask body) {
        Thread[] workers = new Thread[count];
        final Throwable[] failure = new Throwable[1];
        for (int w = 0; w < count; w++) {
            final int index = w;
            workers[w] = new Thread(() -> {
                try {
                    body.run(index);
                } catch (Throwable ex) {
                    synchronized (failure) {
                        if (failure[0] == null) failure[0] = ex;
                    }
                    abort = true;
                }
            }, "momentum-search-" + w);
            workers[w].setDaemon(true);
            workers[w].start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException ex) {
                abort = true;
                stopped = true;
                Thread.currentThread().interrupt();
            }
        }
        if (failure[0] instanceof OutOfMemoryError) {
            tooLarge = true;
            return;
        }
        if (failure[0] != null) throw new IllegalStateException(failure[0]);
    }

    private boolean single(int t) {
        return !joins[t] && !joins[t + 1];
    }

    private static void collapse(double[] values) {
        if (values == null) return;
        double best = Double.NEGATIVE_INFINITY;
        for (double value : values) if (value > best) best = value;
        if (best == Double.NEGATIVE_INFINITY) return;
        Arrays.fill(values, best > 0 ? best * SEC : best);
    }

    private void prepare() {
        forward = new double[m][cutCount][][];
        maxAir = new double[cutCount][];
        parallel(cutCount, c -> {
            Cut cut = cuts[c];
            int[] key = new int[1];
            double[][] current = new double[states][];
            current[rules.startState()] = new double[DIRS];
            forward[0][c] = current;
            int k = 1;
            for (int t = 0; t < last; t++) {
                double[][] after = new double[states][];
                for (int st = 0; st < states; st++) {
                    double[] from = current[st];
                    if (from == null) continue;
                    for (int combo : rules.alphabet) {
                        int next = rules.next(t, st, combo, variant, key);
                        if (next < 0) continue;
                        double x = cut.x[t * KEYS + key[0]];
                        double y = cut.y[t * KEYS + key[0]];
                        double[] to = after[next];
                        if (to == null) {
                            to = new double[DIRS];
                            Arrays.fill(to, Double.NEGATIVE_INFINITY);
                            after[next] = to;
                        }
                        if (single(t)) {
                            double value = from[0] + len(x, y);
                            if (value > to[0]) Arrays.fill(to, value);
                        } else {
                            for (int d = 0; d < DIRS; d++) {
                                double value = from[d] + x * DX[d] + y * DY[d];
                                if (value > to[d]) to[d] = value;
                            }
                        }
                    }
                }
                if (joins[t] && !joins[t + 1]) for (double[] to : after) collapse(to);
                current = after;
                if (k < m && t + 1 == bounds[k]) forward[k++][c] = current;
            }
            double[] air = new double[states];
            for (int t = n - 1; t >= last; t--) {
                double[] before = new double[states];
                Arrays.fill(before, Double.NEGATIVE_INFINITY);
                for (int st = 0; st < states; st++) {
                    if (!reach[t][st]) continue;
                    double best = Double.NEGATIVE_INFINITY;
                    for (int combo : rules.alphabet) {
                        int next = rules.next(t, st, combo, variant, key);
                        if (next < 0 || air[next] == Double.NEGATIVE_INFINITY) continue;
                        int at = t * KEYS + key[0];
                        double value = len(cut.x[at], cut.y[at]) + air[next];
                        if (value > best) best = value;
                    }
                    before[st] = best;
                }
                air = before;
            }
            maxAir[c] = air;
        });
    }

    private double[][][][] completions(int k, List<Suffix>[] sigma) {
        final int t0 = bounds[k - 1];
        final int t1 = bounds[k];
        final int length = t1 - t0;
        final double[][][][] support = new double[cutCount][length + 1][][];
        parallel(cutCount, c -> {
            Cut cut = cuts[c];
            int[] key = new int[1];
            double[][] end = new double[states][];
            if (sigma == null) {
                for (int st = 0; st < states; st++) {
                    if (!reach[t1][st] || maxAir[c][st] == Double.NEGATIVE_INFINITY) continue;
                    double[] e = new double[DIRS];
                    Arrays.fill(e, maxAir[c][st]);
                    end[st] = e;
                }
            } else {
                for (int st = 0; st < states; st++) {
                    List<Suffix> list = sigma[st];
                    if (list == null || list.isEmpty()) continue;
                    double[] e = new double[DIRS];
                    Arrays.fill(e, Double.NEGATIVE_INFINITY);
                    for (Suffix s : list) {
                        double rest = s.vs[c] + maxAir[c][s.end];
                        double x = s.vx[c];
                        double y = s.vy[c];
                        for (int d = 0; d < DIRS; d++) {
                            double value = x * DX[d] + y * DY[d] + rest;
                            if (value > e[d]) e[d] = value;
                        }
                    }
                    end[st] = e;
                }
            }
            support[c][length] = end;
            for (int t = t1 - 1; t >= t0; t--) {
                double[][] after = support[c][t + 1 - t0];
                double[][] current = new double[states][];
                for (int st = 0; st < states; st++) {
                    if (!reach[t][st]) continue;
                    double[] best = null;
                    for (int combo : rules.alphabet) {
                        int next = rules.next(t, st, combo, variant, key);
                        if (next < 0) continue;
                        double[] a = after[next];
                        if (a == null) continue;
                        double x = cut.x[t * KEYS + key[0]];
                        double y = cut.y[t * KEYS + key[0]];
                        if (best == null) {
                            best = new double[DIRS];
                            Arrays.fill(best, Double.NEGATIVE_INFINITY);
                        }
                        if (single(t)) {
                            double value = len(x, y) + a[0];
                            if (value > best[0]) Arrays.fill(best, value);
                        } else {
                            for (int d = 0; d < DIRS; d++) {
                                double value = x * DX[d] + y * DY[d] + a[d];
                                if (value > best[d]) best[d] = value;
                            }
                        }
                    }
                    if (!joins[t] && joins[t + 1]) collapse(best);
                    current[st] = best;
                }
                support[c][t - t0] = current;
            }
        });
        return support;
    }

    private final class Walker {
        final int k;
        final int t0;
        final int t1;
        final int firstEnd;
        final double[][][][] support;
        final List<Suffix>[] sigma;
        final double[][] px;
        final double[][] py;
        final double[][] ps;
        final double[] fx;
        final double[] fy;
        final double[] headBound;
        final int[] combos = new int[n];
        final int[] order;
        final int[] key = new int[1];
        final double[][] head;
        final List<Suffix> out = new ArrayList<>();
        final List<Cut> local = new ArrayList<>();
        final List<int[]> sample = new ArrayList<>();
        final FreeAirExact.Screen screen = new FreeAirExact.Screen(tables);
        final boolean[] sprint = new boolean[n];
        final double[] total;
        final Random random;
        final long suffixShare;
        int startState;
        boolean taskAbort;
        long taskSurvivors;
        long walked;
        long solved;
        long passed;

        Walker(int index, int k, double[][][][] support, List<Suffix>[] sigma) {
            this.k = k;
            this.t0 = bounds[k - 1];
            this.t1 = bounds[k];
            int end = t0;
            if (joins[t0]) {
                end = t0 + 1;
                while (joins[end]) end++;
            }
            this.firstEnd = end;
            this.support = support;
            this.sigma = sigma;
            this.px = new double[t1 - t0 + 1][cutCount];
            this.py = new double[t1 - t0 + 1][cutCount];
            this.ps = new double[t1 - t0 + 1][cutCount];
            this.fx = new double[cutCount];
            this.fy = new double[cutCount];
            this.headBound = new double[cutCount];
            this.order = new int[cutCount];
            for (int i = 0; i < cutCount; i++) order[i] = i;
            this.head = new double[cutCount][];
            this.total = new double[cutCount];
            this.random = new Random(31L * index + 7L);
            this.suffixShare = suffixBudget == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, suffixBudget / threads);
        }

        void task(int state, int first, int second) {
            startState = state;
            taskAbort = false;
            taskSurvivors = 0;
            for (int c = 0; c < cutCount; c++) head[c] = forward[k - 1][c][state];
            Arrays.fill(px[0], 0.0);
            Arrays.fill(py[0], 0.0);
            Arrays.fill(ps[0], 0.0);
            if (!joins[t0]) {
                Arrays.fill(fx, 0.0);
                Arrays.fill(fy, 0.0);
                for (int c = 0; c < cutCount; c++) headBound[c] = head[c][0];
            }
            int next = advance(t0, state, 0, first);
            if (next < 0) return;
            walked++;
            if (t0 + 1 == t1) {
                if (second == rules.alphabet[0]) walk(t0 + 1, next, 1);
                return;
            }
            if (!alive(t0 + 1, next, 1, Math.min(cutCount, settings.nodeCuts))) return;
            step(t0 + 1, next, 1, second);
        }

        void step(int t, int state, int depth, int combo) {
            int next = advance(t, state, depth, combo);
            if (next >= 0) walk(t + 1, next, depth + 1);
        }

        int advance(int t, int state, int depth, int combo) {
            int next = rules.next(t, state, combo, variant, key);
            if (next < 0) return -1;
            if (support[0][t + 1 - t0][next] == null) return -1;
            int at = t * KEYS + key[0];
            double[] x0 = px[depth];
            double[] y0 = py[depth];
            double[] s0 = ps[depth];
            double[] x1 = px[depth + 1];
            double[] y1 = py[depth + 1];
            double[] s1 = ps[depth + 1];
            boolean ends = !joins[t + 1];
            boolean firstEnds = ends && t + 1 == firstEnd;
            for (int c = 0; c < cutCount; c++) {
                double x = x0[c] + cuts[c].x[at];
                double y = y0[c] + cuts[c].y[at];
                if (!ends) {
                    x1[c] = x;
                    y1[c] = y;
                    s1[c] = s0[c];
                    continue;
                }
                x1[c] = 0.0;
                y1[c] = 0.0;
                if (firstEnds) {
                    fx[c] = x;
                    fy[c] = y;
                    headBound[c] = joined(head[c], x, y) * SEC;
                    s1[c] = s0[c];
                } else {
                    s1[c] = s0[c] + len(x, y);
                }
            }
            combos[t] = combo;
            return next;
        }

        double joined(double[] before, double x, double y) {
            double best = Double.NEGATIVE_INFINITY;
            for (int d = 0; d < DIRS; d++) {
                double value = before[d] + x * DX[d] + y * DY[d];
                if (value > best) best = value;
            }
            return best;
        }

        boolean alive(int t, int state, int depth, int limit) {
            double[] x = px[depth];
            double[] y = py[depth];
            double[] s = ps[depth];
            boolean open = t < firstEnd;
            boolean runs = joins[t];
            for (int i = 0; i < limit; i++) {
                int c = order[i];
                double[] after = support[c][t - t0][state];
                if (after == null) return false;
                double value;
                if (!runs) {
                    value = headBound[c] + s[c] + after[0];
                } else {
                    double[] before = head[c];
                    double xc = x[c];
                    double yc = y[c];
                    double best = Double.NEGATIVE_INFINITY;
                    if (open) {
                        for (int d = 0; d < DIRS; d++) {
                            double v = before[d] + xc * DX[d] + yc * DY[d] + after[d];
                            if (v > best) best = v;
                        }
                        value = best * SEC;
                    } else {
                        for (int d = 0; d < DIRS; d++) {
                            double v = xc * DX[d] + yc * DY[d] + after[d];
                            if (v > best) best = v;
                        }
                        value = headBound[c] + s[c] + best * SEC;
                    }
                }
                if (value < need[c] - EPS) {
                    promote(i);
                    return false;
                }
            }
            return true;
        }

        void promote(int i) {
            if (i == 0) return;
            int swap = order[i - 1];
            order[i - 1] = order[i];
            order[i] = swap;
        }

        void walk(int t, int state, int depth) {
            if (abort || taskAbort) return;
            walked++;
            if ((walked & 4095) == 0 && outOfTime()) return;
            boolean leaf = t == t1;
            if (!alive(t, state, depth, leaf ? cutCount : Math.min(cutCount, settings.nodeCuts))) return;
            if (leaf) {
                leaf(state, depth);
                return;
            }
            int length = rules.alphabet.length;
            int offset = taskBudget == Long.MAX_VALUE ? 0 : random.nextInt(length);
            for (int i = 0; i < length; i++) step(t, state, depth, rules.alphabet[(i + offset) % length]);
        }

        boolean outOfTime() {
            if (stop.getAsBoolean()) {
                stopped = true;
                abort = true;
                return true;
            }
            if (System.nanoTime() > deadline) {
                budgetHit = true;
                abort = true;
                return true;
            }
            return false;
        }

        void leaf(int state, int depth) {
            double[] x = px[depth];
            double[] y = py[depth];
            double[] s = ps[depth];
            if (sigma == null && k == 1) {
                System.arraycopy(s, 0, total, 0, cutCount);
                finish(state);
                return;
            }
            if (sigma == null) {
                Suffix made = new Suffix();
                made.start = startState;
                made.end = state;
                made.vx = fx.clone();
                made.vy = fy.clone();
                made.vs = s.clone();
                made.combos = new byte[t1 - t0];
                for (int t = t0; t < t1; t++) made.combos[t - t0] = (byte) combos[t];
                keep(made);
                return;
            }
            List<Suffix> list = sigma[state];
            if (list == null) return;
            boolean open = t1 < firstEnd;
            for (Suffix suffix : list) {
                if (abort || taskAbort) return;
                boolean dead = false;
                for (int i = 0; i < cutCount; i++) {
                    int c = order[i];
                    double sx = x[c] + suffix.vx[c];
                    double sy = y[c] + suffix.vy[c];
                    double value;
                    if (open) value = joined(head[c], sx, sy) * SEC + suffix.vs[c];
                    else value = headBound[c] + s[c] + len(sx, sy) + suffix.vs[c];
                    if (value + maxAir[c][suffix.end] < need[c] - EPS) {
                        dead = true;
                        promote(i);
                        break;
                    }
                }
                if (dead) continue;
                if (k == 1) {
                    for (int c = 0; c < cutCount; c++) {
                        total[c] = s[c] + len(x[c] + suffix.vx[c], y[c] + suffix.vy[c]) + suffix.vs[c];
                    }
                    for (int t = t1; t < last; t++) combos[t] = suffix.combos[t - t1];
                    finish(suffix.end);
                } else {
                    Suffix made = new Suffix();
                    made.start = startState;
                    made.end = suffix.end;
                    made.vx = new double[cutCount];
                    made.vy = new double[cutCount];
                    made.vs = new double[cutCount];
                    for (int c = 0; c < cutCount; c++) {
                        double sx = x[c] + suffix.vx[c];
                        double sy = y[c] + suffix.vy[c];
                        if (open) {
                            made.vx[c] = sx;
                            made.vy[c] = sy;
                            made.vs[c] = suffix.vs[c];
                        } else {
                            made.vx[c] = fx[c];
                            made.vy[c] = fy[c];
                            made.vs[c] = s[c] + len(sx, sy) + suffix.vs[c];
                        }
                    }
                    made.combos = new byte[last - t0];
                    for (int t = t0; t < t1; t++) made.combos[t - t0] = (byte) combos[t];
                    System.arraycopy(suffix.combos, 0, made.combos, t1 - t0, suffix.combos.length);
                    keep(made);
                }
            }
        }

        void keep(Suffix s) {
            out.add(s);
            if (out.size() > suffixShare) {
                budgetHit = true;
                abort = true;
            }
        }

        void finish(int end) {
            Finals list = finals.computeIfAbsent(end, MomentumSearch.this::buildFinals);
            int count = list.keys.size;
            if (count == 0) return;
            double room = need[0] - total[0] - EPS;
            double[] first = list.air[0];
            int lo = 0;
            int hi = count;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (first[mid] >= room) lo = mid + 1;
                else hi = mid;
            }
            for (int f = 0; f < lo; f++) {
                if (abort || taskAbort) return;
                boolean dead = false;
                for (int c = 1; c < cutCount; c++) {
                    if (total[c] < need[c] - list.air[c][f] - EPS) {
                        dead = true;
                        break;
                    }
                }
                if (dead) continue;
                if (taskBudget == Long.MAX_VALUE && !list.fresh[f]) continue;
                list.keys.decode(f, combos);
                if (taskBudget != Long.MAX_VALUE) {
                    passed++;
                    if ((passed & 3) == 0 && sample.size() < 4000) sample.add(combos.clone());
                    if (++taskSurvivors > taskBudget) {
                        taskAbort = true;
                        budgetHit = true;
                        return;
                    }
                }
                for (int c = 0; c < local.size(); c++) {
                    Cut cut = local.get(c);
                    if (cutValue(cut, combos) > keep) {
                        dead = true;
                        cut.rejects.incrementAndGet();
                        if (c > 0) {
                            local.set(c, local.get(c - 1));
                            local.set(c - 1, cut);
                        }
                        break;
                    }
                }
                if (dead) continue;
                if (!list.fresh[f]) continue;
                if (known != null && known.accepts(combos)) continue;
                MomentumRules.sprintOf(combos, variant, sprint);
                solved++;
                if ((solved & 63) == 0 && outOfTime()) return;
                FreeAirExact.Result r = screen.solve(combos, sprint, keep);
                if (r.dual <= keep) {
                    emit(combos, r.dual);
                } else if (r.lambda != null) {
                    Cut cut = cutOf(r.lambda);
                    if (local.size() < 128) local.add(cut);
                    else local.set(local.size() - 1, cut);
                }
            }
        }

        void flush() {
            nodes.addAndGet(walked);
            screens.addAndGet(solved);
        }
    }

    private void emit(int[] combos, double value) {
        char[] text = new char[n + 1];
        text[0] = (char) ('0' + variant);
        for (int t = 0; t < n; t++) text[t + 1] = (char) ('a' + combos[t]);
        if (!sent.add(new String(text))) return;
        sink.kept(combos.clone(), variant, value);
        if (emitted.incrementAndGet() >= settings.keptCap) {
            capped = true;
            abort = true;
        }
    }

    private void listFinals(int t, int state, int[] combos, int[] key, Packed out, List<Boolean> fresh) {
        if (tooLarge) return;
        if (t == n) {
            if (finalsCount.incrementAndGet() > finalsLimit) {
                tooLarge = true;
                abort = true;
                return;
            }
            out.add(combos);
            fresh.add(rules.edgesOf(state) == rules.maxEdges);
            return;
        }
        for (int combo : rules.alphabet) {
            int next = rules.next(t, state, combo, variant, key);
            if (next < 0) continue;
            combos[t] = combo;
            listFinals(t + 1, next, combos, key, out, fresh);
        }
    }

    private Finals buildFinals(int end) {
        Packed raw = new Packed(last, n);
        List<Boolean> freshRaw = new ArrayList<>();
        int[] key = new int[1];
        listFinals(last, end, new int[n], key, raw, freshRaw);
        int count = raw.size;
        double[][] air = new double[cutCount][count];
        int[] combos = new int[n];
        for (int f = 0; f < count; f++) {
            raw.decode(f, combos);
            int state = end;
            for (int t = last; t < n; t++) {
                state = rules.next(t, state, combos[t], variant, key);
                int at = t * KEYS + key[0];
                for (int c = 0; c < cutCount; c++) air[c][f] += len(cuts[c].x[at], cuts[c].y[at]);
            }
        }
        Integer[] order = new Integer[count];
        for (int f = 0; f < count; f++) order[f] = f;
        final double[] first = cutCount > 0 ? air[0] : new double[count];
        Arrays.sort(order, (a, b) -> Double.compare(first[b], first[a]));
        Finals out = new Finals();
        out.keys = raw.permuted(order);
        out.air = new double[cutCount][count];
        out.fresh = new boolean[count];
        for (int f = 0; f < count; f++) {
            out.fresh[f] = freshRaw.get(order[f]);
            for (int c = 0; c < cutCount; c++) out.air[c][f] = air[c][order[f]];
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Suffix>[] level(int k, List<Suffix>[] sigma) {
        final double[][][][] support = completions(k, sigma);
        int t0 = bounds[k - 1];
        final List<int[]> tasks = new ArrayList<>();
        for (int st = 0; st < states; st++) {
            if (!reach[t0][st] || forward[k - 1][0][st] == null || support[0][0][st] == null) continue;
            for (int first : rules.alphabet) for (int second : rules.alphabet) tasks.add(new int[]{st, first, second});
        }
        Collections.shuffle(tasks, new Random(5));
        final Walker[] walkers = new Walker[threads];
        for (int i = 0; i < threads; i++) walkers[i] = new Walker(i, k, support, sigma);
        final AtomicInteger next = new AtomicInteger();
        runWorkers(threads, worker -> {
            Walker walker = walkers[worker];
            int at;
            while ((at = next.getAndIncrement()) < tasks.size()) {
                int[] task = tasks.get(at);
                walker.task(task[0], task[1], task[2]);
            }
            walker.flush();
        });
        List<Suffix>[] out = new List[states];
        for (Walker walker : walkers) {
            for (Suffix s : walker.out) {
                if (out[s.start] == null) out[s.start] = new ArrayList<>();
                out[s.start].add(s);
            }
            pool.addAll(walker.local);
            survivors.addAll(walker.sample);
        }
        return out;
    }

    private List<int[]> mixedSamples(Random random, List<Suffix>[] sigma) {
        List<int[]> mixed = new ArrayList<>();
        int t0 = bounds[m - 1];
        int[] key = new int[1];
        for (int tries = 0; tries < 200000 && mixed.size() < 3000; tries++) {
            int[] combos = randomSchedule(random);
            if (combos == null) continue;
            int state = stateAt(combos, t0);
            if (state < 0 || sigma[state] == null || sigma[state].isEmpty()) continue;
            Suffix suffix = sigma[state].get(random.nextInt(sigma[state].size()));
            for (int t = t0; t < last; t++) combos[t] = suffix.combos[t - t0];
            int end = suffix.end;
            boolean ok = true;
            for (int t = last; t < n && ok; t++) {
                int next = -1;
                for (int q = 0; q < 40 && next < 0; q++) {
                    int combo = random.nextDouble() < 0.85 ? combos[t - 1]
                            : rules.alphabet[random.nextInt(rules.alphabet.length)];
                    next = rules.next(t, end, combo, variant, key);
                    if (next >= 0) combos[t] = combo;
                }
                if (next < 0) ok = false;
                end = next;
            }
            if (ok) mixed.add(combos);
        }
        return mixed;
    }

    public Outcome run() {
        if (last < 1) return Outcome.GAVE_UP;
        Random random = new Random(11);
        FreeAirExact.Screen screen = new FreeAirExact.Screen(tables);
        learn(randomSchedules(random, pool.isEmpty() ? settings.warmup : settings.carriedWarmup), screen);
        List<int[]> judge = randomSchedules(random, settings.judges);
        pickCuts(judge, settings.cuts);
        prepare();
        for (int round = 0; ; round++) {
            boolean probe = round < PROBE_SURVIVORS.length;
            abort = false;
            budgetHit = false;
            deadline = Long.MAX_VALUE;
            taskBudget = probe ? PROBE_SURVIVORS[round] : Long.MAX_VALUE;
            suffixBudget = probe ? Math.min(PROBE_SUFFIXES, suffixLimit) : suffixLimit;
            survivors.clear();
            if (stop.getAsBoolean()) return Outcome.STOPPED;
            List<Suffix>[] sigma = level(m, null);
            List<Suffix>[] lastLevel = probe && m > 1 && !abort ? sigma : null;
            for (int k = m - 1; k >= 1 && !abort; k--) {
                deadline = probe ? System.nanoTime() + settings.probeNanos : Long.MAX_VALUE;
                sigma = level(k, sigma);
            }
            if (capped) return Outcome.CAPPED;
            if (stopped) return Outcome.STOPPED;
            if (tooLarge) return Outcome.GAVE_UP;
            if (!budgetHit) return Outcome.COMPLETE;
            if (!probe) return Outcome.GAVE_UP;
            List<int[]> mixed = lastLevel != null ? mixedSamples(random, lastLevel) : new ArrayList<>();
            lastLevel = null;
            sigma = null;
            learn(mixed.subList(0, mixed.size() / 2), screen);
            List<int[]> next = new ArrayList<>(judge.subList(0, Math.min(128, judge.size())));
            next.addAll(mixed.subList(mixed.size() / 2, mixed.size()));
            next.addAll(survivors);
            pickCuts(next, settings.cuts);
            prepare();
            finals.clear();
            finalsCount.set(0);
        }
    }
}
