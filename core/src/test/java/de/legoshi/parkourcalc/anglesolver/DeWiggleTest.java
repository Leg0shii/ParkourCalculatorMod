package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.DeWiggle;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A feasibility repair leaves short same-sign turn runs behind, which read as flicks. The de-wiggle
 * pass must collapse those runs while staying strictly feasible, and must be an exact no-op wherever
 * it cannot: it may never hand back a path with more turn runs than it was given.
 */
public class DeWiggleTest {

    private static final int N = 12;
    private static final ForwardModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    /** All-air, W-held segment from rest at facing 180 (running -Z). */
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

    private static JumpSpec specWithLooseWall(JumpPhysicsInputs sc, double slack) {
        double[] straight = new double[N];
        Arrays.fill(straight, 180.0);
        double z0 = sc.startPos.z;
        double reach = MODEL.forward(sc, sc.toGameFacings(straight)).getPos(N, JumpPhysicsInputs.Axis.Z) - z0;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, z0 + reach * slack, "zwall"));
        return new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));
    }

    private static double violation(JumpPhysicsInputs sc, JumpSpec spec, double[] y) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, MODEL.forward(sc, gf));
    }

    @Test
    public void collapsesAShortSpuriousRunAndStaysFeasible() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = specWithLooseWall(sc, 1.5);

        double[] start = new double[N];
        Arrays.fill(start, 180.0);
        start[5] = Angles.wrap(180.5);
        assertEquals("the flicked start must be feasible", 0.0, violation(sc, spec, start), 0.0);

        int startRuns = DeWiggle.runs(sc.startYaw, start).size();
        assertTrue("the flick must show up as extra turn runs", startRuns >= 2);

        double[] out = DeWiggle.run(MODEL, spec, start.clone(), new AtomicBoolean(false));

        assertEquals("de-wiggling must stay strictly feasible", 0.0, violation(sc, spec, out), 0.0);
        assertTrue("the short run must be gone (was " + startRuns
                        + ", now " + DeWiggle.runs(sc.startYaw, out).size() + ")",
                DeWiggle.runs(sc.startYaw, out).size() < startRuns);
    }

    @Test
    public void longRunsAreLeftAlone() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = specWithLooseWall(sc, 1.5);

        double[] start = new double[N];
        for (int t = 0; t < N; t++) {
            double turn = t < N / 2 ? -10.0 * (t + 1) : -10.0 * (N - t);
            start[t] = Angles.wrap(180.0 + turn);
        }
        assertEquals("the arc path must be feasible", 0.0, violation(sc, spec, start), 0.0);
        for (int[] r : DeWiggle.runs(sc.startYaw, start)) {
            double mass = 0.0;
            double prev = r[0] == 0 ? sc.startYaw : start[r[0] - 1];
            for (int t = r[0]; t <= r[1]; t++) {
                mass += Angles.wrapDelta(start[t] - prev);
                prev = start[t];
            }
            assertTrue("every run must clear the short-run threshold", Math.abs(mass) >= DeWiggle.MIN_ARC_DEG);
        }

        double[] out = DeWiggle.run(MODEL, spec, start.clone(), new AtomicBoolean(false));
        assertArrayEquals("a path of long arcs must pass through bit for bit", start, out, 0.0);
    }

    @Test
    public void infeasibleStartIsLeftUntouched() {
        JumpPhysicsInputs sc = airScenario();
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, sc.startPos.z + 50.0, "unreachable"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, N));

        double[] start = new double[N];
        Arrays.fill(start, 180.0);
        start[5] = Angles.wrap(180.5);
        double[] out = DeWiggle.run(MODEL, spec, start.clone(), new AtomicBoolean(false));
        assertArrayEquals("a failed solve must pass through unchanged for honest reporting",
                start, out, 0.0);
    }
}
