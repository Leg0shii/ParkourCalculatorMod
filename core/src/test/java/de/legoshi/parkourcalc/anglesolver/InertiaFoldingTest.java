package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class InertiaFoldingTest {

    private static final double RAD = Math.PI / 180.0;

    @Test
    public void loopmmInertiaTermDropsOutOfFoldedModel() {
        ProblemFixture pf = ProblemFixture.load("solve", "loopmm-3jump-lands");
        JumpSpec spec = pf.specFor(AngleSolverState.Axis.Z, AngleSolverState.Goal.MAX);
        JumpPhysicsInputs sc = spec.asScenario();
        ExactJumpModel exact = pf.model;
        int n = sc.numTicks;

        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] yaws = recoverMarginZero(lin, spec);

        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        lin.zeroingPattern(yaws, exact.inertiaThreshold(), exact.perAxisInertia(), zx, zz);
        boolean anyZero = false;
        for (int t = 0; t < n; t++) anyZero |= zx[t] || zz[t];
        assertTrue("loopmm recovery must activate an inertia tick", anyZero);

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath exactPath = exact.forward(sc, gf);

        double clampFree = maxDiff(lin, sc, yaws, exactPath);
        JumpLinearModel folded = new JumpLinearModel(sc, zx, zz);
        double foldedDiff = maxDiff(folded, sc, yaws, exactPath);

        assertTrue("inertia term must be present clamp-free (got " + clampFree + ")", clampFree > 1.0e-2);
        assertTrue("folded model must differ from byte-exact by the sine residual only (got " + foldedDiff + ")",
                foldedDiff < 2.0e-3);
    }

    @Test
    public void keepAliveWallIsTheComplementOfTheZeroingWall() {
        ProblemFixture pf = ProblemFixture.load("solve", "loopmm-3jump-lands");
        JumpSpec spec = pf.specFor(AngleSolverState.Axis.Z, AngleSolverState.Goal.MAX);
        JumpPhysicsInputs sc = spec.asScenario();
        ExactJumpModel exact = pf.model;
        int n = sc.numTicks;
        double thr = exact.inertiaThreshold();

        JumpLinearModel lin = new JumpLinearModel(sc);
        double[] yaws = recoverMarginZero(lin, spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = exact.forward(sc, gf);
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            double phi = lin.baseArg(t) + Angles.wrap(yaws[t]) * RAD;
            ux[t] = lin.mMag(t) * Math.cos(phi);
            uz[t] = lin.mMag(t) * Math.sin(phi);
        }

        for (int axis = 0; axis < 2; axis++) {
            double[] vel = axis == 0 ? path.velX : path.velZ;
            int firstGate = n;
            for (int k = 0; k < n; k++) {
                if (Math.abs(vel[k]) < thr) { firstGate = k; break; }
            }
            boolean[] mask = new boolean[n];
            for (int k = 1; k < n; k++) {
                mask[k] = true;
                List<JumpLinearModel.Wall> zeroing = new JumpLinearModel(sc, axis == 0 ? mask : null,
                        axis == 1 ? mask : null).velocityWalls(thr);
                mask[k] = false;
                JumpLinearModel.Wall keep = lin.keepAliveWall(axis, k, thr, true);
                if (keep == null) continue;

                JumpLinearModel.Wall mirror = null;
                for (JumpLinearModel.Wall w : zeroing) if (w.name.endsWith("-")) mirror = w;
                assertTrue("the zeroing pattern must emit the mirrored wall at " + axis + "@" + k, mirror != null);
                for (int s = 0; s < n; s++) {
                    assertTrue("keep-alive must reuse the zeroing coefficients at " + axis + "@" + k,
                            keep.coef[s] == mirror.coef[s]);
                }
                assertTrue("keep-alive must sit a band width below the zeroing bound at " + axis + "@" + k,
                        Math.abs(keep.bPrime - (mirror.bPrime - 2.0 * thr)) < 1.0e-15);

                if (k > firstGate) continue;
                double slack = -keep.bPrime;
                for (int s = 0; s < n; s++) slack += keep.coef[s] * (axis == 0 ? ux[s] : uz[s]);
                assertTrue("keep-alive slack must read the byte-exact velocity at " + axis + "@" + k
                        + " (slack=" + slack + " vel=" + vel[k] + ")", Math.abs(slack - (thr - vel[k])) < 2.0e-3);
            }
        }
    }

    private static double[] recoverMarginZero(JumpLinearModel lin, JumpSpec spec) {
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            if (gx * gx + gz * gz < 1.0e-18) {
                gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
            }
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        return yaws;
    }

    private static double maxDiff(JumpLinearModel model, JumpPhysicsInputs sc, double[] yaws, ForwardPath exactPath) {
        int n = sc.numTicks;
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            double phi = model.baseArg(t) + yaws[t] * RAD;
            ux[t] = model.mMag(t) * Math.cos(phi);
            uz[t] = model.mMag(t) * Math.sin(phi);
        }
        double worst = 0.0;
        for (int k = 0; k <= n; k++) {
            double px = model.constPos(k, 0);
            double pz = model.constPos(k, 1);
            for (int s = 0; s < n; s++) {
                px += model.coefAxis(0, s, k) * ux[s];
                pz += model.coefAxis(1, s, k) * uz[s];
            }
            worst = Math.max(worst, Math.max(Math.abs(exactPath.posX[k] - px), Math.abs(exactPath.posZ[k] - pz)));
        }
        return worst;
    }
}
