package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FoldReplayDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FoldDriverProbe {

    @Test
    public void run() throws Exception {
        String capPath = System.getenv("PKC_FOLD_CAPTURE");
        Assume.assumeTrue("set PKC_FOLD_CAPTURE to a capture path", capPath != null && !capPath.isEmpty());
        String out = System.getenv("PKC_FOLD_OUT");

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

        if ("1".equals(System.getenv("PKC_FOLD_DEBUG"))) FoldReplayDriver.DEBUG = true;
        FoldReplayDriver.Params params = new FoldReplayDriver.Params();
        String seedPat = System.getenv("PKC_FOLD_SEEDPAT");
        if (seedPat != null && !seedPat.isEmpty()) {
            JsonObject pat = new com.google.gson.JsonParser()
                    .parse(new String(Files.readAllBytes(new File(seedPat).toPath()), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray zxArr = pat.getAsJsonArray("zeroX");
            JsonArray zzArr = pat.getAsJsonArray("zeroZ");
            boolean[] zx = new boolean[zxArr.size()];
            boolean[] zz = new boolean[zzArr.size()];
            for (int i = 0; i < zx.length; i++) zx[i] = zxArr.get(i).getAsBoolean();
            for (int i = 0; i < zz.length; i++) zz[i] = zzArr.get(i).getAsBoolean();
            params.seedZeroX = zx;
            params.seedZeroZ = zz;
        }
        String objRounds = System.getenv("PKC_FOLD_OBJROUNDS");
        if (objRounds != null && !objRounds.isEmpty()) params.objectiveRounds = Integer.parseInt(objRounds);
        String deadlineMs = System.getenv("PKC_FOLD_DEADLINE_MS");
        if (deadlineMs != null && !deadlineMs.isEmpty()) {
            params.deadlineNanos = System.nanoTime() + Long.parseLong(deadlineMs) * 1_000_000L;
        }
        String seedDec = System.getenv("PKC_FOLD_SEEDDEC");
        if (seedDec != null && !seedDec.isEmpty()) {
            JsonObject dec = new com.google.gson.JsonParser()
                    .parse(new String(Files.readAllBytes(new File(seedDec).toPath()), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray ys = dec.getAsJsonArray("yawsDeg");
            double[] seedYaws = new double[ys.size()];
            for (int i = 0; i < seedYaws.length; i++) seedYaws[i] = ys.get(i).getAsDouble();
            params.seedYaws = seedYaws;
        }
        String refEnv = System.getenv("PKC_FOLD_REF");
        if (refEnv != null && !refEnv.isEmpty()) {
            String[] parts = refEnv.split(",");
            de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs sc = spec.asScenario();
            de.legoshi.parkourcalc.core.anglesolver.solver.StartBox box = sc.startBox;
            double rx = "lo".equals(parts[0]) ? box.pxLo : "hi".equals(parts[0]) ? box.pxHi
                    : "mid".equals(parts[0]) ? 0.5 * (box.pxLo + box.pxHi) : Double.parseDouble(parts[0]);
            double rz = "lo".equals(parts[1]) ? box.pzLo : "hi".equals(parts[1]) ? box.pzHi
                    : "mid".equals(parts[1]) ? 0.5 * (box.pzLo + box.pzHi) : Double.parseDouble(parts[1]);
            sc.startBox = new de.legoshi.parkourcalc.core.anglesolver.solver.StartBox(rx, rz,
                    box.vx, box.vz, box.pxLo, box.pxHi, box.pzLo, box.pzHi,
                    box.vx, box.vx, box.vz, box.vz);
            sc.startPos = new de.legoshi.parkourcalc.core.sim.Vec3dCore(rx, sc.startPos.y, rz);
            System.out.printf("FOLD ref=(%.6f, %.6f)%n", rx, rz);
        }
        FoldReplayDriver.Result res = FoldReplayDriver.solve(model, spec, params);
        for (FoldReplayDriver.Round r : res.rounds) {
            System.out.printf("FOLD round=%d bound=%.6f byteObj=%.9f maxViol=%.6g events=%d polished=%b%n",
                    r.index, r.linearBound, r.objective, r.maxViolation, r.patternEvents, r.polished);
        }
        FoldReplayDriver.Round b = res.best;
        if (b == null) {
            System.out.println("FOLD BEST none trivialInfeasible=" + res.trivialInfeasible);
        } else {
            System.out.printf("FOLD BEST round=%d obj=%.9f maxViol=%.6g px=%.9f pz=%.9f fixedPoint=%b%n",
                    b.index, b.objective, b.maxViolation, b.px, b.pz, res.fixedPoint);
        }

        if (out != null && !out.isEmpty() && b != null) {
            JsonObject root = new JsonObject();
            root.addProperty("capture", capPath);
            root.addProperty("objective", b.objective);
            root.addProperty("maxViol", b.maxViolation);
            root.addProperty("px", b.px);
            root.addProperty("pz", b.pz);
            root.addProperty("fixedPoint", res.fixedPoint);
            root.addProperty("rounds", res.rounds.size());
            JsonArray yaws = new JsonArray();
            for (double y : b.yawsDeg) yaws.add(y);
            root.add("yawsDeg", yaws);
            JsonArray zx = new JsonArray();
            for (boolean z : b.zeroX) zx.add(z);
            root.add("zeroX", zx);
            JsonArray zz = new JsonArray();
            for (boolean z : b.zeroZ) zz.add(z);
            root.add("zeroZ", zz);
            JsonArray allRounds = new JsonArray();
            for (FoldReplayDriver.Round r : res.rounds) {
                JsonObject ro = new JsonObject();
                ro.addProperty("index", r.index);
                ro.addProperty("objective", r.objective);
                ro.addProperty("maxViol", r.maxViolation);
                ro.addProperty("px", r.px);
                ro.addProperty("pz", r.pz);
                JsonArray ry = new JsonArray();
                for (double y : r.yawsDeg) ry.add(y);
                ro.add("yawsDeg", ry);
                allRounds.add(ro);
            }
            root.add("roundsDetail", allRounds);
            File dst = new File(out);
            if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
            Files.write(dst.toPath(), new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
                    .create().toJson(root).getBytes(StandardCharsets.UTF_8));
            System.out.println("FOLD wrote " + dst.getAbsolutePath());
        }
    }
}
