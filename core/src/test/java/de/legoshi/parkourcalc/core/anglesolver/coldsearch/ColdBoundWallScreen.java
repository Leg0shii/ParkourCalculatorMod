package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Wall-aware bound go/no-go: the same objective lower bound as {@link ColdBound#lowerBoundXFromState} but fed
 * with ArcSweep's real wall-constrained form propagation (via {@link ArcSweep#walkStatesPerTick}). This is the
 * bound the B&B tree would evaluate at each internal node. VALIDATION ONLY (human keyline + objective).
 * PKC_COLD_BOUND_FILES=&lt;a.json,b.json,...&gt;.
 */
public class ColdBoundWallScreen {

    @Test
    public void wallBoundProbe() throws Exception {
        String files = System.getenv("PKC_COLD_BOUND_FILES");
        if (files == null || files.isEmpty()) files = System.getenv("PKC_COLD_BOUND_FILE");
        Assume.assumeTrue("set PKC_COLD_BOUND_FILES=<a.json,...>", files != null && !files.isEmpty());
        for (String path : files.split(",")) {
            path = path.trim();
            if (path.isEmpty()) continue;
            try {
                runOne(path);
            } catch (Exception e) {
                System.out.printf(Locale.ROOT, "%n### %s FAILED: %s%n", path, e);
                e.printStackTrace();
            }
        }
    }

    private void runOne(String path) throws Exception {
        String raw = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(raw);
        ColdProblem p = ColdProblem.fromSave(file);
        ColdBound b = new ColdBound(p);
        JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
        JsonObject as = root.getAsJsonObject("angleSolver");
        JsonObject res = as.has("result") && as.get("result").isJsonObject() ? as.getAsJsonObject("result") : null;
        double humanX = res != null && res.has("objectiveValue")
                ? res.get("objectiveValue").getAsDouble() : Double.NaN;

        int nT = p.numTicks;
        int last = p.lastPressSeg;
        int n = last + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        boolean sprintPrev = false;
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(p.startTick + k);
            int fwd = (has(row, "W") ? 1 : 0) - (has(row, "S") ? 1 : 0);
            int str = (has(row, "A") ? 1 : 0) - (has(row, "D") ? 1 : 0);
            int combo = comboOf(fwd, str);
            mk[k] = combo;
            boolean canRun = KeyLine.canRun(combo);
            boolean h = canRun && (sprintPrev || has(row, "SPRINT"));
            hd[k] = h;
            sprintPrev = canRun && (sprintPrev || h);
        }

        ColdSearch.Config cfg = new ColdSearch.Config();
        ArcSweep sweep = new ArcSweep(p, cfg, 0, null);
        List<List<ArcSweep.ArcState>> perTick = sweep.walkStatesPerTick(mk, hd);

        System.out.printf(Locale.ROOT, "%n========== WALL %s ==========%n", new File(path).getName());
        System.out.printf(Locale.ROOT, "nT=%d last=%d humanX=%.7f rectX=[%.5f,%.5f]%n",
                nT, last, humanX, p.rectXLo, p.rectXHi);

        double rootFree = b.lowerBoundX(mk2full(mk, nT), sprintFull(mk, hd, nT), 0, -Math.PI, Math.PI, null);
        double span = humanX - rootFree;
        System.out.printf(Locale.ROOT, "rootFreeLB=%.6f span=%.6f%n", rootFree, span);
        System.out.printf(Locale.ROOT, "  %4s %8s %10s %12s %12s %8s%n",
                "d", "states", "arcDeg", "wallLB", "gap", "frac");

        int halfDepth = last / 2;
        double fracAtHalf = Double.NaN;
        boolean trueLbOk = true;
        for (int d = 1; d <= last; d++) {
            List<ArcSweep.ArcState> states = perTick.get(d - 1);
            if (states.isEmpty()) {
                System.out.printf(Locale.ROOT, "  %4d %8s (no feasible state at human prefix)%n", d, "0");
                continue;
            }
            double lb = Double.POSITIVE_INFINITY;
            double arcDeg = 0.0;
            for (ArcSweep.ArcState st : states) {
                double v = b.lowerBoundXFromState(st.lowerX, st.dxs, st.dxc, st.vxs, st.vxc, d, st.arcs);
                if (v < lb) {
                    lb = v;
                    arcDeg = Math.toDegrees(st.arcs.totalLength());
                }
            }
            double frac = (lb - rootFree) / span;
            if (!Double.isNaN(humanX) && lb > humanX + 1.0e-4) trueLbOk = false;
            if (d % Math.max(1, last / 14) == 0 || d == last) {
                System.out.printf(Locale.ROOT, "  %4d %8d %10.4f %12.6f %12.6f %8.3f%n",
                        d, states.size(), arcDeg, lb, humanX - lb, frac);
            }
            if (d >= halfDepth && Double.isNaN(fracAtHalf)) fracAtHalf = frac;
        }
        String verdict = (trueLbOk && fracAtHalf >= 0.5) ? "GO" : "NO-GO";
        System.out.printf(Locale.ROOT, "WALL VERDICT %s: trueLB=%b frac@half=%.3f%n", verdict, trueLbOk, fracAtHalf);
    }

    private static int[] mk2full(int[] mk, int nT) {
        int[] full = new int[nT];
        for (int k = 0; k < nT; k++) full[k] = k < mk.length ? mk[k] : KeyLine.WA;
        return full;
    }

    private static boolean[] sprintFull(int[] mk, boolean[] hd, int nT) {
        boolean[] s = new boolean[nT];
        boolean on = false;
        for (int k = 0; k < nT; k++) {
            int combo = k < mk.length ? mk[k] : KeyLine.WA;
            boolean hold = k < hd.length ? hd[k] : true;
            boolean canRun = KeyLine.canRun(combo);
            if (!canRun) on = false;
            else if (!on && hold) on = true;
            s[k] = on;
        }
        return s;
    }

    private static boolean has(SaveFile.Row row, String key) {
        return row != null && row.keys != null && row.keys.contains(key);
    }

    private static int comboOf(int fwd, int str) {
        for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
            if (KeyLine.FORWARD_SIGN[c] == fwd && KeyLine.STRAFE_SIGN[c] == str) return c;
        }
        return KeyLine.NONE;
    }
}
