package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.velocity.LandingPad;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VelocityFinderMultiJumpTest {

    @Test
    public void fastPathLandsMultiJumpLikeEngine() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j008b-2jump"));
        SaveFile.Start seed = file.angleSolver.seed;
        int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick;

        AngleSolverState probe = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, probe);
        System.out.printf("[MJ] startTick=%d landingTick=%d effort=%s defInputs=%s defSprint=%s%n",
                st, lt, probe.getEffort(), probe.getDefaultInputs(), probe.getDefaultSprint());

        BlockSelection land = probe.getLandBlock();
        double[] baseBox = land != null
                ? new double[]{land.box.min.x, land.box.max.x, land.box.min.z, land.box.max.z}
                : new double[]{seed.pos[0] - 0.5, seed.pos[0] + 0.5, seed.pos[2] - 0.5, seed.pos[2] + 0.5};
        TickConstraints tc = probe.tickConstraintsOrNull(lt);
        double[] pb = LandingPad.derive(tc == null ? null : tc.getConstraints(), baseBox);
        System.out.printf("[MJ] pad x[%.3f, %.3f] z[%.3f, %.3f]%n", pb[0], pb[1], pb[2], pb[3]);

        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                return in;
            }
        };
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(
                st, new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]), seed.yaw, seed.vel[1], file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);

        VelocityFinder finder = new VelocityFinder(problem, model, anchor, lt, pad, null, 20_000L);

        VelocityFinder.Candidate a = finder.evaluate(-0.004, -0.17);
        VelocityFinder.Candidate b = finder.evaluate(-0.5041, 0.0641);
        VelocityFinder.Candidate ae = finder.evaluateThorough(-0.004, -0.17);
        VelocityFinder.Candidate be = finder.evaluateThorough(-0.5041, 0.0641);
        System.out.printf("[MJ] (-0.004,-0.17)  fast lands=%s engine lands=%s%n", a.lands, ae.lands);
        System.out.printf("[MJ] (-0.5041,0.0641) fast lands=%s engine lands=%s%n", b.lands, be.lands);

        assertTrue("engine lands (-0.004,-0.17)", ae.lands);
        assertTrue("engine lands (-0.5041,0.0641)", be.lands);
        assertTrue("fast path must land (-0.004,-0.17) like the engine", a.lands);
        assertTrue("fast path must land (-0.5041,0.0641) like the engine", b.lands);
    }
}
