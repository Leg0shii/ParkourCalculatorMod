package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.WallHomotopyLadder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class HomotopyProbe {

    @Test
    public void run() throws Exception {
        String capPath = System.getenv("PKC_HOM_CAPTURE");
        Assume.assumeTrue("set PKC_HOM_CAPTURE to a capture path or pool name", capPath != null && !capPath.isEmpty());

        String raw;
        File direct = new File(capPath);
        if (direct.isFile()) {
            raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        } else {
            raw = Fixtures.rawPool(capPath);
        }
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(capPath + ": failed to parse");

        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalStateException(capPath + ": no spec");
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        System.out.printf("HOM capture=%s n=%d obj=%s %s@%d free=%b%n", capPath, n,
                spec.objective.sense, spec.objective.axis, spec.objective.tick,
                sc.startBox != null && sc.startBox.startFree());

        if ("1".equals(System.getenv("PKC_HOM_WALLS"))) {
            for (JumpConstraint c : spec.constraints) {
                System.out.printf("WALL name=%s mode=%s cmp=%s t1=%d t2=%s rhs=%.10f%n",
                        c.name, c.mode, c.cmp, c.t1, String.valueOf(c.t2), c.rhs);
            }
        }

        String warmPath = System.getenv("PKC_HOM_WARM");
        if (warmPath != null && !warmPath.isEmpty()) {
            JsonObject dec;
            try (Reader r = new FileReader(warmPath)) {
                dec = new JsonParser().parse(r).getAsJsonObject();
            }
            JsonArray ya = dec.get("yawsDeg").getAsJsonArray();
            if (ya.size() != n) throw new IllegalStateException("witness has " + ya.size() + " yaws, spec n=" + n);
            double[] yaws = new double[n];
            for (int i = 0; i < n; i++) yaws[i] = ya.get(i).getAsDouble();
            double px = dec.has("px") ? dec.get("px").getAsDouble() : sc.startPos.x;
            double pz = dec.has("pz") ? dec.get("pz").getAsDouble() : sc.startPos.z;
            JumpPhysicsInputs sc2 = Scoring.pinnedScenario(sc, px, pz);
            double[] gf = sc2.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath fp = model.forward(sc2, gf);
            double pos = fp.getPos(spec.objective.tick, spec.objective.axis);
            double maxViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
            System.out.printf("HOM WARM obj=%.13f maxViol=%.6g px=%.13f pz=%.13f%n", pos, maxViol, px, pz);
            if ("1".equals(System.getenv("PKC_HOM_MARGINS"))) {
                boolean[] zx = new boolean[n];
                boolean[] zz = new boolean[n];
                de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver.extractPattern(model, fp, n, zx, zz);
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel plin =
                        new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc2, zx, zz);
                java.util.Map<String, Double> mg = de.legoshi.parkourcalc.core.anglesolver.solver.AnchorSlp
                        .margins(plin, spec.constraints, Angles.wrapAll(yaws), sc.startBox, px, pz, gf, fp);
                double mx = 0.0;
                for (java.util.Map.Entry<String, Double> e : new java.util.TreeMap<>(mg).entrySet()) {
                    System.out.printf("HOM MARGIN %s %.6e%n", e.getKey(), e.getValue());
                    mx = Math.max(mx, Math.abs(e.getValue()));
                }
                System.out.printf("HOM MARGIN MAX %.6e count=%d%n", mx, mg.size());
            }
            if (maxViol > 0.0) {
                ForwardPath fpv = fp;
                for (JumpConstraint c : spec.constraints) {
                    double s = JumpConstraintCompiler.slack(c, gf, fpv);
                    if (s > 0.0) System.out.printf("HOM WARM viol %s slack=%.6g%n", c.name, s);
                }
            }
            if ("1".equals(System.getenv("PKC_HOM_POLISH2"))) {
                String pb = System.getenv("PKC_HOM_PERTURB");
                int buckets = pb == null || pb.isEmpty() ? 2 : Integer.parseInt(pb);
                double bucketDeg = 360.0 / 65536.0;
                double[] pert = new double[n];
                for (int t = 0; t < n; t++) {
                    pert[t] = yaws[t] + ((t % 2 == 0) ? buckets : -buckets) * bucketDeg;
                }
                if ("1".equals(System.getenv("PKC_HOM_DEBUG"))) FoldReplayDriver.DEBUG = true;
                FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(model, spec,
                        pert, px, pz, null);
                for (FoldReplayDriver.Round r : pr.rounds) {
                    System.out.printf("HOM POLISH2 round=%d obj=%.13f viol=%.6g%s%n",
                            r.index, r.objective, r.maxViolation, r.polished ? " polished" : "");
                }
                if (pr.best != null) {
                    System.out.printf("HOM POLISH2 BEST obj=%.13f maxViol=%.6g feasible=%b (perturb %d)%n",
                            pr.best.objective, pr.best.maxViolation, pr.best.feasible(), buckets);
                } else {
                    System.out.println("HOM POLISH2 BEST none");
                }
            }
            if ("1".equals(System.getenv("PKC_HOM_POLISH"))) {
                String pb = System.getenv("PKC_HOM_PERTURB");
                int buckets = pb == null || pb.isEmpty() ? 2 : Integer.parseInt(pb);
                double bucketDeg = 360.0 / 65536.0;
                double[] pert = new double[n];
                for (int t = 0; t < n; t++) {
                    pert[t] = yaws[t] + ((t % 2 == 0) ? buckets : -buckets) * bucketDeg;
                }
                pert = Angles.wrapAll(pert);
                double[] pgf = sc2.toGameFacings(pert);
                ForwardPath pfp = model.forward(sc2, pgf);
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler.Compiled comp =
                        JumpConstraintCompiler.compile(spec);
                double pviol = comp.maxViolation(pgf, pfp);
                System.out.printf("HOM POLISH anchor viol=%.6g obj=%.9f (perturb %d buckets)%n",
                        pviol, pfp.getPos(spec.objective.tick, spec.objective.axis), buckets);
                boolean[] pzx = new boolean[n];
                boolean[] pzz = new boolean[n];
                de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver
                        .extractPattern(model, pfp, n, pzx, pzz);
                de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel plin2 =
                        new de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel(sc2, pzx, pzz);
                java.util.List<FoldReplayDriver.Round> sink = new java.util.ArrayList<>();
                de.legoshi.parkourcalc.core.anglesolver.solver.AnchorSlp.Outcome oc =
                        de.legoshi.parkourcalc.core.anglesolver.solver.AnchorSlp.polish(model, spec, comp,
                                sc, plin2, pert, pgf, pfp, px, pz, pviol, sink,
                                spec.objective.sense == de.legoshi.parkourcalc.core.anglesolver.solver.Objective.Sense.MAX);
                for (FoldReplayDriver.Round r : sink) {
                    System.out.printf("HOM POLISH slp[%d] obj=%.9f viol=%.6g%n", r.index, r.objective, r.maxViolation);
                }
                System.out.printf("HOM POLISH RESULT landed=%b bestViol=%.6g rounds=%d%n",
                        oc.landed != null, oc.viol, sink.size());
            }
        }

        if ("1".equals(System.getenv("PKC_HOM_ENGINE"))) {
            long t0 = System.nanoTime();
            engine.solve(AngleSolverState.Effort.FAST);
            long deadline = System.currentTimeMillis() + 30000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(1);
            }
            engine.poll();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            SolveResult res = state.getResult();
            if (res == null) {
                System.out.printf("HOM ENGINE FAST none ms=%d solving=%b%n", ms, engine.isSolving());
            } else {
                System.out.printf("HOM ENGINE FAST success=%b met=%d/%d obj=%s solver=%s ms=%d%n",
                        res.isSuccess(), res.getMet(), res.getTotal(),
                        res.hasObjective() ? String.format("%.9f", res.getObjectiveValue()) : "-",
                        res.getSolver(), ms);
            }
        }

        if ("1".equals(System.getenv("PKC_HOM_SEEDPOLISH"))) {
            long t0 = System.nanoTime();
            engine.solve(AngleSolverState.Effort.FAST);
            long deadline = System.currentTimeMillis() + 30000L;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                Thread.sleep(1);
            }
            engine.poll();
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            SolveResult res = state.getResult();
            if (res == null || res.getYaws() == null || res.getYaws().size() != n) {
                System.out.printf("HOM SEEDPOLISH engine gave no yaw chain (res=%s yaws=%s) ms=%d%n",
                        res == null ? "null" : "ok",
                        res == null || res.getYaws() == null ? "null" : String.valueOf(res.getYaws().size()), ms);
            } else {
                System.out.printf("HOM SEEDPOLISH engine success=%b obj=%s ms=%d%n",
                        res.isSuccess(),
                        res.hasObjective() ? String.format("%.13f", res.getObjectiveValue()) : "-", ms);
                java.util.List<SolveResult.YawEntry> ye = new java.util.ArrayList<>(res.getYaws());
                ye.sort((a, b) -> Integer.compare(a.tick, b.tick));
                double[] seed = new double[n];
                for (int k = 0; k < n; k++) seed[k] = ye.get(k).yaw;
                if ("1".equals(System.getenv("PKC_HOM_DEBUG"))) FoldReplayDriver.DEBUG = true;
                FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(model, spec, seed, null, null, null);
                for (FoldReplayDriver.Round r : pr.rounds) {
                    System.out.printf("HOM SEEDPOLISH round=%d obj=%.13f viol=%.6g%s%n",
                            r.index, r.objective, r.maxViolation, r.polished ? " polished" : "");
                }
                if (pr.best != null) {
                    System.out.printf("HOM SEEDPOLISH BEST obj=%.13f maxViol=%.6g feasible=%b%n",
                            pr.best.objective, pr.best.maxViolation, pr.best.feasible());
                } else {
                    System.out.println("HOM SEEDPOLISH BEST none");
                }
            }
        }

        if ("1".equals(System.getenv("PKC_HOM_DRIVER"))) {
            if ("1".equals(System.getenv("PKC_HOM_DEBUG"))) FoldReplayDriver.DEBUG = true;
            FoldReplayDriver.Result res = FoldReplayDriver.solve(model, spec);
            for (FoldReplayDriver.Round r : res.rounds) {
                System.out.printf("HOM DRIVER round=%d bound=%.6f byteObj=%.9f maxViol=%.6g events=%d%s%n",
                        r.index, r.linearBound, r.objective, r.maxViolation, r.patternEvents,
                        r.polished ? " polished" : "");
            }
            if (res.best != null) {
                System.out.printf("HOM DRIVER BEST obj=%.9f maxViol=%.6g%n", res.best.objective, res.best.maxViolation);
            } else {
                System.out.println("HOM DRIVER BEST none");
            }
        }

        if ("1".equals(System.getenv("PKC_HOM_LADDER"))) {
            if ("1".equals(System.getenv("PKC_HOM_DEBUG"))) FoldReplayDriver.DEBUG = true;
            WallHomotopyLadder.Result lr = WallHomotopyLadder.solve(model, spec, null, 0L);
            for (WallHomotopyLadder.Rung rung : lr.rungs) {
                FoldReplayDriver.Round b = rung.result.best;
                System.out.printf("HOM RUNG delta=%.4g rounds=%d best[obj=%.9f maxViol=%.6g] fixedPoint=%b%n",
                        rung.delta, rung.result.rounds.size(),
                        b == null ? Double.NaN : b.objective, b == null ? Double.NaN : b.maxViolation,
                        rung.result.fixedPoint);
            }
            if (lr.best != null) {
                System.out.printf("HOM LADDER BEST obj=%.13f maxViol=%.6g feasible=%b%n",
                        lr.best.objective, lr.best.maxViolation, lr.best.feasible());
            } else {
                System.out.println("HOM LADDER BEST none");
            }
        }
    }
}
