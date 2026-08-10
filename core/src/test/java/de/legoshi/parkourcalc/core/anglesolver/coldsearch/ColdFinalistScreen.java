package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class ColdFinalistScreen {

    @Test
    public void engineOnLine() throws Exception {
        String path = System.getenv("PKC_COLD_FINALIST_FILE");
        String sig = System.getenv("PKC_COLD_FINALIST_SIG");
        Assume.assumeTrue("set PKC_COLD_FINALIST_FILE and PKC_COLD_FINALIST_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        double theta = Double.parseDouble(env("PKC_COLD_FINALIST_THETA", "66.5"));
        int seconds = Integer.parseInt(env("PKC_COLD_FINALIST_SECONDS", "90"));
        int tailCombo = Integer.parseInt(env("PKC_COLD_FINALIST_TAIL", String.valueOf(KeyLine.WA)));

        int n = p.lastPressSeg + 1;
        int[] moveKey = new int[n];
        boolean[] hold = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            moveKey[k] = sig.charAt(idx) - '0';
            hold[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        KeyLine line = new KeyLine(p, moveKey, hold, tailCombo);
        System.out.println("line: " + line.describe());

        double sx = 0.5 * (p.rectXLo + p.rectXHi);
        double sz = 0.5 * (p.rectZLo + p.rectZHi);
        InputData inputs = new InputData();
        inputs.getRows().clear();
        inputs.getRows().addAll(line.toRows());
        AngleSolverState st = new AngleSolverState();
        SaveIO.applyAngleSolverTo(p.solverOnly, st);
        st.clearResult();
        st.setEffort(AngleSolverState.Effort.THOROUGH);
        st.setOptimizeSeconds(seconds);
        BoxController boxes = LineSpec.buildBoxes(line, theta, sx, sz);
        AngleSolverEngine engine = new AngleSolverEngine(st, boxes, inputs, t -> {
        }, p.model);
        final Vec3dCore[] moved = new Vec3dCore[1];
        engine.setOnStartMoved(pos -> moved[0] = pos);
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + (seconds + 30) * 1000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(5);
        }
        if (engine.isSolving()) engine.cancel();
        engine.poll();
        SolveResult res = st.getResult();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf(Locale.ROOT, "engine result: %s solver=%s ms=%d%n",
                res == null ? "null" : (res.isSuccess() ? "SUCCESS" : "fail"),
                res == null ? "-" : res.getSolver(), ms);
        if (res == null || !res.isSuccess()) return;
        engine.apply();
        double[] yaws = new double[p.numTicks];
        java.util.Arrays.fill(yaws, theta);
        for (SolveResult.YawEntry ye : res.getYaws()) {
            int k = ye.tick - 1;
            if (k >= 0 && k < p.numTicks) yaws[k] = ye.yaw;
        }
        double fx = moved[0] != null ? moved[0].x : sx;
        double fz = moved[0] != null ? moved[0].z : sz;
        JumpSpec spec = LineSpec.build(line, theta, sx, sz);
        double v = FreeStartSolve.violationAt(p.model, spec, yaws, fx, fz);
        System.out.printf(Locale.ROOT, "verify viol=%.6e start=(%.7f,%.7f)%n", v, fx, fz);
        System.out.print("yaws:");
        for (int k = 0; k < yaws.length; k++) {
            System.out.printf(Locale.ROOT, " %.4f", yaws[k]);
        }
        System.out.println();
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? dflt : v;
    }
}
