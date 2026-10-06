package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.Comparator;
import java.util.List;

public final class NoTurnRanking {

    public enum Mode {
        EASIEST("Easiest", "Fewest input changes first"),
        FURTHEST("Furthest", "Largest landing offset first");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private static final double OFFSET_TIE = 1.0e-6;

    private NoTurnRanking() {
    }

    public static int keyChanges(NoTurnResult r) {
        return r.pressCount >= 0 ? r.pressCount : NoTurnKeys.countPresses(r.combos);
    }

    public static int sprintToggles(NoTurnResult r) {
        int n = 0;
        boolean[] sprint = r.sprint;
        if (sprint == null || sprint.length == 0) return 0;
        if (sprint[0]) n++;
        for (int t = 1; t < sprint.length; t++) if (sprint[t] != sprint[t - 1]) n++;
        return n;
    }

    public static int inputChanges(NoTurnResult r) {
        return keyChanges(r) + sprintToggles(r);
    }

    public static double turnDeg(NoTurnResult r) {
        double[] yaws = r.yaws;
        if (yaws == null || yaws.length < 2) return 0.0;
        double m = 0.0;
        for (int t = 1; t < yaws.length; t++) m = Math.max(m, Math.abs(Angles.wrapDelta(yaws[t] - yaws[t - 1])));
        return m;
    }

    public static JumpConstraint goalWall(List<JumpConstraint> constraints, Objective objective) {
        boolean max = objective.sense == Objective.Sense.MAX;
        JumpConstraint.Cmp want = max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE;
        JumpConstraint.Mode mode = objective.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z;
        JumpConstraint tight = null;
        for (JumpConstraint c : constraints) {
            if (c.t2 != null || c.mode != mode || c.t1 != objective.tick || c.cmp != want) continue;
            if (tight == null || (max ? c.rhs > tight.rhs : c.rhs < tight.rhs)) tight = c;
        }
        return tight;
    }

    public static double offset(JumpConstraint goalWall, Objective objective, double objectiveValue) {
        boolean max = objective.sense == Objective.Sense.MAX;
        if (goalWall == null) return max ? objectiveValue : -objectiveValue;
        return max ? objectiveValue - goalWall.rhs : goalWall.rhs - objectiveValue;
    }

    public static Comparator<NoTurnResult> easiest(JumpConstraint goalWall, Objective objective) {
        return (a, b) -> {
            int c = compareEasiness(a, b);
            if (c != 0) return c;
            return compareOffset(a, b, goalWall, objective);
        };
    }

    public static Comparator<NoTurnResult> furthest(JumpConstraint goalWall, Objective objective) {
        return (a, b) -> {
            int c = compareOffset(a, b, goalWall, objective);
            if (c != 0) return c;
            return compareEasiness(a, b);
        };
    }

    public static Comparator<NoTurnResult> by(Mode mode, JumpConstraint goalWall, Objective objective) {
        return mode == Mode.FURTHEST ? furthest(goalWall, objective) : easiest(goalWall, objective);
    }

    private static int compareEasiness(NoTurnResult a, NoTurnResult b) {
        if (a.ja != b.ja) return a.ja ? 1 : -1;
        int ia = inputChanges(a);
        int ib = inputChanges(b);
        if (ia != ib) return Integer.compare(ia, ib);
        int ba = NoTurnKeys.countBackward(a.combos);
        int bb = NoTurnKeys.countBackward(b.combos);
        if (ba != bb) return Integer.compare(ba, bb);
        return Double.compare(Math.round(turnDeg(a)), Math.round(turnDeg(b)));
    }

    private static int compareOffset(NoTurnResult a, NoTurnResult b, JumpConstraint goalWall, Objective objective) {
        double oa = offset(goalWall, objective, a.objective);
        double ob = offset(goalWall, objective, b.objective);
        if (Double.isNaN(oa) || Double.isNaN(ob)) return Boolean.compare(Double.isNaN(oa), Double.isNaN(ob));
        if (Math.abs(oa - ob) <= OFFSET_TIE) return 0;
        return oa > ob ? -1 : 1;
    }
}
