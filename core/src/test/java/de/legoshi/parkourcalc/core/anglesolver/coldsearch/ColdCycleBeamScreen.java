package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdCycleBeamScreen {

    private static final int[] COASTS = {KeyLine.NONE, KeyLine.S, KeyLine.A, KeyLine.D, KeyLine.SA, KeyLine.SD,
            KeyLine.W, KeyLine.WA, KeyLine.WD};
    private static final int[] GLIDES = {KeyLine.A, KeyLine.D, KeyLine.SA, KeyLine.SD, KeyLine.WA, KeyLine.WD, KeyLine.S};
    private static final int[] PRESSES = {KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.S, KeyLine.SA, KeyLine.SD};
    private static final int[] ENGAGES = {KeyLine.W, KeyLine.WA, KeyLine.WD};

    private static List<int[]> cycleFamilies(int L, int glideLo, int glideHi,
                                             int[] coasts, int[] glides, int[] presses, int[] engages, int[] brakes) {
        List<int[]> pats = new ArrayList<int[]>();
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

    private static int comboByName(String s) {
        s = s.trim().toUpperCase(Locale.ROOT);
        if (s.equals("NONE") || s.equals("-")) return KeyLine.NONE;
        for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
            if (KeyLine.COMBO_LABEL[c].equalsIgnoreCase(s)) return c;
        }
        throw new IllegalArgumentException("unknown combo: " + s);
    }

    private static int[] parseCombos(String key, int[] dflt) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) return dflt;
        String[] parts = v.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = comboByName(parts[i]);
        return out;
    }

    private static int[][] parseRanges(String v, int glideMax) {
        if (v == null || v.isEmpty()) return null;
        String[] parts = v.split(",");
        int[][] out = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String[] lh = parts[i].trim().split("-");
            out[i][0] = Integer.parseInt(lh[0].trim());
            out[i][1] = lh.length > 1 ? Integer.parseInt(lh[1].trim()) : glideMax;
        }
        return out;
    }

    private static int[] rangeFor(int[][] ranges, int ci, int glideMax) {
        if (ranges != null && ci < ranges.length) return ranges[ci];
        return new int[] {1, glideMax};
    }

    private static String rangesStr(int[][] r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(r[i][0]).append('-').append(r[i][1]);
        }
        return sb.toString();
    }

    private static String labels(int[] combos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < combos.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(KeyLine.COMBO_LABEL[combos[i]]);
        }
        return sb.append(']').toString();
    }

    private static java.io.PrintStream out = System.out;

    @Test
    public void beam() throws Exception {
        String path = System.getenv("PKC_COLD_BEAM_FILE");
        Assume.assumeTrue("set PKC_COLD_BEAM_FILE", path != null && !path.isEmpty());
        String logPath = System.getenv("PKC_COLD_BEAM_LOG");
        if (logPath != null && !logPath.isEmpty()) {
            out = new java.io.PrintStream(new java.io.FileOutputStream(logPath, true), true, "UTF-8");
        }
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config cfg = new ColdSearch.Config();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int glideMax = Integer.parseInt(env("PKC_COLD_BEAM_GLIDE", "2"));
        int beamCap = Integer.parseInt(env("PKC_COLD_BEAM_CAP", "4000"));
        double facingStep = Double.parseDouble(env("PKC_COLD_BEAM_FSTEP", "1.0"));
        int certifyCap = Integer.parseInt(env("PKC_COLD_BEAM_CERTCAP", "4000"));
        long budgetMs = Long.parseLong(env("PKC_COLD_BEAM_BUDGET_MS", "600000"));
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        final int bucketBudget = Integer.parseInt(env("PKC_COLD_BEAM_BUCKET_BUDGET",
                String.valueOf(ColdSearch.BUCKET_SLICE_BUDGET)));
        final int nThreads = Math.max(1, Integer.parseInt(
                env("PKC_COLD_BEAM_THREADS", String.valueOf(Runtime.getRuntime().availableProcessors()))));
        int[] coastSet = parseCombos("PKC_COLD_BEAM_COASTS", COASTS);
        int[] glideSet = parseCombos("PKC_COLD_BEAM_GLIDES", GLIDES);
        int[] pressSet = parseCombos("PKC_COLD_BEAM_PRESSES", PRESSES);
        int[] engageSet = parseCombos("PKC_COLD_BEAM_ENGAGES", ENGAGES);
        int[] brakeSet = parseCombos("PKC_COLD_BEAM_BRAKES", new int[0]);
        out.printf(Locale.ROOT, "alphabets coasts=%s glides=%s presses=%s engages=%s brakes=%s%n",
                labels(coastSet), labels(glideSet), labels(pressSet), labels(engageSet), labels(brakeSet));
        int[][] glideRanges = parseRanges(env("PKC_COLD_BEAM_GLIDE_RANGES", ""), glideMax);
        if (glideRanges != null) out.printf(Locale.ROOT, "per-cycle glide ranges=%s%n", rangesStr(glideRanges));

        int fsteps = (int) Math.round(360.0 / facingStep);
        ColdSearch.Sweep[] fscan = new ColdSearch.Sweep[fsteps];
        for (int i = 0; i < fsteps; i++) fscan[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * facingStep, 0, null);
        final double probeStep = Double.parseDouble(env("PKC_COLD_BEAM_PROBE_STEP", "0.5"));

        int[] presses = p.pressSegTicks;
        int nSegs = p.lastPressSeg + 1;
        int[][] cyc = new int[presses.length][2];
        int prev = -1;
        for (int i = 0; i < presses.length; i++) {
            cyc[i][0] = prev + 1;
            cyc[i][1] = presses[i];
            prev = presses[i];
        }
        out.printf(Locale.ROOT, "cycles=%d nSegs=%d facings=%d glideMax=%d beamCap=%d%n",
                presses.length, nSegs, fsteps, glideMax, beamCap);
        for (int i = 0; i < cyc.length; i++) {
            int[] rg = rangeFor(glideRanges, i, glideMax);
            out.printf(Locale.ROOT, "  cycle %d: segs [%d..%d] L=%d glide=%d-%d families=%d%n",
                    i, cyc[i][0], cyc[i][1], cyc[i][1] - cyc[i][0] + 1, rg[0], rg[1],
                    cycleFamilies(cyc[i][1] - cyc[i][0] + 1, rg[0], rg[1], coastSet, glideSet, pressSet, engageSet, brakeSet).size());
        }

        String seEnv = env("PKC_COLD_BEAM_SPRINT_ENGAGE", "0");
        int[] engagePts;
        if (seEnv.equalsIgnoreCase("sweep")) {
            engagePts = new int[cyc.length + 1];
            for (int i = 0; i < cyc.length; i++) engagePts[i] = cyc[i][0];
            engagePts[cyc.length] = nSegs;
        } else {
            engagePts = new int[] {Integer.parseInt(seEnv.trim())};
        }
        out.printf(Locale.ROOT, "sprint engage points=%s%n", java.util.Arrays.toString(engagePts));

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
            int[] rg = rangeFor(glideRanges, ci, glideMax);
            List<int[]> fams = cycleFamilies(L, rg[0], rg[1], coastSet, glideSet, pressSet, engageSet, brakeSet);
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
                            ? feasWithTail(fscan, mk, hb, endSeg, cfg.rectSlack)
                            : feasWidth(fscan, mk, hb, endSeg, cfg) >= -cfg.rectSlack;
                    if (!ok && lastCycle) tailCut++;
                    if (ok) {
                        next.add(new int[][] {mk, hd, partial[2]});
                    }
                    if (System.nanoTime() > deadline) break;
                }
                if (System.nanoTime() > deadline) break;
            }
            if (next.size() > beamCap) {
                final int es = endSeg;
                final java.util.HashMap<int[][], Double> keyCache = new java.util.HashMap<int[][], Double>();
                for (int[][] q : next) keyCache.put(q, maxWidth(fscan, q, es));
                Collections.sort(next, new Comparator<int[][]>() {
                    @Override
                    public int compare(int[][] x, int[][] y) {
                        return Double.compare(keyCache.get(x), keyCache.get(y));
                    }
                });
                next = new ArrayList<int[][]>(next.subList(0, beamCap));
            }
            beam = next;
            out.printf(Locale.ROOT, "cycle %d: extensions so far=%d survivors=%d tailCut=%d (deadline hit=%b)%n",
                    ci, extTotal, beam.size(), tailCut, System.nanoTime() > deadline);
            if (System.nanoTime() > deadline) break;
        }

        final List<String> sigs = new ArrayList<String>();
        for (int[][] partial : beam) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nSegs; i++) sb.append(partial[0][i]).append(partial[1][i] == 1 ? '+' : '.');
            sigs.add(sb.toString());
        }
        final double probeGate = Double.parseDouble(env("PKC_COLD_BEAM_PROBE_GATE", "0.15"));
        final boolean certifyAll = probeGate >= 100.0;
        final String[] sigArr = sigs.toArray(new String[0]);
        final java.util.concurrent.atomic.AtomicInteger nextIdx = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger certifiedCt = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger probedCt = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicReference<String> solvedSig =
                new java.util.concurrent.atomic.AtomicReference<String>(null);
        final double[] runBestBox = {Double.POSITIVE_INFINITY};
        final String[] runBestSigBox = {"-"};
        final Object bestLock = new Object();
        final long streamStart = System.nanoTime();
        final int oldBucketBudget = ColdSearch.BUCKET_SLICE_BUDGET;
        try {
            ColdSearch.BUCKET_SLICE_BUDGET = bucketBudget;
            out.printf(Locale.ROOT,
                    "streaming certify over %d candidates (gate=%s cap=%d threads=%d bucketBudget=%d)...%n",
                    sigArr.length, certifyAll ? "ALL" : String.format(Locale.ROOT, "%.3f", probeGate),
                    certifyCap, nThreads, ColdSearch.BUCKET_SLICE_BUDGET);
            Thread[] workers = new Thread[nThreads];
            for (int t = 0; t < nThreads; t++) {
                workers[t] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            work();
                        } catch (Throwable ex) {
                            cancel.set(true);
                            synchronized (out) {
                                out.printf(Locale.ROOT, "WORKER FAILED: %s%n", ex);
                                ex.printStackTrace(out);
                            }
                        }
                    }

                    private void work() {
                        ColdSearch.Sweep[] tcscan = buildScan(p, cfg, 0.5);
                        ColdSearch.Sweep[] tpscan = certifyAll ? null : buildScan(p, cfg, probeStep);
                        while (solvedSig.get() == null) {
                            int i = nextIdx.getAndIncrement();
                            if (i >= sigArr.length) break;
                            if (System.nanoTime() > deadline) {
                                cancel.set(true);
                                break;
                            }
                            String sig = sigArr[i];
                            boolean doCert = certifyAll;
                            if (!certifyAll) {
                                double v = ColdSearch.probeViolOf(p, tpscan, sig, cfg, cancel);
                                synchronized (bestLock) {
                                    if (v < runBestBox[0]) {
                                        runBestBox[0] = v;
                                        runBestSigBox[0] = sig;
                                    }
                                }
                                doCert = Double.isFinite(v) && v <= probeGate;
                            }
                            int done = probedCt.incrementAndGet();
                            if (doCert && certifiedCt.get() < certifyCap) {
                                certifiedCt.incrementAndGet();
                                long[] full = ColdSearch.benchSig(p, tcscan, sig, cfg, true, false, cancel);
                                if (full[2] == 1 && solvedSig.compareAndSet(null, sig)) {
                                    out.printf(Locale.ROOT, "SOLVED sig=%s idx=%d certified=%d ms=%d%n",
                                            sig, i, certifiedCt.get(),
                                            (System.nanoTime() - streamStart) / 1_000_000L);
                                    cancel.set(true);
                                    return;
                                }
                            }
                            if (done % 500 == 0) {
                                out.printf(Locale.ROOT, "  progress done=%d/%d certified=%d ms=%d%n",
                                        done, sigArr.length, certifiedCt.get(),
                                        (System.nanoTime() - streamStart) / 1_000_000L);
                            }
                        }
                    }
                });
            }
            for (Thread w : workers) w.start();
            for (Thread w : workers) w.join();
        } finally {
            ColdSearch.BUCKET_SLICE_BUDGET = oldBucketBudget;
        }
        if (solvedSig.get() != null) return;
        out.printf(Locale.ROOT, "no solve: probed=%d certified=%d bestProbe=%s sig=%s ms=%d%n",
                probedCt.get(), certifiedCt.get(),
                certifyAll ? "n/a" : String.format(Locale.ROOT, "%.4e", runBestBox[0]),
                certifyAll ? "-" : runBestSigBox[0],
                (System.nanoTime() - streamStart) / 1_000_000L);
    }

    private static boolean feasWithTail(ColdSearch.Sweep[] fscan, int[] mk, boolean[] hd, int endSeg, double slack) {
        for (ColdSearch.Sweep s : fscan) {
            double[] tr = s.traceLineTo(mk, hd, endSeg);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            if (w >= -slack && s.lineTailReachable(mk, hd)) return true;
        }
        return false;
    }

    private static double feasWidth(ColdSearch.Sweep[] fscan, int[] mk, boolean[] hd, int endSeg, ColdSearch.Config cfg) {
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

    private static ColdSearch.Sweep[] buildScan(ColdProblem p, ColdSearch.Config cfg, double step) {
        int steps = (int) Math.round(360.0 / step);
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
        for (int i = 0; i < steps; i++) scan[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * step, 0, null);
        return scan;
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
