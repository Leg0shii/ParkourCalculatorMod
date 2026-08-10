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

public class ColdSpeedDebugScreen {

    @Test
    public void fastestEntries() throws Exception {
        String path = System.getenv("PKC_COLD_SPEED_FILE");
        Assume.assumeTrue("set PKC_COLD_SPEED_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config cfg = new ColdSearch.Config();
        double theta = Double.parseDouble(env("PKC_COLD_SPEED_THETA", "66.5"));
        int level = Integer.parseInt(env("PKC_COLD_SPEED_LEVEL", "3"));

        ColdSearch.SigCollector collector = new ColdSearch.SigCollector(cfg);
        ColdSearch.Sweep sweep = new ColdSearch.Sweep(p, cfg, theta, level, collector);
        sweep.run();
        List<ColdSearch.Candidate> all = new ArrayList<ColdSearch.Candidate>();
        for (Map.Entry<String, List<ColdSearch.Candidate>> e : collector.perSig.entrySet()) {
            for (ColdSearch.Candidate c : e.getValue()) {
                if (c.theta == theta) {
                    all.add(c);
                    break;
                }
            }
        }
        Collections.sort(all, new Comparator<ColdSearch.Candidate>() {
            @Override
            public int compare(ColdSearch.Candidate a, ColdSearch.Candidate b) {
                return Double.compare(Math.hypot(b.vx, b.vz), Math.hypot(a.vx, a.vz));
            }
        });
        System.out.printf(Locale.ROOT, "theta=%.3f level=%d emitted=%d distinct=%d nodes=%d trunc=%b%n",
                theta, level, collector.emitted, all.size(), sweep.nodes, sweep.truncated);
        for (int i = 0; i < Math.min(25, all.size()); i++) {
            ColdSearch.Candidate c = all.get(i);
            System.out.printf(Locale.ROOT,
                    "  |v|=%.4f v=(%.4f,%.4f) open=%b d=(%.3f,%.3f) sig=%s%n",
                    Math.hypot(c.vx, c.vz), c.vx, c.vz, c.trueOpen, c.dx, c.dz, c.sig);
        }
        int fast = 0;
        for (ColdSearch.Candidate c : all) {
            if (Math.hypot(c.vx, c.vz) > 0.3) fast++;
        }
        System.out.printf(Locale.ROOT, "entries>0.3: %d of %d%n", fast, all.size());
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
