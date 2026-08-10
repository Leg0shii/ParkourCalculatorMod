package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class KeyLine {

    public static final int NONE = 0;
    public static final int W = 1;
    public static final int WA = 2;
    public static final int WD = 3;
    public static final int A = 4;
    public static final int D = 5;
    public static final int S = 6;
    public static final int SA = 7;
    public static final int SD = 8;
    public static final int COMBO_COUNT = 9;

    public static final int[] FORWARD_SIGN = {0, 1, 1, 1, 0, 0, -1, -1, -1};
    public static final int[] STRAFE_SIGN = {0, 0, 1, -1, 1, -1, 0, 1, -1};
    public static final String[] COMBO_LABEL = {"-", "W", "WA", "WD", "A", "D", "S", "SA", "SD"};

    public static boolean canRun(int combo) {
        return FORWARD_SIGN[combo] > 0;
    }

    public final ColdProblem problem;
    public final int[] moveKey;
    public final boolean[] sprintHold;
    public final int tailCombo;

    public KeyLine(ColdProblem problem, int[] moveKey, boolean[] sprintHold) {
        this(problem, moveKey, sprintHold, WA);
    }

    public KeyLine(ColdProblem problem, int[] moveKey, boolean[] sprintHold, int tailCombo) {
        this.problem = problem;
        this.moveKey = moveKey;
        this.sprintHold = sprintHold;
        this.tailCombo = tailCombo;
    }

    public int comboAt(int seg) {
        if (seg <= problem.lastPressSeg) return moveKey[seg];
        return tailCombo;
    }

    public boolean holdAt(int seg) {
        if (seg <= problem.lastPressSeg) return sprintHold[seg];
        return true;
    }

    public boolean[] sprintStates() {
        boolean[] sprint = new boolean[problem.numTicks];
        boolean on = false;
        for (int k = 0; k < problem.numTicks; k++) {
            int combo = comboAt(k);
            if (!canRun(combo)) {
                on = false;
            } else if (!on && holdAt(k)) {
                on = true;
            }
            sprint[k] = on;
        }
        return sprint;
    }

    public boolean isPress(int seg) {
        for (int p : problem.pressSegTicks) {
            if (p == seg) return true;
        }
        return false;
    }

    public List<InputRow> toRows() {
        List<InputRow> rows = new ArrayList<InputRow>(problem.landingTick + 1);
        for (int t = 0; t <= problem.landingTick; t++) {
            InputRow row = new InputRow();
            int seg = t - problem.startTick;
            if (seg >= 0 && seg < problem.numTicks) {
                int combo = comboAt(seg);
                if (FORWARD_SIGN[combo] > 0) row.setKeyActive(InputRow.Key.W, true);
                if (FORWARD_SIGN[combo] < 0) row.setKeyActive(InputRow.Key.S, true);
                if (STRAFE_SIGN[combo] > 0) row.setKeyActive(InputRow.Key.A, true);
                if (STRAFE_SIGN[combo] < 0) row.setKeyActive(InputRow.Key.D, true);
                if (holdAt(seg)) row.setKeyActive(InputRow.Key.SPRINT, true);
                if (isPress(seg)) row.setKeyActive(InputRow.Key.JUMP, true);
            }
            rows.add(row);
        }
        return rows;
    }

    public String signature() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k <= problem.lastPressSeg; k++) {
            sb.append(moveKey[k]).append(sprintHold[k] ? '+' : '.');
        }
        return sb.toString();
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        while (k <= problem.lastPressSeg) {
            int run = k;
            while (run + 1 <= problem.lastPressSeg
                    && moveKey[run + 1] == moveKey[k] && sprintHold[run + 1] == sprintHold[k]) {
                run++;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append('t').append(problem.startTick + k).append('-').append(problem.startTick + run)
                    .append(':').append(COMBO_LABEL[moveKey[k]]);
            if (sprintHold[k]) sb.append("+SPRINT");
            k = run + 1;
        }
        sb.append(" presses");
        for (int p : problem.pressSegTicks) sb.append(' ').append(problem.startTick + p);
        sb.append(" tail:").append(COMBO_LABEL[tailCombo]).append("+SPRINT");
        return sb.toString();
    }
}
