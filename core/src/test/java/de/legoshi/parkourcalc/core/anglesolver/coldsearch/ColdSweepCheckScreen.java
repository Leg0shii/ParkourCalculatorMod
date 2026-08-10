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

public class ColdSweepCheckScreen {

    @Test
    public void sigEmission() throws Exception {
        String path = System.getenv("PKC_COLD_SWEEPCHECK_FILE");
        String sig = System.getenv("PKC_COLD_SWEEPCHECK_SIG");
        Assume.assumeTrue("set PKC_COLD_SWEEPCHECK_FILE and PKC_COLD_SWEEPCHECK_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        int level = Integer.parseInt(env("PKC_COLD_SWEEPCHECK_LEVEL", "7"));
        String thetas = env("PKC_COLD_SWEEPCHECK_THETAS", "-162,-161.5,-161,-160.5,-160");
        String widths = env("PKC_COLD_SWEEPCHECK_BEAMS", "3000,12000,48000");

        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            mk[k] = sig.charAt(idx) - '0';
            hd[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        for (String ts : thetas.split(",")) {
            double theta = Double.parseDouble(ts.trim());
            ColdSearch.Sweep sweep = new ColdSearch.Sweep(p, new ColdSearch.Config(), theta, 0, null);
            System.out.printf(Locale.ROOT, "walk theta=%-8.2f: %s%n", theta, sweep.walkLine(mk, hd));
        }

        for (String bw : widths.split(",")) {
            ColdSearch.Config acfg = new ColdSearch.Config();
            acfg.beamWidth = Integer.parseInt(bw.trim());
            ColdSearch.SigCollector arcCollector = new ColdSearch.SigCollector(acfg);
            ArcSweep arcSweep = new ArcSweep(p, acfg, level, arcCollector);
            arcSweep.debugTrace = new StringBuilder();
            arcSweep.dbgMk = mk;
            arcSweep.dbgHd = hd;
            long t0 = System.nanoTime();
            arcSweep.run();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean arcHit = arcCollector.perSig.containsKey(sig);
            System.out.printf(Locale.ROOT,
                    "ARC beam=%-6s level=%d emitted=%d distinct=%d nodes=%d trunc=%b ms=%d sig=%s%n",
                    bw.trim(), level, arcCollector.emitted, arcCollector.perSig.size(), arcSweep.nodes,
                    arcSweep.truncated, ms, arcHit ? "EMITTED" : "missing");
            System.out.print(arcSweep.debugTrace);
            if (arcHit) {
                ColdSearch.Candidate hc = arcCollector.perSig.get(sig).get(0);
                StringBuilder arcs = new StringBuilder();
                if (hc.arcsDeg != null) {
                    for (int i = 0; i + 1 < hc.arcsDeg.length; i += 2) {
                        arcs.append(String.format(Locale.ROOT, " [%.3f,%.3f]", hc.arcsDeg[i], hc.arcsDeg[i + 1]));
                    }
                }
                System.out.println("  arcs:" + arcs);
            }
        }

        for (String bw : widths.split(",")) {
            ColdSearch.Config cfg = new ColdSearch.Config();
            cfg.beamWidth = Integer.parseInt(bw.trim());
            for (String ts : thetas.split(",")) {
                double theta = Double.parseDouble(ts.trim());
                ColdSearch.SigCollector collector = new ColdSearch.SigCollector(cfg);
                ColdSearch.Sweep sweep = new ColdSearch.Sweep(p, cfg, theta, level, collector);
                sweep.run();
                boolean hit = collector.perSig.containsKey(sig);
                int prefixMatch = 0;
                String probe = sig.substring(0, Math.min(sig.length(), 20));
                for (Map.Entry<String, List<ColdSearch.Candidate>> e : collector.perSig.entrySet()) {
                    if (e.getKey().startsWith(probe)) prefixMatch++;
                }
                System.out.printf(Locale.ROOT,
                        "beam=%-6s theta=%-8.2f level=%d emitted=%d distinct=%d nodes=%d trunc=%b sig=%s prefix20=%d%n",
                        bw.trim(), theta, level, collector.emitted, collector.perSig.size(), sweep.nodes,
                        sweep.truncated, hit ? "EMITTED" : "missing", prefixMatch);
            }
        }
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
