package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;

public final class GoalWall {

    private GoalWall() {
    }

    public static JumpConstraint tightest(List<JumpConstraint> constraints, Objective objective) {
        return select(constraints, objective, new String[1], true);
    }

    public static JumpConstraint selectLegal(List<JumpConstraint> constraints, Objective objective, String[] whyNot) {
        return select(constraints, objective, whyNot, false);
    }

    private static JumpConstraint select(List<JumpConstraint> constraints, Objective objective,
                                         String[] whyNot, boolean allowTies) {
        if (objective.isCustomAngle()) {
            whyNot[0] = "the Angle goal has no goal wall on an axis";
            return null;
        }
        boolean max = objective.sense == Objective.Sense.MAX;
        JumpConstraint.Cmp want = max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE;
        JumpConstraint.Mode wantMode = objective.axis == JumpPhysicsInputs.Axis.X
                ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        List<JumpConstraint> cands = new ArrayList<>();
        for (JumpConstraint c : constraints) {
            if (c.t2 != null) continue;
            if (c.mode != wantMode) continue;
            if (c.t1 != objective.tick) continue;
            if (c.cmp != want) continue;
            if (c.isEqHalf()) continue;
            cands.add(c);
        }
        if (cands.isEmpty()) {
            whyNot[0] = "no qualifying goal wall on the objective axis at the objective tick";
            return null;
        }
        JumpConstraint tight = cands.get(0);
        for (JumpConstraint c : cands) {
            if (max ? c.rhs > tight.rhs : c.rhs < tight.rhs) tight = c;
        }
        int ties = 0;
        for (JumpConstraint c : cands) {
            if (c.rhs == tight.rhs) ties++;
        }
        if (ties != 1 && !allowTies) {
            whyNot[0] = "ambiguous goal wall: " + ties + " walls tie at " + tight.rhs;
            return null;
        }
        return tight;
    }
}
