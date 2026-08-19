package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direction-independent objective ascent for a feasible witness whose own direction's deterministic
 *  ascent degenerated (optimizing into a same-axis wall degenerates the dual recovery, so one Solve For
 *  can certify while the opposite fails; see {@link ClosedFormSolve}). Instead of a local ascent, the
 *  objective is rewritten as a movable goal wall and its level is bisected between the witness's achieved
 *  value and the weak-duality bound ({@link ClosedFormSolve#dualBound}); each rung is a feasibility solve
 *  ({@link SlpSolve} phase 1) seeded from the best point so far, which is the robust operation feasibility
 *  is (unlike a local objective ascent) direction-independent. Monotone: the returned facings never realize
 *  a worse objective than the witness. Returns {@code null} when the ladder does not apply (a facing/dF
 *  wall leaves the dual bound undefined), leaving the caller to keep the witness and inform the user. */
public final class LevelSetAscent {

    public static final class Config {
        public int iters = 12;
        public int slpPhase1Calls = 40;
        public int slpTotalCalls = 60;
    }

    private LevelSetAscent() {
    }

    public static double[] improve(ExactJumpModel exact, JumpSpec spec, double[] witness,
                                   double feasTol, AtomicBoolean cancel) {
        return improve(exact, spec, witness, feasTol, cancel, new Config());
    }

    public static double[] improve(ExactJumpModel exact, JumpSpec spec, double[] witness,
                                   double feasTol, AtomicBoolean cancel, Config cfg) {
        if (witness == null) return null;
        if (spec.objective.isCustomAngle()) return null;
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return null;
        double bound = ClosedFormSolve.dualBound(spec);
        if (Double.isNaN(bound)) return null;

        boolean max = spec.objective.sense == Objective.Sense.MAX;
        JumpPhysicsInputs sc = spec.asScenario();
        double baseObj = objectiveOf(exact, sc, spec, witness);
        double gap = max ? bound - baseObj : baseObj - bound;
        if (gap <= feasTol) return witness;

        JumpConstraint.Mode mode = spec.objective.axis == JumpPhysicsInputs.Axis.X
                ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        JumpConstraint.Cmp cmp = max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE;

        double[] best = witness;
        double bestObj = baseObj;
        double feasibleEnd = baseObj;   // best objective known achievable
        double optimisticEnd = bound;   // no feasible point is beyond the dual bound
        for (int i = 0; i < cfg.iters; i++) {
            if (cancel != null && cancel.get()) break;
            double level = 0.5 * (feasibleEnd + optimisticEnd);
            List<JumpConstraint> aug = new ArrayList<>(spec.constraints);
            aug.add(new JumpConstraint(mode, spec.objective.tick, null, JumpConstraint.Op.PLUS, cmp, level, "levelset"));
            double[] y = SlpSolve.optimize(exact, new JumpSpec(sc, aug, spec.objective), feasTol, cancel,
                    best, cfg.slpPhase1Calls, cfg.slpTotalCalls);
            if (y != null) {
                double o = objectiveOf(exact, sc, spec, y);
                if (max ? o > bestObj : o < bestObj) {
                    best = y;
                    bestObj = o;
                }
                feasibleEnd = o;
            } else {
                optimisticEnd = level;
            }
        }
        return best;
    }

    private static double objectiveOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] yawsAbs) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawsAbs));
        return spec.objective.evaluate(exact.forward(sc, gf));
    }
}
