package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NondetProbe {

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_DIAG_FILE");
        Assume.assumeTrue("set PKC_DIAG_FILE", path != null && !path.isEmpty());
        int runs = Integer.parseInt(System.getenv().getOrDefault("PKC_RUNS", "4"));
        int secs = Integer.parseInt(System.getenv().getOrDefault("PKC_SECS", "20"));

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        double recorded = file.angleSolver.result != null ? file.angleSolver.result.objectiveValue : Double.NaN;
        out.printf("recorded objective (this same build, in-game): %.6f%n", recorded);
        out.printf("running %d solves at %ds each...%n%n", runs, secs);

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < runs; i++) {
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            state.clearResult();
            state.setOptimizeSeconds(secs);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);

            long t0 = System.currentTimeMillis();
            engine.solve();
            long deadline = t0 + (secs + 15L) * 1000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(20);
            }
            engine.poll();
            SolveResult r = state.getResult();
            double obj = r != null && r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
            boolean ok = r != null && r.isSuccess();
            min = Math.min(min, obj);
            max = Math.max(max, obj);
            out.printf("run %d: success=%s obj=%.6f  chain=\"%s\"%n", i + 1, ok, obj, r != null ? r.getSolver() : "null");
        }
        out.printf("%nSPREAD across runs: min=%.6f max=%.6f range=%.6f%n", min, max, max - min);
        out.printf("(range > 0 = NON-DETERMINISTIC on identical build+input)%n");
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/nondet-probe.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }
}
