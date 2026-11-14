package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class MomentumRules {

    static final int NO_KEYS_YET = NoTurnKeys.COUNT;
    public static final int SPRINT_VARIANTS = 3;

    final int n;
    final int maxEdges;
    final int minDwell;
    final int[] alphabet;
    final int states;
    private final int holds;
    private final boolean[] jump;
    private final boolean[] takeoff = new boolean[NoTurnKeys.COUNT];

    public MomentumRules(NoTurnProblem problem, StructurePoolDriver.Config cfg, int maxEdges, int minDwell) {
        this.n = problem.n;
        this.jump = problem.jump;
        this.maxEdges = maxEdges;
        this.minDwell = minDwell;
        this.alphabet = cfg.alphabet;
        for (int c : cfg.takeoffCombos) takeoff[c] = true;
        this.holds = Math.max(1, minDwell);
        this.states = (NoTurnKeys.COUNT + 1) * holds * (maxEdges + 1) * 4;
    }

    int state(int previous, int hold, int edges, int phase) {
        return ((previous * holds + hold) * (maxEdges + 1) + edges) * 4 + phase;
    }

    public int startState() {
        return state(NO_KEYS_YET, 0, 0, 0);
    }

    int edgesOf(int state) {
        return (state >> 2) % (maxEdges + 1);
    }

    int previousOf(int state) {
        return (state >> 2) / (maxEdges + 1) / holds;
    }

    static int phaseOf(int state) {
        return state & 3;
    }

    private boolean anchor(int t) {
        return jump[t] || (t > 0 && jump[t - 1]);
    }

    private boolean freeChange(int t, int combo) {
        return combo == NoTurnKeys.NONE ? jump[t] : takeoff[combo];
    }

    private int holdAt(int t) {
        boolean shortHold = anchor(t) || t == 0 || (t + 1 < n && jump[t + 1]);
        return (shortHold ? 1 : Math.min(minDwell, n - t)) - 1;
    }

    public int next(int t, int state, int combo, int variant, int[] keyOut) {
        int phase = state & 3;
        int rest = state >> 2;
        int edges = rest % (maxEdges + 1);
        rest /= maxEdges + 1;
        int hold = rest % holds;
        int previous = rest / holds;
        int nextHold;
        if (previous == NO_KEYS_YET) {
            nextHold = holdAt(t);
        } else if (combo == previous) {
            nextHold = Math.max(0, hold - 1);
        } else {
            if (hold != 0) return -1;
            if (!(anchor(t) && freeChange(t, combo))) edges++;
            if (edges > maxEdges) return -1;
            nextHold = holdAt(t);
        }
        boolean run = NoTurnKeys.isRun(combo);
        int nextPhase = phase == 0 ? (run ? 1 : 0) : Math.min(phase + 1, 3);
        boolean sprint = run && nextPhase > variant;
        boolean before = previous == NO_KEYS_YET ? sprint : NoTurnKeys.isRun(previous) && phase > variant;
        keyOut[0] = FreeAirExact.Tables.key(combo, sprint, before);
        return state(combo, nextHold, edges, nextPhase);
    }

    public boolean accepts(int[] combos) {
        int state = startState();
        int[] key = new int[1];
        for (int t = 0; t < n && state >= 0; t++) state = next(t, state, combos[t], 0, key);
        return state >= 0;
    }

    public static void sprintOf(int[] combos, int variant, boolean[] sprint) {
        int phase = 0;
        for (int t = 0; t < combos.length; t++) {
            boolean run = NoTurnKeys.isRun(combos[t]);
            phase = phase == 0 ? (run ? 1 : 0) : Math.min(phase + 1, 3);
            sprint[t] = run && phase > variant;
        }
    }
}
