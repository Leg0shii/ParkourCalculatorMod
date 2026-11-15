package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class SprintTap {

    public static final int WINDOW = 7;
    private static final float IMPULSE = 1.0e-5F;
    private static final int NONE = Integer.MIN_VALUE;

    private SprintTap() {
    }

    public static boolean forward(JumpPhysicsInputs sc, int t) {
        return sc.strafeAt(t) || sc.forwardAt(t) > IMPULSE;
    }

    public static boolean backward(JumpPhysicsInputs sc, int t) {
        return !sc.strafeAt(t) && sc.forwardAt(t) < -IMPULSE;
    }

    private static int[] armers(JumpPhysicsInputs sc) {
        int n = sc.numTicks;
        int[] out = new int[n];
        int idle = 1;
        int set = 1 << WINDOW;
        int timers = sc.incomingSprintTap >= 0 && sc.incomingSprintTap <= WINDOW ? 1 << sc.incomingSprintTap : (set << 1) - 1;
        int armer = -1;
        boolean sprinted = sc.incomingSprint != null && sc.incomingSprint;
        boolean pressKnown = sc.incomingForward != null || sprinted;
        boolean prevForward = sc.incomingForward != null ? sc.incomingForward : sprinted;
        for (int t = 0; t < n; t++) {
            out[t] = NONE;
            timers = (timers >> 1) | (timers & idle);
            if (backward(sc, t) || (t > 0 && sc.sneakAt(t - 1))) {
                timers = idle;
            }
            boolean forward = forward(sc, t);
            if (forward && !(pressKnown && prevForward)) {
                if ((timers & ~idle) != 0) {
                    out[t] = armer;
                }
                if ((timers & idle) != 0) {
                    timers = pressKnown ? (timers & ~idle) | set : timers | set;
                    armer = t;
                }
            }
            prevForward = forward;
            pressKnown = true;
        }
        return out;
    }

    public static boolean[] conflicts(JumpPhysicsInputs sc) {
        int[] armers = armers(sc);
        boolean[] out = new boolean[armers.length];
        for (int t = 0; t < out.length; t++) {
            out[t] = armers[t] != NONE && !sc.sprintAt(t);
        }
        return out;
    }

    public static int armer(JumpPhysicsInputs sc, int tick) {
        int armer = armers(sc)[tick];
        return armer == NONE ? -1 : armer;
    }

    public static int firstNew(JumpPhysicsInputs base, JumpPhysicsInputs planned) {
        boolean[] was = conflicts(base);
        boolean[] now = conflicts(planned);
        boolean same = true;
        for (int t = 0; t < now.length; t++) {
            same = same && forward(base, t) == forward(planned, t) && backward(base, t) == backward(planned, t)
                    && base.sprintAt(t) == planned.sprintAt(t);
            if (now[t] && !(same && was[t])) {
                return t;
            }
        }
        return -1;
    }

    public static int added(boolean[] before, boolean[] after) {
        for (int t = 0; t < after.length; t++) {
            if (after[t] && !before[t]) {
                return t;
            }
        }
        return -1;
    }
}
