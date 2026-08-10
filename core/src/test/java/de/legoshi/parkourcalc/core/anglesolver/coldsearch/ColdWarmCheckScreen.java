package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class ColdWarmCheckScreen {

    @Test
    public void humanLineThroughFunnel() throws Exception {
        String path = System.getenv("PKC_COLD_WARMCHECK_FILE");
        Assume.assumeTrue("set PKC_COLD_WARMCHECK_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);

        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        boolean sprintPrev = false;
        int changes = 0;
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(p.startTick + k);
            boolean w = hasKey(row, "W");
            boolean a = hasKey(row, "A");
            boolean s = hasKey(row, "S");
            boolean d = hasKey(row, "D");
            boolean sprintKey = hasKey(row, "SPRINT");
            int fwd = (w ? 1 : 0) - (s ? 1 : 0);
            int str = (a ? 1 : 0) - (d ? 1 : 0);
            moveKey[k] = comboOf(fwd, str);
            boolean canRun = KeyLine.canRun(moveKey[k]);
            boolean h = !canRun ? false : (sprintPrev ? true : sprintKey);
            hold[k] = h;
            boolean sprintCur = canRun && (sprintPrev || h);
            if (k > 0 && (moveKey[k] != moveKey[k - 1] || hold[k] != hold[k - 1])) changes++;
            sprintPrev = sprintCur;
        }
        KeyLine line = new KeyLine(p, moveKey, hold);
        System.out.printf(Locale.ROOT, "human sig=%s%n", line.signature());
        System.out.printf(Locale.ROOT, "changes(level)=%d lastPress=%d%n", changes, p.lastPressSeg);
        System.out.println("line: " + line.describe());

        ColdSearch.Config cfg = new ColdSearch.Config();
        long t0 = System.nanoTime();
        ColdResult r = ColdSearch.certifyLine(file, line.signature(), cfg);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf(Locale.ROOT, "certifyLine: %s in %d ms lastDirect=[%s]%n",
                r != null && r.solved() ? "SOLVED" : "MISS", ms, ColdSearch.lastDirectDebug);
        if (r != null && r.solved()) {
            System.out.println(r.summary());
            return;
        }

        java.util.List<double[]> open = new java.util.ArrayList<double[]>();
        for (double th = -180.0; th < 180.0; th += 0.25) {
            ColdSearch.Sweep s = new ColdSearch.Sweep(p, cfg, th, 0, null);
            double[] t = s.traceLine(moveKey, hold);
            double w = Math.min(t[1] - t[0], t[3] - t[2]);
            if (w >= 0.0) open.add(new double[] {th, w});
        }
        System.out.printf(Locale.ROOT, "open theta windows: %d points%n", open.size());
        java.util.Collections.sort(open, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(b[1], a[1]);
            }
        });
        String thetaEnv = System.getenv("PKC_COLD_WARMCHECK_THETA");
        double thBest = thetaEnv != null && !thetaEnv.isEmpty() ? Double.parseDouble(thetaEnv)
                : (open.isEmpty() ? 0.0 : open.get(0)[0]);
        ColdSearch.Sweep sBest = new ColdSearch.Sweep(p, cfg, thBest, 0, null);
        double[] tr = sBest.traceLine(moveKey, hold);
        double cxr = 0.5 * (p.rectXLo + p.rectXHi);
        double czr = 0.5 * (p.rectZLo + p.rectZHi);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec full = LineSpec.build(line, thBest, cxr, czr);
        de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = full.asScenario();
        double[] held = new double[sc.numTicks];
        java.util.Arrays.fill(held, thBest);
        double[] gf = sc.toGameFacings(held);
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath fp = p.model.forward(sc, gf);
        int lp = p.lastPressSeg;
        System.out.printf(Locale.ROOT,
                "model-vs-trace at theta=%.2f: model entry pos=(%.6f,%.6f) vel=(%.6f,%.6f)%n",
                thBest, fp.posX[lp], fp.posZ[lp], fp.velX[lp], fp.velZ[lp]);
        System.out.printf(Locale.ROOT,
                "                     trace entry pos=(%.6f,%.6f) vel=(%.6f,%.6f) (start=center)%n",
                cxr + tr[6], czr + tr[7], tr[4], tr[5]);
        System.out.printf(Locale.ROOT, "  trace rect X[%.4f,%.4f] Z[%.4f,%.4f]%n", tr[0], tr[1], tr[2], tr[3]);
        int[] boxTicks = {4, 17, 30};
        for (int bt : boxTicks) {
            if (bt < fp.posX.length) {
                System.out.printf(Locale.ROOT, "  model pos@%d=(%.6f, %.6f)%n", bt, fp.posX[bt], fp.posZ[bt]);
            }
        }

        for (int i = 0; i < Math.min(8, open.size()); i++) {
            double th = open.get(i)[0];
            ColdSearch.Sweep s = new ColdSearch.Sweep(p, cfg, th, 0, null);
            double[] t = s.traceLine(moveKey, hold);
            double refX = 0.5 * (t[0] + t[1]) + t[6];
            double refZ = 0.5 * (t[2] + t[3]) + t[7];
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec slice = ColdSearch.buildSliceSpec(
                    p, line, th, refX, refZ, t[0] + t[6], t[1] + t[6], t[2] + t[7], t[3] + t[7], t[4], t[5]);
            double v = ColdSearch.dualScreenViol(p, slice);
            System.out.printf(Locale.ROOT,
                    "  theta=%.2f width=%.4f entryBoxX[%.4f,%.4f] Z[%.4f,%.4f] v=(%.3f,%.3f) dualViol=%s%n",
                    th, open.get(i)[1], t[0] + t[6], t[1] + t[6], t[2] + t[7], t[3] + t[7], t[4], t[5],
                    Double.isNaN(v) ? "NaN" : String.format(Locale.ROOT, "%.4e", v));
        }
    }

    private static boolean hasKey(SaveFile.Row row, String key) {
        return row != null && row.keys != null && row.keys.contains(key);
    }

    private static int comboOf(int fwd, int str) {
        for (int c = 0; c < KeyLine.COMBO_COUNT; c++) {
            if (KeyLine.FORWARD_SIGN[c] == fwd && KeyLine.STRAFE_SIGN[c] == str) return c;
        }
        return KeyLine.NONE;
    }
}
