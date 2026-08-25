package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.ResidualRescue;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class ResidualRescueProbe {

    @Test
    public void j021ReachesCoptReference() {
        Case c = solveCase("j021-rinav1-01");
        report(c, 1067.863789, 1067.862397);
        assertTrue("j021 rescue must be byte-exact feasible", c.rescuedViol <= 0.0);
        assertTrue("j021 rescue must reach the ARCH-1 target (>= 1067.8637), was " + c.rescuedObj,
                c.rescuedObj >= 1067.8637);
        assertTrue("j021 rescue must beat shipped THOROUGH 1067.862397, was " + c.rescuedObj,
                c.rescuedObj > 1067.862397 + 1.0e-4);
    }

    @Test
    public void j008bImprovesOverBaseline() {
        Case c = solveCase("j008b-2jump");
        report(c, -0.197052, -0.215314);
        assertTrue("j008b rescue must be byte-exact feasible", c.rescuedViol <= 0.0);
        assertTrue("j008b rescue must not regress the baseline", c.rescuedObj >= c.baselineObj - 1.0e-9);
    }

    @Test
    public void loopmmStaysFeasibleAndNeverRegresses() {
        Case c = solveCase("loopmm-3jump-lands");
        report(c, -279.299065, -279.2997);
        assertTrue("loopmm rescue must be byte-exact feasible", c.rescuedViol <= 0.0);
        assertTrue("loopmm rescue must not regress the baseline", c.rescuedObj >= c.baselineObj - 1.0e-9);
    }

    private static final class Case {
        String name;
        int n;
        int[] degen;
        double[] costateMag;
        double baselineObj;
        double baselineViol;
        double rescuedObj;
        double rescuedViol;
        long rescueMs;
        int objTick;
        JumpPhysicsInputs.Axis axis;
        Objective.Sense sense;
    }

    private Case solveCase(String capName) {
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

        Case c = new Case();
        c.name = capName;
        c.n = sc.numTicks;
        c.degen = ResidualRescue.degenerateTicks(spec);
        c.costateMag = costateMagnitudes(spec);
        c.objTick = spec.objective.tick;
        c.axis = spec.objective.axis;
        c.sense = spec.objective.sense;
        c.baselineObj = objectiveOf(model, sc, spec, baseline);
        c.baselineViol = violationOf(model, sc, spec, baseline);

        long t0 = System.nanoTime();
        double[] rescued = ResidualRescue.improve(model, spec, baseline, 0.0, cancel, 0L);
        c.rescueMs = (System.nanoTime() - t0) / 1_000_000L;
        assertNotNull(capName + ": improve returned null on a non-null baseline", rescued);
        rescued = Angles.wrapAll(rescued);
        c.rescuedObj = objectiveOf(model, sc, spec, rescued);
        c.rescuedViol = violationOf(model, sc, spec, rescued);
        return c;
    }

    private static void report(Case c, double coptTarget, double shipped) {
        StringBuilder d = new StringBuilder("[");
        if (c.degen != null) {
            for (int i = 0; i < c.degen.length; i++) {
                if (i > 0) d.append(",");
                d.append(c.degen[i]);
            }
        } else {
            d.append("null");
        }
        d.append("]");
        System.out.printf(
                "RESIDUAL %-18s n=%d objTick=%d %s/%s |D|=%s degen=%s%n"
                + "    baseline obj=%.9f viol=%.3e%n"
                + "    rescued  obj=%.9f viol=%.3e ms=%d%n"
                + "    shipped  obj=%.9f   coptTarget=%.9f   gain(rescue-baseline)=%.6e  toCopt=%.6e%n",
                c.name, c.n, c.objTick, c.axis, c.sense,
                c.degen == null ? "null" : String.valueOf(c.degen.length), d,
                c.baselineObj, c.baselineViol, c.rescuedObj, c.rescuedViol, c.rescueMs,
                shipped, coptTarget, c.rescuedObj - c.baselineObj, coptTarget - c.rescuedObj);
        System.out.print("    costateMag(sorted asc, smallest 6): ");
        double[] m = c.costateMag.clone();
        java.util.Arrays.sort(m);
        int show = Math.min(6, m.length);
        for (int i = 0; i < show; i++) System.out.printf("%.4e ", m[i]);
        double mx = 0.0;
        for (double v : c.costateMag) mx = Math.max(mx, v);
        System.out.printf("  max=%.4e%n", mx);
    }

    private static double[] costateMagnitudes(JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        CostateDualSolver.Result r = new CostateDualSolver(n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
        double[] mag = new double[n];
        if (r == null) return mag;
        for (int t = 0; t < n; t++) mag[t] = Math.sqrt(r.gx[t] * r.gx[t] + r.gz[t] * r.gz[t]);
        return mag;
    }

    private static double objectiveOf(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return model.forward(sc, gf).getPos(spec.objective.tick, spec.objective.axis);
    }

    private static double violationOf(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
    }
}
