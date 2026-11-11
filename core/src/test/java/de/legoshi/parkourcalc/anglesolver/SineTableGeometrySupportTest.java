package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SineTableGeometry;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class SineTableGeometrySupportTest {

    private static final int PIECE_INDICES = 48;
    private static final int ARCS = 40;
    private static final int FULL_TURNS = 6;

    private static JumpPhysicsInputs scenario(boolean jumpTick0) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(2);
        sc.startPos = new Vec3dCore(0.5, 100.0, 0.5);
        sc.startYaw = 0.0F;
        sc.initialVelocity = Vec3dCore.ZERO;
        sc.startBox = StartBox.pinned(0.5, 0.5, 0.0, 0.0);
        sc.jumpTick = jumpTick0 ? 0 : -1;
        sc.slipPerTick = new double[]{jumpTick0 ? 0.6 : Double.NaN, Double.NaN};
        return sc;
    }

    private static double directionDeg(Random rnd, int kind, double lo, double width) {
        if (kind == 0) return lo + rnd.nextDouble() * width;
        if (kind == 1) return lo + 0.5 * width + 180.0 + (rnd.nextDouble() - 0.5) * 0.2;
        if (kind == 2) return (rnd.nextBoolean() ? lo : lo + width) + (rnd.nextDouble() - 0.5) * 8.0;
        return rnd.nextDouble() * 360.0;
    }

    private static double piecewise(SineTableGeometry geom, double gx, double gz, double lo, double hi) {
        int lastIdx = SineTableGeometry.idxOf(hi);
        double best = Double.NEGATIVE_INFINITY;
        int from = SineTableGeometry.idxOf(lo);
        while (from <= lastIdx) {
            int to = Math.min(lastIdx, from + PIECE_INDICES);
            best = Math.max(best, geom.support(0, gx, gz, SineTableGeometry.degOfIndexCenter(from),
                    SineTableGeometry.degOfIndexCenter(to)));
            from = to + 1;
        }
        return best;
    }

    @Test
    public void theSupportOfAWideArcIsTheLargestSupportOfItsPieces() {
        Random rnd = new Random(20261110L);
        for (String ver : new String[]{"1.8.9", "1.12.2", "1.21.5", "26.2"}) {
            ExactJumpModel model = ExactJumpModel.forMcVersion(ver);
            for (boolean jump : new boolean[]{true, false}) {
                SineTableGeometry geom = new SineTableGeometry(model, scenario(jump));
                if (!geom.hasInput(0)) continue;
                for (int k = 0; k < ARCS; k++) {
                    double width = 6.0 + rnd.nextDouble() * 200.0;
                    double lo = -360.0 + rnd.nextDouble() * 720.0;
                    double hi = lo + width;
                    double dir = Math.toRadians(directionDeg(rnd, k % 4, lo, width));
                    double scale = Math.pow(10.0, -6.0 + 12.0 * rnd.nextDouble());
                    double gx = scale * Math.cos(dir);
                    double gz = scale * Math.sin(dir);
                    String label = ver + " jump=" + jump + " arc=" + lo + ".." + hi + " g=" + gx + "," + gz;
                    assertEquals(label, piecewise(geom, gx, gz, lo, hi), geom.support(0, gx, gz, lo, hi), 0.0);
                }
                for (int k = 0; k < FULL_TURNS; k++) {
                    double dir = rnd.nextDouble() * 2.0 * Math.PI;
                    double gx = Math.cos(dir);
                    double gz = Math.sin(dir);
                    String label = ver + " jump=" + jump + " full turn g=" + gx + "," + gz;
                    assertEquals(label, piecewise(geom, gx, gz, -180.0, 179.999),
                            geom.support(0, gx, gz, Double.NaN, Double.NaN), 0.0);
                }
            }
        }
    }
}
