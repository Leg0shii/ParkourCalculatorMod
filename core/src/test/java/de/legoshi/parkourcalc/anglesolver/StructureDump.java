package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
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
import java.util.List;

public class StructureDump {

    @Test
    public void dump() throws Exception {
        String path = System.getenv("PKC_STRUCT_FILE");
        Assume.assumeTrue("set PKC_STRUCT_FILE to a capture path", path != null && !path.isEmpty());
        String out = System.getenv("PKC_STRUCT_OUT");
        if (out == null || out.isEmpty()) out = "build/reports/structure-dump.json";

        String raw;
        File direct = new File(path);
        if (direct.isFile()) {
            raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        } else {
            raw = Fixtures.rawPool(path);
        }
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(path + ": failed to parse");

        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        boolean[] zeroX = null;
        boolean[] zeroZ = null;
        String zeroPath = System.getenv("PKC_STRUCT_ZERO");
        if (zeroPath != null && !zeroPath.isEmpty()) {
            JsonObject pat;
            try (Reader r = new FileReader(zeroPath)) {
                pat = new JsonParser().parse(r).getAsJsonObject();
            }
            zeroX = readBools(pat.getAsJsonArray("zeroX"), n);
            zeroZ = readBools(pat.getAsJsonArray("zeroZ"), n);
        }
        JumpLinearModel lm = zeroX != null ? new JumpLinearModel(sc, zeroX, zeroZ) : new JumpLinearModel(sc);
        int startTick = state.getStartTick();

        Objective obj = spec.objective;
        int objTick = obj.tick;
        int objAxis = (obj.axis == JumpPhysicsInputs.Axis.X) ? 0 : 1;
        boolean objMax = obj.sense == Objective.Sense.MAX;

        double[] cx = new double[n];
        double[] cz = new double[n];
        lm.objectiveVectors(obj, cx, cz);
        double objConst = lm.constPos(objTick, objAxis);

        boolean[] trivial = new boolean[1];
        List<JumpLinearModel.Wall> walls = lm.compileWalls(spec.constraints, 0.0, trivial);
        if (zeroX != null) walls.addAll(lm.velocityWalls(model.inertiaThreshold()));

        JsonObject root = new JsonObject();
        root.addProperty("capture", path);
        root.addProperty("mcVersion", file.mcVersion);
        root.addProperty("numTicks", n);
        root.addProperty("startTick", startTick);
        root.addProperty("objTick", objTick);
        root.addProperty("objAxis", objAxis);
        root.addProperty("objAxisName", obj.axis.name());
        root.addProperty("objSense", obj.sense.name());
        root.addProperty("objMaximize", objMax);
        root.addProperty("objConst", objConst);
        root.addProperty("modern", model.modern());
        root.addProperty("perAxisInertia", model.perAxisInertia());
        root.addProperty("inertiaThreshold", model.inertiaThreshold());
        root.addProperty("trivialInfeasible", trivial[0]);
        root.addProperty("hasFacingWall", JumpLinearModel.hasFacingWall(spec.constraints));

        JsonArray startPos = new JsonArray();
        startPos.add(sc.startPos.x);
        startPos.add(sc.startPos.z);
        root.add("startPos", startPos);
        JsonArray initVel = new JsonArray();
        initVel.add(sc.initialVelocity.x);
        initVel.add(sc.initialVelocity.z);
        root.add("initialVelocity", initVel);

        StartBox sb = sc.startBox;
        if (sb == null) {
            root.add("startBox", null);
        } else {
            JsonObject sbj = new JsonObject();
            sbj.addProperty("startFree", sb.startFree());
            sbj.addProperty("pinned", sb.isPinned());
            sbj.addProperty("px", sb.px);
            sbj.addProperty("pz", sb.pz);
            sbj.addProperty("vx", sb.vx);
            sbj.addProperty("vz", sb.vz);
            sbj.addProperty("pxLo", sb.pxLo);
            sbj.addProperty("pxHi", sb.pxHi);
            sbj.addProperty("pzLo", sb.pzLo);
            sbj.addProperty("pzHi", sb.pzHi);
            sbj.addProperty("vxLo", sb.vxLo);
            sbj.addProperty("vxHi", sb.vxHi);
            sbj.addProperty("vzLo", sb.vzLo);
            sbj.addProperty("vzHi", sb.vzHi);
            root.add("startBox", sbj);
        }

        JsonArray ticks = new JsonArray();
        for (int t = 0; t < n; t++) {
            JsonObject tk = new JsonObject();
            tk.addProperty("t", t);
            tk.addProperty("mMag", lm.mMag(t));
            tk.addProperty("baseArg", lm.baseArg(t));
            tk.addProperty("f4", lm.friction(t));
            tk.addProperty("forwardMag", lm.forwardMag(t));
            tk.addProperty("strafeMag", lm.strafeMag(t));
            tk.addProperty("boost", lm.boostAt(t));
            double slip = sc.slipAt(t);
            boolean contact = !Double.isNaN(slip);
            tk.addProperty("contact", contact);
            tk.addProperty("jump", sc.jumpAt(t) && contact);
            tk.addProperty("sprint", sc.sprintAt(t));
            tk.addProperty("cx", cx[t]);
            tk.addProperty("cz", cz[t]);
            ticks.add(tk);
        }
        root.add("ticks", ticks);

        JsonArray wjson = new JsonArray();
        for (JumpLinearModel.Wall w : walls) {
            JsonObject wj = new JsonObject();
            wj.addProperty("name", w.name);
            wj.addProperty("axis", w.axis);
            wj.addProperty("bPrime", w.bPrime);
            wj.addProperty("eq", w.eq);
            wj.addProperty("p0coef", w.p0coef);
            wj.add("coef", arr(w.coef));
            wjson.add(wj);
        }
        root.add("walls", wjson);

        JsonArray cons = new JsonArray();
        for (JumpConstraint c : spec.constraints) {
            JsonObject cj = new JsonObject();
            cj.addProperty("name", c.name);
            cj.addProperty("mode", c.mode.name());
            cj.addProperty("t1", c.t1);
            if (c.t2 == null) cj.add("t2", null);
            else cj.addProperty("t2", c.t2);
            cj.addProperty("op", c.op.name());
            cj.addProperty("cmp", c.cmp.name());
            cj.addProperty("rhs", c.rhs);
            cons.add(cj);
        }
        root.add("constraints", cons);

        if (file.debug != null && !file.debug.isEmpty() && file.debug.size() > startTick + n) {
            double[] warm = new double[n];
            List<SaveFile.DebugTick> debug = file.debug;
            for (int k = 0; k < n; k++) warm[k] = debug.get(startTick + k + 1).yaw;
            double[] gf = sc.toGameFacings(Angles.wrapAll(warm));
            ForwardPath fp = model.forward(sc, gf);
            double warmObj = fp.getPos(objTick, obj.axis);
            double warmViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
            root.add("warmYawsDeg", arr(warm));
            root.add("warmGameFacings", arr(gf));
            root.addProperty("warmObj", warmObj);
            root.addProperty("warmViol", warmViol);
        }

        String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
        File dst = new File(out);
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("STRUCT DUMP wrote " + dst.getAbsolutePath()
                + " n=" + n + " objTick=" + objTick + " walls=" + walls.size()
                + " free=" + (sb != null && sb.startFree()) + " facingWall=" + JumpLinearModel.hasFacingWall(spec.constraints));
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(v);
        return j;
    }

    private static boolean[] readBools(JsonArray a, int n) {
        boolean[] out = new boolean[n];
        int len = Math.min(n, a.size());
        for (int i = 0; i < len; i++) out[i] = a.get(i).getAsBoolean();
        return out;
    }
}
