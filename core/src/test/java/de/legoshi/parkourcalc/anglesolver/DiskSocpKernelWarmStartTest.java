package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DiskSocpKernelWarmStartTest {

    private static final int N = 3;
    private static final double[] CX = {0.44, 0.64, -0.11};
    private static final double[] CZ = {0.44, 0.9, -0.84};
    private static final double[] M_MAG = {0.29, 0.254, 0.138};
    private static final double[] FEASIBLE_UX = {-0.014141, -0.040873, -0.092685};
    private static final double[] FEASIBLE_UZ = {0.289654, 0.055236, -0.102241};
    private static final double FEASIBLE_SLACK = 0.01;

    private static List<JumpLinearModel.Wall> walls() {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{-0.83, 0.65, 0.25}, -0.038, false, "w0", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{0.19, 0.65, 0.29}, 0.064, false, "w1", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{-0.2, -0.29, 0.49}, 0.114, false, "w2", 0.0));
        walls.add(new JumpLinearModel.Wall(0, new double[]{0.68, 0.65, 0.76}, 0.075, false, "w3", 0.0));
        return walls;
    }

    private static List<DiskSocpKernel.ChordRow> childChords() {
        List<DiskSocpKernel.ChordRow> chords = new ArrayList<>();
        chords.add(new DiskSocpKernel.ChordRow(2, -0.6777, -0.7353, 0.13785, "c0"));
        chords.add(new DiskSocpKernel.ChordRow(0, -0.0775, 0.997, 0.28988, "c1"));
        return chords;
    }

    private static Map<String, Double> parentMultipliers() {
        DiskSocpKernel.Outcome parent = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, null, null);
        assertNotNull(parent.result);
        assertEquals(DiskSocpKernel.FAIL_NONE, parent.failCode);
        Map<String, Double> warm = new HashMap<>();
        List<JumpLinearModel.Wall> walls = walls();
        for (int j = 0; j < walls.size(); j++) warm.put(walls.get(j).name, parent.result.lambda[j]);
        return warm;
    }

    @Test
    public void aChildWarmStartedFromItsParentReturnsTheSameBoundAsAColdSolve() {
        DiskSocpKernel.Outcome cold = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, childChords(), null);
        DiskSocpKernel.Outcome warm = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, childChords(),
                parentMultipliers());
        assertEquals(DiskSocpKernel.FAIL_NONE, cold.failCode);
        assertEquals(DiskSocpKernel.FAIL_NONE, warm.failCode);
        assertEquals("the warm-started bound is as tight as the cold one", cold.result.value, warm.result.value,
                1.0e-9);
    }

    @Test
    public void theWarmStartedBoundSitsJustAboveAKnownFeasiblePoint() {
        double[] ux = FEASIBLE_UX;
        double[] uz = FEASIBLE_UZ;
        double feasibleValue = 0.0;
        for (int t = 0; t < N; t++) {
            feasibleValue += CX[t] * ux[t] + CZ[t] * uz[t];
            assertTrue("tick " + t + " stays in its disk", StrictMath.hypot(ux[t], uz[t]) <= M_MAG[t]);
        }
        for (JumpLinearModel.Wall w : walls()) {
            double lhs = 0.0;
            for (int t = 0; t < N; t++) lhs += w.coef[t] * (w.axis == 0 ? ux[t] : uz[t]);
            assertTrue(w.name + " holds: " + lhs + " <= " + w.bPrime, lhs <= w.bPrime);
        }
        for (DiskSocpKernel.ChordRow c : childChords()) {
            double lhs = c.ax * ux[c.tick] + c.az * uz[c.tick];
            assertTrue(c.name + " holds: " + lhs + " >= " + c.rhs, lhs >= c.rhs);
        }
        DiskSocpKernel.Outcome warm = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, childChords(),
                parentMultipliers());
        assertNotNull(warm.result);
        assertTrue("a bound never sits under a feasible value: " + warm.result.value,
                warm.result.value >= feasibleValue);
        assertTrue("and stays within " + FEASIBLE_SLACK + " of it: " + warm.result.value,
                warm.result.value <= feasibleValue + FEASIBLE_SLACK);
    }
}
