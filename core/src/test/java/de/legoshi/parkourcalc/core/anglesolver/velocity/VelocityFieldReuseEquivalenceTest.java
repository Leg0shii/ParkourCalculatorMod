package de.legoshi.parkourcalc.core.anglesolver.velocity;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VelocityFieldReuseEquivalenceTest {

    private static final int N = 33;
    private static final double VX_LO = -0.30, VX_HI = 0.00, VZ_LO = 0.00, VZ_HI = 0.16;

    private static VelocityFinder buildFinder() {
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
        return new VelocityFinder(problem, pf.model, anchor, file.angleSolver.landingTick, pad, null, 20_000L);
    }

    @Test
    public void singleSolveFieldEqualsLegacyDoubleSolve() {
        VelocityFinder finder = buildFinder();
        AtomicBoolean cancel = new AtomicBoolean(false);
        double dvx = (VX_HI - VX_LO) / (N - 1), dvz = (VZ_HI - VZ_LO) / (N - 1);

        int met = 0, miss = 0;
        for (int r = 0; r < N; r++) {
            double vz = VZ_LO + r * dvz;
            for (int c = 0; c < N; c++) {
                double vx = VX_LO + c * dvx;

                VelocityFinder.Candidate legacyCand = finder.evaluate(vx, vz, cancel);
                double legacyField = finder.cellField(legacyCand, vx, vz);

                VelocityFinder.CellResult cr = finder.evaluateAndField(vx, vz, cancel);

                String at = "cell (" + r + "," + c + ") v0=(" + vx + "," + vz + ")";
                assertEquals(at + " field bits", Float.floatToRawIntBits((float) legacyField),
                        Float.floatToRawIntBits((float) cr.field));
                assertEquals(at + " constraintsMet", legacyCand.constraintsMet, cr.cand.constraintsMet);
                assertEquals(at + " lands", legacyCand.lands, cr.cand.lands);
                assertEquals(at + " landX bits", Double.doubleToRawLongBits(legacyCand.landX),
                        Double.doubleToRawLongBits(cr.cand.landX));
                assertEquals(at + " landZ bits", Double.doubleToRawLongBits(legacyCand.landZ),
                        Double.doubleToRawLongBits(cr.cand.landZ));
                assertEquals(at + " support bits", Double.doubleToRawLongBits(legacyCand.support),
                        Double.doubleToRawLongBits(cr.cand.support));

                if (cr.cand.constraintsMet) met++; else miss++;
            }
        }
        assertTrue("expected some constraint-met cells in the grid", met > 0);
        assertTrue("expected some miss cells (exercises the reused-Result forward sim path)", miss > 0);
    }
}
