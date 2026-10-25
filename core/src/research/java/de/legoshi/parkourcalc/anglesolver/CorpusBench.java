package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CorpusBench {

    @Test
    public void bench() throws Exception {
        Assume.assumeTrue("set PKC_CORPUS=1 to run", System.getenv("PKC_CORPUS") != null);
        String tag = env("PKC_CORPUS_TAG", "run");
        String tier = env("PKC_CORPUS_TIER", "FAST");
        boolean thorough = "THOROUGH".equalsIgnoreCase(tier);
        int runs = Integer.parseInt(env("PKC_CORPUS_RUNS", thorough ? "1" : "6"));
        int optSec = Integer.parseInt(env("PKC_CORPUS_OPT_SEC", "4"));
        long perSolveTimeoutMs = Long.parseLong(env("PKC_CORPUS_TIMEOUT_MS", "30000"));
        String filter = System.getenv("PKC_CORPUS_FILTER");

        List<File> files = new ArrayList<>();
        collect(resolve("/captures"), files);
        collect(resolve("/problems/solve"), files);
        collect(resolve("/problems/closedform"), files);

        File dst = new File("build/reports/corpus-" + tag + ".tsv");
        dst.getParentFile().mkdirs();
        java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(dst), true);
        w.println("capture\ttier\tsuccess\tmet\ttotal\tsolver\tsense\taxis\tobjTick\thasObj\tshippedObj"
                + "\trecertObj\trecertViol\tcoldMs\twarmMedMs\truns\tsmoothLambda\tn");
        int done = 0;
        long tStart = System.currentTimeMillis();
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            if (filter != null && !matchesFilter(stem, filter)) continue;
            try {
                String line = benchOne(stem, f, thorough, optSec, runs, perSolveTimeoutMs);
                if (line != null) {
                    w.println(line);
                    done++;
                    System.out.printf(Locale.ROOT, "[%3d %5ds] %s%n", done,
                            (System.currentTimeMillis() - tStart) / 1000, line.split("\t", 2)[0]);
                }
            } catch (Throwable t) {
                w.println(stem + '\t' + tier + "\tEXC\t\t\t" + t);
            }
        }
        w.close();
        System.out.println("[corpus-bench] wrote " + dst + " (" + done + " captures, tier=" + tier + ")");
    }

    private String benchOne(String stem, File f, boolean thorough, int optSec, int runs, long timeoutMs)
            throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                || file.rows == null || file.rows.isEmpty()) {
            return null;
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);

        long[] wall = new long[runs];
        SolveResult last = null;
        JumpSpec spec = null;
        for (int i = 0; i < runs; i++) {
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            if (thorough) {
                state.setEffort(AngleSolverState.Effort.THOROUGH);
                state.setOptimizeSeconds(optSec);
            } else {
                state.setEffort(AngleSolverState.Effort.FAST);
            }
            state.clearResult();
            AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(file), inputs, t -> { }, model);
            spec = engine.debugBuildSpec();

            long t0 = System.nanoTime();
            engine.solve();
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (engine.isSolving() && System.currentTimeMillis() < deadline) {
                engine.poll();
                sleep(1);
            }
            engine.poll();
            wall[i] = (System.nanoTime() - t0) / 1_000_000L;
            last = state.getResult();
        }

        String tier = thorough ? "THOROUGH" : "FAST";
        if (last == null || spec == null) {
            return stem + '\t' + tier + "\tnone\t\t\t\t\t\t\t\t\t\t\t" + wall[0] + "\t\t" + runs + "\t\t";
        }

        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        String sense = spec.objective != null ? spec.objective.sense.name() : "-";
        String axis = spec.objective != null ? spec.objective.axis.name() : "-";
        int objTick = spec.objective != null ? spec.objective.tick : -1;
        double smoothLambda = spec.objective != null ? spec.objective.smoothLambda : 0.0;
        boolean hasObj = last.hasObjective();
        double shippedObj = hasObj ? last.getObjectiveValue() : Double.NaN;

        double recertObj = Double.NaN;
        double recertViol = Double.NaN;
        List<SolveResult.YawEntry> ye = last.getYaws();
        if (spec.objective != null && ye != null && ye.size() == n) {
            double[] yaws = new double[n];
            List<SolveResult.YawEntry> sorted = new ArrayList<>(ye);
            sorted.sort((a, b) -> Integer.compare(a.tick, b.tick));
            for (int k = 0; k < n; k++) yaws[k] = sorted.get(k).yaw;
            double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath path = model.forward(sc, gf);
            recertObj = path.getPos(spec.objective.tick, spec.objective.axis);
            recertViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        }

        long cold = wall[0];
        long warmMed = warmMedian(wall);
        String solver = last.getSolver() == null ? "-" : last.getSolver();
        return String.format(Locale.ROOT,
                "%s\t%s\t%s\t%d\t%d\t%s\t%s\t%s\t%d\t%s\t%.9f\t%.9f\t%.9e\t%d\t%d\t%d\t%.6f\t%d",
                stem, tier, last.isSuccess(), last.getMet(), last.getTotal(), solver,
                sense, axis, objTick, hasObj, shippedObj, recertObj, recertViol, cold, warmMed, runs, smoothLambda, n);
    }

    private static long warmMedian(long[] wall) {
        if (wall.length <= 2) return wall[wall.length - 1];
        long[] warm = Arrays.copyOfRange(wall, 2, wall.length);
        Arrays.sort(warm);
        int m = warm.length;
        return m % 2 == 1 ? warm[m / 2] : (warm[m / 2 - 1] + warm[m / 2]) / 2;
    }

    private static BoxController buildBoxes(SaveFile file) {
        int seedTick = file.angleSolver.startTick;
        SaveFile.Start seed = file.angleSolver.seed;
        List<SaveFile.DebugTick> debug = file.debug;
        int count = Math.max(file.rows.size(), debug != null ? debug.size() : 0);
        BoxController boxes = new BoxController();
        for (int i = 0; i < count; i++) {
            Vec3dCore pos = Vec3dCore.ZERO;
            Vec3dCore vel = Vec3dCore.ZERO;
            float yaw = 0f;
            boolean onGround = false;
            boolean sneaking = false;
            boolean wallCollision = false;
            boolean softCollision = false;
            double collisionAngle = Double.NaN;
            boolean sprinting = false;
            float moveForward = Float.NaN;
            float moveStrafe = Float.NaN;
            SaveFile.DebugTick d = debug != null && i < debug.size() ? debug.get(i) : null;
            if (d != null) {
                if (d.pos != null && d.pos.length >= 3) pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
                if (d.vel != null && d.vel.length >= 3) vel = new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]);
                yaw = d.yaw;
                onGround = d.onGround;
                sneaking = d.sneaking;
                wallCollision = d.wallCollision;
                softCollision = d.softCollision;
                collisionAngle = d.collisionAngle != null ? d.collisionAngle : Double.NaN;
                sprinting = d.sprinting;
                moveForward = d.moveForward != null ? d.moveForward : Float.NaN;
                moveStrafe = d.moveStrafe != null ? d.moveStrafe : Float.NaN;
            }
            if (i == seedTick) {
                if (seed.pos != null && seed.pos.length >= 3) pos = new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]);
                if (seed.vel != null && seed.vel.length >= 3) vel = new Vec3dCore(seed.vel[0], seed.vel[1], seed.vel[2]);
                yaw = seed.yaw;
            }
            boxes.add(new TickState(pos, onGround, sneaking, wallCollision, yaw,
                    Collections.<Vec3dCore>emptyList(), vel, softCollision, collisionAngle,
                    sprinting, moveForward, moveStrafe));
        }
        return boxes;
    }

    private static boolean matchesFilter(String stem, String filter) {
        for (String tok : filter.split(",")) {
            if (!tok.isEmpty() && stem.contains(tok)) return true;
        }
        return false;
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return v != null ? v : dflt;
    }

    private static File resolve(String path) throws Exception {
        URL url = CorpusBench.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json") && !e.getName().endsWith(".expect.json")) out.add(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
