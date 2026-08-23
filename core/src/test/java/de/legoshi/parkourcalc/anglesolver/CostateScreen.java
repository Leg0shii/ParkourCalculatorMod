package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
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
import java.util.ArrayList;
import java.util.List;

/**
 * gh-418 screen: the dual recovers each tick as u_t = m_t * g_t / sqrt(|g_t|^2 + EPS2). Where |g_t| is
 * small the direction is arbitrary and the magnitude falls below m_t, so the wall values the dual
 * believes are not the wall values the recovered yaws actually produce. This prints |g_t| per tick and
 * both wall evaluations side by side.
 */
public class CostateScreen {

    private static final double EPS2 = 1.0e-14;

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_SCREENS=1", "1".equals(System.getenv("PKC_SCREENS")));
        String path = System.getenv("PKC_CS_FILE");
        Assume.assumeTrue("set PKC_CS_FILE", path != null && !path.isEmpty());

        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()),
                StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        JumpLinearModel lin = new JumpLinearModel(sc);
        List<JumpConstraint> ineq = new ArrayList<JumpConstraint>();
        List<JumpLinearModel.Wall> walls = new ArrayList<JumpLinearModel.Wall>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            JumpLinearModel.Wall w = lin.compileWall(c, 0.0, null);
            if (w != null) {
                ineq.add(c);
                walls.add(w);
            }
        }
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);

        System.out.println("=== CS file=" + new File(path).getName() + " n=" + n + " walls=" + walls.size()
                + " EPS2=" + EPS2 + " ===");
        CostateDualSolver dual = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls);
        double margin = Double.parseDouble(System.getenv("PKC_CS_MARGIN") == null
                ? "0" : System.getenv("PKC_CS_MARGIN"));
        CostateDualSolver.Result r = dual.solve(margin, null);
        System.out.printf("CS margin=%.4g iters=%d pgres=%.4e stalled=%s dualValue=%.9f%n",
                margin, dual.lastIters, dual.lastPgres, dual.lastStalled, r.value);

        System.out.println("CS --- per tick: costate norm and the recovered magnitude ---");
        double[] ux = new double[n];
        double[] uz = new double[n];
        double[] yaw = new double[n];
        int degenerate = 0;
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t];
            double gz = r.gz[t];
            double nrm = Math.sqrt(gx * gx + gz * gz);
            double smoothed = Math.sqrt(gx * gx + gz * gz + EPS2);
            double mm = lin.mMag(t);
            ux[t] = mm / smoothed * gx;
            uz[t] = mm / smoothed * gz;
            double recovered = Math.hypot(ux[t], uz[t]);
            double ratio = mm == 0.0 ? 1.0 : recovered / mm;
            yaw[t] = lin.recoverYawDeg(t, gx, gz);
            boolean weak = nrm < 1.0e-6;
            if (weak) degenerate++;
            if (weak || ratio < 0.999999 || "1".equals(System.getenv("PKC_CS_ALL"))) {
                System.out.printf("CS   t=%-4d |g|=%12.6e mMag=%10.6f recovered=%10.6f ratio=%.9f yaw=%9.4f%s%n",
                        t, nrm, mm, recovered, ratio, yaw[t], weak ? "  <-WEAK" : "");
            }
        }
        System.out.printf("CS ticks with |g| < 1e-6: %d of %d%n", degenerate, n);

        System.out.println("CS --- per wall: what the dual believes vs what the recovered yaws give ---");
        double[] tx = new double[n];
        double[] tz = new double[n];
        for (int t = 0; t < n; t++) {
            double phi = lin.baseArg(t) + yaw[t] * Math.PI / 180.0;
            tx[t] = lin.mMag(t) * Math.cos(phi);
            tz[t] = lin.mMag(t) * Math.sin(phi);
        }
        double worst = 0.0;
        for (int j = 0; j < walls.size(); j++) {
            JumpLinearModel.Wall w = walls.get(j);
            double[] u = w.axis == 0 ? ux : uz;
            double[] tu = w.axis == 0 ? tx : tz;
            double believed = 0.0;
            double actual = 0.0;
            for (int t = 0; t < n; t++) {
                believed += w.coef[t] * u[t];
                actual += w.coef[t] * tu[t];
            }
            double slackBelieved = w.bPrime - believed;
            double slackActual = w.bPrime - actual;
            if (slackActual < worst) worst = slackActual;
            System.out.printf("CS   wall %-3d %-2s t=%-4d lam=%12.6e bPrime=%14.7f believed=%14.7f actual=%14.7f "
                            + "slackBelieved=%11.3e slackActual=%11.3e %s%n",
                    j, w.axis == 0 ? "X" : "Z", ineq.get(j).t1, r.lambda[j], w.bPrime, believed, actual,
                    slackBelieved, slackActual, slackActual < 0 ? "VIOLATED" : "");
        }
        System.out.printf("CS worst actual linear slack = %.9e%n", worst);
    }
}
