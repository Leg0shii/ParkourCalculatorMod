package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;

public class J008VelocityFieldGoldenTest {

    private static final String GOLDEN_RESOURCE = "/golden/j008-velfield-33.bin";
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

    private static byte[] capture(int threads) {
        VelocityFinder finder = buildFinder();
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
        try (InputStream in = J008VelocityFieldGoldenTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
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
        Path nested = base.resolve("core/src/test/resources").resolve(GOLDEN_RESOURCE.substring(1));
        return nested;
    }

    @Test
    public void fieldIsBitIdenticalToFrozenBaseline() throws IOException {
        byte[] current = capture(1);
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
        byte[] one = capture(1);
        byte[] many = capture(8);
        byte[] oneAgain = capture(1);
        assertArrayEquals("threads=1 vs threads=8 must be bit-identical", one, many);
        assertArrayEquals("repeated threads=1 run must be bit-identical", one, oneAgain);
    }
}
