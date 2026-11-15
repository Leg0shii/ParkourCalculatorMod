package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CostateDualStopTest {

    private static final int N = 3;
    private static final double[] CX = {0.44, 0.64, -0.11};
    private static final double[] CZ = {0.44, 0.9, -0.84};
    private static final double[] M_MAG = {0.29, 0.254, 0.138};
    private static final double REACH = M_MAG[0] + M_MAG[1] + M_MAG[2];
    private static final double RAY_VIOLATION = 1.0e-6;

    private static List<JumpLinearModel.Wall> feasibleWalls() {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{-0.83, 0.65, 0.25}, -0.038, false, "w0", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{0.19, 0.65, 0.29}, 0.064, false, "w1", 0.0));
        walls.add(new JumpLinearModel.Wall(1, new double[]{-0.2, -0.29, 0.49}, 0.114, false, "w2", 0.0));
        walls.add(new JumpLinearModel.Wall(0, new double[]{0.68, 0.65, 0.76}, 0.075, false, "w3", 0.0));
        return walls;
    }

    private static List<JumpLinearModel.Wall> wallBeyondReachBy(double miss) {
        List<JumpLinearModel.Wall> walls = new ArrayList<>();
        walls.add(new JumpLinearModel.Wall(0, new double[]{1.0, 1.0, 1.0}, -REACH - miss, false, "far", 0.0));
        return walls;
    }

    private static CostateDualSolver solver(List<JumpLinearModel.Wall> walls) {
        return new CostateDualSolver(N, CX, CZ, M_MAG, walls);
    }

    @Test
    public void aFeasibleSolveIsTheSameWithTheRayStop() {
        CostateDualSolver.Result plain = solver(feasibleWalls()).solve(0.0, null);
        CostateDualSolver.Result guarded = solver(feasibleWalls()).stopOnRay(RAY_VIOLATION).solve(0.0, null);
        assertNotNull(plain);
        assertNotNull(guarded);
        assertEquals(Double.doubleToLongBits(plain.value), Double.doubleToLongBits(guarded.value));
        assertArrayEquals(plain.lambda, guarded.lambda, 0.0);
    }

    @Test
    public void aWallNoInputCanReachEndsTheSolveAtOnce() {
        CostateDualSolver plain = solver(wallBeyondReachBy(0.3));
        plain.solve(0.0, null);
        CostateDualSolver guarded = solver(wallBeyondReachBy(0.3)).stopOnRay(RAY_VIOLATION);
        assertNull(guarded.solve(0.0, null));
        assertTrue("the ray stop took " + guarded.lastIters + " iterations", guarded.lastIters <= 3);
        assertTrue("without the stop the solve runs on: " + plain.lastIters, plain.lastIters > guarded.lastIters);
    }

    @Test
    public void aMissUnderTheMarginIsNotARay() {
        CostateDualSolver plain = solver(wallBeyondReachBy(5.0e-7));
        CostateDualSolver.Result expected = plain.solve(0.0, null);
        CostateDualSolver guarded = solver(wallBeyondReachBy(5.0e-7)).stopOnRay(RAY_VIOLATION);
        CostateDualSolver.Result actual = guarded.solve(0.0, null);
        assertEquals(plain.lastIters, guarded.lastIters);
        assertEquals(expected == null, actual == null);
        if (expected != null) {
            assertEquals(Double.doubleToLongBits(expected.value), Double.doubleToLongBits(actual.value));
        }
    }

    @Test
    public void theCutoffStopsAtAValidBound() {
        CostateDualSolver full = solver(feasibleWalls());
        CostateDualSolver.Result optimum = full.solve(0.0, null);
        assertNotNull(optimum);
        double free = 0.0;
        for (int t = 0; t < N; t++) free += M_MAG[t] * StrictMath.hypot(CX[t], CZ[t]);
        assertTrue("the walls bind: " + optimum.value + " under " + free, optimum.value < free - 1.0e-3);
        double cutoff = 0.5 * (free + optimum.value);
        CostateDualSolver cut = solver(feasibleWalls());
        CostateDualSolver.Result early = cut.solve(0.0, null, 0.0, cutoff);
        assertNotNull(early);
        assertTrue("stopped at " + early.value + " over the cutoff " + cutoff, early.value <= cutoff);
        assertTrue("a dual value never falls under the optimum", early.value >= optimum.value);
        assertTrue("the cut solve took " + cut.lastIters + " of " + full.lastIters + " iterations",
                cut.lastIters < full.lastIters);
        CostateDualSolver.Result shifted = solver(feasibleWalls()).solve(0.0, null, 7.0, cutoff + 7.0);
        assertEquals(Double.doubleToLongBits(early.value), Double.doubleToLongBits(shifted.value));
    }
}
