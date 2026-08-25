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
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class OneTailProbe {

    @Test
    public void j021TerminalWindowSolvesFeasibleAtTheUnifiedTailObjective() {
        Case c = solveCase("j021-rinav1-01");
        report(c);
        assertNotNull(c.name + ": unified terminal window produced no feasible result", c.gf);
        assertTrue(c.name + ": must be byte-exact feasible, viol=" + c.viol, c.viol <= 0.0);
        assertTrue(c.name + ": unified tail must reach >= 1067.84, was " + c.obj, c.obj >= 1067.84);
    }

    @Test
    public void j008bTerminalWindowSolvesFeasible() {
        Case c = solveCase("j008b-2jump");
        report(c);
        assertNotNull(c.name + ": unified terminal window produced no feasible result", c.gf);
        assertTrue(c.name + ": must be byte-exact feasible, viol=" + c.viol, c.viol <= 0.0);
    }

    @Test
    public void loopmmTerminalWindowSolvesFeasible() {
        Case c = solveCase("loopmm-3jump-lands");
        report(c);
        assertNotNull(c.name + ": unified terminal window produced no feasible result", c.gf);
        assertTrue(c.name + ": must be byte-exact feasible, viol=" + c.viol, c.viol <= 0.0);
    }

    private static final class Case {
        String name;
        int n;
        int objTick;
        JumpPhysicsInputs.Axis axis;
        double[] gf;
        double obj;
        double viol;
        long ms;
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

        Case c = new Case();
        c.name = capName;
        c.n = sc.numTicks;
        c.objTick = spec.objective.tick;
        c.axis = spec.objective.axis;

        AtomicBoolean cancel = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        double[] gf = LongRunSolver.solve(model, spec, 0.0, cancel);
        c.ms = (System.nanoTime() - t0) / 1_000_000L;
        if (gf != null) {
            double[] replay = sc.toGameFacings(Angles.wrapAll(gf));
            c.gf = replay;
            c.obj = model.forward(sc, replay).getPos(spec.objective.tick, spec.objective.axis);
            ForwardPath path = model.forward(sc, replay);
            c.viol = JumpConstraintCompiler.compile(spec).maxViolation(replay, path);
        }
        return c;
    }

    private static void report(Case c) {
        System.out.printf("ONETAIL %-18s n=%d objTick=%d axis=%s  unified tail obj=%s viol=%s ms=%d%n",
                c.name, c.n, c.objTick, c.axis,
                c.gf == null ? "MISS" : String.format(java.util.Locale.ROOT, "%.9f", c.obj),
                c.gf == null ? "-" : String.format(java.util.Locale.ROOT, "%.3e", c.viol), c.ms);
    }
}
