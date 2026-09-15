package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CrossJvmDeterminismTest {

    static final long GOLDEN_LINEAR_MODEL = 6680509507868675287L;
    static final long GOLDEN_CLOSED_FORM = 7100074979304643135L;

    @Test
    public void searchPathIsBitIdenticalAcrossJvms() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("j004"));
        assertNotNull(file);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(spec);

        JumpLinearModel lin = new JumpLinearModel(spec.asScenario());
        long linHash = 1469598103934665603L;
        for (int t = 0; t < lin.n; t++) {
            linHash = mix(linHash, lin.mMag(t));
            linHash = mix(linHash, lin.baseArg(t));
            linHash = mix(linHash, lin.constPos(t, 0));
            linHash = mix(linHash, lin.constPos(t, 1));
        }
        double[] cf = ClosedFormSolve.optimize(model, spec, 0.0, new AtomicBoolean(false));
        long cfHash = cf == null ? 0L : hash(cf);

        String report = String.format(java.util.Locale.ROOT,
                "linear=%dL closedForm=%dL (%s)", linHash, cfHash, System.getProperty("java.version"));
        System.out.println("[cross-jvm] " + report);
        if (GOLDEN_LINEAR_MODEL != 0L) assertEquals("linear model drifted: " + report, GOLDEN_LINEAR_MODEL, linHash);
        if (GOLDEN_CLOSED_FORM != 0L) assertEquals("closed form drifted: " + report, GOLDEN_CLOSED_FORM, cfHash);
    }

    private static long hash(double[] values) {
        long h = 1469598103934665603L;
        for (double v : values) h = mix(h, v);
        return h;
    }

    private static long mix(long h, double v) {
        long bits = Double.doubleToLongBits(v);
        for (int i = 0; i < 8; i++) {
            h ^= (bits >>> (i * 8)) & 0xffL;
            h *= 1099511628211L;
        }
        return h;
    }
}
