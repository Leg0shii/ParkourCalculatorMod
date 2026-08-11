package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ColdSearch {

    public static final class Config {
        public double facingStepDeg = 2.0;
        public int maxChanges = 14;
        public long nodeCapPerFacing = 4_000_000L;
        public long arcNodeCap = 300_000_000L;
        public int arcExhaustiveMaxLevel = 4;
        public boolean seededPass = true;
        public int seededPreCapMax = 4;
        public int seededSuffixCap = 2;
        public int beamWidth = 8_000;
        public int distinctLineCap = 200_000;
        public int probesPerLine = 4;
        public int certifyCap = 400;
        public double rectSlack = 0.05;
        public long timeBudgetMs = 0L;
        public int[][] segAlphabet;
        public int[] segMaxChanges;
        public int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    private static final double MET_TOL = 1.0e-4;
    private static final boolean ENGINE_FALLBACK = false;
    private static final int SEEDED_MIN_LASTPRESS = 16;
    private static final Config DEFAULT_CFG = new Config();

    private ColdSearch() {
    }

    public static ColdResult solve(SaveFile file) {
        return solve(file, new Config(), null);
    }

    public static ColdResult certifyLine(SaveFile file, String sig, Config cfg) {
        long t0 = System.nanoTime();
        ColdProblem p = ColdProblem.fromSave(file);
        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            moveKey[k] = sig.charAt(idx) - '0';
            hold[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        Candidate c = new Candidate(moveKey, hold, sig, 0.0, 0.0, 0.0, 0.0, 0.0,
                p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        AtomicBoolean cancel = new AtomicBoolean(false);
        int steps = (int) Math.round(360.0 / PROBE_SCAN_STEP);
        Sweep[] scan = new Sweep[steps];
        for (int si = 0; si < steps; si++) {
            scan[si] = new Sweep(p, cfg, -180.0 + si * PROBE_SCAN_STEP, 0, null);
        }
        probeSig(p, Collections.singletonList(c), scan, cfg, cancel);
        return certify(p, c, true, scan, cancel, t0, 0, 0, 0, 1, 1, false);
    }

    static double probeViolOf(ColdProblem p, Sweep[] scan, String sig, Config cfg, AtomicBoolean cancel) {
        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            moveKey[k] = sig.charAt(idx) - '0';
            hold[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        Candidate c = new Candidate(moveKey, hold, sig, 0.0, 0.0, 0.0, 0.0, 0.0,
                p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        probeSig(p, Collections.singletonList(c), scan, cfg, cancel);
        return c.probeViol;
    }

    static ColdResult certifySig(ColdProblem p, Sweep[] scan, String sig, Config cfg, AtomicBoolean cancel) {
        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            moveKey[k] = sig.charAt(idx) - '0';
            hold[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        Candidate c = new Candidate(moveKey, hold, sig, 0.0, 0.0, 0.0, 0.0, 0.0,
                p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        probeSig(p, Collections.singletonList(c), scan, cfg, cancel);
        return certify(p, c, true, scan, cancel, System.nanoTime(), 0, 0, 0, 1, 1, false);
    }

    static long[] benchSig(ColdProblem p, Sweep[] scan, String sig, Config cfg, boolean doCertify,
                           boolean jointFallback, AtomicBoolean cancel) {
        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            moveKey[k] = sig.charAt(idx) - '0';
            hold[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        Candidate c = new Candidate(moveKey, hold, sig, 0.0, 0.0, 0.0, 0.0, 0.0,
                p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        long t0 = System.nanoTime();
        probeSig(p, Collections.singletonList(c), scan, cfg, cancel);
        long t1 = System.nanoTime();
        int solved = 0;
        if (doCertify) {
            ColdResult r = certify(p, c, jointFallback, scan, cancel, System.nanoTime(), 0, 0, 0, 1, 1, false);
            solved = r != null && r.solved() ? 1 : 0;
        }
        long t2 = System.nanoTime();
        return new long[] {t1 - t0, doCertify ? t2 - t1 : 0L, solved};
    }

    private static final class Totals {
        long nodes;
        int cands;
        int probed;
        int certified;
        boolean truncated;
    }

    public static ColdResult solve(SaveFile file, Config cfg, PrintStream log) {
        long t0 = System.nanoTime();
        ColdProblem p = ColdProblem.fromSave(file);
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = cfg.timeBudgetMs > 0 ? t0 + cfg.timeBudgetMs * 1_000_000L : Long.MAX_VALUE;
        Totals tot = new Totals();

        int scanSteps = (int) Math.round(360.0 / PROBE_SCAN_STEP);
        Sweep[] scan = new Sweep[scanSteps];
        for (int si = 0; si < scanSteps; si++) {
            scan[si] = new Sweep(p, cfg, -180.0 + si * PROBE_SCAN_STEP, 0, null);
        }

        boolean runSeeded = cfg.seededPass && !p.singleHeld && p.lastPressSeg >= SEEDED_MIN_LASTPRESS;
        if (log != null && cfg.seededPass && !runSeeded) {
            log.printf(Locale.ROOT, "COLD .. seeded pass skipped (singleHeld=%b lastPressSeg=%d)%n",
                    p.singleHeld, p.lastPressSeg);
        }
        if (runSeeded) {
            List<StratPrefixes.Seed> seeds = StratPrefixes.generate(p);
            long perSeedCap = Math.max(50_000L, cfg.arcNodeCap / Math.max(1, seeds.size() * 4L));
            for (int preCap = 0; preCap <= cfg.seededPreCapMax; preCap++) {
                long passStart = System.nanoTime();
                SigCollector collector = new SigCollector(cfg);
                long passNodes = 0;
                for (StratPrefixes.Seed seed : seeds) {
                    ArcSweep sweep = new ArcSweep(p, cfg, 0, collector);
                    sweep.runSeeded(seed, preCap, cfg.seededSuffixCap, perSeedCap);
                    passNodes += sweep.nodes;
                    tot.truncated |= sweep.truncated;
                    if (System.nanoTime() > deadline) break;
                }
                tot.nodes += passNodes;
                tot.cands += collector.emitted;
                tot.truncated |= collector.truncated;
                if (log != null) {
                    log.printf(Locale.ROOT, "COLD .. seeded preCap=%d emitted=%d distinct=%d nodes=%d ms=%d%n",
                            preCap, collector.emitted, collector.perSig.size(), passNodes,
                            (System.nanoTime() - passStart) / 1_000_000L);
                }
                ColdResult r = probeCertify(p, cfg, collector, scan, "seeded" + preCap, preCap,
                        log, cancel, t0, deadline, tot, 800, Math.min(60, cfg.certifyCap), true);
                if (r != null) return r;
                if (System.nanoTime() > deadline) {
                    tot.truncated = true;
                    break;
                }
            }
        }

        for (int level = 0; level <= cfg.maxChanges && System.nanoTime() < deadline; level++) {
            long levelStart = System.nanoTime();
            SigCollector collector = new SigCollector(cfg);
            ArcSweep arcSweep = new ArcSweep(p, cfg, level, collector);
            arcSweep.run();
            tot.truncated |= arcSweep.truncated || collector.truncated;
            tot.nodes += arcSweep.nodes;
            tot.cands += collector.emitted;
            if (log != null) {
                log.printf(Locale.ROOT, "COLD .. level=%d arc sweep emitted=%d distinct=%d nodes=%d ms=%d%n",
                        level, collector.emitted, collector.perSig.size(), arcSweep.nodes,
                        (System.nanoTime() - levelStart) / 1_000_000L);
            }
            ColdResult r = probeCertify(p, cfg, collector, scan, "level" + level, level,
                    log, cancel, t0, deadline, tot, 4000, cfg.certifyCap, false);
            if (r != null) return r;
        }
        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
        return new ColdResult(null, Double.NaN, null, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY,
                null, elapsed, cfg.maxChanges, tot.nodes, tot.cands, tot.probed, tot.certified, tot.truncated);
    }

    public static ColdResult solveConstrained(SaveFile file, Config cfg, PrintStream log, AtomicBoolean cancel) {
        long t0 = System.nanoTime();
        ColdProblem p = ColdProblem.fromSave(file);
        if (cancel == null) cancel = new AtomicBoolean(false);
        long deadline = cfg.timeBudgetMs > 0 ? t0 + cfg.timeBudgetMs * 1_000_000L : Long.MAX_VALUE;
        Totals tot = new Totals();

        int maxLevel = 0;
        if (cfg.segMaxChanges != null) {
            for (int m : cfg.segMaxChanges) maxLevel += Math.max(0, m);
        }
        maxLevel = Math.min(maxLevel, cfg.maxChanges);

        for (int level = 0; level <= maxLevel && System.nanoTime() < deadline; level++) {
            long levelStart = System.nanoTime();
            long[] stats = new long[3];
            List<SigCollector> collectors = runLevel(p, cfg, level, deadline, cancel, stats);
            tot.nodes += stats[0];
            tot.cands += stats[1];
            tot.truncated |= stats[2] != 0;
            long arcMs = (System.nanoTime() - levelStart) / 1_000_000L;

            Candidate[] ordered = sigsByMargin(collectors);
            tot.certified += ordered.length;
            long certStart = System.nanoTime();
            String solvedSig = parallelCertifyFirst(p, cfg, ordered, cancel, deadline);
            if (log != null) {
                log.printf(Locale.ROOT,
                        "COLD constrained level=%d emitted=%d distinct=%d nodes=%d arcMs=%d certMs=%d probeSumMs=%d certSumMs=%d certN=%d solved=%b%n",
                        level, stats[1], ordered.length, stats[0], arcMs,
                        (System.nanoTime() - certStart) / 1_000_000L,
                        lastProbeNs / 1_000_000L, lastCertNs / 1_000_000L, lastCertCount, solvedSig != null);
            }
            if (solvedSig != null) return certifyLine(file, solvedSig, cfg);
        }
        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
        return new ColdResult(null, Double.NaN, null, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY,
                null, elapsed, maxLevel, tot.nodes, tot.cands, tot.probed, tot.certified, tot.truncated);
    }

    private static List<SigCollector> runLevel(final ColdProblem p, final Config cfg, final int level,
                                               final long deadline, final AtomicBoolean cancel, long[] stats) {
        boolean rootParallel = level <= cfg.arcExhaustiveMaxLevel && cfg.segAlphabet != null
                && cfg.segAlphabet.length > 0 && cfg.segAlphabet[0] != null
                && cfg.segAlphabet[0].length > 1 && cfg.threads > 1;
        if (!rootParallel) {
            SigCollector collector = new SigCollector(cfg);
            ArcSweep sweep = new ArcSweep(p, cfg, level, collector);
            sweep.setSegments(cfg.segAlphabet, cfg.segMaxChanges);
            sweep.setBudget(deadline, cancel);
            sweep.run();
            stats[0] = sweep.nodes;
            stats[1] = collector.emitted;
            stats[2] = sweep.truncated || collector.truncated ? 1 : 0;
            return Collections.singletonList(collector);
        }
        final int[] roots = cfg.segAlphabet[0];
        final SigCollector[] cols = new SigCollector[roots.length];
        final long[] nodes = new long[roots.length];
        final long[] emitted = new long[roots.length];
        final boolean[] trunc = new boolean[roots.length];
        Thread[] threads = new Thread[roots.length];
        for (int r = 0; r < roots.length; r++) {
            final int ri = r;
            threads[r] = new Thread(new Runnable() {
                @Override
                public void run() {
                    SigCollector collector = new SigCollector(cfg);
                    ArcSweep sweep = new ArcSweep(p, cfg, level, collector);
                    sweep.setSegments(cfg.segAlphabet, cfg.segMaxChanges);
                    sweep.setBudget(deadline, cancel);
                    sweep.setRootCombo(roots[ri]);
                    sweep.run();
                    cols[ri] = collector;
                    nodes[ri] = sweep.nodes;
                    emitted[ri] = collector.emitted;
                    trunc[ri] = sweep.truncated || collector.truncated;
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ie) {
                cancel.set(true);
                Thread.currentThread().interrupt();
            }
        }
        List<SigCollector> out = new ArrayList<SigCollector>();
        for (int r = 0; r < roots.length; r++) {
            stats[0] += nodes[r];
            stats[1] += emitted[r];
            if (trunc[r]) stats[2] = 1;
            if (cols[r] != null) out.add(cols[r]);
        }
        return out;
    }

    private static Candidate[] sigsByMargin(List<SigCollector> collectors) {
        final Map<String, Candidate> best = new HashMap<String, Candidate>();
        for (SigCollector collector : collectors) {
            for (Map.Entry<String, List<Candidate>> e : collector.perSig.entrySet()) {
                Candidate bc = null;
                for (Candidate c : e.getValue()) {
                    if (bc == null || c.margin > bc.margin) bc = c;
                }
                if (bc == null) continue;
                Candidate prev = best.get(e.getKey());
                if (prev == null || bc.margin > prev.margin) best.put(e.getKey(), bc);
            }
        }
        List<Candidate> cands = new ArrayList<Candidate>(best.values());
        Collections.sort(cands, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                int c = Double.compare(b.margin, a.margin);
                return c != 0 ? c : a.sig.compareTo(b.sig);
            }
        });
        return cands.toArray(new Candidate[0]);
    }

    static int JOINT_FALLBACK_TOP = 64;
    static long lastProbeNs;
    static long lastCertNs;
    static long lastCertCount;

    private static String parallelCertifyFirst(final ColdProblem p, final Config cfg, final Candidate[] candArr,
                                               final AtomicBoolean cancel, final long deadline) {
        if (candArr.length == 0) return null;
        final AtomicInteger nextIdx = new AtomicInteger(0);
        final AtomicReference<String> solved = new AtomicReference<String>(null);
        final AtomicBoolean abort = new AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicLong probeNs = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicLong certNs = new java.util.concurrent.atomic.AtomicLong(0);
        final AtomicInteger done = new AtomicInteger(0);
        final int nThreads = Math.max(1, cfg.threads);
        final int steps = (int) Math.round(360.0 / PROBE_SCAN_STEP);
        Thread[] workers = new Thread[nThreads];
        for (int t = 0; t < nThreads; t++) {
            workers[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Sweep[] scan = new Sweep[steps];
                        for (int i = 0; i < steps; i++) {
                            scan[i] = new Sweep(p, cfg, -180.0 + i * PROBE_SCAN_STEP, 0, null);
                        }
                        while (!abort.get()) {
                            if (cancel.get() || System.nanoTime() > deadline) {
                                abort.set(true);
                                break;
                            }
                            int i = nextIdx.getAndIncrement();
                            if (i >= candArr.length) break;
                            boolean joint = i < JOINT_FALLBACK_TOP;
                            long[] full = benchSig(p, scan, candArr[i].sig, cfg, true, joint, abort);
                            probeNs.addAndGet(full[0]);
                            certNs.addAndGet(full[1]);
                            done.incrementAndGet();
                            if (full[2] == 1 && solved.compareAndSet(null, candArr[i].sig)) {
                                abort.set(true);
                                return;
                            }
                        }
                    } catch (Throwable ex) {
                        // stop this worker; the others keep going
                    }
                }
            });
        }
        for (Thread w : workers) w.start();
        for (Thread w : workers) {
            try {
                w.join();
            } catch (InterruptedException ie) {
                abort.set(true);
                Thread.currentThread().interrupt();
            }
        }
        lastProbeNs = probeNs.get();
        lastCertNs = certNs.get();
        lastCertCount = done.get();
        return solved.get();
    }

    private static ColdResult probeCertify(ColdProblem p, Config cfg, SigCollector collector, Sweep[] scan,
                                           String tag, int level, PrintStream log, AtomicBoolean cancel,
                                           long t0, long deadline, Totals tot,
                                           int quickKeep, int certifyCap, boolean skipIfAllInf) {
        Map<String, List<Candidate>> perSig = collector.perSig;
        if (perSig.isEmpty()) return null;
        long probeStart = System.nanoTime();
        List<Map.Entry<String, List<Candidate>>> entryList =
                new ArrayList<Map.Entry<String, List<Candidate>>>(perSig.entrySet());
        if (entryList.size() > quickKeep) {
            final Map<String, Double> quickScore = new HashMap<String, Double>();
            for (Map.Entry<String, List<Candidate>> e : entryList) {
                double[] qs = quickScoreSig(p, e.getValue(), scan, cfg, cancel);
                quickScore.put(e.getKey(), qs[0]);
                Candidate c0 = e.getValue().get(0);
                c0.probeViol = qs[0];
                c0.probeTheta = qs[1];
                if (System.nanoTime() > deadline) break;
            }
            Collections.sort(entryList, new Comparator<Map.Entry<String, List<Candidate>>>() {
                @Override
                public int compare(Map.Entry<String, List<Candidate>> a, Map.Entry<String, List<Candidate>> b) {
                    Double qa = quickScore.get(a.getKey());
                    Double qb = quickScore.get(b.getKey());
                    double va = qa == null ? Double.POSITIVE_INFINITY : qa;
                    double vb = qb == null ? Double.POSITIVE_INFINITY : qb;
                    int cmp = Double.compare(va, vb);
                    if (cmp != 0) return cmp;
                    return a.getKey().compareTo(b.getKey());
                }
            });
            entryList = new ArrayList<Map.Entry<String, List<Candidate>>>(entryList.subList(0, quickKeep));
            if (log != null) {
                log.printf(Locale.ROOT, "COLD .. %s quickscreen %d -> %d ms=%d%n",
                        tag, perSig.size(), entryList.size(),
                        (System.nanoTime() - probeStart) / 1_000_000L);
            }
        }
        Map<String, Candidate> bySig = new HashMap<String, Candidate>();
        int probeCount = 0;
        double probeBest = Double.POSITIVE_INFINITY;
        String probeBestSig = "-";
        for (Map.Entry<String, List<Candidate>> e : entryList) {
            Candidate best = probeSig(p, e.getValue(), scan, cfg, cancel);
            tot.probed++;
            probeCount++;
            if (best != null) {
                bySig.put(e.getKey(), best);
                if (best.probeViol < probeBest) {
                    probeBest = best.probeViol;
                    probeBestSig = best.sig;
                }
            }
            if (log != null && probeCount % 25000 == 0) {
                log.printf(Locale.ROOT, "COLD .. %s probed %d/%d best=%.4e sig=%s%n",
                        tag, probeCount, entryList.size(), probeBest, probeBestSig);
            }
            if (System.nanoTime() > deadline) {
                tot.truncated = true;
                break;
            }
        }
        if (log != null) {
            log.printf(Locale.ROOT, "COLD %s distinct=%d probeMs=%d%n",
                    tag, bySig.size(), (System.nanoTime() - probeStart) / 1_000_000L);
        }
        if (bySig.isEmpty()) return null;

        List<Candidate> ranked = new ArrayList<Candidate>(bySig.values());
        Collections.sort(ranked, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.trueOpen != b.trueOpen) return a.trueOpen ? -1 : 1;
                int cmp = Double.compare(a.probeViol, b.probeViol);
                if (cmp != 0) return cmp;
                return a.sig.compareTo(b.sig);
            }
        });

        if (skipIfAllInf && !Double.isFinite(ranked.get(0).probeViol)) {
            if (log != null) log.printf(Locale.ROOT, "COLD %s all probes infinite, certify skipped%n", tag);
            return null;
        }
        int certifyLimit = Math.min(ranked.size(), certifyCap);
        if (log != null) {
            log.printf(Locale.ROOT, "COLD %s bestProbe=%.4e certifying=%d%n",
                    tag, ranked.get(0).probeViol, certifyLimit);
        }
        long certStart = System.nanoTime();
        for (int i = 0; i < certifyLimit; i++) {
            Candidate c = ranked.get(i);
            if (i >= 30 && Double.isFinite(c.probeViol) && c.probeViol > 4.0 * cfg.rectSlack) continue;
            tot.certified++;
            ColdResult r = certify(p, c, i < 30, scan, cancel, t0, level,
                    tot.nodes, tot.cands, tot.probed, tot.certified, tot.truncated);
            if (r != null) {
                if (log != null) log.printf(Locale.ROOT, "COLD solved: %s%n", c.sig);
                return r;
            }
            if (log != null && (i < 3 || i % 50 == 49)) {
                log.printf(Locale.ROOT, "COLD certify %d/%d miss sig=%s theta=%.1f probe=%.4e direct=[%s]%n",
                        i + 1, certifyLimit, c.sig, c.theta, c.probeViol, lastDirectDebug);
            }
            if (System.nanoTime() > deadline) {
                tot.truncated = true;
                break;
            }
        }
        if (log != null) {
            log.printf(Locale.ROOT, "COLD %s certifyMs=%d%n",
                    tag, (System.nanoTime() - certStart) / 1_000_000L);
        }
        return null;
    }

    static final class SigCollector {
        final Config cfg;
        final Map<String, List<Candidate>> perSig = new HashMap<String, List<Candidate>>();
        final java.util.PriorityQueue<Candidate> evictHeap = new java.util.PriorityQueue<Candidate>(
                new Comparator<Candidate>() {
                    @Override
                    public int compare(Candidate a, Candidate b) {
                        return Double.compare(a.margin, b.margin);
                    }
                });
        int emitted;
        boolean truncated;

        SigCollector(Config cfg) {
            this.cfg = cfg;
        }

        private double sigBestMargin(String sig) {
            List<Candidate> list = perSig.get(sig);
            double best = Double.NEGATIVE_INFINITY;
            for (Candidate c : list) best = Math.max(best, c.margin);
            return best;
        }

        void accept(Candidate c) {
            emitted++;
            List<Candidate> list = perSig.get(c.sig);
            if (list == null) {
                if (perSig.size() >= cfg.distinctLineCap && !evictWorseThan(c)) {
                    truncated = true;
                    return;
                }
                list = new ArrayList<Candidate>(cfg.probesPerLine);
                perSig.put(c.sig, list);
                list.add(c);
                evictHeap.add(c);
                return;
            }
            if (list.size() < cfg.probesPerLine) {
                list.add(c);
                return;
            }
            int worst = 0;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).margin < list.get(worst).margin) worst = i;
            }
            if (c.margin > list.get(worst).margin) list.set(worst, c);
        }

        private boolean evictWorseThan(Candidate challenger) {
            while (true) {
                Candidate top = evictHeap.peek();
                if (top == null) return false;
                if (!perSig.containsKey(top.sig)) {
                    evictHeap.poll();
                    continue;
                }
                double cur = sigBestMargin(top.sig);
                if (cur > top.margin + 1.0e-12) {
                    evictHeap.poll();
                    evictHeap.add(bestOf(top.sig));
                    continue;
                }
                if (challenger.margin <= cur) return false;
                evictHeap.poll();
                perSig.remove(top.sig);
                return true;
            }
        }

        private Candidate bestOf(String sig) {
            List<Candidate> list = perSig.get(sig);
            Candidate best = list.get(0);
            for (Candidate c : list) {
                if (c.margin > best.margin) best = c;
            }
            return best;
        }
    }

    static final class Candidate {
        final int[] moveKey;
        final boolean[] hold;
        final String sig;
        final double theta;
        final double vx;
        final double vz;
        final double dx;
        final double dz;
        final double rxLo;
        final double rxHi;
        final double rzLo;
        final double rzHi;
        boolean trueOpen;
        double margin = Double.NaN;
        int tailCombo = KeyLine.WA;
        double probeViol = Double.NaN;
        double probeTheta = Double.NaN;
        double[] arcsDeg;

        Candidate(int[] moveKey, boolean[] hold, String sig, double theta, double vx, double vz,
                  double dx, double dz, double rxLo, double rxHi, double rzLo, double rzHi) {
            this.moveKey = moveKey;
            this.hold = hold;
            this.sig = sig;
            this.theta = theta;
            this.vx = vx;
            this.vz = vz;
            this.dx = dx;
            this.dz = dz;
            this.rxLo = rxLo;
            this.rxHi = rxHi;
            this.rzLo = rzLo;
            this.rzHi = rzHi;
        }
    }

    static final class Sweep {
        final ColdProblem p;
        final Config cfg;
        final double theta;
        final int level;
        final SigCollector out;

        final int last;
        final double thr;
        final boolean perAxis;
        final float sinMove;
        final float cosMove;
        final float sinJump;
        final float cosJump;
        final double[] accelGroundSprint;
        final double[] accelGroundWalk;
        final double[] maxAx;
        final double[] maxAz;
        final int[] nextWallX;
        final int[] nextWallZ;
        final double[] wallXLo;
        final double[] wallXHi;
        final int[] wallXSeg;
        final double[] wallZLo;
        final double[] wallZHi;
        final int[] wallZSeg;
        final int[] tailWallSeg;
        final boolean[] tailWallX;
        final double[] tailWallLo;
        final double[] tailWallHi;

        final int[] moveKey;
        final boolean[] hold;
        long nodes;
        boolean truncated;

        Sweep(ColdProblem p, Config cfg, double theta, int level, SigCollector out) {
            this.p = p;
            this.cfg = cfg;
            this.theta = theta;
            this.level = level;
            this.out = out;
            this.last = p.lastPressSeg;
            this.thr = p.model.inertiaThreshold();
            this.perAxis = p.model.perAxisInertia();

            float yawF = (float) theta;
            float radMove = yawF * (float) Math.PI / 180.0F;
            float radJump = yawF * (float) (Math.PI / 180.0);
            this.sinMove = McSineTable.sinStep(radMove);
            this.cosMove = McSineTable.cosStep(radMove);
            this.sinJump = McSineTable.sinStep(radJump);
            this.cosJump = McSineTable.cosStep(radJump);

            this.accelGroundSprint = new double[p.numTicks];
            this.accelGroundWalk = new double[p.numTicks];
            for (int k = 0; k < p.numTicks; k++) {
                if (p.slip[k] < 1.0) {
                    float slipF = (float) p.slip[k];
                    float f4 = slipF * 0.91F;
                    float ground = 0.16277136F / (f4 * f4 * f4);
                    accelGroundSprint[k] = Constants.attrValueF(0, true) * ground;
                    accelGroundWalk[k] = Constants.attrValueF(0, false) * ground;
                } else {
                    accelGroundSprint[k] = Constants.AIR_SPEED_F;
                    accelGroundWalk[k] = Constants.AIR_SPEED_NO_SPRINT_F;
                }
            }

            this.maxAx = new double[p.numTicks];
            this.maxAz = new double[p.numTicks];
            for (int k = 0; k < p.numTicks; k++) {
                double mx = 0;
                double mz = 0;
                for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
                    double[] a = comboAccel(c, (float) accelGroundSprint[k]);
                    mx = Math.max(mx, Math.abs(a[0]));
                    mz = Math.max(mz, Math.abs(a[1]));
                }
                maxAx[k] = mx;
                maxAz[k] = mz;
            }

            List<ColdProblem.Wall> wx = new ArrayList<ColdProblem.Wall>();
            List<ColdProblem.Wall> wz = new ArrayList<ColdProblem.Wall>();
            for (ColdProblem.Wall w : p.momentumWalls) {
                if (w.axisX) wx.add(w);
                else wz.add(w);
            }
            Comparator<ColdProblem.Wall> bySeg = new Comparator<ColdProblem.Wall>() {
                @Override
                public int compare(ColdProblem.Wall a, ColdProblem.Wall b) {
                    return Integer.compare(a.segTick, b.segTick);
                }
            };
            Collections.sort(wx, bySeg);
            Collections.sort(wz, bySeg);
            wallXSeg = new int[wx.size()];
            wallXLo = new double[wx.size()];
            wallXHi = new double[wx.size()];
            for (int i = 0; i < wx.size(); i++) {
                wallXSeg[i] = wx.get(i).segTick;
                wallXLo[i] = wx.get(i).lo;
                wallXHi[i] = wx.get(i).hi;
            }
            wallZSeg = new int[wz.size()];
            wallZLo = new double[wz.size()];
            wallZHi = new double[wz.size()];
            for (int i = 0; i < wz.size(); i++) {
                wallZSeg[i] = wz.get(i).segTick;
                wallZLo[i] = wz.get(i).lo;
                wallZHi[i] = wz.get(i).hi;
            }
            nextWallX = new int[last + 2];
            nextWallZ = new int[last + 2];
            for (int s = 0; s <= last + 1; s++) {
                nextWallX[s] = firstAtOrAfter(wallXSeg, s);
                nextWallZ[s] = firstAtOrAfter(wallZSeg, s);
            }

            List<ColdProblem.Wall> tw = new ArrayList<ColdProblem.Wall>(p.tailWalls);
            Collections.sort(tw, bySeg);
            tailWallSeg = new int[tw.size()];
            tailWallX = new boolean[tw.size()];
            tailWallLo = new double[tw.size()];
            tailWallHi = new double[tw.size()];
            for (int i = 0; i < tw.size(); i++) {
                tailWallSeg[i] = tw.get(i).segTick;
                tailWallX[i] = tw.get(i).axisX;
                tailWallLo[i] = tw.get(i).lo;
                tailWallHi[i] = tw.get(i).hi;
            }

            this.moveKey = new int[last + 1];
            this.hold = new boolean[last + 1];
        }

        private static int firstAtOrAfter(int[] segs, int s) {
            for (int i = 0; i < segs.length; i++) {
                if (segs[i] >= s) return i;
            }
            return segs.length;
        }

        private double[] comboAccel(int combo, float accelSpeed) {
            float s = LineSpec.KEY_INPUT_SCALE * KeyLine.STRAFE_SIGN[combo];
            float f = LineSpec.KEY_INPUT_SCALE * KeyLine.FORWARD_SIGN[combo];
            float fm = s * s + f * f;
            if (fm < 1.0E-4F) return new double[] {0.0, 0.0};
            fm = (float) Math.sqrt((double) fm);
            if (fm < 1.0F) fm = 1.0F;
            fm = accelSpeed / fm;
            float sw = s * fm;
            float fw = f * fm;
            double ax = (double) (sw * cosMove - fw * sinMove);
            double az = (double) (fw * cosMove + sw * sinMove);
            return new double[] {ax, az};
        }

        static final class BeamState {
            final int tick;
            final double vx;
            final double vz;
            final boolean sprintPrev;
            final int changes;
            final double[] rects;
            final double dx;
            final double dz;
            final int[] prefixKey;
            final boolean[] prefixHold;
            final double rank;

            BeamState(int tick, double vx, double vz, boolean sprintPrev, int changes,
                      double[] rects, double dx, double dz, int[] prefixKey, boolean[] prefixHold) {
                this.tick = tick;
                this.vx = vx;
                this.vz = vz;
                this.sprintPrev = sprintPrev;
                this.changes = changes;
                this.rects = rects;
                this.dx = dx;
                this.dz = dz;
                this.prefixKey = prefixKey;
                this.prefixHold = prefixHold;
                boolean open = rects[4] <= rects[5] && rects[6] <= rects[7];
                double w = open ? Math.min(rects[5] - rects[4], rects[7] - rects[6])
                        : Math.min(rects[1] - rects[0], rects[3] - rects[2]);
                this.rank = (open ? 1000.0 : 0.0) + w + 3.0 * Math.hypot(vx, vz);
            }
        }

        private List<BeamState> stageSink;
        private int stageEnd;
        private long stageNodes;
        private long stageNodeCap;
        private boolean stageExhausted;

        private static final int MICRO_STAGE_TICKS = 5;

        private int[] stageBoundaries() {
            List<Integer> b = new ArrayList<Integer>();
            int prev = 0;
            for (int k = 1; k <= last; k++) {
                boolean landing = p.ground[k] && !p.ground[k - 1];
                boolean micro = k - prev >= MICRO_STAGE_TICKS;
                if ((landing || micro) && k < last) {
                    b.add(k);
                    prev = k;
                }
            }
            b.add(last);
            int[] out = new int[b.size()];
            for (int i = 0; i < out.length; i++) out[i] = b.get(i);
            return out;
        }

        void run() {
            int[] bounds = stageBoundaries();
            stageNodeCap = Math.max(1, cfg.nodeCapPerFacing / bounds.length);
            List<BeamState> beam = new ArrayList<BeamState>();
            beam.add(new BeamState(0, 0.0, 0.0, false, 0,
                    new double[] {p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi,
                            p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi},
                    0.0, 0.0, new int[0], new boolean[0]));
            for (int si = 0; si < bounds.length; si++) {
                boolean finalStage = si == bounds.length - 1;
                stageEnd = finalStage ? -1 : bounds[si];
                stageSink = finalStage ? null : new ArrayList<BeamState>();
                Collections.sort(beam, new Comparator<BeamState>() {
                    @Override
                    public int compare(BeamState a, BeamState b) {
                        return Double.compare(b.rank, a.rank);
                    }
                });
                stageNodes = 0;
                stageExhausted = false;
                for (BeamState b : beam) {
                    System.arraycopy(b.prefixKey, 0, moveKey, 0, b.prefixKey.length);
                    for (int i = 0; i < b.prefixHold.length; i++) hold[i] = b.prefixHold[i];
                    dfs(b.tick, b.vx, b.vz, b.sprintPrev, b.changes,
                            b.rects[0], b.rects[1], b.rects[2], b.rects[3],
                            b.rects[4], b.rects[5], b.rects[6], b.rects[7], b.dx, b.dz);
                    if (stageExhausted) break;
                }
                truncated |= stageExhausted;
                if (finalStage) break;
                beam = capBeam(stageSink);
                if (beam.isEmpty()) break;
            }
        }

        private List<BeamState> capBeam(List<BeamState> states) {
            Map<Long, BeamState> byKey = new HashMap<Long, BeamState>();
            for (BeamState s : states) {
                long key = (Math.round(s.vx * 200.0) & 0xFFFFFL)
                        | ((Math.round(s.vz * 200.0) & 0xFFFFFL) << 20)
                        | ((long) s.changes << 40)
                        | ((s.sprintPrev ? 1L : 0L) << 50);
                BeamState prev = byKey.get(key);
                if (prev == null || s.rank > prev.rank) byKey.put(key, s);
            }
            List<BeamState> out = new ArrayList<BeamState>(byKey.values());
            if (out.size() > cfg.beamWidth) {
                Collections.sort(out, new Comparator<BeamState>() {
                    @Override
                    public int compare(BeamState a, BeamState b) {
                        return Double.compare(b.rank, a.rank);
                    }
                });
                out = new ArrayList<BeamState>(out.subList(0, cfg.beamWidth));
            }
            return out;
        }

        private void dfs(int k, double vx, double vz, boolean sprintPrev, int changes,
                         double rxLo, double rxHi, double rzLo, double rzHi,
                         double uxLo, double uxHi, double uzLo, double uzHi, double dx, double dz) {
            if (stageExhausted) return;
            if (stageSink != null && k == stageEnd) {
                int[] pk = new int[k];
                boolean[] ph = new boolean[k];
                System.arraycopy(moveKey, 0, pk, 0, k);
                System.arraycopy(hold, 0, ph, 0, k);
                stageSink.add(new BeamState(k, vx, vz, sprintPrev, changes,
                        new double[] {rxLo, rxHi, rzLo, rzHi, uxLo, uxHi, uzLo, uzHi}, dx, dz, pk, ph));
                return;
            }
            nodes++;
            if (++stageNodes > stageNodeCap) {
                stageExhausted = true;
                return;
            }
            int prevCombo = k > 0 ? moveKey[k - 1] : -1;
            boolean prevHold = k > 0 && hold[k - 1];
            for (int ci = 0; ci < KeyLine.COMBO_COUNT; ci++) {
                int combo = comboOrder(prevCombo, ci);
                boolean canRun = KeyLine.canRun(combo);
                int holdOptions = !canRun ? 1 : (sprintPrev ? 1 : 2);
                for (int hi = 0; hi < holdOptions; hi++) {
                    boolean h = !canRun ? false : (sprintPrev ? true : (hi == 0));
                    int change = (k > 0 && (combo != prevCombo || h != prevHold)) ? 1 : 0;
                    int used = changes + change;
                    if (used > level) continue;
                    if (used + (last - k) < level) continue;
                    boolean sprintCur = canRun && (sprintPrev || h);
                    moveKey[k] = combo;
                    hold[k] = h;
                    if (k == last) {
                        if (used != level) continue;
                        emit(vx, vz, dx, dz, rxLo, rxHi, rzLo, rzHi, uxLo, uxHi, uzLo, uzHi);
                        continue;
                    }
                    step(k, vx, vz, sprintPrev, sprintCur, used, rxLo, rxHi, rzLo, rzHi,
                            uxLo, uxHi, uzLo, uzHi, dx, dz);
                    if (stageExhausted) return;
                }
            }
        }

        private void step(int k, double vx, double vz, boolean sprintPrev, boolean sprintCur, int used,
                          double rxLo, double rxHi, double rzLo, double rzHi,
                          double uxLo, double uxHi, double uzLo, double uzHi, double dx, double dz) {
            double nvx = vx;
            double nvz = vz;
            if (perAxis) {
                if (Math.abs(nvx) < thr) nvx = 0.0;
                if (Math.abs(nvz) < thr) nvz = 0.0;
            } else {
                if (nvx * nvx + nvz * nvz < 9.0E-6) {
                    nvx = 0.0;
                    nvz = 0.0;
                }
            }
            boolean contact = p.slip[k] < 1.0;
            boolean isPress = contact && isPressSeg(k);
            if (isPress && sprintCur) {
                nvx -= (double) (sinJump * 0.2F);
                nvz += (double) (cosJump * 0.2F);
            }
            float accelSpeed;
            if (contact) {
                accelSpeed = (float) (sprintCur ? accelGroundSprint[k] : accelGroundWalk[k]);
            } else {
                boolean airSprint = k == 0 ? false : sprintPrev;
                accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }
            double[] a = comboAccel(moveKey[k], accelSpeed);
            nvx += a[0];
            nvz += a[1];
            double ndx = dx + nvx;
            double ndz = dz + nvz;
            float f4 = contact ? ((float) p.slip[k]) * 0.91F : 0.91F;
            double cvx = nvx * (double) f4;
            double cvz = nvz * (double) f4;

            int at = k + 1;
            double nrxLo = rxLo;
            double nrxHi = rxHi;
            double nrzLo = rzLo;
            double nrzHi = rzHi;
            double nuxLo = uxLo;
            double nuxHi = uxHi;
            double nuzLo = uzLo;
            double nuzHi = uzHi;
            for (int i = nextWallX[at]; i < wallXSeg.length && wallXSeg[i] == at; i++) {
                nrxLo = Math.max(nrxLo, wallXLo[i] - cfg.rectSlack - ndx);
                nrxHi = Math.min(nrxHi, wallXHi[i] + cfg.rectSlack - ndx);
                nuxLo = Math.max(nuxLo, wallXLo[i] - ndx);
                nuxHi = Math.min(nuxHi, wallXHi[i] - ndx);
            }
            for (int i = nextWallZ[at]; i < wallZSeg.length && wallZSeg[i] == at; i++) {
                nrzLo = Math.max(nrzLo, wallZLo[i] - cfg.rectSlack - ndz);
                nrzHi = Math.min(nrzHi, wallZHi[i] + cfg.rectSlack - ndz);
                nuzLo = Math.max(nuzLo, wallZLo[i] - ndz);
                nuzHi = Math.min(nuzHi, wallZHi[i] - ndz);
            }
            if (nrxLo > nrxHi || nrzLo > nrzHi) return;

            if (!reachable(at, cvx, ndx, nrxLo, nrxHi, true)) return;
            if (!reachable(at, cvz, ndz, nrzLo, nrzHi, false)) return;

            boolean nextSprintPrev = sprintCur;
            dfs(at, cvx, cvz, nextSprintPrev, used, nrxLo, nrxHi, nrzLo, nrzHi,
                    nuxLo, nuxHi, nuzLo, nuzHi, ndx, ndz);
        }

        private boolean reachable(int fromSeg, double v, double d, double rLo, double rHi, boolean axisX) {
            int[] segs = axisX ? wallXSeg : wallZSeg;
            int wi = axisX ? nextWallX[Math.min(fromSeg, last + 1)] : nextWallZ[Math.min(fromSeg, last + 1)];
            while (wi < segs.length && segs[wi] <= fromSeg) wi++;
            if (wi >= segs.length) return true;
            int target = segs[wi];
            double lo = axisX ? wallXLo[wi] : wallZLo[wi];
            double hi = axisX ? wallXHi[wi] : wallZHi[wi];
            double vlo = v;
            double vhi = v;
            double dlo = 0;
            double dhi = 0;
            for (int s = fromSeg; s < target; s++) {
                if (vlo < thr && vhi > -thr) {
                    if (vlo > 0) vlo = 0;
                    if (vhi < 0) vhi = 0;
                }
                double am = (axisX ? maxAx[s] : maxAz[s]) + (isPressSeg(s) ? 0.2 : 0.0);
                vhi += am;
                vlo -= am;
                dhi += vhi;
                dlo += vlo;
                double f = p.slip[s] < 1.0 ? ((float) p.slip[s]) * 0.91F : 0.91F;
                vhi *= f;
                vlo *= f;
            }
            double posLo = rLo + d + dlo;
            double posHi = rHi + d + dhi;
            return posLo <= hi + cfg.rectSlack && posHi >= lo - cfg.rectSlack;
        }

        private boolean isPressSeg(int k) {
            for (int pSeg : p.pressSegTicks) {
                if (pSeg == k) return true;
            }
            return false;
        }

        private void emit(double vx, double vz, double dx, double dz,
                          double rxLo, double rxHi, double rzLo, double rzHi,
                          double uxLo, double uxHi, double uzLo, double uzHi) {
            boolean trueOpen = uxLo <= uxHi && uzLo <= uzHi;
            double cxLo = trueOpen ? uxLo : rxLo;
            double cxHi = trueOpen ? uxHi : rxHi;
            double czLo = trueOpen ? uzLo : rzLo;
            double czHi = trueOpen ? uzHi : rzHi;
            double margin = tailMargin(vx, vz, dx, dz, cxLo, cxHi, czLo, czHi);
            if (margin < -cfg.rectSlack) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= last; i++) {
                sb.append(moveKey[i]).append(hold[i] ? '+' : '.');
            }
            Candidate c = new Candidate(moveKey.clone(), hold.clone(), sb.toString(), theta,
                    vx, vz, dx, dz, cxLo, cxHi, czLo, czHi);
            c.trueOpen = trueOpen;
            c.margin = margin + (trueOpen ? 1000.0 : 0.0);
            out.accept(c);
        }

        private double tailMargin(double vx, double vz, double dx, double dz,
                                  double rxLo, double rxHi, double rzLo, double rzHi) {
            double posXLo = rxLo + dx;
            double posXHi = rxHi + dx;
            double posZLo = rzLo + dz;
            double posZHi = rzHi + dz;
            double margin = Double.POSITIVE_INFINITY;
            double s = Math.hypot(vx, vz) + 0.2;
            double maxDisp = 0;
            int wi = 0;
            for (int k = last; k < p.numTicks && wi < tailWallSeg.length; k++) {
                s += p.slip[k] < 1.0 ? accelGroundSprint[k] : Constants.AIR_SPEED_F;
                maxDisp += s;
                s *= p.slip[k] < 1.0 ? ((float) p.slip[k]) * 0.91F : 0.91;
                int at = k + 1;
                while (wi < tailWallSeg.length && tailWallSeg[wi] == at) {
                    double g = tailWallX[wi]
                            ? gap(posXLo, posXHi, tailWallLo[wi], tailWallHi[wi])
                            : gap(posZLo, posZHi, tailWallLo[wi], tailWallHi[wi]);
                    margin = Math.min(margin, maxDisp - g);
                    if (maxDisp + cfg.rectSlack < g) return Double.NEGATIVE_INFINITY;
                    wi++;
                }
            }
            return margin;
        }

        double lineTailMargin(int[] mk, boolean[] hd) {
            if (tailWallSeg.length == 0) return Double.POSITIVE_INFINITY;
            double[] tr = traceLine(mk, hd);
            return tailMargin(tr[4], tr[5], tr[6], tr[7], tr[0], tr[1], tr[2], tr[3]);
        }

        /** Sound forward interval reachability over the tail from the momentum exit (position ranges over the
         *  feasible start rect, velocity a point at this facing). Intersects the reachable X/Z intervals with each
         *  tail constraint and propagates the tightened interval forward; returns false only when a constraint is
         *  provably unreachable. Over-approximates (X/Z accel treated independently), so a feasible line is never
         *  pruned. Much tighter than {@link #tailMargin} because satisfying an early constraint constrains what is
         *  reachable at the later ones. */
        boolean lineTailReachable(int[] mk, boolean[] hd) {
            if (tailWallSeg.length == 0) return true;
            double[] tr = traceLine(mk, hd);
            double xlo = tr[0] + tr[6], xhi = tr[1] + tr[6];
            double zlo = tr[2] + tr[7], zhi = tr[3] + tr[7];
            double vxlo = tr[4], vxhi = tr[4], vzlo = tr[5], vzhi = tr[5];
            double slack = cfg.rectSlack + 2.0e-3;
            for (int k = last; k < p.numTicks; k++) {
                double a = 0.98 * (p.slip[k] < 1.0 ? accelGroundSprint[k] : Constants.AIR_SPEED_F);
                if (isPressSeg(k) && p.slip[k] < 1.0) a += 0.2;
                vxlo -= a; vxhi += a; vzlo -= a; vzhi += a;
                xlo += vxlo; xhi += vxhi; zlo += vzlo; zhi += vzhi;
                double f = p.slip[k] < 1.0 ? (double) (((float) p.slip[k]) * 0.91F) : 0.91;
                vxlo *= f; vxhi *= f; vzlo *= f; vzhi *= f;
                int at = k + 1;
                for (int i = 0; i < tailWallSeg.length; i++) {
                    if (tailWallSeg[i] != at) continue;
                    if (tailWallX[i]) {
                        xlo = Math.max(xlo, tailWallLo[i] - slack);
                        xhi = Math.min(xhi, tailWallHi[i] + slack);
                        if (xlo > xhi) return false;
                    } else {
                        zlo = Math.max(zlo, tailWallLo[i] - slack);
                        zhi = Math.min(zhi, tailWallHi[i] + slack);
                        if (zlo > zhi) return false;
                    }
                }
            }
            return true;
        }

        private static double gap(double aLo, double aHi, double bLo, double bHi) {
            if (bLo > aHi) return bLo - aHi;
            if (aLo > bHi) return aLo - bHi;
            return 0.0;
        }

        private static int comboOrder(int prevCombo, int i) {
            if (prevCombo < 0) return i;
            if (i == KeyLine.COMBO_COUNT - 1) return prevCombo;
            return i < prevCombo ? i : i + 1;
        }

        String walkLine(int[] mk, boolean[] holds) {
            double vx = 0.0;
            double vz = 0.0;
            double dx = 0.0;
            double dz = 0.0;
            boolean sprintPrev = false;
            double rxLo = p.rectXLo;
            double rxHi = p.rectXHi;
            double rzLo = p.rectZLo;
            double rzHi = p.rectZHi;
            for (int k = 0; k < last; k++) {
                boolean canRun = KeyLine.canRun(mk[k]);
                boolean sprintCur = canRun && (sprintPrev || holds[k]);
                if (perAxis) {
                    if (Math.abs(vx) < thr) vx = 0.0;
                    if (Math.abs(vz) < thr) vz = 0.0;
                } else if (vx * vx + vz * vz < 9.0E-6) {
                    vx = 0.0;
                    vz = 0.0;
                }
                boolean contact = p.slip[k] < 1.0;
                if (contact && isPressSeg(k) && sprintCur) {
                    vx -= (double) (sinJump * 0.2F);
                    vz += (double) (cosJump * 0.2F);
                }
                float accelSpeed;
                if (contact) {
                    accelSpeed = (float) (sprintCur ? accelGroundSprint[k] : accelGroundWalk[k]);
                } else {
                    boolean airSprint = k != 0 && sprintPrev;
                    accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
                }
                double[] a = comboAccel(mk[k], accelSpeed);
                vx += a[0];
                vz += a[1];
                dx += vx;
                dz += vz;
                float f4 = contact ? ((float) p.slip[k]) * 0.91F : 0.91F;
                vx *= (double) f4;
                vz *= (double) f4;
                int at = k + 1;
                for (int i = nextWallX[at]; i < wallXSeg.length && wallXSeg[i] == at; i++) {
                    rxLo = Math.max(rxLo, wallXLo[i] - cfg.rectSlack - dx);
                    rxHi = Math.min(rxHi, wallXHi[i] + cfg.rectSlack - dx);
                }
                for (int i = nextWallZ[at]; i < wallZSeg.length && wallZSeg[i] == at; i++) {
                    rzLo = Math.max(rzLo, wallZLo[i] - cfg.rectSlack - dz);
                    rzHi = Math.min(rzHi, wallZHi[i] + cfg.rectSlack - dz);
                }
                if (rxLo > rxHi || rzLo > rzHi) {
                    return String.format(Locale.ROOT, "rect empty after tick %d (X %.4f Z %.4f)",
                            k, rxHi - rxLo, rzHi - rzLo);
                }
                if (!reachable(at, vx, dx, rxLo, rxHi, true)) {
                    return "reachable X pruned after tick " + k;
                }
                if (!reachable(at, vz, dz, rzLo, rzHi, false)) {
                    return "reachable Z pruned after tick " + k;
                }
                sprintPrev = sprintCur;
            }
            double margin = tailMargin(vx, vz, dx, dz, rxLo, rxHi, rzLo, rzHi);
            if (margin < -cfg.rectSlack) {
                return String.format(Locale.ROOT, "tail margin pruned at emit (%.4f)", margin);
            }
            return "SURVIVES to emit";
        }

        double[] traceLine(int[] mk, boolean[] holds) {
            double vx = 0.0;
            double vz = 0.0;
            double dx = 0.0;
            double dz = 0.0;
            boolean sprintPrev = false;
            double txLo = p.rectXLo;
            double txHi = p.rectXHi;
            double tzLo = p.rectZLo;
            double tzHi = p.rectZHi;
            for (int k = 0; k < last; k++) {
                boolean canRun = KeyLine.canRun(mk[k]);
                boolean sprintCur = canRun && (sprintPrev || holds[k]);
                if (perAxis) {
                    if (Math.abs(vx) < thr) vx = 0.0;
                    if (Math.abs(vz) < thr) vz = 0.0;
                } else {
                    if (vx * vx + vz * vz < 9.0E-6) {
                        vx = 0.0;
                        vz = 0.0;
                    }
                }
                boolean contact = p.slip[k] < 1.0;
                if (contact && isPressSeg(k) && sprintCur) {
                    vx -= (double) (sinJump * 0.2F);
                    vz += (double) (cosJump * 0.2F);
                }
                float accelSpeed;
                if (contact) {
                    accelSpeed = (float) (sprintCur ? accelGroundSprint[k] : accelGroundWalk[k]);
                } else {
                    boolean airSprint = k != 0 && sprintPrev;
                    accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
                }
                double[] a = comboAccel(mk[k], accelSpeed);
                vx += a[0];
                vz += a[1];
                dx += vx;
                dz += vz;
                float f4 = contact ? ((float) p.slip[k]) * 0.91F : 0.91F;
                vx *= (double) f4;
                vz *= (double) f4;
                int at = k + 1;
                for (int i = nextWallX[at]; i < wallXSeg.length && wallXSeg[i] == at; i++) {
                    txLo = Math.max(txLo, wallXLo[i] - dx);
                    txHi = Math.min(txHi, wallXHi[i] - dx);
                }
                for (int i = nextWallZ[at]; i < wallZSeg.length && wallZSeg[i] == at; i++) {
                    tzLo = Math.max(tzLo, wallZLo[i] - dz);
                    tzHi = Math.min(tzHi, wallZHi[i] - dz);
                }
                sprintPrev = sprintCur;
            }
            return new double[] {txLo, txHi, tzLo, tzHi, vx, vz, dx, dz};
        }

        double[] traceLineTo(int[] mk, boolean[] holds, int endSeg) {
            double vx = 0.0;
            double vz = 0.0;
            double dx = 0.0;
            double dz = 0.0;
            boolean sprintPrev = false;
            double txLo = p.rectXLo;
            double txHi = p.rectXHi;
            double tzLo = p.rectZLo;
            double tzHi = p.rectZHi;
            for (int k = 0; k < endSeg; k++) {
                boolean canRun = KeyLine.canRun(mk[k]);
                boolean sprintCur = canRun && (sprintPrev || holds[k]);
                if (perAxis) {
                    if (Math.abs(vx) < thr) vx = 0.0;
                    if (Math.abs(vz) < thr) vz = 0.0;
                } else {
                    if (vx * vx + vz * vz < 9.0E-6) {
                        vx = 0.0;
                        vz = 0.0;
                    }
                }
                boolean contact = p.slip[k] < 1.0;
                if (contact && isPressSeg(k) && sprintCur) {
                    vx -= (double) (sinJump * 0.2F);
                    vz += (double) (cosJump * 0.2F);
                }
                float accelSpeed;
                if (contact) {
                    accelSpeed = (float) (sprintCur ? accelGroundSprint[k] : accelGroundWalk[k]);
                } else {
                    boolean airSprint = k != 0 && sprintPrev;
                    accelSpeed = airSprint ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
                }
                double[] a = comboAccel(mk[k], accelSpeed);
                vx += a[0];
                vz += a[1];
                dx += vx;
                dz += vz;
                float f4 = contact ? ((float) p.slip[k]) * 0.91F : 0.91F;
                vx *= (double) f4;
                vz *= (double) f4;
                int at = k + 1;
                for (int i = nextWallX[at]; i < wallXSeg.length && wallXSeg[i] == at; i++) {
                    txLo = Math.max(txLo, wallXLo[i] - dx);
                    txHi = Math.min(txHi, wallXHi[i] - dx);
                }
                for (int i = nextWallZ[at]; i < wallZSeg.length && wallZSeg[i] == at; i++) {
                    tzLo = Math.max(tzLo, wallZLo[i] - dz);
                    tzHi = Math.min(tzHi, wallZHi[i] - dz);
                }
                sprintPrev = sprintCur;
            }
            return new double[] {txLo, txHi, tzLo, tzHi, vx, vz, dx, dz};
        }
    }

    private static final double PROBE_SCAN_STEP = 0.5;

    private static int scanIndex(double thetaDeg, int len) {
        int si = (int) Math.round((thetaDeg + 180.0) / PROBE_SCAN_STEP);
        if (si < 0) si = 0;
        if (si >= len) si = len - 1;
        return si;
    }

    static double[] quickScoreSig(ColdProblem p, List<Candidate> stored, Sweep[] scan, Config cfg,
                                  AtomicBoolean cancel) {
        Candidate c0 = stored.get(0);
        if (p.singleHeld) {
            double[] hb = heldFacingProbe(p, c0.moveKey, c0.hold);
            c0.tailCombo = (int) hb[2];
            return new double[] {hb[0], hb[1]};
        }
        double[] arcs = c0.arcsDeg;
        if (arcs == null || arcs.length < 2) arcs = new double[] {c0.theta - 1.0, c0.theta + 1.0};
        double best = Double.POSITIVE_INFINITY;
        double bestTh = c0.theta;
        for (int ai = 0; ai + 1 < arcs.length; ai += 2) {
            double lo = arcs[ai];
            double hi = arcs[ai + 1];
            double span = Math.max(hi - lo, 1.0e-6);
            int steps = Math.max(1, (int) Math.ceil(span / 2.0));
            for (int s = 0; s <= steps; s++) {
                double th = lo + span * s / steps;
                Sweep sw = scan[scanIndex(th, scan.length)];
                double[] tr = sw.traceLine(c0.moveKey, c0.hold);
                double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
                double v = probeAt(p, c0, cfg, w, tr, sw, sw.theta, cancel);
                if (v < best) {
                    best = v;
                    bestTh = sw.theta;
                }
            }
        }
        if (Double.isFinite(best)) {
            ThetaWindow wn = refineTheta(p, cfg, c0.moveKey, c0.hold, bestTh);
            if (wn.width >= -cfg.rectSlack) {
                double v = probeAt(p, c0, cfg, wn.width, wn.trace, wn.sweep, wn.theta, cancel);
                if (v < best) {
                    best = v;
                    bestTh = wn.theta;
                }
            }
        }
        return new double[] {best, bestTh};
    }
    private static final double THETA_REFINE_FLOOR = 5.0e-4;

    static final class ThetaWindow {
        final double theta;
        final double[] trace;
        final Sweep sweep;
        final double width;

        ThetaWindow(double theta, double[] trace, Sweep sweep) {
            this.theta = theta;
            this.trace = trace;
            this.sweep = sweep;
            this.width = Math.min(trace[1] - trace[0], trace[3] - trace[2]);
        }
    }

    private static ThetaWindow refineTheta(ColdProblem p, Config cfg, int[] mk, boolean[] hold, double theta0) {
        double bestTh = theta0;
        Sweep bestSweep = new Sweep(p, cfg, theta0, 0, null);
        double[] bestTrace = bestSweep.traceLine(mk, hold);
        double bestW = Math.min(bestTrace[1] - bestTrace[0], bestTrace[3] - bestTrace[2]);
        double step = PROBE_SCAN_STEP * 0.5;
        while (step >= THETA_REFINE_FLOOR) {
            boolean improved = false;
            for (int dir = -1; dir <= 1; dir += 2) {
                double th = bestTh + dir * step;
                Sweep s = new Sweep(p, cfg, th, 0, null);
                double[] t = s.traceLine(mk, hold);
                double w = Math.min(t[1] - t[0], t[3] - t[2]);
                if (w > bestW) {
                    bestW = w;
                    bestTh = th;
                    bestSweep = s;
                    bestTrace = t;
                    improved = true;
                    break;
                }
            }
            if (!improved) step *= 0.5;
        }
        return new ThetaWindow(bestTh, bestTrace, bestSweep);
    }

    static List<ThetaWindow> thetaWindows(ColdProblem p, Config cfg, int[] mk, boolean[] hold,
                                          Sweep[] scan, int seeds) {
        double[] widths = new double[scan.length];
        for (int i = 0; i < scan.length; i++) {
            double[] t = scan[i].traceLine(mk, hold);
            widths[i] = Math.min(t[1] - t[0], t[3] - t[2]);
        }
        List<Integer> seedIdx = new ArrayList<Integer>();
        for (int i = 0; i < scan.length; i++) {
            int prev = (i + scan.length - 1) % scan.length;
            int next = (i + 1) % scan.length;
            if (widths[i] >= widths[prev] && widths[i] >= widths[next]) seedIdx.add(i);
        }
        Collections.sort(seedIdx, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(widths[b], widths[a]);
            }
        });
        List<ThetaWindow> out = new ArrayList<ThetaWindow>();
        for (Integer idx : seedIdx) {
            if (out.size() >= seeds) break;
            double th = scan[idx].theta;
            boolean near = false;
            for (ThetaWindow w : out) {
                if (Math.abs(Angles.wrap(w.theta - th)) < 1.0) {
                    near = true;
                    break;
                }
            }
            if (near) continue;
            out.add(refineTheta(p, cfg, mk, hold, th));
        }
        Collections.sort(out, new Comparator<ThetaWindow>() {
            @Override
            public int compare(ThetaWindow a, ThetaWindow b) {
                return Double.compare(b.width, a.width);
            }
        });
        return out;
    }

    private static final int PROBE_FIRST_SAMPLES = 4;
    private static final double PROBE_EXTEND_TRIGGER = 5.0e-2;

    static List<ThetaWindow> thetaSamples(ColdProblem p, Config cfg, int[] mk, boolean[] hold, Sweep[] scan) {
        double[] widths = new double[scan.length];
        double[][] traces = new double[scan.length][];
        for (int i = 0; i < scan.length; i++) {
            traces[i] = scan[i].traceLine(mk, hold);
            widths[i] = Math.min(traces[i][1] - traces[i][0], traces[i][3] - traces[i][2]);
        }
        List<ThetaWindow> out = new ArrayList<ThetaWindow>();
        int i = 0;
        while (i < scan.length) {
            if (widths[i] < 0.0) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < scan.length && widths[j + 1] >= 0.0) j++;
            int runLen = j - i + 1;
            int samples = Math.min(runLen, PROBE_RUN_SAMPLES);
            for (int k = 0; k < samples; k++) {
                int idx = samples == 1 ? i : i + (int) Math.round(k * (runLen - 1.0) / (samples - 1.0));
                out.add(new ThetaWindow(scan[idx].theta, traces[idx], scan[idx]));
            }
            i = j + 1;
        }
        List<ThetaWindow> peaks = thetaWindows(p, cfg, mk, hold, scan, PROBE_PEAK_SEEDS);
        for (ThetaWindow w : peaks) {
            if (w.width < 0.0 && w.width >= -cfg.rectSlack) out.add(w);
            if (w.width >= 0.0) {
                boolean covered = false;
                for (ThetaWindow o : out) {
                    if (Math.abs(Angles.wrap(o.theta - w.theta)) < 0.25) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) out.add(w);
            }
        }
        Collections.sort(out, new Comparator<ThetaWindow>() {
            @Override
            public int compare(ThetaWindow a, ThetaWindow b) {
                return Double.compare(b.width, a.width);
            }
        });
        return out;
    }

    private static final int PROBE_RUN_SAMPLES = 10;
    private static final int PROBE_PEAK_SEEDS = 6;

    private static Candidate probeSig(ColdProblem p, List<Candidate> stored, Sweep[] scan, Config cfg,
                                      AtomicBoolean cancel) {
        if (stored.isEmpty()) return null;
        Candidate c = stored.get(0);
        if (p.singleHeld) {
            double[] hb = heldFacingProbe(p, c.moveKey, c.hold);
            c.probeViol = hb[0];
            c.probeTheta = hb[1];
            c.tailCombo = (int) hb[2];
            c.trueOpen = Double.isFinite(hb[0]);
            return c;
        }
        List<ThetaWindow> samples;
        if (c.arcsDeg != null && c.arcsDeg.length > 0) {
            samples = new ArrayList<ThetaWindow>();
            for (Candidate sc : stored) {
                if (sc.arcsDeg == null) continue;
                for (int i = 0; i + 1 < sc.arcsDeg.length && samples.size() < 24; i += 2) {
                    double lo = sc.arcsDeg[i];
                    double hi = sc.arcsDeg[i + 1];
                    int inner = Math.max(1, Math.min(5, (int) Math.ceil((hi - lo) / 2.0)));
                    for (int j = 0; j < inner; j++) {
                        double th = lo + (hi - lo) * (j + 0.5) / inner;
                        samples.add(refineTheta(p, cfg, c.moveKey, c.hold, th));
                    }
                }
            }
            Collections.sort(samples, new Comparator<ThetaWindow>() {
                @Override
                public int compare(ThetaWindow a, ThetaWindow b) {
                    return Double.compare(b.width, a.width);
                }
            });
        } else {
            samples = thetaSamples(p, cfg, c.moveKey, c.hold, scan);
        }
        if (samples.isEmpty()) {
            c.probeViol = Double.POSITIVE_INFINITY;
            return c;
        }
        c.trueOpen = samples.get(0).width >= 0.0;
        double best = Double.isNaN(c.probeViol) ? Double.POSITIVE_INFINITY : c.probeViol;
        double bestTheta = Double.isNaN(c.probeTheta) ? samples.get(0).theta : c.probeTheta;
        int bestTail = c.tailCombo;
        int spread = Math.max(1, samples.size() / PROBE_FIRST_SAMPLES);
        for (int pass = 0; pass < 2; pass++) {
            for (int si = 0; si < samples.size(); si++) {
                boolean firstPass = si % spread == 0;
                if (pass == 0 != firstPass) continue;
                ThetaWindow w = samples.get(si);
                if (w.width < -cfg.rectSlack) continue;
                double v = probeAt(p, c, cfg, w.width, w.trace, w.sweep, w.theta, cancel);
                if (v < best) {
                    best = v;
                    bestTheta = w.theta;
                    bestTail = c.tailCombo;
                }
                if (best <= 0.0) break;
            }
            if (best <= 0.0 || best > PROBE_EXTEND_TRIGGER) break;
        }
        c.probeTheta = bestTheta;
        c.tailCombo = bestTail;
        c.probeViol = best;
        return c;
    }

    static double probeAt(ColdProblem p, Candidate c, Config cfg, double bestW,
                          double[] bestTrace, Sweep bestSweep, double thP, AtomicBoolean cancel) {
        if (bestW < -cfg.rectSlack) return Double.POSITIVE_INFINITY;

        double txLo = Math.min(bestTrace[0], bestTrace[1]);
        double txHi = Math.max(bestTrace[0], bestTrace[1]);
        double tzLo = Math.min(bestTrace[2], bestTrace[3]);
        double tzHi = Math.max(bestTrace[2], bestTrace[3]);
        double vx = bestTrace[4];
        double vz = bestTrace[5];
        double dx = bestTrace[6];
        double dz = bestTrace[7];
        double refX = 0.5 * (txLo + txHi) + dx;
        double refZ = 0.5 * (tzLo + tzHi) + dz;

        int[] tails = p.tailYawsFree ? new int[] {KeyLine.WA} : new int[] {KeyLine.W, KeyLine.WA, KeyLine.WD};
        double best = Double.POSITIVE_INFINITY;
        for (int tail : tails) {
            KeyLine line = new KeyLine(p, c.moveKey, c.hold, tail);
            JumpSpec slice = buildSliceSpec(p, line, thP, refX, refZ,
                    txLo + dx, txHi + dx, tzLo + dz, tzHi + dz, vx, vz);
            double v;
            if (!p.tailYawsFree) {
                JumpPhysicsInputs sc = slice.asScenario();
                double[] yaws = new double[sc.numTicks];
                Arrays.fill(yaws, thP);
                double[] rs = FreeStartSolve.recoverStart(p.model, slice, yaws);
                v = rs != null ? FreeStartSolve.violationAt(p.model, slice, yaws, rs[0], rs[1])
                        : FreeStartSolve.violationAt(p.model, slice, yaws, refX, refZ);
            } else {
                v = dualScreenViol(p, slice);
                if (Double.isNaN(v)) {
                    v = probeTail(p, c, tail, thP, refX, refZ, vx, vz, cancel);
                }
            }
            if (v < best) {
                best = v;
                c.tailCombo = tail;
            }
            if (best <= 0.0) break;
        }
        return best;
    }

    static double dualScreenViol(ColdProblem p, JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel lin =
                new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc);
        de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold pre =
                de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold.analyze(spec.constraints, lin);
        if (pre == null) return Double.NaN;
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.Wall> walls =
                lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) return Double.POSITIVE_INFINITY;
        de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold.Reduced red =
                pre.reduce(cx, cz, lin.mMagAll(), walls);
        StartBox box = sc.startBox;
        de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver solver;
        if (box != null && box.startFree()) {
            boolean max = spec.objective.sense == Objective.Sense.MAX;
            double devX = spec.objective.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
            double devZ = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
            solver = new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(
                    red.n, red.cx, red.cz, red.mMag, red.walls,
                    new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.FreeP0(
                            box.pxLo - box.px, box.pxHi - box.px, box.pzLo - box.pz, box.pzHi - box.pz,
                            devX, devZ));
        } else {
            solver = new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(
                    red.n, red.cx, red.cz, red.mMag, red.walls);
        }
        de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.Result r = solver.solve(0.0, null);
        if (r == null) return Double.NaN;
        double[] yaws = pre.expand(lin, spec.objective, r);
        if (box != null && box.startFree()) {
            double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
            if (rs != null) return FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1]);
        }
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, p.model.forward(sc, gf));
    }

    private static double probeTail(ColdProblem p, Candidate c, int tail, double theta,
                                    double ex, double ez, double vx, double vz, AtomicBoolean cancel) {
        KeyLine line = new KeyLine(p, c.moveKey, c.hold, tail);
        JumpSpec spec = buildSliceSpec(p, line, theta, ex, ez, ex, ex, ez, ez, vx, vz);
        try {
            if (!p.tailYawsFree) {
                JumpPhysicsInputs sc = spec.asScenario();
                double[] yaws = new double[sc.numTicks];
                java.util.Arrays.fill(yaws, theta);
                double[] gf = sc.toGameFacings(yaws);
                return JumpConstraintCompiler.compile(spec).maxViolation(gf, p.model.forward(sc, gf));
            }
            ProbeResult r = sliceProbeSolve(p, spec, cancel);
            if (r == null) return Double.POSITIVE_INFINITY;
            return r.feasible ? 0.0 : r.viol;
        } catch (RuntimeException ex2) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private static final class ProbeResult {
        final double[] yaws;
        final double viol;
        final boolean feasible;

        ProbeResult(double[] yaws, double viol, boolean feasible) {
            this.yaws = yaws;
            this.viol = viol;
            this.feasible = feasible;
        }
    }

    static double SLP_RESCUE_TRIGGER = 5.0e-3;

    private static ProbeResult sliceProbeSolve(ColdProblem p, JumpSpec spec, AtomicBoolean cancel) {
        profProbeSolves++;
        ClosedFormSolve.Result r = null;
        long tc = System.nanoTime();
        try {
            r = ClosedFormSolve.optimizeRobustGraded(p.model, spec, 0.0, cancel);
        } catch (RuntimeException ex) {
        }
        profNsClosedForm += System.nanoTime() - tc;
        if (r != null && r.feasible) return new ProbeResult(r.yaws, 0.0, true);
        if (r == null) {
            Objective o = spec.objective;
            Objective flipped = new Objective(o.axis,
                    o.sense == Objective.Sense.MAX ? Objective.Sense.MIN : Objective.Sense.MAX,
                    spec.asScenario().numTicks);
            long tc2 = System.nanoTime();
            try {
                r = ClosedFormSolve.optimizeRobustGraded(p.model,
                        new JumpSpec(spec.asScenario(), spec.constraints, flipped), 0.0, cancel);
            } catch (RuntimeException ex) {
            }
            profNsClosedForm += System.nanoTime() - tc2;
            if (r != null && r.feasible) return new ProbeResult(r.yaws, 0.0, true);
        }
        if (r == null || r.violation < SLP_RESCUE_TRIGGER) {
            double[] y;
            long tsl = System.nanoTime();
            try {
                y = de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve.optimize(p.model, spec, 0.0, cancel);
            } catch (RuntimeException ex) {
                y = null;
            }
            profNsSlp += System.nanoTime() - tsl;
            if (y != null) {
                JumpPhysicsInputs sc = spec.asScenario();
                double[] wrapped = Angles.wrapAll(y);
                double[] gf = sc.toGameFacings(wrapped);
                double v = JumpConstraintCompiler.compile(spec).maxViolation(gf, p.model.forward(sc, gf));
                if (v <= 0.0) {
                    profSlpDecisiveCount++;
                    if (r == null) profSlpDecisiveNull++;
                    else profSlpDecisiveMaxViol = Math.max(profSlpDecisiveMaxViol, r.violation);
                    return new ProbeResult(wrapped, 0.0, true);
                }
                if (r == null || v < r.violation) return new ProbeResult(wrapped, v, false);
            }
        }
        if (r == null) return null;
        return new ProbeResult(r.yaws, r.violation, r.feasible);
    }

    static JumpSpec buildSliceSpec(ColdProblem p, KeyLine line, double theta,
                                   double refX, double refZ, double pxLo, double pxHi,
                                   double pzLo, double pzHi, double vx, double vz) {
        int lastPress = p.lastPressSeg;
        int n2 = p.numTicks - lastPress;
        boolean[] sprint = line.sprintStates();

        JumpPhysicsInputs sc = new JumpPhysicsInputs(n2);
        sc.startPos = new Vec3dCore(refX, 0.0, refZ);
        sc.startYaw = (float) theta;
        sc.initialVelocity = new Vec3dCore(vx, 0.0, vz);
        sc.startBox = new StartBox(refX, refZ, vx, vz, pxLo, pxHi, pzLo, pzHi, vx, vx, vz, vz);
        sc.jumpTick = 0;
        boolean[] jump = new boolean[n2];
        jump[0] = true;
        sc.jumpPerTick = jump;
        double[] slip2 = new double[n2];
        boolean[] sprint2 = new boolean[n2];
        float[] fwd2 = new float[n2];
        float[] str2 = new float[n2];
        for (int i = 0; i < n2; i++) {
            int seg = lastPress + i;
            slip2[i] = p.slip[seg] < 1.0 ? p.slip[seg] : Double.NaN;
            sprint2[i] = sprint[seg];
            int combo = line.comboAt(seg);
            fwd2[i] = LineSpec.KEY_INPUT_SCALE * KeyLine.FORWARD_SIGN[combo];
            str2[i] = LineSpec.KEY_INPUT_SCALE * KeyLine.STRAFE_SIGN[combo];
        }
        sc.slipPerTick = slip2;
        sc.sprintPerTick = sprint2;
        sc.forwardInputPerTick = fwd2;
        sc.strafeInputPerTick = str2;
        sc.incomingSprint = lastPress > 0 ? sprint[lastPress - 1] : Boolean.FALSE;
        sc.incomingAmp = 0;

        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        AngleSolverState state = p.state;
        for (Integer tickKey : state.populatedTicks()) {
            int seg2 = tickKey - p.startTick - lastPress;
            if (seg2 < 0 || seg2 > n2) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint uc : tc.getConstraints()) {
                if (!uc.isEnabled()) continue;
                mapSliceConstraint(cons, uc, seg2, n2, theta, p.lastPressYawTied);
            }
        }

        Objective obj = new Objective(axisOf(state), senseOf(state), n2);
        return new JumpSpec(sc, cons, obj);
    }

    private static void mapSliceConstraint(List<JumpConstraint> out, Constraint c, int seg2, int n2,
                                           double theta, boolean lastPressYawTied) {
        String tag = "cold" + c.getField() + "@" + seg2;
        switch (c.getField()) {
            case X:
            case Z: {
                if (c.isRelative()) return;
                JumpConstraint.Mode mode = c.getField() == Constraint.Field.X
                        ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
                addScalarOrRange(out, mode, seg2, c, tag);
                return;
            }
            case F:
                if (seg2 >= n2) return;
                addScalarOrRange(out, JumpConstraint.Mode.F, seg2, c, tag);
                return;
            case DF:
                if (seg2 >= n2) return;
                if (seg2 >= 1) {
                    addRelative(out, JumpConstraint.Mode.F, seg2, seg2 - 1, c, tag);
                } else if (lastPressYawTied) {
                    out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS,
                            JumpConstraint.Cmp.GE, theta - MET_TOL, tag + "pinLo"));
                    out.add(new JumpConstraint(JumpConstraint.Mode.F, 0, null, JumpConstraint.Op.PLUS,
                            JumpConstraint.Cmp.LE, theta + MET_TOL, tag + "pinHi"));
                }
                return;
            default:
        }
    }

    private static void addScalarOrRange(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1,
                                         Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(mode, t1, null, JumpConstraint.Op.PLUS, cmpOf(c.getOp()), c.getValue(), tag));
        }
    }

    private static void addRelative(List<JumpConstraint> out, JumpConstraint.Mode mode, int t1, int t2,
                                    Constraint c, String tag) {
        if (c.isRange()) {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getLo(), tag + "lo"));
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getHi(), tag + "hi"));
        } else if (c.getOp() == Constraint.Op.EQ) {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, c.getValue() - MET_TOL, tag + "eqLo"));
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, JumpConstraint.Cmp.LE, c.getValue() + MET_TOL, tag + "eqHi"));
        } else {
            out.add(new JumpConstraint(mode, t1, t2, JumpConstraint.Op.MINUS, cmpOf(c.getOp()), c.getValue(), tag));
        }
    }

    private static JumpConstraint.Cmp cmpOf(Constraint.Op op) {
        switch (op) {
            case LT:
            case LE:
                return JumpConstraint.Cmp.LE;
            case GT:
            case GE:
                return JumpConstraint.Cmp.GE;
            default:
                return JumpConstraint.Cmp.EQ;
        }
    }

    private static JumpPhysicsInputs.Axis axisOf(AngleSolverState state) {
        return state.getAxis() == AngleSolverState.Axis.X ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
    }

    private static Objective.Sense senseOf(AngleSolverState state) {
        return state.getGoal() == AngleSolverState.Goal.MAX ? Objective.Sense.MAX : Objective.Sense.MIN;
    }

    private static ColdResult certify(ColdProblem p, Candidate c, boolean jointFallback, Sweep[] scan,
                                      AtomicBoolean cancel, long t0, int level,
                                      long nodes, int cands, int probed, int certified, boolean truncated) {
        double sx0 = clamp(0.5 * (c.rxLo + c.rxHi), c.rxLo, c.rxHi);
        double sz0 = clamp(0.5 * (c.rzLo + c.rzHi), c.rzLo, c.rzHi);
        Sol r;
        try {
            r = p.singleHeld ? heldChainScan(p, c, cancel) : null;
            if (r == null) {
                KeyLine line0 = new KeyLine(p, c.moveKey, c.hold, c.tailCombo);
                JumpSpec spec0 = LineSpec.build(line0, c.theta, sx0, sz0);
                if (spec0 == null) return null;
                r = certifyDirect(p, line0, spec0, c, scan, cancel);
                if (r == null && ENGINE_FALLBACK && jointFallback && !p.singleHeld) {
                    double th = Double.isNaN(c.probeTheta) ? c.theta : c.probeTheta;
                    r = engineCertify(p, line0, th, sx0, sz0, false, cancel);
                    if (r == null && c.probeViol < 2.0e-2) {
                        r = engineCertify(p, line0, th, sx0, sz0, true, cancel);
                    }
                }
            }
        } catch (RuntimeException ex) {
            return null;
        }
        if (r == null) return null;

        KeyLine line = new KeyLine(p, c.moveKey, c.hold, c.tailCombo);
        JumpSpec spec = LineSpec.build(line, c.theta, sx0, sz0);
        if (spec == null) return null;
        double viol = FreeStartSolve.violationAt(p.model, spec, r.yaws, r.startX, r.startZ);
        if (viol > 0.0) return null;

        double[] wrapped = Angles.wrapAll(r.yaws);
        JumpPhysicsInputs at = spec.asScenario().copy();
        at.startPos = new Vec3dCore(r.startX, at.startPos.y, r.startZ);
        at.startBox = StartBox.pinned(r.startX, r.startZ, at.initialVelocity.x, at.initialVelocity.z);
        double[] gf = at.toGameFacings(wrapped);
        ForwardPath path = p.model.forward(at, gf);
        double check = JumpConstraintCompiler.compile(new JumpSpec(at, spec.constraints, spec.objective))
                .maxViolation(gf, path);
        if (check > 0.0) return null;

        double facingDeg = p.singleHeld && wrapped.length > 0 ? wrapped[0] : c.theta;
        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
        return new ColdResult(line, facingDeg, wrapped, r.startX, r.startZ, check, path, elapsed, level,
                nodes, cands, probed, certified, truncated);
    }

    private static final class Sol {
        final double[] yaws;
        final double startX;
        final double startZ;

        Sol(double[] yaws, double startX, double startZ) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
        }
    }

    static volatile String lastDirectDebug = "";

    static int profEntryEvals;
    static int profProbeSolves;
    static int profFeasEntries;
    static long profNsBuildSpec;
    static long profNsProbeSolve;
    static long profNsClosedForm;
    static long profNsSlp;
    static double profSlpDecisiveMaxViol;
    static int profSlpDecisiveNull;
    static int profSlpDecisiveCount;
    static int profDescentDecisive;
    static double profDescentDecisiveMaxMiss;

    static void profReset() {
        profEntryEvals = 0;
        profProbeSolves = 0;
        profFeasEntries = 0;
        profNsBuildSpec = 0;
        profNsProbeSolve = 0;
        profNsClosedForm = 0;
        profNsSlp = 0;
        profSlpDecisiveMaxViol = 0.0;
        profSlpDecisiveNull = 0;
        profSlpDecisiveCount = 0;
        profDescentDecisive = 0;
        profDescentDecisiveMaxMiss = 0.0;
    }

    private static final int DIRECT_SLICE_TRIES = 6;

    private static final class MomentumEval {
        final double theta;
        final JumpSpec spec;
        final ForwardPath path;
        final double txLo;
        final double txHi;
        final double tzLo;
        final double tzHi;

        MomentumEval(double theta, JumpSpec spec, ForwardPath path,
                     double txLo, double txHi, double tzLo, double tzHi) {
            this.theta = theta;
            this.spec = spec;
            this.path = path;
            this.txLo = txLo;
            this.txHi = txHi;
            this.tzLo = tzLo;
            this.tzHi = tzHi;
        }

        boolean rectNonEmpty() {
            return txLo <= txHi && tzLo <= tzHi;
        }

        double minWidth() {
            return Math.min(txHi - txLo, tzHi - tzLo);
        }
    }

    private static MomentumEval evalMomentum(ColdProblem p, KeyLine line, double theta, double sx, double sz) {
        JumpSpec spec = LineSpec.build(line, theta, sx, sz);
        if (spec == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        double[] held = new double[n];
        java.util.Arrays.fill(held, theta);
        double[] gf = sc.toGameFacings(held);
        ForwardPath path = p.model.forward(sc, gf);
        double txLo = p.rectXLo;
        double txHi = p.rectXHi;
        double tzLo = p.rectZLo;
        double tzHi = p.rectZHi;
        for (ColdProblem.Wall w : p.momentumWalls) {
            double d = (w.axisX ? path.posX[w.segTick] : path.posZ[w.segTick])
                    - (w.axisX ? sc.startPos.x : sc.startPos.z);
            if (w.axisX) {
                txLo = Math.max(txLo, w.lo - d);
                txHi = Math.min(txHi, w.hi - d);
            } else {
                tzLo = Math.max(tzLo, w.lo - d);
                tzHi = Math.min(tzHi, w.hi - d);
            }
        }
        return new MomentumEval(theta, spec, path, txLo, txHi, tzLo, tzHi);
    }

    private static final int DIRECT_FULL_SEEDS = 3;

    private static Sol certifyDirect(ColdProblem p, KeyLine line, JumpSpec spec, Candidate c,
                                     Sweep[] scan, AtomicBoolean cancel) {
        JumpPhysicsInputs base = spec.asScenario();
        double sx = base.startPos.x;
        double sz = base.startPos.z;
        lastDirectDebug = "";

        List<ThetaWindow> windows = new ArrayList<ThetaWindow>(
                thetaSamples(p, DEFAULT_CFG, c.moveKey, c.hold, scan));
        if (!Double.isNaN(c.probeTheta)) {
            windows.add(refineTheta(p, DEFAULT_CFG, c.moveKey, c.hold, c.probeTheta));
        }
        List<double[]> quick = new ArrayList<double[]>();
        double bestDeficit = Double.POSITIVE_INFINITY;
        double bestDeficitTh = Double.NaN;
        for (ThetaWindow w : windows) {
            if (w.width >= 0.0) {
                double v = quickSliceViol(p, line, w.sweep, w.trace, cancel);
                quick.add(new double[] {w.theta, v});
            } else {
                if (-w.width < bestDeficit) {
                    bestDeficit = -w.width;
                    bestDeficitTh = w.theta;
                }
                if (w.width >= -DEFAULT_CFG.rectSlack) {
                    double[] tr = w.trace.clone();
                    double mx = 0.5 * (tr[0] + tr[1]);
                    double mz = 0.5 * (tr[2] + tr[3]);
                    tr[0] = mx;
                    tr[1] = mx;
                    tr[2] = mz;
                    tr[3] = mz;
                    double v = quickSliceViol(p, line, w.sweep, tr, cancel);
                    quick.add(new double[] {w.theta, v});
                }
            }
        }
        if (quick.isEmpty()) {
            lastDirectDebug = String.format(Locale.ROOT, "rect=empty deficit=%.4f@%.2f", bestDeficit, bestDeficitTh);
            return null;
        }
        Collections.sort(quick, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[1], b[1]);
            }
        });
        lastDirectDebug = String.format(Locale.ROOT, "windows=%d quickBest=%.4e@%.4f",
                quick.size(), quick.get(0)[1], quick.get(0)[0]);

        for (int i = 0; i < Math.min(quick.size(), DIRECT_FULL_SEEDS); i++) {
            double[] seed = quick.get(i);
            MomentumEval e = evalMomentum(p, line, seed[0], sx, sz);
            double[] miss = {seed[1]};
            if (e != null && e.rectNonEmpty()) {
                Sol r = sliceSolve(p, line, e, cancel, miss);
                if (r != null) return r;
            }
            double best = Math.min(miss[0], seed[1]);
            if (best < DIRECT_DESCENT_TRIGGER) {
                Sol r = thetaDescent(p, line, seed[0], best, sx, sz, cancel);
                if (r != null) return r;
            }
        }
        if (quick.get(0)[1] < BUCKET_SWEEP_TRIGGER) {
            double thetaC = dualRefineTheta(p, line, quick.get(0)[0], cancel);
            Sol r = bucketSweepCertify(p, line, thetaC, sx, sz, cancel);
            if (r != null) return r;
        }
        return null;
    }

    private static final double YAW_BUCKET_DEG = (180.0 / Math.PI) / 10430.378350470453;
    static int BUCKET_SWEEP_RADIUS = 40;
    static int BUCKET_SLICE_BUDGET = 30;
    static double BUCKET_LP_MAX = 5.0e-2;
    private static final double BUCKET_SWEEP_TRIGGER = 1.5e-1;
    private static final double[] DUAL_REFINE_SCALES = {0.5, 0.125, 0.03, 0.008};

    private static double quickViolAtTheta(ColdProblem p, KeyLine line, double theta, AtomicBoolean cancel) {
        Sweep s = new Sweep(p, DEFAULT_CFG, theta, 0, null);
        double[] tr = s.traceLine(line.moveKey, line.sprintHold);
        double width = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
        if (width < -DEFAULT_CFG.rectSlack) return Double.POSITIVE_INFINITY;
        if (width < 0.0) {
            double mx = 0.5 * (tr[0] + tr[1]);
            double mz = 0.5 * (tr[2] + tr[3]);
            tr[0] = mx;
            tr[1] = mx;
            tr[2] = mz;
            tr[3] = mz;
        }
        return quickSliceViol(p, line, s, tr, cancel);
    }

    private static double dualRefineTheta(ColdProblem p, KeyLine line, double theta0, AtomicBoolean cancel) {
        double best = theta0;
        double bestV = quickViolAtTheta(p, line, theta0, cancel);
        for (double scale : DUAL_REFINE_SCALES) {
            double lb = best;
            double lv = bestV;
            for (int k = -6; k <= 6; k++) {
                if (k == 0) continue;
                double th = best + k * scale;
                double v = quickViolAtTheta(p, line, th, cancel);
                if (v < lv) {
                    lv = v;
                    lb = th;
                }
            }
            best = lb;
            bestV = lv;
            if (bestV <= 0.0) break;
        }
        return best;
    }

    private static Sol bucketSweepCertify(ColdProblem p, KeyLine line, double thetaC,
                                          double sx, double sz, AtomicBoolean cancel) {
        int m = 2 * BUCKET_SWEEP_RADIUS + 1;
        final double[] dual = new double[m];
        Integer[] order = new Integer[m];
        for (int i = 0; i < m; i++) {
            double th = thetaC + (i - BUCKET_SWEEP_RADIUS) * YAW_BUCKET_DEG;
            dual[i] = quickViolAtTheta(p, line, th, cancel);
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(dual[a], dual[b]);
            }
        });
        int budget = BUCKET_SLICE_BUDGET;
        for (int oi = 0; oi < m && budget > 0; oi++) {
            int i = order[oi];
            if (!(dual[i] < BUCKET_LP_MAX)) break;
            double th = thetaC + (i - BUCKET_SWEEP_RADIUS) * YAW_BUCKET_DEG;
            MomentumEval e = evalMomentum(p, line, th, sx, sz);
            if (e == null || !e.rectNonEmpty()) continue;
            budget--;
            double[] miss = {Double.POSITIVE_INFINITY};
            Sol r = sliceSolve(p, line, e, cancel, miss);
            if (r != null) return r;
            if (cancel != null && cancel.get()) return null;
        }
        return null;
    }

    private static double quickSliceViol(ColdProblem p, KeyLine line, Sweep s, double[] trace,
                                         AtomicBoolean cancel) {
        double txLo = trace[0];
        double txHi = trace[1];
        double tzLo = trace[2];
        double tzHi = trace[3];
        double vx = trace[4];
        double vz = trace[5];
        double dx = trace[6];
        double dz = trace[7];
        double refX = 0.5 * (txLo + txHi) + dx;
        double refZ = 0.5 * (tzLo + tzHi) + dz;
        JumpSpec slice = buildSliceSpec(p, line, s.theta, refX, refZ,
                txLo + dx, txHi + dx, tzLo + dz, tzHi + dz, vx, vz);
        double v = dualScreenViol(p, slice);
        if (!Double.isNaN(v)) return v;
        ProbeResult pr = sliceProbeSolve(p, slice, cancel);
        if (pr == null) return Double.POSITIVE_INFINITY;
        return pr.feasible ? 0.0 : pr.viol;
    }

    private static final double DIRECT_DESCENT_TRIGGER = 1.0e-2;
    private static final double[] DESCENT_STEPS = {0.0625, 0.015625, 0.004, 0.001};
    private static final int DESCENT_MAX_EVALS = 24;

    private static Sol thetaDescent(ColdProblem p, KeyLine line, double theta, double missAt,
                                    double sx, double sz, AtomicBoolean cancel) {
        int evals = 0;
        for (double step : DESCENT_STEPS) {
            boolean improved = true;
            while (improved && evals < DESCENT_MAX_EVALS) {
                improved = false;
                for (int dir = -1; dir <= 1; dir += 2) {
                    double th = theta + dir * step;
                    MomentumEval e = evalMomentum(p, line, th, sx, sz);
                    if (e == null || !e.rectNonEmpty()) continue;
                    double[] miss = {Double.POSITIVE_INFINITY};
                    Sol r = sliceSolve(p, line, e, cancel, miss);
                    evals++;
                    if (r != null) return r;
                    if (miss[0] < missAt) {
                        missAt = miss[0];
                        theta = th;
                        improved = true;
                        break;
                    }
                }
            }
        }
        lastDirectDebug += String.format(Locale.ROOT, " descent=%.4e@%.4f", missAt, theta);
        return null;
    }

    private static final int SLICE_GRID = 3;
    private static final int SLICE_GRID_FINE = 9;
    private static final double SLICE_GRID_FINE_TRIGGER = 3.0e-3;
    private static final double SLICE_GRID_INSET = 1.0e-9;
    static double ENTRY_DESCENT_TRIGGER = 3.0e-3;
    private static final int ENTRY_DESCENT_MAX_EVALS = 80;

    private static Sol lastEntrySol;

    private static double sliceEntryEval(ColdProblem p, KeyLine line, MomentumEval e,
                                         double sx, double sz, AtomicBoolean cancel) {
        profEntryEvals++;
        lastEntrySol = null;
        int lp = p.lastPressSeg;
        JumpPhysicsInputs sc = e.spec.asScenario();
        double dxLp = e.path.posX[lp] - sc.startPos.x;
        double dzLp = e.path.posZ[lp] - sc.startPos.z;
        long tb = System.nanoTime();
        JumpSpec slice = buildSliceSpec(p, line, e.theta, sx + dxLp, sz + dzLp,
                sx + dxLp, sx + dxLp, sz + dzLp, sz + dzLp,
                e.path.velX[lp], e.path.velZ[lp]);
        long ts = System.nanoTime();
        profNsBuildSpec += ts - tb;
        ProbeResult pr = sliceProbeSolve(p, slice, cancel);
        profNsProbeSolve += System.nanoTime() - ts;
        if (pr == null) return Double.POSITIVE_INFINITY;
        if (pr.feasible) {
            lastEntrySol = stitch(p, line, e, pr.yaws, sx, sz);
            if (lastEntrySol != null) profFeasEntries++;
            return lastEntrySol != null ? 0.0 : 1.0e-9;
        }
        return pr.viol;
    }

    private static Sol sliceSolve(ColdProblem p, KeyLine line, MomentumEval e, AtomicBoolean cancel,
                                  double[] missOut) {
        missOut[0] = Double.POSITIVE_INFINITY;
        int lp = p.lastPressSeg;
        JumpPhysicsInputs sc = e.spec.asScenario();
        double dxLp = e.path.posX[lp] - sc.startPos.x;
        double dzLp = e.path.posZ[lp] - sc.startPos.z;

        double gxLo = e.txLo + SLICE_GRID_INSET;
        double gxHi = Math.max(gxLo, e.txHi - SLICE_GRID_INSET);
        double gzLo = e.tzLo + SLICE_GRID_INSET;
        double gzHi = Math.max(gzLo, e.tzHi - SLICE_GRID_INSET);
        double bestMiss = Double.POSITIVE_INFINITY;
        double bestSx = 0.5 * (gxLo + gxHi);
        double bestSz = 0.5 * (gzLo + gzHi);
        for (int pass = 0; pass < 2; pass++) {
            int grid = pass == 0 ? SLICE_GRID : SLICE_GRID_FINE;
            if (pass == 1 && bestMiss > SLICE_GRID_FINE_TRIGGER) break;
            for (int gi = 0; gi < grid; gi++) {
                for (int gj = 0; gj < grid; gj++) {
                    if (pass == 1 && gi % 4 == 0 && gj % 4 == 0) continue;
                    double sx = gxLo + (gxHi - gxLo) * gi / (grid - 1.0);
                    double sz = gzLo + (gzHi - gzLo) * gj / (grid - 1.0);
                    double v = sliceEntryEval(p, line, e, sx, sz, cancel);
                    if (v <= 0.0) {
                        Sol s = lastEntrySol;
                        if (s != null) return s;
                    }
                    if (v < bestMiss) {
                        bestMiss = v;
                        bestSx = sx;
                        bestSz = sz;
                    }
                }
            }
        }
        if (bestMiss < ENTRY_DESCENT_TRIGGER) {
            double descentEntryMiss = bestMiss;
            double stepX = Math.max((gxHi - gxLo) / 16.0, 1.0e-6);
            double stepZ = Math.max((gzHi - gzLo) / 16.0, 1.0e-6);
            int evals = 0;
            while (evals < ENTRY_DESCENT_MAX_EVALS && (stepX > 5.0e-7 || stepZ > 5.0e-7)) {
                boolean improved = false;
                double[][] moves = {{stepX, 0}, {-stepX, 0}, {0, stepZ}, {0, -stepZ}};
                for (double[] mv : moves) {
                    double sx = clamp(bestSx + mv[0], gxLo, gxHi);
                    double sz = clamp(bestSz + mv[1], gzLo, gzHi);
                    if (sx == bestSx && sz == bestSz) continue;
                    double v = sliceEntryEval(p, line, e, sx, sz, cancel);
                    evals++;
                    if (v <= 0.0) {
                        Sol s = lastEntrySol;
                        if (s != null) {
                            profDescentDecisive++;
                            profDescentDecisiveMaxMiss = Math.max(profDescentDecisiveMaxMiss, descentEntryMiss);
                            return s;
                        }
                    }
                    if (v < bestMiss) {
                        bestMiss = v;
                        bestSx = sx;
                        bestSz = sz;
                        improved = true;
                        break;
                    }
                }
                if (!improved) {
                    stepX *= 0.25;
                    stepZ *= 0.25;
                }
            }
        }
        missOut[0] = bestMiss;

        FreeStartSolve.Result r;
        double refX = clamp(sc.startPos.x, e.txLo, e.txHi) + dxLp;
        double refZ = clamp(sc.startPos.z, e.tzLo, e.tzHi) + dzLp;
        JumpSpec slice = buildSliceSpec(p, line, e.theta, refX, refZ,
                e.txLo + dxLp, e.txHi + dxLp, e.tzLo + dzLp, e.tzHi + dzLp,
                e.path.velX[lp], e.path.velZ[lp]);
        try {
            r = FreeStartSolve.solveJoint(p.model, slice, 0.0, cancel);
            if (r == null || !r.feasible) r = FreeStartSolve.solve(p.model, slice, 0.0, cancel);
        } catch (RuntimeException ex) {
            lastDirectDebug += " slice=ex";
            return null;
        }
        if (r == null || !r.feasible) {
            lastDirectDebug += String.format(Locale.ROOT, " slice=miss grid=%.4e [%s]",
                    bestMiss, FreeStartSolve.lastJointDebug);
            return null;
        }
        return stitch(p, line, e, r.yaws, r.startX - dxLp, r.startZ - dzLp);
    }

    private static Sol stitch(ColdProblem p, KeyLine line, MomentumEval e, double[] sliceYaws,
                              double startX, double startZ) {
        int lp = p.lastPressSeg;
        int n = e.spec.asScenario().numTicks;
        double[] yaws = new double[n];
        java.util.Arrays.fill(yaws, e.theta);
        for (int i = 0; i < n - lp; i++) {
            yaws[lp + i] = sliceYaws[i];
        }
        double v = FreeStartSolve.violationAt(p.model, e.spec, yaws, startX, startZ);
        if (v <= 0.0) return new Sol(yaws, startX, startZ);
        double[] rs = FreeStartSolve.recoverStart(p.model, e.spec, yaws);
        if (rs != null) {
            double v2 = FreeStartSolve.violationAt(p.model, e.spec, yaws, rs[0], rs[1]);
            if (v2 <= 0.0) return new Sol(yaws, rs[0], rs[1]);
            v = Math.min(v, v2);
        }
        lastDirectDebug += String.format(Locale.ROOT, " stitch=%.4e@%s", v,
                worstConstraint(p, e.spec, yaws, startX, startZ));
        return null;
    }

    private static final int[] HELD_TAILS = {KeyLine.W, KeyLine.WA, KeyLine.WD};

    private static int[] orderedHeldTails(int first) {
        int[] out = new int[HELD_TAILS.length];
        out[0] = first;
        int idx = 1;
        for (int t : HELD_TAILS) {
            if (t != first) out[idx++] = t;
        }
        return out;
    }

    static double[] heldFacingProbe(ColdProblem p, int[] moveKey, boolean[] hold) {
        double cx = 0.5 * (p.rectXLo + p.rectXHi);
        double cz = 0.5 * (p.rectZLo + p.rectZHi);
        double best = Double.POSITIVE_INFINITY;
        double bestTh = Double.NaN;
        int bestTail = KeyLine.WA;
        for (int tail : HELD_TAILS) {
            KeyLine line = new KeyLine(p, moveKey, hold, tail);
            JumpSpec spec = LineSpec.build(line, 0.0, cx, cz);
            if (spec == null) continue;
            for (double th = -180.0; th < 180.0; th += 2.0) {
                double v = heldViolationAt(p, spec, th);
                if (v < best) {
                    best = v;
                    bestTh = th;
                    bestTail = tail;
                }
            }
        }
        return new double[] {best, bestTh, bestTail};
    }

    private static Sol heldChainScan(ColdProblem p, Candidate c, AtomicBoolean cancel) {
        if (!p.singleHeld) return null;
        double cx = 0.5 * (p.rectXLo + p.rectXHi);
        double cz = 0.5 * (p.rectZLo + p.rectZHi);
        for (int tail : orderedHeldTails(c.tailCombo)) {
            KeyLine line = new KeyLine(p, c.moveKey, c.hold, tail);
            JumpSpec spec = LineSpec.build(line, 0.0, cx, cz);
            if (spec == null) continue;
            int n = spec.asScenario().numTicks;
            double bestTheta = Double.NaN;
            double bestViol = Double.POSITIVE_INFINITY;
            for (double th = -180.0; th < 180.0; th += 0.25) {
                double v = heldViolationAt(p, spec, th);
                if (v < bestViol) {
                    bestViol = v;
                    bestTheta = th;
                }
                if (cancel != null && cancel.get()) return null;
            }
            double[] stepsDeg = {0.05, 0.01, 0.002, 0.0005};
            for (double step : stepsDeg) {
                boolean improved = true;
                while (improved) {
                    improved = false;
                    for (int dir = -1; dir <= 1; dir += 2) {
                        double th = bestTheta + dir * step;
                        double v = heldViolationAt(p, spec, th);
                        if (v < bestViol) {
                            bestViol = v;
                            bestTheta = th;
                            improved = true;
                        }
                    }
                }
            }
            if (bestViol > 0.0) continue;
            double[] yaws = new double[n];
            java.util.Arrays.fill(yaws, bestTheta);
            double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
            if (rs == null) continue;
            double v = FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1]);
            if (v > 0.0) continue;
            c.tailCombo = tail;
            return new Sol(yaws, rs[0], rs[1]);
        }
        return null;
    }

    private static double heldViolationAt(ColdProblem p, JumpSpec spec, double theta) {
        int n = spec.asScenario().numTicks;
        double[] yaws = new double[n];
        java.util.Arrays.fill(yaws, theta);
        double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
        if (rs == null) {
            StartBox box = spec.asScenario().startBox;
            rs = new double[] {box == null ? spec.asScenario().startPos.x : box.px,
                    box == null ? spec.asScenario().startPos.z : box.pz};
        }
        return FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1]);
    }

    private static final long ENGINE_CERTIFY_BUDGET_MS = 3000L;
    private static final long ENGINE_ESCALATE_BUDGET_MS = 20_000L;
    private static final int ENGINE_ESCALATE_SECONDS = 15;

    private static Sol engineCertify(ColdProblem p, KeyLine line, double theta, double sx, double sz,
                                     boolean escalate, AtomicBoolean cancel) {
        InputData inputs = new InputData();
        inputs.getRows().clear();
        inputs.getRows().addAll(line.toRows());
        AngleSolverState st = new AngleSolverState();
        SaveIO.applyAngleSolverTo(p.solverOnly, st);
        st.clearResult();
        if (escalate) {
            st.setEffort(AngleSolverState.Effort.THOROUGH);
            st.setOptimizeSeconds(ENGINE_ESCALATE_SECONDS);
        } else {
            st.setEffort(AngleSolverState.Effort.FAST);
            st.setStopOnFeasible(true);
        }
        BoxController boxes = LineSpec.buildBoxes(line, theta, sx, sz);
        AngleSolverEngine engine = new AngleSolverEngine(st, boxes, inputs, t -> {
        }, p.model);
        final Vec3dCore[] moved = new Vec3dCore[1];
        engine.setOnStartMoved(pos -> moved[0] = pos);
        engine.solve();
        long deadline = System.currentTimeMillis()
                + (escalate ? ENGINE_ESCALATE_BUDGET_MS : ENGINE_CERTIFY_BUDGET_MS);
        while (engine.isSolving() && System.currentTimeMillis() < deadline
                && (cancel == null || !cancel.get())) {
            engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (engine.isSolving()) engine.cancel();
        engine.poll();
        SolveResult res = st.getResult();
        if (res == null || !res.isSuccess()) {
            lastDirectDebug += " engine=miss";
            return null;
        }
        engine.apply();
        double[] yaws = new double[p.numTicks];
        java.util.Arrays.fill(yaws, theta);
        for (SolveResult.YawEntry ye : res.getYaws()) {
            int k = ye.tick - 1;
            if (k >= 0 && k < p.numTicks) yaws[k] = ye.yaw;
        }
        double fx = moved[0] != null ? moved[0].x : sx;
        double fz = moved[0] != null ? moved[0].z : sz;
        JumpSpec spec = LineSpec.build(line, theta, sx, sz);
        if (spec == null) return null;
        double v = FreeStartSolve.violationAt(p.model, spec, yaws, fx, fz);
        if (v <= 0.0) return new Sol(yaws, fx, fz);
        double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
        if (rs != null) {
            double v2 = FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1]);
            if (v2 <= 0.0) return new Sol(yaws, rs[0], rs[1]);
            v = Math.min(v, v2);
        }
        lastDirectDebug += String.format(Locale.ROOT, " engine=%.4e", v);
        return null;
    }

    private static String worstConstraint(ColdProblem p, JumpSpec spec, double[] yaws, double sx, double sz) {
        JumpPhysicsInputs at = spec.asScenario().copy();
        at.startPos = new Vec3dCore(sx, at.startPos.y, sz);
        double[] gf = at.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = p.model.forward(at, gf);
        String worstName = "?";
        double worst = 0.0;
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            if (s > worst) {
                worst = s;
                worstName = c.name;
            }
        }
        return worstName;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
