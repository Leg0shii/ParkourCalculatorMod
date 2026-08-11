package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Meet-in-the-middle diagnostics. All three tests are inert unless their env gate is set. The join/certify
 * path drives the production {@link ColdMitmSolver#buildPlan}/{@link ColdMitmSolver#certifyPlan} so this
 * screen never forks the solve; it only layers measurement on top (join funnel, the output-sensitive index
 * experiment, the transfer-vs-continueFrom soundness XCHECK, the per-component bench, the constraint-shrink
 * experiment). {@code mitm} and {@code mitmSolve} are independent exact-walk validators for the mechanism.
 */
public class ColdMitmScreen {

    @Test
    public void mitm() {
        Assume.assumeTrue("set PKC_COLD_MITM=1", "1".equals(System.getenv("PKC_COLD_MITM")));
        String stem = env("PKC_COLD_MITM_STEM", "hpk_human/d11/j1150-2x2bm_Nix_Neo");
        String knownSig = env("PKC_COLD_MITM_SIG",
                "7.7.7.7.7.7.7.7.7.7.7.7.7.7.7.7.3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+1+");
        int[] alpha = parseAlpha(env("PKC_COLD_MITM_ALPHA", "-,W,WA,WD,SA,SD"));
        int frontCap = Integer.parseInt(env("PKC_COLD_MITM_FRONTCAP", "1"));
        int backCap = Integer.parseInt(env("PKC_COLD_MITM_BACKCAP", "1"));

        PrintStream out = logStream();

        SaveFile file = ColdTestHarness.loadSave(stem);
        ColdProblem p = ColdProblem.fromSave(file);
        int last = p.lastPressSeg;
        int nSegs = p.pressSegTicks.length;
        int m = Integer.parseInt(env("PKC_COLD_MITM_SEAM", Integer.toString(last / 2)));
        out.printf(Locale.ROOT, "%s: presses=%s lastPressSeg=%d numTicks=%d seam=%d frontCap=%d backCap=%d%n",
                stem, java.util.Arrays.toString(p.pressSegTicks), last, p.numTicks, m, frontCap, backCap);

        int[] mk = new int[last];
        boolean[] hd = new boolean[last];
        parseSig(knownSig, mk, hd);

        ColdSearch.Config cfg = new ColdSearch.Config();
        cfg.arcExhaustiveMaxLevel = 99;
        int[][] segAlpha = new int[nSegs][];
        int[] segMax = new int[nSegs];
        for (int i = 0; i < nSegs; i++) {
            segAlpha[i] = alpha.clone();
            segMax[i] = 10;
        }

        int[] frontKey = new int[m];
        boolean[] frontHold = new boolean[m];
        System.arraycopy(mk, 0, frontKey, 0, m);
        System.arraycopy(hd, 0, frontHold, 0, m);

        long t0 = System.nanoTime();
        ArcSweep fwd = new ArcSweep(p, cfg, 0, new ColdSearch.SigCollector(cfg));
        fwd.setSegments(segAlpha, segMax);
        List<ArcSweep.ArcState> F = fwd.collectSeam(m, frontCap, 300_000_000L, frontKey, frontHold);
        long fMs = (System.nanoTime() - t0) / 1_000_000L;
        out.printf(Locale.ROOT, "FORWARD |F|=%d (retained-matching=%d) nodes=%d ms=%d truncated=%b%n",
                fwd.seamCount, F.size(), fwd.nodes, fMs, fwd.truncated);
        int matchBranches = 0;
        int endsTotal = 0;
        boolean contFeasible = false;
        for (ArcSweep.ArcState f : F) {
            if (ColdMitmSolver.prefixEq(f.prefixKey, frontKey) && ColdMitmSolver.prefixEq(f.prefixHold, frontHold)) {
                matchBranches++;
                List<ArcSweep.ArcState> ends = fwd.continueFrom(f, mk, hd);
                endsTotal += ends.size();
                for (ArcSweep.ArcState e : ends) {
                    if (fwd.tailFeasible(e)) contFeasible = true;
                }
            }
        }
        out.printf(Locale.ROOT, "FRONT-PREFIX present in F: %b (branches=%d)%n", matchBranches > 0, matchBranches);
        out.printf(Locale.ROOT, "CONTINUATION through known back-suffix tail-feasible: %b (endsTotal=%d)%n",
                contFeasible, endsTotal);

        ColdSearch.Sweep[] scan = ColdTestHarness.buildScan(p, cfg, 0.5);
        ColdResult cr = ColdSearch.certifySig(p, scan, knownSig, cfg, new AtomicBoolean(false));
        out.printf(Locale.ROOT, "CERTIFY known sig: solved=%b viol=%.3e%n",
                cr != null && cr.solved(), cr == null ? Double.NaN : cr.maxViolation);

        long bCount = countBack(alpha, m, last, backCap);
        out.printf(Locale.ROOT, "BACK |B|~=%d (templates over %d..%d, <=%d changes)%n", bCount, m, last, backCap);
        double naive = (double) fwd.seamCount * bCount;
        out.printf(Locale.ROOT, "MITM naive product |F|x|B| = %.3g ; direct L(front+back) build enumerates the full tree%n", naive);
        out.printf(Locale.ROOT, "VERDICT: mechanism %s%n",
                (matchBranches > 0 && contFeasible && cr != null && cr.solved()) ? "VALIDATED" : "FAILED");
        out.flush();
    }

    @Test
    public void mitmJoin() {
        Assume.assumeTrue("set PKC_COLD_MITM_JOIN=1", "1".equals(System.getenv("PKC_COLD_MITM_JOIN")));
        final String stem = env("PKC_COLD_MITM_STEM", "hpk_human/d11/j1150-2x2bm_Nix_Neo");
        final String knownSig = env("PKC_COLD_MITM_SIG",
                "7.7.7.7.7.7.7.7.7.7.7.7.7.7.7.7.3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+1+");
        int[] alpha = parseAlpha(env("PKC_COLD_MITM_ALPHA", "-,W,WA,WD,SA,SD"));
        int frontCap = Integer.parseInt(env("PKC_COLD_MITM_FRONTCAP", "1"));
        int backCap = Integer.parseInt(env("PKC_COLD_MITM_BACKCAP", "1"));
        final int level = Integer.parseInt(env("PKC_COLD_MITM_LEVEL", Integer.toString(frontCap + backCap + 1)));
        final int bucket = Integer.parseInt(env("PKC_COLD_MITM_BUCKET", "3"));
        boolean doCertify = "1".equals(env("PKC_COLD_MITM_CERTIFY", "1"));

        PrintStream out = logStream();
        SaveFile file = ColdTestHarness.loadSave(stem);
        final ColdProblem p = ColdProblem.fromSave(file);
        double shrink = Double.parseDouble(env("PKC_COLD_SHRINK", "1.0"));
        if (shrink < 1.0) {
            java.util.List<ColdProblem.Wall> sw = new java.util.ArrayList<ColdProblem.Wall>();
            int tightened = 0;
            for (ColdProblem.Wall w : p.momentumWalls) {
                if (w.lo != Double.NEGATIVE_INFINITY && w.hi != Double.POSITIVE_INFINITY) {
                    double c = 0.5 * (w.lo + w.hi);
                    double h = 0.5 * (w.hi - w.lo) * shrink;
                    sw.add(new ColdProblem.Wall(w.segTick, w.axisX, c - h, c + h));
                    tightened++;
                } else {
                    sw.add(w);
                }
            }
            p.momentumWalls.clear();
            p.momentumWalls.addAll(sw);
            out.printf(Locale.ROOT, "SHRINK momentum walls x%.2f toward center (tightened %d of %d)%n",
                    shrink, tightened, sw.size());
        }
        final int last = p.lastPressSeg;
        final int m = Integer.parseInt(env("PKC_COLD_MITM_SEAM", Integer.toString(last / 2)));

        final ColdSearch.Config cfg = new ColdSearch.Config();
        cfg.arcExhaustiveMaxLevel = 99;
        final double margThresh = -(cfg.rectSlack + 2.0e-3);

        long maxB = Long.parseLong(env("PKC_COLD_MITM_MAXB", "4000000"));
        long bEst = backRec(alpha, m, last, -1, false, 0, backCap);
        out.printf(Locale.ROOT, "|B_mom| estimate=%d (cap=%d)%n", bEst, maxB);
        if (bEst > maxB) {
            out.printf(Locale.ROOT, "ABORT: |B_mom| exceeds cap; reduce backCap or move seam.%n");
            out.flush();
            return;
        }

        final boolean haveKnown = knownSig != null && knownSig.length() == (last + 1) * 2;
        ColdMitmSolver.MitmDiag diag = new ColdMitmSolver.MitmDiag();
        diag.bench = true;
        if (haveKnown) {
            diag.known = true;
            diag.knownMk = new int[last + 1];
            diag.knownHd = new boolean[last + 1];
            parseSig(knownSig, diag.knownMk, diag.knownHd);
        }

        ColdMitmSolver.Params params = new ColdMitmSolver.Params();
        params.alphabet = alpha;
        params.frontCap = frontCap;
        params.backCap = backCap;
        params.seam = m;
        params.level = level;
        params.bucket = bucket;
        params.nodeCap = 300_000_000L;

        AtomicBoolean noCancel = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        ColdMitmSolver.MitmPlan plan = ColdMitmSolver.buildPlan(p, cfg, params, noCancel, diag);
        if (plan == null) {
            out.printf(Locale.ROOT, "%s seam=%d: no front states / degenerate; abort%n", stem, m);
            out.flush();
            return;
        }
        out.printf(Locale.ROOT,
                "%s seam=%d frontCap=%d backCap=%d level=%d tightTail=%b: |F|=%d nodes=%d collectMs=%d%n",
                stem, m, frontCap, backCap, level, ArcSweep.MITM_TIGHT_TAIL,
                plan.F.size(), plan.sweep.nodes, plan.collectSeamMs);
        double product = (double) plan.F.size() * plan.nBack;
        out.printf(Locale.ROOT, "|B_mom|=%d product=%.3g back momentum walls=%d transferMs=%d%n",
                plan.nBack, product, plan.backWalls.size(), plan.transferMs);
        if (haveKnown) {
            out.printf(Locale.ROOT, "known back momentum template present in B_mom: %b (idx=%d)%n",
                    diag.knownT >= 0, diag.knownT);
        }
        long survivors = plan.surv.length;
        out.printf(Locale.ROOT, "JOIN survivors=%d product=%.3g selectivity=%.3e joinMs=%d%n",
                survivors, product, survivors / product, plan.joinMs);
        long budgetKilled = plan.totalPairs - plan.budgetPairs;
        double marginFrac = plan.workerNs > 0 ? (double) plan.marginNs / plan.workerNs : 0.0;
        out.printf(Locale.ROOT,
                "BENCH-FILTER pairs=%d budgetKilled=%d(%.1f%%) enteredMargin=%d | tailCheck=%.1f%% of worker-ns enum/budget=%.1f%% | avgMarginNs=%.0f%n",
                plan.totalPairs, budgetKilled, 100.0 * budgetKilled / Math.max(1, plan.totalPairs), plan.budgetPairs,
                100.0 * marginFrac, 100.0 * (1 - marginFrac),
                plan.budgetPairs > 0 ? (double) plan.marginNs / plan.budgetPairs : 0.0);
        out.printf(Locale.ROOT,
                "BENCH-FUNNEL of %d entered: diedBackWall=%d(%.1f%%) diedIntervalTail=%d(%.2f%%) diedOmniTail=%d(%.2f%%) survived=%d(%.2f%%)%n",
                plan.budgetPairs, plan.funnel[0], pct(plan.funnel[0], plan.budgetPairs),
                plan.funnel[1], pct(plan.funnel[1], plan.budgetPairs), plan.funnel[2], pct(plan.funnel[2], plan.budgetPairs),
                plan.funnel[3], pct(plan.funnel[3], plan.budgetPairs));
        out.printf(Locale.ROOT, "VALIDATE known (front-prefix, back-momentum) pair survives filter: %b%n",
                diag.knownSurv);

        if ("1".equals(env("PKC_COLD_MITM_INDEX", "0"))) {
            runIndexedJoin(out, p, cfg, plan.sweep, plan.F, plan.nBack, plan.fullMkByT, plan.fullHdByT,
                    plan.bChanges, plan.btByT, plan.backWalls, m, last, level, margThresh, plan.surv);
        }

        if ("1".equals(env("PKC_COLD_MITM_XCHECK", "1"))) {
            int nf = Math.min(1500, plan.F.size());
            int nt = Math.min(60, plan.nBack);
            long agree = 0;
            long disagree = 0;
            long fastOnly = 0;
            long exactOnly = 0;
            boolean savedTight = ArcSweep.MITM_TIGHT_TAIL;
            ArcSweep.MITM_TIGHT_TAIL = false;
            for (int fi = 0; fi < nf; fi++) {
                ArcSweep.ArcState f = plan.F.get(fi);
                int sp = f.sprintPrev ? 1 : 0;
                for (int bi = 0; bi < nt; bi++) {
                    boolean fast = plan.sweep.mitmTailMargin(f, plan.btByT[bi][sp], plan.backWalls) >= margThresh;
                    List<ArcSweep.ArcState> ends = plan.sweep.continueFrom(f, plan.fullMkByT[bi], plan.fullHdByT[bi]);
                    boolean exact = false;
                    for (ArcSweep.ArcState e : ends) {
                        if (plan.sweep.tailFeasible(e)) {
                            exact = true;
                            break;
                        }
                    }
                    if (fast == exact) {
                        agree++;
                    } else {
                        disagree++;
                        if (fast) fastOnly++;
                        else exactOnly++;
                    }
                }
            }
            ArcSweep.MITM_TIGHT_TAIL = savedTight;
            out.printf(Locale.ROOT,
                    "XCHECK transfer vs continueFrom (omni tail both) on %dx%d: agree=%d disagree=%d (fastOnly=%d exactOnly=%d) [exactOnly>0 = transfer drops real pairs = gate fired]%n",
                    nf, nt, agree, disagree, fastOnly, exactOnly);
        }

        if (haveKnown) {
            ColdSearch.Sweep[] scanD = ColdTestHarness.buildScan(p, cfg, 0.5);
            int ob = ColdSearch.BUCKET_SLICE_BUDGET;
            ColdSearch.BUCKET_SLICE_BUDGET = bucket;
            ColdResult kr = ColdSearch.certifySig(p, scanD, knownSig, cfg, new AtomicBoolean(false));
            ColdSearch.BUCKET_SLICE_BUDGET = ob;
            out.printf(Locale.ROOT, "DIAG known full sig certify at bucket=%d: solved=%b viol=%.3e%n",
                    bucket, kr != null && kr.solved(), kr == null ? Double.NaN : kr.maxViolation);
        }

        if ("1".equals(env("PKC_COLD_MITM_BENCH", "0")) && plan.surv.length > 0) {
            int sample = Math.min(Integer.parseInt(env("PKC_COLD_MITM_BENCH_N", "400")), plan.surv.length);
            ColdSearch.Sweep[] scan = ColdTestHarness.buildScan(p, cfg, 0.5);
            int ob = ColdSearch.BUCKET_SLICE_BUDGET;
            ColdSearch.BUCKET_SLICE_BUDGET = bucket;
            AtomicBoolean noCancelBench = new AtomicBoolean(false);
            ColdSearch.profReset();
            long probeNs = 0;
            long certNs = 0;
            int solvedN = 0;
            for (int si = 0; si < sample; si++) {
                int fi = (int) (plan.surv[si][0] >>> 32);
                int bi = (int) (plan.surv[si][0] & 0xFFFFFFFFL);
                ArcSweep.ArcState f = plan.F.get(fi);
                int[] fm = plan.fullMkByT[bi];
                boolean[] fh = plan.fullHdByT[bi];
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < m; k++) sb.append(f.prefixKey[k]).append(f.prefixHold[k] ? '+' : '.');
                for (int k = m; k < last; k++) sb.append(fm[k]).append(fh[k] ? '+' : '.');
                sb.append(fm[last - 1]).append(fh[last - 1] ? '+' : '.');
                long[] bench = ColdSearch.benchSig(p, scan, sb.toString(), cfg, true, true, noCancelBench);
                probeNs += bench[0];
                certNs += bench[1];
                if (bench[2] == 1) solvedN++;
            }
            ColdSearch.BUCKET_SLICE_BUDGET = ob;
            out.printf(Locale.ROOT,
                    "BENCH-CERTIFY sample=%d solved=%d | avgProbeMs=%.3f avgCertMs=%.3f probe:cert=1:%.1f%n",
                    sample, solvedN, probeNs / 1e6 / sample, certNs / 1e6 / sample,
                    probeNs > 0 ? (double) certNs / probeNs : 0.0);
            out.printf(Locale.ROOT,
                    "BENCH-CERTIFY-INNER entryEvals=%d probeSolves=%d feasEntries=%d | ns buildSpec=%d probeSolve=%d closedForm=%d slp=%d | slpDecisive=%d%n",
                    ColdSearch.profEntryEvals, ColdSearch.profProbeSolves, ColdSearch.profFeasEntries,
                    ColdSearch.profNsBuildSpec, ColdSearch.profNsProbeSolve, ColdSearch.profNsClosedForm,
                    ColdSearch.profNsSlp, ColdSearch.profSlpDecisiveCount);
        }

        String solvedSig = null;
        long certMs = 0;
        AtomicLong certs = new AtomicLong(0);
        if (doCertify && survivors > 0) {
            long t2 = System.nanoTime();
            ColdResult r = ColdMitmSolver.certifyPlan(p, cfg, params, plan, noCancel, certs);
            certMs = (System.nanoTime() - t2) / 1_000_000L;
            if (r != null && r.solved()) solvedSig = r.line.signature();
        }
        long totMs = (System.nanoTime() - t0) / 1_000_000L;
        out.printf(Locale.ROOT, "CERTIFY certs=%d certMs=%d totalMs=%d%n", certs.get(), certMs, totMs);
        out.printf(Locale.ROOT, "MITM-JOIN %s: %s%n", stem,
                solvedSig != null ? "SOLVED sig=" + solvedSig : (doCertify ? "no solution found" : "certify skipped"));
        out.flush();
    }

    @Test
    public void mitmSolve() {
        Assume.assumeTrue("set PKC_COLD_MITM_SOLVE=1", "1".equals(System.getenv("PKC_COLD_MITM_SOLVE")));
        final String stem = env("PKC_COLD_MITM_STEM", "hpk_human/d11/j1150-2x2bm_Nix_Neo");
        int[] alpha = parseAlpha(env("PKC_COLD_MITM_ALPHA", "-,W,WA,WD,SA,SD"));
        int frontCap = Integer.parseInt(env("PKC_COLD_MITM_FRONTCAP", "1"));
        int backCap = Integer.parseInt(env("PKC_COLD_MITM_BACKCAP", "1"));
        final int bucket = Integer.parseInt(env("PKC_COLD_MITM_BUCKET", "3"));

        PrintStream out = logStream();

        SaveFile file = ColdTestHarness.loadSave(stem);
        final ColdProblem p = ColdProblem.fromSave(file);
        final int last = p.lastPressSeg;
        int nSegs = p.pressSegTicks.length;
        final int m = Integer.parseInt(env("PKC_COLD_MITM_SEAM", Integer.toString(last / 2)));

        final ColdSearch.Config cfg = new ColdSearch.Config();
        cfg.arcExhaustiveMaxLevel = 99;
        int[][] segAlpha = new int[nSegs][];
        int[] segMax = new int[nSegs];
        for (int i = 0; i < nSegs; i++) {
            segAlpha[i] = alpha.clone();
            segMax[i] = 20;
        }

        long t0 = System.nanoTime();
        ArcSweep fwd = new ArcSweep(p, cfg, 0, new ColdSearch.SigCollector(cfg));
        fwd.setSegments(segAlpha, segMax);
        final List<ArcSweep.ArcState> F = fwd.collectSeam(m, frontCap, 300_000_000L);
        long fMs = (System.nanoTime() - t0) / 1_000_000L;
        out.printf(Locale.ROOT, "%s seam=%d frontCap=%d backCap=%d: |F|=%d ms=%d%n",
                stem, m, frontCap, backCap, F.size(), fMs);

        final List<int[]> bMk = new java.util.ArrayList<int[]>();
        final List<boolean[]> bHd = new java.util.ArrayList<boolean[]>();
        ColdMitmSolver.enumBack(alpha, last - m + 1, backCap, bMk, bHd);
        out.printf(Locale.ROOT, "|B|=%d  naive pairs=%.3g%n", bMk.size(), (double) F.size() * bMk.size());

        final ArcSweep join = new ArcSweep(p, cfg, 0, new ColdSearch.SigCollector(cfg));
        join.setSegments(segAlpha, segMax);
        final java.util.concurrent.atomic.AtomicReference<String> solved =
                new java.util.concurrent.atomic.AtomicReference<String>(null);
        final java.util.concurrent.atomic.AtomicBoolean abort = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicLong feasPairs = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicLong certs = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicInteger nextF = new java.util.concurrent.atomic.AtomicInteger(0);
        int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        final int steps = (int) Math.round(360.0 / 0.5);
        Thread[] workers = new Thread[nThreads];
        final int oldBucket = ColdSearch.BUCKET_SLICE_BUDGET;
        ColdSearch.BUCKET_SLICE_BUDGET = bucket;
        final long t1 = System.nanoTime();
        for (int w = 0; w < nThreads; w++) {
            workers[w] = new Thread(new Runnable() {
                @Override
                public void run() {
                    ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
                    for (int i = 0; i < steps; i++) {
                        scan[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * 0.5, 0, null);
                    }
                    {
                        while (!abort.get()) {
                            int fi = nextF.getAndIncrement();
                            if (fi >= F.size()) break;
                            ArcSweep.ArcState f = F.get(fi);
                            int[] fullMk = new int[last + 1];
                            boolean[] fullHd = new boolean[last + 1];
                            System.arraycopy(f.prefixKey, 0, fullMk, 0, m);
                            System.arraycopy(f.prefixHold, 0, fullHd, 0, m);
                            for (int bi = 0; bi < bMk.size() && !abort.get(); bi++) {
                                int[] bk = bMk.get(bi);
                                boolean[] bh = bHd.get(bi);
                                for (int j = 0; j <= last - m; j++) {
                                    fullMk[m + j] = bk[j];
                                    fullHd[m + j] = bh[j];
                                }
                                List<ArcSweep.ArcState> ends = join.continueFrom(f, fullMk, fullHd);
                                boolean feas = false;
                                for (ArcSweep.ArcState e : ends) {
                                    if (join.tailFeasible(e)) {
                                        feas = true;
                                        break;
                                    }
                                }
                                if (!feas) continue;
                                feasPairs.incrementAndGet();
                                StringBuilder sb = new StringBuilder();
                                for (int k = 0; k <= last; k++) sb.append(fullMk[k]).append(fullHd[k] ? '+' : '.');
                                String sig = sb.toString();
                                certs.incrementAndGet();
                                ColdResult r = ColdSearch.certifySig(p, scan, sig, cfg, abort);
                                if (r != null && r.solved() && r.maxViolation <= 0.0
                                        && solved.compareAndSet(null, sig)) {
                                    abort.set(true);
                                    return;
                                }
                            }
                        }
                    }
                }
            });
        }
        for (Thread th : workers) th.start();
        for (Thread th : workers) {
            try {
                th.join();
            } catch (InterruptedException ie) {
                abort.set(true);
                Thread.currentThread().interrupt();
            }
        }
        ColdSearch.BUCKET_SLICE_BUDGET = oldBucket;
        long joinMs = (System.nanoTime() - t1) / 1_000_000L;
        long totMs = (System.nanoTime() - t0) / 1_000_000L;
        out.printf(Locale.ROOT, "JOIN feasPairs=%d certs=%d joinMs=%d totalMs=%d%n",
                feasPairs.get(), certs.get(), joinMs, totMs);
        out.printf(Locale.ROOT, "MITM-SOLVE %s: %s%n",
                stem, solved.get() != null ? "SOLVED sig=" + solved.get() : "no solution found");
        out.flush();
    }

    private static double pct(long part, long whole) {
        return whole > 0 ? 100.0 * part / whole : 0.0;
    }

    private static double maxForm(java.util.List<ArcSweep.Form> forms, double sin, double cos) {
        double v = Double.NEGATIVE_INFINITY;
        for (ArcSweep.Form f : forms) v = Math.max(v, f.at(sin, cos));
        return v;
    }

    private static double minForm(java.util.List<ArcSweep.Form> forms, double sin, double cos) {
        double v = Double.POSITIVE_INFINITY;
        for (ArcSweep.Form f : forms) v = Math.min(v, f.at(sin, cos));
        return v;
    }

    private static boolean inArcs(ArcSweep.Arcs arcs, double cellLo, double cellHi) {
        for (int i = 0; i < arcs.lo.length; i++) {
            if (arcs.hi[i] >= cellLo && arcs.lo[i] <= cellHi) return true;
        }
        return false;
    }

    private static int upperBoundByA(java.util.List<double[]> lst, double upper) {
        int lo = 0;
        int hi = lst.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (lst.get(mid)[0] <= upper) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    @SuppressWarnings("unchecked")
    private static void runIndexedJoin(PrintStream out, ColdProblem p, ColdSearch.Config cfg, ArcSweep sweep,
                                       List<ArcSweep.ArcState> F, int nBack, int[][] fullMkByT, boolean[][] fullHdByT,
                                       int[] bChanges, ArcSweep.BackTransfer[][] btByT,
                                       List<ColdProblem.Wall> backWalls, int m, int last, int level,
                                       double margThresh, long[][] surv) {
        long t0 = System.nanoTime();
        java.util.HashSet<Long> unindexed = new java.util.HashSet<Long>();
        for (long[] s : surv) unindexed.add(s[0]);

        ColdProblem.Wall w0 = null;
        double bestRange = Double.POSITIVE_INFINITY;
        for (ColdProblem.Wall w : backWalls) {
            if (w.lo == Double.NEGATIVE_INFINITY || w.hi == Double.POSITIVE_INFINITY) continue;
            double range = w.hi - w.lo;
            if (range < bestRange) {
                bestRange = range;
                w0 = w;
            }
        }
        if (w0 == null) {
            out.printf(Locale.ROOT, "INDEX: no back wall with a finite range to key on; skipping%n");
            return;
        }
        final int at0 = w0.segTick;
        final boolean w0x = w0.axisX;
        final double w0lo = w0.lo;
        final double w0hi = w0.hi;
        final double marginW = cfg.rectSlack + 2.0e-3;
        final double cw0 = btByT[0][0].cd[at0];

        int steps = 720;
        double halfCell = Math.toRadians(0.25);
        double[] sinB = new double[steps];
        double[] cosB = new double[steps];
        double[] thB = new double[steps];
        for (int b = 0; b < steps; b++) {
            double th = Math.toRadians(-180.0 + b * 0.5);
            thB[b] = th;
            sinB[b] = Math.sin(th);
            cosB[b] = Math.cos(th);
        }

        java.util.List<double[]>[][] idx = new java.util.List[steps][2];
        for (int fi = 0; fi < F.size(); fi++) {
            ArcSweep.ArcState f = F.get(fi);
            int sp = f.sprintPrev ? 1 : 0;
            for (int b = 0; b < steps; b++) {
                if (!inArcs(f.arcs, thB[b] - halfCell, thB[b] + halfCell)) continue;
                double sin = sinB[b];
                double cos = cosB[b];
                double lo;
                double hi;
                double ds;
                double vs;
                if (w0x) {
                    lo = maxForm(f.lowerX, sin, cos);
                    hi = minForm(f.upperX, sin, cos);
                    ds = f.dxs * sin + f.dxc * cos;
                    vs = f.vxs * sin + f.vxc * cos;
                } else {
                    lo = maxForm(f.lowerZ, sin, cos);
                    hi = minForm(f.upperZ, sin, cos);
                    ds = f.dzs * sin + f.dzc * cos;
                    vs = f.vzs * sin + f.vzc * cos;
                }
                double a = lo + ds + cw0 * vs;
                double bb = hi + ds + cw0 * vs;
                if (idx[b][sp] == null) idx[b][sp] = new java.util.ArrayList<double[]>();
                idx[b][sp].add(new double[] {a, bb, fi});
            }
        }
        for (int b = 0; b < steps; b++) {
            for (int sp = 0; sp < 2; sp++) {
                if (idx[b][sp] != null) {
                    java.util.Collections.sort(idx[b][sp], new java.util.Comparator<double[]>() {
                        @Override
                        public int compare(double[] x, double[] y) {
                            return Double.compare(x[0], y[0]);
                        }
                    });
                }
            }
        }
        long buildMs = (System.nanoTime() - t0) / 1_000_000L;

        long t1 = System.nanoTime();
        java.util.HashSet<Long> cand = new java.util.HashSet<Long>();
        long touches = 0;
        for (int b = 0; b < steps; b++) {
            double sin = sinB[b];
            double cos = cosB[b];
            for (int sp = 0; sp < 2; sp++) {
                java.util.List<double[]> lst = idx[b][sp];
                if (lst == null || lst.isEmpty()) continue;
                for (int t = 0; t < nBack; t++) {
                    ArcSweep.BackTransfer bt = btByT[t][sp];
                    double kw0 = w0x
                            ? bt.kdxs[at0] * sin + bt.kdxc[at0] * cos
                            : bt.kdzs[at0] * sin + bt.kdzc[at0] * cos;
                    double upper = w0hi + marginW - kw0;
                    double lower = w0lo - marginW - kw0;
                    int k = upperBoundByA(lst, upper);
                    for (int i = 0; i < k; i++) {
                        double[] rec = lst.get(i);
                        touches++;
                        if (rec[1] < lower) continue;
                        int fi = (int) rec[2];
                        ArcSweep.ArcState f = F.get(fi);
                        int fCombo = m > 0 ? f.prefixKey[m - 1] : -1;
                        boolean fHold = m > 0 && f.prefixHold[m - 1];
                        int seamChg = (m > 0 && (fullMkByT[t][m] != fCombo || fullHdByT[t][m] != fHold)) ? 1 : 0;
                        if (f.changes + bChanges[t] + seamChg > level) continue;
                        cand.add(((long) fi << 32) | (t & 0xFFFFFFFFL));
                    }
                }
            }
        }
        long queryMs = (System.nanoTime() - t1) / 1_000_000L;

        long t2 = System.nanoTime();
        java.util.HashSet<Long> indexedSet = new java.util.HashSet<Long>();
        for (Long key : cand) {
            int fi = (int) (key.longValue() >>> 32);
            int t = (int) (key.longValue() & 0xFFFFFFFFL);
            ArcSweep.ArcState f = F.get(fi);
            int sp = f.sprintPrev ? 1 : 0;
            if (sweep.mitmTailMargin(f, btByT[t][sp], backWalls) >= margThresh) indexedSet.add(key);
        }
        long confirmMs = (System.nanoTime() - t2) / 1_000_000L;

        int missing = 0;
        for (Long key : unindexed) {
            if (!indexedSet.contains(key)) missing++;
        }
        int extra = indexedSet.size() - (unindexed.size() - missing);
        double productD = (double) F.size() * nBack;
        out.printf(Locale.ROOT,
                "INDEX primaryWall=%s@%d range=%.3f | candidates=%d confirmedSurv=%d unindexedSurv=%d MISSING=%d extraConfirmed=%d%n",
                w0x ? "X" : "Z", at0, bestRange, cand.size(), indexedSet.size(), unindexed.size(), missing, extra);
        out.printf(Locale.ROOT,
                "INDEX touches=%d (vs product=%.3g = %.4f%%) buildMs=%d queryMs=%d confirmMs=%d%n",
                touches, productD, 100.0 * touches / productD, buildMs, queryMs, confirmMs);
        out.printf(Locale.ROOT, "INDEX CORRECTNESS: %s%n",
                missing == 0 ? "PASS (indexed survivors == un-indexed)" : "FAIL (dropped " + missing + " real survivors)");
    }

    private static PrintStream logStream() {
        String logPath = System.getenv("PKC_COLD_MITM_LOG");
        if (logPath != null && !logPath.isEmpty()) {
            try {
                return new PrintStream(new java.io.FileOutputStream(logPath, true), true, "UTF-8");
            } catch (Exception e) {
                return System.out;
            }
        }
        return System.out;
    }

    private static long countBack(int[] alpha, int m, int last, int cap) {
        return backRec(alpha, m, last, -1, false, 0, cap);
    }

    private static long backRec(int[] alpha, int k, int last, int prevCombo, boolean prevHold,
                                int changes, int cap) {
        if (k == last) return 1;
        long total = 0;
        for (int c : alpha) {
            boolean canRun = KeyLine.canRun(c);
            int holdOpts = !canRun ? 1 : 2;
            for (int hoi = 0; hoi < holdOpts; hoi++) {
                boolean h = canRun && hoi == 1;
                int chg = (prevCombo >= 0 && (c != prevCombo || h != prevHold)) ? 1 : 0;
                if (changes + chg > cap) continue;
                total += backRec(alpha, k + 1, last, c, h, changes + chg, cap);
                if (total > 5_000_000_000L) return total;
            }
        }
        return total;
    }

    private static void parseSig(String sig, int[] mk, boolean[] hd) {
        int idx = 0;
        for (int k = 0; k < mk.length; k++) {
            mk[k] = sig.charAt(idx) - '0';
            hd[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : v;
    }

    private static int[] parseAlpha(String s) {
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = comboByLabel(parts[i].trim());
        return out;
    }

    private static int comboByLabel(String label) {
        for (int i = 0; i < KeyLine.COMBO_LABEL.length; i++) {
            if (KeyLine.COMBO_LABEL[i].equalsIgnoreCase(label)) return i;
        }
        if ("NONE".equalsIgnoreCase(label)) return KeyLine.NONE;
        throw new IllegalArgumentException("unknown combo " + label);
    }
}
