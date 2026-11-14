package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphRunner;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver.Candidate;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PoolCertifier {

    private static final int CERTIFY_THREAD_CAP = 8;
    private static final int GPU_CERTIFY_THREAD_CAP = Integer.getInteger("pkc.pool.gpuCertifyThreads", 16);
    private static final long WARM_BUDGET_NANOS = 300_000_000L;
    private static final long SHORT_PASS_NANOS = Long.getLong("pkc.pool.shortPassMs", 100L) * 1_000_000L;
    private static final long FLASH_PASS_NANOS = Long.getLong("pkc.pool.flashPassMs", 16L) * 1_000_000L;
    private static final boolean STREAM_STATS = Boolean.getBoolean("pkc.pool.stageTimes");
    private static final long STREAM_STATS_NANOS = 2_000_000_000L;
    private static final int MULTI_MAX_CERTIFY = 6000;
    private static final long MULTI_NEAR_SEARCH_NANOS = 300_000_000L;

    private final StructurePoolDriver.Config cfg;
    private final AtomicBoolean cancel;
    private final StructurePoolDriver.Progress progress;
    private final NoTurnProblem problem;
    private final NoTurnCertifier cert;
    private final ExecutorService exec;
    private final int threads;
    private final long deadline;
    private final boolean maximize;
    private int maxCertify;
    private final long nearBudgetNanos;

    private final Set<String> found = new HashSet<>();
    private final List<Candidate> queue = new ArrayList<>();
    private final List<Candidate> retryQueue = new ArrayList<>();
    private final List<Candidate> everything = new ArrayList<>();
    private boolean retryDirty;
    private final Set<String> warmTried = new HashSet<>();
    private NoTurnResult best;
    private int certs;
    private double extraCerts;
    private long extraDeadline = Long.MAX_VALUE;

    private final Set<String> tried = new HashSet<>();

    PoolCertifier(ExactJumpModel model, StructurePoolDriver.Config cfg, AtomicBoolean cancel,
                  StructurePoolDriver.Progress progress, NoTurnProblem problem, long deadline, boolean multi) {
        this.cfg = cfg;
        this.cancel = cancel;
        this.progress = progress;
        this.problem = problem;
        this.cert = new NoTurnCertifier(model, cfg.playable);
        int cap = PoolScreenAccelerator.get() != null ? GPU_CERTIFY_THREAD_CAP : CERTIFY_THREAD_CAP;
        this.threads = Math.min(cap, NoTurnParallel.resolveThreads(cfg.threads));
        this.exec = Executors.newFixedThreadPool(threads);
        this.flights = new ExecutorCompletionService<>(exec);
        this.deadline = deadline;
        this.maximize = problem.objective.sense == Objective.Sense.MAX;
        this.maxCertify = multi ? Math.max(cfg.maxCertify, MULTI_MAX_CERTIFY) : cfg.maxCertify;
        this.nearBudgetNanos = multi ? Math.min(cfg.nearSearchBudgetNanos, MULTI_NEAR_SEARCH_NANOS)
                : cfg.nearSearchBudgetNanos;
        boolean flash = FLASH_PASS_NANOS > 0L && FLASH_PASS_NANOS < SHORT_PASS_NANOS;
        boolean retry = SHORT_PASS_NANOS < RETRY_PASS_NANOS;
        int count = 1 + (flash ? 1 : 0) + (retry ? 1 : 0);
        long[] budgets = new long[count];
        double[] weights = new double[count];
        int at = 0;
        if (flash) {
            budgets[at] = FLASH_PASS_NANOS;
            weights[at++] = FLASH_WEIGHT;
        }
        budgets[at] = SHORT_PASS_NANOS;
        weights[at++] = SHORT_WEIGHT;
        if (retry) {
            budgets[at] = RETRY_PASS_NANOS;
            weights[at] = RETRY_WEIGHT;
        }
        this.ladder = new CertifyLadder(cfg, budgets, weights, cfg.streamCertify, queue, familyQueue, byValue,
                momentumQueue);
        this.passWall = new long[budgets.length];
        this.passCpu = new long[budgets.length];
        this.passLanded = new int[budgets.length];
        this.streamStart = System.nanoTime();
    }

    NoTurnResult best() {
        return best;
    }

    void resetCertifyCount() {
        certs = 0;
    }

    void openChangeTurns() {
        changeLeft = maxCertify;
    }

    void liftCertifyCap() {
        certs = 0;
        maxCertify = Integer.MAX_VALUE;
        changeLeft = Integer.MAX_VALUE;
    }

    boolean variationsOver() {
        return best != null && (extraCerts >= cfg.extraCertify || System.nanoTime() > extraDeadline);
    }

    void shutdown() {
        exec.shutdownNow();
    }

    private static final class Flight {
        final Candidate candidate;
        final String key;
        final CertifyLadder.Ticket ticket;
        final AtomicBoolean cancel = new AtomicBoolean(false);
        NoTurnResult result;
        boolean failed;
        double reached = Double.POSITIVE_INFINITY;
        long wallNanos;
        long cpuNanos;

        Flight(CertifyLadder.Ticket ticket) {
            this.candidate = ticket.candidate;
            this.key = ticket.key;
            this.ticket = ticket;
        }
    }

    private final ExecutorCompletionService<Flight> flights;
    private final List<Flight> flying = new ArrayList<>();
    private final Set<String> flyingKeys = new HashSet<>();
    private final List<Candidate> momentumQueue = new ArrayList<>();
    private final CertifyLadder ladder;
    private final long streamStart;
    private boolean fallbackFamily;
    private long statsAt;
    private int lostFlights;
    private final long[] passWall;
    private final long[] passCpu;
    private final int[] passLanded;

    boolean stream() {
        boolean moved = collect(0L);
        if (stopped()) return !flying.isEmpty() || moved;
        moved |= launch();
        if (moved) return true;
        if (!flying.isEmpty()) {
            collect(STREAM_WAIT_MS);
            return true;
        }
        try {
            Thread.sleep(STREAM_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    void land(boolean abort) {
        if (abort) for (Flight f : flying) f.cancel.set(true);
        while (!flying.isEmpty()) {
            if (!abort && (stopped() || Thread.currentThread().isInterrupted())) {
                for (Flight f : flying) f.cancel.set(true);
            }
            collect(STREAM_WAIT_MS);
        }
        settle();
    }

    private void settle() {
        if (ladder.settle(SHORT_PASS_NANOS, queue, familyQueue, retryQueue)) retryDirty = true;
    }

    private boolean launch() {
        boolean launched = false;
        ladder.sort();
        if (STREAM_STATS && System.nanoTime() >= statsAt) {
            statsAt = System.nanoTime() + STREAM_STATS_NANOS;
            StringBuilder flown = new StringBuilder();
            StringBuilder waiting = new StringBuilder();
            for (int pass = 0; pass < ladder.passCount(); pass++) {
                flown.append(pass == 0 ? "" : "/").append(ladder.flown(pass));
                waiting.append(pass == 0 ? "" : "/").append(ladder.waiting(pass));
            }
            StringBuilder wall = new StringBuilder();
            StringBuilder cpu = new StringBuilder();
            for (int pass = 0; pass < ladder.passCount(); pass++) {
                int landed = Math.max(1, passLanded[pass]);
                wall.append(pass == 0 ? "" : "/").append(passWall[pass] / landed / 100_000L / 10.0);
                cpu.append(pass == 0 ? "" : "/").append(passCpu[pass] / landed / 100_000L / 10.0);
            }
            System.out.println("CERTIFY flown=" + flown + " waiting=" + waiting + " flying=" + flying.size() + " lost="
                    + lostFlights + " wallMs=" + wall + " cpuMs=" + cpu + " atMs="
                    + (System.nanoTime() - streamStart) / 1_000_000L + StructurePoolDriver.runtimeNote());
        }
        while (flying.size() < threads && !stopped()) {
            CertifyLadder.Ticket next = nextTicket();
            if (next == null) break;
            final Flight f = new Flight(next);
            flying.add(f);
            flyingKeys.add(f.key);
            certs++;
            if (StructurePoolDriver.watching(f.candidate.combos)) {
                System.out.println("[watch] certify pass " + next.pass + " starts, budget ms=" + next.budget / 1_000_000L
                        + " flown=" + ladder.flown(next.pass) + " waiting=" + ladder.waiting(next.pass) + " atMs="
                        + (System.nanoTime() - streamStart) / 1_000_000L + " keys="
                        + NoTurnKeys.describe(f.candidate.combos));
            }
            flights.submit(() -> {
                long wall = STREAM_STATS ? System.nanoTime() : 0L;
                long cpu = STREAM_STATS ? threadCpu() : 0L;
                try {
                    if (!f.cancel.get() && System.nanoTime() <= deadline) {
                        f.result = certifyOne(f.candidate, false, f.ticket.budget, f.cancel, f);
                        if (f.result != null) progress.found(f.result);
                    }
                } catch (RuntimeException | Error e) {
                    f.result = null;
                    f.failed = true;
                }
                if (STREAM_STATS) {
                    f.wallNanos = System.nanoTime() - wall;
                    f.cpuNanos = threadCpu() - cpu;
                }
                return f;
            });
            launched = true;
        }
        return launched;
    }

    static long threadCpu() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : 0L;
    }

    private boolean closed(String key) {
        return found.contains(key) || tried.contains(key) || flyingKeys.contains(key);
    }

    private CertifyLadder.Ticket nextTicket() {
        CertifyLadder.Ticket t = ladder.next(this::closed, changeLeft > 0);
        if (t != null) {
            if (t.pass == 0 && t.source == CertifyLadder.SOURCE_CHANGE) changeLeft--;
            return t;
        }
        if (certs >= maxCertify) return null;
        for (int k = 0; k < 2; k++) {
            fallbackFamily = !fallbackFamily;
            t = ladder.anyOfFirst(this::closed, fallbackFamily);
            if (t != null) return t;
        }
        return null;
    }

    private boolean collect(long waitMs) {
        boolean any = false;
        java.util.concurrent.Future<Flight> done;
        try {
            done = waitMs > 0L ? flights.poll(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS) : flights.poll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            done = null;
        }
        while (done != null) {
            Flight f;
            try {
                f = done.get();
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                f = null;
            }
            if (f != null) {
                flying.remove(f);
                flyingKeys.remove(f.key);
                landed(f);
                any = true;
            }
            done = flights.poll();
        }
        return any;
    }

    private void landed(Flight f) {
        Candidate c = f.candidate;
        if (f.failed) lostFlights++;
        if (STREAM_STATS && !f.cancel.get()) {
            passWall[f.ticket.pass] += f.wallNanos;
            passCpu[f.ticket.pass] += f.cpuNanos;
            passLanded[f.ticket.pass]++;
        }
        if (StructurePoolDriver.watching(c.combos)) {
            System.out.println("[watch] certify pass " + f.ticket.pass + (f.result != null ? " lands" : f.failed ? " failed"
                    : f.cancel.get() ? " cancelled" : " misses") + " reached=" + f.reached + " wallMs="
                    + f.wallNanos / 100_000L / 10.0 + " cpuMs=" + f.cpuNanos / 100_000L / 10.0 + " keys="
                    + NoTurnKeys.describe(c.combos));
        }
        if (f.cancel.get() && f.result == null) return;
        if (f.result == null) {
            if (c.family && f.ticket.budget >= SHORT_PASS_NANOS) shortTried.add(f.key);
            if (!ladder.missed(f.ticket)) tried.add(f.key);
        } else {
            tried.add(f.key);
        }
        if (best != null) extraCerts += Math.min(1.0, (double) f.ticket.budget / SHORT_PASS_NANOS);
        if (f.result == null) return;
        boolean hadBest = best != null;
        found.add(keyOf(f.result));
        if (best == null || StructurePoolDriver.betterResult(maximize, f.result, best)) best = f.result;
        if (!hadBest) extraDeadline = System.nanoTime() + cfg.extraCertifyNanos;
        progress.update("certify streaming: " + certs + " certified (parallel " + threads + ") best=" + best.describe(),
                fraction());
        List<NoTurnResult> hits = new ArrayList<>(1);
        hits.add(f.result);
        exploreWarm(hits);
    }

    private boolean stopped() {
        return (cancel != null && cancel.get()) || System.nanoTime() > deadline || variationsOver();
    }

    private double fraction() {
        if (best == null) return Math.min(0.95, 0.4 + 0.55 * certs / Math.max(1.0, Math.min(maxCertify, cfg.maxCertify)));
        double window = Math.max(1L, cfg.extraCertifyNanos);
        double elapsed = (System.nanoTime() - (extraDeadline - cfg.extraCertifyNanos)) / window;
        return 0.4 + 0.59 * Math.max(0.0, Math.min(1.0, elapsed));
    }

    void offer(List<Candidate> ranked) {
        everything.addAll(ranked);
        for (Candidate c : ranked) {
            ladder.offer(c);
            if (!c.family) turn = CHANGE_TURN;
        }
    }

    private final List<Candidate> byValue = new ArrayList<>();
    private final List<Candidate> familyQueue = new ArrayList<>();
    private boolean familyTurn;
    private static final int CHANGE_TURN = 0;
    private static final int FAMILY_COST_TURN = 1;
    private static final int RETRY_TURN = 3;
    private static final int TURNS = 4;
    private static final int VALUE_TURN = 2;
    private static final double FLASH_WEIGHT = 4.0;
    private static final double SHORT_WEIGHT = 2.0;
    private static final double RETRY_WEIGHT = 1.0;
    private static final long STREAM_WAIT_MS = 2L;

    private static final int RETRY_BATCH = 4;
    private static final long RETRY_PASS_NANOS = 500_000_000L;
    private int turn;
    private int changeLeft;
    private final java.util.Set<String> shortTried = new java.util.HashSet<>();

    void reofferAll() {
        queue.clear();
        familyQueue.clear();
        for (Candidate c : everything) (c.family ? familyQueue : queue).add(c);
        ladder.touchFirst();
        retryQueue.clear();
    }

    boolean certifyNext(boolean ja, boolean goodOnly) {
        if (stopped() || certs >= maxCertify) return false;
        sortQueues();
        familyTurn = !familyTurn;
        if (takeBatch(familyTurn ? familyQueue : queue, ja, goodOnly, false)) return true;
        if (takeBatch(familyTurn ? queue : familyQueue, ja, goodOnly, false)) return true;
        if (retryDirty) {
            retryQueue.sort(StructurePoolDriver.rankOrder(cfg));
            retryDirty = false;
        }
        return takeBatch(retryQueue, ja, goodOnly, true);
    }

    boolean certifyTurn() {
        if (stopped()) return false;
        sortQueues();
        for (int k = 0; k < TURNS; k++) {
            int source = turn;
            turn = (turn + 1) % TURNS;
            if (source == CHANGE_TURN) {
                int before = certs;
                if (changeLeft > 0 && takeBatch(queue, false, true, false, Math.min(cfg.perEdgeCertify, changeLeft))) {
                    changeLeft -= certs - before;
                    return true;
                }
            } else if (source == FAMILY_COST_TURN) {
                if (takeBatch(familyQueue, false, true, false, cfg.perEdgeCertify)) return true;
            } else if (source == RETRY_TURN) {
                if (retryTurn()) return true;
            } else if (certifyFamilyByValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean retryTurn() {
        if (SHORT_PASS_NANOS >= RETRY_PASS_NANOS || retryQueue.isEmpty()) return false;
        if (retryDirty) {
            retryQueue.sort(StructurePoolDriver.rankOrder(cfg));
            retryDirty = false;
        }
        List<Candidate> batch = new ArrayList<>(RETRY_BATCH);
        int i = 0;
        while (i < retryQueue.size() && batch.size() < RETRY_BATCH) {
            Candidate c = retryQueue.get(i++);
            String key = scheduleKey(c.combos, c.engage, false);
            if (!found.contains(key) && !tried.contains(key)) batch.add(c);
        }
        retryQueue.subList(0, i).clear();
        if (batch.isEmpty()) return false;
        certifyBatch(batch, false, RETRY_PASS_NANOS, "retry", retryQueue.size() + batch.size(), false);
        return true;
    }

    private void sortQueues() {
        ladder.sortFirst();
    }

    boolean certifyFamilyByValue() {
        if (stopped()) return false;
        ladder.sortFirst();
        List<Candidate> batch = new ArrayList<>(cfg.perEdgeCertify);
        int i = 0;
        while (i < byValue.size() && batch.size() < cfg.perEdgeCertify) {
            Candidate c = byValue.get(i++);
            String key = scheduleKey(c.combos, c.engage, false);
            if (!found.contains(key) && !tried.contains(key) && !shortTried.contains(key)) batch.add(c);
        }
        byValue.subList(0, i).clear();
        if (batch.isEmpty()) return false;
        boolean shortPass = SHORT_PASS_NANOS < cfg.searchBudgetNanos;
        certifyBatch(batch, false, shortPass ? SHORT_PASS_NANOS : cfg.searchBudgetNanos,
                shortPass ? "screen-exact short by value" : "screen-exact by value", byValue.size() + batch.size(),
                shortPass);
        return true;
    }

    private boolean takeBatch(List<Candidate> from, boolean ja, boolean goodOnly, boolean longPass) {
        return takeBatch(from, ja, goodOnly, longPass, Math.min(cfg.perEdgeCertify, maxCertify - certs));
    }

    private boolean takeBatch(List<Candidate> from, boolean ja, boolean goodOnly, boolean longPass, int room) {
        List<Candidate> batch = new ArrayList<>(room);
        int tier = -1;
        int i = 0;
        int firstTaken = -1;
        boolean flash = false;
        while (i < from.size() && batch.size() < room) {
            Candidate c = from.get(i);
            int t = StructurePoolDriver.certifyTier(cfg, c);
            if (goodOnly && t >= StructurePoolDriver.SCREEN_MISS_TIER) break;
            if (tier < 0) {
                tier = t;
                flash = c.flash && !longPass && cfg.repairFlashNanos > 0;
            } else if (t != tier || (c.flash && !longPass && cfg.repairFlashNanos > 0) != flash) break;
            if (firstTaken < 0) firstTaken = i;
            String key = scheduleKey(c.combos, c.engage, ja);
            boolean shortDone = c.family && !longPass && !ja && shortTried.contains(key);
            if (!found.contains(key) && !tried.contains(key) && !shortDone) batch.add(c);
            i++;
        }
        if (firstTaken >= 0) from.subList(firstTaken, i).clear();
        if (batch.isEmpty()) return i > 0;
        boolean exact = tier == StructurePoolDriver.SCREEN_EXACT_TIER;
        boolean shortPass = flash || (!longPass && SHORT_PASS_NANOS < (exact ? cfg.searchBudgetNanos : nearBudgetNanos));
        long budget = flash ? cfg.repairFlashNanos
                : shortPass ? SHORT_PASS_NANOS : exact ? cfg.searchBudgetNanos : nearBudgetNanos;
        String label = (tier >= StructurePoolDriver.SCREEN_MISS_TIER ? "screen-miss"
                : exact ? (shortPass ? "screen-exact short" : longPass ? "screen-exact retry" : "screen-exact")
                : "screen-near") + (flash ? " flash" : "") + (ja ? "+ja" : "");
        certifyBatch(batch, ja, budget, label, from.size() + batch.size(), shortPass);
        return true;
    }

    private void certifyBatch(List<Candidate> batch, boolean ja, long budget, String label, int tierSize,
                              boolean shortPass) {
        final int certsBefore = certs;
        final AtomicInteger done = new AtomicInteger();
        progress.update("certify " + label + " batch of " + batch.size() + " from " + tierSize
                + " (parallel " + threads + ")" + (best != null ? " best=" + best.describe() : ""), fraction());
        List<NoTurnResult> res = NoTurnParallel.collectAll(exec, batch.size(), cancel, (idx, tc) -> {
            if (System.nanoTime() > deadline) return null;
            NoTurnResult rr = certifyOne(batch.get(idx), ja, budget, tc, null);
            int doneNow = certsBefore + done.incrementAndGet();
            progress.update("certify " + label + " " + doneNow + " certified (parallel " + threads + ")", fraction());
            if (rr != null) progress.found(rr);
            return rr;
        });
        certs += batch.size();
        for (int bi = 0; bi < batch.size(); bi++) {
            Candidate c = batch.get(bi);
            if (shortPass && res.get(bi) == null) {
                boolean first = !c.family || shortTried.add(scheduleKey(c.combos, c.engage, ja));
                if (first) {
                    retryQueue.add(c);
                    retryDirty = true;
                }
            } else {
                tried.add(scheduleKey(c.combos, c.engage, ja));
            }
        }
        if (best != null) extraCerts += batch.size();
        boolean hadBest = best != null;
        List<NoTurnResult> hits = new ArrayList<>();
        for (NoTurnResult r : res) {
            if (r == null) continue;
            hits.add(r);
            found.add(keyOf(r));
            if (best == null || StructurePoolDriver.betterResult(maximize, r, best)) best = r;
        }
        if (best != null && !hadBest) extraDeadline = System.nanoTime() + cfg.extraCertifyNanos;
        if (!hits.isEmpty()) exploreWarm(hits);
    }

    private NoTurnResult certifyOne(Candidate c, boolean ja, long budget, AtomicBoolean cancelTok, Flight flight) {
        JumpSpec spec = problem.buildSpec(c.combos, c.sprint, cfg.turnCombo, ja);
        long t0 = System.nanoTime();
        NoTurnCertifier.Result cr = cert.certifySearch(spec, budget, cancelTok);
        if (GraphRunner.TRACE) {
            System.out.println(String.format(Locale.ROOT, "[pool] certify ms=%.1f feasible=%s ja=%s engage=%d keys=%s",
                    (System.nanoTime() - t0) / 1e6, cr != null && cr.feasible, ja, c.engage,
                    NoTurnKeys.describe(c.combos)));
        }
        if (cr == null || !cr.feasible) {
            if (flight != null && cr != null) flight.reached = cr.reached;
            return null;
        }
        return bind(c.combos, c.sprint, c.engage, ja, cr);
    }

    private NoTurnResult bind(int[] combos, boolean[] sprint, int engage, boolean ja, NoTurnCertifier.Result cr) {
        NoTurnResult out = new NoTurnResult(combos.clone(), sprint.clone(), cfg.turnCombo, ja || problem.jaLast,
                StructurePoolDriver.countEdges(cfg, problem, combos), engage, cr.objective, cr.violation, cr.startX,
                cr.startZ, cr.yaws);
        out.pressCount = StructurePoolDriver.countPresses(cfg, problem, combos);
        out.jaAll = problem.jaAll;
        return out;
    }

    static String scheduleKey(int[] combos, int engage, boolean ja) {
        StringBuilder sb = new StringBuilder(combos.length + 12);
        sb.append(NoTurnKeys.key(combos));
        sb.append('|').append(engage);
        if (ja) sb.append("|ja");
        return sb.toString();
    }

    private static String keyOf(NoTurnResult r) {
        return scheduleKey(r.combos, r.sprintEngage, r.ja);
    }

    private static final class WarmItem {
        final int[] combos;
        final boolean[] sprint;
        final int engage;
        final boolean ja;
        final double[] seed;

        WarmItem(int[] combos, boolean[] sprint, int engage, boolean ja, double[] seed) {
            this.combos = combos;
            this.sprint = sprint;
            this.engage = engage;
            this.ja = ja;
            this.seed = seed;
        }
    }

    private void addNeighbour(List<WarmItem> out, int[] combos, int engageReq, boolean ja, double[] seed) {
        int latch = engageReq < 0 ? Integer.MAX_VALUE : engageReq;
        boolean[] sprint = NoTurnKeys.latchSprint(combos, latch);
        int eng = NoTurnKeys.firstSprint(sprint);
        String key = scheduleKey(combos, eng, ja);
        if (found.contains(key) || !warmTried.add(key)) return;
        out.add(new WarmItem(combos, sprint, eng, ja, seed));
    }

    private List<WarmItem> neighbours(NoTurnResult r) {
        List<WarmItem> out = new ArrayList<>();
        int[] base = r.combos;
        int last = base.length - 1;
        int engage = r.sprintEngage;
        boolean ja = r.ja;
        double[] seed = r.yaws;
        for (int b = 1; b <= last; b++) {
            if (base[b] == base[b - 1]) continue;
            int[] left = base.clone();
            left[b - 1] = base[b];
            addNeighbour(out, left, engage, ja, seed);
            int[] right = base.clone();
            right[b] = base[b - 1];
            addNeighbour(out, right, engage, ja, seed);
        }
        addNeighbour(out, base.clone(), engage - 1, ja, seed);
        addNeighbour(out, base.clone(), engage + 1, ja, seed);
        for (int jt : problem.jumpTicks) {
            if (jt > last) continue;
            int end = jt;
            while (end + 1 <= last && base[end + 1] == base[jt]) end++;
            for (int combo : cfg.takeoffCombos) {
                if (combo == base[jt]) continue;
                int[] v = base.clone();
                for (int t = jt; t <= end; t++) v[t] = combo;
                addNeighbour(out, v, engage, ja, seed);
            }
        }
        return out;
    }

    private NoTurnResult certifyWarmOne(WarmItem w, long budget, AtomicBoolean tc) {
        JumpSpec spec = problem.buildSpec(w.combos, w.sprint, cfg.turnCombo, w.ja);
        NoTurnCertifier.Result cr = cert.certifyWarm(spec, w.seed, budget, tc);
        if (cr == null || !cr.feasible) return null;
        return bind(w.combos, w.sprint, w.engage, w.ja, cr);
    }

    private void exploreWarm(List<NoTurnResult> seeds) {
        ArrayDeque<NoTurnResult> work = new ArrayDeque<>(seeds);
        int tried = 0;
        int lines = 0;
        while (!work.isEmpty()) {
            if (stopped()) return;
            final List<WarmItem> nbrs = neighbours(work.poll());
            for (int from = 0; from < nbrs.size(); from += threads) {
                if (stopped()) return;
                int take = Math.min(threads, nbrs.size() - from);
                final int base = from;
                progress.update("explore neighbours: " + lines + " strats from " + tried + " warm certifies (parallel "
                        + threads + ")", fraction());
                List<NoTurnResult> res = NoTurnParallel.collectAll(exec, take, cancel, (idx, tc) -> {
                    long budget = Math.min(WARM_BUDGET_NANOS, extraDeadline - System.nanoTime());
                    if (budget <= 0) return null;
                    return certifyWarmOne(nbrs.get(base + idx), budget, tc);
                });
                tried += take;
                for (NoTurnResult r : res) {
                    if (r == null) continue;
                    lines++;
                    found.add(keyOf(r));
                    if (StructurePoolDriver.betterResult(maximize, r, best)) best = r;
                    progress.found(r);
                    work.add(r);
                }
            }
        }
    }
}
