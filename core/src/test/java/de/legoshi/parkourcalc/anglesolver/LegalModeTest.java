package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegalModeTest {

    @Test
    public void proofSpecSelectsExactlyThePadLowWall() {
        RazorFixtures.Loaded l = RazorFixtures.loadProofSpec();
        String[] whyNot = new String[1];
        JumpConstraint goal = AngleSolverEngine.selectLegalGoalWall(l.spec.constraints, l.spec.objective, whyNot);
        assertNotNull("proof spec must yield a goal wall, got: " + whyNot[0], goal);
        assertEquals("X@49lo", goal.name);
        assertEquals(212.69999998807907, goal.rhs, 0.0);
    }

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

    @Test
    public void legalSolveOnCheapFixtureIsDeterministicWithShortfall() {
        double s1 = runLegalShortfall();
        double s2 = runLegalShortfall();
        assertEquals("legal solve must be deterministic",
                Double.doubleToRawLongBits(s1), Double.doubleToRawLongBits(s2));
        assertTrue("shortfall must reflect the unreachable 100.0 goal wall: " + s1, s1 > 95.0 && s1 < 100.0);
    }

    private double runLegalShortfall() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("synth-legal-shortfall"));
        assertNotNull(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        assertTrue("legalMode must round-trip through SaveIO", state.isLegalMode());
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        engine.solve();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        engine.poll();
        SolveResult r = state.getResult();
        assertNotNull("engine returned no result", r);
        assertTrue("legal solver name must carry the suffix: " + r.getSolver(), r.getSolver().contains("(legal)"));
        String detail = null;
        for (SolveResult.Detail d : r.getDetails()) {
            if ("Legal shortfall".equals(d.label)) detail = d.value;
        }
        assertNotNull("result must report the legal shortfall", detail);
        assertTrue(detail.contains("X@11"));
        int unmet = 0;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if (!o.met) unmet++;
        }
        assertEquals("every hard wall must hold; only the dropped goal wall may be unmet", 1, unmet);
        return Double.parseDouble(detail.split(" ")[0]);
    }
}
