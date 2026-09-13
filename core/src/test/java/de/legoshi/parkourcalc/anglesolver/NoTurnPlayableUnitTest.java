package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoTurnPlayableUnitTest {

    private static NoTurnResult result(double objective, double... yaws) {
        int[] combos = {NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W};
        boolean[] sprint = {true, true, true};
        return new NoTurnResult(combos, sprint, NoTurnKeys.WA, false, 0, 0, objective, 0.0, 0.0, 0.0, yaws);
    }

    @Test
    public void reversalsCountSignChangesOfTheYawDeltas() {
        assertEquals(0, NoTurnCertifier.reversals(new double[]{10, 20, 30, 40}));
        assertEquals(0, NoTurnCertifier.reversals(new double[]{10, 10, 10, 10}));
        assertEquals(2, NoTurnCertifier.reversals(new double[]{10, 25, 24, 39}));
        assertEquals(3, NoTurnCertifier.reversals(new double[]{0, 10, 0, 10, 0}));
        assertEquals(0, NoTurnCertifier.reversals(null));
    }

    @Test
    public void fewerReversalsOutrankObjectiveAndMaxTurn() {
        NoTurnResult flicky = result(-10.0, 10, 25, 24, 39, 54);
        NoTurnResult steady = result(-10.5, 10, 25, 30, 39, 54);
        assertTrue(StructurePoolDriver.betterResult(true, steady, flicky));
        assertFalse(StructurePoolDriver.betterResult(true, flicky, steady));
        NoTurnResult sharperSteady = result(-9.0, 10, 40, 41, 42, 54);
        assertTrue(StructurePoolDriver.betterResult(true, sharperSteady, flicky));
    }

    @Test
    public void giveBackIsTheMarginAboveTheNearLandingWall() {
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, 4, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, -12.0, "far"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, 4, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, -11.0, "near"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.X, 4, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, -5.0, "cap"));
        cons.add(new JumpConstraint(JumpConstraint.Mode.Z, 4, null, JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 3.0, "side"));
        JumpSpec max = new JumpSpec(new JumpPhysicsInputs(5), cons,
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 4));
        assertEquals(0.75, NoTurnCertifier.landingGiveBack(max, -10.25), 1e-12);
        assertEquals(0.0, NoTurnCertifier.landingGiveBack(max, -11.5), 1e-12);
        JumpSpec min = new JumpSpec(new JumpPhysicsInputs(5), cons,
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, 4));
        assertEquals(2.0, NoTurnCertifier.landingGiveBack(min, -7.0), 1e-12);
        JumpSpec unbounded = new JumpSpec(new JumpPhysicsInputs(5), new ArrayList<JumpConstraint>(),
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, 4));
        assertTrue(NoTurnCertifier.landingGiveBack(unbounded, 1.0) >= 1.0e6);
    }
}
