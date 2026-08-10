package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdScreenRankScreen {

    @Test
    public void rankTargetSig() throws Exception {
        String path = System.getenv("PKC_COLD_RANK_FILE");
        String sig = System.getenv("PKC_COLD_RANK_SIG");
        Assume.assumeTrue("set PKC_COLD_RANK_FILE and PKC_COLD_RANK_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        int level = Integer.parseInt(envOr("PKC_COLD_RANK_LEVEL", "3"));

        ColdSearch.Config cfg = new ColdSearch.Config();
        ColdSearch.SigCollector col = new ColdSearch.SigCollector(cfg);
        ArcSweep sweep = new ArcSweep(p, cfg, level, col);
        long t0 = System.nanoTime();
        sweep.run();
        System.out.printf(Locale.ROOT, "sweep level=%d emitted=%d distinct=%d nodes=%d trunc=%b ms=%d%n",
                level, col.emitted, col.perSig.size(), sweep.nodes, sweep.truncated,
                (System.nanoTime() - t0) / 1_000_000L);
        List<ColdSearch.Candidate> target = col.perSig.get(sig);
        System.out.println("target present: " + (target != null));
        if (target == null) return;

        AtomicBoolean cancel = new AtomicBoolean(false);
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[720];
        for (int si = 0; si < scan.length; si++) {
            scan[si] = new ColdSearch.Sweep(p, cfg, -180.0 + si * 0.5, 0, null);
        }
        double[] ts = ColdSearch.quickScoreSig(p, target, scan, cfg, cancel);
        ColdSearch.Candidate tc = target.get(0);
        StringBuilder ab = new StringBuilder();
        if (tc.arcsDeg != null) {
            for (int i = 0; i + 1 < tc.arcsDeg.length; i += 2) {
                ab.append(String.format(Locale.ROOT, " [%.3f,%.3f]", tc.arcsDeg[i], tc.arcsDeg[i + 1]));
            }
        }
        System.out.printf(Locale.ROOT, "target score=%.6e theta=%.4f arcs=%s%n", ts[0], ts[1], ab);
        if (tc.arcsDeg != null && tc.arcsDeg.length >= 2) {
            for (double th = tc.arcsDeg[0]; th <= tc.arcsDeg[tc.arcsDeg.length - 1] + 0.25; th += 0.5) {
                ColdSearch.Sweep sw = scan[Math.max(0, Math.min(scan.length - 1,
                        (int) Math.round((th + 180.0) / 0.5)))];
                double[] tr = sw.traceLine(tc.moveKey, tc.hold);
                double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
                double v = ColdSearch.probeAt(p, tc, cfg, w, tr, sw, sw.theta, cancel);
                System.out.printf(Locale.ROOT, "  profile theta=%7.2f width=%10.4f probe=%s%n",
                        sw.theta, w, Double.isInfinite(v) ? "Inf" : String.format(Locale.ROOT, "%.4e", v));
            }
        }

        long s0 = System.nanoTime();
        int better = 0;
        int done = 0;
        for (Map.Entry<String, List<ColdSearch.Candidate>> e : col.perSig.entrySet()) {
            if (e.getKey().equals(sig)) continue;
            double v = ColdSearch.quickScoreSig(p, e.getValue(), scan, cfg, cancel)[0];
            if (v < ts[0]) better++;
            done++;
            if (done % 25000 == 0) {
                System.out.printf(Locale.ROOT, "scored %d better=%d elapsedMs=%d%n",
                        done, better, (System.nanoTime() - s0) / 1_000_000L);
            }
        }
        System.out.printf(Locale.ROOT, "FINAL rank=%d of %d scoreMs=%d%n",
                better + 1, done + 1, (System.nanoTime() - s0) / 1_000_000L);
    }

    private static String envOr(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
