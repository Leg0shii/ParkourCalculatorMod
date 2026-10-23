package de.legoshi.parkourcalc.core.anglesolver.velocity;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class J008VelocityTest {

    private static final String GOLDEN_RESOURCE = "/golden/j008-velfield-9.bin";
    private static final int N = 9;
    private static final double VX_LO = -0.30, VX_HI = 0.00, VZ_LO = 0.00, VZ_HI = 0.16;

    private static VelocityFinder shared;

    private static VelocityFinder.ProblemFactory problemOf(SaveFile file, boolean rowsAsTyped, boolean stopOnFeasible,
                                                           AngleSolverState.Effort effort) {
        return new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                if (effort != null) s.setEffort(effort);
                if (stopOnFeasible) s.setStopOnFeasible(true);
                if (rowsAsTyped) {
                    s.setDefaultInputs(AngleSolverState.InputMode.KEEP);
                    s.setDefaultSprint(AngleSolverState.SprintMode.DERIVE);
                }
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                return in;
            }
        };
    }

    private static VelocityFinder.Anchor anchorOf(SaveFile file) {
        SaveFile.Start seed = file.angleSolver.seed;
        return new VelocityFinder.Anchor(file.angleSolver.startTick,
                new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]), seed.yaw, seed.vel[1], file.rows.size());
    }

    private static VelocityFinder bfneoFinder(ProblemFixture pf, boolean rowsAsTyped, List<TickState> forward) {
        return new VelocityFinder(problemOf(pf.file, rowsAsTyped, false, pf.expect.effort()), pf.model,
                anchorOf(pf.file), pf.file.angleSolver.landingTick, forward, 20_000L);
    }

    private static VelocityFinder sharedFinder() {
        if (shared == null) shared = bfneoFinder(ProblemFixture.load("solve", "j008-bfneo"), false, null);
        return shared;
    }

    @Test
    public void recordedVelocityIsClassifiedAsLanding() {
        ProblemFixture pf = ProblemFixture.load("solve", "j008-bfneo");
        SaveFile.Start seed = pf.file.angleSolver.seed;
        double recorded = sharedFinder().fieldAt(seed.vel[0], seed.vel[2]);
        assertTrue("field at the recorded (collision-free) velocity must be finite", !Double.isNaN(recorded));
        assertTrue("the recorded velocity is feasible, so the field must classify it as a hit (negative)",
                recorded < 0.0);
    }

    @Test
    public void findsBandAndReproducesRecorded() {
        ProblemFixture pf = ProblemFixture.load("solve", "j008-bfneo");
        SaveFile file = pf.file;
        SaveFile.Start seed = file.angleSolver.seed;
        VelocityFinder finder = sharedFinder();

        VelocityFinder.Candidate rec = finder.evaluate(seed.vel[0], seed.vel[2]);
        System.out.printf("recorded v0=(%.4f, %.4f) -> feasible=%s at x=%.4f z=%.4f support=%.4f%n",
                seed.vel[0], seed.vel[2], rec.lands, rec.landX, rec.landZ, rec.support);
        assertTrue("recorded v0 must be feasible", rec.lands);

        int segTicks = file.angleSolver.landingTick - file.angleSolver.startTick;
        assertTrue("recorded candidate must carry its TAS yaws", rec.yawsGameFacing != null);
        assertEquals("TAS length == segment ticks", segTicks, rec.yawsGameFacing.length);

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
        SaveFile.Start seed = pf.file.angleSolver.seed;

        List<TickState> zeroForward = new ArrayList<>();
        for (int i = 0; i < pf.file.rows.size(); i++) {
            zeroForward.add(new TickState(Vec3dCore.ZERO, false, false, false, 0f,
                    Collections.<Vec3dCore>emptyList(), Vec3dCore.ZERO, false, Double.NaN, false, 0f, 0f));
        }

        VelocityFinder fallback = bfneoFinder(pf, true, null);
        VelocityFinder withSamples = bfneoFinder(pf, true, zeroForward);

        VelocityFinder.Candidate fb = fallback.evaluate(seed.vel[0], seed.vel[2]);
        VelocityFinder.Candidate ws = withSamples.evaluate(seed.vel[0], seed.vel[2]);

        assertTrue("baseline: raw-key fallback reproduces the recorded landing", fb.lands);
        boolean differs = ws.lands != fb.lands
                || ws.yawsGameFacing == null
                || Math.abs(ws.landX - fb.landX) + Math.abs(ws.landZ - fb.landZ) > 0.05;
        assertTrue("finder must consume the recorded movement samples, not the raw row keys", differs);
    }

    private static byte[] capture(VelocityFinder finder, int threads) {
        VelocityFinder.Grid grid = new VelocityFinder.Grid(VX_LO, VX_HI, 0.05, VZ_LO, VZ_HI, 0.05);
        final VelocityFinder.Candidate[] cells = new VelocityFinder.Candidate[N * N];
        float[] z = finder.sweepFieldParallel(grid, N, N, threads, new AtomicBoolean(false),
                (r, c, f, cand) -> cells[r * N + c] = cand);

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            for (int i = 0; i < N * N; i++) {
                VelocityFinder.Candidate cand = cells[i];
                out.writeInt(Float.floatToRawIntBits(z[i]));
                out.writeLong(Double.doubleToRawLongBits(cand == null ? Double.NaN : cand.landX));
                out.writeLong(Double.doubleToRawLongBits(cand == null ? Double.NaN : cand.landZ));
                out.writeLong(Double.doubleToRawLongBits(cand == null ? Double.NaN : cand.support));
                out.writeBoolean(cand != null && cand.lands);
                out.writeBoolean(cand != null && cand.constraintsMet);
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] readGoldenResource() throws IOException {
        try (InputStream in = J008VelocityTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static Path goldenSourcePath() {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path direct = base.resolve("src/test/resources").resolve(GOLDEN_RESOURCE.substring(1));
        if (Files.isDirectory(base.resolve("src/test/resources"))) return direct;
        return base.resolve("core/src/test/resources").resolve(GOLDEN_RESOURCE.substring(1));
    }

    @Test
    public void fieldIsBitIdenticalToFrozenBaseline() throws IOException {
        byte[] current = capture(sharedFinder(), 1);
        byte[] golden = readGoldenResource();
        if (golden == null) {
            Path out = goldenSourcePath();
            Files.createDirectories(out.getParent());
            Files.write(out, current);
            System.out.println("GENERATED GOLDEN BASELINE (" + current.length + " bytes) -> " + out
                    + "  (user.dir=" + System.getProperty("user.dir") + "); re-run to assert against it");
            return;
        }
        assertArrayEquals("velocity field + candidate bits drifted from frozen baseline", golden, current);
    }

    @Test
    public void sweepIsThreadCountInvariant() {
        VelocityFinder finder = sharedFinder();
        byte[] one = capture(finder, 1);
        byte[] many = capture(finder, 8);
        assertArrayEquals("threads=1 vs threads=8 must be bit-identical", one, many);
    }

    @Test
    public void singleSolveFieldEqualsLegacyDoubleSolve() {
        VelocityFinder finder = sharedFinder();
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

    @Test
    public void hyperLandersAreReal() {
        final SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j008-hyper-fp"));
        final ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        final SaveFile.Start seed = file.angleSolver.seed;

        VelocityFinder.Grid grid = new VelocityFinder.Grid(
                seed.vel[0] - 0.02, seed.vel[0] + 0.02, 0.02,
                seed.vel[2] - 0.02, seed.vel[2] + 0.02, 0.02);

        VelocityFinder f = new VelocityFinder(problemOf(file, false, true, null), model, anchorOf(file),
                file.angleSolver.landingTick, null, 2_000L);
        f.setAccuracy(VelocityFinder.Accuracy.HYPER);
        List<VelocityFinder.Candidate> cells = f.sweep(grid);
        int landers = 0, badWalls = 0;
        double worstViol = 0.0;
        for (VelocityFinder.Candidate c : cells) {
            if (c == null || !c.lands) continue;
            landers++;
            double worst = worstWallViolation(file, model, seed, c);
            worstViol = Math.max(worstViol, worst);
            if (worst > 1e-3) badWalls++;
        }
        System.out.printf("HYPER: %d cells, %d landers, %d violate walls (worst %.5f)%n",
                cells.size(), landers, badWalls, worstViol);
        assertTrue("sweep must report HYPER landers (non-vacuous)", landers > 0);
        assertEquals("every HYPER lander's realized TAS must meet the walls", 0, badWalls);
    }

    private static double worstWallViolation(SaveFile file, ExactJumpModel model, SaveFile.Start seed,
                                             VelocityFinder.Candidate c) {
        int st = file.angleSolver.startTick, lt = file.angleSolver.landingTick, n = lt - st;
        JumpPhysicsInputs in = new JumpPhysicsInputs(n);
        in.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        in.startYaw = seed.yaw;
        in.initialVelocity = new Vec3dCore(c.vx, seed.vel[1], c.vz);
        in.forwardInputPerTick = new float[n];
        in.strafeInputPerTick = new float[n];
        boolean[] jump = new boolean[n], sprint = new boolean[n], locked = new boolean[n];
        double[] slip = new double[n];
        for (int k = 0; k < n; k++) {
            SaveFile.Row row = file.rows.get(st + k);
            boolean w = row.keys.contains("W"), s = row.keys.contains("S");
            boolean a = false, d = false, spr = row.keys.contains("SPRINT");
            if (c.force45Mask != null && k < c.force45Mask.length && c.force45Mask[k]) {
                w = true; s = false; spr = true;
                boolean strafeThis = c.strafeMask != null && k < c.strafeMask.length && c.strafeMask[k];
                a = strafeThis && c.strafeSign > 0;
                d = strafeThis && c.strafeSign < 0;
            }
            in.forwardInputPerTick[k] = 0.98F * ((w ? 1 : 0) - (s ? 1 : 0));
            in.strafeInputPerTick[k] = 0.98F * ((a ? 1 : 0) - (d ? 1 : 0));
            jump[k] = row.keys.contains("JUMP");
            sprint[k] = spr;
            slip[k] = slipFor(file, st + k);
            locked[k] = true;
        }
        in.jumpPerTick = jump; in.sprintPerTick = sprint; in.slipPerTick = slip; in.yawLockedPerTick = locked;
        ForwardPath p = model.forward(in, c.yawsGameFacing);

        double worst = 0.0;
        AngleSolverState s = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, s);
        for (Integer tickKey : s.populatedTicks()) {
            int seg = tickKey - st;
            if (seg < 0 || seg > n) continue;
            TickConstraints tc = s.tickConstraintsOrNull(tickKey);
            if (tc == null) continue;
            for (Constraint con : tc.getConstraints()) {
                if (!con.isEnabled() || con.isRange()) continue;
                double found = con.getField() == Constraint.Field.X ? p.posX[seg]
                        : con.getField() == Constraint.Field.Z ? p.posZ[seg] : Double.NaN;
                if (Double.isNaN(found)) continue;
                double v = con.getValue();
                double viol;
                switch (con.getOp()) {
                    case GE: case GT: viol = v - found; break;
                    case LE: case LT: viol = found - v; break;
                    default: viol = 0.0;
                }
                worst = Math.max(worst, viol);
            }
        }
        return worst;
    }

    private static double slipFor(SaveFile file, int tick) {
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick t : file.angleSolver.ticks) {
                if (t.tick == tick && t.override != null && t.override.slipperiness != null) {
                    String sl = t.override.slipperiness;
                    if ("DEFAULT".equals(sl)) return 0.6;
                    if ("SLIME".equals(sl)) return 0.8;
                    if ("ICE".equals(sl) || "PACKED_ICE".equals(sl)) return 0.98;
                    if ("BLUE_ICE".equals(sl)) return 0.989;
                }
            }
        }
        return Double.NaN;
    }
}
