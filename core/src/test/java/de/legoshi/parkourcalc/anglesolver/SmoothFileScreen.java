package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import de.legoshi.parkourcalc.core.anglesolver.solver.TurnShape;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmoothFileScreen {

    @Test
    public void smoothSavedResult() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        org.junit.Assume.assumeTrue("set PKC_SOLVE_FILE=<save.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, exact);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        double[] seed = new double[sc.numTicks];
        for (int i = 0; i < seed.length; i++) seed[i] = state.getResult().getYaws().get(i).yaw;
        double giveBackRatio = envDouble("PKC_GIVE_BACK", 1.0);
        long secs = (long) envDouble("PKC_SMOOTH_SECS", 20.0);
        double giveBack = giveBackBlocks(exact, spec, sc, seed, giveBackRatio);
        System.out.printf(Locale.ROOT, "SMOOTH n=%d giveBack=%.4f blocks secs=%d%n", sc.numTicks, giveBack, secs);
        report("before", state, sc, exact, spec, seed);
        long t0 = System.nanoTime();
        double[] out = TrendFilterSmooth.smooth(exact, spec, seed, giveBack, System.nanoTime() + secs * 1_000_000_000L, new AtomicBoolean(false));
        System.out.printf(Locale.ROOT, "SMOOTH ran %d ms%n", (System.nanoTime() - t0) / 1_000_000L);
        report("after", state, sc, exact, spec, out);
    }

    private static double giveBackBlocks(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws, double ratio) {
        Objective obj = spec.objective;
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(spec.constraints, obj);
        if (wall == null || obj.isCustomAngle()) return Double.POSITIVE_INFINITY;
        double achieved = obj.evaluate(exact.forward(sc, sc.toGameFacings(yaws)));
        double offset = obj.sense == Objective.Sense.MAX ? achieved - wall.rhs : wall.rhs - achieved;
        return Math.max(0.0, offset) * ratio;
    }

    private static void report(String what, AngleSolverState state, JumpPhysicsInputs sc, ExactJumpModel exact,
                               JumpSpec spec, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        double obj = spec.objective.evaluate(path);
        TurnShape shape = TurnShape.of(sc.startYaw, yaws);
        System.out.printf(Locale.ROOT, "SMOOTH %s obj=%.6f viol=%.3e %s%n", what, obj, viol, shape);
        double[] d = TurnShape.deltas(sc.startYaw, yaws);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            sb.append(String.format(Locale.ROOT, "T%d:%.1f ", state.getStartTick() + i + 1, d[i]));
        }
        System.out.println("SMOOTH " + what + " deltas " + sb);
    }

    private static double envDouble(String key, double def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : Double.parseDouble(v);
    }
}
