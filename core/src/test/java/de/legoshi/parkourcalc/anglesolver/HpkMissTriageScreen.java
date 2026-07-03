package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BoundPrunedRecovery;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class HpkMissTriageScreen {

    private static final String[] MISSES = {
            "j335_1bmhh_Single_Fencegat_Butterfly_Neo",
            "j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo",
            "j717_Panewall_Momentum_Single_Block_Butterfly_Neo",
            "j828-1bm_5.3125-1.5",
    };

    private static final long BUDGET_NANOS = 30_000_000_000L;

    @Test
    public void clampFreeClosedFormCensus() {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        double[] margins = {0.0, 1.0e-4, 3.0e-4, 6.0e-4, 1.2e-3, 2.5e-3, 5.0e-3, 1.0e-2};
        int solved = 0;
        int total = 0;
        for (String name : de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog.problemNames("dualrecovery")) {
            ProblemFixture pf = ProblemFixture.load("dualrecovery", name);
            ExactJumpModel exact = pf.model;
            JumpSpec spec = pf.specFor(null, null);
            JumpPhysicsInputs sc = spec.asScenario();
            total++;
            if (de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.hasFacingWall(spec.constraints)) {
                System.out.printf("CFREE %-52s facing wall, skip%n", name);
                continue;
            }
            JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
            int n = sc.numTicks;
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel lin =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.Wall> walls =
                    lin.compileWalls(spec.constraints, 0.0, trivial);
            if (trivial[0]) {
                System.out.printf("CFREE %-52s trivial infeasible%n", name);
                continue;
            }
            de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver dual =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(n, cx, cz, lin.mMagAll(), walls);
            double[] warm = null;
            boolean ok = false;
            double bestViol = Double.POSITIVE_INFINITY;
            double margin = Double.NaN;
            boolean max = spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX;
            boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
            for (double m : margins) {
                de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.Result r = dual.solve(m, warm);
                if (r == null) break;
                warm = r.lambda;
                double[] yaws = new double[n];
                for (int t = 0; t < n; t++) {
                    double gx = r.gx[t];
                    double gz = r.gz[t];
                    if (gx * gx + gz * gz < 1.0e-18) {
                        gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                        gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
                    }
                    yaws[t] = lin.recoverYawDeg(t, gx, gz);
                }
                double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
                ForwardPath p = exact.forward(sc, gf);
                double viol = compiled.maxViolation(gf, p);
                if (viol < bestViol) bestViol = viol;
                if (viol <= 0.0) {
                    ok = true;
                    margin = m;
                    break;
                }
            }
            if (ok) solved++;
            System.out.printf("CFREE %-52s %s%s%n", name,
                    ok ? "solved margin=" + margin : "miss",
                    ok ? "" : String.format(" bestViol=%.3e", bestViol));
        }
        System.out.printf("CFREE census: %d/%d solve on the clamp-free closed form%n", solved, total);
    }

    @Test
    public void j716HandPattern() {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo");
        ExactJumpModel exact = pf.model;
        JumpSpec spec = pf.specFor(null, null);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        int n = sc.numTicks;
        double thr = exact.inertiaThreshold();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int[][] windows = {{10, 11}, {9, 11}, {10, 12}, {9, 12}};
        for (int[] w : windows) {
            boolean[] zeroX = new boolean[n];
            boolean[] zeroZ = new boolean[n];
            for (int t = w[0]; t < w[1]; t++) zeroZ[t] = true;
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel lin =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc, zeroX, zeroZ);
            double[] cx = new double[n];
            double[] cz = new double[n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            java.util.List<de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel.Wall> walls =
                    lin.compileWalls(spec.constraints, 0.0, trivial);
            if (trivial[0]) {
                System.out.printf("J716 wz[%d,%d) trivial infeasible%n", w[0], w[1]);
                continue;
            }
            walls.addAll(lin.velocityWalls(thr));
            de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver.Result r =
                    new de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver(n, cx, cz, lin.mMagAll(), walls)
                            .solve(0.0, null);
            if (r == null) {
                System.out.printf("J716 wz[%d,%d) dual unbounded%n", w[0], w[1]);
                continue;
            }
            double[] yaws = new double[n];
            for (int t = 0; t < n; t++) {
                double gx = r.gx[t];
                double gz = r.gz[t];
                if (gx * gx + gz * gz < 1.0e-18) {
                    gx = 1.0;
                    gz = 0.0;
                }
                yaws[t] = lin.recoverYawDeg(t, gx, gz);
            }
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath p = exact.forward(sc, gf);
            double dualViol = compiled.maxViolation(gf, p);
            double[] slp = de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve.optimizeBestEffort(
                    exact, spec, 0.0, cancel, yaws, 160, 220, true);
            double slpViol = Double.NaN;
            double slpObj = Double.NaN;
            if (slp != null) {
                double[] gf2 = sc.toGameFacings(Angles.wrapAll(slp));
                ForwardPath p2 = exact.forward(sc, gf2);
                slpViol = compiled.maxViolation(gf2, p2);
                slpObj = p2.getPos(spec.objective.tick, spec.objective.axis);
            }
            System.out.printf("J716 wz[%d,%d) bound=%.6f dualSeedViol=%.3e slpViol=%.3e slpObj=%.6f%n",
                    w[0], w[1], r.value, dualViol, slpViol, slpObj);
        }
    }

    @Test
    public void blindBnb() {
        org.junit.Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        for (String name : MISSES) {
            ProblemFixture pf = ProblemFixture.load("dualrecovery", name);
            ExactJumpModel exact = pf.model;
            JumpSpec spec = pf.specFor(null, null);
            JumpPhysicsInputs sc = spec.asScenario();
            JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
            AtomicBoolean cancel = new AtomicBoolean(false);
            boolean max = spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX;
            double firstFeasible = max ? -1.0e300 : 1.0e300;
            long t0 = System.nanoTime();
            double[] yaws = BoundPrunedRecovery.solve(exact, spec, 0.0, cancel, BUDGET_NANOS, firstFeasible);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (yaws == null) {
                System.out.printf("TRIAGE %-48s bnb=null  %d ms%n", name, ms);
                continue;
            }
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath p = exact.forward(sc, gf);
            double viol = compiled.maxViolation(gf, p);
            double obj = p.getPos(spec.objective.tick, spec.objective.axis);
            System.out.printf("TRIAGE %-48s viol=%.3e obj=%.6f solved=%s  %d ms%n",
                    name, viol, obj, viol <= 0.0, ms);
        }
    }
}
