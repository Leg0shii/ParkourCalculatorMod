package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Measures whether the byte-exact tail-feasible ENTRY region at tick=lastPress is thin. If it is, the momentum
 * search can target a tight terminal state (a strong prune) instead of the loose momentum checkpoints. Builds
 * the air slice from a fixed entry (pos,vel) at the human momentum facing and byte-exact solves it (free yaws).
 * VALIDATION ONLY: centers the grid on the human entry to characterize the region. PKC_COLD_BOUND_FILE(S).
 */
public class ColdEntryRegionScreen {

    @Test
    public void entryRegion() throws Exception {
        String files = System.getenv("PKC_COLD_BOUND_FILES");
        if (files == null || files.isEmpty()) files = System.getenv("PKC_COLD_BOUND_FILE");
        Assume.assumeTrue("set PKC_COLD_BOUND_FILE(S)", files != null && !files.isEmpty());
        for (String path : files.split(",")) {
            path = path.trim();
            if (!path.isEmpty()) runOne(path);
        }
    }

    private void runOne(String path) throws Exception {
        String raw = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(raw);
        ColdProblem p = ColdProblem.fromSave(file);
        JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
        double thetaDeg = root.getAsJsonObject("angleSolver").getAsJsonObject("result")
                .getAsJsonArray("yaws").get(0).getAsJsonObject().get("yaw").getAsDouble();
        JsonObject sp = root.getAsJsonObject("start");
        double startX = sp.getAsJsonArray("pos").get(0).getAsDouble();
        double startZ = sp.getAsJsonArray("pos").get(2).getAsDouble();

        int last = p.lastPressSeg;
        int n = last + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        boolean sprintPrev = false;
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(p.startTick + k);
            int fwd = (has(row, "W") ? 1 : 0) - (has(row, "S") ? 1 : 0);
            int str = (has(row, "A") ? 1 : 0) - (has(row, "D") ? 1 : 0);
            mk[k] = comboOf(fwd, str);
            boolean canRun = KeyLine.canRun(mk[k]);
            boolean h = canRun && (sprintPrev || has(row, "SPRINT"));
            hd[k] = h;
            sprintPrev = canRun && (sprintPrev || h);
        }
        KeyLine line = new KeyLine(p, mk, hd);
        JumpSpec full = LineSpec.build(line, thetaDeg, startX, startZ);
        JumpPhysicsInputs sc = full.asScenario();
        double[] held = new double[sc.numTicks];
        Arrays.fill(held, thetaDeg);
        ForwardPath fp = p.model.forward(sc, sc.toGameFacings(held));
        double eX = fp.posX[last];
        double eZ = fp.posZ[last];
        double eVx = fp.velX[last];
        double eVz = fp.velZ[last];

        System.out.printf(Locale.ROOT, "%n===== ENTRY REGION %s =====%n", new File(path).getName());
        System.out.printf(Locale.ROOT, "theta=%.5f humanEntry@%d pos=(%.6f,%.6f) vel=(%.6f,%.6f) feasible@human=%b%n",
                thetaDeg, last, eX, eZ, eVx, eVz, feasible(p, line, thetaDeg, eX, eZ, eVx, eVz));

        double[][] dims = {
                {eX, 0.02, 1.0, 0},   // posX: step, half-range-guess
                {eZ, 0.02, 1.0, 1},
                {eVx, 0.005, 0.2, 2},
                {eVz, 0.005, 0.2, 3},
        };
        String[] names = {"posX", "posZ", "velX", "velZ"};
        for (int di = 0; di < 4; di++) {
            double center = dims[di][0];
            double step = dims[di][1];
            double range = dims[di][2];
            double lo = Double.NaN;
            double hi = Double.NaN;
            int feasCount = 0;
            int total = 0;
            for (double off = -range; off <= range + 1e-9; off += step) {
                double v = center + off;
                double[] e = {eX, eZ, eVx, eVz};
                e[di] = v;
                total++;
                if (feasible(p, line, thetaDeg, e[0], e[1], e[2], e[3])) {
                    feasCount++;
                    if (Double.isNaN(lo)) lo = v;
                    hi = v;
                }
            }
            System.out.printf(Locale.ROOT, "  %-5s feasible over [%.5f, %.5f] width=%.5f (%d/%d samples, center=%.5f)%n",
                    names[di], lo, hi, Double.isNaN(lo) ? 0.0 : hi - lo, feasCount, total, center);
        }
    }

    private boolean feasible(ColdProblem p, KeyLine line, double theta,
                             double refX, double refZ, double vx, double vz) {
        double eps = 0.0015;
        JumpSpec slice = ColdSearch.buildSliceSpec(p, line, theta, refX, refZ,
                refX - eps, refX + eps, refZ - eps, refZ + eps, vx, vz);
        FreeStartSolve.Result r = FreeStartSolve.solve(p.model, slice, 0.0, new AtomicBoolean(false));
        if (r == null || !r.feasible) return false;
        double viol = FreeStartSolve.violationAt(p.model, slice, r.yaws, r.startX, r.startZ);
        return viol <= 0.0;
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
