package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic for the incremental (ArcSweep segment-constrained) solve path. Inert unless PKC_COLD_INCR is
 * set. PKC_COLD_INCR_STEM overrides the capture stem; PKC_COLD_INCR_ALPHA (comma names), PKC_COLD_INCR_CHG,
 * PKC_COLD_INCR_BUDGET_MS, PKC_COLD_INCR_BUCKET configure the run.
 */
public class ColdIncrementalSmokeScreen {

    @Test
    public void solveIncremental() {
        Assume.assumeTrue("set PKC_COLD_INCR=1", "1".equals(System.getenv("PKC_COLD_INCR")));
        String stem = env("PKC_COLD_INCR_STEM", "hpk_human/d11/j1150-2x2bm_Nix_Neo");
        SaveFile file = ColdTestHarness.loadSave(stem);

        int[] alpha = parseAlpha(env("PKC_COLD_INCR_ALPHA", "-,SA,WD,W"));
        int chg = Integer.parseInt(env("PKC_COLD_INCR_CHG", "3"));
        long budgetMs = Long.parseLong(env("PKC_COLD_INCR_BUDGET_MS", "900000"));
        int bucket = Integer.parseInt(env("PKC_COLD_INCR_BUCKET", "30"));

        PrintStream out = System.out;
        String logPath = System.getenv("PKC_COLD_INCR_LOG");
        if (logPath != null && !logPath.isEmpty()) {
            try {
                out = new PrintStream(new java.io.FileOutputStream(logPath, true), true, "UTF-8");
            } catch (Exception e) {
                out = System.out;
            }
        }

        ColdProblem p = ColdProblem.fromSave(file);
        int nSegs = p.pressSegTicks.length;
        out.printf(Locale.ROOT, "%s: segments=%d presses=%s lastPressSeg=%d numTicks=%d%n",
                stem, nSegs, java.util.Arrays.toString(p.pressSegTicks), p.lastPressSeg, p.numTicks);

        ColdSearch.Config cfg = new ColdSearch.Config();
        cfg.certifyCap = 500_000;
        cfg.timeBudgetMs = budgetMs;
        cfg.segAlphabet = new int[nSegs][];
        cfg.segMaxChanges = new int[nSegs];
        for (int i = 0; i < nSegs; i++) {
            cfg.segAlphabet[i] = alpha.clone();
            cfg.segMaxChanges[i] = chg;
        }

        int old = ColdSearch.BUCKET_SLICE_BUDGET;
        long t0 = System.nanoTime();
        ColdResult r;
        try {
            ColdSearch.BUCKET_SLICE_BUDGET = bucket;
            r = ColdSearch.solveConstrained(file, cfg, out, new AtomicBoolean(false));
        } finally {
            ColdSearch.BUCKET_SLICE_BUDGET = old;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        out.printf(Locale.ROOT, "RESULT solved=%b ms=%d%n", r != null && r.solved(), ms);
        if (r != null && r.solved()) {
            out.printf(Locale.ROOT, "SIG %s%nSTART (%.17g,%.17g) viol=%.3e%n",
                    r.line.signature(), r.startX, r.startZ, r.maxViolation);
        } else if (r != null) {
            out.println(r.summary());
        }
        out.flush();
    }

    @Test
    public void honestyMeasurement() {
        Assume.assumeTrue("set PKC_COLD_HONESTY=1", "1".equals(System.getenv("PKC_COLD_HONESTY")));
        String logPath = env("PKC_COLD_HONESTY_LOG",
                "C:\\Users\\benja\\AppData\\Local\\Temp\\claude\\coldlogs\\honesty.log");
        long budgetMs = Long.parseLong(env("PKC_COLD_HONESTY_BUDGET_MS", "480000"));
        int[] alpha = parseAlpha(env("PKC_COLD_HONESTY_ALPHA", "-,W,WA,WD,SA,SD"));
        int chg = Integer.parseInt(env("PKC_COLD_HONESTY_CHG", "2"));

        String[] stems = {
                "hpk_human/d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl",
                "hpk_human/d11/j1150-2x2bm_Nix_Neo",
                "hpk_human/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo",
                "hpk_human/d12/j154_1bm_Head_Butterfly_Neo",
        };

        PrintStream out;
        try {
            out = new PrintStream(new java.io.FileOutputStream(logPath, true), true, "UTF-8");
        } catch (Exception e) {
            out = System.out;
        }
        out.printf(Locale.ROOT, "== HONESTY (alpha=%s changes=%d budget=%ds, engage auto-swept) ==%n",
                java.util.Arrays.toString(alpha), chg, budgetMs / 1000);

        for (String stem : stems) {
            SaveFile file = ColdTestHarness.loadSave(stem);
            ColdProblem p = ColdProblem.fromSave(file);
            int nSegs = p.pressSegTicks.length;
            ColdSearch.Config cfg = new ColdSearch.Config();
            cfg.certifyCap = 500_000;
            cfg.timeBudgetMs = budgetMs;
            cfg.segAlphabet = new int[nSegs][];
            cfg.segMaxChanges = new int[nSegs];
            for (int i = 0; i < nSegs; i++) {
                cfg.segAlphabet[i] = alpha.clone();
                cfg.segMaxChanges[i] = chg;
            }
            int oldB = ColdSearch.BUCKET_SLICE_BUDGET;
            int bkt = Integer.parseInt(env("PKC_COLD_HONESTY_BUCKET", "30"));
            boolean verbose = "1".equals(System.getenv("PKC_COLD_HONESTY_VERBOSE"));
            long t0 = System.nanoTime();
            ColdResult r;
            try {
                ColdSearch.BUCKET_SLICE_BUDGET = bkt;
                out.printf(Locale.ROOT, "-- %s (segs=%d) --%n", stem, nSegs);
                r = ColdSearch.solveConstrained(file, cfg, verbose ? out : null, new AtomicBoolean(false));
            } catch (RuntimeException ex) {
                r = null;
            } finally {
                ColdSearch.BUCKET_SLICE_BUDGET = oldB;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            boolean solved = r != null && r.solved();
            out.printf(Locale.ROOT, "%-58s segs=%d -> %s in %ds%s%n",
                    stem, nSegs, solved ? "SOLVED" : "TIMEOUT/MISS", ms / 1000,
                    solved ? " sig=" + r.line.signature() : "");
        }
        out.println("== END HONESTY ==");
        out.flush();
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
