package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinearModelLongRunTest {

    private static final int TICKS = 400;
    private static final int JUMP_PERIOD = 12;
    private static final double SINE_RESIDUAL = 1.0e-3;
    private static final double COUPLING_TOL = 1.0e-8;

    private static JumpPhysicsInputs hops(int n) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0.0F;
        sc.initialVelocity = new Vec3dCore(0.0, 0.0, 0.2);
        sc.startBox = StartBox.pinned(0.5, 0.5, 0.0, 0.2);
        sc.jumpTick = 0;
        boolean[] jump = new boolean[n];
        double[] slip = new double[n];
        for (int t = 0; t < n; t++) {
            jump[t] = t % JUMP_PERIOD == 0;
            slip[t] = jump[t] ? 0.6 : Double.NaN;
        }
        sc.jumpPerTick = jump;
        sc.slipPerTick = slip;
        return sc;
    }

    @Test
    public void lateTicksKeepTheirCoupling() {
        JumpLinearModel lin = new JumpLinearModel(hops(TICKS));
        for (int s = 0; s < TICKS; s++) {
            assertEquals("one tick after tick " + s, 1.0, lin.coef(s, s + 1), COUPLING_TOL);
            if (s + 2 <= TICKS) {
                assertEquals("two ticks after tick " + s, 1.0 + lin.friction(s), lin.coef(s, s + 2), COUPLING_TOL);
            }
        }
    }

    @Test
    public void linearPathFollowsTheExactPathOverManyJumps() {
        for (String version : new String[]{"1.8.9", "1.21.3"}) {
            ExactJumpModel exact = ExactJumpModel.forMcVersion(version);
            JumpPhysicsInputs sc = hops(TICKS);
            double[] yaws = new double[TICKS];
            ForwardPath path = exact.forward(sc, sc.toGameFacings(yaws));
            assertTrue(version + " must travel", path.posZ[TICKS] - path.posZ[0] > 50.0);
            JumpLinearModel lin = new JumpLinearModel(sc);
            double[] ux = new double[TICKS];
            double[] uz = new double[TICKS];
            lin.inputVectors(yaws, ux, uz);
            for (int k = 1; k <= TICKS; k++) {
                double px = lin.constPos(k, 0);
                double pz = lin.constPos(k, 1);
                for (int s = 0; s < k; s++) {
                    px += lin.coefAxis(0, s, k) * ux[s];
                    pz += lin.coefAxis(1, s, k) * uz[s];
                }
                assertEquals(version + " X at tick " + k, path.posX[k], px, SINE_RESIDUAL);
                assertEquals(version + " Z at tick " + k, path.posZ[k], pz, SINE_RESIDUAL);
            }
        }
    }
}
