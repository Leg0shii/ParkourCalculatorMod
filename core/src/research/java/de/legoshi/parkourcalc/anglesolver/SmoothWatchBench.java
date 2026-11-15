package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothPortfolio;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import de.legoshi.parkourcalc.core.anglesolver.solver.TurnShape;
import de.legoshi.parkourcalc.core.anglesolver.solver.WatchScore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmoothWatchBench {

    @Test
    public void compareSmoothers() throws Exception {
        String names = System.getProperty("pkc.smoothbench", "");
        Assume.assumeTrue("set -Dpkc.smoothbench=<capture,capture,...> or file paths to run", !names.isEmpty());
        long budgetMs = Long.getLong("pkc.smoothbench.budgetMs", 6000L);
        long solveMs = Long.getLong("pkc.smoothbench.solveMs", 60000L);
        int runs = Integer.getInteger("pkc.smoothbench.runs", 1);
        String tag = System.getProperty("pkc.smoothbench.tag", "run");
        File dir = new File("build/reports");
        dir.mkdirs();
        try (PrintWriter tsv = new PrintWriter(new File(dir, "smoothbench-" + tag + ".tsv"), "UTF-8");
             PrintWriter json = new PrintWriter(new File(dir, "smoothbench-" + tag + ".jsonl"), "UTF-8")) {
            tsv.println("capture\tticks\trun\twhat\tms\tstop\tcost\treversals\tshakes\twobbles\tmicro\ttravel\texcess\toffHeading\tmaxTurn");
            for (String name : names.split(",")) {
                one(name.trim(), budgetMs, solveMs, runs, tsv, json);
                tsv.flush();
                json.flush();
            }
        }
    }

    private static void one(String name, long budgetMs, long solveMs, int runs, PrintWriter tsv, PrintWriter json)
            throws Exception {
        File asFile = new File(name);
        String raw = asFile.isFile() ? new String(Files.readAllBytes(asFile.toPath()), StandardCharsets.UTF_8)
                : Fixtures.rawPool(name);
        if (raw.startsWith("﻿")) {
            raw = raw.substring(1);
        }
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) {
            System.out.println("SMOOTHBENCH " + name + " parse failed");
            return;
        }
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        state.clearResult();
        state.setEffort(AngleSolverState.Effort.FAST);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, exact);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) {
            System.out.println("SMOOTHBENCH " + name + " no spec");
            return;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        engine.solve();
        long until = System.currentTimeMillis() + solveMs;
        while (engine.isSolving() && System.currentTimeMillis() < until) {
            engine.poll();
            Thread.sleep(2L);
        }
        engine.poll();
        SolveResult result = state.getResult();
        if (result == null || !result.isSuccess()) {
            engine.cancel();
            System.out.println("SMOOTHBENCH " + name + " did not land");
            return;
        }
        double[] seed = new double[sc.numTicks];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = result.getYaws().get(i).yaw;
        }
        String label = asFile.isFile() ? asFile.getName().replaceAll("\\.json$", "") : name;
        double giveBack = giveBackBlocks(exact, spec, sc, seed);
        List<String> shapes = new ArrayList<String>();
        shapes.add(row(tsv, label, 0, "solve", 0L, "", exact, spec, sc, seed));
        for (int run = 1; run <= runs; run++) {
            long t0 = System.nanoTime();
            TrendFilterSmooth.Report classic = TrendFilterSmooth.smoothReport(exact, spec, seed, giveBack,
                    System.nanoTime() + budgetMs * 1_000_000L, new AtomicBoolean(false));
            String classicShape = row(tsv, label, run, "classic", (System.nanoTime() - t0) / 1_000_000L,
                    classic.stop.label, exact, spec, sc, classic.yaws);
            t0 = System.nanoTime();
            TrendFilterSmooth.Report portfolio = SmoothPortfolio.smoothReport(exact, spec, seed, giveBack,
                    System.nanoTime() + budgetMs * 1_000_000L, new AtomicBoolean(false));
            String portfolioShape = row(tsv, label, run, "portfolio", (System.nanoTime() - t0) / 1_000_000L,
                    portfolio.stop.label, exact, spec, sc, portfolio.yaws);
            if (run == 1) {
                shapes.add(classicShape);
                shapes.add(portfolioShape);
            }
        }
        StringBuilder jumps = new StringBuilder();
        for (int t = 0; t < sc.numTicks; t++) {
            if (sc.jumpAt(t)) {
                jumps.append(jumps.length() == 0 ? "" : ",").append(t);
            }
        }
        json.println("{\"name\":\"" + label + "\",\"mc\":\"" + file.mcVersion + "\",\"startYaw\":" + sc.startYaw
                + ",\"jumps\":[" + jumps + "],\"seed\":" + shapes.get(0) + ",\"classic\":" + shapes.get(1)
                + ",\"portfolio\":" + shapes.get(2) + "}");
    }

    private static double giveBackBlocks(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws) {
        Objective obj = spec.objective;
        JumpConstraint wall = AngleSolverEngine.tightestGoalWall(spec.constraints, obj);
        if (wall == null || obj.isCustomAngle()) {
            return Double.POSITIVE_INFINITY;
        }
        double achieved = obj.evaluate(exact.forward(sc, sc.toGameFacings(yaws)));
        double offset = obj.sense == Objective.Sense.MAX ? achieved - wall.rhs : wall.rhs - achieved;
        return Math.max(0.0, offset);
    }

    private static String row(PrintWriter tsv, String name, int run, String what, long ms, String stop,
                              ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc, double[] yaws) {
        double[] wrapped = Angles.wrapAll(yaws.clone());
        double[] gf = sc.toGameFacings(wrapped);
        ForwardPath path = exact.forward(sc, gf);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        WatchScore score = WatchScore.of(sc.startYaw, wrapped, path);
        tsv.printf(Locale.ROOT, "%s\t%d\t%d\t%s\t%d\t%s\t%.2f\t%d\t%d\t%d\t%d\t%.0f\t%.0f\t%.0f\t%.0f%n", name, sc.numTicks,
                run, what, ms, stop, score.cost, score.reversals, score.shakes, score.wobbles, score.microTurns,
                score.travelDeg, score.excessDeg, score.offHeadingDeg, score.maxTurnDeg);
        System.out.printf(Locale.ROOT, "SMOOTHBENCH %s %s run=%d ms=%d viol=%.0e %s%n", name, what, run, ms, viol, score);
        double[] d = TurnShape.deltas(sc.startYaw, wrapped);
        StringBuilder sb = new StringBuilder("{\"d\":[");
        for (int i = 0; i < d.length; i++) {
            sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.2f", d[i]));
        }
        sb.append("],\"x\":[");
        for (int i = 0; i < path.posX.length; i++) {
            sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.3f", path.posX[i]));
        }
        sb.append("],\"z\":[");
        for (int i = 0; i < path.posZ.length; i++) {
            sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.3f", path.posZ[i]));
        }
        sb.append(String.format(Locale.ROOT,
                "],\"cost\":%.2f,\"reversals\":%d,\"shakes\":%d,\"wobbles\":%d,\"micro\":%d,\"travel\":%.0f,\"excess\":%.0f,\"off\":%.0f,\"max\":%.0f}",
                score.cost, score.reversals, score.shakes, score.wobbles, score.microTurns, score.travelDeg,
                score.excessDeg, score.offHeadingDeg, score.maxTurnDeg));
        return sb.toString();
    }
}
