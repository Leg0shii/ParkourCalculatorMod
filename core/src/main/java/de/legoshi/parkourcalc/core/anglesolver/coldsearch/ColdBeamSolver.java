package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ColdBeamSolver {

    public static final int[] DEFAULT_COASTS = {KeyLine.NONE, KeyLine.S, KeyLine.A, KeyLine.D, KeyLine.SA,
            KeyLine.SD, KeyLine.W, KeyLine.WA, KeyLine.WD};
    public static final int[] DEFAULT_GLIDES = {KeyLine.A, KeyLine.D, KeyLine.SA, KeyLine.SD, KeyLine.WA,
            KeyLine.WD, KeyLine.S};
    public static final int[] DEFAULT_PRESSES = {KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.S, KeyLine.SA,
            KeyLine.SD};
    public static final int[] DEFAULT_ENGAGES = {KeyLine.W, KeyLine.WA, KeyLine.WD};
    public static final int[] DEFAULT_BRAKES = {};
    public static final int DEFAULT_BUCKET_BUDGET = 30;

    public enum SprintEngage {
        ALWAYS,
        SWEEP,
        AT_CYCLE
    }

    public static final class CycleConfig {
        public boolean fixed;
        public int[] coasts;
        public int[] glides;
        public int[] presses;
        public int[] engages;
        public int[] brakes;
        public int glideLo = 1;
        public int glideHi;

        public CycleConfig copy() {
            CycleConfig c = new CycleConfig();
            c.fixed = fixed;
            c.coasts = coasts == null ? null : coasts.clone();
            c.glides = glides == null ? null : glides.clone();
            c.presses = presses == null ? null : presses.clone();
            c.engages = engages == null ? null : engages.clone();
            c.brakes = brakes == null ? null : brakes.clone();
            c.glideLo = glideLo;
            c.glideHi = glideHi;
            return c;
        }
    }

    public static final class Config {
        public int[] coasts = DEFAULT_COASTS.clone();
        public int[] glides = DEFAULT_GLIDES.clone();
        public int[] presses = DEFAULT_PRESSES.clone();
        public int[] engages = DEFAULT_ENGAGES.clone();
        public int[] brakes = DEFAULT_BRAKES.clone();
        public int glideMax = 2;
        public List<CycleConfig> cycles = new ArrayList<CycleConfig>();
        public SprintEngage sprintEngage = SprintEngage.ALWAYS;
        public int sprintEngageCycle = 0;
        public int beamCap = 4000;
        public int certifyCap = 4000;
        public double probeGate = 999.0;
        public double probeStep = 0.5;
        public double facingStep = 1.0;
        public int bucketBudget = DEFAULT_BUCKET_BUDGET;
        public int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        public long budgetMs = 600_000L;

        CycleConfig cycle(int idx) {
            if (cycles != null && idx < cycles.size() && cycles.get(idx) != null) return cycles.get(idx);
            return null;
        }
    }

    public interface ProgressSink {
        void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut);

        void onBuilt(int candidates, long tailCut);

        void onCertify(int done, int total, int certified, long elapsedMs);

        void onSolved(String sig, int idx, int certified, long elapsedMs);
    }

    public static final ProgressSink NO_PROGRESS = new ProgressSink() {
        @Override
        public void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut) {
        }

        @Override
        public void onBuilt(int candidates, long tailCut) {
        }

        @Override
        public void onCertify(int done, int total, int certified, long elapsedMs) {
        }

        @Override
        public void onSolved(String sig, int idx, int certified, long elapsedMs) {
        }
    };

    private ColdBeamSolver() {
    }

    public static ColdResult solve(SaveFile file, Config cfg, ProgressSink progress, AtomicBoolean cancel) {
        if (progress == null) progress = NO_PROGRESS;
        if (cancel == null) cancel = new AtomicBoolean(false);
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config solverCfg = new ColdSearch.Config();

        long deadline = System.nanoTime() + Math.max(1L, cfg.budgetMs) * 1_000_000L;

        int[] presses = p.pressSegTicks;
        int nSegs = p.lastPressSeg + 1;
        int[][] cyc = new int[presses.length][2];
        int prev = -1;
        for (int i = 0; i < presses.length; i++) {
            cyc[i][0] = prev + 1;
            cyc[i][1] = presses[i];
            prev = presses[i];
        }

        int fsteps = Math.max(1, (int) Math.round(360.0 / cfg.facingStep));
        ColdSearch.Sweep[] fscan = buildScan(p, solverCfg, cfg.facingStep, fsteps);

        int[] engagePts = engagePoints(cfg, cyc, nSegs);
        List<int[][]> beam = new ArrayList<int[][]>();
        for (int ep : engagePts) {
            beam.add(new int[][] {new int[nSegs], new int[nSegs], new int[] {ep}});
        }

        long extTotal = 0;
        long tailCut = 0;
        for (int ci = 0; ci < cyc.length; ci++) {
            int a = cyc[ci][0];
            int b = cyc[ci][1];
            int L = b - a + 1;
            int endSeg = b + 1;
            boolean lastCycle = ci == cyc.length - 1;
            List<int[]> fams = cycleFamilies(L, cfg, cfg.cycle(ci));
            List<int[][]> next = new ArrayList<int[][]>();
            for (int[][] partial : beam) {
                for (int[] pat : fams) {
                    int[] mk = partial[0].clone();
                    int[] hd = partial[1].clone();
                    int engageStart = partial[2][0];
                    for (int i = 0; i < L; i++) {
                        mk[a + i] = pat[i];
                        hd[a + i] = (a + i >= engageStart && KeyLine.canRun(pat[i])) ? 1 : 0;
                    }
                    extTotal++;
                    boolean[] hb = new boolean[nSegs];
                    for (int i = 0; i < nSegs; i++) hb[i] = hd[i] == 1;
                    boolean ok = lastCycle
                            ? feasWithTail(fscan, mk, hb, endSeg, solverCfg.rectSlack)
                            : feasWidth(fscan, mk, hb, endSeg) >= -solverCfg.rectSlack;
                    if (!ok && lastCycle) tailCut++;
                    if (ok) next.add(new int[][] {mk, hd, partial[2]});
                    if ((extTotal & 0x3FFF) == 0 && (cancel.get() || System.nanoTime() > deadline)) break;
                }
                if (cancel.get() || System.nanoTime() > deadline) break;
            }
            if (next.size() > cfg.beamCap) {
                final int es = endSeg;
                final HashMap<int[][], Double> keyCache = new HashMap<int[][], Double>();
                for (int[][] q : next) keyCache.put(q, maxWidth(fscan, q, es));
                Collections.sort(next, new Comparator<int[][]>() {
                    @Override
                    public int compare(int[][] x, int[][] y) {
                        return Double.compare(keyCache.get(x), keyCache.get(y));
                    }
                });
                next = new ArrayList<int[][]>(next.subList(0, cfg.beamCap));
            }
            beam = next;
            progress.onBuildCycle(ci, cyc.length, extTotal, beam.size(), tailCut);
            if (cancel.get() || System.nanoTime() > deadline) break;
        }

        List<String> sigs = new ArrayList<String>();
        for (int[][] partial : beam) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nSegs; i++) sb.append(partial[0][i]).append(partial[1][i] == 1 ? '+' : '.');
            sigs.add(sb.toString());
        }
        progress.onBuilt(sigs.size(), tailCut);
        if (cancel.get() || sigs.isEmpty()) return null;

        String solvedSig = streamCertify(p, solverCfg, cfg, sigs, progress, cancel, deadline);
        if (solvedSig == null) return null;
        return ColdSearch.certifyLine(file, solvedSig, solverCfg);
    }

    private static String streamCertify(ColdProblem p, ColdSearch.Config solverCfg, Config cfg,
                                        List<String> sigs, final ProgressSink progress,
                                        final AtomicBoolean cancel, final long deadline) {
        final String[] sigArr = sigs.toArray(new String[0]);
        final boolean certifyAll = cfg.probeGate >= 100.0;
        final int certifyCap = cfg.certifyCap;
        final double probeGate = cfg.probeGate;
        final double probeStep = cfg.probeStep;
        final ColdProblem pp = p;
        final ColdSearch.Config scfg = solverCfg;
        final AtomicInteger nextIdx = new AtomicInteger(0);
        final AtomicInteger certifiedCt = new AtomicInteger(0);
        final AtomicInteger probedCt = new AtomicInteger(0);
        final AtomicReference<String> solvedSig = new AtomicReference<String>(null);
        final long streamStart = System.nanoTime();
        final int nThreads = Math.max(1, cfg.threads);
        final int oldBucketBudget = ColdSearch.BUCKET_SLICE_BUDGET;
        try {
            ColdSearch.BUCKET_SLICE_BUDGET = cfg.bucketBudget;
            Thread[] workers = new Thread[nThreads];
            for (int t = 0; t < nThreads; t++) {
                workers[t] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ColdSearch.Sweep[] tcscan = buildScan(pp, scfg, 0.5,
                                    (int) Math.round(360.0 / 0.5));
                            ColdSearch.Sweep[] tpscan = certifyAll ? null
                                    : buildScan(pp, scfg, probeStep, (int) Math.round(360.0 / probeStep));
                            while (solvedSig.get() == null) {
                                int i = nextIdx.getAndIncrement();
                                if (i >= sigArr.length) break;
                                if (cancel.get() || System.nanoTime() > deadline) {
                                    cancel.set(true);
                                    break;
                                }
                                String sig = sigArr[i];
                                boolean doCert = certifyAll;
                                if (!certifyAll) {
                                    double v = ColdSearch.probeViolOf(pp, tpscan, sig, scfg, cancel);
                                    doCert = Double.isFinite(v) && v <= probeGate;
                                }
                                int done = probedCt.incrementAndGet();
                                if (doCert && certifiedCt.get() < certifyCap) {
                                    certifiedCt.incrementAndGet();
                                    long[] full = ColdSearch.benchSig(pp, tcscan, sig, scfg, true, false, cancel);
                                    if (full[2] == 1 && solvedSig.compareAndSet(null, sig)) {
                                        progress.onSolved(sig, i, certifiedCt.get(),
                                                (System.nanoTime() - streamStart) / 1_000_000L);
                                        cancel.set(true);
                                        return;
                                    }
                                }
                                if (done % 200 == 0) {
                                    progress.onCertify(done, sigArr.length, certifiedCt.get(),
                                            (System.nanoTime() - streamStart) / 1_000_000L);
                                }
                            }
                        } catch (Throwable ex) {
                            cancel.set(true);
                        }
                    }
                });
            }
            for (Thread w : workers) w.start();
            for (Thread w : workers) {
                try {
                    w.join();
                } catch (InterruptedException ie) {
                    cancel.set(true);
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            ColdSearch.BUCKET_SLICE_BUDGET = oldBucketBudget;
        }
        progress.onCertify(probedCt.get(), sigArr.length, certifiedCt.get(),
                (System.nanoTime() - streamStart) / 1_000_000L);
        return solvedSig.get();
    }

    private static int[] engagePoints(Config cfg, int[][] cyc, int nSegs) {
        if (cfg.sprintEngage == SprintEngage.SWEEP) {
            int[] pts = new int[cyc.length + 1];
            for (int i = 0; i < cyc.length; i++) pts[i] = cyc[i][0];
            pts[cyc.length] = nSegs;
            return pts;
        }
        if (cfg.sprintEngage == SprintEngage.AT_CYCLE) {
            int c = Math.max(0, Math.min(cyc.length - 1, cfg.sprintEngageCycle));
            return new int[] {cyc[c][0]};
        }
        return new int[] {0};
    }

    private static List<int[]> cycleFamilies(int L, Config cfg, CycleConfig cc) {
        int[] coasts = resolve(cc == null ? null : cc.coasts, cfg.coasts);
        int[] glides = resolve(cc == null ? null : cc.glides, cfg.glides);
        int[] presses = resolve(cc == null ? null : cc.presses, cfg.presses);
        int[] engages = resolve(cc == null ? null : cc.engages, cfg.engages);
        int[] brakes = resolve(cc == null ? null : cc.brakes, cfg.brakes);
        int glideLo = Math.max(1, cc == null ? 1 : cc.glideLo);
        int glideHi = cc != null && cc.glideHi > 0 ? cc.glideHi : cfg.glideMax;

        List<int[]> pats = new ArrayList<int[]>();
        if (cc != null && cc.fixed) {
            int j = Math.max(1, Math.min(glideLo, L - 1));
            int c = coasts.length > 0 ? coasts[0] : KeyLine.NONE;
            int g = glides.length > 0 ? glides[0] : KeyLine.W;
            int pr = presses.length > 0 ? presses[0] : KeyLine.W;
            int[] pat = new int[L];
            for (int i = 0; i < L; i++) {
                pat[i] = i < L - 1 - j ? c : (i < L - 1 ? g : pr);
            }
            pats.add(pat);
            return pats;
        }

        for (int e : engages) {
            int[] pat = new int[L];
            java.util.Arrays.fill(pat, e);
            pats.add(pat);
        }
        if (glideLo <= 1) {
            for (int c : coasts) {
                for (int k = 1; k <= Math.min(3, L); k++) {
                    for (int pr : presses) {
                        int[] pat = new int[L];
                        for (int i = 0; i < L; i++) pat[i] = i < L - k ? c : pr;
                        pats.add(pat);
                    }
                }
            }
        }
        for (int c : coasts) {
            for (int g : glides) {
                for (int j = Math.max(1, glideLo); j <= Math.min(glideHi, L - 1); j++) {
                    for (int pr : presses) {
                        int[] pat = new int[L];
                        for (int i = 0; i < L; i++) {
                            pat[i] = i < L - 1 - j ? c : (i < L - 1 ? g : pr);
                        }
                        pats.add(pat);
                    }
                }
            }
        }
        for (int c : coasts) {
            for (int g : glides) {
                for (int j = Math.max(1, glideLo); j <= Math.min(glideHi, L - 2); j++) {
                    for (int br : brakes) {
                        for (int pr : presses) {
                            int[] pat = new int[L];
                            for (int i = 0; i < L; i++) {
                                if (i < L - 2 - j) pat[i] = c;
                                else if (i < L - 2) pat[i] = g;
                                else if (i == L - 2) pat[i] = br;
                                else pat[i] = pr;
                            }
                            pats.add(pat);
                        }
                    }
                }
            }
        }
        if (L >= 2) {
            for (int c : coasts) {
                for (int br : brakes) {
                    for (int pr : presses) {
                        int[] pat = new int[L];
                        for (int i = 0; i < L; i++) {
                            pat[i] = i < L - 2 ? c : (i == L - 2 ? br : pr);
                        }
                        pats.add(pat);
                    }
                }
            }
        }
        return pats;
    }

    private static int[] resolve(int[] override, int[] global) {
        if (override != null && override.length > 0) return override;
        return global;
    }

    private static boolean feasWithTail(ColdSearch.Sweep[] fscan, int[] mk, boolean[] hd, int endSeg, double slack) {
        for (ColdSearch.Sweep s : fscan) {
            double[] tr = s.traceLineTo(mk, hd, endSeg);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            if (w >= -slack && s.lineTailReachable(mk, hd)) return true;
        }
        return false;
    }

    private static double feasWidth(ColdSearch.Sweep[] fscan, int[] mk, boolean[] hd, int endSeg) {
        double best = Double.NEGATIVE_INFINITY;
        for (ColdSearch.Sweep s : fscan) {
            double[] tr = s.traceLineTo(mk, hd, endSeg);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            if (w > best) best = w;
            if (best >= 0.0) break;
        }
        return best;
    }

    private static double maxWidth(ColdSearch.Sweep[] fscan, int[][] partial, int endSeg) {
        boolean[] hb = new boolean[partial[1].length];
        for (int i = 0; i < hb.length; i++) hb[i] = partial[1][i] == 1;
        double best = Double.NEGATIVE_INFINITY;
        for (ColdSearch.Sweep s : fscan) {
            double[] tr = s.traceLineTo(partial[0], hb, endSeg);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            if (w > best) best = w;
        }
        return best;
    }

    private static ColdSearch.Sweep[] buildScan(ColdProblem p, ColdSearch.Config cfg, double step, int steps) {
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
        for (int i = 0; i < steps; i++) scan[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * step, 0, null);
        return scan;
    }
}
