package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

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
import java.util.concurrent.atomic.AtomicBoolean;

public class ColdEntryMapScreen {

    @Test
    public void entryVelocityMap() throws Exception {
        String path = System.getenv("PKC_COLD_ENTRYMAP_FILE");
        Assume.assumeTrue("set PKC_COLD_ENTRYMAP_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        AtomicBoolean cancel = new AtomicBoolean(false);
        double theta = Double.parseDouble(env("PKC_COLD_ENTRYMAP_THETA", "0"));
        double vMax = Double.parseDouble(env("PKC_COLD_ENTRYMAP_VMAX", "0.6"));
        double vStep = Double.parseDouble(env("PKC_COLD_ENTRYMAP_VSTEP", "0.05"));

        ColdProblem.Wall lastBoxX = null;
        ColdProblem.Wall lastBoxZ = null;
        for (ColdProblem.Wall w : p.momentumWalls) {
            if (w.lo == Double.NEGATIVE_INFINITY || w.hi == Double.POSITIVE_INFINITY) continue;
            if (w.axisX) {
                if (lastBoxX == null || w.segTick > lastBoxX.segTick) lastBoxX = w;
            } else {
                if (lastBoxZ == null || w.segTick > lastBoxZ.segTick) lastBoxZ = w;
            }
        }
        double exLo = lastBoxX != null ? lastBoxX.lo : p.rectXLo;
        double exHi = lastBoxX != null ? lastBoxX.hi : p.rectXHi;
        double ezLo = lastBoxZ != null ? lastBoxZ.lo : p.rectZLo;
        double ezHi = lastBoxZ != null ? lastBoxZ.hi : p.rectZHi;
        System.out.printf(Locale.ROOT, "entry pos box X[%.4f,%.4f] Z[%.4f,%.4f] theta=%.1f lastPress=%d%n",
                exLo, exHi, ezLo, ezHi, theta, p.lastPressSeg);

        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hold = new boolean[n];
        Arrays.fill(mk, KeyLine.W);
        Arrays.fill(hold, true);
        KeyLine line = new KeyLine(p, mk, hold, KeyLine.WA);

        double over = Double.parseDouble(env("PKC_COLD_ENTRYMAP_OVERHANG", "0"));
        double[][] entries;
        if (over > 0) {
            entries = new double[][] {
                    {exHi + 0.2, ezLo}, {exHi + 0.4, ezLo}, {exHi + over, ezLo},
                    {exHi + 0.2, ezHi}, {exHi + 0.4, ezHi}, {exHi + over, ezHi},
                    {exLo - 0.2, ezLo}, {exLo - 0.4, ezLo}, {exLo - over, ezLo},
                    {0.5 * (exLo + exHi), ezLo - 0.2}, {0.5 * (exLo + exHi), ezLo - over},
                    {0.5 * (exLo + exHi), ezHi + 0.2}, {0.5 * (exLo + exHi), ezHi + over}};
        } else {
            entries = new double[][] {{exLo, ezLo}, {exLo, ezHi}, {exHi, ezLo}, {exHi, ezHi},
                    {0.5 * (exLo + exHi), 0.5 * (ezLo + ezHi)}};
        }
        for (double[] epos : entries) {
            int feasible = 0;
            double bestViol = Double.POSITIVE_INFINITY;
            double bestVx = Double.NaN;
            double bestVz = Double.NaN;
            StringBuilder cells = new StringBuilder();
            for (double vx = -vMax; vx <= vMax + 1e-9; vx += vStep) {
                for (double vz = -vMax; vz <= vMax + 1e-9; vz += vStep) {
                    JumpSpec slice = ColdSearch.buildSliceSpec(p, line, theta,
                            epos[0], epos[1], epos[0], epos[0], epos[1], epos[1], vx, vz);
                    double v;
                    try {
                        de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve.Result r =
                                de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve
                                        .optimizeRobustGraded(p.model, slice, 0.0, cancel);
                        v = r == null ? Double.POSITIVE_INFINITY : (r.feasible ? 0.0 : r.violation);
                    } catch (RuntimeException ex) {
                        v = Double.POSITIVE_INFINITY;
                    }
                    if (v <= 0.0) {
                        feasible++;
                        cells.append(String.format(Locale.ROOT, " (%.2f,%.2f)", vx, vz));
                    }
                    if (v < bestViol) {
                        bestViol = v;
                        bestVx = vx;
                        bestVz = vz;
                    }
                }
            }
            System.out.printf(Locale.ROOT, "entry (%.4f, %.4f): feasibleCells=%d best=%.4e at v=(%.2f,%.2f)%n",
                    epos[0], epos[1], feasible, bestViol, bestVx, bestVz);
            if (cells.length() > 0) System.out.println("  cells:" + cells);
        }
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
