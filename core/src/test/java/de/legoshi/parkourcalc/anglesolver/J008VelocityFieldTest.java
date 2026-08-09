package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.experimental.categories.Category;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class J008VelocityFieldTest {

    @Test
    public void recordedVelocityIsClassifiedAsLanding() {
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
        VelocityFinder finder = new VelocityFinder(
                problem, pf.model, anchor, file.angleSolver.landingTick, null, 20_000L);

        double recorded = finder.fieldAt(seed.vel[0], seed.vel[2]);
        assertTrue("field at the recorded (collision-free) velocity must be finite", !Double.isNaN(recorded));
        assertTrue("the recorded velocity is feasible, so the field must classify it as a hit (negative)",
                recorded < 0.0);
    }
}
