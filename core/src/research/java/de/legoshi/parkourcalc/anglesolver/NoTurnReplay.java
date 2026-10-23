package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
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

public class NoTurnReplay {

    @Test
    public void replay() throws Exception {
        String capPath = System.getenv("PKC_NOTURN_CAPTURE");
        Assume.assumeTrue("set PKC_NOTURN_CAPTURE to a capture path", capPath != null && !capPath.isEmpty());
        String decPath = System.getenv("PKC_NOTURN_FILE");
        Assume.assumeTrue("set PKC_NOTURN_FILE to a decode json", decPath != null && !decPath.isEmpty());
        String out = System.getenv("PKC_NOTURN_OUT");
        if (out == null || out.isEmpty()) out = "build/reports/noturn-replay.json";

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
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        JsonObject dec;
        try (Reader r = new FileReader(decPath)) {
            dec = new JsonParser().parse(r).getAsJsonObject();
        }
        double[] yaws = readDoubles(dec.get("yawsDeg"), n);
        float[] fwd = readFloats(dec.get("forward"), n);
        float[] strafe = readFloats(dec.get("strafe"), n);
        boolean[] sprint = readBools(dec.get("sprint"), n);
        double px = dec.has("px") ? dec.get("px").getAsDouble() : sc.startPos.x;
        double pz = dec.has("pz") ? dec.get("pz").getAsDouble() : sc.startPos.z;

        JumpPhysicsInputs sc2 = Scoring.pinnedScenario(sc, px, pz);
        sc2.forwardInputPerTick = fwd;
        sc2.strafeInputPerTick = strafe;
        sc2.sprintPerTick = sprint;

        double[] gf = sc2.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(sc2, gf);
        Objective obj = spec.objective;
        double pos = fp.getPos(obj.tick, obj.axis);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double maxViol = comp.maxViolation(gf, fp);

        JsonObject root = new JsonObject();
        root.addProperty("capture", capPath);
        root.addProperty("decode", decPath);
        root.addProperty("objTick", obj.tick);
        root.addProperty("objAxis", obj.axis.name());
        root.addProperty("objSense", obj.sense.name());
        root.addProperty("objective", pos);
        root.addProperty("maxViol", maxViol);
        root.addProperty("px", px);
        root.addProperty("pz", pz);
        JsonArray viols = new JsonArray();
        for (JumpConstraint c : spec.constraints) {
            double s = JumpConstraintCompiler.slack(c, gf, fp);
            if (s <= 0.0) continue;
            JsonObject vj = new JsonObject();
            vj.addProperty("name", c.name);
            vj.addProperty("slack", s);
            viols.add(vj);
        }
        root.add("violations", viols);
        root.add("gameFacings", arr(gf));
        root.add("posX", arr(fp.posX));
        root.add("posZ", arr(fp.posZ));
        root.add("velX", arr(fp.velX));
        root.add("velZ", arr(fp.velZ));

        String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
        File dst = new File(out);
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("NOTURN REPLAY wrote " + dst.getAbsolutePath()
                + " objective=" + pos + " maxViol=" + maxViol + " violations=" + viols.size());
    }

    private static double[] readDoubles(JsonElement e, int n) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != n) throw new IllegalStateException("expected " + n + " entries, got " + a.size());
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static float[] readFloats(JsonElement e, int n) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != n) throw new IllegalStateException("expected " + n + " entries, got " + a.size());
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static boolean[] readBools(JsonElement e, int n) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != n) throw new IllegalStateException("expected " + n + " entries, got " + a.size());
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) out[i] = a.get(i).getAsBoolean();
        return out;
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(v);
        return j;
    }
}
