package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
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

/**
 * Writes a loadable mod save for a certified cold sibling: certifies PKC_COLD_EXPORT_SIG against
 * PKC_COLD_EXPORT_FILE, then emits a SaveFile whose start = the found feasible start and whose rows =
 * the line's per-tick keys + absolute-locked yaws (mirroring AngleSolverEngine.writeYawRows). Output to
 * PKC_COLD_EXPORT_OUT via the mod's own SaveIO.saveJson.
 */
public class ColdExportSiblingScreen {

    @Test
    public void export() throws Exception {
        String path = System.getenv("PKC_COLD_EXPORT_FILE");
        String sig = System.getenv("PKC_COLD_EXPORT_SIG");
        String out = System.getenv("PKC_COLD_EXPORT_OUT");
        Assume.assumeTrue("set PKC_COLD_EXPORT_FILE, PKC_COLD_EXPORT_SIG, PKC_COLD_EXPORT_OUT",
                path != null && sig != null && out != null && !path.isEmpty() && !sig.isEmpty() && !out.isEmpty());

        String raw = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(raw);
        ColdResult r = ColdSearch.certifyLine(file, sig, new ColdSearch.Config());
        if (r == null || !r.solved()) {
            throw new IllegalStateException("sibling did not certify: " + (r == null ? "null" : r.summary()));
        }
        KeyLine line = r.line;
        ColdProblem p = line.problem;
        double[] yaws = r.yaws;
        JumpPhysicsInputs sc = LineSpec.build(line, yaws[0], r.startX, r.startZ).asScenario();
        double[] gf = sc.toGameFacings(yaws);

        double startY = file.start != null && file.start.pos != null && file.start.pos.length > 1
                ? file.start.pos[1] : 0.0;
        double[] startVel = file.start != null && file.start.vel != null
                ? file.start.vel : new double[] {0.0, 0.0, 0.0};
        float startPitch = file.start != null && file.start.pitch != null ? file.start.pitch : 0.0F;

        List<SaveFile.Row> rows = new ArrayList<SaveFile.Row>();
        for (int t = 0; t <= p.landingTick; t++) {
            SaveFile.Row row = new SaveFile.Row();
            int seg = t - p.startTick;
            if (seg >= 0 && seg < p.numTicks) {
                int combo = line.comboAt(seg);
                if (KeyLine.FORWARD_SIGN[combo] > 0) row.keys.add("W");
                if (KeyLine.FORWARD_SIGN[combo] < 0) row.keys.add("S");
                if (KeyLine.STRAFE_SIGN[combo] > 0) row.keys.add("A");
                if (KeyLine.STRAFE_SIGN[combo] < 0) row.keys.add("D");
                if (line.holdAt(seg)) row.keys.add("SPRINT");
                if (line.isPress(seg)) row.keys.add("JUMP");
                row.yaw = (float) gf[seg];
                row.yawLocked = true;
            }
            rows.add(row);
        }
        file.rows = rows;

        SaveFile.Start s = new SaveFile.Start();
        s.pos = new double[] {r.startX, startY, r.startZ};
        s.vel = startVel;
        s.yaw = (float) gf[0];
        s.pitch = startPitch;
        file.start = s;
        if (file.angleSolver != null) {
            SaveFile.Start seed = new SaveFile.Start();
            seed.pos = new double[] {r.startX, startY, r.startZ};
            seed.vel = startVel;
            seed.yaw = (float) yaws[0];
            file.angleSolver.seed = seed;
            file.angleSolver.result = null;
        }

        String json = SaveIO.saveJson(file);
        Files.write(new File(out).toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.printf(Locale.ROOT, "EXPORTED sibling to %s%n", out);
        System.out.printf(Locale.ROOT, "  sig=%s tail=%s facing=%.6f start=(%.10f,%.10f) viol=%.3e rows=%d%n",
                sig, KeyLine.COMBO_LABEL[line.tailCombo], r.facingDeg, r.startX, r.startZ, r.maxViolation, rows.size());
        System.out.printf(Locale.ROOT, "  line: %s%n", line.describe());
    }
}
