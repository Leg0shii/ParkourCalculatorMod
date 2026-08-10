package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class CaptureSanityScreen {

    @Test
    public void storedLineSatisfiesConstraints() throws Exception {
        String path = System.getenv("PKC_CAPTURE_SANITY_FILE");
        Assume.assumeTrue("set PKC_CAPTURE_SANITY_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state,
                de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file), inputs, t -> {
        }, model);
        JumpSpec spec = engine.debugBuildSpec();
        Assume.assumeTrue("spec build failed", spec != null);
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int startTick = state.getStartTick();

        double[] yaws = new double[n];
        for (int k = 0; k < n; k++) {
            int t = startTick + k + 1;
            yaws[k] = t < file.debug.size() ? file.debug.get(t).yaw : file.debug.get(file.debug.size() - 1).yaw;
        }
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath pathF = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double viol = compiled.maxViolation(gf, pathF);
        System.out.printf(Locale.ROOT, "capture=%s numTicks=%d constraints=%d startBox=%s%n",
                new File(path).getName(), n, spec.constraints.size(),
                sc.startBox == null ? "null" : sc.startBox.label());
        System.out.printf(Locale.ROOT, "stored line maxViolation=%.6e%n", viol);
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, pathF);
            if (s > 0.0) {
                System.out.printf(Locale.ROOT, "  VIOLATED %s by %.6e%n", c.name, s);
            }
        }
        double worstClear = Double.POSITIVE_INFINITY;
        String worstName = "-";
        for (JumpConstraint c : spec.constraints) {
            double e = Math.abs(JumpConstraintCompiler.evaluate(c, gf, pathF));
            double s = JumpConstraintCompiler.slack(c, gf, pathF);
            if (s <= 0.0 && e < worstClear) {
                worstClear = e;
                worstName = c.name;
            }
        }
        System.out.printf(Locale.ROOT, "tightest satisfied: %s clearance=%.6e%n", worstName, worstClear);

        String traceTicks = System.getenv("PKC_CAPTURE_SANITY_TRACE");
        if (traceTicks != null && !traceTicks.isEmpty()) {
            for (String t : traceTicks.split(",")) {
                int k = Integer.parseInt(t.trim());
                if (k < 0 || k > n) continue;
                System.out.printf(Locale.ROOT, "  t%-3d pos=(%.6f, %.6f) F=%s%n",
                        startTick + k, pathF.posX[k], pathF.posZ[k],
                        k < n ? String.format(Locale.ROOT, "%.4f", gf[k]) : "-");
            }
        }
    }
}
