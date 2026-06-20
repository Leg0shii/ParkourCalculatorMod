package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class J008VelocityFieldTest {

    @Test
    public void continuousFieldLocalizesBand() {
        ProblemFixture pf = ProblemFixture.load("solve", "j008-bfneo");
        final SaveFile file = pf.file;
        SaveFile.Start seed = file.angleSolver.seed;

        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                s.setEffort(pf.expect.effort());
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                return in;
            }
        };

        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(
                file.angleSolver.startTick,
                new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]),
                seed.yaw, seed.vel[1], file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(0.0, 1.0, 1.0, 2.0);
        VelocityFinder finder = new VelocityFinder(
                problem, pf.model, anchor, file.angleSolver.landingTick, pad, null, 20_000L);

        double recorded = finder.fieldAt(seed.vel[0], seed.vel[2]);
        System.out.printf("field at recorded v0=(%.4f,%.4f): %.5f%n", seed.vel[0], seed.vel[2], recorded);
        assertTrue("field at the recorded (collision-free) velocity must be finite", !Double.isNaN(recorded));
        assertTrue("the recorded velocity lands, so the continuous field must classify it as landing (negative)",
                recorded < 0.0);

        int n = 33;
        VelocityFinder.Grid grid = new VelocityFinder.Grid(-0.30, 0.00, 0.05, 0.00, 0.16, 0.05);
        float[] z = finder.sweepFieldParallel(grid, n, n, 4, new AtomicBoolean(false), null);

        int finite = 0, neg = 0, pos = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (float v : z) {
            if (Float.isNaN(v)) continue;
            finite++;
            if (v < 0) neg++;
            if (v > 0) pos++;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        System.out.printf("field %dx%d: finite=%d/%d neg=%d pos=%d min=%.4f max=%.4f%n",
                n, n, finite, z.length, neg, pos, min, max);
        printSignMap(z, n);

        assertTrue("most of the grid must be collision-feasible (finite field)", finite > z.length / 2);
        assertTrue("the landing trough must appear (field dips below zero somewhere)", neg > 0);
        assertTrue("the miss/infeasible region must appear (field above zero somewhere)", pos > 0);
    }

    private void printSignMap(float[] z, int n) {
        for (int r = n - 1; r >= 0; r--) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < n; c++) {
                float v = z[r * n + c];
                sb.append(Float.isNaN(v) ? ' ' : v < 0 ? '#' : v < 1.0 ? '.' : ':');
            }
            System.out.println(sb);
        }
    }
}
