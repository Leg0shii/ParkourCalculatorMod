package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Exercises the core {@link VelocityFinder} on j008-bfneo: it must reproduce the recorded jump
 *  (recorded v0 lands at the recorded spot) and return a ranked set of landing initial velocities. */
public class J008VelocityFinderTest {

    @Test
    public void findsBandAndReproducesRecorded() {
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

        // LAND block (capture selectedBlocks): x in [0,1], z in [1,2].
        VelocityFinder.Pad pad = new VelocityFinder.Pad(0.0, 1.0, 1.0, 2.0);

        VelocityFinder finder = new VelocityFinder(
                problem, pf.model, anchor, file.angleSolver.landingTick, pad, null, 20_000L);

        // 1) Reproduce the recorded jump: recorded entry v0 must land at the recorded spot (x = -0.215).
        VelocityFinder.Candidate rec = finder.evaluate(seed.vel[0], seed.vel[2]);
        System.out.printf("recorded v0=(%.4f, %.4f) -> lands=%s at x=%.4f z=%.4f support=%.4f%n",
                seed.vel[0], seed.vel[2], rec.lands, rec.landX, rec.landZ, rec.support);
        assertTrue("recorded v0 must land on the pad", rec.lands);
        // Pad x[0,1] z[1,2]; lands == the player AABB (half-width 0.3) overlaps it.
        assertTrue("landing X within pad footprint", rec.landX >= -0.3 - 1e-6 && rec.landX <= 1.3 + 1e-6);
        assertTrue("landing Z within pad footprint", rec.landZ >= 0.7 - 1e-6 && rec.landZ <= 2.3 + 1e-6);

        // The cell carries its TAS (the solved aim), so applying it needs no re-solve.
        int segTicks = file.angleSolver.landingTick - file.angleSolver.startTick;
        assertTrue("recorded candidate must carry its TAS yaws", rec.yawsGameFacing != null);
        assertEquals("TAS length == segment ticks", segTicks, rec.yawsGameFacing.length);

        // 2) Find a band: a small sweep must yield a ranked, non-empty set of landing velocities.
        VelocityFinder.Grid grid = new VelocityFinder.Grid(-0.30, 0.00, 0.05, 0.02, 0.14, 0.02);
        List<VelocityFinder.Candidate> serial = finder.sweep(grid);
        List<VelocityFinder.Candidate> ranked = VelocityFinder.rankedLanders(serial);
        System.out.println("ranked landers (top 8 of " + ranked.size() + "):");
        for (int i = 0; i < Math.min(8, ranked.size()); i++) {
            VelocityFinder.Candidate c = ranked.get(i);
            System.out.printf("  v0=(%+.3f, %+.3f) support=%.3f -> x=%.3f z=%.3f%n",
                    c.vx, c.vz, c.support, c.landX, c.landZ);
        }
        assertTrue("sweep must find landing velocities", !ranked.isEmpty());
        assertTrue("ranked solidest-first",
                ranked.get(0).support >= ranked.get(ranked.size() - 1).support);

        // 3) Parallel sweep must agree cell-for-cell with serial (deterministic, just faster).
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        long t0 = System.nanoTime();
        List<VelocityFinder.Candidate> par = finder.sweepParallel(grid, threads, null);
        long parMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals("same cell count", serial.size(), par.size());
        int landDiffs = 0;
        for (int i = 0; i < serial.size(); i++) {
            if (serial.get(i).lands != par.get(i).lands) landDiffs++;
        }
        System.out.printf("parallel sweep: %d cells on %d threads in %d ms, %d/%d lands-flag diffs vs serial%n",
                par.size(), threads, parMs, landDiffs, serial.size());
        assertEquals("parallel == serial lands classification", 0, landDiffs);
    }

    @Test
    public void usesRecordedMovementSamplesNotRawKeys() {
        ProblemFixture pf = ProblemFixture.load("solve", "j008-bfneo");
        final SaveFile file = pf.file;
        SaveFile.Start seed = file.angleSolver.seed;

        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                s.setEffort(pf.expect.effort());
                s.setDefaultInputs(AngleSolverState.InputMode.KEEP);
                s.setDefaultSprint(AngleSolverState.SprintMode.DERIVE);
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
        int lt = file.angleSolver.landingTick;

        List<TickState> zeroForward = new ArrayList<>();
        for (int i = 0; i < file.rows.size(); i++) {
            zeroForward.add(new TickState(Vec3dCore.ZERO, false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN, false, 0f, 0f));
        }

        VelocityFinder fallback = new VelocityFinder(problem, pf.model, anchor, lt, pad, null, 20_000L);
        VelocityFinder withSamples = new VelocityFinder(problem, pf.model, anchor, lt, pad, zeroForward, 20_000L);

        VelocityFinder.Candidate fb = fallback.evaluate(seed.vel[0], seed.vel[2]);
        VelocityFinder.Candidate ws = withSamples.evaluate(seed.vel[0], seed.vel[2]);

        assertTrue("baseline: raw-key fallback reproduces the recorded landing", fb.lands);
        boolean differs = ws.lands != fb.lands
                || ws.yawsGameFacing == null
                || Math.abs(ws.landX - fb.landX) + Math.abs(ws.landZ - fb.landZ) > 0.05;
        assertTrue("finder must consume the recorded movement samples, not the raw row keys", differs);
    }
}
