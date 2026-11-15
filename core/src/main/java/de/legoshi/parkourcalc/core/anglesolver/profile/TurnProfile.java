package de.legoshi.parkourcalc.core.anglesolver.profile;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;

public final class TurnProfile {

    private TurnProfile() {
    }

    public static double pixelDeg(float sensitivity) {
        double f = sensitivity * 0.6 + 0.2;
        return 0.15 * f * f * f * 8.0;
    }

    public static boolean lands(ForwardModel model, JumpSpec spec, double[] yawsAbsWrapped) {
        JumpPhysicsInputs sc = spec.asScenario();
        double[] facing = Angles.wrapAll(yawsAbsWrapped.clone());
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(facing);
        return comp.maxViolation(gf, model.forward(sc, gf)) <= 0.0;
    }
}
