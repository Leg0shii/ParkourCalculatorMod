package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.KeySwap;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.TickInputs;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KeySwapTest {

    private static final int N = 12;
    private static final ExactJumpModel MODEL = ExactJumpModel.forMcVersion("1.8.9");
    private static final TickInputs SPRINT = new TickInputs(true, false, false, false, true);

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

    private static JumpSpec looseSpec(JumpPhysicsInputs sc) {
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS,
                JumpConstraint.Cmp.GE, sc.startPos.z - 10.0, "zwall"));
        return new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, N));
    }

    private static double[] brakingTurn() {
        double[] yaws = new double[N];
        for (int t = 4; t < 8; t++) {
            yaws[t] = 180.0;
        }
        return yaws;
    }

    @Test
    public void brakingWithTheCameraTurnedAroundBecomesBackKeysWithTheCameraAhead() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = looseSpec(sc);
        KeySwap.Result swap = KeySwap.plan(MODEL, spec, brakingTurn(), true, true, false);
        assertNotNull("the turned around stretch must be swapped", swap);
        assertEquals(4, swap.changed);
        for (int t = 0; t < N; t++) {
            TickInputs keys = TickInputs.of(swap.scenario, t);
            if (t >= 4 && t < 8) {
                assertTrue("tick " + t + " brakes with S: " + keys.label(), keys.s && !keys.w && !keys.sprint);
            } else {
                assertEquals("tick " + t + " keeps its keys", SPRINT, keys);
            }
            assertEquals("tick " + t + " looks ahead", 0.0, Angles.wrapDelta(swap.yaws[t]), 1.0e-6);
        }
    }

    @Test
    public void theSwappedKeysLandOnTheExactModel() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec spec = looseSpec(sc);
        double[] turn = brakingTurn();
        KeySwap.Result swap = KeySwap.plan(MODEL, spec, turn, true, true, false);
        assertNotNull(swap);
        KeySwap.Result landed = KeySwap.realise(MODEL, spec, turn, swap, spec.constraints, 0L, new AtomicBoolean(false));
        assertNotNull("a loose wall must hold with the swapped keys", landed);
        assertEquals(4, landed.changed);
        double[] gf = landed.scenario.toGameFacings(landed.yaws);
        JumpSpec swapped = new JumpSpec(landed.scenario, spec.constraints, spec.objective);
        assertEquals(0.0, JumpConstraintCompiler.compile(swapped).maxViolation(gf, MODEL.forward(landed.scenario, gf)), 0.0);
    }

    @Test
    public void keepSprintingNeverTakesAKeyThatDropsTheSprint() {
        JumpPhysicsInputs sc = airScenario();
        KeySwap.Result swap = KeySwap.plan(MODEL, looseSpec(sc), brakingTurn(), true, true, true);
        if (swap == null) {
            return;
        }
        for (int t = 0; t < N; t++) {
            TickInputs keys = TickInputs.of(swap.scenario, t);
            assertTrue("tick " + t + " keeps W and sprint: " + keys.label(), keys.forward() && keys.sprint);
        }
    }

    @Test
    public void aStraightRunKeepsItsKeys() {
        JumpPhysicsInputs sc = airScenario();
        assertNull(KeySwap.plan(MODEL, looseSpec(sc), new double[N], true, true, false));
    }

    @Test
    public void aTickHeldByAFacingConstraintKeepsItsKeys() {
        JumpPhysicsInputs sc = airScenario();
        JumpSpec loose = looseSpec(sc);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(loose.constraints);
        cons.add(new JumpConstraint(JumpConstraint.Mode.F, 5, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.EQ, 180.0, "f"));
        KeySwap.Result swap = KeySwap.plan(MODEL, new JumpSpec(sc, cons, loose.objective), brakingTurn(), true, true, false);
        assertNotNull("the ticks around the held one may still swap", swap);
        assertEquals(SPRINT, TickInputs.of(swap.scenario, 5));
        assertEquals(180.0, Math.abs(Angles.wrapDelta(swap.yaws[5])), 1.0e-6);
        int changed = 0;
        for (int t = 0; t < N; t++) {
            if (!SPRINT.equals(TickInputs.of(swap.scenario, t))) {
                changed++;
            }
        }
        assertEquals("the count is the number of ticks whose keys differ", changed, swap.changed);
    }

    private static JumpPhysicsInputs brakeThenCoast() {
        JumpPhysicsInputs sc = TickInputs.editable(airScenario());
        for (int t = 8; t < N; t++) {
            TickInputs.NONE.applyTo(sc, t);
        }
        return sc;
    }

    @Test
    public void aSwapThatFallsShortLandsWithThrustOnAReleasedTick() {
        JumpPhysicsInputs sc = brakeThenCoast();
        double[] turn = new double[N];
        turn[6] = 180.0;
        turn[7] = 180.0;
        ForwardPath path = MODEL.forward(sc, sc.toGameFacings(turn));
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, 6, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE,
                path.posZ[6] - 2.0e-3, "run"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, N, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE,
                path.posZ[N] + 2.0e-3, "stop"));
        JumpSpec spec = new JumpSpec(sc, cons, new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, N));
        KeySwap.Result swap = KeySwap.plan(MODEL, spec, turn, true, true, false);
        assertNotNull(swap);
        assertEquals(2, swap.changed);
        KeySwap.Result stepwise = KeySwap.realise(MODEL, spec, turn, swap, spec.constraints, 0L, new AtomicBoolean(false));
        assertTrue("S brakes with 77 percent of the push, so the whole swap alone overshoots",
                stepwise == null || stepwise.changed < swap.changed);
        KeySwap.Result landed = KeySwap.compensate(MODEL, spec, swap, spec.constraints, 0L, new AtomicBoolean(false));
        assertNotNull("thrust on a released tick makes up the lost push", landed);
        int helpers = 0;
        for (int t = 0; t < N; t++) {
            TickInputs keys = TickInputs.of(landed.scenario, t);
            if (t == 6 || t == 7) {
                assertTrue("tick " + t + " keeps the back key: " + keys.label(), keys.s && !keys.w);
            } else if (t >= 8 && !TickInputs.NONE.equals(keys)) {
                assertEquals(SPRINT, keys);
                helpers++;
            } else if (t < 6) {
                assertEquals(SPRINT, keys);
            }
        }
        assertTrue("a released tick got thrust", helpers >= 1);
        assertEquals(2 + helpers, landed.changed);
        double[] gf = landed.scenario.toGameFacings(landed.yaws);
        JumpSpec swapped = new JumpSpec(landed.scenario, spec.constraints, spec.objective);
        assertEquals(0.0, JumpConstraintCompiler.compile(swapped).maxViolation(gf, MODEL.forward(landed.scenario, gf)), 0.0);
    }
}
