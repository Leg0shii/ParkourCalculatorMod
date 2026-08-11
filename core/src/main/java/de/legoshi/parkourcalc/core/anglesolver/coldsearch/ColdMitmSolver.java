package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Meet-in-the-middle cold solver. Splits the momentum at a seam past the run-up, forward-collects the front
 * halves, enumerates back momentum templates, and joins them with the friction-decomposition filter
 * (displacement at a back wall = d_seam + C_w * v_seam + K_w(t), C_w combo-independent) under a total
 * change budget counted across the seam; survivors are margin-ordered and certified byte-exact via
 * {@link ColdSearch#certifySig}, first solve wins. Byte-exact via the certify judge: a false join match
 * only fails certify, so this never returns a wrong solution, only fails to find one (falls back to null).
 * Wins where the direct incremental build is dominated by the b^d run-up enumeration (L2-L3).
 *
 * The solve is {@link #buildPlan} (front collect, back templates, C_w join, margin sort) then
 * {@link #certifyPlan} (parallel first-solve certify). The MITM diagnostic screen drives these same two
 * methods so it never forks the join; {@link MitmDiag} carries the screen's known-pair validation and
 * benchmark timing without changing the diag-null (production) path.
 */
public final class ColdMitmSolver {

    private ColdMitmSolver() {
    }

    public static final class Params {
        public int seam = -1;
        public int frontCap = 2;
        public int backCap = 2;
        public int level = -1;
        public int[] alphabet;
        public int bucket = 30;
        public long nodeCap = 300_000_000L;
    }

    static final class MitmPlan {
        int m;
        int last;
        int level;
        int nBack;
        double margThresh;
        List<ArcSweep.ArcState> F;
        int[][] fullMkByT;
        boolean[][] fullHdByT;
        int[] bChanges;
        ArcSweep.BackTransfer[][] btByT;
        List<ColdProblem.Wall> backWalls;
        long[][] surv;
        ArcSweep sweep;
        long collectSeamMs;
        long transferMs;
        long joinMs;
        long totalPairs;
        long budgetPairs;
        long marginNs;
        long workerNs;
        final long[] funnel = new long[4];
    }

    static final class MitmDiag {
        boolean known;
        int[] knownMk;
        boolean[] knownHd;
        boolean bench;
        int knownT = -1;
        boolean knownSurv;
    }

    public static ColdResult solve(ColdProblem p, ColdSearch.Config cfg, Params params, AtomicBoolean cancel) {
        if (cancel == null) cancel = new AtomicBoolean(false);
        MitmPlan plan = buildPlan(p, cfg, params, cancel, null);
        if (plan == null || cancel.get() || plan.surv.length == 0) return null;
        return certifyPlan(p, cfg, params, plan, cancel, null);
    }

    static MitmPlan buildPlan(ColdProblem p, ColdSearch.Config cfg, Params params, AtomicBoolean cancel,
                              final MitmDiag diag) {
        if (cancel == null) cancel = new AtomicBoolean(false);
        final int last = p.lastPressSeg;
        if (last < 2 || params.alphabet == null || params.alphabet.length == 0) return null;
        final int m = params.seam >= 0 ? params.seam
                : Math.max(p.pressSegTicks[0] + 1, (p.pressSegTicks[0] + last) / 2);
        if (m <= 0 || m >= last) return null;
        final int level = params.level >= 0 ? params.level : params.frontCap + params.backCap + 1;
        final double margThresh = -(cfg.rectSlack + 2.0e-3);
        final int[] alphabet = params.alphabet;

        int nSegs = p.pressSegTicks.length;
        int[][] segAlpha = new int[nSegs][];
        int[] segMax = new int[nSegs];
        for (int i = 0; i < nSegs; i++) {
            segAlpha[i] = alphabet.clone();
            segMax[i] = 20;
        }

        long tSeam = System.nanoTime();
        final ArcSweep sweep = new ArcSweep(p, cfg, 0, new ColdSearch.SigCollector(cfg));
        sweep.setSegments(segAlpha, segMax);
        final List<ArcSweep.ArcState> F = sweep.collectSeam(m, params.frontCap, params.nodeCap);
        long collectSeamMs = (System.nanoTime() - tSeam) / 1_000_000L;
        if (F.isEmpty() || cancel.get()) return null;

        final int backLen = last - m;
        List<int[]> bMkList = new ArrayList<int[]>();
        List<boolean[]> bHdList = new ArrayList<boolean[]>();
        enumBack(alphabet, backLen, params.backCap, bMkList, bHdList);
        final int nBack = bMkList.size();
        if (nBack == 0) return null;

        long tTransfer = System.nanoTime();
        final int[][] fullMkByT = new int[nBack][];
        final boolean[][] fullHdByT = new boolean[nBack][];
        final int[] bChanges = new int[nBack];
        final ArcSweep.BackTransfer[][] btByT = new ArcSweep.BackTransfer[nBack][2];
        for (int bi = 0; bi < nBack; bi++) {
            int[] fm = new int[last];
            boolean[] fh = new boolean[last];
            int[] bk = bMkList.get(bi);
            boolean[] bh = bHdList.get(bi);
            int chg = 0;
            for (int j = 0; j < backLen; j++) {
                fm[m + j] = bk[j];
                fh[m + j] = bh[j];
                if (j > 0 && (bk[j] != bk[j - 1] || bh[j] != bh[j - 1])) chg++;
            }
            bChanges[bi] = chg;
            fullMkByT[bi] = fm;
            fullHdByT[bi] = fh;
            btByT[bi][0] = sweep.backTransfer(m, fm, fh, false);
            btByT[bi][1] = sweep.backTransfer(m, fm, fh, true);
        }
        long transferMs = (System.nanoTime() - tTransfer) / 1_000_000L;

        final List<ColdProblem.Wall> backWalls = sweep.backMomentumWalls(m);

        final int[] knownFrontKey = diag != null && diag.known ? Arrays.copyOf(diag.knownMk, m) : null;
        final boolean[] knownFrontHold = diag != null && diag.known ? Arrays.copyOf(diag.knownHd, m) : null;
        int knownT = -1;
        if (diag != null && diag.known) {
            for (int bi = 0; bi < nBack && knownT < 0; bi++) {
                boolean match = true;
                for (int j = 0; j < backLen; j++) {
                    if (fullMkByT[bi][m + j] != diag.knownMk[m + j] || fullHdByT[bi][m + j] != diag.knownHd[m + j]) {
                        match = false;
                        break;
                    }
                }
                if (match) knownT = bi;
            }
            diag.knownT = knownT;
        }
        final int knownTf = knownT;
        final boolean diagKnown = diag != null && diag.known;
        final boolean diagBench = diag != null && diag.bench;

        final int fm2 = m;
        final int levelF = level;
        final AtomicInteger fCursor = new AtomicInteger(0);
        final List<long[]> survPacked = Collections.synchronizedList(new ArrayList<long[]>());
        final AtomicBoolean cancelF = cancel;
        final AtomicBoolean knownSurv = new AtomicBoolean(false);
        final long[] funnelSum = new long[4];
        final AtomicLong aTotal = new AtomicLong(0);
        final AtomicLong aBudget = new AtomicLong(0);
        final AtomicLong aMarginNs = new AtomicLong(0);
        final AtomicLong aWorkerNs = new AtomicLong(0);
        final int nThreads = Math.max(2, cfg == null ? 4 : Runtime.getRuntime().availableProcessors());
        long tJoin = System.nanoTime();
        Thread[] jw = new Thread[nThreads];
        for (int w = 0; w < nThreads; w++) {
            jw[w] = new Thread(new Runnable() {
                @Override
                public void run() {
                    long wStart = System.nanoTime();
                    long total = 0;
                    long budget = 0;
                    long marginNs = 0;
                    long[] funnel = new long[4];
                    List<long[]> mine = new ArrayList<long[]>();
                    int chunk = 64;
                    while (!cancelF.get()) {
                        int start = fCursor.getAndAdd(chunk);
                        if (start >= F.size()) break;
                        int end = Math.min(F.size(), start + chunk);
                        for (int fi = start; fi < end; fi++) {
                            ArcSweep.ArcState f = F.get(fi);
                            int sp = f.sprintPrev ? 1 : 0;
                            int fCombo = fm2 > 0 ? f.prefixKey[fm2 - 1] : -1;
                            boolean fHold = fm2 > 0 && f.prefixHold[fm2 - 1];
                            boolean fMatch = diagKnown && prefixEq(f.prefixKey, knownFrontKey)
                                    && prefixEq(f.prefixHold, knownFrontHold);
                            for (int bi = 0; bi < nBack; bi++) {
                                total++;
                                int seamChg = (fm2 > 0 && (fullMkByT[bi][fm2] != fCombo
                                        || fullHdByT[bi][fm2] != fHold)) ? 1 : 0;
                                if (f.changes + bChanges[bi] + seamChg > levelF) continue;
                                budget++;
                                long mt0 = diagBench ? System.nanoTime() : 0;
                                double mg = sweep.mitmTailMargin(f, btByT[bi][sp], backWalls, funnel);
                                if (diagBench) marginNs += System.nanoTime() - mt0;
                                if (mg >= margThresh) {
                                    long key = ((long) fi << 32) | (bi & 0xFFFFFFFFL);
                                    mine.add(new long[] {key, Double.doubleToRawLongBits(mg)});
                                    if (fMatch && bi == knownTf) knownSurv.set(true);
                                }
                            }
                        }
                    }
                    survPacked.addAll(mine);
                    aTotal.addAndGet(total);
                    aBudget.addAndGet(budget);
                    aMarginNs.addAndGet(marginNs);
                    aWorkerNs.addAndGet(System.nanoTime() - wStart);
                    synchronized (funnelSum) {
                        for (int i = 0; i < 4; i++) funnelSum[i] += funnel[i];
                    }
                }
            });
        }
        for (Thread th : jw) th.start();
        joinAll(jw);
        long joinMs = (System.nanoTime() - tJoin) / 1_000_000L;
        if (diagKnown) diag.knownSurv = knownSurv.get();

        final long[][] surv = survPacked.toArray(new long[0][]);
        Arrays.sort(surv, new Comparator<long[]>() {
            @Override
            public int compare(long[] a, long[] b) {
                return Double.compare(Double.longBitsToDouble(b[1]), Double.longBitsToDouble(a[1]));
            }
        });

        MitmPlan plan = new MitmPlan();
        plan.m = m;
        plan.last = last;
        plan.level = level;
        plan.nBack = nBack;
        plan.margThresh = margThresh;
        plan.F = F;
        plan.fullMkByT = fullMkByT;
        plan.fullHdByT = fullHdByT;
        plan.bChanges = bChanges;
        plan.btByT = btByT;
        plan.backWalls = backWalls;
        plan.surv = surv;
        plan.sweep = sweep;
        plan.collectSeamMs = collectSeamMs;
        plan.transferMs = transferMs;
        plan.joinMs = joinMs;
        plan.totalPairs = aTotal.get();
        plan.budgetPairs = aBudget.get();
        plan.marginNs = aMarginNs.get();
        plan.workerNs = aWorkerNs.get();
        System.arraycopy(funnelSum, 0, plan.funnel, 0, 4);
        return plan;
    }

    static ColdResult certifyPlan(final ColdProblem p, final ColdSearch.Config cfg, Params params,
                                  final MitmPlan plan, final AtomicBoolean cancel, final AtomicLong certsOut) {
        if (plan.surv.length == 0) return null;
        final int[] alphabet = params.alphabet;
        final long[][] surv = plan.surv;
        final List<ArcSweep.ArcState> F = plan.F;
        final int[][] fullMkByT = plan.fullMkByT;
        final boolean[][] fullHdByT = plan.fullHdByT;
        final int[] bChanges = plan.bChanges;
        final int fm2 = plan.m;
        final int lastF = plan.last;
        final int levelC = plan.level;
        final int steps = (int) Math.round(360.0 / 0.5);
        final int nThreads = Math.max(2, cfg == null ? 4 : Runtime.getRuntime().availableProcessors());
        final int oldBucket = ColdSearch.BUCKET_SLICE_BUDGET;
        ColdSearch.BUCKET_SLICE_BUDGET = params.bucket;
        final AtomicReference<ColdResult> solved = new AtomicReference<ColdResult>(null);
        final AtomicBoolean abort = new AtomicBoolean(false);
        final AtomicInteger sCursor = new AtomicInteger(0);
        final ColdProblem pF = p;
        final ColdSearch.Config cfgF = cfg;
        final AtomicBoolean cancelF = cancel != null ? cancel : new AtomicBoolean(false);
        try {
            Thread[] cw = new Thread[nThreads];
            for (int w = 0; w < nThreads; w++) {
                cw[w] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
                        for (int i = 0; i < steps; i++) {
                            scan[i] = new ColdSearch.Sweep(pF, cfgF, -180.0 + i * 0.5, 0, null);
                        }
                        while (!abort.get() && !cancelF.get()) {
                            int si = sCursor.getAndIncrement();
                            if (si >= surv.length) break;
                            int fi = (int) (surv[si][0] >>> 32);
                            int bi = (int) (surv[si][0] & 0xFFFFFFFFL);
                            ArcSweep.ArcState f = F.get(fi);
                            int[] fm = fullMkByT[bi];
                            boolean[] fh = fullHdByT[bi];
                            int backLastCombo = fm[lastF - 1];
                            boolean backLastHold = fh[lastF - 1];
                            int fCombo = fm2 > 0 ? f.prefixKey[fm2 - 1] : -1;
                            boolean fHold = fm2 > 0 && f.prefixHold[fm2 - 1];
                            int seamChg = (fm2 > 0 && (fm[fm2] != fCombo || fh[fm2] != fHold)) ? 1 : 0;
                            int momChg = f.changes + bChanges[bi] + seamChg;
                            for (int lc = 0; lc < alphabet.length && !abort.get(); lc++) {
                                int lastCombo = alphabet[lc];
                                boolean canRun = KeyLine.canRun(lastCombo);
                                int holdOpts = canRun ? 2 : 1;
                                for (int hoi = 0; hoi < holdOpts; hoi++) {
                                    boolean lastHold = canRun && hoi == 1;
                                    int chg = (lastCombo != backLastCombo || lastHold != backLastHold) ? 1 : 0;
                                    if (momChg + chg > levelC) continue;
                                    StringBuilder sb = new StringBuilder();
                                    for (int k = 0; k < fm2; k++) {
                                        sb.append(f.prefixKey[k]).append(f.prefixHold[k] ? '+' : '.');
                                    }
                                    for (int k = fm2; k < lastF; k++) {
                                        sb.append(fm[k]).append(fh[k] ? '+' : '.');
                                    }
                                    sb.append(lastCombo).append(lastHold ? '+' : '.');
                                    if (certsOut != null) certsOut.incrementAndGet();
                                    ColdResult r = ColdSearch.certifySig(pF, scan, sb.toString(), cfgF, abort);
                                    if (r != null && r.solved() && r.maxViolation <= 0.0
                                            && solved.compareAndSet(null, r)) {
                                        abort.set(true);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                });
            }
            for (Thread th : cw) th.start();
            joinAll(cw);
        } finally {
            ColdSearch.BUCKET_SLICE_BUDGET = oldBucket;
        }
        return solved.get();
    }

    static boolean prefixEq(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    static boolean prefixEq(boolean[] a, boolean[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    static void joinAll(Thread[] ts) {
        for (Thread th : ts) {
            try {
                th.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void enumBack(int[] alpha, int len, int cap, List<int[]> outMk, List<boolean[]> outHd) {
        enumBackRec(alpha, new int[len], new boolean[len], 0, len, -1, false, 0, cap, outMk, outHd);
    }

    static void enumBackRec(int[] alpha, int[] mk, boolean[] hd, int k, int len,
                            int prevCombo, boolean prevHold, int changes, int cap,
                            List<int[]> outMk, List<boolean[]> outHd) {
        if (k == len) {
            outMk.add(mk.clone());
            outHd.add(hd.clone());
            return;
        }
        for (int c : alpha) {
            boolean canRun = KeyLine.canRun(c);
            int holdOpts = !canRun ? 1 : 2;
            for (int hoi = 0; hoi < holdOpts; hoi++) {
                boolean h = canRun && hoi == 1;
                int chg = (prevCombo >= 0 && (c != prevCombo || h != prevHold)) ? 1 : 0;
                if (changes + chg > cap) continue;
                mk[k] = c;
                hd[k] = h;
                enumBackRec(alpha, mk, hd, k + 1, len, c, h, changes + chg, cap, outMk, outHd);
            }
        }
    }
}
