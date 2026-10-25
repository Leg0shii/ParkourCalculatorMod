package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegalModeTest {

    @Test
    public void velocityEqAndCapWallsAreNeverSelected() {
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 10);
        String[] whyNot = new String[1];

        List<JumpConstraint> velOnly = new ArrayList<JumpConstraint>();
        velOnly.add(new JumpConstraint(JumpConstraint.Mode.X, 10, Integer.valueOf(9),
                JumpConstraint.Op.MINUS, JumpConstraint.Cmp.GE, 0.3, "dX@10"));
        assertNull("velocity wall must never be the goal wall",
                AngleSolverEngine.selectLegalGoalWall(velOnly, obj, whyNot));

        List<JumpConstraint> eqOnly = new ArrayList<JumpConstraint>();
        eqOnly.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 5.0, "X@10eqLo"));
        eqOnly.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, 5.0, "X@10eqHi"));
        assertNull("EQ-derived pair must never be the goal wall",
                AngleSolverEngine.selectLegalGoalWall(eqOnly, obj, whyNot));

        List<JumpConstraint> range = new ArrayList<JumpConstraint>();
        range.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 5.0, "X@10lo"));
        range.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.LE, 6.0, "X@10hi"));
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(range, obj, whyNot);
        assertNotNull(goal);
        assertEquals("only the sense-matching range half is the goal wall; the cap stays hard", "X@10lo", goal.name);

        List<JumpConstraint> offTick = new ArrayList<JumpConstraint>();
        offTick.add(new JumpConstraint(JumpConstraint.Mode.X, 9, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 5.0, "X@9"));
        assertNull("walls off the objective tick must never be the goal wall",
                AngleSolverEngine.selectLegalGoalWall(offTick, obj, whyNot));

        List<JumpConstraint> offAxis = new ArrayList<JumpConstraint>();
        offAxis.add(new JumpConstraint(JumpConstraint.Mode.Z, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 5.0, "Z@10"));
        assertNull("walls off the objective axis must never be the goal wall",
                AngleSolverEngine.selectLegalGoalWall(offAxis, obj, whyNot));
    }

    @Test
    public void tightestWinsAndTiesRefuse() {
        Objective obj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 10);
        String[] whyNot = new String[1];

        List<JumpConstraint> two = new ArrayList<JumpConstraint>();
        two.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 5.0, "X@10a"));
        two.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 7.0, "X@10b"));
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(two, obj, whyNot);
        assertNotNull(goal);
        assertEquals("the tightest reach wall wins for MAX", "X@10b", goal.name);

        List<JumpConstraint> tie = new ArrayList<JumpConstraint>();
        tie.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 7.0, "X@10a"));
        tie.add(new JumpConstraint(JumpConstraint.Mode.X, 10, null,
                JumpConstraint.Op.PLUS, JumpConstraint.Cmp.GE, 7.0, "X@10b"));
        assertNull("tied walls must refuse", AngleSolverEngine.selectLegalGoalWall(tie, obj, whyNot));
        assertTrue(whyNot[0].contains("ambiguous"));

        assertNull("empty set must refuse",
                AngleSolverEngine.selectLegalGoalWall(new ArrayList<JumpConstraint>(), obj, whyNot));
    }
}
