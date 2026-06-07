package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loads a saved DERIVE fixture (file with {@code selectedBlocks} + {@code debug}) into a
 *  {@link DeriveProblem}: rebuilds the launch scenario from the segment's seed + inputs, the real MC
 *  hitboxes from {@code selectedBlocks}, the yaw-independent feet-Y/height tracks, and a {@link DeriveOracle}.
 *  Pure Java; the SOLVE/verify pieces are all in :core, so the whole DERIVE loop runs headless. */
public final class DeriveFixtures {

    public static final double HALF = DeriveSupport.HALF;
    public static final double SIGMA_DEG = 90.0;
    public static final double FEAS_TOL = 0.0;

    /** A solid default search budget: enough CMA-ES reach + polish that a genuinely feasible constraint
     *  set is found, while staying well under a second so the algorithm search can iterate fast. */
    public static SolveCore.Budget defaultBudget() {
        return new SolveCore.Budget(28, 7000, 6, BucketAscentPolish.BALANCED);
    }

    public static SolveCore.Budget thoroughBudget() {
        return new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
    }

    public static final class Loaded {
        public final DeriveProblem problem;
        public final double[] recordedGameFacings; // the in-game executed facings for this segment, or null
        public final int startTick;
        public final int landingTick;
        public final String mcVersion;

        Loaded(DeriveProblem problem, double[] recordedGameFacings, int startTick, int landingTick, String mcVersion) {
            this.problem = problem;
            this.recordedGameFacings = recordedGameFacings;
            this.startTick = startTick;
            this.landingTick = landingTick;
            this.mcVersion = mcVersion;
        }
    }

    public static Loaded load(String fixtureName) {
        return load(fixtureName, defaultBudget());
    }

    public static Loaded load(String fixtureName, SolveCore.Budget budget) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixtureName));
        if (file == null || file.angleSolver == null) {
            throw new IllegalStateException("fixture missing angleSolver: " + fixtureName);
        }
        SaveFile.AngleSolver a = file.angleSolver;
        int startTick = a.startTick;
        int landingTick = a.landingTick;
        int n = landingTick - startTick;
        if (n <= 0) throw new IllegalStateException("bad segment in " + fixtureName);

        String mcVersion = file.mcVersion != null ? file.mcVersion : "1.8.9";
        ForwardModel model = ExactJumpModel.forMcVersion(mcVersion);

        List<SaveFile.DebugTick> dbg = file.debug;
        if (dbg == null || dbg.size() <= landingTick) {
            throw new IllegalStateException("fixture missing debug ticks: " + fixtureName);
        }

        JumpPhysicsInputs sc = buildScenario(file, startTick, n);

        // Blocks from the real picked hitboxes.
        List<AABB> obstacles = new ArrayList<>();
        AABB land = null;
        AABB start = null;
        for (SaveFile.BlockSel b : a.selectedBlocks) {
            AABB box = boxOf(b);
            if ("COLLISION".equals(b.kind)) obstacles.add(box);
            else if ("LAND".equals(b.kind)) land = box;
            else if ("START".equals(b.kind)) start = box;
        }
        if (land == null) throw new IllegalStateException("fixture has no LAND block: " + fixtureName);

        // feet-Y is yaw-independent: forward any facing track and read posY.
        double[] flat = new double[n];
        java.util.Arrays.fill(flat, sc.startYaw);
        ForwardPath yPath = model.forward(sc, sc.toGameFacings(flat));
        double[] feetY = yPath.posY.clone();

        double[] heights = new double[n + 1];
        for (int st = 0; st <= n; st++) {
            boolean sneak = dbg.get(startTick + st).sneaking;
            heights[st] = sneak ? 1.5 : 1.8;
        }

        AtomicBoolean cancel = new AtomicBoolean(false);
        DeriveOracle oracle = new DeriveOracle(sc, obstacles, land, heights, HALF, model, budget, SIGMA_DEG, FEAS_TOL, cancel);
        DeriveProblem problem = new DeriveProblem(sc, obstacles, land, start, feetY, heights, HALF, model,
                budget, SIGMA_DEG, FEAS_TOL, oracle, cancel);

        // Recorded executed facings: outgoing-facing convention (yaw after each move).
        double[] recorded = new double[n];
        boolean haveAll = true;
        for (int k = 0; k < n; k++) {
            SaveFile.DebugTick d = dbg.get(startTick + k + 1);
            recorded[k] = d.yaw;
            if (Float.isNaN(d.yaw)) haveAll = false;
        }
        return new Loaded(problem, haveAll ? recorded : null, startTick, landingTick, mcVersion);
    }

    private static JumpPhysicsInputs buildScenario(SaveFile file, int startTick, int n) {
        List<SaveFile.DebugTick> dbg = file.debug;
        List<SaveFile.Row> rows = file.rows;
        SaveFile.DebugTick seed = dbg.get(startTick);

        JumpPhysicsInputs sc = new JumpPhysicsInputs(n);
        sc.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        sc.startYaw = seed.yaw;
        sc.initialVelocity = (seed.vel != null && seed.vel.length >= 3)
                ? new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]) : Vec3dCore.ZERO;

        int jumpTickRel = -1;
        int sign = 1;
        boolean sawSign = false;
        boolean[] strafe = new boolean[n];
        boolean[] yawLocked = new boolean[n];
        int[] speedAmp = new int[n];
        boolean force45 = file.angleSolver.defaultInputs == null
                || file.angleSolver.defaultInputs.contains("45");
        for (int k = 0; k < n; k++) {
            int t = startTick + k;
            SaveFile.Row r = (t < rows.size()) ? rows.get(t) : null;
            boolean jumpRow = r != null && r.keys != null && r.keys.contains("JUMP");
            if (jumpRow && jumpTickRel < 0) jumpTickRel = k;
            boolean hasA = r != null && r.keys != null && r.keys.contains("A");
            boolean hasD = r != null && r.keys != null && r.keys.contains("D");
            if (!sawSign && (hasA || hasD)) {
                sign = hasA ? 1 : -1;
                sawSign = true;
            }
            strafe[k] = force45 && !jumpRow;
            yawLocked[k] = r != null && r.yawLocked;
            speedAmp[k] = r != null ? r.speedAmplifier : 0;
        }
        sc.jumpTick = jumpTickRel;
        sc.strafeSign = sign;
        sc.strafePerTick = strafe;
        sc.yawLockedPerTick = yawLocked;
        sc.speedAmplifier = speedAmp;
        return sc;
    }

    private static AABB boxOf(SaveFile.BlockSel b) {
        if (b.box != null && b.box.length >= 6) {
            return new AABB(new Vec3dCore(b.box[0], b.box[1], b.box[2]),
                    new Vec3dCore(b.box[3], b.box[4], b.box[5]));
        }
        return new AABB(new Vec3dCore(b.x, b.y, b.z), new Vec3dCore(b.x + 1.0, b.y + 1.0, b.z + 1.0));
    }

    /** The in-game executed facings (outgoing convention) for ticks [startTick, startTick+n) of any
     *  fixture's debug track. Used to cross-validate one file's recorded yaws against another's geometry. */
    public static double[] recordedFacings(String fixtureName, int startTick, int n) {
        SaveFile file = SaveIO.parseSafe(readFixture(fixtureName));
        if (file == null || file.debug == null || file.debug.size() <= startTick + n) {
            throw new IllegalStateException("fixture missing debug ticks: " + fixtureName);
        }
        double[] gf = new double[n];
        for (int k = 0; k < n; k++) gf[k] = file.debug.get(startTick + k + 1).yaw;
        return gf;
    }

    public static String readFixture(String name) {
        try (InputStream in = DeriveFixtures.class.getResourceAsStream("/anglesolver/" + name)) {
            if (in == null) throw new IllegalStateException("missing test fixture: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("failed to read fixture " + name, e);
        }
    }

    private DeriveFixtures() {
    }
}
