package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CrossAxisSignTest {

    private static final String CAPTURE = "mcc11-cross-axis-sign";

    private static final class Loaded {
        final ExactJumpModel model;
        final JumpSpec spec;
        final BoxController boxes;
        final int startTick;

        Loaded(ExactJumpModel model, JumpSpec spec, BoxController boxes, int startTick) {
            this.model = model;
            this.spec = spec;
            this.boxes = boxes;
            this.startTick = startTick;
        }
    }

    private static Loaded load() {
        return load(CAPTURE);
    }

    private static Loaded load(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        assertNotNull(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        BoxController boxes = Fixtures.buildBoxes(file);
        AngleSolverEngine engine = new AngleSolverEngine(state, boxes, inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);
        return new Loaded(model, spec, boxes, state.getStartTick());
    }

    private static double violation(Loaded l, double[] yaws) {
        JumpPhysicsInputs sc = l.spec.asScenario();
        double[] gf = sc.toGameFacings(yaws);
        return JumpConstraintCompiler.compile(l.spec).maxViolation(gf, l.model.forward(sc, gf));
    }

    @Test
    public void recordedPathNeedsOppositeDominantSigns() {
        Loaded l = load();
        JumpPhysicsInputs sc = l.spec.asScenario();
        double[] gf = new double[sc.numTicks];
        for (int i = 0; i < gf.length; i++) gf[i] = l.boxes.getYaw(l.startTick + i + 1);
        ForwardPath path = l.model.forward(sc, gf);
        assertEquals(0.0, JumpConstraintCompiler.compile(l.spec).maxViolation(gf, path), 0.0);
        assertEquals(2, JumpLinearModel.crossAxisCount(l.spec.constraints));
        int seen = 0;
        for (JumpConstraint c : l.spec.constraints) {
            if (!JumpLinearModel.isCrossAxis(c)) continue;
            assertEquals(c.name, c.mode == JumpConstraint.Mode.DXZ ? -1 : 1, JumpLinearModel.dominantSignOnPath(c, path));
            seen++;
        }
        assertEquals(2, seen);
    }

    @Test
    public void closedFormSolvesWithPerConstraintSigns() {
        Loaded l = load();
        double[] yaws = ClosedFormSolve.optimize(l.model, l.spec, 0.0, new AtomicBoolean(false));
        assertNotNull("closed form must find the mixed-sign branch", yaws);
        assertTrue(violation(l, yaws) <= 0.0);
    }

    @Test
    public void slpEnforcesCrossAxisWallsFromTheDualSeed() {
        Loaded l = load();
        double[] yaws = SlpSolve.optimize(l.model, l.spec, 0.0, new AtomicBoolean(false));
        assertNotNull("SLP must honour the dX vs dZ constraints", yaws);
        assertTrue(violation(l, yaws) <= 0.0);
    }

    @Test
    public void signCombosEnumerateEveryBranchAllPlusAndAllMinusFirst() {
        Loaded l = load();
        List<int[]> combos = ClosedFormSolve.crossSignCombos(l.spec.constraints);
        assertEquals(4, combos.size());
        assertArrayEquals(new int[] {1, 1}, combos.get(0));
        assertArrayEquals(new int[] {-1, -1}, combos.get(1));
        List<String> seen = new ArrayList<>();
        for (int[] c : combos) {
            String key = c[0] + "," + c[1];
            assertTrue("duplicate combo " + key, !seen.contains(key));
            seen.add(key);
        }
        List<JumpConstraint> none = new ArrayList<>();
        assertEquals(1, ClosedFormSolve.crossSignCombos(none).size());
        assertEquals(0, ClosedFormSolve.crossSignCombos(none).get(0).length);
    }

    @Test
    public void halfWallSlacksReproduceTheExactCrossAxisSlack() {
        Loaded l = load();
        JumpPhysicsInputs sc = l.spec.asScenario();
        double[] gf = new double[sc.numTicks];
        for (int i = 0; i < gf.length; i++) gf[i] = l.boxes.getYaw(l.startTick + i + 1) + (i % 3 - 1) * 7.0;
        ForwardPath path = l.model.forward(sc, gf);
        for (JumpConstraint c : l.spec.constraints) {
            if (!JumpLinearModel.isCrossAxis(c)) continue;
            double exact = JumpConstraintCompiler.evaluate(c, gf, path);
            double expected = c.cmp == JumpConstraint.Cmp.GE ? -exact : exact;
            double halves = Math.max(JumpLinearModel.crossAxisHalfSlack(c, -1.0, path),
                    JumpLinearModel.crossAxisHalfSlack(c, 1.0, path));
            assertEquals(c.name, expected, halves, 1e-12);
        }
    }

    @Test
    public void certifiedSearchNeverCallsTheJumpInfeasible() {
        Loaded l = load();
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
        cfg.nodeCap = 40;
        cfg.cancel = new AtomicBoolean(false);
        cfg.seedYaws = new double[l.spec.asScenario().numTicks];
        CertifiedBnb.Result r = CertifiedBnb.solve(l.model, l.spec, cfg);
        assertFalse("a jump with a recorded solution must not be certified infeasible", r.certifiedInfeasible);
    }

    @Test
    public void facingPinKeepsTheCrossAxisWalls() {
        Loaded l = load();
        JumpPhysicsInputs sc = l.spec.asScenario();
        double[] free = ClosedFormSolve.optimize(l.model, l.spec, 0.0, new AtomicBoolean(false));
        assertNotNull(free);
        for (int tick : new int[] {0, 5, 20}) {
            List<JumpConstraint> cons = new ArrayList<>(l.spec.constraints);
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.GE, free[tick] - 1.0e-4, "pin@" + tick + "eqLo"));
            cons.add(new JumpConstraint(JumpConstraint.Mode.F, tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.LE, free[tick] + 1.0e-4, "pin@" + tick + "eqHi"));
            JumpSpec pinned = new JumpSpec(sc, cons, l.spec.objective);
            double[] yaws = ClosedFormSolve.optimize(l.model, pinned, 0.0, new AtomicBoolean(false));
            assertNotNull("pin at tick " + tick, yaws);
            double[] gf = sc.toGameFacings(yaws);
            assertTrue(JumpConstraintCompiler.compile(pinned).maxViolation(gf, l.model.forward(sc, gf)) <= 0.0);
        }
    }

    @Test
    public void dualBoundCoversBothDominantSigns() {
        Loaded l = load("gh398-optimize-2jump");
        JumpPhysicsInputs sc = l.spec.asScenario();
        List<JumpConstraint> crossOnly = new ArrayList<>();
        for (JumpConstraint c : l.spec.constraints) if (JumpLinearModel.isCrossAxis(c)) crossOnly.add(c);
        assertEquals(1, crossOnly.size());
        Objective objective = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, l.spec.objective.tick);
        JumpSpec withCross = new JumpSpec(sc, crossOnly, objective);
        double[] free = ClosedFormSolve.optimize(l.model, new JumpSpec(sc, new ArrayList<JumpConstraint>(), objective),
                0.0, new AtomicBoolean(false));
        assertNotNull(free);
        double[] gf = sc.toGameFacings(free);
        ForwardPath path = l.model.forward(sc, gf);
        assertTrue(JumpConstraintCompiler.compile(withCross).maxViolation(gf, path) <= 0.0);
        double bound = ClosedFormSolve.dualBound(withCross);
        assertTrue("a feasible path at " + objective.evaluate(path) + " lies beyond the bound " + bound,
                Double.isNaN(bound) || objective.evaluate(path) >= bound - 1.0e-4);
    }
}
