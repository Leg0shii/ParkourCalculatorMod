package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
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
import java.util.List;

public class ThousandDiagScreen {

    private static final double FLOOR = 0.01;

    @Test
    public void diag() throws Exception {
        String path = System.getenv("PKC_DIAG_FILE");
        Assume.assumeTrue("set PKC_DIAG_FILE", path != null && !path.isEmpty());
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        try {
            SolverTrace.enable("thousand");
            run(path, out);
        } finally {
            SolverTrace.disable();
            out.flush();
            File dst = new File("build/reports/thousand-diag.txt");
            dst.getParentFile().mkdirs();
            Files.write(dst.toPath(), sw.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(sw);
        }
    }

    private void run(String path, PrintWriter out) throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);

        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();

        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);

        JumpSpec spec = engine.debugBuildSpec();
        double anchor = spec.asScenario().startYaw;
        out.printf("effort=%s optimizeSeconds=%d axis=%s goal=%s startTick=%d landingTick=%d n=%d constraints=%d%n",
                state.getEffort(), state.getOptimizeSeconds(), state.getAxis(), state.getGoal(),
                state.getStartTick(), state.getLandingTick(), spec.asScenario().numTicks, spec.constraints.size());

        double bound = ClosedFormSolve.dualBound(spec);
        double boundIgnoringFacing = ClosedFormSolve.dualBoundIgnoringFacing(spec);
        out.printf("DUAL BOUND (max X upper bound, fixed seed start): %.6f%n", bound);
        out.printf("DUAL BOUND ignoring facing walls:                 %.6f%n", boundIgnoringFacing);
        out.printf("recorded objective in save:                       %s%n",
                file.angleSolver.result != null ? file.angleSolver.result.objectiveValue : Double.NaN);
        out.println("(if the dual bound is >= the target, a feasible path reaching it provably exists)");

        long t0 = System.currentTimeMillis();
        engine.solve();
        long deadline = t0 + 75_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(20);
        }
        engine.poll();
        long ms = System.currentTimeMillis() - t0;

        SolveResult r = state.getResult();
        out.printf("%n=== RE-SOLVE RESULT (this build) after %d ms ===%n", ms);
        if (r == null) {
            out.println("NULL result (engine still solving or produced nothing)");
            return;
        }
        out.printf("success=%s met=%d/%d solver=\"%s\" obj=%s%n",
                r.isSuccess(), r.getMet(), r.getTotal(), r.getSolver(),
                r.hasObjective() ? String.format("%.6f", r.getObjectiveValue()) : "none");
        double recorded = file.angleSolver.result != null ? file.angleSolver.result.objectiveValue : Double.NaN;
        if (r.hasObjective() && !Double.isNaN(recorded)) {
            out.printf("DELTA vs recorded save: %.6f  (%s)%n", r.getObjectiveValue() - recorded,
                    r.getObjectiveValue() + 1e-9 >= recorded ? "matched or beat" : "REGRESSED below recorded solution");
        }

        List<SolveResult.YawEntry> ys = r.getYaws();
        if (ys != null && !ys.isEmpty()) {
            double[] y = new double[ys.size()];
            for (int i = 0; i < ys.size(); i++) y[i] = ys.get(i).yaw;
            out.printf("re-solved path: reversals=%d headingChanges=%d%n", reversals(anchor, y), changes(anchor, y));
            out.println("re-solved yaws:");
            for (int i = 0; i < y.length; i++) out.printf("  t=%2d yaw=%9.3f%n", i, y[i]);
        }
        out.println("\nFull step-by-step trace written to build/reports/solver-trace-thousand.txt");
    }

    private static int reversals(double anchor, double[] y) {
        int c = 0, last = 0;
        double prev = anchor;
        for (double v : y) {
            double d = Angles.wrapDelta(v - prev);
            prev = v;
            if (Math.abs(d) <= FLOOR) continue;
            int s = d > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }

    private static int changes(double anchor, double[] y) {
        int c = 0;
        double prev = anchor;
        for (double v : y) {
            if (Math.abs(Angles.wrapDelta(v - prev)) > FLOOR) c++;
            prev = v;
        }
        return c;
    }
}
