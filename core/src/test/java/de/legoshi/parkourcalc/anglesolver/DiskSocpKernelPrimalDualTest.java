package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DiskSocpKernelPrimalDualTest {

    private static final int N = 3;
    private static final double[] CX = {0.44, 0.64, -0.11};
    private static final double[] CZ = {0.44, 0.9, -0.84};
    private static final double[] M_MAG = {0.29, 0.254, 0.138};
    private static final double[] FEASIBLE_UX = {-0.014141, -0.040873, -0.092685};
    private static final double[] FEASIBLE_UZ = {0.289654, 0.055236, -0.102241};
    private static final double FEASIBLE_SLACK = 0.01;
    private static final double TOL = 1.0e-7;

    private static List<JumpLinearModel.Wall> walls() {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{-0.83, 0.65, 0.25}, -0.038, false, "w0", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{0.19, 0.65, 0.29}, 0.064, false, "w1", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{-0.2, -0.29, 0.49}, 0.114, false, "w2", 0.0));
        walls.add(new JumpLinearModel.Wall(0, new double[]{0.68, 0.65, 0.76}, 0.075, false, "w3", 0.0));
        return walls;
    }

    private static List<DiskSocpKernel.ChordRow> chords() {
        List<DiskSocpKernel.ChordRow> chords = new ArrayList<>();
        chords.add(new DiskSocpKernel.ChordRow(2, -0.6777, -0.7353, 0.13785, "c0"));
        chords.add(new DiskSocpKernel.ChordRow(0, -0.0775, 0.997, 0.28988, "c1"));
        return chords;
    }

    private static double objective(double[] ux, double[] uz) {
        double value = 0.0;
        for (int t = 0; t < N; t++) value += CX[t] * ux[t] + CZ[t] * uz[t];
        return value;
    }

    private static void assertFeasible(double[] ux, double[] uz, double tol) {
        for (int t = 0; t < N; t++) {
            assertTrue("tick " + t + " stays in its disk", StrictMath.hypot(ux[t], uz[t]) <= M_MAG[t] + tol);
        }
        for (JumpLinearModel.Wall w : walls()) {
            double lhs = 0.0;
            for (int t = 0; t < N; t++) lhs += w.coef[t] * (w.axis == 0 ? ux[t] : uz[t]);
            assertTrue(w.name + " holds: " + lhs + " <= " + w.bPrime, lhs <= w.bPrime + tol);
        }
        for (DiskSocpKernel.ChordRow c : chords()) {
            double lhs = c.ax * ux[c.tick] + c.az * uz[c.tick];
            assertTrue(c.name + " holds: " + lhs + " >= " + c.rhs, lhs >= c.rhs - tol);
        }
    }

    @Test
    public void theBoundSitsJustAboveAKnownFeasiblePoint() {
        assertFeasible(FEASIBLE_UX, FEASIBLE_UZ, 0.0);
        double feasibleValue = objective(FEASIBLE_UX, FEASIBLE_UZ);
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, chords());
        assertNotNull(oc.result);
        assertEquals(DiskSocpKernel.FAIL_NONE, oc.failCode);
        assertTrue(oc.result.converged);
        assertTrue("a bound never sits under a feasible value: " + oc.result.value,
                oc.result.value >= feasibleValue);
        assertTrue("and stays within " + FEASIBLE_SLACK + " of it: " + oc.result.value,
                oc.result.value <= feasibleValue + FEASIBLE_SLACK);
    }

    @Test
    public void theReturnedPrimalPointIsFeasibleAndReachesTheBound() {
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, chords());
        assertEquals(DiskSocpKernel.FAIL_NONE, oc.failCode);
        assertFeasible(oc.result.ux, oc.result.uz, TOL);
        assertEquals("primal and dual value meet", oc.result.value, objective(oc.result.ux, oc.result.uz), TOL);
    }

    @Test
    public void theBoundIsTheDualValueOfTheReturnedMultipliers() {
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls(), null, chords());
        List<JumpLinearModel.Wall> walls = walls();
        List<DiskSocpKernel.ChordRow> chords = chords();
        double[] gx = CX.clone();
        double[] gz = CZ.clone();
        double value = 0.0;
        for (int j = 0; j < walls.size(); j++) {
            JumpLinearModel.Wall w = walls.get(j);
            double lambda = oc.result.lambda[j];
            assertTrue(w.name + " multiplier is not negative", lambda >= 0.0);
            for (int t = 0; t < N; t++) {
                if (w.axis == 0) gx[t] -= lambda * w.coef[t];
                else gz[t] -= lambda * w.coef[t];
            }
            value += lambda * w.bPrime;
        }
        for (int q = 0; q < chords.size(); q++) {
            DiskSocpKernel.ChordRow c = chords.get(q);
            double lambda = oc.result.lambda[walls.size() + q];
            assertTrue(c.name + " multiplier is not negative", lambda >= 0.0);
            gx[c.tick] += lambda * c.ax;
            gz[c.tick] += lambda * c.az;
            value -= lambda * c.rhs;
        }
        for (int t = 0; t < N; t++) {
            assertEquals(gx[t], oc.result.gx[t], 1.0e-12);
            assertEquals(gz[t], oc.result.gz[t], 1.0e-12);
            value += M_MAG[t] * StrictMath.hypot(gx[t], gz[t]);
        }
        assertEquals(value, oc.result.value, 1.0e-12);
    }

    @Test
    public void wallsThatNoPointMeetsAreReportedUnbounded() {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{1.0, 1.0, 1.0}, -1.0, false, "farLeft", 0.0));
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(N, CX, CZ, M_MAG, walls, null, null);
        assertEquals(DiskSocpKernel.FAIL_UNBOUNDED, oc.failCode);
        assertNotNull(oc.result);
        double floor = 0.0;
        for (int t = 0; t < N; t++) floor -= M_MAG[t] * StrictMath.hypot(CX[t], CZ[t]);
        assertTrue("the returned value is under every value a point in the disks can have", oc.result.value < floor);
    }

    @Test
    public void aFreeStartBoxIsSolvedAtItsCorner() {
        double[] cx = {1.0};
        double[] cz = {0.0};
        double[] mMag = {0.5};
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{1.0}, 0.2, false, "cap", -1.0));
        CostateDualSolver.FreeP0 box = new CostateDualSolver.FreeP0(-0.1, 0.3, -0.2, 0.2, 0.0, 0.0, 5.0e-4);
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(1, cx, cz, mMag, walls, box, null);
        assertEquals(DiskSocpKernel.FAIL_NONE, oc.failCode);
        assertEquals(0.3, oc.result.value, TOL);
        assertEquals(0.3, oc.result.ux[0], TOL);
        assertEquals(-0.1, oc.result.dvx, TOL);
    }
}
