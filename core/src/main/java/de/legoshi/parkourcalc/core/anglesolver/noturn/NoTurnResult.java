package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class NoTurnResult {

    public final int[] combos;
    public final boolean[] sprint;
    public final int turnCombo;
    public final boolean ja;
    public final int edges;
    public final int sprintEngage;
    public final double objective;
    public final double violation;
    public final double startX;
    public final double startZ;
    public final double[] yaws;
    public boolean warm = false;

    public NoTurnResult(int[] combos, boolean[] sprint, int turnCombo, boolean ja, int edges, int sprintEngage,
                        double objective, double violation, double startX, double startZ, double[] yaws) {
        this.combos = combos;
        this.sprint = sprint;
        this.turnCombo = turnCombo;
        this.ja = ja;
        this.edges = edges;
        this.sprintEngage = sprintEngage;
        this.objective = objective;
        this.violation = violation;
        this.startX = startX;
        this.startZ = startZ;
        this.yaws = yaws;
    }

    public String describe() {
        return (ja ? "no-turn+ja" : "no-turn") + (warm ? " (from current inputs)" : " (cold)")
                + " | edges=" + edges + " | keys " + NoTurnKeys.describe(combos)
                + " | sprint@" + (sprintEngage >= 0 ? sprintEngage : "-")
                + " | obj=" + String.format(java.util.Locale.ROOT, "%.6f", objective);
    }
}
