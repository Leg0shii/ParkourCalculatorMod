package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

/** Shared byte-exact scoring of a yaw vector against a spec, as the checks and the engine's verify do it. */
final class Paths {

    private Paths() {
    }

    static double[] gameFacings(JumpPhysicsInputs sc, double[] yawsAbs) {
        return sc.toGameFacings(Angles.wrapAll(yawsAbs));
    }

    static double objective(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        return model.forward(sc, gameFacings(sc, yawsAbs)).getPos(spec.objective.tick, spec.objective.axis);
    }

    static double violation(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        double[] gf = gameFacings(sc, yawsAbs);
        ForwardPath path = model.forward(sc, gf);
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
    }

    static boolean feasible(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        return violation(model, sc, spec, yawsAbs) <= 0.0;
    }

    static double[] resultYaws(SolveResult r) {
        double[] yaws = new double[r.getYaws().size()];
        for (int k = 0; k < yaws.length; k++) yaws[k] = r.getYaws().get(k).yaw;
        return yaws;
    }

    /** Positive when {@code candidate} is worse than {@code reference} in the objective's sense. */
    static double shortfall(Objective.Sense sense, double candidate, double reference) {
        return sense == Objective.Sense.MAX ? reference - candidate : candidate - reference;
    }

    static String detail(SolveResult r, String label) {
        for (SolveResult.Detail d : r.getDetails()) {
            if (label.equals(d.label)) return d.value;
        }
        return null;
    }

    static int unmet(SolveResult r) {
        int unmet = 0;
        for (SolveResult.Outcome o : r.getOutcomes()) {
            if (!o.met) unmet++;
        }
        return unmet;
    }
}
