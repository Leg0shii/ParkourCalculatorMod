package de.legoshi.parkourcalc.core.anglesolver.solver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SmoothPortfolioTest {

    private static final int N = 16;
    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    private static JumpPhysicsInputs airScenario() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.startYaw = 180f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[N];
        sc.strafePerTick = new boolean[N];
        sc.speedAmplifier = new int[N];
        sc.slipPerTick = new double[N];
        Arrays.fill(sc.slipPerTick, Double.NaN);
        sc.yawLockedPerTick = new boolean[N];
        return sc;
    }

    private static ForwardPath forward(JumpPhysicsInputs sc, double[] absWrapped) {
        return MODEL.forward(sc, sc.toGameFacings(Angles.wrapAll(absWrapped.clone())));
    }

    private static double zAt(JumpPhysicsInputs sc, double[] absWrapped) {
        return forward(sc, absWrapped).getPos(N, JumpPhysicsInputs.Axis.Z);
    }

    private static double violation(JumpPhysicsInputs sc, JumpSpec spec, double[] absWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absWrapped.clone()));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, MODEL.forward(sc, gf));
    }

    private static WatchScore score(JumpPhysicsInputs sc, double[] absWrapped) {
        double[] wrapped = Angles.wrapAll(absWrapped.clone());
        return WatchScore.of(sc.startYaw, wrapped, forward(sc, wrapped));
    }

    private static double[] zigzag(double amplitudeDeg) {
        double[] y = new double[N];
        for (int k = 0; k < N; k++) {
            y[k] = Angles.wrap(180.0 + (k % 2 == 0 ? amplitudeDeg : -amplitudeDeg));
        }
        return y;
    }

    private static JumpSpec walled(JumpPhysicsInputs sc, double share, List<JumpConstraint> extra) {
        double[] straight = new double[N];
        Arrays.fill(straight, 180.0);
        double z0 = sc.startPos.z;
        double wall = z0 + (zAt(sc, straight) - z0) * share;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(extra);
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.LE, wall, "zwall"));
        return new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));
    }

    @Test
    public void aZigzagBecomesCalmerAndKeepsTheLanding() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = walled(sc, 0.3, new ArrayList<JumpConstraint>());
        double[] seed = zigzag(40.0);
        assertTrue("the zigzag seed must land", violation(sc, spec, seed) <= 0.0);
        WatchScore before = score(sc, seed);
        assertTrue(before.shakes >= 3);

        double giveBack = 1.0e-3;
        TrendFilterSmooth.Report report =
                SmoothPortfolio.smoothReport(MODEL, spec, seed.clone(), giveBack, 0L, new AtomicBoolean(false));

        assertEquals(0.0, Math.max(0.0, violation(sc, spec, report.yaws)), 0.0);
        WatchScore after = score(sc, report.yaws);
        assertTrue("smoothing must lower the watch cost: " + before + " -> " + after, after.betterThan(before));
        assertEquals("no quick reversal is left: " + after, 0, after.shakes);
        assertEquals("no wobble is left: " + after, 0, after.wobbles);
        assertTrue("the give-back holds", zAt(sc, report.yaws) <= zAt(sc, seed) + giveBack);
        assertTrue(report.stop == TrendFilterSmooth.Stop.CONVERGED || report.stop == TrendFilterSmooth.Stop.PASS_LIMIT);
    }

    @Test
    public void aFacingConstraintKeepsItsTick() {
        JumpPhysicsInputs sc = airScenario();
        double[] seed = zigzag(40.0);
        int pinned = 5;
        double facing = sc.toGameFacings(seed.clone())[pinned];
        List<JumpConstraint> extra = new ArrayList<JumpConstraint>();
        extra.add(new JumpConstraint(JumpConstraint.Mode.F, pinned, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.EQ, facing, "pin"));
        JumpSpec spec = walled(sc, 0.3, extra);
        assertTrue("the seed must land", violation(sc, spec, seed) <= 0.0);

        TrendFilterSmooth.Report report =
                SmoothPortfolio.smoothReport(MODEL, spec, seed.clone(), 1.0e-3, 0L, new AtomicBoolean(false));

        assertEquals(0.0, Math.max(0.0, violation(sc, spec, report.yaws)), 0.0);
        assertEquals(seed[pinned], Angles.wrapAll(report.yaws.clone())[pinned], 0.0);
        assertTrue(score(sc, report.yaws).betterThan(score(sc, seed)));
    }

    @Test
    public void aCalmPathComesBackAsItIs() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = walled(sc, 0.3, new ArrayList<JumpConstraint>());
        double[] seed = new double[N];
        Arrays.fill(seed, 180.0);
        TrendFilterSmooth.Report report =
                SmoothPortfolio.smoothReport(MODEL, spec, seed, 1.0e-3, 0L, new AtomicBoolean(false));
        assertSame(seed, report.yaws);
    }

    @Test
    public void aPathThatMissesIsLeftUntouched() {
        JumpPhysicsInputs sc = airScenario();
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, sc.startPos.z + 50.0, "unreachable"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));
        double[] seed = zigzag(40.0);
        TrendFilterSmooth.Report report =
                SmoothPortfolio.smoothReport(MODEL, spec, seed, 1.0e-3, 0L, new AtomicBoolean(false));
        assertSame(seed, report.yaws);
    }

    @Test
    public void theReportNamesWhyItStopped() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = walled(sc, 0.3, new ArrayList<JumpConstraint>());
        double[] seed = zigzag(40.0);

        TrendFilterSmooth.Report spent = SmoothPortfolio.smoothReport(MODEL, spec, seed.clone(), 1.0e-3,
                System.nanoTime() - 1L, new AtomicBoolean(false));
        assertEquals(TrendFilterSmooth.Stop.BUDGET, spent.stop);
        assertEquals(0.0, Math.max(0.0, violation(sc, spec, spent.yaws)), 0.0);

        TrendFilterSmooth.Report cancelled =
                SmoothPortfolio.smoothReport(MODEL, spec, seed.clone(), 1.0e-3, 0L, new AtomicBoolean(true));
        assertEquals(TrendFilterSmooth.Stop.CANCELLED, cancelled.stop);
        assertEquals(0.0, Math.max(0.0, violation(sc, spec, cancelled.yaws)), 0.0);
    }

    @Test
    public void theShapeStepNeverLeavesTheWalls() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = walled(sc, 0.3, new ArrayList<JumpConstraint>());
        ShapeSmooth shape = new ShapeSmooth(MODEL, spec);
        double[] seed = zigzag(40.0);
        for (ShapeSmooth.Norm norm : ShapeSmooth.Norm.values()) {
            double[] out = shape.run(seed, new ShapeSmooth.Weights(norm, 0.2, 1.0, 1.0, 1.0), 0L, null);
            assertTrue(norm + " left the walls", shape.feasible(out));
            assertTrue(norm + " did not calm the path", score(sc, out).betterThan(score(sc, seed)));
        }
    }
}
