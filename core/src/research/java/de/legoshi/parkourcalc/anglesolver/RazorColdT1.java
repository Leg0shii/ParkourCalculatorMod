package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RazorColdT1 {

    @Test
    public void coldHeadlineGate() throws Exception {
        Assume.assumeTrue("set PKC_RAZORT1=1 to run", "1".equals(System.getenv("PKC_RAZORT1")));
        String tag = System.getenv("PKC_RAZORT1_TAG");
        assertNotNull("PKC_RAZORT1_TAG must carry a fresh run tag", tag);
        long timeoutS = Long.parseLong(System.getenv().getOrDefault("PKC_RAZORT1_S", "300"));
        int optimizeS = Integer.parseInt(System.getenv().getOrDefault("PKC_RAZORT1_OPT_S", "180"));

        StringBuilder rep = new StringBuilder();
        rep.append(String.format(Locale.ROOT, "applied: tag=%s timeoutS=%d optimizeS=%d fixture=razor-proof-t1%n",
                tag, timeoutS, optimizeS));

        String rawJson = Fixtures.rawPool("razor-proof-t1");
        SaveFile file = SaveIO.parseSafe(rawJson);
        assertNotNull(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.THOROUGH);
        state.setOptimizeSeconds(optimizeS);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);

        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutS * 1000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(5);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        SolveResult r = state.getResult();
        if (r == null) {
            rep.append(String.format(Locale.ROOT, "RESULT no result after %d ms (still solving)%n", ms));
            write(tag, rep);
            assertTrue("no result within the budget", false);
        }
        JumpSpec spec = engine.lastSpecDebug();
        JumpPhysicsInputs sc = spec.asScenario();
        rep.append(String.format(Locale.ROOT,
                "RESULT success=%b met=%d/%d ms=%d obj=%s solver=\"%s\" finalStart=(%.15f,%.15f)%n",
                r.isSuccess(), r.getMet(), r.getTotal(), ms,
                r.hasObjective() ? String.format(Locale.ROOT, "%.10f", r.getObjectiveValue()) : "-",
                r.getSolver(), sc.startPos.x, sc.startPos.z));

        boolean reparseOk = false;
        if (r.isSuccess()) {
            engine.apply();
            reparseOk = freshReparseVerify(rep, rawJson, inputs, sc);
        }
        write(tag, rep);
        assertTrue("headline gate: cold 5.4375 from t1 must solve (see report)", r.isSuccess());
        assertTrue("fresh reparse must verify byte-exact viol <= 0", reparseOk);
    }

    private boolean freshReparseVerify(StringBuilder rep, String rawJson, InputData appliedInputs,
                                       JumpPhysicsInputs solvedSc) throws Exception {
        JsonObject root = new JsonParser().parse(rawJson).getAsJsonObject();
        Gson gson = new Gson();
        JsonArray rows = new JsonArray();
        for (InputRow row : appliedInputs.getRows()) {
            SaveFile.Row sr = new SaveFile.Row();
            for (InputRow.Key k : InputRow.Key.values()) {
                if (row.isKeyActive(k)) sr.keys.add(k.name());
            }
            sr.yaw = row.getYaw();
            sr.yawLocked = row.isYawLocked();
            sr.pitch = row.getPitch();
            sr.pitchLocked = row.isPitchLocked();
            sr.speedAmplifier = row.getSpeedAmplifier();
            sr.jumpBoostAmplifier = row.getJumpBoostAmplifier();
            rows.add(gson.toJsonTree(sr));
        }
        root.add("rows", rows);
        JsonObject seed = root.getAsJsonObject("angleSolver").getAsJsonObject("seed");
        JsonArray pos = new JsonArray();
        pos.add(solvedSc.startPos.x);
        pos.add(solvedSc.startPos.y);
        pos.add(solvedSc.startPos.z);
        seed.add("pos", pos);
        String freshJson = new GsonBuilder().setPrettyPrinting().create().toJson(root);

        SaveFile fresh = SaveIO.parseSafe(freshJson);
        if (fresh == null) {
            rep.append("REPARSE failed to parse\n");
            return false;
        }
        ExactJumpModel model = ExactJumpModel.forMcVersion(fresh.mcVersion);
        InputData freshInputs = new InputData();
        SaveIO.applyRowsTo(fresh, freshInputs);
        AngleSolverState freshState = new AngleSolverState();
        SaveIO.applyAngleSolverTo(fresh, freshState);
        AngleSolverEngine freshEngine = new AngleSolverEngine(freshState, Fixtures.buildBoxes(fresh),
                freshInputs, t -> { }, model);
        JumpSpec spec2 = freshEngine.debugBuildSpec();
        JumpPhysicsInputs sc2 = spec2.asScenario();
        int n = sc2.numTicks;
        double[] gf2 = new double[n];
        float entity = sc2.startYaw;
        for (int k = 0; k < n; k++) {
            InputRow row = freshInputs.getRows().get(k);
            if (row.isYawLocked()) {
                entity = row.getYaw();
            } else {
                entity = entity + row.getYaw();
            }
            gf2[k] = entity;
        }
        ForwardPath p2 = model.forward(sc2, gf2);
        double viol2 = JumpConstraintCompiler.compile(spec2).maxViolation(gf2, p2);
        double obj2 = p2.getPos(spec2.objective.tick, spec2.objective.axis);
        rep.append(String.format(Locale.ROOT, "REPARSE viol=%.9e objX=%.10f start=(%.15f,%.15f) %s%n",
                viol2, obj2, sc2.startPos.x, sc2.startPos.z, viol2 <= 0.0 ? "PASS" : "FAIL"));
        return viol2 <= 0.0;
    }

    private static void write(String tag, StringBuilder rep) throws Exception {
        File dst = new File("build/reports/razort1-" + tag + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(rep);
    }
}
