package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HpkEngineBench {

    private static final long TIMEOUT_MS = Long.getLong("pkc.bench.timeoutMs",
            System.getenv("PKC_BENCH_TIMEOUT_MS") != null ? Long.parseLong(System.getenv("PKC_BENCH_TIMEOUT_MS")) : 45_000L);

    @Test
    public void bench() throws Exception {
        org.junit.Assume.assumeTrue("set PKC_BENCH=1 to run", System.getenv("PKC_BENCH") != null);
        String tag = System.getenv("PKC_BENCH_TAG") != null ? System.getenv("PKC_BENCH_TAG") : "run";

        List<File> files = new ArrayList<>();
        collect(resolve("/captures/hpk"), files);
        File loopmm = new File(resolve("/captures"), "loopmm-3jump-lands.json");
        if (loopmm.isFile()) files.add(loopmm);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf("%-52s %8s %8s %6s %10s %16s %12s%n",
                "capture", "success", "met", "ms", "solver", "objective", "landMargin");
        String filter = System.getenv("PKC_BENCH_FILTER");
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            if (filter != null && !matches(filter, stem)) continue;
            try {
                benchOne(stem, f, out);
            } catch (Throwable t) {
                out.printf("%-52s EXC %s%n", stem, t);
            }
        }
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/bench-" + tag + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private void benchOne(String stem, File f, PrintWriter out) throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                || file.rows == null || file.rows.isEmpty()) {
            out.printf("%-52s (skipped)%n", stem);
            return;
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (System.getenv("PKC_BENCH_EXH") != null) {
            state.setEffort(AngleSolverState.Effort.CUSTOM);
            AngleSolverState.SolveBudget b = state.getSolveBudget();
            b.setRestarts(16);
            b.setMaxEval(4500);
            b.setPolishCount(2);
            b.setUseWindowSolver(true);
            b.setIlsExhaustive(true);
            b.setTimeBudgetSeconds(30);
        } else {
            state.setEffort(AngleSolverState.Effort.FAST);
        }
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        SolveResult r = state.getResult();
        if (r == null) {
            out.printf("%-52s %8s %8s %6d%n", stem, "none", "-", ms);
            return;
        }
        double obj = r.hasObjective() ? r.getObjectiveValue() : Double.NaN;
        double margin = landMargin(spec, obj);
        out.printf("%-52s %8s %5d/%-2d %6d %10s %16.6f %12s%n",
                stem, r.isSuccess(), r.getMet(), r.getTotal(), ms,
                shortSolver(r), obj,
                Double.isNaN(margin) ? "-" : String.format("%+.6f", margin));
    }

    private static boolean matches(String filter, String stem) {
        for (String tok : filter.split(",")) {
            if (stem.equals(tok.trim())) return true;
        }
        return false;
    }

    private static String shortSolver(SolveResult r) {
        String s = null;
        try {
            java.lang.reflect.Method m = r.getClass().getMethod("getSolver");
            Object v = m.invoke(r);
            s = v != null ? v.toString() : null;
        } catch (Exception ignored) {
        }
        if (s == null) return "-";
        if (s.contains("relaxation")) return "relax";
        if (s.contains("CMA")) return "cmaes";
        if (s.contains("reseeded")) return "slp-alt";
        if (s.contains("SLP")) return "slp";
        if (s.contains("closed")) return "closed";
        if (s.contains("horizon")) return "window";
        return s.length() > 10 ? s.substring(0, 10) : s;
    }

    private static double landMargin(JumpSpec spec, double obj) {
        if (spec == null || Double.isNaN(obj)) return Double.NaN;
        double margin = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (c.t1 != spec.objective.tick || c.t2 != null) continue;
            boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
            if ((c.mode == JumpConstraint.Mode.X) != axisX) continue;
            if (c.mode == JumpConstraint.Mode.F) continue;
            double m;
            if (c.cmp == JumpConstraint.Cmp.GE) m = obj - c.rhs;
            else if (c.cmp == JumpConstraint.Cmp.LE) m = c.rhs - obj;
            else m = -Math.abs(obj - c.rhs);
            if (Double.isNaN(margin) || m < margin) margin = m;
        }
        return margin;
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

    private static File resolve(String path) throws Exception {
        URL url = HpkEngineBench.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json")) out.add(e);
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
