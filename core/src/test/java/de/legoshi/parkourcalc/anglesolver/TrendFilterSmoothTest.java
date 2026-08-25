package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The one give-back-constrained order-1 trend filter (P6). It must iron a wiggly feasible path toward
 * fewer turn reversals against a single shared reference objective, never crossing a wall and never
 * spending more than its one give-back budget; where every tick is load-bearing it is an exact no-op.
 * Also checks the exact O(n) Condat taut-string solver against a brute-force total-variation reference.
 */
public class TrendFilterSmoothTest {

    private static final int N = 8;
    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    private static JumpPhysicsInputs airScenario() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.startYaw = 180f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[N];
        sc.strafePerTick = new boolean[N];
        sc.speedAmplifier = new int[N];
        sc.slipPerTick = new double[N];
        for (int t = 0; t < N; t++) sc.slipPerTick[t] = Double.NaN;
        sc.yawLockedPerTick = new boolean[N];
        return sc;
    }

    private static double zAt(JumpPhysicsInputs sc, double[] absWrapped) {
        return MODEL.forward(sc, sc.toGameFacings(absWrapped)).getPos(N, JumpPhysicsInputs.Axis.Z);
    }

    private static double[] zigzag(double amplitudeDeg) {
        double[] y = new double[N];
        for (int k = 0; k < N; k++) y[k] = Angles.wrap(k % 2 == 0 ? amplitudeDeg : -amplitudeDeg);
        return y;
    }

    private static int reversals(double anchor, double[] y) {
        return Angles.reversals(anchor, Angles.wrapAll(y), Angles.REVERSAL_FLOOR_DEG);
    }

    @Test
    public void underdeterminedZigzagSmoothsWithoutClippingOrOverspendingBudget() {
        JumpPhysicsInputs sc = airScenario();
        double[] straight = new double[N];
        java.util.Arrays.fill(straight, 180.0);
        double z0 = sc.startPos.z;
        double wall = z0 + (zAt(sc, straight) - z0) * 0.75;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, wall, "zwall"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        double[] start = zigzag(120.0);
        double startZ = zAt(sc, start);
        assertTrue("zigzag start must be feasible", startZ >= wall);
        int startRev = reversals(sc.startYaw, start);
        assertTrue("the zigzag must start with several reversals", startRev >= 3);

        double budget = TrendFilterSmooth.MAX_GIVE_BACK;
        double[] out = TrendFilterSmooth.smooth(MODEL, spec, start.clone(), budget, 0L, new AtomicBoolean(false));

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(out));
        assertEquals("trend filter must stay strictly feasible",
                0.0, compiled.maxViolation(gf, MODEL.forward(sc, gf)), 0.0);
        assertTrue("reversals must not grow (was " + startRev + ", now " + reversals(sc.startYaw, out) + ")",
                reversals(sc.startYaw, out) <= startRev);
        assertTrue("the trend filter should remove reversals", reversals(sc.startYaw, out) < startRev);
        assertTrue("give-back must stay within the single budget (MIN Z)",
                zAt(sc, Angles.wrapAll(out)) <= startZ + budget);
    }

    @Test
    public void infeasibleStartIsLeftUntouched() {
        JumpPhysicsInputs sc = airScenario();
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, sc.startPos.z + 5.0, "unreachable"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        double[] start = zigzag(120.0);
        double[] out = TrendFilterSmooth.smooth(MODEL, spec, start, 0L, new AtomicBoolean(false));
        assertArrayEquals("a failed solve must pass through unchanged for honest reporting",
                start, out, 0.0);
    }

    @Test
    public void straightRunTradesNoMoreThanTheSingleBudget() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = new JumpSpec(sc, new ArrayList<JumpConstraint>(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, N));

        double[] start = new double[N];
        java.util.Arrays.fill(start, 90.0);
        double startX = MODEL.forward(sc, sc.toGameFacings(start)).getPos(N, JumpPhysicsInputs.Axis.X);
        double budget = 1.0e-3;
        double[] out = TrendFilterSmooth.smooth(MODEL, spec, start.clone(), budget, 0L, new AtomicBoolean(false));
        double outX = MODEL.forward(sc, sc.toGameFacings(Angles.wrapAll(out))).getPos(N, JumpPhysicsInputs.Axis.X);
        assertTrue("give-back must never exceed the single budget (was " + (outX - startX) + " > " + budget + ")",
                outX <= startX + budget);
        assertTrue("a straight run has no reversals to remove", reversals(sc.startYaw, out) == 0);
    }

    @Test
    public void condatTautStringMatchesBruteForceTV() throws Exception {
        Method m = TrendFilterSmooth.class.getDeclaredMethod("condatTV", double[].class, double.class);
        m.setAccessible(true);
        double[] in = {3.0, 3.5, -2.0, -1.8, 5.0, 5.2, 5.1, 0.0, 0.1, -4.0, -4.1, 2.0};
        for (double lam : new double[]{0.1, 0.5, 2.0, 8.0}) {
            double[] out = (double[]) m.invoke(null, in, lam);
            double objSolver = tvObjective(in, out, lam);
            double[] best = coordinateDescentTV(in, lam);
            double objRef = tvObjective(in, best, lam);
            assertTrue("Condat TV (obj " + objSolver + ") must be <= a refined reference (" + objRef + ") at lambda " + lam,
                    objSolver <= objRef + 1.0e-6);
        }
    }

    private static double tvObjective(double[] y, double[] x, double lambda) {
        double f = 0.0;
        for (int i = 0; i < y.length; i++) f += 0.5 * (x[i] - y[i]) * (x[i] - y[i]);
        for (int i = 1; i < x.length; i++) f += lambda * Math.abs(x[i] - x[i - 1]);
        return f;
    }

    private static double[] coordinateDescentTV(double[] y, double lambda) {
        double[] x = y.clone();
        double[] steps = {1.0, 0.25, 0.0625, 0.015625, 0.001};
        for (double s : steps) {
            boolean moved = true;
            int guard = 0;
            while (moved && guard++ < 5000) {
                moved = false;
                for (int i = 0; i < x.length; i++) {
                    double base = tvObjective(y, x, lambda);
                    double orig = x[i];
                    x[i] = orig + s;
                    double up = tvObjective(y, x, lambda);
                    x[i] = orig - s;
                    double dn = tvObjective(y, x, lambda);
                    if (up < base && up <= dn) { x[i] = orig + s; moved = true; }
                    else if (dn < base) { x[i] = orig - s; moved = true; }
                    else x[i] = orig;
                }
            }
        }
        return x;
    }
}
