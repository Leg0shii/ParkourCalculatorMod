package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphContext;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RunUpSweepNode;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RunUpSweepNodeTest {

    @Test
    public void catalogRegistrationAndDefaults() {
        NodeType type = NodeCatalog.byId("runUpSweep");
        assertNotNull("runUpSweep must be registered", type);
        assertEquals("Run-up sweep", type.label);
        ParamValues p = type.defaultParams();
        assertEquals(30, p.getInt("budgetSec"));
        assertEquals(0.1, p.getDouble("windowDeg"), 0.0);
        assertEquals(400, p.getInt("maxBuckets"));
        assertEquals(300, p.getInt("stage1Ms"));
        assertEquals(6, p.getInt("topK"));
        assertEquals(3, p.getInt("stage2Sec"));
        assertEquals("both", p.getString("positionMode"));
        assertEquals("budgetSec", type.budgetParam);
    }

    @Test
    public void noChainReturnsMinusOne() {
        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, 5, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, 1.0, "x"));
        assertEquals(-1, RunUpSweepNode.leadingChain(cons, 10));
    }

    @Test
    public void leadingDfChainDetected() {
        int lead = 4;
        List<JumpConstraint> cons = dfChain(lead);
        assertEquals(lead, RunUpSweepNode.leadingChain(cons, 10));
    }

    @Test
    public void enumerateAlwaysIncludesCenterBucket() {
        List<Double> zero = RunUpSweepNode.enumerate(0.0F, false, -161.38, 0.0, 400);
        assertEquals("windowDeg 0 yields only the incumbent bucket", 1, zero.size());
        assertEquals(-161.38, zero.get(0), 0.0);
    }

    @Test
    public void enumerateRespectsWindowAndMax() {
        List<Double> wide = RunUpSweepNode.enumerate(0.0F, false, -161.38, 0.1, 400);
        assertTrue("a 0.1 deg window spans several sine buckets", wide.size() > 1);
        assertEquals("center is always first", -161.38, wide.get(0), 0.0);

        Set<Integer> buckets = new HashSet<>();
        double half = 0.1 + 1.0e-9;
        for (double y : wide) {
            assertTrue("sample stays within the window", Math.abs(y - (-161.38)) <= half);
            buckets.add(RunUpSweepNode.sineBucket(RunUpSweepNode.gf0(0.0F, false, y)));
        }
        assertEquals("every enumerated yaw is a distinct sine bucket", wide.size(), buckets.size());

        List<Double> capped = RunUpSweepNode.enumerate(0.0F, false, -161.38, 0.1, 3);
        assertTrue("maxBuckets caps the enumeration", capped.size() <= 3);

        List<Double> one = RunUpSweepNode.enumerate(0.0F, false, -161.38, 0.1, 1);
        assertEquals("maxBuckets 1 yields only the center", 1, one.size());
    }

    @Test
    public void freeStartBoxIntersectedIntoTakeoffBox() throws Exception {
        int n = 12;
        int lead = 4;
        JumpPhysicsInputs sc = phys(n, lead);
        JumpSpec spec = new JumpSpec(sc, dfChain(lead),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n));

        double h = 1.0e-3;
        StartBox freeBox = new StartBox(sc.startPos.x, sc.startPos.z, 0.0, 0.0,
                sc.startPos.x - h, sc.startPos.x + h, sc.startPos.z - h, sc.startPos.z + h,
                0.0, 0.0, 0.0, 0.0);

        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        GraphContext ctx = new GraphContext(spec, model, freeBox, null, 1.0e-9,
                new AtomicBoolean(false), null, true, null);

        ParamValues p = NodeCatalog.byId("runUpSweep").defaultParams()
                .set("windowDeg", 0.0).set("maxBuckets", 1)
                .set("budgetSec", 3).set("stage1Ms", 100).set("topK", 1).set("stage2Sec", 1)
                .set("positionMode", "free");
        RunUpSweepNode node = new RunUpSweepNode(p);

        Method prep = RunUpSweepNode.class.getDeclaredMethod("prep", GraphContext.class, double.class, int.class);
        prep.setAccessible(true);
        Object bucket = prep.invoke(node, ctx, (double) sc.startYaw, lead);
        assertNotNull("prep must produce a takeoff box for the center bucket", bucket);
        double xLo = boxField(bucket, "xLo"), xHi = boxField(bucket, "xHi");
        double zLo = boxField(bucket, "zLo"), zHi = boxField(bucket, "zHi");
        assertTrue("the tight free box must bound the folded takeoff box in X",
                !Double.isInfinite(xLo) && !Double.isInfinite(xHi));
        assertTrue("the tight free box must bound the folded takeoff box in Z",
                !Double.isInfinite(zLo) && !Double.isInfinite(zHi));
        assertEquals("takeoff box X width equals the StartBox width", 2.0 * h, xHi - xLo, 1.0e-9);
        assertEquals("takeoff box Z width equals the StartBox width", 2.0 * h, zHi - zLo, 1.0e-9);

        node.execute(ctx, null, new AtomicBoolean(false), 0L);
        Vec3dCore start = ctx.scenario.startPos;
        assertTrue("committed start X stays inside the StartBox",
                start.x >= freeBox.pxLo - 1.0e-9 && start.x <= freeBox.pxHi + 1.0e-9);
        assertTrue("committed start Z stays inside the StartBox",
                start.z >= freeBox.pzLo - 1.0e-9 && start.z <= freeBox.pzHi + 1.0e-9);
    }

    private static double boxField(Object bucket, String name) throws Exception {
        Field f = bucket.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getDouble(bucket);
    }

    private static JumpPhysicsInputs phys(int n, int jumpTick) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(n);
        p.startPos = new Vec3dCore(10.0, 100.0, 20.0);
        p.startYaw = -45.0F;
        p.initialVelocity = new Vec3dCore(0.0, 0.0, 0.0);
        p.startBox = StartBox.pinned(p.startPos.x, p.startPos.z, 0.0, 0.0);
        boolean[] jumps = new boolean[n];
        if (jumpTick >= 0 && jumpTick < n) jumps[jumpTick] = true;
        p.jumpTick = jumpTick;
        p.jumpPerTick = jumps;
        p.strafePerTick = new boolean[n];
        p.speedAmplifier = new int[n];
        double[] slip = new double[n];
        Arrays.fill(slip, 0.6);
        p.slipPerTick = slip;
        p.yawLockedPerTick = new boolean[n];
        float[] fwd = new float[n];
        Arrays.fill(fwd, 0.98F);
        p.forwardInputPerTick = fwd;
        p.strafeInputPerTick = new float[n];
        boolean[] sprint = new boolean[n];
        Arrays.fill(sprint, true);
        p.sprintPerTick = sprint;
        p.incomingSprint = Boolean.TRUE;
        p.incomingAmp = 0;
        return p;
    }

    private static List<JumpConstraint> dfChain(int lead) {
        List<JumpConstraint> cons = new ArrayList<>();
        for (int t = 1; t < lead; t++) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, t, t - 1, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.EQ, 0.0, "dF" + t));
        }
        return cons;
    }
}
