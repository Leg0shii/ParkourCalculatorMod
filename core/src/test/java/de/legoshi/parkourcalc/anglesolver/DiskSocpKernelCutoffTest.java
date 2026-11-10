package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DiskSocpKernelCutoffTest {

    private static final int N = 3;
    private static final double[] CX = {1.0, 0.5, -0.25};
    private static final double[] CZ = {0.25, -1.0, 0.75};
    private static final double[] M_MAG = {0.1, 0.026, 0.3274};

    private static List<JumpLinearModel.Wall> walls() {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{1.0, 1.0, 1.0}, 0.02, false, "capX", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{1.0, -1.0, 0.5}, 0.05, false, "capZ", 0.0));
        return walls;
    }

    private static DiskSocpKernel.Outcome solve(double cutoff) {
        return DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, null, null, cutoff);
    }

    @Test
    public void aCutoffBelowTheOptimumChangesNothing() {
        DiskSocpKernel.Outcome plain = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, null, null);
        assertNotNull(plain.result);
        assertTrue(plain.result.converged);
        DiskSocpKernel.Outcome low = solve(plain.result.value - 1.0e-3);
        assertEquals(DiskSocpKernel.FAIL_NONE, low.failCode);
        assertEquals(Double.doubleToRawLongBits(plain.result.value), Double.doubleToRawLongBits(low.result.value));
        assertEquals(plain.result.iters, low.result.iters);
        for (int t = 0; t < N; t++) {
            assertEquals(Double.doubleToRawLongBits(plain.result.gx[t]), Double.doubleToRawLongBits(low.result.gx[t]));
            assertEquals(Double.doubleToRawLongBits(plain.result.gz[t]), Double.doubleToRawLongBits(low.result.gz[t]));
        }
        for (int j = 0; j < plain.result.lambda.length; j++) {
            assertEquals(Double.doubleToRawLongBits(plain.result.lambda[j]), Double.doubleToRawLongBits(low.result.lambda[j]));
        }
    }

    @Test
    public void aCutoffAboveTheOptimumStopsEarlyOnAValidBound() {
        DiskSocpKernel.Outcome plain = solve(Double.NEGATIVE_INFINITY);
        double optimum = plain.result.value;
        double cutoff = optimum + 1.0e-3;
        DiskSocpKernel.Outcome early = solve(cutoff);
        assertEquals(DiskSocpKernel.FAIL_CUTOFF, early.failCode);
        assertNotNull(early.result);
        assertTrue("stopped before convergence", early.result.iters < plain.result.iters);
        double value = 0.0;
        for (int t = 0; t < N; t++) value += M_MAG[t] * StrictMath.hypot(early.result.gx[t], early.result.gz[t]);
        List<JumpLinearModel.Wall> walls = walls();
        for (int j = 0; j < walls.size(); j++) {
            assertTrue("multipliers stay dual feasible", early.result.lambda[j] > 0.0);
            value += early.result.lambda[j] * walls.get(j).bPrime;
        }
        assertTrue("the returned point is under the cutoff: " + value, value <= cutoff + 1.0e-12);
        assertTrue("and still an upper bound of the optimum: " + value, value >= optimum - 1.0e-9);
    }
}
