package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
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
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class MiqcpDump {

    @Test
    public void dump() throws Exception {
        Assume.assumeTrue("set PKC_MIQCP_DUMP=1 to run", "1".equals(System.getenv("PKC_MIQCP_DUMP")));
        String cse = System.getenv("PKC_MIQCP_CASE");
        if (cse == null || cse.isEmpty()) cse = "proof";

        JumpSpec spec;
        JumpPhysicsInputs sc;
        ExactJumpModel model;
        double[] warm;
        int startTick;
        String warmSource;

        if ("proof".equals(cse)) {
            Loaded l = loadCapture("razor-proof-improved");
            spec = l.spec;
            sc = l.sc;
            model = l.model;
            warm = l.warm;
            startTick = l.startTick;
            warmSource = "razor-proof-improved";
        } else if ("rung5375".equals(cse)) {
            Loaded base = loadCapture("razor-proof");
            RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(base.spec);
            spec = patch.spec;
            sc = spec.asScenario();
            model = base.model;
            Loaded warmL = loadCapture("razor-proof-improved");
            warm = warmL.warm;
            startTick = base.startTick;
            warmSource = "razor-proof-improved (proof-improved yaws on rung5375-patched spec)";
        } else {
            throw new IllegalArgumentException("unknown PKC_MIQCP_CASE: " + cse);
        }

        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        JumpLinearModel lm = new JumpLinearModel(sc);

        double[] gf = sc.toGameFacings(Angles.wrapAll(warm));
        ForwardPath path = model.forward(sc, gf);
        double warmObjX = path.getPos(objTick, spec.objective.axis);
        double warmViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);

        JsonObject root = new JsonObject();
        root.addProperty("case", cse);
        root.addProperty("warmSource", warmSource);
        root.addProperty("modern", model.modern());
        root.addProperty("perAxisInertia", model.perAxisInertia());
        root.addProperty("inertiaThreshold", model.inertiaThreshold());
        root.addProperty("numTicks", n);
        root.addProperty("objTick", objTick);
        root.addProperty("objAxis", spec.objective.axis.name());
        root.addProperty("objSense", spec.objective.sense.name());
        root.addProperty("startTick", startTick);

        JsonArray startPos = new JsonArray();
        startPos.add(sc.startPos.x);
        startPos.add(sc.startPos.y);
        startPos.add(sc.startPos.z);
        root.add("startPos", startPos);

        JsonArray initVel = new JsonArray();
        initVel.add(sc.initialVelocity.x);
        initVel.add(sc.initialVelocity.y);
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
            tk.addProperty("f4", lm.friction(t));
            tk.addProperty("mMag", lm.mMag(t));
            tk.addProperty("baseArg", lm.baseArg(t));
            tk.addProperty("forwardMag", lm.forwardMag(t));
            tk.addProperty("strafeMag", lm.strafeMag(t));
            tk.addProperty("boost", lm.boostAt(t));
            double slip = sc.slipAt(t);
            boolean contact = !Double.isNaN(slip);
            tk.addProperty("contact", contact);
            if (contact) tk.addProperty("slip", slip);
            else tk.add("slip", null);
            tk.addProperty("jump", sc.jumpAt(t) && contact);
            tk.addProperty("sprint", sc.sprintAt(t));
            tk.addProperty("factorSprint", sc.factorSprintAt(t));
            tk.addProperty("amp", sc.factorAmpAt(t));
            ticks.add(tk);
        }
        root.add("ticks", ticks);

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

        JsonArray warmYaws = new JsonArray();
        for (double w : warm) warmYaws.add(w);
        root.add("warmYawsDeg", warmYaws);

        JsonArray warmGf = new JsonArray();
        for (double g : gf) warmGf.add(g);
        root.add("warmGameFacings", warmGf);

        JsonArray phiWarm = new JsonArray();
        for (int t = 0; t < n; t++) phiWarm.add(lm.baseArg(t) + Math.toRadians(warm[t]));
        root.add("phiWarmRad", phiWarm);

        root.add("warmPosX", arr(path.posX));
        root.add("warmPosZ", arr(path.posZ));
        root.add("warmVelX", arr(path.velX));
        root.add("warmVelZ", arr(path.velZ));
        root.addProperty("warmObjX", warmObjX);
        root.addProperty("warmViol", warmViol);

        String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
        File dst = new File("build/reports/miqcp-dump-" + cse + ".json");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("MIQCP DUMP wrote " + dst.getAbsolutePath()
                + " n=" + n + " objTick=" + objTick + " warmObjX=" + warmObjX + " warmViol=" + warmViol
                + " constraints=" + spec.constraints.size());
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(v);
        return j;
    }

    private static final class Loaded {
        JumpSpec spec;
        JumpPhysicsInputs sc;
        ExactJumpModel model;
        double[] warm;
        int startTick;
    }

    private static Loaded loadCapture(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        if (file.debug == null || file.debug.isEmpty()) throw new IllegalStateException(capture + ": no debug ticks");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int startTick = state.getStartTick();
        int n = sc.numTicks;
        double[] warm = new double[n];
        List<SaveFile.DebugTick> debug = file.debug;
        for (int k = 0; k < n; k++) warm[k] = debug.get(startTick + k + 1).yaw;
        Loaded l = new Loaded();
        l.spec = spec;
        l.sc = sc;
        l.model = model;
        l.warm = warm;
        l.startTick = startTick;
        return l;
    }
}
