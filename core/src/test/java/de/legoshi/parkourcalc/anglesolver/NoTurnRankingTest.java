package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnRanking;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NoTurnRankingTest {

    private static final int GOAL_TICK = 12;
    private static final Objective MAX_X = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, GOAL_TICK);
    private static final Objective MIN_Z = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, GOAL_TICK);

    private static NoTurnResult line(int[] combos, boolean[] sprint, boolean ja, double objective, double[] yaws) {
        return new NoTurnResult(combos, sprint, NoTurnKeys.WA, ja, NoTurnKeys.countEdges(combos),
                NoTurnKeys.firstSprint(sprint), objective, 0.0, 0.0, 0.0, yaws);
    }

    private static boolean[] sprintFrom(int len, int engage) {
        boolean[] s = new boolean[len];
        for (int t = 0; t < len; t++) s[t] = engage >= 0 && t >= engage;
        return s;
    }

    private static double[] flat(int len, double yaw) {
        double[] y = new double[len];
        Arrays.fill(y, yaw);
        return y;
    }

    private static JumpConstraint wall(JumpConstraint.Mode mode, int tick, JumpConstraint.Cmp cmp, double rhs, String name) {
        return new JumpConstraint(mode, tick, null, JumpConstraint.Op.PLUS, cmp, rhs, name);
    }

    @Test
    public void inputChangesCountKeyEdgesAndSprintToggles() {
        int[] combos = {NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WA, NoTurnKeys.W};
        NoTurnResult r = line(combos, sprintFrom(5, 2), false, 0.0, flat(6, 0.0));
        assertEquals(3, NoTurnRanking.keyChanges(r));
        assertEquals(1, NoTurnRanking.sprintToggles(r));
        assertEquals(4, NoTurnRanking.inputChanges(r));
    }

    @Test
    public void keyChangesOnAJumpTickAreFree() {
        int[] wad = {NoTurnKeys.NONE, NoTurnKeys.WD, NoTurnKeys.WD, NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WA, NoTurnKeys.W};
        boolean[] jump = {true, false, false, true, false, false, false};
        assertEquals(4, NoTurnKeys.countEdges(wad));
        assertEquals(3, NoTurnKeys.countEdges(wad, jump));
        assertEquals(3, NoTurnKeys.countPresses(wad, jump));
        int[] pressedOnSpace = {NoTurnKeys.WD, NoTurnKeys.WD, NoTurnKeys.WD, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W};
        assertEquals(2, NoTurnKeys.countPresses(pressedOnSpace));
        assertEquals(0, NoTurnKeys.countPresses(pressedOnSpace, jump));
        assertEquals(2, NoTurnKeys.countPresses(pressedOnSpace, null));
    }

    @Test
    public void boundPressCountOverridesTheKeyEdgeCount() {
        int[] combos = {NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W};
        NoTurnResult r = line(combos, sprintFrom(3, -1), false, 0.0, flat(4, 0.0));
        assertEquals(1, NoTurnRanking.keyChanges(r));
        r.pressCount = 2;
        assertEquals(2, NoTurnRanking.keyChanges(r));
        assertEquals(2, NoTurnRanking.inputChanges(r));
    }

    @Test
    public void easiestOrdersNoJaFirstThenFewestInputsThenBackwardThenTurn() {
        int n = 6;
        NoTurnResult fewInputs = line(new int[]{NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), false, 1.0, flat(n + 1, 0.0));
        NoTurnResult moreInputs = line(new int[]{NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.W, NoTurnKeys.WD, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), false, 5.0, flat(n + 1, 0.0));
        NoTurnResult backward = line(new int[]{NoTurnKeys.S, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 1), false, 9.0, flat(n + 1, 0.0));
        double[] bigTurn = flat(n + 1, 0.0);
        bigTurn[n] = 40.0;
        NoTurnResult sameButTurns = line(new int[]{NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), false, 9.0, bigTurn);
        NoTurnResult ja = line(new int[]{NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), true, 20.0, flat(n + 1, 0.0));

        List<NoTurnResult> list = new ArrayList<>(Arrays.asList(ja, backward, moreInputs, sameButTurns, fewInputs));
        list.sort(NoTurnRanking.easiest(null, MAX_X));

        assertSame(fewInputs, list.get(0));
        assertSame(sameButTurns, list.get(1));
        assertSame(backward, list.get(2));
        assertSame(moreInputs, list.get(3));
        assertSame(ja, list.get(4));
    }

    @Test
    public void easiestBreaksTiesByOffset() {
        int n = 4;
        int[] combos = {NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W};
        NoTurnResult near = line(combos, sprintFrom(n, 0), false, 10.1, flat(n + 1, 0.0));
        NoTurnResult far = line(combos, sprintFrom(n, 0), false, 10.4, flat(n + 1, 0.0));
        JumpConstraint goal = wall(JumpConstraint.Mode.X, GOAL_TICK, JumpConstraint.Cmp.GE, 10.0, "goal");

        List<NoTurnResult> list = new ArrayList<>(Arrays.asList(near, far));
        list.sort(NoTurnRanking.easiest(goal, MAX_X));

        assertSame(far, list.get(0));
        assertSame(near, list.get(1));
    }

    @Test
    public void furthestOrdersByOffsetThenEasiness() {
        int n = 4;
        NoTurnResult farHard = line(new int[]{NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.W},
                sprintFrom(n, 0), false, 10.9, flat(n + 1, 0.0));
        NoTurnResult nearEasy = line(new int[]{NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), false, 10.2, flat(n + 1, 0.0));
        NoTurnResult farEasy = line(new int[]{NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W},
                sprintFrom(n, 0), false, 10.9, flat(n + 1, 0.0));
        JumpConstraint goal = wall(JumpConstraint.Mode.X, GOAL_TICK, JumpConstraint.Cmp.GE, 10.0, "goal");

        List<NoTurnResult> list = new ArrayList<>(Arrays.asList(nearEasy, farHard, farEasy));
        list.sort(NoTurnRanking.furthest(goal, MAX_X));

        assertSame(farEasy, list.get(0));
        assertSame(farHard, list.get(1));
        assertSame(nearEasy, list.get(2));
    }

    @Test
    public void goalWallPicksTheTightestWallOnTheObjectiveTickAndAxis() {
        List<JumpConstraint> cons = Arrays.asList(
                wall(JumpConstraint.Mode.X, GOAL_TICK, JumpConstraint.Cmp.GE, 9.0, "loose"),
                wall(JumpConstraint.Mode.X, GOAL_TICK, JumpConstraint.Cmp.GE, 10.0, "tight"),
                wall(JumpConstraint.Mode.X, GOAL_TICK, JumpConstraint.Cmp.LE, 12.0, "cap"),
                wall(JumpConstraint.Mode.Z, GOAL_TICK, JumpConstraint.Cmp.GE, 50.0, "otherAxis"),
                wall(JumpConstraint.Mode.X, GOAL_TICK - 1, JumpConstraint.Cmp.GE, 11.0, "otherTick"));

        JumpConstraint goal = NoTurnRanking.goalWall(cons, MAX_X);

        assertEquals("tight", goal.name);
        assertEquals(0.75, NoTurnRanking.offset(goal, MAX_X, 10.75), 1.0e-12);
    }

    @Test
    public void minObjectiveOffsetIsMeasuredBelowTheWall() {
        JumpConstraint goal = wall(JumpConstraint.Mode.Z, GOAL_TICK, JumpConstraint.Cmp.LE, -3.0, "goal");
        assertEquals(0.25, NoTurnRanking.offset(goal, MIN_Z, -3.25), 1.0e-12);
        assertNull(NoTurnRanking.goalWall(new ArrayList<>(), MIN_Z));
        assertEquals(3.25, NoTurnRanking.offset(null, MIN_Z, -3.25), 1.0e-12);
    }
}
