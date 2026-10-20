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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PoolCertifier {

    private static final int CERTIFY_THREAD_CAP = 8;
    private static final int GPU_CERTIFY_THREAD_CAP = Integer.getInteger("pkc.pool.gpuCertifyThreads", 16);
    private static final long WARM_BUDGET_NANOS = 300_000_000L;
    private static final long SHORT_PASS_NANOS = Long.getLong("pkc.pool.shortPassMs", 500L) * 1_000_000L;
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
    private boolean queueDirty;
    private boolean retryDirty;
    private final Set<String> warmTried = new HashSet<>();
    private NoTurnResult best;
    private int certs;
    private int extraCerts;
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
        this.deadline = deadline;
        this.maximize = problem.objective.sense == Objective.Sense.MAX;
        this.maxCertify = multi ? Math.max(cfg.maxCertify, MULTI_MAX_CERTIFY) : cfg.maxCertify;
        this.nearBudgetNanos = multi ? Math.min(cfg.nearSearchBudgetNanos, MULTI_NEAR_SEARCH_NANOS)
                : cfg.nearSearchBudgetNanos;
    }

    NoTurnResult best() {
        return best;
    }

    void resetCertifyCount() {
        certs = 0;
    }

    void liftCertifyCap() {
        certs = 0;
        maxCertify = Integer.MAX_VALUE;
    }

    boolean variationsOver() {
        return best != null && (extraCerts >= cfg.extraCertify || System.nanoTime() > extraDeadline);
    }

    void shutdown() {
        exec.shutdownNow();
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
        queue.addAll(ranked);
        everything.addAll(ranked);
        queueDirty = true;
    }

    void reofferAll() {
        queue.clear();
        queue.addAll(everything);
        queueDirty = true;
        retryQueue.clear();
    }

    boolean certifyNext(boolean ja, boolean goodOnly) {
        if (stopped() || certs >= maxCertify) return false;
        if (queueDirty) {
            queue.sort(StructurePoolDriver.rankOrder(cfg));
            queueDirty = false;
        }
        if (takeBatch(queue, ja, goodOnly, false)) return true;
        if (retryDirty) {
            retryQueue.sort(StructurePoolDriver.rankOrder(cfg));
            retryDirty = false;
        }
        return takeBatch(retryQueue, ja, goodOnly, true);
    }

    private boolean takeBatch(List<Candidate> from, boolean ja, boolean goodOnly, boolean longPass) {
        int room = Math.min(cfg.perEdgeCertify, maxCertify - certs);
        List<Candidate> batch = new ArrayList<>(room);
        int tier = -1;
        int i = 0;
        int firstTaken = -1;
        while (i < from.size() && batch.size() < room) {
            Candidate c = from.get(i);
            int t = StructurePoolDriver.certifyTier(cfg, c);
            if (goodOnly && t >= StructurePoolDriver.SCREEN_MISS_TIER) break;
            if (tier < 0) tier = t;
            else if (t != tier) break;
            if (firstTaken < 0) firstTaken = i;
            String key = scheduleKey(c.combos, c.engage, ja);
            if (!found.contains(key) && !tried.contains(key)) batch.add(c);
            i++;
        }
        if (firstTaken >= 0) from.subList(firstTaken, i).clear();
        if (batch.isEmpty()) return i > 0;
        boolean exact = tier == StructurePoolDriver.SCREEN_EXACT_TIER;
        boolean shortPass = exact && !longPass && SHORT_PASS_NANOS < cfg.searchBudgetNanos;
        long budget = exact ? (shortPass ? SHORT_PASS_NANOS : cfg.searchBudgetNanos) : nearBudgetNanos;
        String label = (tier >= StructurePoolDriver.SCREEN_MISS_TIER ? "screen-miss"
                : exact ? (shortPass ? "screen-exact short" : longPass ? "screen-exact retry" : "screen-exact")
                : "screen-near") + (ja ? "+ja" : "");
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
            NoTurnResult rr = certifyOne(batch.get(idx), ja, budget, tc);
            int doneNow = certsBefore + done.incrementAndGet();
            progress.update("certify " + label + " " + doneNow + " certified (parallel " + threads + ")", fraction());
            if (rr != null) progress.found(rr);
            return rr;
        });
        certs += batch.size();
        for (int bi = 0; bi < batch.size(); bi++) {
            Candidate c = batch.get(bi);
            if (shortPass && res.get(bi) == null) {
                retryQueue.add(c);
                retryDirty = true;
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

    private NoTurnResult certifyOne(Candidate c, boolean ja, long budget, AtomicBoolean cancelTok) {
        JumpSpec spec = problem.buildSpec(c.combos, c.sprint, cfg.turnCombo, ja);
        long t0 = System.nanoTime();
        NoTurnCertifier.Result cr = cert.certifySearch(spec, budget, cancelTok);
        if (GraphRunner.TRACE) {
            System.out.println(String.format(Locale.ROOT, "[pool] certify ms=%.1f feasible=%s ja=%s engage=%d keys=%s",
                    (System.nanoTime() - t0) / 1e6, cr != null && cr.feasible, ja, c.engage,
                    NoTurnKeys.describe(c.combos)));
        }
        if (cr == null || !cr.feasible) return null;
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

    private static String scheduleKey(int[] combos, int engage, boolean ja) {
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
                progress.update("explore neighbours: " + lines + " lines from " + tried + " warm certifies (parallel "
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
