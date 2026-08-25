package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ResidualRescue;
import de.legoshi.parkourcalc.core.anglesolver.solver.SphereDecodeSnap;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class SphereDecodeSnapProbe {

    @Test
    public void j021SnapHoldsAndCloses() {
        Case c = run("j021-rinav1-01");
        report(c, 1067.863789);
        assertTrue("j021 snap must be byte-exact feasible", c.snapViol <= 0.0);
        assertTrue("j021 snap must not regress the residual", c.snapObj >= c.residualObj - 1.0e-12);
        assertTrue("j021 snap lock round-trip must reproduce the objective", c.roundTripMatch);
    }

    @Test
    public void j016SnapCapturesHalfAngleGain() {
        Case c = run("j016-X2jmmp2p");
        report(c, Double.NaN);
        assertTrue("j016 snap must be byte-exact feasible", c.snapViol <= 0.0);
        assertTrue("j016 snap must not regress", c.snapObj >= c.residualObj - 1.0e-12);
        assertTrue("j016 snap must strictly improve the half-angle case, was " + (c.snapObj - c.residualObj),
                c.snapObj > c.residualObj + 1.0e-9);
        assertTrue("j016 snap lock round-trip must reproduce the objective", c.roundTripMatch);
    }

    @Test
    public void j019SnapCapturesGain() {
        Case c = run("j019-3jmmtruenix");
        report(c, Double.NaN);
        assertTrue("j019 snap must be byte-exact feasible", c.snapViol <= 0.0);
        assertTrue("j019 snap must strictly improve, was " + (c.snapObj - c.residualObj),
                c.snapObj > c.residualObj + 1.0e-9);
        assertTrue("j019 snap lock round-trip must reproduce the objective", c.roundTripMatch);
    }

    @Test
    public void j008bSnapNeverRegresses() {
        Case c = run("j008b-2jump");
        report(c, -0.197052);
        assertTrue("j008b snap must be byte-exact feasible", c.snapViol <= 0.0);
        assertTrue("j008b snap must not regress the residual", c.snapObj >= c.residualObj - 1.0e-12);
    }

    private static final class Case {
        String name;
        int n;
        int[] degen;
        double baselineObj;
        double residualObj;
        double snapObj;
        double snapViol;
        boolean snapped;
        boolean roundTripMatch;
        long ms;
        int objTick;
        JumpPhysicsInputs.Axis axis;
        Objective.Sense sense;
    }

    private Case run(String capName) {
        String raw = Fixtures.rawPool(capName);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull(capName + ": parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();

        AtomicBoolean cancel = new AtomicBoolean(false);
        String[] chainName = new String[1];
        double[] baseline = AngleSolverEngine.dualChain(model, spec, sc, cancel, chainName);
        assertNotNull(capName + ": baseline dualChain produced no feasible solution", baseline);
        baseline = Angles.wrapAll(baseline);
        double[] residual = Angles.wrapAll(ResidualRescue.improve(model, spec, baseline, 0.0, cancel, 0L));

        Case c = new Case();
        c.name = capName;
        c.n = sc.numTicks;
        c.degen = ResidualRescue.degenerateTicks(spec);
        c.objTick = spec.objective.tick;
        c.axis = spec.objective.axis;
        c.sense = spec.objective.sense;
        c.baselineObj = objOf(model, sc, spec, sc.toGameFacings(baseline));
        c.residualObj = objOf(model, sc, spec, sc.toGameFacings(residual));

        long t0 = System.nanoTime();
        double[] snapGf = SphereDecodeSnap.snap(model, spec, residual, 0.0, cancel, 0L);
        c.ms = (System.nanoTime() - t0) / 1_000_000L;

        if (snapGf == null) {
            c.snapped = false;
            c.snapObj = c.residualObj;
            c.snapViol = violOf(model, sc, spec, sc.toGameFacings(residual));
            c.roundTripMatch = true;
            return c;
        }
        c.snapped = true;
        ForwardPath p = model.forward(sc, snapGf);
        c.snapObj = p.getPos(spec.objective.tick, spec.objective.axis);
        c.snapViol = JumpConstraintCompiler.compile(spec).maxViolation(snapGf, p);

        JumpPhysicsInputs locked = sc.copy();
        boolean[] lockAll = new boolean[locked.numTicks];
        Arrays.fill(lockAll, true);
        locked.yawLockedPerTick = lockAll;
        double[] gfFromLocked = locked.toGameFacings(snapGf);
        ForwardPath pl = model.forward(locked, gfFromLocked);
        double lockedObj = pl.getPos(spec.objective.tick, spec.objective.axis);
        c.roundTripMatch = Arrays.equals(gfFromLocked, snapGf) && lockedObj == c.snapObj;
        return c;
    }

    private static void report(Case c, double coptTarget) {
        System.out.printf(
                "SPHERE %-16s n=%d objTick=%d %s/%s degen=%s snapped=%s ms=%d%n"
                + "    baseline=%.9f  residual=%.9f  snap=%.9f viol=%.3e  gain=%.6e  copt=%.9f  roundTrip=%s%n",
                c.name, c.n, c.objTick, c.axis, c.sense, Arrays.toString(c.degen), c.snapped, c.ms,
                c.baselineObj, c.residualObj, c.snapObj, c.snapViol, c.snapObj - c.residualObj,
                coptTarget, c.roundTripMatch);
    }

    private static double objOf(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] gf) {
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double violOf(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] gf) {
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, model.forward(sc, gf));
    }
}
