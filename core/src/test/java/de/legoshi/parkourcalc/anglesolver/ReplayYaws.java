package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ReplayYaws {

    @Test
    public void replay() throws Exception {
        String capPath = System.getenv("PKC_REPLAY_CAP");
        String yawPath = System.getenv("PKC_REPLAY_YAWS");
        Assume.assumeTrue("set PKC_REPLAY_CAP and PKC_REPLAY_YAWS", capPath != null && yawPath != null);

        String raw;
        File direct = new File(capPath);
        raw = direct.isFile() ? new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8)
                : Fixtures.rawPool(capPath);
        SaveFile file = SaveIO.parseSafe(raw);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        JsonObject y = new JsonParser().parse(
                new String(Files.readAllBytes(new File(yawPath).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray ya = y.getAsJsonArray("yawsDeg");
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) yaws[t] = ya.get(t).getAsDouble();
        double contPos = y.has("contPos") ? y.get("contPos").getAsDouble() : Double.NaN;

        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        double viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);

        System.out.printf("REPLAY cap=%s n=%d objTick=%d axis=%s sense=%s contPos=%.9f byteExactObj=%.9f viol=%.6e diff(byte-cont)=%.3e%n",
                new File(capPath).getName(), n, spec.objective.tick, spec.objective.axis, spec.objective.sense,
                contPos, obj, viol, obj - contPos);
    }
}
