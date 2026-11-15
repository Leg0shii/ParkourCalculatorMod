package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SineTableGeometry;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class InertiaImpulseModelTest {

    private static final int PATHS = 30;
    private static final double POSITION_TOLERANCE = 1.0e-7;
    private static final int MIN_CAPTURES = 40;
    private static final int MIN_CLAMPS = 1000;

    private static JumpPhysicsInputs pinnedScenario(ProblemFixture fixture) {
        ProblemFixture.Rig rig = fixture.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        JumpSpec spec = rig.spec();
        if (spec == null) return null;
        JumpPhysicsInputs sc = spec.asScenario();
        if (sc.lastDamageTick() >= 0) return null;
        for (int t = 0; t < sc.numTicks; t++) {
            if (sc.flowAt(t).pushes() || sc.endFlowAt(t).pushes()) return null;
        }
        JumpPhysicsInputs pinned = sc.copy();
        pinned.startBox = StartBox.pinned(sc.startPos.x, sc.startPos.z, sc.initialVelocity.x, sc.initialVelocity.z);
        return pinned;
    }

    private static double[] randomYaws(Random rnd, int n) {
        double[] yaws = new double[n];
        int shape = rnd.nextInt(6);
        double base = rnd.nextDouble() * 360.0 - 180.0;
        for (int t = 0; t < n; t++) {
            double yaw;
            if (shape == 0) yaw = base;
            else if (shape == 1) yaw = base + (rnd.nextDouble() - 0.5) * 4.0 * t;
            else if (shape == 2) yaw = rnd.nextDouble() * 360.0 - 180.0;
            else if (shape == 3) yaw = base + ((t & 1) == 0 ? 0.0 : 180.0) + rnd.nextGaussian() * 2.0;
            else if (shape == 4) yaw = base + (rnd.nextInt(3) == 0 ? 180.0 : 0.0) + rnd.nextGaussian() * 5.0;
            else yaw = rnd.nextInt(4) * 90.0 + rnd.nextGaussian() * 0.5;
            yaws[t] = Angles.wrapDelta(yaw);
        }
        return yaws;
    }

    @Test
    public void theExactPathIsTheLinearModelDrivenByTheInputsPlusOneBoundedImpulsePerClampedTick() {
        int captures = 0;
        long clamps = 0;
        for (String name : ProblemCatalog.problemNames("solve")) {
            ProblemFixture fixture = ProblemFixture.load("solve", name);
            JumpPhysicsInputs sc = pinnedScenario(fixture);
            if (sc == null) continue;
            captures++;
            ExactJumpModel exact = fixture.model;
            int n = sc.numTicks;
            SineTableGeometry geom = new SineTableGeometry(exact, sc);
            JumpLinearModel lin = new JumpLinearModel(sc, null, null, true);
            double thr = exact.inertiaThreshold();
            boolean perAxis = exact.perAxisInertia();
            double combinedSq = exact.combinedInertiaBound() * exact.combinedInertiaBound();
            Random rnd = new Random(name.hashCode());
            double[] input = new double[2];
            for (int trial = 0; trial < PATHS; trial++) {
                double[] facings = sc.toGameFacings(randomYaws(rnd, n));
                ForwardPath path = exact.forward(sc, facings);
                double[][] drive = new double[2][n];
                for (int t = 0; t < n; t++) {
                    geom.exactU(t, (float) facings[t], input);
                    double vx = path.velX[t];
                    double vz = path.velZ[t];
                    boolean clampX = perAxis ? Math.abs(vx) < thr : vx * vx + vz * vz < combinedSq;
                    boolean clampZ = perAxis ? Math.abs(vz) < thr : clampX;
                    drive[0][t] = input[0] - (clampX ? vx : 0.0);
                    drive[1][t] = input[1] - (clampZ ? vz : 0.0);
                    if (clampX && vx != 0.0) clamps++;
                    if (clampZ && vz != 0.0) clamps++;
                    if (clampX) assertTrue(name + ": impulse outside the threshold", Math.abs(vx) <= thr);
                    if (clampZ) assertTrue(name + ": impulse outside the threshold", Math.abs(vz) <= thr);
                }
                for (int axis = 0; axis < 2; axis++) {
                    double[] pos = axis == 0 ? path.posX : path.posZ;
                    for (int k = 1; k <= n; k++) {
                        double linear = lin.constPos(k, axis);
                        for (int s = 0; s < k; s++) linear += lin.coefAxis(axis, s, k) * drive[axis][s];
                        double residual = Math.abs(pos[k] - linear);
                        assertTrue(name + " path " + trial + " axis " + axis + " tick " + k
                                + ": the exact position is " + residual + " away from the linear model",
                                residual <= POSITION_TOLERANCE);
                    }
                }
            }
        }
        assertTrue("only " + captures + " captures were checked", captures >= MIN_CAPTURES);
        assertTrue("only " + clamps + " clamped ticks were checked", clamps >= MIN_CLAMPS);
    }
}
