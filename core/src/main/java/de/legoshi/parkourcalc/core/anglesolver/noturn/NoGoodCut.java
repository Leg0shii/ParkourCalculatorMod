package de.legoshi.parkourcalc.core.anglesolver.noturn;

public final class NoGoodCut {

    public final int[] ticks;
    public final int[] combos;
    public final int bindingWallTick;
    public final double violation;

    public NoGoodCut(int[] ticks, int[] combos, int bindingWallTick, double violation) {
        this.ticks = ticks;
        this.combos = combos;
        this.bindingWallTick = bindingWallTick;
        this.violation = violation;
    }

    public boolean matches(int[] schedule) {
        for (int i = 0; i < ticks.length; i++) {
            int t = ticks[i];
            if (t < 0 || t >= schedule.length || schedule[t] != combos[i]) return false;
        }
        return true;
    }

    public int size() {
        return ticks.length;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder("cut{");
        for (int i = 0; i < ticks.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ticks[i]).append("->").append(NoTurnKeys.label(combos[i]));
        }
        sb.append("} wall@").append(bindingWallTick)
                .append(" viol=").append(String.format(java.util.Locale.ROOT, "%.3e", violation));
        return sb.toString();
    }
}
