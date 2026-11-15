package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.GateProbe;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SineTableGeometry;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GateProbeTest {

    private static final int TICKS = 6;
    private static final int PATHS = 4000;
    private static final double ROUNDING = 1.0e-12;

    private static JumpPhysicsInputs scenario() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(TICKS);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0.0F;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.startBox = StartBox.pinned(0.5, 0.5, 0.0, 0.0);
        sc.jumpTick = -1;
        double[] slip = new double[TICKS];
        for (int t = 0; t < TICKS; t++) slip[t] = Double.NaN;
        sc.slipPerTick = slip;
        return sc;
    }

    private static final class Rig {
        final SineTableGeometry geom;
        final JumpLinearModel lin;
        final double[] gx = new double[TICKS];
        final double[] gz = new double[TICKS];
        final double[] support = new double[TICKS];
        final double[] full = new double[TICKS];
        double dualValue;

        Rig(String version, Random rnd) {
            JumpPhysicsInputs sc = scenario();
            geom = new SineTableGeometry(ExactJumpModel.forMcVersion(version), sc);
            lin = new JumpLinearModel(sc, new boolean[TICKS], new boolean[TICKS], true);
            for (int t = 0; t < TICKS; t++) {
                gx[t] = rnd.nextDouble() * 2.0 - 1.0;
                gz[t] = rnd.nextDouble() * 2.0 - 1.0;
                full[t] = Double.NaN;
                support[t] = geom.support(t, gx[t], gz[t], Double.NaN, Double.NaN);
                dualValue += support[t];
            }
        }

        GateProbe probe(double target) {
            return new GateProbe(geom, (t, ax, az) -> 0.0, full, full, gx, gz, new double[TICKS],
                    new double[TICKS], support, dualValue, target, 1.0);
        }

        double velocity(int axis, int tick, double[][] u) {
            double v = 0.0;
            for (int s = 0; s < tick; s++) v += lin.velocityCoef(axis, s, tick) * u[s][axis];
            return v;
        }

        double[][] randomCells(Random rnd) {
            double[][] u = new double[TICKS][2];
            for (int t = 0; t < TICKS; t++) geom.exactU(t, (float) (rnd.nextDouble() * 360.0 - 180.0), u[t]);
            return u;
        }

        double objective(double[][] u) {
            double v = 0.0;
            for (int t = 0; t < TICKS; t++) v += gx[t] * u[t][0] + gz[t] * u[t][1];
            return v;
        }
    }

    @Test
    public void aProbeValueBoundsEveryPathOnTheRowsSide() {
        Random rnd = new Random(20261112L);
        for (String version : new String[]{"1.8.9", "1.21.5"}) {
            for (int trial = 0; trial < 12; trial++) {
                Rig rig = new Rig(version, rnd);
                if (!rig.geom.hasInput(0)) continue;
                int axis = trial % 2;
                int tick = 2 + trial % (TICKS - 2);
                boolean positive = trial % 3 != 0;
                double bound = (rnd.nextDouble() - 0.5) * 0.1;
                JumpLinearModel.Wall row = rig.lin.keepAliveWall(axis, tick, bound, positive);
                GateProbe probe = rig.probe(Double.NEGATIVE_INFINITY);
                double[] multipliers = {0.0, 0.01, 0.3, 1.0, 4.0, 50.0};
                double[] values = new double[multipliers.length];
                for (int k = 0; k < multipliers.length; k++) values[k] = probe.valueWith(row, multipliers[k]);
                assertEquals(rig.dualValue, values[0], ROUNDING);
                int onTheSide = 0;
                double bestOnTheSide = Double.NEGATIVE_INFINITY;
                for (int p = 0; p < PATHS; p++) {
                    double[][] u = rig.randomCells(rnd);
                    double v = rig.velocity(axis, tick, u);
                    if (positive ? v < bound : v > -bound) continue;
                    onTheSide++;
                    double obj = rig.objective(u);
                    bestOnTheSide = Math.max(bestOnTheSide, obj);
                    for (int k = 0; k < multipliers.length; k++) {
                        assertTrue(version + " trial " + trial + " multiplier " + multipliers[k] + " value "
                                + values[k] + " under a path of " + obj, values[k] >= obj - ROUNDING);
                    }
                }
                assertTrue(version + " trial " + trial + " sampled no path on the row's side", onTheSide > 0);
                assertFalse(version + " trial " + trial + " excludes a side that holds a path of " + bestOnTheSide,
                        rig.probe(bestOnTheSide - 1.0e-9).excludes(row));
            }
        }
    }

    @Test
    public void theSearchFindsAMultiplierThatReachesTheTargetWhenOneExists() {
        Random rnd = new Random(20261113L);
        int reached = 0;
        for (int trial = 0; trial < 40; trial++) {
            Rig rig = new Rig("1.8.9", rnd);
            if (!rig.geom.hasInput(0)) continue;
            int axis = trial % 2;
            int tick = 2 + trial % (TICKS - 2);
            double bound = 0.02 + rnd.nextDouble() * 0.1;
            JumpLinearModel.Wall row = rig.lin.keepAliveWall(axis, tick, bound, trial % 4 < 2);
            GateProbe open = rig.probe(Double.NEGATIVE_INFINITY);
            double best = rig.dualValue;
            for (int k = 1; k <= 20000; k++) best = Math.min(best, open.valueWith(row, k * 2.0e-3));
            double drop = rig.dualValue - best;
            if (drop < 1.0e-6) continue;
            reached++;
            assertTrue("trial " + trial + " misses a drop of " + drop,
                    rig.probe(best + 0.02 * drop).excludes(row));
        }
        assertTrue("only " + reached + " trials had a row that lowers the value", reached >= 10);
    }

    @Test
    public void theConvexFloorNeverExceedsAConvexFunction() {
        Random rnd = new Random(20261114L);
        for (int trial = 0; trial < 20000; trial++) {
            int pieces = 1 + rnd.nextInt(5);
            double[] slope = new double[pieces];
            double[] offset = new double[pieces];
            for (int k = 0; k < pieces; k++) {
                slope[k] = rnd.nextGaussian() * 3.0;
                offset[k] = rnd.nextGaussian();
            }
            double[] x = new double[4];
            for (int k = 0; k < 4; k++) x[k] = rnd.nextDouble() * 10.0;
            java.util.Arrays.sort(x);
            if (x[1] - x[0] < 1.0e-3 || x[2] - x[1] < 1.0e-3 || x[3] - x[2] < 1.0e-3) continue;
            double floor = GateProbe.convexFloor(x[0], convex(slope, offset, x[0]), x[1], convex(slope, offset, x[1]),
                    x[2], convex(slope, offset, x[2]), x[3], convex(slope, offset, x[3]));
            double least = Double.POSITIVE_INFINITY;
            for (int k = 0; k <= 2000; k++) {
                least = Math.min(least, convex(slope, offset, x[0] + (x[3] - x[0]) * k / 2000.0));
            }
            assertTrue("trial " + trial + " floor " + floor + " above the minimum " + least,
                    floor <= least + 1.0e-9);
        }
    }

    private static double convex(double[] slope, double[] offset, double x) {
        double v = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < slope.length; k++) v = Math.max(v, slope[k] * x + offset[k]);
        return v;
    }
}
