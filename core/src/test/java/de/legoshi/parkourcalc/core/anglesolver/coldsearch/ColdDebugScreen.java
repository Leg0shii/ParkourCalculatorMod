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

public class ColdDebugScreen {

    @Test
    public void dumpProblem() throws Exception {
        String path = System.getenv("PKC_COLD_DEBUG_FILE");
        Assume.assumeTrue("set PKC_COLD_DEBUG_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        System.out.printf(Locale.ROOT, "segment %d..%d lastPress=%d tied=%b%n",
                p.startTick, p.landingTick, p.lastPressSeg, p.lastPressYawTied);
        System.out.print("presses:");
        for (int t : p.pressSegTicks) System.out.print(" " + t);
        System.out.println();
        System.out.print("ground:");
        for (int k = 0; k < p.numTicks; k++) {
            if (p.ground[k]) System.out.print(" " + k);
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "rect X[%.4f,%.4f] Z[%.4f,%.4f]%n", p.rectXLo, p.rectXHi, p.rectZLo, p.rectZHi);
        for (ColdProblem.Wall w : p.momentumWalls) {
            System.out.printf(Locale.ROOT, "wall seg=%d axis=%s [%.4f,%.4f]%n",
                    w.segTick, w.axisX ? "X" : "Z", w.lo, w.hi);
        }
        for (ColdProblem.Wall w : p.tailWalls) {
            System.out.printf(Locale.ROOT, "tailWall seg=%d axis=%s [%.4f,%.4f]%n",
                    w.segTick, w.axisX ? "X" : "Z", w.lo, w.hi);
        }
        System.out.println("tailYawsFree=" + p.tailYawsFree);

        ColdSearch.Config cfg = new ColdSearch.Config();
        for (int level = 0; level <= 1; level++) {
            ColdSearch.SigCollector collector = new ColdSearch.SigCollector(cfg);
            long nodes = 0;
            for (double theta : new double[] {-180.0, -90.0, 0.0, 66.0, 90.0}) {
                int before = collector.emitted;
                ColdSearch.Sweep sweep = new ColdSearch.Sweep(p, cfg, theta, level, collector);
                sweep.run();
                nodes += sweep.nodes;
                System.out.printf(Locale.ROOT, "level=%d theta=%.0f emitted=%d nodes=%d trunc=%b%n",
                        level, theta, collector.emitted - before, sweep.nodes, sweep.truncated);
            }
            int shown = 0;
            for (Map.Entry<String, List<ColdSearch.Candidate>> e : collector.perSig.entrySet()) {
                if (shown++ >= 12) break;
                System.out.printf(Locale.ROOT, "  sig=%s kept=%d%n", e.getKey(), e.getValue().size());
            }
            System.out.printf(Locale.ROOT, "level=%d emitted=%d distinct=%d nodes=%d%n",
                    level, collector.emitted, collector.perSig.size(), nodes);
        }
    }
}
