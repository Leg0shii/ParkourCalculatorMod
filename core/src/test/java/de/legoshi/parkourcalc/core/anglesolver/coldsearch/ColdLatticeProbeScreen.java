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

public class ColdLatticeProbeScreen {

    private static final double BUCKET_DEG = (180.0 / Math.PI) / 10430.378350470453;

    @Test
    public void latticeSensitivity() throws Exception {
        String path = System.getenv("PKC_COLD_LATTICE_FILE");
        Assume.assumeTrue("set PKC_COLD_LATTICE_FILE", path != null && !path.isEmpty());
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

        double[] baseYaws = new double[n];
        for (int k = 0; k < n; k++) {
            int t = startTick + k + 1;
            baseYaws[k] = t < file.debug.size() ? file.debug.get(t).yaw : file.debug.get(file.debug.size() - 1).yaw;
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);

        System.out.printf(Locale.ROOT, "bucketDeg=%.6f numTicks=%d%n", BUCKET_DEG, n);
        double base = maxViol(sc, model, compiled, baseYaws);
        System.out.printf(Locale.ROOT, "human line maxViol=%.6e (>=0 means feasible-slack, i.e. clearance)%n", base);
        System.out.printf(Locale.ROOT, "human worst constraint clearance=%.6e (%s)%n",
                worstClear(spec, sc, model, baseYaws), worstName(spec, sc, model, baseYaws));

        int lastMom = -1;
        for (int k = 0; k < n; k++) {
            if (Math.abs(baseYaws[k] - baseYaws[0]) < 1e-6) lastMom = k;
            else break;
        }
        System.out.printf(Locale.ROOT, "momentum held ticks 0..%d at yaw=%.6f%n", lastMom, baseYaws[0]);

        int[] buckets = {-4, -2, -1, 1, 2, 4};
        System.out.println("--- perturb the held momentum facing (all momentum ticks together) ---");
        for (int b : buckets) {
            double[] y = baseYaws.clone();
            for (int k = 0; k <= lastMom; k++) y[k] += b * BUCKET_DEG;
            double v = maxViol(sc, model, compiled, y);
            System.out.printf(Locale.ROOT, "  momentum %+d bucket (%+.5f deg): maxViol=%.6e %s%n",
                    b, b * BUCKET_DEG, v, v <= 0 ? "FEASIBLE" : "VIOLATES");
        }
        System.out.println("--- perturb each air yaw by +-1 bucket ---");
        for (int k = lastMom + 1; k < n; k++) {
            double[] yp = baseYaws.clone();
            double[] ym = baseYaws.clone();
            yp[k] += BUCKET_DEG;
            ym[k] -= BUCKET_DEG;
            double vp = maxViol(sc, model, compiled, yp);
            double vm = maxViol(sc, model, compiled, ym);
            System.out.printf(Locale.ROOT, "  air tick %2d (yaw=%.4f): +1=%.4e %s | -1=%.4e %s%n",
                    startTick + k + 1, baseYaws[k], vp, vp <= 0 ? "OK" : "VIOL", vm, vm <= 0 ? "OK" : "VIOL");
        }
    }

    private static double maxViol(JumpPhysicsInputs sc, ExactJumpModel model,
                                  JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gf);
        return compiled.maxViolation(gf, path);
    }

    private static double worstClear(JumpSpec spec, JumpPhysicsInputs sc, ExactJumpModel model, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gf);
        double worst = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            double e = Math.abs(JumpConstraintCompiler.evaluate(c, gf, path));
            if (s <= 0.0 && e < worst) worst = e;
        }
        return worst;
    }

    private static String worstName(JumpSpec spec, JumpPhysicsInputs sc, ExactJumpModel model, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gf);
        double worst = Double.POSITIVE_INFINITY;
        String name = "-";
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, path);
            double e = Math.abs(JumpConstraintCompiler.evaluate(c, gf, path));
            if (s <= 0.0 && e < worst) {
                worst = e;
                name = c.name;
            }
        }
        return name;
    }
}
