package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ContinuousDiscreteScreen {

    private static final double FLOOR = 0.01;

    @Test
    public void screen() throws Exception {
        String path = System.getenv("PKC_DIAG_FILE");
        Assume.assumeTrue("set PKC_DIAG_FILE", path != null && !path.isEmpty());
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class, ExactJumpModel.class);
        build.setAccessible(true);
        JumpSpec spec = (JumpSpec) build.invoke(null, file, exact);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        int n = sc.numTicks;
        double anchor = sc.startYaw;
        boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        boolean max = spec.objective.sense == Objective.Sense.MAX;

        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        CostateDualSolver dual = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls);
        CostateDualSolver.Result r = dual.solve(0.0, null);

        out.printf("=== thousand/1: continuous relaxed optimum vs its byte-exact realization ===%n");
        out.printf("dual iters=%d pgres=%.3e (converged if pgres<1e-8)%n", dual.lastIters, dual.lastPgres);
        double contBound = ClosedFormSolve.dualBound(spec);
        out.printf("continuous dual bound (relaxed LP max X): %.6f%n", contBound);

        // Recover the continuous optimum's per-tick inputs u* = m_t * g_t/||g_t||, and the continuous X it gives.
        double[] ux = new double[n];
        double[] uz = new double[n];
        double[] yaws = new double[n];
        double[] mMag = lin.mMagAll();
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            if (gx * gx + gz * gz < 1.0e-18) {
                gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
            }
            double inv = mMag[t] / Math.sqrt(gx * gx + gz * gz);
            ux[t] = gx * inv;
            uz[t] = gz * inv;
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        int objTick = spec.objective.tick;
        int objAxis = axisX ? 0 : 1;
        double contX = lin.constPos(objTick, objAxis);
        for (int s = 0; s < n; s++) contX += lin.coef(s, objTick) * (objAxis == 0 ? ux[s] : uz[s]);
        double contWorst = 0.0;
        for (JumpLinearModel.Wall w : walls) {
            double au = 0.0;
            for (int s = 0; s < n; s++) au += w.coef[s] * (w.axis == 0 ? ux[s] : uz[s]);
            double g = au - w.bPrime;
            contWorst = Math.max(contWorst, w.eq ? Math.abs(g) : g);
        }

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath disc = exact.forward(sc, gf);
        double discX = disc.getPos(objTick, spec.objective.axis);
        double discViol = comp.maxViolation(gf, disc);

        // Also: the CLAMP-FREE continuous model (same yaws, no inertia gate) to separate quantization from inertia.
        ForwardPath sineOnly = new ExactJumpModel(0.0, true, !exact.perAxisInertia()).forward(sc, gf);
        double sineX = sineOnly.getPos(objTick, spec.objective.axis);

        out.printf("%n%-40s %-14s %-12s %-8s%n", "model", "X", "worstViol", "revs");
        out.printf("%-40s %-14.6f %-12.2e %-8d%n", "continuous LP (recovered u*)", contX, contWorst, reversals(anchor, Angles.wrapAll(yaws)));
        out.printf("%-40s %-14.6f %-12s %-8s%n", "  quantized only (buckets, no inertia)", sineX, "-", "-");
        out.printf("%-40s %-14.6f %-12.2e %-8d%n", "  byte-exact (buckets + inertia)", discX, discViol, reversals(anchor, Angles.wrapAll(yaws)));
        out.printf("%ndrop continuous->byte-exact X: %.6f blocks%n", contX - discX);
        out.printf("of which quantization: %.6f ; inertia+feasibility: %.6f%n", contX - sineX, sineX - discX);
        out.println();
        out.println("INTERPRETATION:");
        out.println(" - if byte-exact worstViol is ~1e-4 and X ~ continuous: bound TIGHT, solver leaves distance on table (search bug)");
        out.println(" - if byte-exact worstViol is >>1e-4 (0.1+): the continuous LP optimum is not physically realizable => bound LOOSE");

        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/cont-vs-disc.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
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
}
