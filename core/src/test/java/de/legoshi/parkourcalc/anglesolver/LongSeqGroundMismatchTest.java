package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Multi-jump fix on long-seq-with-ground.json. The solve window [startTick=14, landingTick=40] starts
 *  airborne (an earlier jump's arc), lands on a block at abs 26, runs on the ground 26..33, then jumps
 *  again at abs 33. Ground/air is taken per tick from the slip annotation (default AIR + the DEFAULT
 *  overrides on abs 26..33) and the JUMP at abs 33 fires from the ground, so the byte-exact model now
 *  reproduces the real-sim path across the landing + second jump, and the segment lands in the LAND box. */
public class LongSeqGroundMismatchTest {

    private static final int START = 14;
    private static final int LANDING = 40;
    private static final int N = LANDING - START; // 26

    // LAND footprint (block [-231,-230]x[-444,-443] grown by the hitbox half-width 0.3), the T41 constraint.
    private static final double LAND_XLO = -231.3, LAND_XHI = -229.7, LAND_ZLO = -444.3, LAND_ZHI = -442.7;

    @Test
    public void perTickGroundReproducesTheRecordedMultiJumpPath() {
        SaveFile f = SaveIO.parseSafe(readFixture("long-seq-with-ground.json"));
        assertNotNull(f);
        assertNotNull("fixture must carry recorded debug values", f.debug);
        SaveFile.DebugTick[] route = f.debug.toArray(new SaveFile.DebugTick[0]);

        // Real game facing that produced each move (outgoing facing == yaw recorded at the resulting tick).
        double[] facings = new double[N];
        for (int k = 0; k < N; k++) facings[k] = route[START + k + 1].yaw;

        JumpPhysicsInputs sc = scenarioFromFile(route, /*force45=*/false); // baseline was W-only
        ForwardPath path = ExactJumpModel.forMcVersion("1.8.9").forward(sc, facings);

        double maxErr = 0.0;
        for (int k = 0; k <= N; k++) {
            maxErr = Math.max(maxErr, Math.hypot(
                    path.posX[k] - route[START + k].pos[0], path.posZ[k] - route[START + k].pos[2]));
        }
        System.out.printf("max horiz err vs real sim = %.2e%n", maxErr);
        System.out.printf("model  T41 = (%.7f, %.7f) inBox=%s%n", path.posX[N], path.posZ[N], inBox(path.posX[N], path.posZ[N]));
        System.out.printf("recorded T41 = (%.7f, %.7f) inBox=%s%n", route[LANDING].pos[0], route[LANDING].pos[2],
                inBox(route[LANDING].pos[0], route[LANDING].pos[2]));

        // Per-tick ground makes the model byte-exact across the landing + second jump (X/Z).
        assertTrue("per-tick-ground model reproduces the recorded multi-jump path (err=" + maxErr + ")", maxErr < 1.0e-6);
        assertTrue("model lands in the LAND footprint", inBox(path.posX[N], path.posZ[N]));
    }

    @Test
    public void solverNowFindsAFacingThatLandsInTheBox() {
        SaveFile f = SaveIO.parseSafe(readFixture("long-seq-with-ground.json"));
        assertNotNull(f);
        SaveFile.DebugTick[] route = f.debug.toArray(new SaveFile.DebugTick[0]);

        // W-only here: the recorded facings are a known feasible point, so the search has a basin to find.
        JumpPhysicsInputs sc = scenarioFromFile(route, /*force45=*/false);

        // The LAND footprint as the landing-tick constraint, minimising X (the file's objective).
        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(face(JumpConstraint.Mode.X, JumpConstraint.Cmp.GE, LAND_XLO));
        cons.add(face(JumpConstraint.Mode.X, JumpConstraint.Cmp.LE, LAND_XHI));
        cons.add(face(JumpConstraint.Mode.Z, JumpConstraint.Cmp.GE, LAND_ZLO));
        cons.add(face(JumpConstraint.Mode.Z, JumpConstraint.Cmp.LE, LAND_ZHI));
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, N);

        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        SolveCore.Budget budget = new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
        double[] yaws = SolveCore.optimize(model, new JumpSpec(sc, cons, obj), budget, 90.0, 0.0, new AtomicBoolean(false));
        assertNotNull(yaws);
        ForwardPath path = model.forward(sc, sc.toGameFacings(yaws));
        System.out.printf("solved T41 = (%.7f, %.7f) inBox=%s%n", path.posX[N], path.posZ[N], inBox(path.posX[N], path.posZ[N]));
        assertTrue("solver now lands inside the LAND footprint", inBox(path.posX[N], path.posZ[N]));
    }

    @Test
    public void startFootprintIsPinnedToTheWrongTick() {
        SaveFile f = SaveIO.parseSafe(readFixture("long-seq-with-ground.json"));
        assertNotNull(f);
        SaveFile.DebugTick[] route = f.debug.toArray(new SaveFile.DebugTick[0]);

        // (1) Where does the recorded path actually sit inside the START footprint (block -238/-448 +-0.3)?
        List<Integer> inStart = new ArrayList<>();
        for (int t = START; t <= LANDING; t++) {
            if (within(route[t].pos[0], -238.3, -236.7) && within(route[t].pos[2], -448.3, -446.7)) inStart.add(t);
        }
        System.out.println("baseline is inside the START footprint at abs ticks " + inStart
                + "; block-solving pinned the START constraint at tick 32");
        assertTrue("baseline passes through the START footprint", !inStart.isEmpty());
        assertTrue("but never at tick 32, where the constraint is pinned", !inStart.contains(32));

        // (2) With START pinned at tick 32 AND the LAND footprint at tick 40, no facing can meet all four
        //     (you cannot be back on START at tick 32 and still reach LAND by tick 40) -> the in-game 0/4.
        JumpPhysicsInputs sc = scenarioFromFile(route, /*force45=*/true);
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        SolveCore.Budget budget = new SolveCore.Budget(48, 12000, 16, BucketAscentPolish.THOROUGH);
        List<JumpConstraint> four = new ArrayList<>();
        box(four, 32 - START, -238.3, -236.7, -448.3, -446.7);
        box(four, 40 - START, -231.3, -229.7, -444.3, -442.7);
        double[] yaws = SolveCore.optimize(model, new JumpSpec(sc, four, new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, N)),
                budget, 90.0, 0.0, new AtomicBoolean(false));
        ForwardPath path = model.forward(sc, sc.toGameFacings(yaws));
        int met = (within(path.posX[18], -238.3, -236.7) ? 1 : 0) + (within(path.posZ[18], -448.3, -446.7) ? 1 : 0)
                + (within(path.posX[26], -231.3, -229.7) ? 1 : 0) + (within(path.posZ[26], -444.3, -442.7) ? 1 : 0);
        System.out.printf("START@32 + LAND@40 solve meets %d/4 (T33=%.3f,%.3f ; T41=%.3f,%.3f)%n",
                met, path.posX[18], path.posZ[18], path.posX[26], path.posZ[26]);
        assertTrue("the START@32 + LAND@40 combination is infeasible (matches the in-game 0/4)", met < 4);
    }

    private static void box(List<JumpConstraint> out, int seg, double xlo, double xhi, double zlo, double zhi) {
        out.add(new JumpConstraint(JumpConstraint.Mode.X, seg, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, xlo, "xlo"));
        out.add(new JumpConstraint(JumpConstraint.Mode.X, seg, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, xhi, "xhi"));
        out.add(new JumpConstraint(JumpConstraint.Mode.Z, seg, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, zlo, "zlo"));
        out.add(new JumpConstraint(JumpConstraint.Mode.Z, seg, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, zhi, "zhi"));
    }

    private static boolean within(double v, double lo, double hi) {
        return v >= lo - 1.0e-3 && v <= hi + 1.0e-3;
    }

    /** Scenario built the way AngleSolverEngine.buildPhys now does for this file's authored state. */
    private static JumpPhysicsInputs scenarioFromFile(SaveFile.DebugTick[] route, boolean force45) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        SaveFile.DebugTick seed = route[START];
        sc.startPos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
        sc.startYaw = seed.yaw;
        sc.initialVelocity = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
        sc.strafeSign = 1;
        int[] amp = new int[N];
        Arrays.fill(amp, 2); // Speed II throughout
        sc.speedAmplifier = amp;
        sc.yawLockedPerTick = new boolean[N];

        // Ground/air exactly as authored: default AIR, with DEFAULT (0.60) on abs 26..33 (rel 12..19).
        double[] slip = new double[N];
        Arrays.fill(slip, Double.NaN);
        for (int k = 12; k <= 19; k++) slip[k] = 0.60;
        sc.slipPerTick = slip;

        // JUMP row at abs 33 (rel 19); the earlier jumps (rows 7/9/11) are before the window.
        boolean[] jump = new boolean[N];
        jump[19] = true;
        sc.jumpPerTick = jump;

        boolean[] strafe = new boolean[N];
        for (int k = 0; k < N; k++) strafe[k] = force45 && !(jump[k] && !Double.isNaN(slip[k]));
        sc.strafePerTick = strafe;
        return sc;
    }

    private static JumpConstraint face(JumpConstraint.Mode mode, JumpConstraint.Cmp cmp, double v) {
        return new JumpConstraint(mode, N, null, JumpConstraint.Op.PLUS, cmp, v, "land");
    }

    private static boolean inBox(double x, double z) {
        return x >= LAND_XLO && x <= LAND_XHI && z >= LAND_ZLO && z <= LAND_ZHI;
    }

    private static String readFixture(String name) {
        try (InputStream in = LongSeqGroundMismatchTest.class.getResourceAsStream("/anglesolver/" + name)) {
            assertNotNull("missing test fixture: " + name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("failed to read fixture " + name, e);
        }
    }
}
