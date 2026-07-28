package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.ui.BoxController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Batch replay of generated saves through the real simulator: loads every save in a directory,
 *  lets the loader's SimulatorEntity run its rows, then checks that the sim trajectory fulfills the
 *  save's full constraint set and stays on the model's predicted path. The final gate for solver or
 *  simplifier outputs; a headless cold-verify is model self-agreement, not verification. */
final class SimVerifyBatch {

    static final double MATCH_TOL = 1.0e-9;
    static final double CONSTRAINT_TOL = 1.0e-9;

    private SimVerifyBatch() {
    }

    static String run(Application app, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return "simverify: no such directory: " + dir;
        }
        FileSystemSaveStore original = app.getSaveStore();
        FileSystemSaveStore batchStore = new FileSystemSaveStore(dir,
                original != null ? original.getModVersion() : "?",
                original != null ? original.getMcVersion() : null,
                null);
        List<SaveInfo> saves = batchStore.list();
        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.ROOT, "%-52s %-8s %-7s %-12s %-7s %-10s%n",
                "save", "verdict", "met", "worstViol", "driftT", "maxDrift"));
        int pass = 0;
        int fail = 0;
        int skip = 0;
        app.setSaveStore(batchStore);
        try {
            for (SaveInfo info : saves) {
                Line line = verifyOne(app, info.name);
                if ("PASS".equals(line.verdict)) {
                    pass++;
                } else if ("FAIL".equals(line.verdict)) {
                    fail++;
                } else {
                    skip++;
                }
                report.append(line.text).append('\n');
            }
        } finally {
            app.setSaveStore(original);
            app.saves().discardCurrent();
        }
        String summary = "simverify: " + pass + " PASS, " + fail + " FAIL, " + skip + " skipped of " + saves.size();
        report.append(summary).append('\n');
        try {
            Files.write(dir.resolve("simverify-report.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            summary += " (report write failed: " + e.getMessage() + ")";
        }
        return summary;
    }

    private static final class Line {
        final String verdict;
        final String text;

        Line(String verdict, String text) {
            this.verdict = verdict;
            this.text = text;
        }
    }

    private static Line line(String name, String verdict, String detail) {
        return new Line(verdict, String.format(Locale.ROOT, "%-52s %-8s %s", name, verdict, detail));
    }

    private static Line verifyOne(Application app, String name) {
        Result<SaveFile> loaded = app.saves().load(name);
        if (!loaded.ok) {
            return line(name, "ERROR", loaded.error);
        }
        SaveFile f = loaded.value;
        if (f.angleSolver == null || f.angleSolver.result == null) {
            return line(name, "SKIP", "no solve result");
        }
        int startTick = f.angleSolver.startTick;
        int n = f.angleSolver.landingTick - startTick;
        if (n <= 0 || startTick < 0) {
            return line(name, "SKIP", "empty solve segment");
        }
        BoxController boxes = app.getBoxController();
        if (boxes.size() <= startTick + n) {
            return line(name, "FAIL", "sim produced " + boxes.size() + " states, segment needs "
                    + (startTick + n + 1));
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(f.mcVersion);
        AngleSolverEngine probe = new AngleSolverEngine(app.getAngleSolverState(), boxes, app.inputs(), t -> {
        }, model);
        JumpSpec spec = probe.debugBuildSpec();
        if (spec == null) {
            return line(name, "FAIL", "engine built no spec from the loaded state");
        }
        JumpPhysicsInputs sc = spec.asScenario();
        if (sc.numTicks != n) {
            return line(name, "FAIL", "spec ticks " + sc.numTicks + " != segment " + n);
        }
        double[] gf = new double[n];
        for (int k = 0; k < n; k++) {
            gf[k] = boxes.getYaw(startTick + k + 1);
        }
        double[] px = new double[n + 1];
        double[] py = new double[n + 1];
        double[] pz = new double[n + 1];
        for (int k = 0; k <= n; k++) {
            TickState s = boxes.getState(startTick + k);
            if (s == null || s.position == null) {
                return line(name, "FAIL", "missing sim state at tick " + (startTick + k));
            }
            px[k] = s.position.x;
            py[k] = s.position.y;
            pz[k] = s.position.z;
        }
        ForwardPath simPath = new ForwardPath(px, py, pz);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int met = 0;
        int total = 0;
        for (JumpConstraint c : compiled.ineq) {
            double e = JumpConstraintCompiler.evaluate(c, gf, simPath);
            double margin = c.cmp == JumpConstraint.Cmp.GE ? e : -e;
            total++;
            if (margin >= -CONSTRAINT_TOL) {
                met++;
            }
        }
        for (JumpConstraint c : compiled.eq) {
            double e = JumpConstraintCompiler.evaluate(c, gf, simPath);
            total++;
            if (Math.abs(e) <= CONSTRAINT_TOL) {
                met++;
            }
        }
        double worst = compiled.maxViolation(gf, simPath);
        ForwardPath modelPath = model.forward(sc, gf);
        int driftTick = -1;
        double maxDrift = 0.0;
        for (int k = 1; k <= n; k++) {
            double dx = (px[k] - px[k - 1]) - (modelPath.posX[k] - modelPath.posX[k - 1]);
            double dz = (pz[k] - pz[k - 1]) - (modelPath.posZ[k] - modelPath.posZ[k - 1]);
            double d = Math.max(Math.abs(dx), Math.abs(dz));
            if (d > maxDrift) {
                maxDrift = d;
            }
            if (d > MATCH_TOL && driftTick < 0) {
                driftTick = startTick + k;
            }
        }
        boolean pass = met == total && worst <= 0.0 && driftTick < 0;
        String detail = String.format(Locale.ROOT, "%3d/%-3d %12.3e %-7s %10.3e",
                met, total, worst, driftTick < 0 ? "-" : "T" + (driftTick + 1), maxDrift);
        return line(name, pass ? "PASS" : "FAIL", detail);
    }
}
