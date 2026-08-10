package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

public class ColdHeldDebugScreen {

    @Test
    public void heldScan() throws Exception {
        String path = System.getenv("PKC_COLD_HELD_FILE");
        String sig = System.getenv("PKC_COLD_HELD_SIG");
        Assume.assumeTrue("set PKC_COLD_HELD_FILE and PKC_COLD_HELD_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);

        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            mk[k] = sig.charAt(idx) - '0';
            hd[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        System.out.printf(Locale.ROOT, "tailYawsFree=%b lastPressSeg=%d numTicks=%d%n",
                p.tailYawsFree, p.lastPressSeg, p.numTicks);

        int[] tails = {KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.NONE, KeyLine.A, KeyLine.D};
        double cx = 0.5 * (p.rectXLo + p.rectXHi);
        double cz = 0.5 * (p.rectZLo + p.rectZHi);
        for (int tail : tails) {
            KeyLine line = new KeyLine(p, mk, hd, tail);
            JumpSpec spec = LineSpec.build(line, 0.0, cx, cz);
            if (spec == null) {
                System.out.printf(Locale.ROOT, "tail=%-3s spec=null%n", KeyLine.COMBO_LABEL[tail]);
                continue;
            }
            JumpLinearModel lin = new JumpLinearModel(spec.asScenario());
            FacingPrefold pre = FacingPrefold.analyze(spec.constraints, lin);
            FacingPrefold.ChainScan scan = FacingPrefold.scannable(spec.constraints, lin);
            int nn = spec.asScenario().numTicks;
            boolean allOpen = scan != null;
            if (scan != null) {
                for (int t = 0; t < nn; t++) {
                    if (!scan.openMember(t)) {
                        allOpen = false;
                        break;
                    }
                }
            }
            String guard;
            if (pre != null) guard = "analyze!=null(prefold ok, heldChainScan bails)";
            else if (scan == null) guard = "scannable=null";
            else if (!allOpen) guard = "not-all-openMember";
            else guard = "PASS";

            double bestViol = Double.POSITIVE_INFINITY;
            double bestTheta = Double.NaN;
            for (double th = -180.0; th < 180.0; th += 0.25) {
                double[] yaws = new double[nn];
                Arrays.fill(yaws, th);
                double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
                double v;
                if (rs != null) {
                    v = FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1]);
                } else {
                    v = FreeStartSolve.violationAt(p.model, spec, yaws, cx, cz);
                }
                if (v < bestViol) {
                    bestViol = v;
                    bestTheta = th;
                }
            }
            double[] stepsDeg = {0.05, 0.01, 0.002, 0.0005};
            for (double step : stepsDeg) {
                boolean improved = true;
                while (improved) {
                    improved = false;
                    for (int dir = -1; dir <= 1; dir += 2) {
                        double th = bestTheta + dir * step;
                        double[] yaws = new double[nn];
                        Arrays.fill(yaws, th);
                        double[] rs = FreeStartSolve.recoverStart(p.model, spec, yaws);
                        double v = rs != null
                                ? FreeStartSolve.violationAt(p.model, spec, yaws, rs[0], rs[1])
                                : FreeStartSolve.violationAt(p.model, spec, yaws, cx, cz);
                        if (v < bestViol) {
                            bestViol = v;
                            bestTheta = th;
                            improved = true;
                        }
                    }
                }
            }
            System.out.printf(Locale.ROOT,
                    "tail=%-3s guard=[%s] heldBestViol=%.6e @theta=%.4f (wrap %.4f)%n",
                    KeyLine.COMBO_LABEL[tail], guard, bestViol, bestTheta, Angles.wrap(bestTheta));
        }
    }
}
