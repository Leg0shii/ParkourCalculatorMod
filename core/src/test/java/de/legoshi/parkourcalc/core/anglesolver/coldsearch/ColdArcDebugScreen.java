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

public class ColdArcDebugScreen {

    @Test
    public void arcVsScalar() throws Exception {
        String path = System.getenv("PKC_COLD_ARCDEBUG_FILE");
        Assume.assumeTrue("set PKC_COLD_ARCDEBUG_FILE", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config cfg = new ColdSearch.Config();
        int n = p.lastPressSeg + 1;

        List<String> sigs = new ArrayList<String>();
        StringBuilder allW = new StringBuilder();
        for (int k = 0; k < n; k++) allW.append("1+");
        sigs.add(allW.toString());
        String sigEnv = System.getenv("PKC_COLD_ARCDEBUG_SIG");
        if (sigEnv != null && !sigEnv.isEmpty()) sigs.add(sigEnv);

        for (String sig : sigs) {
            int[] mk = new int[n];
            boolean[] hd = new boolean[n];
            for (int k = 0; k < n; k++) {
                mk[k] = sig.charAt(2 * k) - '0';
                hd[k] = sig.charAt(2 * k + 1) == '+';
            }
            System.out.println("=== sig " + sig);
            ArcSweep arc = new ArcSweep(p, cfg, 0, null);
            List<List<ArcSweep.ArcState>> perTick = arc.walkStatesPerTick(mk, hd);
            for (int k = 0; k < perTick.size(); k++) {
                List<ArcSweep.ArcState> st = perTick.get(k);
                double total = 0;
                for (ArcSweep.ArcState s : st) total += s.arcs.totalLength();
                System.out.printf(Locale.ROOT, "  after tick %2d: states=%d arcTotalDeg=%.6f%n",
                        k, st.size(), Math.toDegrees(total));
                if (st.isEmpty()) break;
            }
            List<ArcSweep.ArcState> fin = perTick.isEmpty() ? new ArrayList<ArcSweep.ArcState>()
                    : perTick.get(perTick.size() - 1);
            int both = 0;
            int scalarOnly = 0;
            int arcOnly = 0;
            List<Double> misses = new ArrayList<Double>();
            for (double deg = -180.0; deg < 180.0; deg += 0.25) {
                boolean scalarOk = new ColdSearch.Sweep(p, cfg, deg, 0, null).walkLine(mk, hd)
                        .startsWith("SURVIVES");
                boolean arcOk = ArcSweep.anyContains(fin, Math.toRadians(deg));
                if (scalarOk && arcOk) both++;
                else if (scalarOk) {
                    scalarOnly++;
                    if (misses.size() < 12) misses.add(deg);
                } else if (arcOk) arcOnly++;
            }
            System.out.printf(Locale.ROOT, "  grid: both=%d scalarOnly=%d arcOnly=%d%n", both, scalarOnly, arcOnly);
            if (!misses.isEmpty()) {
                System.out.println("  scalarOnly thetas: " + misses);
                double rad = Math.toRadians(misses.get(0));
                for (int k = 0; k < perTick.size(); k++) {
                    if (!ArcSweep.anyContains(perTick.get(k), rad)) {
                        System.out.printf(Locale.ROOT, "  theta %.3f lost after tick %d%n", misses.get(0), k);
                        break;
                    }
                }
            }
        }
    }
}
