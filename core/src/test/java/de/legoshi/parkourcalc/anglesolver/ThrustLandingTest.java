package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ThrustLanding;
import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThrustLandingTest {

    private static final int N = 12;
    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.8.9");

    private static JumpPhysicsInputs airScenario() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(N);
        sc.startYaw = 0f;
        sc.jumpTick = -1;
        sc.jumpPerTick = new boolean[N];
        sc.strafePerTick = new boolean[N];
        sc.speedAmplifier = new int[N];
        sc.slipPerTick = new double[N];
        sc.forwardInputPerTick = new float[N];
        sc.strafeInputPerTick = new float[N];
        sc.sprintPerTick = new boolean[N];
        for (int t = 0; t < N; t++) {
            sc.slipPerTick[t] = Double.NaN;
            sc.forwardInputPerTick[t] = TickInputs.SCALE;
            sc.sprintPerTick[t] = true;
        }
        sc.yawLockedPerTick = new boolean[N];
        sc.incomingSprint = Boolean.TRUE;
        return sc;
    }

    private static double sideReach(JumpPhysicsInputs sc) {
        double[] side = new double[N];
        java.util.Arrays.fill(side, -90.0);
        ForwardPath path = MODEL.forward(sc, sc.toGameFacings(side));
        return path.posX[N] - sc.startPos.x;
    }

    private static JumpSpec sideSpec(JumpPhysicsInputs sc, double share, Integer heldTick) {
        double reach = sideReach(sc);
        JumpConstraint.Cmp cmp = reach > 0.0 ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, N, null, JumpConstraint.Op.PLUS, cmp,
                sc.startPos.x + share * reach, "side"));
        if (heldTick != null) {
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, heldTick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.EQ, 0.0, "f"));
        }
        return new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, N));
    }

    private static double violation(JumpSpec spec, double[] yaws) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] gf = sc.toGameFacings(yaws);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, MODEL.forward(sc, gf));
    }

    @Test
    public void aWallInReachLandsFromTheRelaxation() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = sideSpec(sc, 0.6, null);
        double[] straight = new double[N];
        assertTrue("the straight run misses the side wall", violation(spec, straight) > 0.0);
        ThrustLanding landing = new ThrustLanding(spec, straight);
        assertTrue("the relaxation reaches the wall", landing.relax(0.0, 0L).violation <= ThrustLanding.FEASIBLE);
        double[] yaws = landing.land(MODEL, 0L, new AtomicBoolean(false));
        assertNotNull("the relaxed thrust must land on the exact model", yaws);
        assertEquals(0.0, violation(spec, yaws), 0.0);
    }

    @Test
    public void aWallOutOfReachIsShortInTheRelaxation() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = sideSpec(sc, 1.5, null);
        ThrustLanding landing = new ThrustLanding(spec, new double[N]);
        assertTrue("half a reach is missing", landing.relax(0.0, 0L).violation > 1.0e-3);
        assertNull(landing.land(MODEL, 0L, new AtomicBoolean(false)));
    }

    @Test
    public void aTickHeldByAFacingConstraintKeepsItsFacing() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = sideSpec(sc, 0.6, 3);
        ThrustLanding landing = new ThrustLanding(spec, new double[N]);
        ThrustLanding.Relaxed relaxed = landing.relax(0.0, 0L);
        assertTrue(relaxed.violation <= ThrustLanding.FEASIBLE);
        assertEquals("the held tick faces where it faced", 0.0, relaxed.yaws[3], 0.0);
        double[] yaws = landing.land(MODEL, 0L, new AtomicBoolean(false));
        assertNotNull(yaws);
        assertEquals(0.0, violation(spec, yaws), 0.0);
    }
}
