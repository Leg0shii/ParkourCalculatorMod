package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class NoTurnKeys {

    public static final int NONE = 0;
    public static final int W = 1;
    public static final int WA = 2;
    public static final int WD = 3;
    public static final int A = 4;
    public static final int D = 5;
    public static final int S = 6;
    public static final int SA = 7;
    public static final int SD = 8;
    public static final int COUNT = 9;

    public static final float SCALE = 0.98F;

    private static final int[] FORWARD_SIGN = {0, 1, 1, 1, 0, 0, -1, -1, -1};
    private static final int[] STRAFE_SIGN = {0, 0, 1, -1, 1, -1, 0, 1, -1};
    private static final String[] LABEL = {"-", "W", "WA", "WD", "A", "D", "S", "SA", "SD"};

    private NoTurnKeys() {
    }

    public static boolean isRun(int combo) {
        return FORWARD_SIGN[combo] > 0;
    }

    public static boolean isMove(int combo) {
        return combo != NONE;
    }

    public static float forwardInput(int combo) {
        return FORWARD_SIGN[combo] * SCALE;
    }

    public static float strafeInput(int combo) {
        return STRAFE_SIGN[combo] * SCALE;
    }

    public static int forwardSign(int combo) {
        return FORWARD_SIGN[combo];
    }

    public static int strafeSign(int combo) {
        return STRAFE_SIGN[combo];
    }

    public static boolean isDiagonal(int combo) {
        return FORWARD_SIGN[combo] != 0 && STRAFE_SIGN[combo] != 0;
    }

    public static String label(int combo) {
        return LABEL[combo];
    }

    public static String describe(int[] combos) {
        StringBuilder sb = new StringBuilder();
        int t = 0;
        while (t < combos.length) {
            int c = combos[t];
            int run = t;
            while (run + 1 < combos.length && combos[run + 1] == c) run++;
            if (sb.length() > 0) sb.append(' ');
            sb.append(LABEL[c]);
            int len = run - t + 1;
            if (len > 1) sb.append('x').append(len);
            t = run + 1;
        }
        return sb.toString();
    }

    public static boolean[] latchSprint(int[] combos, int engageTick) {
        boolean[] sprint = new boolean[combos.length];
        boolean on = false;
        for (int t = 0; t < combos.length; t++) {
            if (!isRun(combos[t])) {
                on = false;
            } else if (!on && t >= engageTick) {
                on = true;
            }
            sprint[t] = on;
        }
        return sprint;
    }

    public static int comboFor(int forwardSign, int strafeSign) {
        for (int c = 0; c < COUNT; c++) {
            if (FORWARD_SIGN[c] == forwardSign && STRAFE_SIGN[c] == strafeSign) return c;
        }
        return NONE;
    }

    public static int countEdges(int[] combos) {
        int edges = 0;
        for (int t = 1; t < combos.length; t++) {
            if (combos[t] != combos[t - 1]) edges++;
        }
        return edges;
    }
}
