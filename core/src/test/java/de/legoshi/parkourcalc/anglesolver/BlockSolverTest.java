package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.BlockSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.solver.SweptCollision;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BlockSolverTest {

    private static final double HALF = 0.3;
    private static final int START = 32;
    private static final int LANDING = 43;
    private static final int N = LANDING - START; // 11

    @Test
    public void recordedSolutionIsSweptClean() {
        SaveFile file = SaveIO.parseSafe(readFixture("j154.json"));
        assertNotNull(file);
        List<TickState> route = new ArrayList<>();
        for (SaveFile.DebugTick d : file.debug) route.add(toTickState(d));

        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(route);
        double[] facings = new double[N];
        for (int k = 0; k < N; k++) facings[k] = route.get(START + k + 1).yaw; // outgoing facing
        de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath path = model.forward(sc, facings);

        List<AABB> blocks = Arrays.asList(
                cube(-1601, 88, 4929), cube(-1601, 89, 4929),
                box(-1600.0, -1599.5, 89.25, 89.75, 4929.25, 4929.75));

        // The recorded route was a successful in-game jump, so its (collision-free) path must be swept-clean.
        for (int k = 0; k < N; k++) {
            SweptCollision.Hit h = SweptCollision.firstHit(
                    path.posX[k], path.posY[k], path.posZ[k],
                    path.posX[k + 1], path.posY[k + 1], path.posZ[k + 1], HALF, 1.8, blocks);
            if (h.any()) {
                System.out.println("FALSE POSITIVE? swept '" + h.axis + "' at move k=" + k
                        + " p0=(" + path.posX[k] + "," + path.posY[k] + "," + path.posZ[k] + ")"
                        + " p1=(" + path.posX[k + 1] + "," + path.posY[k + 1] + "," + path.posZ[k + 1] + ")");
            }
            assertTrue("SweptCollision false positive on the known-good recorded solution at k=" + k, !h.any());
        }
    }

    @Test
    public void solvesJ154WithoutSweptCollision() {
        SaveFile file = SaveIO.parseSafe(readFixture("j154.json"));
        assertNotNull(file);
        List<TickState> route = new ArrayList<>();
        for (SaveFile.DebugTick d : file.debug) route.add(toTickState(d));

        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        JumpPhysicsInputs sc = scenario(route);

        double[] land = {-1601 - HALF, -1600 + HALF, 4928 - HALF, 4929 + HALF};
        List<JumpConstraint> footprints = new ArrayList<>();
        footprints.add(fp(N, true, true, land[0]));
        footprints.add(fp(N, true, false, land[1]));
        footprints.add(fp(N, false, true, land[2]));
        footprints.add(fp(N, false, false, land[3]));

        // Real block hitboxes: two cubes + the head.
        List<AABB> blocks = Arrays.asList(
                cube(-1601, 88, 4929),
                cube(-1601, 89, 4929),
                box(-1600.0, -1599.5, 89.25, 89.75, 4929.25, 4929.75));
        List<BlockSolver.Obstacle> obstacles = new ArrayList<>();
        for (AABB b : blocks) obstacles.add(new BlockSolver.Obstacle(b));

        double[] heights = new double[N + 1];
        Arrays.fill(heights, 1.8);

        List<Objective> objectives = Arrays.asList(
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, N),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, N),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, N),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        SolveCore.Budget budget = new SolveCore.Budget(16, 5000, 2, BucketAscentPolish.FAST);
        BlockSolver.Result r = new BlockSolver().solve(model, sc, footprints, land, obstacles, heights, objectives,
                budget, 90.0, 0.0, 40, new AtomicBoolean(false));
        assertNotNull(r);

        // The forced-crossing-tick homotopy planner solves the j154 SE-corner wrap headlessly.
        assertTrue("BlockSolver should solve j154 (clean + landed): " + describe(r), r.ok());

        // Safety guarantee: the solver must NEVER report success for a solution that actually collides.
        if (r.ok()) {
            for (int k = 0; k < N; k++) {
                SweptCollision.Hit h = SweptCollision.firstHit(
                        r.path.posX[k], r.path.posY[k], r.path.posZ[k],
                        r.path.posX[k + 1], r.path.posY[k + 1], r.path.posZ[k + 1], HALF, 1.8, blocks);
                assertTrue("ok() reported but MC would clamp '" + h.axis + "' into T" + (START + k + 2), !h.any());
            }
            double lx = r.path.posX[N], lz = r.path.posZ[N];
            assertTrue("ok() reported but did not land on the pad", lx >= land[0] && lx <= land[1] && lz >= land[2] && lz <= land[3]);
        }
    }

    private static String describe(BlockSolver.Result r) {
        int n = r.path.posX.length - 1;
        return "clean=" + r.clean + " landed=" + r.landed + " faces=" + r.faces.size()
                + " land=(" + r.path.posX[n] + "," + r.path.posZ[n] + ")";
    }

    private static JumpPhysicsInputs scenario(List<TickState> route) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        TickState seed = route.get(START);
        sc.startPos = seed.position;
        sc.startYaw = seed.yaw;
        sc.initialVelocity = seed.velocity;
        sc.jumpTick = 0;
        sc.strafeSign = 1;
        boolean[] strafe = new boolean[N];
        boolean[] jump = new boolean[N];
        double[] slip = new double[N];
        for (int k = 0; k < N; k++) {
            strafe[k] = (k != 0);
            jump[k] = (k == 0);                          // sprint-jump fires on the (grounded) first tick
            slip[k] = (k == 0) ? 0.60 : Double.NaN;      // tick 0 is the takeoff; the arc is airborne
        }
        sc.strafePerTick = strafe;
        sc.jumpPerTick = jump;
        sc.slipPerTick = slip;
        return sc;
    }

    private static JumpConstraint fp(int t, boolean axisX, boolean ge, double rhs) {
        return new JumpConstraint(axisX ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z, t, null,
                JumpConstraint.Op.PLUS, ge ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, rhs, "fp");
    }

    private static AABB cube(int x, int y, int z) {
        return new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0));
    }

    private static AABB box(double xlo, double xhi, double ylo, double yhi, double zlo, double zhi) {
        return new AABB(new Vec3dCore(xlo, ylo, zlo), new Vec3dCore(xhi, yhi, zhi));
    }

    private static TickState toTickState(SaveFile.DebugTick d) {
        Vec3dCore pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
        Vec3dCore vel = (d.vel != null && d.vel.length >= 3)
                ? new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]) : Vec3dCore.ZERO;
        double angle = d.collisionAngle == null ? Double.NaN : d.collisionAngle;
        return new TickState(pos, d.onGround, d.sneaking, d.wallCollision, d.yaw,
                Collections.<Vec3dCore>emptyList(), vel, d.softCollision, angle);
    }

    private static String readFixture(String name) {
        try (InputStream in = BlockSolverTest.class.getResourceAsStream("/anglesolver/" + name)) {
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
