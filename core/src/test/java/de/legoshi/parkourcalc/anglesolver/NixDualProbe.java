package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public class NixDualProbe {

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE", path != null && !path.isEmpty());
        String outPath = System.getenv("PKC_OUT");
        PrintStream ps = outPath != null ? new PrintStream(new FileOutputStream(outPath), true, "UTF-8") : System.out;

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        BoxController boxes = de.legoshi.parkourcalc.anglesolver.harness.Fixtures.buildBoxes(file);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
        int landing = state.getLandingTick();

        for (int st : new int[]{24, 25}) {
            state.setStartTick(st);
            state.setLandingTick(landing);
            JumpSpec spec = engine.debugBuildSpec();
            JumpPhysicsInputs sc = spec.asScenario();
            ps.printf("==== startTick=%d n=%d (user %s) ====%n", st, sc.numTicks, st == 24 ? "t25 FAIL" : "t26 OK");

            ClosedFormSolve.Result rob = ClosedFormSolve.optimizeRobustGraded(model, spec, 0.0, new AtomicBoolean(false));
            report(ps, "  optimizeRobustGraded", model, spec, rob == null ? null : rob.yaws,
                    rob == null ? Double.NaN : rob.violation, rob != null && rob.feasible);

            double[] asc = ClosedFormSolve.optimize(model, spec, 0.0, new AtomicBoolean(false));
            report(ps, "  optimize(ascending)", model, spec, asc, Double.NaN, asc != null);
            ps.println();
        }
        if (outPath != null) { ps.flush(); ps.close(); }
    }

    private static void report(PrintStream ps, String label, ExactJumpModel model, JumpSpec spec,
                               double[] yaws, double reportedViol, boolean feasible) {
        if (yaws == null) {
            ps.printf("%s: null (no recovered primal)%n", label);
            return;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath p = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double v = comp.maxViolation(gf, p);
        ps.printf("%s: recoveredViol=%.5e reported=%.5e feasible=%s tick0yaw=%.4f%n",
                label, v, reportedViol, feasible, yaws[0]);
        // which constraints violated, and by how much (the residual the dual could not satisfy)
        double worst = 0; String worstName = "";
        for (JumpConstraint c : spec.constraints) {
            double slk = JumpConstraintCompiler.slack(c, gf, p);
            if (slk > 1.0e-6) {
                ps.printf("      VIOL %-4s t1=%-2d %s rhs=%.4f slack=%.5e%n", c.mode, c.t1, c.cmp, c.rhs, slk);
                if (slk > worst) { worst = slk; worstName = c.mode + "@t1=" + c.t1; }
            }
        }
        if (worst > 0) ps.printf("      -> worst residual %.5e on %s%n", worst, worstName);
    }
}
