package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class NoTurnCapture {

    public final SaveFile file;
    public final ExactJumpModel model;
    public final InputData inputs;
    public final AngleSolverState state;

    private NoTurnCapture(SaveFile file) {
        this.file = file;
        this.model = ExactJumpModel.forMcVersion(file.mcVersion);
        this.inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        this.state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
    }

    public static NoTurnCapture load(String capture) throws IOException {
        String raw;
        File direct = new File(capture);
        if (direct.isFile()) raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        else raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        return new NoTurnCapture(file);
    }

    public JumpSpec buildSpec() {
        return new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model).debugBuildSpec();
    }

    public static boolean hasFreeBox(JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        return sc.startBox != null && sc.startBox.startFree();
    }

    public boolean addFreeBox(JumpSpec spec, double half) {
        if (half <= 0 || hasFreeBox(spec)) return false;
        JumpPhysicsInputs sc0 = spec.asScenario();
        TickConstraints tc = state.tickConstraints(state.getStartTick());
        tc.getConstraints().add(Constraint.range(Constraint.Field.X, sc0.startPos.x - half,
                sc0.startPos.x + half, true, true));
        tc.getConstraints().add(Constraint.range(Constraint.Field.Z, sc0.startPos.z - half,
                sc0.startPos.z + half, true, true));
        return true;
    }
}
