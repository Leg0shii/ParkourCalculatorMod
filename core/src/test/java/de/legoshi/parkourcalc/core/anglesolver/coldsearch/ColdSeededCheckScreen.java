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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdSeededCheckScreen {

    @Test
    public void seededEmission() throws Exception {
        String path = System.getenv("PKC_COLD_SEEDED_FILE");
        Assume.assumeTrue("set PKC_COLD_SEEDED_FILE=<capture.json>", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);

        int preCapLo = Integer.parseInt(env("PKC_COLD_SEEDED_PRECAP_LO", "0"));
        int preCapHi = Integer.parseInt(env("PKC_COLD_SEEDED_PRECAP_HI", "1"));
        int sufCap = Integer.parseInt(env("PKC_COLD_SEEDED_SUFCAP", "2"));
        long nodeCap = Long.parseLong(env("PKC_COLD_SEEDED_NODECAP", "3000000"));
        long budgetMs = Long.parseLong(env("PKC_COLD_SEEDED_BUDGET_MS", "240000"));

        ColdSearch.Config cfg = new ColdSearch.Config();
        AtomicBoolean cancel = new AtomicBoolean(false);
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;

        int steps = (int) Math.round(360.0 / 0.5);
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
        for (int si = 0; si < steps; si++) {
            scan[si] = new ColdSearch.Sweep(p, cfg, -180.0 + si * 0.5, 0, null);
        }

        List<StratPrefixes.Seed> seeds = StratPrefixes.generate(p);
        System.out.printf(Locale.ROOT, "seeds=%d lastPressSeg=%d numTicks=%d singleHeld=%b tailYawsFree=%b%n",
                seeds.size(), p.lastPressSeg, p.numTicks, p.singleHeld, p.tailYawsFree);

        for (int preCap = preCapLo; preCap <= preCapHi; preCap++) {
            long t0 = System.nanoTime();
            ColdSearch.SigCollector collector = new ColdSearch.SigCollector(cfg);
            long passNodes = 0;
            boolean trunc = false;
            for (StratPrefixes.Seed seed : seeds) {
                ArcSweep sweep = new ArcSweep(p, cfg, 0, collector);
                sweep.runSeeded(seed, preCap, sufCap, nodeCap);
                passNodes += sweep.nodes;
                trunc |= sweep.truncated;
                if (System.nanoTime() > deadline) {
                    trunc = true;
                    break;
                }
            }
            long sweepMs = (System.nanoTime() - t0) / 1_000_000L;

            double best = Double.POSITIVE_INFINITY;
            String bestSig = "-";
            int finite = 0;
            int scored = 0;
            long ps = System.nanoTime();
            List<Map.Entry<String, List<ColdSearch.Candidate>>> entries =
                    new ArrayList<Map.Entry<String, List<ColdSearch.Candidate>>>(collector.perSig.entrySet());
            for (Map.Entry<String, List<ColdSearch.Candidate>> e : entries) {
                double[] qs = ColdSearch.quickScoreSig(p, e.getValue(), scan, cfg, cancel);
                scored++;
                if (Double.isFinite(qs[0])) finite++;
                if (qs[0] < best) {
                    best = qs[0];
                    bestSig = e.getKey();
                }
                if (System.nanoTime() > deadline) break;
            }
            long probeMs = (System.nanoTime() - ps) / 1_000_000L;
            System.out.printf(Locale.ROOT,
                    "preCap=%d emitted=%d distinct=%d nodes=%d sweepMs=%d scored=%d finite=%d bestProbe=%.4e bestSig=%s probeMs=%d trunc=%b%n",
                    preCap, collector.emitted, collector.perSig.size(), passNodes, sweepMs,
                    scored, finite, best, bestSig, probeMs, trunc);
            if (System.nanoTime() > deadline) {
                System.out.println("(budget exhausted)");
                break;
            }
        }
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
