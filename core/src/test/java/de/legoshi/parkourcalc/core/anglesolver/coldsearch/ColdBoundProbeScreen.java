package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * M-A go/no-go: probe the LP-relaxation lower bound {@link ColdBound#lowerBoundX} against human oracle lines.
 * VALIDATION ONLY: derives keys / momentum facing / objective from the human capture. Checks the bound is a
 * true lower bound (<= human landing X) and TIGHTENS toward it as keys fix and the facing interval shrinks.
 * Set PKC_COLD_BOUND_FILES=&lt;a.json,b.json,...&gt; (comma-separated) to run several captures in one pass.
 */
public class ColdBoundProbeScreen {

    @Test
    public void boundProbe() throws Exception {
        String files = System.getenv("PKC_COLD_BOUND_FILES");
        if (files == null || files.isEmpty()) files = System.getenv("PKC_COLD_BOUND_FILE");
        Assume.assumeTrue("set PKC_COLD_BOUND_FILES=<a.json,b.json,...>", files != null && !files.isEmpty());
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
        JsonObject res = as.getAsJsonObject("result");
        double objectiveValue = res.get("objectiveValue").getAsDouble();
        JsonArray startPos = root.getAsJsonObject("start").getAsJsonArray("pos");
        double startX = startPos.get(0).getAsDouble();
        double startZ = startPos.get(2).getAsDouble();
        double startYaw = root.getAsJsonObject("start").get("yaw").getAsDouble();

        Map<Integer, Double> yawByTick = new HashMap<Integer, Double>();
        for (com.google.gson.JsonElement e : res.getAsJsonArray("yaws")) {
            JsonObject o = e.getAsJsonObject();
            yawByTick.put(o.get("tick").getAsInt(), o.get("yaw").getAsDouble());
        }

        int nT = p.numTicks;
        int last = p.lastPressSeg;
        int[] keyAll = new int[nT];
        boolean[] sprintAll = new boolean[nT];
        boolean sprintPrev = false;
        for (int k = 0; k < nT; k++) {
            SaveFile.Row row = file.rows.get(p.startTick + k);
            int fwd = (has(row, "W") ? 1 : 0) - (has(row, "S") ? 1 : 0);
            int str = (has(row, "A") ? 1 : 0) - (has(row, "D") ? 1 : 0);
            int combo = comboOf(fwd, str);
            keyAll[k] = combo;
            boolean canRun = KeyLine.canRun(combo);
            boolean h = canRun && (sprintPrev || has(row, "SPRINT"));
            boolean sprintCur = canRun && (sprintPrev || h);
            sprintAll[k] = sprintCur;
            sprintPrev = sprintCur;
        }
        double thetaDeg = yawByTick.containsKey(1) ? yawByTick.get(1) : startYaw;

        System.out.printf(Locale.ROOT, "%n========== %s ==========%n", new File(path).getName());
        System.out.printf(Locale.ROOT,
                "nT=%d last(lastPress)=%d presses=%s momentumTheta=%.6f rectX=[%.5f,%.5f] objectiveValue(humanX)=%.7f%n",
                nT, last, Arrays.toString(p.pressSegTicks), thetaDeg, p.rectXLo, p.rectXHi, objectiveValue);

        // --- Sanity 1: momentum-phase form (pos[last]) vs fixed-theta byte-exact model ---
        double formEntryX = b.posXAtEntry(startX, thetaDeg, keyAll, sprintAll);
        double formEntryZ = b.posZAtEntry(startZ, thetaDeg, keyAll, sprintAll);
        int n = last + 1;
        int[] mk = Arrays.copyOf(keyAll, n);
        boolean[] hd = new boolean[n];
        for (int k = 0; k < n; k++) hd[k] = sprintAll[k];
        KeyLine line = new KeyLine(p, mk, hd);
        JumpSpec spec = LineSpec.build(line, thetaDeg, startX, startZ);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] held = new double[sc.numTicks];
        Arrays.fill(held, thetaDeg);
        ForwardPath fp = p.model.forward(sc, sc.toGameFacings(held));
        System.out.printf(Locale.ROOT,
                "SANITY entry pos[%d]: form=(%.6f,%.6f) model=(%.6f,%.6f) dX=%.2e dZ=%.2e%n",
                last, formEntryX, formEntryZ, fp.posX[last], fp.posZ[last],
                formEntryX - fp.posX[last], formEntryZ - fp.posZ[last]);
        double[][] trOff = b.forwardTrace(startX, startZ, thetaDeg, keyAll, sprintAll, false);
        double[][] trOn = b.forwardTrace(startX, startZ, thetaDeg, keyAll, sprintAll, true);
        double maxOff = 0.0;
        double maxOn = 0.0;
        int gateFires = 0;
        System.out.println("  per-tick divergence (form vs model): tick combo slip gateOnX gateOffX modelX");
        for (int k = 0; k <= last; k++) {
            double dOn = Math.abs(trOn[0][k] - fp.posX[k]) + Math.abs(trOn[1][k] - fp.posZ[k]);
            double dOff = Math.abs(trOff[0][k] - fp.posX[k]) + Math.abs(trOff[1][k] - fp.posZ[k]);
            maxOn = Math.max(maxOn, dOn);
            maxOff = Math.max(maxOff, dOff);
            boolean fired = k < last && (Math.abs(trOn[2][k]) < p.model.inertiaThreshold()
                    || Math.abs(trOn[3][k]) < p.model.inertiaThreshold());
            if (fired) gateFires++;
            if (k <= last && (dOn > 1.0e-4 || dOff > 1.0e-4 || fired)) {
                System.out.printf(Locale.ROOT,
                        "    t=%2d k=%d slip=%.2f v=(%.5f,%.5f) gateOnX=%.6f gateOffX=%.6f modelX=%.6f dOn=%.2e dOff=%.2e%s%n",
                        k, k <= last ? keyAll[k] : -1, p.slip[Math.min(k, nT - 1)],
                        trOn[2][k], trOn[3][k], trOn[0][k], trOff[0][k], fp.posX[k], dOn, dOff, fired ? " GATE" : "");
            }
        }
        System.out.printf(Locale.ROOT, "  gateFires=%d maxDiv(gateOn)=%.2e maxDiv(gateOff)=%.2e%n",
                gateFires, maxOn, maxOff);

        // --- Sanity 2: full-line linearized landing X vs objectiveValue (resolve tick mapping) ---
        for (int shift = 1; shift >= 0; shift--) {
            double[] yaw = new double[nT];
            for (int k = 0; k < nT; k++) {
                Double y = yawByTick.get(k + shift);
                yaw[k] = y != null ? y : thetaDeg;
            }
            double lx = b.landingXAt(startX, yaw, keyAll, sprintAll);
            System.out.printf(Locale.ROOT,
                    "SANITY landingX(form, tickShift=%d)=%.7f  vs objectiveValue=%.7f  d=%.2e%n",
                    shift, lx, objectiveValue, lx - objectiveValue);
        }
        System.out.printf(Locale.ROOT, "gateMargin(threshold*sumGains)=%.5f%n", b.gateMargin());

        double humanX = objectiveValue;

        // --- Root lower bound: everything free, full circle ---
        double rootLB = b.lowerBoundX(keyAll, sprintAll, 0, -Math.PI, Math.PI, null);
        System.out.printf(Locale.ROOT, "ROOT LB (d=0, full circle) = %.6f   range=[%.6f, %.6f] span=%.6f%n",
                rootLB, rootLB, humanX, humanX - rootLB);

        boolean trueLbOk = rootLB <= humanX + 1.0e-6;
        double prevLB = rootLB;
        boolean monotone = true;

        // --- Table 1: fix keys 0..d-1, facing arc = human theta +/- 3 degrees ---
        System.out.println("-- key-depth sweep (arc = theta +/- 3 deg) --");
        System.out.printf(Locale.ROOT, "  %4s %12s %12s %8s%n", "d", "LB", "gap", "frac");
        double thRad = Math.toRadians(thetaDeg);
        double half1 = Math.toRadians(3.0);
        int halfDepth = last / 2;
        double fracAtHalf1 = Double.NaN;
        for (int d = 0; d <= last; d += Math.max(1, last / 10)) {
            double lb = b.lowerBoundX(keyAll, sprintAll, d, thRad - half1, thRad + half1, null);
            double frac = (lb - rootLB) / (humanX - rootLB);
            if (lb > humanX + 1.0e-6) trueLbOk = false;
            System.out.printf(Locale.ROOT, "  %4d %12.6f %12.6f %8.3f%n", d, lb, humanX - lb, frac);
            if (d >= halfDepth && Double.isNaN(fracAtHalf1)) fracAtHalf1 = frac;
        }

        // --- Table 2: combined shrink (keys + facing interval together) ---
        System.out.println("-- combined shrink (keys + arc together) --");
        System.out.printf(Locale.ROOT, "  %4s %10s %12s %12s %8s%n", "d", "halfDeg", "LB", "gap", "frac");
        double fracAtHalf2 = Double.NaN;
        double maxHalfDeg = 15.0;
        double minHalfDeg = 0.5 * ColdSearchYawBucketDeg();
        for (int d = 0; d <= last; d += Math.max(1, last / 10)) {
            double frac0 = last == 0 ? 1.0 : (double) d / last;
            double halfDeg = maxHalfDeg * (1.0 - frac0) + minHalfDeg;
            double half = Math.toRadians(halfDeg);
            double lb = b.lowerBoundX(keyAll, sprintAll, d, thRad - half, thRad + half, null);
            double frac = (lb - rootLB) / (humanX - rootLB);
            if (lb > humanX + 1.0e-6) trueLbOk = false;
            if (lb < prevLB - 1.0e-6) monotone = false;
            prevLB = lb;
            System.out.printf(Locale.ROOT, "  %4d %10.4f %12.6f %12.6f %8.3f%n", d, halfDeg, lb, humanX - lb, frac);
            if (d >= halfDepth && Double.isNaN(fracAtHalf2)) fracAtHalf2 = frac;
        }

        // --- Floor: all momentum fixed + facing narrowed to one bucket ---
        double oneBucket = Math.toRadians(minHalfDeg);
        double floorLB = b.lowerBoundX(keyAll, sprintAll, last, thRad - oneBucket, thRad + oneBucket, null);
        System.out.printf(Locale.ROOT, "FLOOR (d=last, 1-bucket arc) LB = %.6f  gap-to-human = %.6f%n",
                floorLB, humanX - floorLB);

        String verdict = (trueLbOk && fracAtHalf1 >= 0.5) ? "GO" : "NO-GO";
        System.out.printf(Locale.ROOT,
                "VERDICT %s: trueLB=%b monotone=%b frac@half(keyOnly)=%.3f frac@half(combined)=%.3f%n",
                verdict, trueLbOk, monotone, fracAtHalf1, fracAtHalf2);
    }

    private static double ColdSearchYawBucketDeg() {
        return (180.0 / Math.PI) / 10430.378350470453;
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
