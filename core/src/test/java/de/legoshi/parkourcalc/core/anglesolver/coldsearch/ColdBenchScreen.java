package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdBenchScreen {

    @Test
    public void throughput() throws Exception {
        String path = System.getenv("PKC_COLD_BENCH_FILE");
        String landSig = System.getenv("PKC_COLD_BENCH_SIG");
        Assume.assumeTrue("set PKC_COLD_BENCH_FILE and PKC_COLD_BENCH_SIG",
                path != null && !path.isEmpty() && landSig != null && !landSig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config cfg = new ColdSearch.Config();
        AtomicBoolean cancel = new AtomicBoolean(false);

        long ts = System.nanoTime();
        int steps = (int) Math.round(360.0 / 0.5);
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
        for (int si = 0; si < steps; si++) {
            scan[si] = new ColdSearch.Sweep(p, cfg, -180.0 + si * 0.5, 0, null);
        }
        System.out.printf(Locale.ROOT, "scan build (%d sweeps, once per capture) = %.1f ms%n",
                steps, (System.nanoTime() - ts) / 1e6);

        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            mk[k] = landSig.charAt(idx) - '0';
            hd[k] = landSig.charAt(idx + 1) == '+';
            idx += 2;
        }

        List<String> variants = new ArrayList<String>();
        int[] alts = {KeyLine.WA, KeyLine.WD, KeyLine.A, KeyLine.D, KeyLine.SA, KeyLine.SD, KeyLine.W, KeyLine.S};
        for (int k = 0; k < n && variants.size() < 120; k++) {
            for (int a : alts) {
                if (a == mk[k]) continue;
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    sb.append(j == k ? a : mk[j]).append(hd[j] ? '+' : '.');
                }
                variants.add(sb.toString());
                if (variants.size() >= 120) break;
            }
        }

        ColdSearch.benchSig(p, scan, landSig, cfg, false, false, cancel);

        long probeSum = 0;
        long probeMax = 0;
        for (String s : variants) {
            long[] r = ColdSearch.benchSig(p, scan, s, cfg, false, false, cancel);
            probeSum += r[0];
            probeMax = Math.max(probeMax, r[0]);
        }
        double probeAvgMs = probeSum / (double) variants.size() / 1e6;
        System.out.printf(Locale.ROOT, "SCREEN (probe only): %d sigs, avg=%.3f ms/sig, max=%.3f ms => %.0f sigs/sec%n",
                variants.size(), probeAvgMs, probeMax / 1e6, 1000.0 / probeAvgMs);

        int certN = Math.min(20, variants.size());
        for (boolean joint : new boolean[] {false, true}) {
            long certSum = 0;
            long certMax = 0;
            for (int i = 0; i < certN; i++) {
                long[] r = ColdSearch.benchSig(p, scan, variants.get(i), cfg, true, joint, cancel);
                certSum += r[1];
                certMax = Math.max(certMax, r[1]);
            }
            System.out.printf(Locale.ROOT,
                    "CERTIFY MISS jointFallback=%-5b (%d sigs): avg=%.1f ms, max=%.1f ms => %.1f sigs/sec%n",
                    joint, certN, certSum / (double) certN / 1e6, certMax / 1e6,
                    1000.0 / (certSum / (double) certN / 1e6));
        }

        long[] land = ColdSearch.benchSig(p, scan, landSig, cfg, true, false, cancel);
        System.out.printf(Locale.ROOT, "CERTIFY LAND (the solving sig): probe=%.1f ms certify=%.1f ms solved=%d%n",
                land[0] / 1e6, land[1] / 1e6, (int) land[2]);

        System.out.println("-- bucket-RADIUS x budget sweep (missAvg over 20 junk, land still solves?) --");
        int[] radii = {40, 20, 12, 8, 5, 3};
        int[] budgets = {6, 3, 1};
        ColdSearch.BUCKET_LP_MAX = 5.0e-2;
        for (int rad : radii) {
            for (int b : budgets) {
                ColdSearch.BUCKET_SWEEP_RADIUS = rad;
                ColdSearch.BUCKET_SLICE_BUDGET = b;
                long cs = 0;
                long cmax = 0;
                for (int i = 0; i < certN; i++) {
                    long[] r = ColdSearch.benchSig(p, scan, variants.get(i), cfg, true, false, cancel);
                    cs += r[1];
                    cmax = Math.max(cmax, r[1]);
                }
                long[] ld = ColdSearch.benchSig(p, scan, landSig, cfg, true, false, cancel);
                System.out.printf(Locale.ROOT,
                        "  radius=%2d budget=%d : missAvg=%6.1f ms missMax=%7.1f ms landSolved=%d land=%.1f ms%n",
                        rad, b, cs / (double) certN / 1e6, cmax / 1e6, (int) ld[2], ld[1] / 1e6);
            }
        }
        ColdSearch.BUCKET_SWEEP_RADIUS = 40;
        ColdSearch.BUCKET_SLICE_BUDGET = 30;
        ColdSearch.BUCKET_LP_MAX = 5.0e-2;

        double cx = 0.5 * (p.rectXLo + p.rectXHi);
        double cz = 0.5 * (p.rectZLo + p.rectZHi);
        KeyLine kl = new KeyLine(p, mk, hd, KeyLine.WA);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec spec =
                LineSpec.build(kl, -76.627464, cx, cz);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = spec.asScenario();
        int nn = sc.numTicks;
        double[] yaws = new double[nn];
        java.util.Arrays.fill(yaws, -76.627464);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.Compiled compiled =
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.compile(spec);
        int iters = 20000;
        long tv = System.nanoTime();
        double acc = 0;
        for (int i = 0; i < iters; i++) {
            double[] gf = sc.toGameFacings(yaws);
            de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath fp = p.model.forward(sc, gf);
            acc += compiled.maxViolation(gf, fp);
        }
        double verifyUs = (System.nanoTime() - tv) / 1e3 / iters;
        System.out.printf(Locale.ROOT, "RAW verify (forward+constraint check, fixed inputs+yaws): %.4f ms = %.2f us/call (acc=%.1f)%n",
                verifyUs / 1000.0, verifyUs, acc);

        int biters = 500;
        long tb = System.nanoTime();
        for (int i = 0; i < biters; i++) {
            LineSpec.build(kl, -76.627464, cx, cz);
        }
        double buildMs = (System.nanoTime() - tb) / 1e6 / biters;
        System.out.printf(Locale.ROOT, "SPEC build (LineSpec.build -> new AngleSolverEngine + debugBuildSpec): %.3f ms/call%n", buildMs);
    }
}
